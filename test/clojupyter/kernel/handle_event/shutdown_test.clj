(ns clojupyter.kernel.handle-event.shutdown-test
  (:require [clojupyter.kernel.handle-event :as he]
            [clojupyter.kernel.handle-event.shared-ops :as sh]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.test-shared :as ts]
            [clojure.spec.alpha :as s]
            [midje.sweet :as midje :refer [=> fact]]))

(fact
 "shutdown-request yields a relevant shutdown-reply"
 (let [msg ((ts/s*message-header msgs/SHUTDOWN-REQUEST)
            (msgs/shutdown-request-content))
       port :shell_port
       req {:req-message msg, :req-port port}
       {:keys [enter-action leave-action]} (he/calculate-response req)
       {:keys [message-to msgtype message]} (sh/first-spec leave-action)]
   (and (sh/empty-action? enter-action)
        (sh/single-step-action? leave-action)
        (= msgtype msgs/SHUTDOWN-REPLY)
        (= message-to port)
        (s/valid? ::msp/shutdown-reply-content message)))
 => true)
