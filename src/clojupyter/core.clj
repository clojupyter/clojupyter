(ns clojupyter.core
  (:require [spyscope.core]
            [beckon]
            [cheshire.core :as cheshire]
            [cider.nrepl :as cider]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojupyter.middleware.mime-values]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.misc :as nrepl.misc]
            [clojure.tools.nrepl.server :as nrepl.server]
            [clojure.tools.nrepl.transport :as nrepl.trans]
            [clojupyter.misc.complete :as complete]
            [clojure.walk :as walk]
            [pandect.algo.sha256 :refer [sha256-hmac]]
            [zeromq.zmq :as zmq]
            )
  (:import [java.net ServerSocket])
  (:gen-class :main true))

(def protocol-version "5.0")

;;; Map of sockets used; useful for debug shutdown
(defn get-free-port!
  "Get a free port. Problem?: might be taken before I use it."
  []
  (let [socket (ServerSocket. 0)
        port (.getLocalPort socket)]
    (.close socket)
    port))

(def alive (atom true))
(def in-eval (atom nil))
(def interrupted (atom nil))
(def nrepl-server (atom nil))
(def nrepl-session (atom nil))
(def current-session (atom nil))
(def current-commnad-id (atom nil))
(def current-ns (atom (str *ns*)))

(defn prep-config [args]
  (-> args first slurp json/read-str walk/keywordize-keys))

(defn address [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(def kernel-info-content
  {
   :status "ok"
   :protocol_version protocol-version
   :implementation "clojupyter"
   :language_info {:name "clojure"
                   :version (clojure-version)
                   :mimetype "text/x-clojure"
                   :file_extension ".clj"}
   :banner "Clojupyters-0.1.0"
   :help_links []})

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

(defn new-header [msg_type session-id]
  {:date (now)
   :version protocol-version
   :msg_id (uuid)
   :username "kernel"
   :session session-id
   :msg_type msg_type})

(defn immediately-close-content [message]
  {:comm_id (get-in message [:content :comm_id])
   :data {}})

(defn send-message-piece [socket msg]
  (zmq/send socket (.getBytes msg) zmq/send-more))

(defn finish-message [socket msg]
  (zmq/send socket (.getBytes msg)))

(defn send-router-message [socket msg_type content parent-header
                           session-id metadata signer idents]
  (let [socket @socket
        header (cheshire/generate-string (new-header msg_type session-id))
        parent-header (cheshire/generate-string parent-header)
        metadata (cheshire/generate-string metadata)
        content (cheshire/generate-string content)]
    (when (not (empty? idents))
      (doseq [ident idents];
        (zmq/send socket ident zmq/send-more)))
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket (signer header parent-header metadata content))
    (send-message-piece socket header)
    (send-message-piece socket parent-header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn send-message [socket msg_type content parent-header metadata session-id signer]
  (let [socket @socket
        header (cheshire/generate-string (new-header msg_type session-id))
        parent-header (cheshire/generate-string parent-header)
        metadata (cheshire/generate-string metadata)
        content (cheshire/generate-string content)]
    (send-message-piece socket msg_type)
    (send-message-piece socket "<IDS|MSG>")
    (send-message-piece socket (signer header parent-header metadata content))
    (send-message-piece socket header)
    (send-message-piece socket parent-header)
    (send-message-piece socket metadata)
    (finish-message socket content)))

(defn immediately-close-comm [message socket signer]
  "Just close a comm immediately since we don't handle it yet"
  (let [parent-header (:header message)
        session-id (get-in message [:header :session])
        ident (:idents message)
        metadata {}
        content  (immediately-close-content message)]
    (send-router-message socket
                         "comm_close"
                         content parent-header session-id metadata signer ident)))

(defn input-request [socket parent-header session-id signer ident]
  (let [metadata {}
        content  {:prompt ">> "
                  :password false}]
    (send-router-message socket
                         "input_request"
                         content parent-header session-id metadata signer ident)))

(defn input-reply [message]
  (println "in input reply"))

(defn kernel-info-reply [message socket signer]
  (let [parent-header (:header message)
        session-id (get-in message [:header :session])
        ident (:idents message)
        metadata {}
        content  kernel-info-content]
    (send-router-message socket
                         "kernel_info_reply"
                         content parent-header session-id metadata signer ident)))

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

(defn read-raw-message [socket]
  (let [delim "<IDS|MSG>"
        delim-byte (byte-array (map byte delim))
        parts (zmq/receive-all socket)
        delim-idx (first
                   (map first (filter #(apply = (map seq [(second %) delim-byte]))
                                      (map-indexed vector parts))))
        idents (take delim-idx parts)
        blobs (map #(apply str (map char %1))
                   (drop (+ 1 delim-idx) parts))
        blob-names [:signature :header :parent-header :metadata :content]
        n-blobs (count blob-names)
        message (merge
                 {:idents idents :delimiter delim}
                 (zipmap blob-names (take n-blobs blobs))
                 {:buffers (drop n-blobs blobs)})]
    message))

(defn parse-message [message]
  {:idents (:idents message)
   :delimiter (:delimiter message)
   :signature (:signature message)
   :header (cheshire/parse-string (:header message) keyword)
   :parent-header (cheshire/parse-string (:parent-header message) keyword)
   :content (cheshire/parse-string (:content message) keyword)})

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
      (map #(format (str "%" max-file "s: %5d %-" max-name "s")
                    (:file %) (:line %) (:name %))
           clean))))

