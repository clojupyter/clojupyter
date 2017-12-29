(ns clojupyter.middleware.mime-values
  (:require [clojure.tools.nrepl.transport :as t]
            [clojupyter.protocol.mime-convertible :as mime]
            [clojure.tools.nrepl.middleware.pr-values])
  (:use [clojure.tools.nrepl.middleware :only (set-descriptor!)])
  (:import clojure.tools.nrepl.transport.Transport))

(defn mime-values
  [h]
  (fn [{:keys [op ^Transport transport] :as msg}]
    (h (assoc msg
         :transport (reify Transport
                      (recv [this] (.recv transport))
                      (recv [this timeout] (.recv transport timeout))
                      (send [this {:keys [value] :as resp}]
                        (.send transport
                               (if-let [[_ v] (find resp :value)]
                                 (assoc (assoc resp :value (mime/to-mime value))
                                        :printed-value true)
                                 resp))
                        this))))))

(set-descriptor! #'mime-values
                 {:requires #{#'clojure.tools.nrepl.middleware.pr-values/pr-values}
                  :expects #{"eval"}
                  :handles {}})
