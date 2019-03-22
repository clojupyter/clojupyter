(ns clojupyter.misc.util
  (:require
   [cheshire.core				:as cheshire]
   [clojure.pprint				:as pp]
   [clojure.spec.alpha				:as s]
   [java-time					:as jtm]
   [net.cgrand.sjacket.parser			:as p]
   [pandect.algo.sha256						:refer [sha256-hmac]]
   [taoensso.timbre				:as log]
   [zprint.core					:as zp]
   ,,
   [clojupyter.misc.spec			:as sp]
   )
  (:import [java.time.format DateTimeFormatter]))

(defn ctx?
  [v]
  (s/valid? ::sp/ctx v))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn merge-docmeta
  "Add the values for keys `:doc` and `:arglists` from `refvar` to the
  meta of `var`."
  [var refvar]
  (alter-meta! var #(merge % (select-keys (meta refvar) [:doc :arglists]))))

(def json-str cheshire/generate-string)
(def parse-json-str cheshire/parse-string)

(defn >bytes
  [v]
  (cond
    (= (type v) (Class/forName "[B"))	v
    (string? v)	(.getBytes v)
    true	(.getBytes (json-str v))))

(defn now []
 (->> (.withNano (java.time.ZonedDateTime/now) 0)
      (jtm/format DateTimeFormatter/ISO_OFFSET_DATE_TIME)))

(defn pp-str
  [v]
  (with-out-str (pp/pprint v)))

(defn rcomp
  [& fs]
  (apply comp (reverse fs)))

(defmacro with-debug-logging
  [[& args] & forms]
  `(let [uuid# (str "#" (subs (uuid) 0 8))]
     (log/debug uuid# "START" ~@args)
     (let [res# (do ~@forms)]
       (log/debug uuid# "END"  )
       res#)))

(def reformat-form
  (rcomp read-string zp/zprint-str pr-str println))

(defn stream-to-string
  [map]
  (let [repr (java.io.StringWriter.)]
    (cheshire/generate-stream map repr)
    (str repr)))

(defn safe-byte-array-to-string
  [array]
  (apply str (map #(if (pos? %) (char %) \!) (seq array))))

(defn set-var-indent!
  [indent-style var]
  (alter-meta! var #(assoc % :style/indent indent-style)))

(defn set-var-private!
  [var]
  (alter-meta! var #(assoc % :private true)))

(defn make-signer-checker
  [key]
  (let [mkchecker (fn [signer]
                    (fn [{:keys [signature header parent-header metadata content]}]
                      (let [our-signature (signer header parent-header metadata content)]
                        (= our-signature signature))))
        signer	(if (empty? key)
                  (constantly "")
                  (fn [header parent metadata content]
                    (let [res (apply str (map json-str [header parent metadata content]))]
                      (sha256-hmac res key))))]
    [signer (mkchecker signer)]))

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
     :header (parse-json-str (message-header message) keyword)
     :parent-header (parse-json-str (message-parent-header message) keyword)
     :content (parse-json-str (message-content message) keyword)
     ::zmq-raw-message message}))
