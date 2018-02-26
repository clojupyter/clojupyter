(ns clojupyter.misc.messages
  (require
   [cheshire.core :as cheshire]
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojupyter.misc.complete :as complete]
   [clojupyter.misc.history :as his]
   [clojupyter.protocol.zmq-comm :as pzmq]
   [clojupyter.protocol.nrepl-comm :as pnrepl]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [clojure.tools.nrepl :as nrepl]
   [clojure.tools.nrepl.misc :as nrepl.misc]
   [clojure.tools.nrepl.server :as nrepl.server]
   [pandect.algo.sha256 :refer [sha256-hmac]]
   [taoensso.timbre :as log]
   [zeromq.zmq :as zmq]))

(def protocol-version "5.0")

(defn uuid [] (str (java.util.UUID/randomUUID)))

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

(defn send-message-piece
  [zmq-comm
   socket msg]
  (log/debug "Sending" (with-out-str (pp/pprint msg)))
  (pzmq/zmq-send zmq-comm socket (.getBytes msg) zmq/send-more)
  (log/debug "Finished sending part"))

(defn finish-message
  [zmq-comm
   socket msg]
  (log/debug "Sending" (with-out-str (pp/pprint msg)))
  (pzmq/zmq-send zmq-comm socket (.getBytes msg))
  (log/debug "Finished sending all"))

(defn send-router-message
  [zmq-comm
   socket msg_type content parent-header session-id metadata signer idents]
  (log/info "Trying to send router message\n"
            (with-out-str (pp/pprint content)))
  (let [header        (cheshire/generate-string (new-header msg_type session-id))
        parent-header (cheshire/generate-string parent-header)
        metadata      (cheshire/generate-string metadata)
        content       (cheshire/generate-string content)]
   (when (not (empty? idents))
      (doseq [ident idents];
        (pzmq/zmq-send zmq-comm socket ident zmq/send-more)))
    (send-message-piece zmq-comm socket "<IDS|MSG>")
    (send-message-piece zmq-comm socket (signer header parent-header metadata content))
    (send-message-piece zmq-comm socket header)
    (send-message-piece zmq-comm socket parent-header)
    (send-message-piece zmq-comm socket metadata)
    (finish-message     zmq-comm socket content))
  (log/info "Message sent"))

(defn send-message
  [zmq-comm
   socket msg_type content parent-header metadata session-id signer]
  (log/info "Trying to send message\n"
            (with-out-str (pp/pprint content)))
  (let [header        (cheshire/generate-string (new-header msg_type session-id))
        parent-header (cheshire/generate-string parent-header)
        metadata      (cheshire/generate-string metadata)
        content       (cheshire/generate-string content)]
    (send-message-piece zmq-comm socket msg_type)
    (send-message-piece zmq-comm socket "<IDS|MSG>")
    (send-message-piece zmq-comm socket (signer header parent-header metadata content))
    (send-message-piece zmq-comm socket header)
    (send-message-piece zmq-comm socket parent-header)
    (send-message-piece zmq-comm socket metadata)
    (finish-message zmq-comm socket content))
  (log/info "Message sent"))

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

(defn parse-message [message]
  {:idents (:idents message)
   :delimiter (:delimiter message)
   :signature (:signature message)
   :header (cheshire/parse-string (:header message) keyword)
   :parent-header (cheshire/parse-string (:parent-header message) keyword)
   :content (cheshire/parse-string (:content message) keyword)})

;; Message contents

(defn status-content [status]
  {:execution_state status})

(defn pyin-content [execution-count message]
  {:execution_count execution-count
   :code (get-in message [:content :code])})

(defn is-complete-reply-content
  "Returns whether or not what the user has typed is complete (ready for execution).
   Not yet implemented. May be that it is just used by jupyter-console."
  [message]
  (if (complete/complete? (:code (:content message)))
    {:status "complete"}
    {:status "incomplete"})
  )

