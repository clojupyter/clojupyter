(ns clojupyter.states
  (:require
   [clojure.java.io			:as io]
   ,,
   [clojupyter.history			:as his]))

(def current-global-states (atom nil))

(defrecord States [alive display-queue history-session])

(defn make-states []
  (reset! current-global-states (States. (atom true) (atom [])
                                         (his/start-history-session
                                          (his/init-history
                                           (str (System/getenv "HOME")
                                                "/.clojupyter_history"))))))
