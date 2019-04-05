(ns clojupyter.kernel.jupyter
  (:require
   [clojupyter.kernel.util			:as u]))

(def PROTOCOL-VERSION "5.3")

(def COMM-CLOSE			"comm_close")
(def COMM-INFO-REPLY		"comm_info_reply")
(def COMM-INFO-REQUEST		"comm_info_request")
(def COMM-MSG			"comm_msg")
(def COMM-OPEN			"comm_open")
(def COMPLETE-REPLY		"complete_reply")
(def COMPLETE-REQUEST		"complete_request")
(def ERROR			"error")
(def EXECUTE-INPUT		"execute_input")
(def EXECUTE-REPLY		"execute_reply")
(def EXECUTE-REQUEST		"execute_request")
(def EXECUTE-RESULT		"execute_result")
(def HISTORY-REPLY		"history_reply")
(def HISTORY-REQUEST		"history_request")
(def INPUT-REQUEST		"input_request")
(def INSPECT-REPLY		"inspect_reply")
(def INSPECT-REQUEST		"inspect_request")
(def IS-COMPLETE-REPLY		"is_complete_reply")
(def IS-COMPLETE-REQUEST	"is_complete_request")
(def KERNEL-INFO-REPLY		"kernel_info_reply")
(def KERNEL-INFO-REQUEST	"kernel_info_request")
(def SHUTDOWN-REPLY		"shutdown_reply")
(def SHUTDOWN-REQUEST		"shutdown_request")

;;; ----------------------------------------------------------------------------------------------------
;;; MESSAGE ACCESSORS
;;; ----------------------------------------------------------------------------------------------------

(defn message-content		[message]	(get-in message [:content]))
(defn message-allow-stdin	[message]	(get-in message [:content :allow_stdin]))
(defn message-code		[message]	(get-in message [:content :code]))
(defn message-comm-id		[message]	(get-in message [:content :comm_id]))
(defn message-cursor-pos	[message]	(get-in message [:content :cursor_pos]))
(defn message-restart		[message]	(get-in message [:content :restart]))
(defn message-silent		[message]	(get-in message [:content :silent]))
(defn message-stop-on-error?	[message]	(get-in message [:content :stop_on_error]))
(defn message-store-history?	[message]	(if-let [[_ store?] (find (get message :content) :store_history)]
                                                  store?
                                                  true))
(defn message-user-expressions	[message]	(get-in message [:content :user_expressions]))
(defn message-value		[message]	(get-in message [:content :value]))
,,
(defn message-header		[message]	(get-in message [:header]))
(defn message-msg-type		[message]	(get-in message [:header :msg_type]))
(defn message-session		[message]	(get-in message [:header :session]))
(defn message-username		[message]	(get-in message [:header :username]))
,,
(defn message-delimiter		[message]	(get-in message [:delimiter]))
(defn message-envelope		[message]	(get-in message [:envelope]))
(defn message-parent-header	[message]	(get-in message [:parent-header]))
(defn message-signature		[message]	(get-in message [:signature]))

(defn build-message
  [message]
  (when message
    {:envelope (message-envelope message)
     :delimiter (message-delimiter message)
     :signature (message-signature message)
     :header (u/parse-json-str (message-header message) keyword)
     :parent-header (u/parse-json-str (message-parent-header message) keyword)
     :content (u/parse-json-str (message-content message) keyword)
     ::zmq-raw-message message}))
