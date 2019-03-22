(ns clojupyter.middleware.base
  (:require
   [clojure.pprint			:as pp		:refer [pprint]]
   [clojure.spec.alpha			:as s]
   [taoensso.timbre			:as log]
   ,,
   [clojupyter.misc.config		:as cfg]
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.transport		:as tp		:refer [handler-when transport-layer
                                                                response-mapping-transport
                                                                parent-msgtype-pred]]
   [clojupyter.misc.spec		:as sp]
   [clojupyter.kernel.state		:as state]
   [clojupyter.misc.util		:as u]
   ))

(def BANNER "clojupyter-0.1.1-SNAPSHOT")

(defn jupyter-message
  [{:keys [parent-message signer] :as ctx} resp-socket resp-msgtype response]
  (let [session-id	(u/message-session parent-message)
        header 		{:date (u/now)
                         :version jup/PROTOCOL-VERSION
                         :msg_id (u/uuid)
                         :username "kernel"
                         :session session-id
                         :msg_type resp-msgtype}
        parent-header	(u/message-header parent-message)
        metadata	{}
        ]
    {:envelope (if (= resp-socket :req) (u/message-envelope parent-message) [(u/>bytes resp-msgtype)])
     :delimiter "<IDS|MSG>"
     :signature (signer header parent-header metadata response)
     :header header
     :parent-header parent-header
     :metadata metadata
     :content response}))

(defn encode-jupyter-message
  [jupyter-message]
  (let [segment-order	(juxt :envelope :delimiter :signature :header :parent-header :metadata :content)
        segments	(segment-order jupyter-message)
        envelope	(first segments)
        payload		(rest segments)]
    (vec (concat envelope
                 (for [p payload]
                   (u/>bytes p))))))

;;; ----------------------------------------------------------------------------------------------------
;;; MIDDLEWARE FUNCTIONS
;;; ----------------------------------------------------------------------------------------------------

(def wrapin-verify-request-bindings
  (handler-when (complement u/ctx?)
    (fn [ctx]
      (let [s (s/explain-str ::sp/ctx ctx)]
        (throw (ex-info (str "Bad ctx: " s)
                 {:ctx ctx, :explain-str s}))))))

(def wrapin-bind-msgtype
  (fn [handler]
   (fn [ctx]
     (handler
      (assoc ctx :msgtype (get-in ctx [:parent-message :header :msg_type]))))))

(def wrapout-construct-jupyter-message
  (transport-layer
   {:send-fn (fn [{:keys [transport] :as ctx} socket resp-msgtype response]
               (tp/send* transport socket resp-msgtype
                         (assoc response :jupyter-message (jupyter-message ctx socket resp-msgtype response))))}))

(def wrapout-encode-jupyter-message
  (response-mapping-transport
   (fn [ctx {:keys [jupyter-message] :as response}]
     (assoc response :encoded-jupyter-message (encode-jupyter-message jupyter-message)))))

(def wrap-busy-idle
  (fn [handler]
    (fn [{:keys [transport] :as ctx}]
      (let [update-status #(tp/send-iopub transport "status" {:execution_state %})]
        (update-status "busy")
        (handler ctx)
        (update-status "idle")))))

(def wrap-kernel-info-request
  (handler-when (parent-msgtype-pred jup/KERNEL-INFO-REQUEST)
   (fn [{:keys [transport]}]
     (tp/send-req transport jup/KERNEL-INFO-REPLY
       {:status "ok"
        :protocol_version jup/PROTOCOL-VERSION
        :implementation "clojupyter"
        :language_info {:name "clojure"
                        :version (clojure-version)
                        :mimetype "text/x-clojure"
                        :file_extension ".clj"}
        :banner BANNER
        :help_links []}))))

(def wrap-shutdown-request
  (handler-when (parent-msgtype-pred jup/SHUTDOWN-REQUEST)
   (fn [{:keys [transport parent-message]}]
     (tp/send-req transport jup/SHUTDOWN-REPLY
       {:restart (u/message-restart parent-message) :status "ok"}))))

;;; ----------------------------------------------------------------------------------------------------
;;; HANDLER
;;; ----------------------------------------------------------------------------------------------------

(def not-implemented-handler
  (fn [{:keys [msgtype parent-message]}]
    (do (log/error (str "Message type " msgtype " not implemented: Ignored."))
        (log/debug "Message dump:\n" (u/pp-str parent-message)))))
