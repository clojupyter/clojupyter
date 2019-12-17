(ns clojupyter.kernel.handle-event-process
  (:require [clojupyter.kernel.handle-event :as he]
            [clojupyter.kernel.jup-channels :refer [jup-channels send!!]]
            [clojupyter.messages :as msgs]
            [clojupyter.log :as log]
            [clojupyter.shutdown :as shutdown]
            [clojupyter.util :as u]
            [clojupyter.util-actions :as u!]
            [clojure.core.async :as async]
            [io.simplect.compose :refer [c C p P]]))

(defn- handle-error
  [msg]
  (log/debug "handle-event-process: error on input" (log/ppstr {:msg msg})))

(defn- handle-event-or-channel-error
  "Handles request, returns `false` iff event-handling should terminate."
  [{:keys [jup] :as ctx} channel-port inbound-msg]
  (let [{:keys [req-message req-port error?]} inbound-msg
        update-status! #(send!! jup :iopub_port req-message msgs/STATUS (msgs/status-message-content %))]
    (assert (= channel-port req-port)
            (str "handle-request - internal error ('" channel-port "' neq '"  req-port "')"))
    (if error?
      (do (handle-error inbound-msg)
          ;; do not continue after error:
          false)
      (do (update-status! "busy")
          (he/handle-event! (assoc ctx :req-message req-message :req-port req-port))
          (update-status! "idle")
          ;; continue iff we are not handling a shutdown request:
          (not= (msgs/message-msg-type req-message) msgs/SHUTDOWN-REQUEST)))))

(defn- run-dispatch-loop
  [{:keys [jup term] :as ctx}]
  (let [term-ch (shutdown/notify-on-shutdown term (async/chan 1))
        [ctrl-in _] (jup-channels jup :control_port)
        [shell-in _] (jup-channels jup :shell_port)]
    (loop []
      (let [[inmsg-or-token rcv-ch] (async/alts!! [term-ch ctrl-in shell-in] :priority true)]
        (cond
          (nil? inmsg-or-token)
          ,, (log/debug "handle-event-process received nil - terminating")
          (shutdown/is-token? inmsg-or-token)
          ,, (log/debug "handle-event-process received shutdown - terminating")
          :else 
          ,, (let [port (cond 
                          (= rcv-ch ctrl-in) :control_port
                          (= rcv-ch shell-in) :shell_port
                          :else  (throw (Exception. "run-dispatch-loop: internal error")))]
               (when (handle-event-or-channel-error ctx port inmsg-or-token)
                 (recur))))))))

(defn start-handle-event-process
  [{:keys [term] :as ctx}]
  (async/thread
    (shutdown/initiating-shutdown-on-exit [:handle-event-process term]
      (u!/with-exception-logging
          (do (log/debug "handle-event-process starting")
              (run-dispatch-loop ctx))
        (log/debug "handle-event-process terminating")))))
