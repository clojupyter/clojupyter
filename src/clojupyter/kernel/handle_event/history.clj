(ns clojupyter.kernel.handle-event.history
  (:require [clojupyter.kernel.handle-event.ops :refer [definterceptor s*append-enter-action s*set-response]]
            [clojupyter.kernel.history :as his]
            [clojupyter.messages :as msgs]
            [clojupyter.plan :refer [s*bind-state]]
            [io.simplect.compose.action :refer [step]]))

(definterceptor ic*history msgs/HISTORY-REQUEST
  (s*append-enter-action (step (fn [S] (assoc S ::history-result (his/get-history)))
                               {:local :get-history}))
  (s*bind-state {:keys [::history-result]}
    (s*set-response msgs/HISTORY-REPLY (msgs/history-reply-content history-result))))
