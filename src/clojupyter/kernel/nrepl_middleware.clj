(ns clojupyter.kernel.nrepl-middleware
  (:require
   [nrepl.middleware.print				:refer [wrap-print]]
   [nrepl.middleware					:refer [set-descriptor!]]
   ,,
   [clojupyter.misc.mime-convertible]
   [clojupyter.protocol.mime-convertible	:as mc])
  (:import [nrepl.transport Transport]))

(defn mime-values
  [h]
  (fn [{:keys [op ^Transport transport] :as msg}]
    (h (assoc msg
              :transport (reify Transport
                           (recv [this] (.recv transport))
                           (recv [this timeout] (.recv transport timeout))
                           (send [this resp]
                             (.send transport
                                    (if-let [[_ v] (find resp :value)]
                                      (assoc resp :mime-tagged-value (mc/to-mime v))
                                      resp))
                             this))))))

(set-descriptor! #'mime-values
                 {:requires #{#'wrap-print}
                  :expects #{"eval"}
                  :handles {}})