(defn nrepl-trace
  [nrepl-client]
  (-> nrepl-client
      (nrepl/message {:op :stacktrace
                      :session @nrepl-session})
      nrepl/combine-responses
      doall))

(defn nrepl-interrupt
  [nrepl-client]
  (reset! interrupted true)
  (-> nrepl-client
      (nrepl/message {:op :interrupt
                      :session @nrepl-session})))

(defn nrepl-eval
  [code parent-header session-id signer ident
   shell-socket stdin-socket iopub-socket
   nrepl-client]
  (let [pending (atom #{})
        command-id (nrepl.misc/uuid)
        result (atom {:result "nil"})
        io-sleep   50
        get-input (fn []
                    (input-request stdin-socket
                                   parent-header session-id signer ident))
        send-input (fn  [nrepl-client session pending]
                     (get-input)
                     (let [message (read-raw-message @stdin-socket)
                           parsed-message (parse-message message)
                           input (get-in parsed-message [:content :value])
                           command-id (nrepl.misc/uuid)]
                       (swap! pending conj command-id)
                       (nrepl/message nrepl-client {:id command-id
                                                    :op "stdin"
                                                    :stdin (str input "\n")
                                                    :session session})))
        done?      (fn [{:keys [id status] :as msg} pending]
                     (let [pending? (@pending id)]
                       (swap! pending disj id)
                       (and (not pending?) (some #{"done"
                                                   "interrupted"
                                                   "error"}
                                                 status))))
        stdout     (fn [msg]
                     (Thread/sleep io-sleep)
                     (send-message iopub-socket "stream"
                                   {:name "stdout" :text msg}
                                   parent-header {} session-id signer))
        stderr     (fn [msg]
                     (Thread/sleep io-sleep)
                     (send-message iopub-socket "stream"
                                   {:name "stdout" :text msg}
                                   parent-header {} session-id signer))]

    (reset! in-eval true)
    (doseq [{:keys [ns out err status session ex value] :as msg}
            (nrepl/message nrepl-client
                           {:id command-id
                            :op :eval
                            :session @nrepl-session
                            :code code})
            :while (and (not @interrupted)
                        (not (done? msg pending)))]
      (do
        (when ns (reset! current-ns ns))
        (when out (stdout out))
        (when err (stderr err))
        (when (some #{"need-input"} status)
          (send-input nrepl-client session pending))
        (when ex (swap! result assoc :ename ex))
        (when value (swap! result assoc :result value))))
    (reset! in-eval false)
    (reset! interrupted false)

    (when-let [ex (:ename @result)]
      (swap! result assoc :traceback
             (if (re-find #"StackOverflowError" ex) []
                 (stacktrace-string (nrepl-trace nrepl-client)))))
    @result))

(defn nrepl-complete [code nrepl-client]
  (let [ns @current-ns
        result (-> nrepl-client
                   (nrepl/message {:op :complete
                                   :session @nrepl-session
                                   :symbol code
                                   :ns ns})
                   nrepl/combine-responses)]
    (->> result
         :completions
         (map :candidate)
         (into []))))

(defn execute-request-handler [shell-socket stdin-socket iopub-socket nrepl-client]
  (let [execution-count (atom 0N)]
    (fn [message signer]
      (let [session-id (get-in message [:header :session])
            ident (:idents message)
            parent-header (:header message)
            code (get-in message [:content :code])
            silent (str/ends-with? code ";")]
        (send-message iopub-socket "execute_input"
                      (pyin-content @execution-count message)
                      parent-header {} session-id signer)
        (let [nrepl-resp (nrepl-eval code
                                     parent-header
                                     session-id signer ident
                                     shell-socket stdin-socket iopub-socket
                                     nrepl-client)
              {:keys [result ename traceback]} nrepl-resp
              error (if ename
                      {:status "error"
                       :ename ename
                       :evalue ""
                       :execution_count @execution-count
                       :traceback traceback}
                      nil)]
          (when-not error (swap! execution-count inc))
          (send-router-message shell-socket "execute_reply"
                               (if error
                                 error
                                 {:status "ok"
                                  :execution_count @execution-count
                                  :user_expressions {}})
                               parent-header
                               session-id
                               {}
                               signer ident)
          (if error
            (send-message iopub-socket "error"
                          error parent-header {} session-id signer)
            (when-not (or (= result "nil") silent)
              (send-message iopub-socket "execute_result"
                            {:execution_count @execution-count
                             :data (cheshire/parse-string result true)
                             :metadata {}}
                            parent-header {} session-id signer))))))))

(defn history-reply [message signer]
  "returns REPL history, not implemented for now and returns a dummy message"
  {:history []})

(defn shutdown-reply
  [message socket signer]
  (let [parent-header (:header message)
        metadata {}
        restart (get-in message message [:content :restart])
        content {:restart restart :status "ok"}
        session-id (get-in message [:header :session])
        ident (:idents message)
        server @nrepl-server]
    (reset! alive false)
    (nrepl.server/stop-server server)
    (send-router-message socket
                         "shutdown_reply"
                         content parent-header session-id metadata signer ident)
    (Thread/sleep 100)))

(defn is-complete-reply-content
  "Returns whether or not what the user has typed is complete (ready for execution).
   Not yet implemented. May be that it is just used by jupyter-console."
  [message]
  (if (complete/complete? (:code (:content message)))
    {:status "complete"}
    {:status "incomplete"})
  )

(defn complete-reply-content
  [message nrepl-client]
  (let [content (:content message)
        cursor_pos (:cursor_pos content)
        code (subs (:code content) 0 cursor_pos)
        prefix (second (re-find #".*[\( ]([/-_*+!?\w]*)" code))]
    {:matches (nrepl-complete prefix nrepl-client)
     :cursor_start (- cursor_pos (count prefix))
     :cursor_end cursor_pos
     :status "ok"}))

(defn comm-info-reply
  [message socket signer]
  (let [parent-header (:header message)
        metadata {}
        content  {:comms
                  {:comm_id {:target_name ""}}}
        session-id (get-in message [:header :session])]
    (send-message socket "comm_info_reply" content parent-header metadata session-id signer)))

(defn comm-msg-reply
  [message socket signer]
  (let [parent-header (:header message)
        metadata {}
        content  {}
        session-id (get-in message [:header :session])]
    (send-message socket "comm_msg_reply" content parent-header metadata session-id signer)))

(defn is-complete-reply [message socket signer]
  (let [parent-header (:header message)
        metadata {}
        content  (is-complete-reply-content message)
        session-id (get-in message [:header :session])
        ident (:idents message)]
    (send-router-message socket
                         "is_complete_reply"
                         content parent-header session-id metadata signer ident)))

(defn complete-reply
  [message socket signer transport]
  (let [parent-header (:header message)
        metadata {}
        content  (complete-reply-content message transport)
        session-id (get-in message [:header :session])
        ident (:idents message)]
    (send-router-message socket
                         "complete_reply"
                         content parent-header session-id metadata signer ident)))

(defn configure-shell-handler [shell-socket stdin-socket iopub-socket signer nrepl-client]
  (let [execute-request (execute-request-handler shell-socket stdin-socket iopub-socket
                                                 nrepl-client)]
    (fn [message]
      (let [msg-type (get-in message [:header :msg_type])]
        (case msg-type
          "kernel_info_request" (kernel-info-reply message shell-socket signer)
          "execute_request" (execute-request message signer)
          "history_request" (history-reply message signer)
          "shutdown_request" (shutdown-reply message shell-socket signer)
          "comm_open" (immediately-close-comm message shell-socket signer)
          "comm_info_request" (comm-info-reply message shell-socket signer)
          "comm_msg" (comm-msg-reply message shell-socket signer)
          "is_complete_request" (is-complete-reply message shell-socket signer)
          "complete_request" (complete-reply message shell-socket signer nrepl-client)
          (do
            (log/error "Message type" msg-type "not handled yet. Exiting.")
            (log/error "Message dump:" message)
            (System/exit -1)))))))

(defn configure-control-handler [control-socket signer]
  (fn [message]
    (let [msg-type (get-in message [:header :msg_type])]
      (case msg-type
        "kernel_info_request" (kernel-info-reply message control-socket signer)
        "shutdown_request" (shutdown-reply message control-socket signer)
        (do
          (log/error "Message type" msg-type "not handled yet. Exiting.")
          (log/error "Message dump:" message)
          (System/exit -1))))))

(def clojupyter-middleware
  '[clojupyter.middleware.mime-values/mime-values])

(def clojupyer-nrepl-handler
  (apply nrepl.server/default-handler
         (map resolve
              (concat cider/cider-middleware
                      clojupyter-middleware))))

(defn start-nrepl-server
  []
  (when-let [server @nrepl-server]
    (nrepl.server/stop-server server))
  (nrepl.server/start-server
   :port (get-free-port!)
   :handler clojupyer-nrepl-handler))

(defn event-loop [socket iopub-socket signer handler]
  (while @alive
    (let [message (read-raw-message @socket)
          parsed-message (parse-message message)
          parent-header (:header parsed-message)
          session-id (:session parent-header)]
      (send-message iopub-socket "status" (status-content "busy")
                    parent-header {} session-id signer)
      (handler parsed-message)
      (send-message iopub-socket "status" (status-content "idle")
                    parent-header {} session-id signer))))

(defn heartbeat-loop [hb-socket]
  (while @alive
    (let [hb-socket @hb-socket
          message (zmq/receive hb-socket)]
      (zmq/send hb-socket message))))

(defn shell-loop [shell-socket stdin-socket iopub-socket signer checker]
  (with-open [server    (start-nrepl-server)
              transport (nrepl/connect :port (:port server))
              client    (nrepl/client transport Integer/MAX_VALUE)
              session   (nrepl/new-session client)]
    (reset! nrepl-server server)
    (reset! nrepl-session session)
    (let [shell-handler     (configure-shell-handler
                             shell-socket stdin-socket iopub-socket signer client)
          sigint-handle (fn [] (nrepl-interrupt client))]
      (reset! (beckon/signal-atom "INT") #{sigint-handle})
      (event-loop shell-socket iopub-socket signer shell-handler))))

(defn control-loop [control-socket iopub-socket signer checker]
  (let [control-handler (configure-control-handler control-socket signer)]
    (event-loop control-socket iopub-socket signer control-handler)))

(defn -main [& args]
  (let [hb-addr      (address (prep-config args) :hb_port)
        shell-addr   (address (prep-config args) :shell_port)
        iopub-addr   (address (prep-config args) :iopub_port)
        control-addr (address (prep-config args) :control_port)
        stdin-addr   (address (prep-config args) :stdin_port)
        key          (:key (prep-config args))
        signer       (get-message-signer key)
        checker      (get-message-checker signer)]
    (let [context (zmq/context 1)
          shell-socket   (atom (doto (zmq/socket context :router)
                                 (zmq/bind shell-addr)))
          iopub-socket   (atom (doto (zmq/socket context :pub)
                                 (zmq/bind iopub-addr)))
          control-socket (atom (doto (zmq/socket context :router)
                                 (zmq/bind control-addr)))
          stdin-socket   (atom (doto (zmq/socket context :router)
                                 (zmq/bind stdin-addr)))
          hb-socket      (atom (doto (zmq/socket context :rep)
                                 (zmq/bind hb-addr)))]
      (try
        (future (shell-loop shell-socket stdin-socket iopub-socket signer checker))
        (future (control-loop control-socket iopub-socket signer checker))
        (future (heartbeat-loop hb-socket))
        (while @alive (Thread/sleep 1000))
        (finally (doseq [socket [shell-socket iopub-socket control-socket hb-socket]]
                   (zmq/set-linger @socket 0)
                   (zmq/close @socket))
                 (System/exit 0))))))
