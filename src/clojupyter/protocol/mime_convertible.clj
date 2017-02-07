(ns clojupyter.protocol.mime-convertible
  (:require [cheshire.core :as cheshire]))

(defn stream-to-string [map]
  (let [repr (java.io.StringWriter.)]
    (cheshire/generate-stream map repr)
    (str repr))
  )

(defprotocol PMimeConvertible
  (to-mime [o]))

(extend-protocol PMimeConvertible
  Object
  (to-mime [o]
    (stream-to-string {:text/plain (str o)})
    )
  nil
  (to-mime [o]
    "nil"))
