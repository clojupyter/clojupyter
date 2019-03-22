(ns clojupyter.kernel.state
  (:require
   [cheshire.core				:as cheshire]
   [clojure.java.io				:as io]
   [clojure.pprint				:as pp]
   [clojure.walk				:as walk]
   [nrepl.server]
   [taoensso.timbre				:as log]
   ,,
   [clojupyter.kernel.history			:as his]
   [clojupyter.misc.mime-convertible]
   [clojupyter.misc.version			:as version]
   [clojupyter.protocol.mime-convertible	:as mc]))

(def ^:private EMPTY-QUEUE [])

(def STATE (atom nil))

(defrecord State [execute-count term? display-queue history-session])

(alter-meta! #'->State #(assoc % :private true))

(defn make-state
  ;; Not private to enable testing
  []
  (let [execute-count	1N
        term?		false
        display-queue	EMPTY-QUEUE
        sess		(-> (his/init-history) his/start-history-session)]
    (->State execute-count term? display-queue sess)))

(defn set-initial-state!
  []
  (reset! STATE (make-state)))

(defn inc-execute-count!
  "Increments Jupyter Execution Counter by 1."
  []
  (swap! STATE #(update-in % [:execute-count] inc)))

(defn execute-count
  "Returns current value of Jupyter Execution Counter."
  []
  (:execute-count @STATE))

(defn display!
  "Adds `obj` to the end of the display queue for to be output to the
  Jupyter `stdout` stream.

  Calling `display!` before kernel has been initialized generates an
  exception.

  Returns `:ok`."
  [obj]
  (assert @STATE "Clojupyter internal error: Global state not initialized.")
  (swap! STATE #(assoc % :display-queue conj (mc/to-mime obj)))
  :ok)

(defn- clear-display-queue!
  "Sets display queue to be empty."
  []
  (swap! STATE #(assoc % :display-queue EMPTY-QUEUE)))

(defn display-queue
  "Returns the current display queue."
  []
  (:display-queue @STATE))

(defn add-history!
  [code]
  (let [sess (:history-session @STATE)
        exe-count (:execute-count @STATE)]
    (assert sess "Clojupyter internal error: History session not found.")
    (assert exe-count "Clojupyter internal error: Execute count not found.")
    (his/add-history sess exe-count code)))

(defn get-history
  []
  (let [sess (:history-session @STATE)]
    (assert sess "Clojupyter internal error: History session not found.")
    (his/get-history sess)))

(defn end-history-session
  "Returns the history session."
  []
  (his/end-history-session (:history-session @STATE)))

(defn get-and-clear-display-queue!
  "Returns display queue after clearing it."
  []
  (let [q (display-queue)]
    (clear-display-queue!)
    q))

(defn terminate!
  "Sets kernel state to terminated and stops nREPL server.  After
  `terminate!` has been called `terminated?` returns `true`."
  [{:keys [nrepl-comm] :as R}]
  (swap! STATE #(assoc % :term? true))
  (nrepl.server/stop-server (:nrepl-server nrepl-comm))
  (Thread/sleep 100))

(defn terminated?
  "Returns `true` if kernel been `terminated!`, `false` otherwise."
  []
  (:term? @STATE))

