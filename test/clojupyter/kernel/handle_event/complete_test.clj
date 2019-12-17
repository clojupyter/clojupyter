(ns clojupyter.kernel.handle-event.complete-test
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
 "complete-request for 'printl' yields a complete-reply with more than 6 matches"
 (log/with-level :error
   (with-open [srv (srv/make-cljsrv)]
     (let [msg ((ts/s*message-header msgs/COMPLETE-REQUEST)
                (msgs/complete-request-content "(printl" 6))
           port :shell_port
           req {:req-message msg, :req-port port, :cljsrv srv}
           {:keys [enter-action leave-action] :as rsp} (he/calculate-response req)
           {:keys [message-to msgtype message]} (sh/first-spec leave-action)]
       (and (sh/single-step-action? enter-action)
            (sh/successful-action? enter-action)
            (sh/single-step-action? leave-action)
            (= msgtype msgs/COMPLETE-REPLY)
            (= message-to port)
            (s/valid? ::msp/complete-reply-content message)
            (let [matches (get message :matches)]
              (and (vector? matches) (-> matches count (> 6))))))))
 => true)