(defn complete-reply-content
  [nrepl-comm
   message]
  (let [delimiters #{\( \" \% \space}
        content (:content message)
        cursor_pos (:cursor_pos content)
        code (subs (:code content) 0 cursor_pos)
        sym (as-> (reverse code) $
                  (take-while #(not (contains? delimiters %)) $)
                  (apply str (reverse $)))]
    {:matches (pnrepl/nrepl-complete nrepl-comm sym)
     :metadata {:_jupyter_types_experimental []}
     :cursor_start (- cursor_pos (count sym))
     :cursor_end cursor_pos
     :status "ok"}))

(defn kernel-info-content []
  {:status "ok"
   :protocol_version protocol-version
   :implementation "clojupyter"
   :language_info {:name "clojure"
                   :version (clojure-version)
                   :mimetype "text/x-clojure"
                   :file_extension ".clj"}
   :banner "Clojupyters-0.1.0"
   :help_links []})

(defn comm-open-reply-content [message]
  {:comm_id (get-in message [:content :comm_id])
   :data {}})

;; Request and reply messages

(defn input-request
  [zmq-comm parent-header session-id signer ident]
  (let [metadata {}
        content  {:prompt ">> "
                  :password false}]
    (send-router-message zmq-comm :stdin-socket
                         "input_request"
                         content parent-header session-id metadata signer ident)))

(defn comm-open-reply
  [zmq-comm
   socket message signer]
  "Just close a comm immediately since we don't handle it yet"
  (let [parent-header (:header message)
        session-id (get-in message [:header :session])
        ident (:idents message)
        metadata {}
        content  (comm-open-reply-content message)]
    (send-router-message zmq-comm socket
                         "comm_close"
                         content parent-header session-id metadata signer ident)))

(defn kernel-info-reply
  [zmq-comm
   socket message signer]
  (let [parent-header (:header message)
        session-id (get-in message [:header :session])
        ident (:idents message)
        metadata {}
        content  (kernel-info-content)]
    (send-router-message zmq-comm socket
                         "kernel_info_reply"
                         content parent-header session-id metadata signer ident)))

(defn shutdown-reply
  [states zmq-comm nrepl-comm socket message signer]
  (let [parent-header (:header message)
        metadata {}
        restart (get-in message message [:content :restart])
        content {:restart restart :status "ok"}
        session-id (get-in message [:header :session])
        ident (:idents message)
        server @(:nrepl-server nrepl-comm)]
    (reset! (:alive states) false)
    (nrepl.server/stop-server server)
    (send-router-message zmq-comm socket
                         "shutdown_reply"
                         content parent-header session-id metadata signer ident)
    (Thread/sleep 100)))

(defn comm-info-reply
  [zmq-comm
   socket message signer]
  (let [parent-header (:header message)
        metadata {}
        content  {:comms
                  {:comm_id {:target_name ""}}}
        session-id (get-in message [:header :session])]
    (send-message zmq-comm socket "comm_info_reply"
                  content parent-header metadata session-id signer)))

(defn comm-msg-reply
  [zmq-comm
   socket message socket signer]
  (let [parent-header (:header message)
        metadata {}
        content  {}
        session-id (get-in message [:header :session])]
    (send-message zmq-comm socket "comm_msg_reply"
                  content parent-header metadata session-id signer)))

(defn is-complete-reply
  [zmq-comm
   socket message signer]
  (let [parent-header (:header message)
        metadata {}
        content  (is-complete-reply-content message)
        session-id (get-in message [:header :session])
        ident (:idents message)]
    (send-router-message zmq-comm socket
                         "is_complete_reply"
                         content parent-header session-id metadata signer ident)))

(defn complete-reply
  [zmq-comm nrepl-comm
   socket message signer]
  (let [parent-header (:header message)
        metadata {}
        content  (complete-reply-content nrepl-comm message)
        session-id (get-in message [:header :session])
        ident (:idents message)]
    (send-router-message zmq-comm socket
                         "complete_reply"
                         content parent-header session-id metadata signer ident)))

(defn history-reply
  [states zmq-comm
   socket message signer]
  (let [parent-header (:header message)
        metadata {}
        content  {:history (map #(vector (:session %) (:line %) (:source %))
                            (his/get-history (:history-session states)))}
        session-id (get-in message [:header :session])
        ident (:idents message)]
    (send-router-message zmq-comm socket
                         "history_reply"
                         content parent-header session-id metadata signer ident)))

;; Handlers

(defn execute-request-handler
  [states zmq-comm nrepl-comm socket]
  (let [execution-count (atom 1N)]
    (fn [message signer]
      (let [session-id (get-in message [:header :session])
            ident (:idents message)
            parent-header (:header message)
            code (get-in message [:content :code])
            silent (str/ends-with? code ";")]
        (send-message zmq-comm :iopub-socket "execute_input"
                      (pyin-content @execution-count message)
                      parent-header {} session-id signer)
        (let [nrepl-resp (pnrepl/nrepl-eval nrepl-comm states zmq-comm
                                            code parent-header
                                            session-id signer ident)
              {:keys [result ename traceback]} nrepl-resp
              error (if ename
                      {:status "error"
                       :ename ename
                       :evalue ""
                       :execution_count @execution-count
                       :traceback traceback})]
          (send-router-message zmq-comm :shell-socket "execute_reply"
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
            (send-message zmq-comm :iopub-socket "error"
                          error parent-header {} session-id signer)
            (when-not (or (= result "nil") silent)
              (send-message zmq-comm :iopub-socket "execute_result"
                            {:execution_count @execution-count
                             :data (cheshire/parse-string result true)
                             :metadata {}}
                            parent-header {} session-id signer)))
          (his/add-history (:history-session states) @execution-count code)
          (swap! execution-count inc))))))
