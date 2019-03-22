(ns clojupyter.middleware.inspect
  (:require
   [clojure.string			:as str]
   [taoensso.timbre			:as log]
   ,,
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.nrepl.nrepl-comm		:as pnrepl]
   [clojupyter.transport		:as tp		:refer [handler-when parent-msgtype-pred]]
   [clojupyter.misc.util		:as u]
   ))

(defn- re-index
  "Returns a sorted-map of indicies to matches."
  [re s]
  (loop [matcher (re-matcher re s)
         matches (sorted-map)]
    (if (.find matcher)
      (recur matcher
             (assoc matches (.start matcher) (.group matcher)))
      matches)))

(defn- token-at
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

(def wrap-inspect-request
  (handler-when (parent-msgtype-pred jup/INSPECT-REQUEST)
   (fn [{:keys [transport nrepl-comm parent-message] :as ctx}]
     (let [code		(u/message-code parent-message)
           cursor_pos	(u/message-cursor-pos parent-message)
           symstr	(token-at code cursor_pos)
           result	(when-let [doc (pnrepl/nrepl-doc nrepl-comm symstr)]
                          (if-let [i (-> doc (str/index-of \newline) inc)]
                            (subs doc i (count doc))
                            doc))
           found?	(not (str/blank? result))
           data		(if found? {:text/html (str "<pre>" result "</pre>"), :text/plain (str result)}
                            {})
           reply	{:status "ok", :found found?, :code code, :metadata {}, :data data}]
       (tp/send-req transport jup/INSPECT-REPLY reply)))))
