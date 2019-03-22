(ns clojupyter.transport
  (:refer-clojure :exclude [send])
  (:require
   [clojupyter.misc.util			:as u]))

(defprotocol Transport
  (send* [_ socket msgtype message]
    "Send `message` of type `msgtype` on `socket`.  Not intended to be
    used directly, use `send-req`, `send-stdin`, and `send-iopub`
    instead.")
  (receive* [_ socket]
    "Read full Jupyter message from `socket`.  Not intended to be used directly,
    use `receive-stdin` or `receive-req` instead."))

(defn ^{:style/indent :defn} send-req
  [transport msgtype message]
  (send* transport :req msgtype message))

(defn ^{:style/indent :defn} send-stdin
  [transport msgtype message]
  (send* transport :stdin msgtype message))

(defn ^{:style/indent :defn} send-iopub
  [transport msgtype message]
  (send* transport :iopub msgtype message))

(defn receive-stdin
  [transport]
  (receive* transport :stdin))

(defn receive-req
  [transport]
  (receive* transport :req))

(defn ^{:style/indent :defn} bind-parent-message
  [transport parent-message]
  (assoc transport :parent-message parent-message))

(defn ^{:style/indent :defn} bind-transport
  [message transport]
  (assoc message :transport transport))

(defn transport-layer
  "Defines middleware which transforms what is sent to the transport
  from lower layers.  `send-fn` must be a function taking the 4
  arguments: `message`, `socket`, `msgtype`, and `response`.
  `receive-fn` must be a function taking 2 arguments: `message` and
  `socket`. `message-fn` must be a function taking 1 argument:
  `message` and must return the ctx to be provided to the lower
  layer."
  [{:keys [send-fn receive-fn message-fn]}]
  (let [send-fn		(or send-fn	(fn [{:keys [transport]} socket msgtype message]
                                          (send* transport socket msgtype message)))
        receive-fn	(or receive-fn	(fn [{:keys [transport]} socket]
                                          (receive* transport socket)))
        message-fn	(or message-fn	(fn [ctx _] ctx))]
    (fn [handler]
      (fn [{:keys [parent-message] :as ctx}]
        (handler
         (bind-transport (message-fn ctx parent-message)
           (reify Transport
             (send* [_ socket msgtype response]
               (send-fn ctx socket msgtype response))
             (receive* [_ socket]
               (receive-fn ctx socket)))))))))

(defn response-mapping-transport
  "Returns a [[transport-layer]] which transforms the response being
  passed back.  `f` must be a function taking 2 arguments, the ctx
  that caused the response, and the response being handed back, and
  must return the transformed response."
  [f]
  (transport-layer
    {:send-fn (fn [{:keys [transport] :as ctx} socket resp-msgtype response]
                (send* transport socket resp-msgtype (f ctx response)))}))

(defn request-mapping-transport
  "Returns a [[transport-layer]] which transforms the request being
  handled.  `f` must be a function taking a single argument: the ctx
  that caused the response."
  [f]
  (transport-layer {:message-fn f}))

(defn handler-when
  [pred handler]
  (fn [handler']
   (fn [message]
     ((if (pred message) handler handler') message))))

(defn parent-msgtype-pred
  [msgtype]
  (fn [{:keys [parent-message]}]
    (= (u/message-msg-type parent-message) msgtype)))

(map (partial u/set-var-indent! :defn)
     [#'send* #'receive* #'bind-parent-message #'bind-transport
      #'transport-layer #'handler-when])
