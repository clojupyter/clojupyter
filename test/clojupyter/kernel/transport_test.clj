(ns clojupyter.kernel.transport-test
  (:require
   [clojure.walk				:as walk]
   [midje.sweet							:refer [fact =>]]
   ,,
   [clojupyter.kernel.transport			:as T]))

(def UNHANDLED (fn [{:keys [transport parent-message]}]
                 (T/send-req transport "unhandled:unknown_message" {:msg parent-message})
                 :unhandled))

(defprotocol TransportAux
  (sent [_]))

(defrecord test-transport [_input _output]
  T/Transport
  (T/send* [_ socket msgtype message]
    (swap! _output (fn [o] (update-in o [socket] #(conj (or % []) {:msgtype msgtype :message message}))))
    nil)
  (T/receive* [_ socket]
    (let [v (first (get @_input socket))]
      (swap! _input #(update-in % [socket] next))
      v))
  TransportAux
  (sent [_] @_output))

(alter-meta! #'->test-transport #(assoc % :private true))
(alter-meta! #'fact #(assoc % :style/indent :defn))

(defn mktt
  "Instantiate `test-transport`."
  [input]
  (assert (map? input))
  (->test-transport (atom input) (atom {})))

(defn without-transport
  [v]
  (walk/postwalk #(if (map? %) (dissoc % :transport) %) v))

(defn test-ctx
  ([message parent-message] (test-ctx message parent-message {}))
  ([message parent-message transport-input]
   (let [transport	(mktt transport-input)
         ctx		(-> message
                            (T/bind-transport transport)
                            (T/bind-parent-message parent-message))]
     ctx)))

;;; ----------------------------------------------------------------------------------------------------
;;; TESTS
;;; ----------------------------------------------------------------------------------------------------

(fact "send-{stdin,iopub,req} works (test-transport supports send)"
  (let [tt (mktt {})]
    (T/send-stdin tt :mt-a {:x 1})
    (T/send-stdin tt :mt-b {:x 2})
    (T/send-iopub tt :mt-c {:x 3})
    (T/send-iopub tt :mt-d {:x 4})
    (T/send-req tt  :mt-e {:x 5})
    (T/send-req tt  :mt-f {:x 6})
    (sent tt))
  =>
  {:stdin [{:msgtype :mt-a, :message {:x 1}}
           {:msgtype :mt-b, :message {:x 2}}],
   :iopub [{:msgtype :mt-c, :message {:x 3}},
           {:msgtype :mt-d, :message {:x 4}}],
   :req   [{:msgtype :mt-e, :message {:x 5}},
           {:msgtype :mt-f, :message {:x 6}}]})

(fact "receive{req,stdin} work (test-transport supports receive)"
  (let [tt (mktt {:req [:a :b] :stdin [:x :y]})]
    [(T/receive-req tt) (T/receive-stdin tt)
     (T/receive-req tt) (T/receive-stdin tt)])
  =>
  [:a :x :b :y])


(fact "handler-when uses predicate"
  (let [h ((T/handler-when odd? (fn [msg] [:handled msg]))
           (fn [msg] [:unhandled msg]))]
    [(h 0) (h 1)])
  =>
  [[:unhandled 0] [:handled 1]])

(fact "parent-msgtype-pred returns a function which checks message type"
  (let [mk (fn [mt] {:parent-message {:header {:msg_type mt}}})]
    [((T/parent-msgtype-pred "my-msgtype") (mk "my-msgtype"))
     ((T/parent-msgtype-pred "my-msgtype") (mk "not-my-msgtype"))])
  =>
  [true false])

(fact "transport-layer functions work"
  (let [tt (mktt {})
        h0 (fn [{:keys [transport parent-message] :as ctx}] (T/send-req transport "mt-1" {:h0 parent-message}))
        tl (T/transport-layer
            {:send-fn (fn [{:keys [transport] :as ctx} socket resp-msgtype response]
                        (T/send* transport socket resp-msgtype (assoc response :sending 1)))
             :message-fn (fn [ctx parent-message]
                           (assoc ctx :parent-message {:mapped parent-message}))})
        handle (tl h0)
        msg {:x 0}
        ctx (-> {} (T/bind-transport tt) (T/bind-parent-message msg))]
    (handle ctx)
    (->> (sent (:transport ctx)) :req (mapv :message)))
  =>
  [{:h0 {:mapped {:x 0}}, :sending 1}])

(fact "response-mapping-transport transforms responses sent"
  (let [tt (mktt {})
        base-handler (fn [{:keys [transport parent-message] :as ctx}] (T/send-req transport "mt-1" parent-message))
        wrapout-inc-1 (T/response-mapping-transport (fn [ctx msg] (assoc msg :y (-> msg :x inc))))
        wrapout-inc-2 (T/response-mapping-transport (fn [ctx msg] (assoc msg :z (-> msg :y inc))))
        handler ((comp wrapout-inc-2 wrapout-inc-1) base-handler)
        msg {:x 0}]
    (handler (-> {} (T/bind-transport tt) (T/bind-parent-message msg)))
    (->> tt sent :req (map :message) first (#(dissoc % :transport))))
  =>
  {:x 0, :y 1, :z 2})

