(ns clojupyter.misc.display
  (:require [clojupyter.protocol.mime-convertible :as mc]
            [clojupyter.misc.states :as states]
            [cheshire.core :as cheshire]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io])
  (:import [javax.imageio ImageIO]))

(defn display [obj]
  (swap! (:display-queue @states/current-global-states) conj (mc/to-mime obj))
  nil)

;; Incanter Plot

(defrecord IncanterPlot [chart width height])

(extend-protocol mc/PMimeConvertible
  IncanterPlot
  (to-mime [plot]
    (let [out (io/java.io.ByteArrayOutputStream.)
          {:keys [chart width height]} plot]
      (ImageIO/write (.createBufferedImage chart width height)
                     "png" out)
      (mc/stream-to-string
       {:image/png (str (apply str (map char (b64/encode (.toByteArray out)))))}))))

(defn make-incanter-plot [chart & {:keys [width height]
                                   :or {width 600 height 400}}]
  (IncanterPlot. chart width height))


;; Html

(defrecord Html [html])

(extend-protocol mc/PMimeConvertible
  Html
  (to-mime [self]
      (mc/stream-to-string
       {:text/html (:html self)})))

(defn make-html [html]
  (Html. html))

;; Latex

(defrecord Latex [latex])

(extend-protocol mc/PMimeConvertible
  Latex
  (to-mime [self]
    (mc/stream-to-string
      {:text/latex (:latex self)})))

(defn make-latex [latex]
  (Latex. latex))


;; Markdown

(defrecord Markdown [markdown])

(extend-protocol mc/PMimeConvertible
  Markdown
  (to-mime [self]
    (mc/stream-to-string
      {:text/markdown (:markdown self)})))

(defn make-markdown [latex]
  (Markdown. latex))
