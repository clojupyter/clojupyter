(ns clojupyter.kernel.handle-event.kernel-info
  (:require [clojupyter.kernel.handle-event.ops :refer [definterceptor s*set-response]]
            [clojupyter.messages :as msgs]))

(definterceptor ic*kernel-info msgs/KERNEL-INFO-REQUEST
  identity
  (s*set-response msgs/KERNEL-INFO-REPLY (msgs/kernel-info-reply-content msgs/PROTOCOL-VERSION)))

