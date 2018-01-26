(ns clojupyter.protocol.mime-convertible
  (:require
   [cheshire.core :as cheshire]
   [clojure.data.codec.base64 :as b64]
   [clojure.java.io :as io])
  (:import
   [javax.imageio ImageIO]
   [java.awt.image.BufferedImage]))

(defn stream-to-string [map]
  (let [repr (java.io.StringWriter.)]
    (cheshire/generate-stream map repr)
    (str repr)))


(defprotocol PMimeConvertible
  (to-mime [o]))

(extend-protocol PMimeConvertible
  Object
  (to-mime [o]
    (stream-to-string {:text/plain (pr-str o)}))

  nil
  (to-mime [o]
    "nil")

  java.awt.image.BufferedImage
  (to-mime [o]
    (let [out (io/java.io.ByteArrayOutputStream.)]
      (ImageIO/write o "png" out)
      (stream-to-string
       {:image/png (-> out .toByteArray b64/encode String.)}))))
