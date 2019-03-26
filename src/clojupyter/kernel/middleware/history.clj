(ns clojupyter.kernel.middleware.history
  (:require
   [clojupyter.kernel.jupyter		:as jup]
   [clojupyter.kernel.state		:as state]
   [clojupyter.kernel.transport		:as tp		:refer [handler-when parent-msgtype-pred]]
   ))

(def wrap-history-request
  (handler-when (parent-msgtype-pred jup/HISTORY-REQUEST)
   (fn [{:keys [transport]}]
     (tp/send-req transport jup/HISTORY-REPLY
       {:history (map (juxt :session :line :source) (state/get-history))}))))
