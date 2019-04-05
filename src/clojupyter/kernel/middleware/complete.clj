(ns clojupyter.kernel.middleware.complete
  (:require
   [clojure.pprint				:as pp		:refer [pprint]]
   [net.cgrand.sjacket.parser			:as p]
   ,,
   [clojupyter.kernel.jupyter			:as jup]
   [clojupyter.kernel.cljsrv.nrepl-comm		:as pnrepl]
   [clojupyter.kernel.transport			:as tp		:refer [handler-when transport-layer
                                                                        response-mapping-transport
                                                                        parent-msgtype-pred]]
   ))

(defn- complete?
  [code]
  (not (some  #(= :net.cgrand.parsley/unfinished %)
              (map :tag (tree-seq :tag
                                  :content
                                  (p/parser code))))))

(def wrap-is-complete-request
  (handler-when (parent-msgtype-pred jup/IS-COMPLETE-REQUEST)
   (fn [{:keys [transport parent-message]}]
     (tp/send-req transport jup/IS-COMPLETE-REPLY
       (if (complete? (jup/message-code parent-message))
         {:status "complete"}
         {:status "incomplete"})))))

(def wrap-complete-request
  (handler-when (parent-msgtype-pred jup/COMPLETE-REQUEST)
   (fn [{:keys [transport nrepl-comm parent-message]}]
     (tp/send-req transport jup/COMPLETE-REPLY
       (let [delimiters #{\( \" \% \space}
             cursor_pos (jup/message-cursor-pos parent-message)
             codestr (subs (jup/message-code parent-message) 0 cursor_pos)
             sym (as-> (reverse codestr) $
                   (take-while #(not (contains? delimiters %)) $)
                   (apply str (reverse $)))]
         {:matches (pnrepl/nrepl-complete nrepl-comm sym)
          :metadata {:_jupyter_types_experimental []}
          :cursor_start (- cursor_pos (count sym))
          :cursor_end cursor_pos
          :status "ok"})))))
