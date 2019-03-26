(ns clojupyter.kernel.middleware.comm
  (:require
   [clojure.pprint			:as pp		:refer [pprint]]
   ,,
   [clojupyter.kernel.jupyter		:as jup]
   [clojupyter.kernel.transport		:as tp		:refer [handler-when transport-layer
                                                                response-mapping-transport
                                                                parent-msgtype-pred]]
   ))

(def COMM-ID		"d9af479d-10fb-4ceb-a3c2-3c6638081a3c")
(def TARGET-NAME	"clojupyter.widget")
(def STATE		(atom {}))
(def TRANSPORT		(atom nil))

(defn save-transport
  [t]
  (when-not @TRANSPORT
    (reset! TRANSPORT t)))

;;; ----------------------------------------------------------------------------------------------------
;;; MIDDLEWARE
;;; ----------------------------------------------------------------------------------------------------

(def wrap-comm-info
  (handler-when (parent-msgtype-pred jup/COMM-INFO-REQUEST)
    (fn [{:keys [transport parent-message] :as ctx}]
      (save-transport transport)
      (tp/send-req transport jup/COMM-INFO-REPLY {:comms {COMM-ID {:target_name TARGET-NAME}}}))))

(def wrap-comm-msg
  (handler-when (parent-msgtype-pred jup/COMM-MSG)
    (fn [{:keys [transport parent-message] :as ctx}]
      (tp/send-req transport jup/COMM-MSG {:comm_id COMM-ID, :data {:method :update, :state {}}}))))

(def wrap-comm-open
  (handler-when (parent-msgtype-pred jup/COMM-OPEN)
   (fn [{:keys [transport parent-message] :as ctx}]
     (tp/send-req transport jup/COMM-CLOSE {:comm_id (jup/message-comm-id parent-message), :data {}}))))

