(ns clojupyter.misc.mime-convertible
  (:require [clojupyter.protocol.mime-convertible :as mc]
            [clojupyter.util :as u]
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
       {:image/png (-> out .toByteArray u/base64-encode String.)}))))
