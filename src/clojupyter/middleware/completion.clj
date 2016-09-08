(ns clojupyter.middleware.completion
  (require [clojure.edn :as edn]))

(defn complete? [code]
  (try
    (edn/read-string code)
    true
    (catch RuntimeException e false)))
