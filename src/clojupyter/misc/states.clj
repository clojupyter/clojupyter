(ns clojupyter.misc.states)

(def current-global-states (atom nil))

(defrecord States [alive display-queue])

(defn make-states []
  (reset! current-global-states (States. (atom true) (atom []))))
