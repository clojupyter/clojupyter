(ns clojupyter.widgets.alpha-test
  (:require
   [clojupyter.kernel.comm-atom :as comm-atom]
   [clojupyter.kernel.core-test :as core-test]
   [clojupyter.kernel.jup-channels :as jup]
   [clojupyter.shutdown :as shutdown]
   [clojupyter.log :as log]
   [clojupyter.messages :as msgs]
   [clojupyter.state :as state]
   [clojupyter.util :as u]
   [clojupyter.util-actions :as u! :refer [uuid]]
   [clojupyter.widgets.alpha :as alpha]
   [clojure.core.async :as async]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :refer [instrument unstrument]]
   [io.simplect.compose :refer [def- c C p P >->> >>-> sdefn sdefn-]]
   [io.simplect.compose.action :as act :refer [action side-effect step]]
   [midje.sweet :as midje :refer [=> fact]]
   [clojupyter.test-shared :as ts]
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]))

(use 'clojure.pprint)

(defmacro with-setup
  [[jup reqmsg] & body]
  `(let [bufsize# 1000
         term# (shutdown/make-terminator 1)
         [ctrlin# ctrlout# shin# shout# ioin# ioout# stdin# stdout#] (repeatedly #(async/chan bufsize#))
         ~jup (jup/make-jup ctrlin# ctrlout# shin# shout# ioin# ioout# stdin# stdout#)
         ~reqmsg :reqmsg
         ctx# {:jup ~jup, :reqmsg :reqmsg}]
     (state/with-current-context [ctx#]
       ~@body)))


(fact
 "Basic COMM map operations appear to work"
 (with-setup [jup reqmsg]
   (let [crt (fn [state] (comm-atom/create jup reqmsg "target" (uuid) state))]
     (and
      ;; basic
      (let [comm (crt {})]
        (and (= {} @(crt {}))
             (= {:x 8} @(crt {:x 8}))
             (= {:a 1, :b 2} @(crt {:a 1 :b 2}))))
      ;; assoc, keys, vals, get, keyword-as-access, defaulting-get
      (let [comm (crt {})]
        (and (= {:x 1} @(assoc comm :x 1))
             (= #{:x} (into #{} (keys comm)))
             (= #{1} (into #{} (vals comm)))
             (= 1 (get comm :x))
             (= 1 (:x comm))
             (= :missing (get comm :not-there :missing))))
      ;; same, more keys
      (let [comm (crt {})]
        (and (= {:x 1 :y 2 :z 3} @(assoc comm :x 1, :y 2, :z 3))
             (= #{:x :y :z} (into #{} (keys comm)))
             (= #{1 2 3} (into #{} (vals comm)))
             (= #{[:x 1] [:y 2] [:z 3] (into #{} (seq comm))})
             (= 2 (get comm :y))))
      ;; assoc same key more than once
      (let [comm (crt {})]
        (and (= {:x 1 :y 3} @(assoc comm :x 1, :y 2, :y 3))
             (= #{:x :y} (into #{} (keys comm)))
             (= #{1 3} (into #{} (vals comm )))))
      ;; empty
      (let [comm (crt {:x 1})]
        (and (= 1 (get comm :x :missing))
             (do (empty comm)
                 (= :missing (get comm :x :missing)))))
      ;; dissoc
      (let [comm (crt {:x 1})]
        (and (= 1 (get comm :x :missing))
             (do (dissoc comm :x)
                 (= :missing (get comm :x :missing)))))
      ;; count
      (let [comm (crt {:x 1})]
        (and (= 1 (count (crt {:x 1})))
             (= 2 (count (crt {:a 1 :b 2})))))
      ;; equality
      (let [state {:x 1}
            comm1 (crt state)
            comm2 (crt state)
            uuid3 (uuid)
            comm3 (comm-atom/create jup reqmsg "target" uuid3 state)
            comm4 (comm-atom/create jup reqmsg "target" uuid3 state)
            ]
        (and (= comm1 comm1)
             (= comm2 comm2)
             (not= comm1 comm2)
             (not= comm2 comm1)
             (not= comm3 comm4)
             (not= comm4 comm3)
             (not= comm1 nil)
             (not= nil comm1)
             (not= comm2 27)
             (not= 27 comm2)
             (not= comm1 [])
             (not= true []))))))
 => true)
