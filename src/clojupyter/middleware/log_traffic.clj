(ns clojupyter.middleware.log-traffic
  (:require
   [clojure.pprint			:as pp		:refer [pprint]]
   [clojure.spec.alpha			:as s]
   [taoensso.timbre			:as log]
   ,,
   [clojupyter.misc.config		:as cfg]
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.transport		:as tp		:refer [handler-when transport-layer
                                                                response-mapping-transport
                                                                parent-msgtype-pred]]
   [clojupyter.misc.spec		:as sp]
   [clojupyter.kernel.state		:as state]
   [clojupyter.misc.util		:as u]
   ))

(def ^:private logging? (atom false))

(defn- set-logging-traffic!
  [v]
  (reset! logging? (or (and v true) false)))

(defn enable-log-traffic!
  []
  (set-logging-traffic! true))

(defn disable-log-traffic!
  []
  (set-logging-traffic! false))

(def wrap-print-messages
  (transport-layer
   {:send-fn (fn [{:keys [transport parent-message msgtype] :as ctx} socket resp-msgtype resp-message]
               (let [uuid (subs (u/uuid) 0 6)]
                 (when (and @logging? (not= socket :iopub)
                             (log/info (str "wrap-print-messages parent-message (" uuid "):") socket msgtype
                                       "\n" (with-out-str (pprint (dissoc parent-message ::u/zmq-raw-message)))))
                   (log/info (str "wrap-print-messages response-message (" uuid "):") socket resp-msgtype
                             "\n" (with-out-str (pprint resp-message))))
                 (tp/send* transport socket msgtype resp-message)))}))

(defn init!
  []
  (set-logging-traffic! (cfg/log-traffic?)))
