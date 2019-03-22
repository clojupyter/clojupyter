(ns clojupyter.nrepl-middleware.mime-values
  (:require
   [clojure.pprint				:as pp]
   [nrepl.transport				:as t]
   [nrepl.middleware.pr-values					:refer [pr-values]]
   ,,
   [clojupyter.misc.mime-convertible]
   [clojupyter.protocol.mime-convertible	:as mc])
  (:use
   [nrepl.middleware				:only (set-descriptor!)])
  (:import [nrepl.transport Transport]))

(defn mime-values
  [h]
  (fn [{:keys [op ^Transport transport] :as msg}]
    (h (assoc msg
              :transport (reify Transport
                           (recv [this] (.recv transport))
                           (recv [this timeout] (.recv transport timeout))
                           (send [this {:keys [printed-value value] :as resp}]
                             (.send transport
                                    (if-let [[_ v] (find resp :value)]
                                      (assoc resp :mime-tagged-value (mc/to-mime v))
                                      resp))
                             this))))))

(set-descriptor! #'mime-values 
                 {:requires #{#'pr-values}
                  :expects #{"eval"}
                  :handles {}})
