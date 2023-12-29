(ns clojupyter.kernel.handle-event.inspect-test
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
 "inspect-request yields a relevant inspect-reply"
 (log/with-level :error
   (with-open [srv (srv/make-cljsrv)]
     (let [msg ((ts/s*message-header msgs/INSPECT-REQUEST)
                (msgs/inspect-request-content "(list 1 2 3)" 3))
           port :shell_port
           req {:req-message msg, :req-port port, :cljsrv srv}
           {:keys [enter-action leave-action] :as rsp} (he/calculate-response req)
           {:keys [message-to msgtype message]} (sh/first-spec leave-action)
           {:keys [status found data]} message
           {:keys [:text/html :text/plain]} data]
       (and (sh/single-step-action? enter-action)
            (sh/successful-action? enter-action)
            (sh/single-step-action? leave-action)
            (= msgtype msgs/INSPECT-REPLY)
            (= message-to port)
            (= found true)
            (= status "ok")
            (string? html)
            (string? plain)
            (s/valid? ::msp/inspect-reply-content message)))))
 => true)
