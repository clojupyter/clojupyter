(ns clojupyter.middleware.comm
  (:require
   [clojure.pprint			:as pp		:refer [pprint]]
   [clojure.spec.alpha			:as s]
   [clojure.string			:as str]
   [pandect.algo.sha256					:refer [sha256-hmac]]
   [taoensso.timbre			:as log]
   [zeromq.zmq				:as zmq]
   ,,
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.nrepl.nrepl-comm		:as pnrepl]
   [clojupyter.transport		:as tp		:refer [handler-when transport-layer
                                                                response-mapping-transport
                                                                parent-msgtype-pred]]
   [clojupyter.misc.spec		:as sp]
   [clojupyter.kernel.state		:as state]
   [clojupyter.misc.util		:as u]
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
     (tp/send-req transport jup/COMM-CLOSE {:comm_id (u/message-comm-id parent-message), :data {}}))))

