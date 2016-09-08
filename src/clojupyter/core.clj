(ns clojupyter.core
  (:require
            [clojupyter.middleware.pprint]
            [clojupyter.middleware.stacktrace]
            [clojupyter.middleware.completion :as completion]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [zeromq.zmq :as zmq]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [pandect.algo.sha256 :refer [sha256-hmac]]
            [clojure.tools.nrepl :as repl]
            [clojure.tools.nrepl.server :as nrepl-server])
  (:import [org.zeromq ZMQ]
           [java.net ServerSocket])
  (:gen-class :main true))

(def protocol-version "5.0")

;;; Map of sockets used; useful for debug shutdown
(defonce jup-sockets  (atom {}))

(defn get-free-port!
  "Get a free port. Problem?: might be taken before I use it."
  []
  (let [socket (ServerSocket. 0)
        port (.getLocalPort socket)]
    (.close socket)
    port))

(def the-nrepl (atom nil))

(defn prep-config [args]
  (-> args first slurp json/read-str walk/keywordize-keys))

(defn address [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(def kernel-info-content
  {:protocol_version protocol-version
   :language_version "1.8"
   :language "clojure"})

(defn now []
  "Returns current ISO 8601 compliant date."
  (let [current-date-time (time/to-time-zone (time/now) (time/default-time-zone))]
    (time-format/unparse
     (time-format/with-zone (time-format/formatters :date-time-no-ms)
                            (.getZone current-date-time))
     current-date-time)))

(defn message-header [message msgtype]
  (cheshire/generate-string
   {:msg_id (uuid)
    :date (now)
    :username (get-in message [:header :username])
    :session (get-in message [:header :session])
    :msg_type msgtype
    :version protocol-version}))

(defn immediately-close-content [message]
  {:comm_id (get-in message [:content :comm_id])
   :data {}})

(defn send-message-piece [socket msg]
  (zmq/send socket (.getBytes msg) zmq/send-more))

(defn finish-message [socket msg]
  (zmq/send socket (.getBytes msg)))

(defn immediately-close-comm [message socket signer]
  "Just close a comm immediately since we don't handle it yet"
  (let [header (message-header message "comm_close")
        parent_header (cheshire/generate-string (:header message))
        metadata (cheshire/generate-string {})
        content  (cheshire/generate-string (immediately-close-content message))]

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
  (let [header (message-header message "kernel_info_reply")
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
      (send-message iopub-socket "execute_input"
                    (pyin-content @execution-count message)
                    parent-header {} session-id signer))))

(def nrepl-session (atom nil))

(defn set-session!
  "All interaction will occur in one session; this sets atom nrepl-session to it."
  []
  (reset! nrepl-session
          (with-open [conn (repl/connect :port (:port @the-nrepl))]
            (-> (repl/client conn 10000)
                (repl/message {:op :clone})
                doall
                repl/combine-responses
                :new-session))))

(defn request-trace
  "Request a stacktrace for the most recent exception."
  [transport]
  (-> (repl/client transport 10000)
      (repl/message {:op "stacktrace" :session @nrepl-session})
      repl/combine-responses
      doall))

(defn stacktrace-string
  "Return a nicely formatted string."
  [msg]
  (when-let [st (:stacktrace msg)]
    (let [clean (->> st
                     (filter (fn [f] (not-any? #(= "dup" %) (:flags f))))
                     (filter (fn [f] (not-any? #(= "tooling" %) (:flags f))))
                     (filter (fn [f] (not-any? #(= "repl" %) (:flags f))))
                     (filter :file))
          max-file (apply max (map count (map :file clean)))
          max-name (apply max (map count (map :name clean)))]
      (str (format "%s (%s)\n\n" (:message msg) (:class msg))
           (apply str
                  (map #(format (str "%" max-file "s: %5d %-" max-name "s  \n")
                                (:file %) (:line %) (:name %))

                       clean))))))

(defn nrepl-eval
  "Send message to nrepl and process response."
  [code transport]
  (let [result (-> (repl/client transport 3000) ; timeout=3sec.
                   (repl/message {:op :eval :session @nrepl-session :code code})
                   repl/combine-responses)]
    (cond
      (empty? result) "Clojure: Unbalanced parentheses or kernel timed-out while processing form.",
      (seq (:ex result)) (stacktrace-string (request-trace transport))
      :else result)))
;      (if-let [vals (:value result)]
;              (apply str (interpose " " vals)) ; could have sent multiple forms
                                        ;              "Unexpected response from Clojure."))))

(defn reformat-values [result]
  (if-let [vals (:value result)]
    (apply str (interpose " " vals)) ; could have sent multiple forms
    ))


(defn execute-request-handler [shell-socket iopub-socket transport]
  (let [execution-count (atom 0N)
        busy-handler (get-busy-handler iopub-socket)]
    (fn [message signer]
      (let [session-id (get-in message [:header :session])
            parent-header (:header message)]
        (swap! execution-count inc)
        (busy-handler message signer execution-count)
        (let [s# (new java.io.StringWriter)
              [output result error]
              (binding [*out* s#]
                (let [fullresult (nrepl-eval (get-in message [:content :code]) transport)
                      result (reformat-values fullresult)
                      output (:out fullresult)
                      error (:err fullresult)]
                  [output result error]))]
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
          (when output
            (send-message iopub-socket "stream" {:name "stdout" :text output}
                          parent-header {} session-id signer))
          ;; Send stderr
          (when error
            (send-message iopub-socket "stream" {:name "stderr" :text error}
                          parent-header {} session-id signer))

          ;; Send result
          (send-message iopub-socket "execute_result"
                        {:execution_count @execution-count
                         :data {:text/plain result}
                         :metadata {}
                         }
                        parent-header {} session-id signer))
        (send-message iopub-socket "status" (status-content "idle")
                      parent-header {} session-id signer)))))

(defn history-reply [message signer]
  "returns REPL history, not implemented for now and returns a dummy message"
  {:history []})

(defn shutdown-reply [message signer]
  {:restart true})

(defn is-complete-reply-content
  "Returns whether or not what the user has typed is complete (ready for execution).
   Not yet implemented. May be that it is just used by jupyter-console."
  [message]
  (if (completion/complete? (:code (:content message)))
    {:status "complete"}
    {:status "incomplete"}))

(defn complete-reply
  [message signer]
  {:status "ok"})

(defn is-complete-info-reply [message socket signer]
  (let [header (message-header message "is_complete_reply")
        parent_header (cheshire/generate-string (:header message))
        metadata (cheshire/generate-string {})
        content  (cheshire/generate-string (is-complete-reply-content message))]

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

(defn configure-shell-handler [shell-socket iopub-socket signer nrepl-transport]
  (let [execute-request (execute-request-handler shell-socket iopub-socket nrepl-transport)]
    (fn [message]
      (let [msg-type (get-in message [:header :msg_type])]
        (case msg-type
          "kernel_info_request" (kernel-info-reply message shell-socket signer)
          "execute_request" (execute-request message signer)
          "history_request" (history-reply message signer)
          "shutdown_request" (shutdown-reply message signer)
          "comm_open" (immediately-close-comm message shell-socket signer)
          "is_complete_request" (is-complete-info-reply message shell-socket signer)
          "complete_request" (complete-reply message signer)
          (do
            (println "Message type" msg-type "not handled yet. Exiting.")
            (println "Message dump:" message)
            (System/exit -1)))))))

(defrecord Heartbeat [addr]
    Runnable
    (run [this]
      (let [context (zmq/context 1)
            socket (doto (:hb (swap! jup-sockets assoc :hb (zmq/socket context :rep)))
                     (zmq/bind addr))]
        (while (not (.. Thread currentThread isInterrupted))
          (let [message (zmq/receive socket)]
            (zmq/send socket message))))))

(def clojupyter-middleware
  "A vector containing all Clojupyters middleware (additions to nrepl default)."
  '[clojupyter.middleware.pprint/wrap-pprint
    clojupyter.middleware.pprint/wrap-pprint-fn
    clojupyter.middleware.stacktrace/wrap-stacktrace])

(def clojupyer-nrepl-handler
  "Clojupyters nREPL handler."
  (apply nrepl-server/default-handler (map resolve clojupyter-middleware)))

(defn start-nrepl
  "Start an nrepl server. Stop the existing one, if any (only possible when debugging)."
  []
  (when-let [server @the-nrepl]
    (nrepl-server/stop-server server))
  (reset! the-nrepl
          (nrepl-server/start-server
           :port (get-free-port!)
           :handler clojupyer-nrepl-handler))
  (set-session!))

(defn shell-loop [shell-addr iopub-addr signer checker]
  (start-nrepl)
  (with-open [transport (repl/connect :port (:port @the-nrepl))]
    (let [context (zmq/context 1)
          shell-socket (doto (:sh (swap! jup-sockets assoc :sh (zmq/socket context :router)))
                         (zmq/bind shell-addr))
          iopub-socket (doto (:io (swap! jup-sockets assoc :io (zmq/socket context :pub)))
                         (zmq/bind iopub-addr))
          shell-handler (configure-shell-handler shell-socket iopub-socket signer transport)]

    (while (not (.. Thread currentThread isInterrupted))
      (let [message (read-raw-message shell-socket)
            parsed-message (parse-message message)]
        (shell-handler parsed-message))))))

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
