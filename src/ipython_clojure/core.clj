(ns ipython-clojure.core
  (:require [clojure.data.json :as json]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [zeromq.zmq :as zmq]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [pandect.algo.sha256 :refer [sha256-hmac]])
  (:import [org.zeromq ZMQ])
  (:gen-class :main true))

(def protocol-version "5.0")

(defmacro try-let
  "A combination of try and let such that exceptions thrown in the binding or
   body can be handled by catch clauses in the body, and all bindings are
   available to the catch and finally clauses. If an exception is thrown while
   evaluating a binding value, it and all subsequent binding values will be nil.
   Example:
   (try-let [x (f a)
             y (f b)]
     (g x y)
     (catch Exception e (println a b x y e)))"
  {:arglists '([[bindings*] exprs* catch-clauses* finally-clause?])}
  [bindings & exprs]
  (when-not (even? (count bindings))
    (throw (IllegalArgumentException. "try-let requires an even number of forms in binding vector")))
  (let [names  (take-nth 2 bindings)
        values (take-nth 2 (next bindings))
        ex     (gensym "ex__")]
    `(let [~ex nil
           ~@(interleave names (repeat nil))
           ~@(interleave
               (map vector names (repeat ex))
               (for [v values]
                 `(if ~ex
                    [nil ~ex]
                    (try [~v nil]
                      (catch Throwable ~ex [nil ~ex])))))]
      (try
        (when ~ex (throw ~ex))
        ~@exprs))))


(defn prep-config [args]
  (-> args first slurp json/read-str walk/keywordize-keys))

