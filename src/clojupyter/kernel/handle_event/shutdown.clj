(ns clojupyter.kernel.handle-event.shutdown
  (:require [clojupyter.kernel.handle-event.ops :refer [definterceptor s*set-response]]
            [clojupyter.messages :as msgs]
            [clojupyter.plan :refer [s*bind-state]]))

(definterceptor ic*shutdown msgs/SHUTDOWN-REQUEST
  identity
  (s*bind-state {:keys [req-message]}
    ;; Termination of Clojupyter itself commences because the handle-event loop terminates when it
    ;; handles a SHUTDOWN-REQUEST message, cf. `handle_event_process.clj`
    (s*set-response msgs/SHUTDOWN-REPLY
                    (msgs/shutdown-reply-content (msgs/message-restart req-message)))))
