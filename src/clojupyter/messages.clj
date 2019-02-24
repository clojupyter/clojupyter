(ns clojupyter.messages
  (:require
   [cheshire.core			:as cheshire]
   [clojure.pprint			:as pp]
   [clojure.string			:as str]
   [nrepl.core				:as nrepl]
   [nrepl.misc				:as nrepl.misc]
   [nrepl.server			:as nrepl.server]
   [pandect.algo.sha256					:refer [sha256-hmac]]
   [taoensso.timbre			:as log]
   [zeromq.zmq				:as zmq]
   ,,
   [clojupyter.history			:as his]
   [clojupyter.protocol.nrepl-comm	:as pnrepl]
   [clojupyter.protocol.zmq-comm	:as pzmq]
   [clojupyter.util			:as u]))

(def protocol-version "5.0")

(defn message-code		[message]	(get-in message [:content :code]))
(defn message-comm-id		[message]	(get-in message [:content :comm_id]))
(defn message-content		[message]	(get-in message [:content]))
(defn message-cursor-pos	[message]	(get-in message [:content :cursor_pos]))
(defn message-delimiter		[message]	(get-in message [:delimiter]))
(defn message-header		[message]	(get-in message [:header]))
(defn message-idents		[message]	(get-in message [:idents]))
(defn message-msg-type		[message]	(get-in message [:header :msg_type]))
(defn message-parent-header	[message]	(get-in message [:parent-header]))
(defn message-restart		[message]	(get-in message [:content :restart]))
(defn message-session		[message]	(get-in message [:header :session]))
(defn message-signature		[message]	(get-in message [:signature]))
(defn message-username		[message]	(get-in message [:header :username]))
(defn message-value		[message]	(get-in message [:content :value]))

(def as-json cheshire/generate-string)

(defn- new-header
  [msg_type session-id]
  {:date (u/now)
   :version protocol-version
   :msg_id (u/uuid)
   :username "kernel"
   :session session-id
   :msg_type msg_type})

(defn- send-message-piece
  [zmq-comm socket msg]
  (log/debug "Sending part: " (u/pp-str msg))
  (pzmq/zmq-send zmq-comm socket (.getBytes msg) zmq/send-more)
  (log/debug "Finished sending part"))

(defn- finish-message
  [zmq-comm socket msg]
  (log/debug "Sending message: " (u/pp-str msg))
  (pzmq/zmq-send zmq-comm socket (.getBytes msg))
  (log/debug "Finished sending all"))

(defn- send-parts
  [{:keys [zmq-comm socket signer] :as S} msg-type content parent-message]
  (let [session-id	(message-session parent-message)
        header		(as-json (new-header msg-type session-id))
        parent-header	(as-json (message-header parent-message))
        metadata	(as-json {})
        content		(as-json content)]
    (log/debug "send-parts: " :S S :msg-type msg-type :content content
               :header header :parent-header  parent-header :metadata metadata)
    (doall
     (map (partial send-message-piece zmq-comm socket)
          ["<IDS|MSG>" (signer header parent-header metadata content) 
           header parent-header metadata]))))

(defn- send-idents
  [{:keys [zmq-comm socket] :as S} message]
  (log/debug "send-idents: " :S S :message message)
  (doseq [ident (message-idents message)]               ;
    (pzmq/zmq-send zmq-comm socket ident zmq/send-more)))

(defn- send-msg-type
  [{:keys [zmq-comm socket] :as S} msg-type]
  (log/debug "send-msg-type: " :S S :msg-type msg-type)
  (send-message-piece zmq-comm socket msg-type))

(defn- send-router-message
  [{:keys [zmq-comm socket signer] :as S} msg-type content parent-message]
  (log/info "send-router-message sending: " (u/pp-str content))
  (send-idents S parent-message)
  (send-parts S msg-type content parent-message)
  (finish-message zmq-comm socket (as-json content))
  (log/info "send-router-message: message sent"))

(defn send-message
  [{:keys [zmq-comm socket signer] :as S} msg-type content parent-message]
  (log/info "send-message sending: " (u/pp-str content))
  (send-msg-type S msg-type)
  (send-parts S msg-type content parent-message)
  (finish-message zmq-comm socket (as-json content))
  (log/info "send-message: message sent"))

(defn make-message-signer
  [key]
  (if (empty? key)
    (constantly "")
    (fn [header parent metadata content]
      (sha256-hmac (str header parent metadata content) key))))

