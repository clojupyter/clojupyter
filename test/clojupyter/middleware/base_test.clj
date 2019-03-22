(ns clojupyter.middleware.base-test
  (:require
   [clojure.spec.alpha				:as s]
   [midje.sweet							:refer :all]
   [nrepl.core					:as nrepl]
   ,,
   [clojupyter.kernel.state			:as state]
   [clojupyter.middleware			:as M]
   [clojupyter.middleware.base			:as B]
   [clojupyter.misc.jupyter			:as jup]
   [clojupyter.misc.spec			:as sp]
   [clojupyter.misc.util			:as u]
   [clojupyter.nrepl.nrepl-server		:as clojupyter-nrepl-server]
   [clojupyter.nrepl.nrepl-comm			:as nrepl-comm]
   [clojupyter.nrepl.nrepl-comm			:as pnrepl]
   [clojupyter.transport			:as T]
   ,,
   [clojupyter.transport-test			:as TT]))


(def K-INFO-MSG {:envelope [(byte-array (range 5))],
              :delimiter "<IDS|MSG>",
              :signature "45e37bb540161a3e160d621200d2a2e3a7a28e5db525e987cd3746c2da5aaaaa",
              :header
              {:msg_id "0b36bc0c-e577a786e783383c0ea8d9c3",
               :msg_type "kernel_info_request",
               :username "xxx",
               :session "4fb56c501d3340d398bb8d3742cd9e50",
               :date "2019-03-12T06:35:48.091674Z",
               :version "5.3"},
              :parent-header {},
              :content {}})

(def SHUT-MSG {:envelope [(byte-array [0, 82, -49, -38, -60])],
               :delimiter "<IDS|MSG>",
               :signature
               "bb1429af3efcd927e400f0bdb6b0ef68b58f73a631e8e5d0ae4ce80a1592ecd3",
               :header
               {:msg_id "d9c02801-99decec6b80a64e98e65bf27",
                :msg_type "shutdown_request",
                :username "xxxx",
                :session "b5060fc6-0407636b6485ed7de77a55e7",
                :date "2019-03-12T16:55:55.744537Z",
                :version "5.3"},
               :parent-header {},
               :content {:restart false}})

(def UNKN-MSG {:envelope
               [(byte-array [52])],
               :delimiter "<IDS|MSG>",
               :signature
               "5a98d552a77da7f1595030a8771f4ef861a5ca406d13b1001d4acfa7a58ace88",
               :header
               {:msg_id "e93eb6ef140940518bb379ae9c962a7d",
                :username "username",
                :session "4fb56c501d3340d398bb8d3742cd9e50",
                :msg_type "unknown_request",
                :version "5.2"},
               :parent-header {},
               :content
               {}})

(fact "jupyter-message yields valid message"
  (->> (B/jupyter-message {:parent-message K-INFO-MSG, :signer (constantly (apply str (repeat 64 \a)))}
                          :req "msgtype" {:x 1})
       (s/valid? ::sp/jupyter-message))
  =>
  true)

(fact "jupyter messages encode"
 (let [enc (->> (B/jupyter-message {:parent-message K-INFO-MSG, :signer (constantly (apply str (repeat 64 \a)))}
                                    :req "msgtype" {:x 1})
                B/encode-jupyter-message)
       n-env (count (:envelope K-INFO-MSG))]
   [(count enc)					;; expected number
    (s/valid? (s/coll-of ::sp/byte-array) enc)  ;; we get byte-arrays
    (= (:envelope K-INFO-MSG) (take n-env enc))	;; enveloped correctly carried over
    ])
  =>
  [7 true true])

(def WO-T TT/without-transport)


(fact "wrapin-bind-msgtype binds message type"
  (let [H		(B/wrapin-bind-msgtype identity)
        ctx		(TT/test-ctx {:x 1} K-INFO-MSG)]
    (:msgtype (H ctx)))
  => "kernel_info_request")

(fact "kernel_info_request yields kernel_info_reply on :req"
  (let [H	((comp M/wrapin-bind-msgtype
                       M/wrap-base-handlers) TT/UNHANDLED)
        ctx	(TT/test-ctx {} K-INFO-MSG)]
    (H ctx)
    (-> ctx :transport TT/sent :req first ((juxt :msgtype
                                                 (u/rcomp :message :status)
                                                 (u/rcomp :message :implementation)
                                                 (u/rcomp :message :language_info :name)))))
  =>
  [jup/KERNEL-INFO-REPLY "ok" "clojupyter" "clojure"])

(fact "shutdown_request yields shutdown_reply on :req"
  (let [H	((comp M/wrapin-bind-msgtype
                       M/wrap-base-handlers) TT/UNHANDLED)
        ctx	(TT/test-ctx {} SHUT-MSG)]
    (H ctx)
    (-> ctx :transport TT/sent :req first ((juxt :msgtype
                                                 (u/rcomp :message :status)
                                                 (u/rcomp :message :restart)))))
  =>
  [jup/SHUTDOWN-REPLY "ok" false])

(fact "busy/idle messages are sent before and after responding to a message"
  (let [H	((comp M/wrapin-bind-msgtype
                       M/wrap-busy-idle
                       M/wrap-base-handlers) 	TT/UNHANDLED)
        ctx	(TT/test-ctx {} UNKN-MSG)]
    (H ctx)
    [(->> ctx :transport TT/sent :req first :msgtype)
     (->> ctx :transport TT/sent :iopub (mapv (u/rcomp :message :execution_state)))])
  =>
  ["unhandled:unknown_message" ["busy" "idle"]])

(fact "construction of zmq messages look ok incl envelope"
  (let [H	((comp M/wrapin-bind-msgtype
                       M/wrap-jupyter-messaging
                       M/wrap-base-handlers)	TT/UNHANDLED)
        [signer
         checker]	(u/make-signer-checker nil)
        ctx	(TT/test-ctx {:signer signer, :checker checker} K-INFO-MSG)]
    (H ctx)
    [(->> ctx :transport TT/sent :req
          (mapv (u/rcomp :message
                         :jupyter-message
                         (partial s/valid? ::sp/jupyter-message)))
          (reduce #(and %1 %2)))
     (let [get-envelope (u/rcomp :transport
                                 TT/sent
                                 :req
                                 first
                                 :message
                                 :jupyter-message
                                 :envelope
                                 (partial map seq))
           envelope (get-envelope ctx)]
       (= envelope
          (->> ctx :transport TT/sent :req first 
               ((u/rcomp :message
                         :encoded-jupyter-message
                         (partial take (count envelope))
                         (partial map seq))))))])
  =>
  [true true])


