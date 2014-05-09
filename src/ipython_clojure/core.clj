(ns ipython-clojure.core
  (require [clojure.data.json :as json]
           [cheshire.core :as cheshire]
           [clojure.walk :as walk]
           [zeromq.zmq :as zmq]
           [clj-time.core :as time]
           [clj-time.format :as time-format]
           [com.keminglabs.zmq-async.core :refer [register-socket!]]
           [clojure.core.async :refer [sliding-buffer >! <! go chan close!]])
  (:import [org.jeromq ZMQ])
  (:gen-class :main true))

(defn prep-config [args]
  (-> args first slurp json/read-str walk/keywordize-keys))

(defn address [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(defn get-iopub-socket [addr]
  (let [context (zmq/context 1)]
    (doto zmq/socket context :pub) (zmq/bind addr)))

(defn get-shell-socket [addr]
  (let [context (zmq/context 2)]
    (doto (zmq/socket context :router) (zmq/bind addr))))

(defn uuid [] (str (java.util.UUID/randomUUID)))


(def kernel-info-content
  {:protocol_version [4 1]
   :ipython_version [2 0 0 "dev"]
   :language_version [1 5 1]
   :language "clojure"})


(defn now []
  "Returns current ISO 8601 compliant date."
  (let [current-date-time (time/to-time-zone (time/now) (time/default-time-zone))]
    (time-format/unparse
     (time-format/with-zone (time-format/formatters :date-time-no-ms)
                            (.getZone current-date-time))
     current-date-time)))

(defn kernel-info-header [message]
  (let [header (cheshire/generate-string {:date (get-in message [:header :date])
                                          :msg_id (uuid)
                                          :username (get-in message [:header :username])
                                          :session (get-in message [:header :session])
                                          :msg_type "kernel_info_reply"})]
    header))

(defn send-message-piece [socket msg]
;  (println "Sending piece " msg)
  (zmq/send socket (.getBytes msg) zmq/send-more))

(defn finish-message [socket msg]
;  (println "Sending message " msg)
  (zmq/send socket (.getBytes msg)))

(defn kernel-info-reply [message socket]
  (let [header (kernel-info-header message)
        parent_header (cheshire/generate-string (:header message))
        metadata (cheshire/generate-string {})
        content  (cheshire/generate-string kernel-info-content)]
    (send-message-piece socket (get-in message [:header :session]))
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket "")
    (send-message-piece socket header)
    (send-message-piece socket parent_header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn read-blob [socket]
  (let [part (zmq/receive socket)
        blob (apply str (map char part))]
    blob))

(defn read-until-delimiter [socket]
  (let [preamble (doall (drop-last
                         (take-while (comp not #(= "<IDS|MSG>" %))
                                     (repeatedly #(read-blob socket)))))]
;    (println "PREABMLE:" preamble)
    preamble))

(defn new-header [msg_type session-id]
  {:date (now)
   :msg_id (uuid)
   :username "kernel"
   :session session-id
   :msg_type msg_type})

(defn status-content [status]
  {:execution_state status})

(defn pyin-content [execution-count message]
  {:execution_count execution-count
   :code (get-in message [:content :code])})

(defn pyout-content [execution-count message executer]
  {:execution_count execution-count
   :data {:text/plain (pr-str (eval (read-string (get-in message [:content :code]))))}
   :metadata {}
   })


(defn send-message [socket msg_type content parent_header metadata session-id]
  (let [header (cheshire/generate-string (new-header msg_type session-id))
        parent_header (cheshire/generate-string parent_header)
        metadata (cheshire/generate-string metadata)
        content (cheshire/generate-string content)]
    (send-message-piece socket session-id)
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket "")
    (send-message-piece socket header)
    (send-message-piece socket parent_header)
    (send-message-piece socket metadata)
    (finish-message socket content)))


(defn read-message [socket]
  {:uuid (read-until-delimiter socket)
   :signature (read-blob socket)
   :header (cheshire/parse-string (read-blob socket) keyword)
   :parent-header (cheshire/parse-string (read-blob socket) keyword)
   :metadata (cheshire/parse-string (read-blob socket) keyword)
   :content (cheshire/parse-string (read-blob socket) keyword)})

(defn execute-request-handler [shell-socket iopub-socket executer]
  (let [execution-count (atom 0N)]
    (fn [message]
      (let [session-id (get-in message [:header :session])
            parent-header (:header message)]
        (swap! execution-count inc)
        (send-message iopub-socket "status" (status-content "busy")
                      parent-header {} session-id)
        (send-message iopub-socket "pyin" (pyin-content @execution-count message)
                      parent-header {} session-id)
        (send-message shell-socket "execute_reply"
                      {:status "ok"
                       :execution_count @execution-count
                       :user_variables {}
                       :payload [{}]
                       :user_expressions {}}
                      parent-header
                      {:dependencies_met "True"
                       :engine session-id
                       :status "ok"
                       :started (now)} session-id)
        (send-message iopub-socket "pyout"  (pyout-content @execution-count
                                                           message executer)
                      parent-header {} session-id)
        (send-message iopub-socket "status" (status-content "idle")
                      parent-header {} session-id)))))

;; (defn execute-request [message shell-socket iopub-socket executer]
;;   (let [parent-header (:header message)
;;         execution-count 1
;;         session-id (get-in message [:header :session])]
;;     (send-message iopub-socket "status" (status-content "busy") parent-header {} session-id)
;;     (send-message iopub-socket "pyin" (pyin-content execution-count message)
;;                   parent-header {} session-id)
;;     (send-message shell-socket "execute_reply"
;;                   {:status "ok"
;;                    :execution_count execution-count
;;                    :user_variables {}
;;                    :payload [{}]
;;                    :user_expressions {}}
;;                   parent-header
;;                   {:dependencies_met "True"
;;                    :engine session-id
;;                    :status "ok"
;;                    :started (now)} session-id)
;;     (send-message iopub-socket "pyout"  (pyout-content execution-count message user-ns)
;;                   parent-header {} session-id)
;;     (send-message iopub-socket "status" (status-content "idle") parent-header {} session-id)))

(defn execute
  "evaluates s-forms"
  ([request] (execute request *ns*))
  ([request user-ns]
    (str
      (try
        (binding [*ns* user-ns] (eval (read-string request)))
        (catch Exception e (.getLocalizedMessage e))))))

(defn generate-ns []
  "generates ns for client connection"
  (let [user-ns (create-ns (gensym "client-"))]
    (execute (str "(clojure.core/refer 'clojure.core)") user-ns)
    user-ns))


(defn get-executer []
  "evaluates s-forms"
  (let [user-ns (generate-ns)]
    (fn [request]
      (str
       (try
         (binding [*ns* user-ns] (eval (read-string request)))
         (catch Exception e (.getLocalizedMessage e)))))))


(defn configure-shell-handler [shell-socket iopub-socket]
  (let [execute-request (execute-request-handler shell-socket iopub-socket
                                                 (get-executer))]
    (fn [message]
      (let [msg-type (get-in message [:header :msg_type])]
        (case msg-type
          "kernel_info_request" (kernel-info-reply message shell-socket)
          "execute_request" (execute-request message)
          (do
            (println "Message type" msg-type "not handled yet. Exiting.")
            (println "Message dump:" message)
            (System/exit -1)))))))

; snagged from http://stackoverflow.com/questions/7684656/clojure-eval-code-in-different-namespace


(defrecord Heartbeat [addr]
    Runnable
    (run [this]
      (let [context (zmq/context 1)
            socket (doto (zmq/socket context :rep)
                     (zmq/bind addr))]
        (while (not (.. Thread currentThread isInterrupted))
          (let [message (zmq/receive socket)]
            (zmq/send socket message))))))

(defrecord Shell [shell-addr iopub-addr]
  Runnable
  (run [this]
    (let [context (zmq/context 1)
          shell-socket (doto (zmq/socket context :router)
                         (zmq/bind shell-addr))
          iopub-socket (doto (zmq/socket context :pub)
                         (zmq/bind iopub-addr))
          shell-handler (configure-shell-handler shell-socket iopub-socket)]
      (while (not (.. Thread currentThread isInterrupted))
        (let [message (read-message shell-socket)]
          (println "Receieved message on shell socket: " message)
          (shell-handler message))))))

(defn shell-loop [shell-addr iopub-addr]
  (let [context (zmq/context 1)
        shell-socket (doto (zmq/socket context :router)
                       (zmq/bind shell-addr))
        iopub-socket (doto (zmq/socket context :pub)
                       (zmq/bind iopub-addr))
        shell-handler (configure-shell-handler shell-socket iopub-socket)]
    (while (not (.. Thread currentThread isInterrupted))
      (let [message (read-message shell-socket)]
        (println "Receieved message on shell socket: " message)
        (shell-handler message)))))

(defn -main [& args]
  (let [hb-addr (address (prep-config args) :hb_port)
        shell-addr (address (prep-config args) :shell_port)
        iopub-addr (address (prep-config args) :iopub_port)]
    (println (prep-config args))
    (println (str "Connecting heartbeat to " hb-addr))
    (-> hb-addr Heartbeat. Thread. .start)
    (println (str "Connecting shell to " shell-addr))
    (println (str "Connecting iopub to " iopub-addr))
    (shell-loop shell-addr iopub-addr)))


