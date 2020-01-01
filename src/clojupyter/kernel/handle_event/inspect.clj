(ns clojupyter.kernel.handle-event.inspect
  (:require
   [clojure.string			:as str]
   [io.simplect.compose.action				:refer [step]]
   ,,
   [clojupyter.kernel.cljsrv				:refer [nrepl-doc]]
   [clojupyter.kernel.handle-event.ops			:refer [definterceptor s*append-enter-action s*set-response]]
   [clojupyter.messages		:as msgs]
   [clojupyter.plan					:refer [s*bind-state]]
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

(defn inspect-info
  [req-message]
  (let [code		(msgs/message-code req-message)
        cursor-pos	(msgs/message-cursor-pos req-message)
        inspect-string	(token-at code cursor-pos)]
    {:code code, :cursor-pos cursor-pos, :inspect-string inspect-string}))

(definterceptor ic*inspect msgs/INSPECT-REQUEST
  (s*bind-state {:keys [cljsrv req-message] :as ctx}
    (let [{:keys [inspect-string] :as inspect-info} (inspect-info req-message)]
      (s*append-enter-action (step (fn [S] (-> (assoc S ::inspect-info inspect-info)
                                               (assoc ::inspect-result (nrepl-doc cljsrv inspect-string))))
                                   {:nrepl :inspect :data inspect-info}))))
  (s*bind-state {:keys [::inspect-result] {:keys [code]} ::inspect-info}
    (let [doc		inspect-result
          result-str	(when (string? doc)
                          (if-let [i (str/index-of doc \newline)]
                            (subs doc (inc i) (count doc))
                            doc))]
      (s*set-response msgs/INSPECT-REPLY (msgs/inspect-reply-content code result-str)))))
