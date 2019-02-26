(ns clojupyter.misc.tokenize)

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
