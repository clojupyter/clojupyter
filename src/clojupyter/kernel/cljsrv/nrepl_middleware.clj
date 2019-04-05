(ns clojupyter.kernel.cljsrv.nrepl-middleware
  (:require
   [nrepl.middleware.print				:refer [wrap-print]]
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
                 {:requires #{#'wrap-print}
                  :expects #{"eval"}
                  :handles {}})
