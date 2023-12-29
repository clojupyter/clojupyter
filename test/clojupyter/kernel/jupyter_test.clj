(ns clojupyter.kernel.jupyter-test
  (:require
   [clojure.spec.alpha          :as s]
   [midje.sweet             :as midje   :refer [fact =>]]
   ,,
   [clojupyter.messages         :as msgs]
   [clojupyter.messages-specs       :as msp]
   [clojupyter.test-shared      :as ts]))

(fact
 "comm-close-content conforms"
 (s/valid? ::msp/comm-close-content (msgs/comm-close-content "comm-id" {:x 1}))
 => true)

(fact
 "comm-info-reply-content conforms"
 (s/valid? ::msp/comm-info-reply-content
           (msgs/comm-info-reply-content {"comm-id" "target-name"}))
 => true)

(fact
 "comm-info-request-content conforms"
 (s/valid? ::msp/comm-info-request-content (msgs/comm-info-request-content "target-name"))
 => true)

(fact
 "comm-message-content conforms"
 (s/valid? ::msp/comm-message-content (msgs/comm-msg-content "comm-id" {:x 1}))
 => true)

(fact
 "comm-open conforms"
 (s/valid? ::msp/comm-open-content
           (msgs/comm-open-content "comm-id" {:z 1}
                                   {:target_module "target-module"
                                    :target_name "target-name "}))
 => true)

(fact
 "complete-reply-content conforms"
 (s/valid? ::msp/complete-reply-content (msgs/complete-reply-content ["abc" "def"] 0 5))
 => true)

(fact
 "execute-input-message-content conforms"
 (s/valid? ::msp/execute-input-content (msgs/execute-input-msg-content 1 "(list 1 2 3)"))
 => true)

(fact
 "execute-reply-content conforms"
 (s/valid? ::msp/execute-reply-content (msgs/execute-reply-content "ok" 1))
 => true)

(fact
 "execute-request-content conforms"
 (s/valid? ::msp/execute-request-content (ts/default-execute-request-content))
 => true)

(fact
 "execute-result-content conforms"
 (s/valid? ::msp/execute-input-content (msgs/execute-input-msg-content 1 "(list 1 2 3)"))
 => true)

(fact
 "history-reply-content conforms"
 (s/valid? ::msp/history-reply-content
           (msgs/history-reply-content [{:session 1, :line 1, :source "(list 1 2 3)"}
                                        {:session 3, :line 1, :source "(list 4 5 6)"}
                                        {:session 4, :line 1, :source "(list 7 8 9)"}]))
 => true)

(fact
 "input-request-content conforms"
 (s/valid? ::msp/input-request-content (msgs/input-request-content "Enter value:"))
 => true)

(fact
 "inspect-reply-content conforms"
 (s/valid? ::msp/inspect-reply-content (msgs/inspect-reply-content "(println" "Some docstring here"))
 => true)

(fact
 "is-complete-reply-content conforms"
 (s/valid? ::msp/is-complete-reply-content (msgs/is-complete-reply-content "complete"))
 =>
 (s/valid? ::msp/is-complete-reply-content (msgs/is-complete-reply-content "incomplete"))
 => true
 (s/valid? ::msp/is-complete-reply-content (msgs/is-complete-reply-content "xxx"))
 => false)

(fact
 "kernel_info_reply-content conforms"
 (s/valid? ::msp/kernel-info-reply-content (msgs/kernel-info-reply-content msgs/PROTOCOL-VERSION))
 => true)

(fact
 "shutdown-reply-content conforms"
 (s/valid? ::msp/shutdown-reply-content (msgs/shutdown-reply-content true))
 => true
 (s/valid? ::msp/shutdown-reply-content (msgs/shutdown-reply-content false))
 => true)

(fact
 "stream-message conforms"
 (s/valid? ::msp/stream-message-content (msgs/stream-message-content "stdout" "hello"))
 => true)
