(ns clojupyter.middleware.mime-values
  (:require [nrepl.transport :as t]
            [clojure.pprint :as pp]
            [clojupyter.protocol.mime-convertible :as mime]
            [nrepl.middleware.pr-values])
  (:use [nrepl.middleware :only (set-descriptor!)])
  (:import [nrepl.transport Transport]))

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
                                 (assoc resp :mime-tagged-value (mime/to-mime value))
                                 resp))
                        this))))))

(set-descriptor! #'mime-values
                 {:requires #{#'nrepl.middleware.pr-values/pr-values}
                  :expects #{"eval"}
                  :handles {}})
