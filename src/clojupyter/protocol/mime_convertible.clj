(ns clojupyter.protocol.mime-convertible
  (:require
   [clojupyter.misc.util		:as u]))

(defprotocol PMimeConvertible
  (to-mime [o]))

(def stream-to-string u/stream-to-string)