(defn make-message-checker
  [signer]
  (fn [{:keys [signature header parent-header metadata content]}]
    (let [our-signature (signer header parent-header metadata content)]
      (= our-signature signature))))

(defn parse-message
  [message]
  {:idents (message-idents message)
   :delimiter (message-delimiter message)
   :signature (message-signature message)
   :header (cheshire/parse-string (message-header message) keyword)
   :parent-header (cheshire/parse-string (message-parent-header message) keyword)
   :content (cheshire/parse-string (message-content message) keyword)})

;; Message contents

(defn status-content
  [status]
  {:execution_state status})

(defn pyin-content
  [execution-count message]
  {:execution_count execution-count, :code (message-code message)})

(defn input-request
  [S parent-message]
  (let [content  {:prompt ">> ", :password false}]
    (send-router-message (assoc S :socket :stdin-socket) "input_request" content parent-message)))

;;; ----------------------------------------------------------------------------------------------------
;;; RESPOND-TO-MESSAGE
;;; ----------------------------------------------------------------------------------------------------

(defmacro defresponse [bindings msg-type form]
  `(defmethod respond-to-message ~msg-type ~bindings
     (do (log/debug :respond-to-message " " ~msg-type " " :arglist " " ~bindings)
         ~form
         (log/debug :respond-to-message ~msg-type :done))))

(defmulti respond-to-message (fn [_ msg-type _] msg-type))

(defresponse [S _ message] "comm_info_request"
  (send-message S "comm_info_reply" {:comms {:comm_id {:target_name ""}}} message))

(defresponse [S _ message] "comm_msg"
  (send-message S "comm_msg_reply" {} message))

(defresponse [S _ message] "comm_open"
  (send-router-message S "comm_close" {:comm_id (message-comm-id message), :data {}} message))

(defresponse [S _ message] "kernel_info_request"
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

(defresponse [{:keys [states nrepl-comm] :as S} _ message] "shutdown_request"
  (send-router-message S "shutdown_reply" {:restart (message-restart message) :status "ok"} message))

(defresponse [S _ message] "is_complete_request"
  (send-router-message S "is_complete_reply"
                       (if (u/complete? (message-code message))
                         {:status "complete"}
                         {:status "incomplete"}) message))

(defresponse [{:keys [nrepl-comm] :as S} _ message] "complete_request"
  (let [content (let [delimiters #{\( \" \% \space}
                      cursor_pos (message-cursor-pos message)
                      codestr (subs (message-code message) 0 cursor_pos)
                      sym (as-> (reverse codestr) $
                            (take-while #(not (contains? delimiters %)) $)
                            (apply str (reverse $)))]
                  {:matches (pnrepl/nrepl-complete nrepl-comm sym)
                   :metadata {:_jupyter_types_experimental []}
                   :cursor_start (- cursor_pos (count sym))
                   :cursor_end cursor_pos
                   :status "ok"})]
    (send-router-message S "complete_reply" content message)))

(defresponse [{:keys [states] :as S} _ message] "history_request"
  (let [content {:history (map #(vector (:session %) (:line %) (:source %))
                               (his/get-history (:history-session states)))}]
    (send-router-message S "history_reply" content message)))

(defresponse [{:keys [zmq-comm nrepl-comm socket signer] :as S} _ message] "inspect_request"
  (let [code (message-code message)
        cursor_pos (message-cursor-pos message)
        sym (u/token-at code cursor_pos)
        result (if-let [doc (pnrepl/nrepl-doc nrepl-comm sym)]
                 (str/join "\n" (rest (str/split-lines doc)))
                 "")
        content (if (str/blank? result)
                  {:status "ok" :found false :metadata {} :data {}}
                  {:status "ok" :found true :metadata {}
                   :data {:text/html (str "<pre>" result "</pre>")
                          :text/plain (str result)}})]
    (send-router-message S "inspect_reply" content message)))

(defresponse [{:as S} msg-type message] :default
  (do (log/error "Message type " msg-type " not implemented. Exiting.")
      (log/error "Message dump:" (u/pp-str message))
      (System/exit -1)))

(defn execute-request-handler
  []
  (let [execution-count (atom 1N)]
    (fn [{:keys [states zmq-comm nrepl-comm socket signer] :as S} _ message]
      (log/debug "exe-request-handler: " :S S :message message)
      (let [code		(message-code message)
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
