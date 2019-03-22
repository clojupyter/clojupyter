(ns clojupyter.middleware.execute
  (:require
   [clojure.string			:as str]
   [taoensso.timbre			:as log]
   ,,
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.nrepl.nrepl-comm		:as pnrepl]
   [clojupyter.transport		:as tp		:refer [handler-when parent-msgtype-pred]]
   [clojupyter.kernel.state		:as state]
   [clojupyter.misc.util		:as u]
   ))

(defn- hushed?
  [s]
  (str/ends-with? (str/trimr (or s "")) ";"))

(defn- submit-eval-request
  [{:keys [nrepl-comm transport parent-message] :as ctx}]
  (let [nrepl-resp				(pnrepl/nrepl-eval nrepl-comm transport (u/message-code parent-message))
        {:keys [result ename traceback]}	nrepl-resp
        err					(when ename
                                                  {:status "error", :ename ename,
                                                   :evalue "", :traceback traceback})]
    (log/debug "submit-eval-request: " :nrepl-resp nrepl-resp)
    {:err err, :result result, :nrepl-resp nrepl-resp}))

(def wrap-execute-request
  (handler-when (parent-msgtype-pred jup/EXECUTE-REQUEST)
    (fn [{:keys [transport msgtype parent-message] :as ctx}]
      (let [code			(u/message-code parent-message)
            {:keys [err result]
             :as eval-response}		(submit-eval-request ctx)
            content 			(-> err
                                            (or {:status "ok", :user_expressions {}})
                                            (assoc :execution_count (state/execute-count)))]
        (tp/send-iopub transport jup/EXECUTE-INPUT
          {:execution_count (state/execute-count), :code (u/message-code parent-message)})
        (tp/send-req transport jup/EXECUTE-REPLY content)
        (cond
          err
          ,, (tp/send-iopub transport jup/ERROR err)
          (not (hushed? code))
          ,, (tp/send-iopub transport jup/EXECUTE-RESULT
               {:execution_count (state/execute-count)
                :code code
                :raw-result result
                :data (u/parse-json-str result true)
                :metadata {}}))
        (state/add-history! code)
        (state/inc-execute-count!)))))
