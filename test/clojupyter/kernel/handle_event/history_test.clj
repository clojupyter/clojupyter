(ns clojupyter.kernel.handle-event.history-test
  (:require [clojupyter.kernel.handle-event :as he]
            [clojupyter.kernel.handle-event.shared-ops :as sh]
            [clojupyter.kernel.init :as init]
            [clojupyter.log :as log]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.test-shared :as ts]
            [clojure.spec.alpha :as s]
            [midje.sweet :as midje :refer [=> fact]]))

(fact
 "history-request yields a history-reply"
 (log/with-level :error
   (do (init/ensure-init-global-state!)
       (let [msg ((ts/s*message-header msgs/HISTORY-REQUEST) (msgs/history-request-content))
             port :shell_port
             req {:req-message msg, :req-port port}
             {:keys [enter-action leave-action]} (he/calculate-response req)
             {:keys [message-to msgtype message]} (sh/first-spec leave-action)]
         (and (sh/single-step-action? enter-action)
              (sh/successful-action? enter-action)
              (sh/single-step-action? leave-action)
              (= msgtype msgs/HISTORY-REPLY)
              (= message-to port)
              (s/valid? ::msp/history-reply-content message)))))
 => true)
