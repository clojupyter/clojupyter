(ns clojupyter.misc.util
  (:require
   [clojure.pprint				:as pp]))

(defn pp-str
  [v]
  (with-out-str (pp/pprint v)))
