(ns clojupyter.kernel.handle-event.interrupt-test
  (:require [clojupyter.kernel.cljsrv :as srv]
            [clojupyter.kernel.handle-event :as he]
            [clojupyter.kernel.handle-event.shared-ops :as sh]
            [clojupyter.log :as log]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.test-shared :as ts]
            [clojure.spec.alpha :as s]
            [midje.sweet :as midje :refer [=> fact]]))

(fact
 "interrupt-request yields an interrupt-reply"
 (log/with-level :error
   (with-open [srv (srv/make-cljsrv)]
     (let [msg ((ts/s*message-header msgs/INTERRUPT-REQUEST)
                (msgs/interrupt-request-content))
           port :control_port
           req {:req-message msg, :req-port port, :cljsrv srv}
           {:keys [enter-action leave-action] :as rsp} (he/calculate-response req)
           {:keys [message-to msgtype message]} (sh/first-spec leave-action)]
       (and (sh/single-step-action? enter-action)
            (sh/successful-action? enter-action)
            (sh/single-step-action? leave-action)
            (= msgtype msgs/INTERRUPT-REPLY)
            (= message-to port)
            (s/valid? ::msp/interrupt-reply-content message)))))
 => true)
