(ns clojupyter.kernel.middleware.execute
  (:require
   [clojure.string				:as str]
   [taoensso.timbre				:as log]
   ,,
   [clojupyter.kernel.jupyter			:as jup]
   [clojupyter.kernel.cljsrv.nrepl-comm		:as pnrepl]
   [clojupyter.kernel.transport			:as tp		:refer [handler-when parent-msgtype-pred]]
   [clojupyter.kernel.state			:as state]
   [clojupyter.kernel.util			:as u]
   ))

(defn- code-hushed?
  [s]
  (str/ends-with? (str/trimr (or s "")) ";"))

(defn- code-empty?
  [s]
  (= "" (str/trim s)))

(defn- submit-eval-request
  [{:keys [nrepl-comm transport parent-message]}]
  (let [nrepl-resp				(pnrepl/nrepl-eval nrepl-comm transport (jup/message-code parent-message))
        {:keys [result ename traceback]}	nrepl-resp
        err					(when ename
                                                  {:status "error", :ename ename,
                                                   :evalue "", :traceback traceback})]
    (log/debug "submit-eval-request: " :nrepl-resp nrepl-resp)
    {:err err, :result result, :nrepl-resp nrepl-resp}))

(def wrap-execute-request
  (handler-when (parent-msgtype-pred jup/EXECUTE-REQUEST)
    (fn [{:keys [transport parent-message] :as ctx}]
      (let [code			(jup/message-code parent-message)
            silent?			(or (jup/message-silent parent-message) (code-empty? code))
            store-history?		(if silent? false (jup/message-store-history? parent-message))
            {:keys [err result]}	(submit-eval-request ctx)
            content 			(-> err
                                            (or {:status "ok", :user_expressions {}})
                                            (assoc :execution_count (state/execute-count)))]
        (when-not silent?
          (tp/send-iopub transport jup/EXECUTE-INPUT
            {:execution_count (state/execute-count), :code (jup/message-code parent-message)}))
        (tp/send-req transport jup/EXECUTE-REPLY content)
        (cond
          err
          ,, (when-not silent?
               (tp/send-iopub transport jup/ERROR content))
          (and (not silent?) (not (code-hushed? code)))
          ,, (tp/send-iopub transport jup/EXECUTE-RESULT
               {:execution_count (state/execute-count)
                :data (when-not (nil? result) (u/parse-json-str result true))
                :metadata {}}))
        (when store-history?
          (state/add-history! code))
        (when-not silent?
          (state/inc-execute-count!))))))