(defn address [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(def kernel-info-content
  {:protocol_version protocol-version
   :language_version "1.5.1"
   :language "clojure"})

(defn now []
  "Returns current ISO 8601 compliant date."
  (let [current-date-time (time/to-time-zone (time/now) (time/default-time-zone))]
    (time-format/unparse
     (time-format/with-zone (time-format/formatters :date-time-no-ms)
                            (.getZone current-date-time))
     current-date-time)))

(defn kernel-info-header [message]
  (let [header (cheshire/generate-string {:msg_id (uuid)
                                          :date (now)
                                          :username (get-in message [:header :username])
                                          :session (get-in message [:header :session])
                                          :msg_type "kernel_info_reply"
                                          :version protocol-version})]
    header))

(defn close-comm-header [message]
  (let [header (cheshire/generate-string {:msg_id (uuid)
                                          :data ""
                                          :msg_type "comm_close"})]
    header))

(defn send-message-piece [socket msg]
  (zmq/send socket (.getBytes msg) zmq/send-more))

(defn finish-message [socket msg]
  (zmq/send socket (.getBytes msg)))

(defn immediately-close-comm [message socket signer]
  "Just close a comm immediately since we don't handle it yet"
  (let [header (close-comm-header message)
        parent_header (cheshire/generate-string (:header message))
        metadata (cheshire/generate-string {})
        content  (cheshire/generate-string kernel-info-content)]

    ;; First send the client identifiers for the router socket's benefit
    (when (not (empty? (:idents message)))
      (doseq [ident (:idents message)]
        (zmq/send socket ident zmq/send-more))
      (zmq/send socket (byte-array 0) zmq/send-more))

    (send-message-piece socket (get-in message [:header :session]))
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket (signer header parent_header metadata content))
    (send-message-piece socket header)
    (send-message-piece socket parent_header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn kernel-info-reply [message socket signer]
  (let [header (kernel-info-header message)
        parent_header (cheshire/generate-string (:header message))
        metadata (cheshire/generate-string {})
        content  (cheshire/generate-string kernel-info-content)]

    ;; First send the client identifiers for the router socket's benefit
    (when (not (empty? (:idents message)))
      (doseq [ident (:idents message)]
        (zmq/send socket ident zmq/send-more))
      (zmq/send socket (byte-array 0) zmq/send-more))

    (send-message-piece socket (get-in message [:header :session]))
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket (signer header parent_header metadata content))
    (send-message-piece socket header)
    (send-message-piece socket parent_header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn read-blob [socket]
  (let [part (zmq/receive socket)]
    (try
      (apply str (map char part))
      (catch Exception e (str "caught exception: " (.getMessage e) part)))))

(defn try-convert-to-string [bytes]
  (try
    (apply str (map char bytes))
    (catch Exception e "")))

(defn read-until-delimiter [socket]
  (let [preamble (doall (drop-last
                         (take-while (comp not #(= "<IDS|MSG>"
                                                   (try-convert-to-string %)))
                                     (repeatedly #(zmq/receive socket)))))]
    preamble))

(defn new-header [msg_type session-id]
  {:date (now)
   :version protocol-version
   :msg_id (uuid)
   :username "kernel"
   :session session-id
   :msg_type msg_type})

(defn status-content [status]
  {:execution_state status})

(defn pyin-content [execution-count message]
  {:execution_count execution-count
   :code (get-in message [:content :code])})

(defn get-message-signer [key]
  "returns a function used to sign a message"
  (if (empty? key)
    (fn [header parent metadata content] "")
    (fn [header parent metadata content]
      (sha256-hmac (str header parent metadata content) key))))

(defn get-message-checker [signer]
  "returns a function to check an incoming message"
  (fn [{:keys [signature header parent-header metadata content]}]
    (let [our-signature (signer header parent-header metadata content)]
      (= our-signature signature))))

(defn send-router-message [socket msg_type content parent_header metadata session-id signer idents]
  (let [header (cheshire/generate-string (new-header msg_type session-id))
        parent_header (cheshire/generate-string parent_header)
        metadata (cheshire/generate-string metadata)
        content (cheshire/generate-string content)]

    (when (not (empty? idents))
      (doseq [ident idents] ; First send the zmq identifiers for the router socket's benefit
        (zmq/send socket ident zmq/send-more))
      (zmq/send socket (byte-array 0) zmq/send-more))

    (send-message-piece socket session-id)
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket (signer header parent_header metadata content))
    (send-message-piece socket header)
    (send-message-piece socket parent_header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn send-message [socket msg_type content parent_header metadata session-id signer]
  (let [header (cheshire/generate-string (new-header msg_type session-id))
        parent_header (cheshire/generate-string parent_header)
        metadata (cheshire/generate-string metadata)
        content (cheshire/generate-string content)]
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket (signer header parent_header metadata content))
    (send-message-piece socket header)
    (send-message-piece socket parent_header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn read-raw-message [socket]
  {:idents (read-until-delimiter socket)
   :delimiter "<IDS|MSG>"
   :signature (read-blob socket)
   :header (read-blob socket)
   :parent-header (read-blob socket)
   :metadata (read-blob socket)
   :content (read-blob socket)})

(defn parse-message [message]
  {:idents (:idents message)
   :delimiter (:delimiter message)
   :signature (:signature message)
   :header (cheshire/parse-string (:header message) keyword)
   :parent-header (cheshire/parse-string (:parent-header message) keyword)
   :content (cheshire/parse-string (:content message) keyword)})

(defn get-busy-handler [iopub-socket]
  "handles setting the engine to busy when executing a request"
  (fn [message signer execution-count]
    (let [session-id (get-in message [:header :session])
          parent-header (:header message)]
      (send-message iopub-socket "status" (status-content "busy")
                    parent-header {} session-id signer)
      (send-message iopub-socket "execute-content"
                    (pyin-content @execution-count message)
                    parent-header {} session-id signer))))

(defn execute-request-handler [shell-socket iopub-socket executer]
  (let [execution-count (atom 0N)
        busy-handler (get-busy-handler iopub-socket)]
    (fn [message signer]
      (let [session-id (get-in message [:header :session])
            parent-header (:header message)]
        (swap! execution-count inc)
        (busy-handler message signer execution-count)
        (try
          (let [s# (new java.io.StringWriter) [output results]
                (binding [*out* s#]
                  (let [result (pr-str (eval (read-string
                                              (get-in message [:content :code]))))
                      output (str s#)]
                  [output, result]))]
            (send-router-message shell-socket "execute_reply"
                                 {:status "ok"
                                  :execution_count @execution-count
                                  :user_expressions {}}
                                 parent-header
                                 {:dependencies_met "True"
                                  :engine session-id
                                  :status "ok"
                                  :started (now)} session-id signer (:idents message))
            ;; Send stdout
            (send-message iopub-socket "stream" {:name "stdout" :text output}
                          parent-header {} session-id signer)
            ;; Send results
            (send-message iopub-socket "execute_result"
                          {:execution_count @execution-count
                           :data {:text/plain results}
                           :metadata {}
                           }
                          parent-header {} session-id signer))
          (catch Exception e
            ;; Send error on iopub socket
            (send-message iopub-socket "error"
                          {:execution_count @execution-count
                           :ename (.getSimpleName (.getClass e))
                           :evalue (.getLocalizedMessage e)
                           :traceback (map #(.toString %) (.getStackTrace e))
                           }
                          parent-header {} session-id signer)
            ;; Send an error execute_reply message
            (send-router-message shell-socket "execute_reply"
                                 {:status "error"
                                  :execution_count @execution-count
                                  :ename (.getSimpleName (.getClass e))
                                  :evalue (.getLocalizedMessage e)
                                  :traceback (map #(.toString %) (.getStackTrace e))
                                  }
                                 parent-header
                                 {:dependencies_met "True"
                                  :engine session-id
                                  :status "ok"
                                  :started (now)} session-id signer (:idents message)))
          )
        (send-message iopub-socket "status" (status-content "idle")
                      parent-header {} session-id signer)))))

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

(defn history-reply [message signer]
  "returns REPL history, not implemented for now and returns a dummy message"
  {:history []})

(defn shutdown-reply [message signer]
  {:restart true})

(defn configure-shell-handler [shell-socket iopub-socket signer]
  (let [execute-request (execute-request-handler shell-socket iopub-socket
                                                 (get-executer))]
    (fn [message]
      (let [msg-type (get-in message [:header :msg_type])]
        (case msg-type
          "kernel_info_request" (kernel-info-reply message shell-socket signer)
          "execute_request" (execute-request message signer)
          "history_request" (history-reply message signer)
          "shutdown_request" (shutdown-reply message signer)
          "comm_open" (immediately-close-comm message shell-socket signer)
          (do
            (println "Message type" msg-type "not handled yet. Exiting.")
            (println "Message dump:" message)
            (System/exit -1)))))))

(defrecord Heartbeat [addr]
    Runnable
    (run [this]
      (let [context (zmq/context 1)
            socket (doto (zmq/socket context :rep)
                     (zmq/bind addr))]
        (while (not (.. Thread currentThread isInterrupted))
          (let [message (zmq/receive socket)]
            (zmq/send socket message))))))

(defn shell-loop [shell-addr iopub-addr signer checker]
  (let [context (zmq/context 1)
        shell-socket (doto (zmq/socket context :router)
                       (zmq/bind shell-addr))
        iopub-socket (doto (zmq/socket context :pub)
                       (zmq/bind iopub-addr))
        shell-handler (configure-shell-handler shell-socket iopub-socket signer)]

    (while (not (.. Thread currentThread isInterrupted))
      (let [message (read-raw-message shell-socket)
            parsed-message (parse-message message)]
        (shell-handler parsed-message)))))

(defn -main [& args]
  (let [hb-addr (address (prep-config args) :hb_port)
        shell-addr (address (prep-config args) :shell_port)
        iopub-addr (address (prep-config args) :iopub_port)
        key (:key (prep-config args))
        signer (get-message-signer key)
        checker (get-message-checker signer)]
    (println "Input configuration:" (prep-config args))
    (println (str "Connecting heartbeat to " hb-addr))
    (-> hb-addr Heartbeat. Thread. .start)
    (println (str "Connecting shell to " shell-addr))
    (println (str "Connecting iopub to " iopub-addr))
    (shell-loop shell-addr iopub-addr signer checker)))
