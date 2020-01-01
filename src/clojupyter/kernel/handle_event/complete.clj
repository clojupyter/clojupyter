(ns clojupyter.kernel.handle-event.complete
  (:require
   [io.simplect.compose.action				:refer [step]]
   [net.cgrand.sjacket.parser		:as p]
   ,,
   [clojupyter.kernel.cljsrv				:refer [nrepl-complete]]
   [clojupyter.kernel.handle-event.ops			:refer [definterceptor s*append-enter-action s*set-response]]
   [clojupyter.messages		:as msgs]
   [clojupyter.plan					:refer [s*bind-state]]
   ))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; IS-COMPLETE
;;; ------------------------------------------------------------------------------------------------------------------------

(defn- complete?
  [code]
  (not (some  #(= :net.cgrand.parsley/unfinished %)
              (map :tag (tree-seq :tag
                                  :content
                                  (p/parser code))))))

(definterceptor ic*is-complete msgs/IS-COMPLETE-REQUEST
  identity
  (s*bind-state {:keys [req-message]}
    (let [reply (if (complete? (msgs/message-code req-message))
                  {:status "complete"}
                  {:status "incomplete"})]
      (s*set-response msgs/IS-COMPLETE-REPLY reply))))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; COMPLETE REQUEST
;;; ------------------------------------------------------------------------------------------------------------------------

(defn string-to-complete
  [codestr]
  (let [delimiters #{\( \" \% \space}]
    (as-> (reverse codestr) $
      (take-while #(not (contains? delimiters %)) $)
      (apply str (reverse $)))))

(defn complete-data
  [req-message]
  (let [pos (msgs/message-cursor-pos req-message)
        codestr (subs (msgs/message-code req-message) 0 pos)
        complete-string (string-to-complete codestr)
        start (- pos (count complete-string))]
    {:cursor-start start, :cursor-pos pos, :complete-string complete-string}))

(definterceptor ic*complete msgs/COMPLETE-REQUEST
  (s*bind-state {:keys [cljsrv req-message] :as ctx}
    (let [{:keys [complete-string] :as comdata} (complete-data req-message)]
      (s*append-enter-action (step (fn [S] (-> (assoc S :complete-pos comdata)
                                               (assoc :complete-matches (nrepl-complete cljsrv complete-string))))
                                   {:nrepl :complete :data comdata}))))
  (s*bind-state {:keys [complete-matches] {:keys [cursor-start cursor-pos]} :complete-pos :as ctx}
    (s*set-response msgs/COMPLETE-REPLY (msgs/complete-reply-content complete-matches cursor-start cursor-pos))))
