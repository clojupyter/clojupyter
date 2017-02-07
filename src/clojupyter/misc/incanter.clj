(ns clojupyter.misc.incanter
  (:require [clojupyter.protocol.mime-convertible :as mc]
            [cheshire.core :as cheshire]
            [clojure.data.codec.base64 :as b64]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io])
  (:import [javax.imageio ImageIO]))

(defrecord IncanterPlot [chart width height])

(extend-protocol mc/PMimeConvertible
  IncanterPlot
  (to-mime [plot]
    (let [out (io/java.io.ByteArrayOutputStream.)
          {:keys [chart width height]} plot]
      (ImageIO/write (.createBufferedImage chart width height)
                     "png" out)
      (mc/stream-to-string
       {:image/png (str (apply str (map char (b64/encode (.toByteArray out)))))}
       ))))

(defn show [chart & {:keys [width height]
                     :or {width 600 height 400}}]
    (IncanterPlot. chart width height)
  )
