(ns clojupyter.display
  (:require [clojupyter.misc.display :as dis]
            [io.simplect.compose :refer [redefn]]
            [clojure.data.codec.base64 :as b64]))

(redefn display		dis/display)
(redefn hiccup-html	dis/hiccup-html)
(redefn html		dis/html)
(redefn latex		dis/latex)
(redefn markdown	dis/markdown)
(redefn render-mime	dis/render-mime)

;; Vega Lite

(defn vega-lite-1
  [v]
  (render-mime :application/vnd.vegalite.v1+json v))

(defn vega-lite-3
  [v]
  (render-mime :application/vnd.vegalite.v3+json v))

(defn vega-lite
  [v]
  (vega-lite-3 v))

;; Vega

(defn vega-5
  [v]
  (render-mime :application/vnd.vega.v5+json v))

(defn vega
  [v]
  (vega-5 v))

;; Gif

(defn gif
  [b]
  (render-mime :image/gif (new String (b64/encode b))))

;; Pdf

(defn pdf
  [b]
  (render-mime :application/pdf (new String (b64/encode b))))

;; JSON

(defn json
  [v]
  (render-mime :application/json v))

;; VDOM

(defn vdom
  [v]
  (render-mime :application/vdom.v1+json v))
