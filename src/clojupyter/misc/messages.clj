(ns clojupyter.misc.messages
  (:require
   [cheshire.core :as cheshire]
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojupyter.misc.complete :as complete]
   [clojupyter.misc.history :as his]
   [clojupyter.misc.tokenize :as tokenize]
   [clojupyter.misc.util	:as u]
   [clojupyter.protocol.zmq-comm :as pzmq]
   [clojupyter.protocol.nrepl-comm :as pnrepl]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [nrepl.core :as nrepl]
   [nrepl.misc :as nrepl.misc]
   [nrepl.server :as nrepl.server]
   [pandect.algo.sha256 :refer [sha256-hmac]]
   [taoensso.timbre :as log]
   [zeromq.zmq :as zmq]))

(def protocol-version "5.0")

(defn- uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn- now
  []
  "Returns current ISO 8601 compliant date."
  (let [current-date-time (time/to-time-zone (time/now) (time/default-time-zone))]
    (time-format/unparse
     (time-format/with-zone (time-format/formatters :date-time-no-ms)
       (.getZone current-date-time))
     current-date-time)))

(defn- message-header
  [message msgtype]
  (cheshire/generate-string
   {:msg_id (uuid)
    :date (now)
    :username (get-in message [:header :username])
    :session (get-in message [:header :session])
    :msg_type msgtype
    :version protocol-version}))

(defn- new-header
  [msg_type session-id]
  {:date (now)
   :version protocol-version
   :msg_id (uuid)
   :username "kernel"
   :session session-id
   :msg_type msg_type})

(defn- send-message-piece
  [zmq-comm socket msg]
  (log/debug "Sending " (u/pp-str msg))
  (pzmq/zmq-send zmq-comm socket (.getBytes msg) zmq/send-more)
  (log/debug "Finished sending part"))

(defn- finish-message
  [zmq-comm socket msg]
  (log/debug "Sending" (u/pp-str msg))
  (pzmq/zmq-send zmq-comm socket (.getBytes msg))
  (log/debug "Finished sending all"))

(defn- send-router-message
  [{:keys [zmq-comm socket signer] :as S} msg-type content parent-message]
  (log/info "Trying to send router message\n" (u/pp-str content))
  (let [session-id	(get-in parent-message [:header :session])
        idents		(:idents parent-message)
        header		(-> (new-header msg-type session-id)	cheshire/generate-string)
        parent-header	(-> parent-message :header		cheshire/generate-string)
        metadata	(-> {}					cheshire/generate-string)
        content		(-> content				cheshire/generate-string)]
    (doseq [ident idents];
      (pzmq/zmq-send zmq-comm socket ident zmq/send-more))
    (send-message-piece zmq-comm socket "<IDS|MSG>")
    (send-message-piece zmq-comm socket (signer header parent-header metadata content))
    (send-message-piece zmq-comm socket header)
    (send-message-piece zmq-comm socket parent-header)
    (send-message-piece zmq-comm socket metadata)
    (finish-message     zmq-comm socket content))
  (log/info "Message sent"))

(defn send-message
  [{:keys [zmq-comm socket signer] :as S} msg-type content parent-message]
  (log/info "Trying to send message\n" (u/pp-str content))
  (let [session-id	(get-in parent-message [:header :session])
        idents		(:idents parent-message)
        header		(-> (new-header msg-type session-id)	cheshire/generate-string)
        parent-header	(-> parent-message :header		cheshire/generate-string)
        metadata	(-> {}					cheshire/generate-string)
        content		(-> content				cheshire/generate-string)]
    (send-message-piece zmq-comm socket msg-type)
    (send-message-piece zmq-comm socket "<IDS|MSG>")
    (send-message-piece zmq-comm socket (signer header parent-header metadata content))
    (send-message-piece zmq-comm socket header)
    (send-message-piece zmq-comm socket parent-header)
    (send-message-piece zmq-comm socket metadata)
    (finish-message zmq-comm socket content))
  (log/info "Message sent"))

(defn get-message-signer
  [key]
  "returns a function used to sign a message"
  (if (empty? key)
    (constantly "")
    (fn [header parent metadata content]
      (sha256-hmac (str header parent metadata content) key))))

(defn get-message-checker
  [signer]
  "returns a function to check an incoming message"
  (fn [{:keys [signature header parent-header metadata content]}]
    (let [our-signature (signer header parent-header metadata content)]
      (= our-signature signature))))

(defn parse-message
  [message]
  {:idents (:idents message)
   :delimiter (:delimiter message)
   :signature (:signature message)
   :header (cheshire/parse-string (:header message) keyword)
   :parent-header (cheshire/parse-string (:parent-header message) keyword)
   :content (cheshire/parse-string (:content message) keyword)})

;; Message contents

(defn status-content
  [status]
  {:execution_state status})

(defn pyin-content
  [execution-count message]
  {:execution_count execution-count
   :code (get-in message [:content :code])})

(defn input-request
  [{:as S} parent-message]
  (let [content  {:prompt ">> ", :password false}]
    (send-router-message (assoc S :socket :stdin-socket) "input_request" content parent-message)))

;;; ----------------------------------------------------------------------------------------------------
;;; RESPOND-TO-MESSAGE
;;; ----------------------------------------------------------------------------------------------------

(defmulti respond-to-message (fn [_ msg-type _] msg-type))

