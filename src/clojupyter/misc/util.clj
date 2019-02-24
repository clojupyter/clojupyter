(ns clojupyter.misc.util
  (:require
   [clojure.pprint			:as pp]
   [net.cgrand.sjacket.parser		:as p]))

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

(defn pp-str
  [v]
  (with-out-str (pp/pprint v)))
