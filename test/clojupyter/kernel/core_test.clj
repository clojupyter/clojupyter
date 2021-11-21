(ns clojupyter.kernel.core-test
  (:require [clojupyter.kernel.cljsrv :as cljsrv]
            [clojupyter.kernel.core :as core]
            [clojupyter.kernel.jup-channels :refer [jup-channel make-jup]]
            [clojupyter.shutdown :as shutdown]
            [clojupyter.log :as log]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.messages-generators-test :as mg]
            [clojupyter.state :as state]
            [clojupyter.test-shared :as ts]
            [clojupyter.test-shared-generators :as shg]
            [clojupyter.util :as u]
            [clojure.core.async :as async :refer [chan >!! <!!]]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [io.simplect.compose :refer [>->> >>-> C P p]]
            [midje.sweet :refer [fact =>]]))

(defn on-outbound-channels
  "Retrieve messages sent on outbound channels in `jup`.  Returns a vector of 2-tuples each consisting of
  the channel keyword and the data retrieved."
  ([jup]
   (on-outbound-channels jup {}))
  ([jup {:keys [timeout maxiter chan-kws] :as opts}]
   (let [chan-kws (or chan-kws #{:control_port :shell_port :iopub_port})
         timeout (or timeout 250)
         maxiter (or maxiter 25)
         chanmap (->> chan-kws
                      (map (fn [kw] [(jup-channel jup kw :outbound) kw]))
                      (into {}))
         chs (keys chanmap)
         timeout-ch (async/timeout timeout)]
     (loop [acc [], iter maxiter]
       (let [[msg rcvchan] (async/alts!! (conj chs timeout-ch))]
         (if (or (nil? msg) (not (pos? iter)))
           acc
           (recur (conj acc msg) (dec iter))))))))

(fact
 "We can send and receive message to/from the kernel via channels"
 (let [N 10]
   (log/with-level :error
     (let [term (shutdown/make-terminator 1)
           [ctrlin ctrlout shin shout ioin ioout stdin stdout] (repeatedly 6 #(chan 1))
           jup (make-jup ctrlin ctrlout shin shout ioin ioout stdin stdout)]
       (swap! state/STATE assoc :jup jup :term term)
       (shutdown/initiating-shutdown-on-exit [:test term]
         (with-open [cljsrv (cljsrv/make-cljsrv)]
           (core/run-kernel jup term cljsrv)
           (let [{:keys [msgtype content]} (last (gen/sample mg/g-kernel-info-request-content N))
                 _ (assert (= msgtype msgs/KERNEL-INFO-REQUEST))
                 hdr (last (gen/sample (mg/g-message-header msgtype) N))
                 phdr (last (gen/sample (mg/g-message-header msgs/COMM-MSG) N))
                 envelope []
                 signature (byte-array [])
                 metadata {}
                 buffers []
                 reqmsg (msgs/make-jupmsg envelope signature hdr phdr metadata content buffers)
                 inbound-msg (msgs/jupmsg->kernelreq :shell_port reqmsg)
                 rspmsgs (do (>!! shin inbound-msg)
                             (Thread/sleep 10)
                             (on-outbound-channels jup))
                 _ (assert (= (count rspmsgs) 3))
                 non-status-msgs (->> rspmsgs (remove (C :rsp-msgtype (p = "status"))))
                 _ (assert (= (count non-status-msgs) 1))
                 {:keys [rsp-msgtype rsp-content rsp-socket req-message] :as rspmsg} (first non-status-msgs)]
             (and (= req-message reqmsg)
                  (= rsp-msgtype msgs/KERNEL-INFO-REPLY)
                  (= rsp-socket :shell_port)
                  (s/valid? ::msp/kernel-info-reply-content rsp-content))))))))
 => true)