(defmethod respond-to-message "comm_info_request"
  [S _ message]
  (log/debug "respond-to comm_info_request: " :S S :message message)
  (let [content {:comms {:comm_id {:target_name ""}}}]
    (send-message S "comm_info_reply" content message)))

(defmethod respond-to-message "comm_msg"
  [S _ message]
  (log/debug "respond-to comm_msg: " :S S :message message)
  (let [content  {}]
    (send-message S "comm_msg_reply" content message)))

(defmethod respond-to-message "comm_open"
  [S _ message]
  (log/debug "respond-to comm_open: " :S S :message message)
  (let [comm-id (get-in message [:content :comm_id])
        content {:comm_id comm-id, :data {}}]
    (send-router-message S "comm_close" content message)))

(defmethod respond-to-message "kernel_info_request"
  [S _ message]
  (log/debug "respond-to kernel_info_request:" :S S :message message)
  (let [content {:status "ok"
                 :protocol_version protocol-version
                 :implementation "clojupyter"
                 :language_info {:name "clojure"
                                 :version (clojure-version)
                                 :mimetype "text/x-clojure"
                                 :file_extension ".clj"}
                 :banner "Clojupyters-0.1.0"
                 :help_links []}]
    (send-router-message S "kernel_info_reply" content message)))

(defmethod respond-to-message "shutdown_request"
  [{:keys [states nrepl-comm] :as S} _ message]
  (log/debug "respond-to shutdown_request: " :S S :message message)
  (let [restart (get-in message message [:content :restart])
        server @(:nrepl-server nrepl-comm)
        content {:restart restart :status "ok"}]
    (reset! (:alive states) false)
    (nrepl.server/stop-server server)
    (send-router-message S "shutdown_reply" content message)
    (Thread/sleep 100)))

(defmethod respond-to-message "is_complete_request"
  [S _ message]
  (log/debug "respond-to is_complete_request: " :S S :message message)
  (let [content (if (complete/complete? (get-in message [:content :code]))
                  {:status "complete"}
                  {:status "incomplete"})]
    (send-router-message S "is_complete_reply" content message)))

(defmethod respond-to-message "complete_request"
  [{:keys [nrepl-comm] :as S} _ message]
  (log/debug "respond-to complete_request" :S S :message message)
  (let [content (let [delimiters #{\( \" \% \space}
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
                   :status "ok"})]
    (send-router-message S "complete_reply" content message)))

(defmethod respond-to-message "history_request"
  [{:keys [states] :as S} _ message]
  (log/debug "respond-to history_request: " :S S :message message)
  (let [content  {:history (map #(vector (:session %) (:line %) (:source %))
                                (his/get-history (:history-session states)))}]
    (send-router-message S "history_reply" content message)))

(defmethod respond-to-message "inspect_request"
  [{:keys [zmq-comm nrepl-comm socket signer] :as S} _ message]
  (log/debug "respond-to inspect_request:" :S S :message message)
  (let [request-content (:content message)
        code (:code request-content)
        cursor_pos (:cursor_pos request-content)
        sym (tokenize/token-at code cursor_pos)
        result (if-let [doc (pnrepl/nrepl-doc nrepl-comm sym)]
                 (str/join "\n" (rest (str/split-lines doc)))
                 "")
        content (if (str/blank? result)
                  {:status "ok" :found false :metadata {} :data {}}
                  {:status "ok" :found true :metadata {}
                   :data {:text/html (str "<pre>" result "</pre>")
                          :text/plain (str result)}})]
    (send-router-message S "inspect_reply" content message)))

(defmethod respond-to-message :default
  [{:as S} msg-type message]
  (log/error "Message type" msg-type "not handled yet. Exiting.")
  (log/error "Message dump:" message)
  (System/exit -1))

(defn execute-request-handler
  []
  (let [execution-count (atom 1N)]
    (fn [{:keys [states zmq-comm nrepl-comm socket signer] :as S} _ message]
      (log/debug "exe-request-handler: " :S S :message message)
      (let [code		(get-in message [:content :code])
            silent?		(str/ends-with? code ";") ;; TODO: Find out what is this about?
            S-iopub 		(assoc S :socket :iopub-socket)]
        (log/debug (str "execute-exe-req-handler: " :message message))
        (send-message S-iopub "execute_input" (pyin-content @execution-count message) message)
        (let [nrepl-resp	(pnrepl/nrepl-eval nrepl-comm S code message)
              _ (log/debug "exe-req-handler :rnrepl-resp 1" :nrepl-resp nrepl-resp)
              {:keys [result ename traceback]}
              ,,		nrepl-resp
              err		(when ename {:status "error"
                                             :ename ename
                                             :evalue ""
                                             :execution_count @execution-count
                                             :traceback traceback})
              content 		(or err {:status "ok"
                                         :execution_count @execution-count
                                         :user_expressions {}})]
          (log/debug (str "exe-req-handler: " :nrepl-resp nrepl-resp))
          (send-router-message S "execute_reply" content message)
          (if err
            (send-message S-iopub "error" err message)
            (when-not silent?
              (log/debug (str "execute-req-handler: " :result result))
              (send-message S-iopub "execute_result" 
                            {:execution_count @execution-count
                             :data (cheshire/parse-string result true)
                             :metadata {}}
                            message)))
          (his/add-history (:history-session states) @execution-count code)
          (swap! execution-count inc))))))
