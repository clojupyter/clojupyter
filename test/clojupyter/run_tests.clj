(ns clojupyter.run-tests
  (:require [midje.repl :as mr]))

(defn- map-options [opts]
  (concat
   (:ns opts)
   (or (:opts opts) [])))

(defn run-tests [opts]
  (let [namespaces
        (or (and opts (map-options opts))
            [:all])]
    (println "Running tests in namespaces")
    (prn namespaces)
    (apply mr/load-facts namespaces)))
