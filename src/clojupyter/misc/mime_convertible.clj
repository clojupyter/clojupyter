(ns clojupyter.misc.mime-convertible
  (:require [clojupyter.protocol.mime-convertible :as mc]
            [clojupyter.util :as u]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io])
  (:import java.awt.image.BufferedImage
           javax.imageio.ImageIO))

(extend-protocol mc/PMimeConvertible
  Object
  (to-mime [o]
    (u/to-json-str {:text/plain (pr-str o)}))
  nil
  (to-mime [o]
    (u/to-json-str {:text/plain "nil"}))
  java.awt.image.BufferedImage
  (to-mime [o]
    (let [out (io/java.io.ByteArrayOutputStream.)]
      (ImageIO/write o "png" out)
      (u/to-json-str
       {:image/png (-> out .toByteArray b64/encode String.)}))))
