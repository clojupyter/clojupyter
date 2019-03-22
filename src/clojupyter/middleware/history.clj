(ns clojupyter.middleware.history
  (:require
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.kernel.state		:as state]
   [clojupyter.transport		:as tp		:refer [handler-when parent-msgtype-pred]]
   [clojupyter.misc.util		:as u]
   ))

(def wrap-history-request
  (handler-when (parent-msgtype-pred jup/HISTORY-REQUEST)
   (fn [{:keys [transport]}]
     (tp/send-req transport jup/HISTORY-REPLY
       {:history (map (juxt :session :line :source) (state/get-history))}))))
