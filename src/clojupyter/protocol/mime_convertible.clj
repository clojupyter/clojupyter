(ns clojupyter.protocol.mime-convertible
  (:require
   [clojupyter.kernel.util		:as u]))

(defprotocol PMimeConvertible
  (to-mime [o]))

(def stream-to-string u/stream-to-string)



