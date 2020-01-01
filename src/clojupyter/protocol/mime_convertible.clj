(ns clojupyter.protocol.mime-convertible
  (:require [clojupyter.util :as u]
            [io.simplect.compose :refer [redefn]]))

(defprotocol PMimeConvertible
  (to-mime [o]))

(redefn to-json-str u/to-json-str)
(def stream-to-string
  "Returns JSON representation (string) of `v`. DEPRECATED - use `to-json-str` instead."
  u/to-json-str)
