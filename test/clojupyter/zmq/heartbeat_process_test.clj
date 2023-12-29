(ns clojupyter.zmq.heartbeat-process-test
  (:require
   [clojure.core.async          :as async]
   [midje.sweet             :as midje       :refer [fact =>]]
   ,,
   [clojupyter.log :as log]
   [clojupyter.test-shared      :as ts]
   [clojupyter.shutdown         :as shutdown]
   [clojupyter.state            :as state]
   [clojupyter.util         :as u]
   [clojupyter.util-actions     :as u!]
   [clojupyter.zmq          :as cjpzmq]
   [clojupyter.zmq-util         :as zutil]
   [clojupyter.zmq.heartbeat-process    :as hb])
  (:import [org.zeromq ZMQException]))

(state/ensure-initial-state!)

(defn- ^{:style/indent :defn} retry-on-ZMQException
  [max-tries f]
  (let [tag (gensym)]
    (loop [tries (dec max-tries)]
      (let [result (try (f) (catch ZMQException _ tag))]
        (if (= result tag)
          (if (pos? tries)
            (recur (dec tries))
            ::failed)
          result)))))

(fact
 "heartbeat terminates on term-ch signal"
 (log/with-level :error
   (retry-on-ZMQException 5
                          #(let [addr (str "tcp://localhost:" (+ 50000 (rand-int 15000)))
                                 ztx (state/zmq-context)
                                 term (shutdown/make-terminator 1)
                                 term-sig-ch (async/chan 1)
                                 timeout (async/timeout 500)]
                             (hb/start-hb ztx addr term {:term-signal-ch term-sig-ch})
                             (shutdown/initiate term)
                             (let [[from-hb _] (async/alts!! [term-sig-ch timeout])]
                               from-hb))))
 => :hb-terminating)

(fact
 "heartbeat responds to messages"
 (log/with-level :error
   (retry-on-ZMQException 5
                          #(let [addr (str "tcp://localhost:" (+ 50000 (rand-int 15000)))
                                 ztx (state/zmq-context)
                                 term (shutdown/make-terminator 1)
                                 term-sig-ch (async/chan 1)
                                 sock (doto (zutil/zsocket ztx :req) (.connect addr))]
                             (try (hb/start-hb ztx addr term {:term-signal-ch term-sig-ch})
                                  (Thread/sleep 500)
                                  (let [msg (byte-array [1 2 3])]
                                    (.send sock msg)
                                    (= (into [] msg) (into [] (.recv sock))))
                                  (finally
                                    (shutdown/initiate term)
                                    (.close sock))))))
 => true)
