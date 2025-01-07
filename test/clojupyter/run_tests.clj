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


(defn- exit [status msg]
  (println msg)
  (System/exit status))

(defn run-tests-and-exit [opts]
  (let [{:keys [failures] :as results}  (run-tests opts)]
    (println results)
    (if (> failures 0)
      (exit 1 "Some tests failed")
      (exit 0 "All tests passed"))))
