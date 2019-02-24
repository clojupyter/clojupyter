(ns clojupyter.util
  (:require
   [clojure.pprint			:as pp]
   [net.cgrand.sjacket.parser		:as p]
   [clj-time.core			:as time]
   [clj-time.format			:as time-format]
   ,,
   [clojupyter.history			:as his]))

(defn- re-index
  "Returns a sorted-map of indicies to matches."
  [re s]
  (loop [matcher (re-matcher re s)
         matches (sorted-map)]
    (if (.find matcher)
      (recur matcher
             (assoc matches (.start matcher) (.group matcher)))
      matches)))

(defn token-at
  "Returns the token at the given position."
  [code position]
  (->>
    (loop [token-pos (re-index #"\S+" code)]
      (cond
        (nil? (first token-pos)) nil
        (nil? (second token-pos)) (val (first token-pos))
        (< position (key (second token-pos))) (val (first token-pos))
        :else (recur (rest token-pos))))
    (re-find #"[^\(\)\{\}\[\]\"\']+")))

(defn complete? [code]
  (not (some #(= :net.cgrand.parsley/unfinished %)
             (map :tag (tree-seq :tag :content (p/parser code))))))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn now
  []
  "Returns current ISO 8601 compliant date."
  (let [current-date-time (time/to-time-zone (time/now) (time/default-time-zone))]
    (time-format/unparse
     (time-format/with-zone (time-format/formatters :date-time-no-ms)
       (.getZone current-date-time))
     current-date-time)))

(defn pp-str
  [v]
  (with-out-str (pp/pprint v)))

;;; ----------------------------------------------------------------------------------------------------
;;; STATES
;;; ----------------------------------------------------------------------------------------------------

(def current-global-states (atom nil))

(defrecord States [alive display-queue history-session])

(defn make-states []
  (reset! current-global-states (States. (atom true) (atom [])
                                         (his/start-history-session
                                          (his/init-history
                                           (str (System/getenv "HOME")
                                                "/.clojupyter_history"))))))
