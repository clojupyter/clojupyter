(ns clojupyter.kernel.handle-event.kernel-info-test
  (:require [clojupyter.kernel.handle-event :as he]
            [clojupyter.kernel.handle-event.shared-ops :as sh]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.test-shared :as ts]
            [clojure.spec.alpha :as s]
            [midje.sweet :as midje :refer [=> fact]]))

(fact
 "kernel-info-request yields a relevant kernel-info-reply"
 (let [msg ((ts/s*message-header msgs/KERNEL-INFO-REQUEST)
            (msgs/kernel-info-request-content))
       port :shell_port
       req {:req-message msg, :req-port port}
       {:keys [enter-action leave-action] :as rsp} (he/calculate-response req)
       {:keys [message-to msgtype message]} (sh/first-spec leave-action)
       {:keys [status implementation language_info]} message
       {:keys [name file_extension]} language_info]
   (and (sh/empty-action? enter-action)
        (sh/single-step-action? leave-action)
        (= msgtype msgs/KERNEL-INFO-REPLY)
        (= message-to port)
        (s/valid? ::msp/kernel-info-reply-content message)
        (= status "ok")
        (= implementation "clojupyter")
        (= name "clojure")
        (= file_extension ".clj")))
 => true)
