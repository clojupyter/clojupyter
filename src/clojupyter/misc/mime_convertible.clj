(ns clojupyter.misc.mime-convertible
  (:require
   [clojure.data.codec.base64			:as b64]
   [clojure.java.io				:as io]
   ,,
   [clojupyter.misc.util			:as u]
   [clojupyter.protocol.mime-convertible	:as mc])
  (:import
   [javax.imageio ImageIO]
   [java.awt.image BufferedImage]))

(extend-protocol mc/PMimeConvertible
  Object
  (to-mime [o]
    (u/stream-to-string {:text/plain (pr-str o)}))
  nil
  (to-mime [o]
    (u/stream-to-string {:text/plain "nil"}))
  java.awt.image.BufferedImage
  (to-mime [o]
    (let [out (io/java.io.ByteArrayOutputStream.)]
      (ImageIO/write o "png" out)
      (u/stream-to-string
       {:image/png (-> out .toByteArray b64/encode String.)}))))
