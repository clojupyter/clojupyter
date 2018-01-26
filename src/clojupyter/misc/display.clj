(ns clojupyter.misc.display
  (:require [clojupyter.protocol.mime-convertible :as mc]
            [clojupyter.misc.states :as states]
            [cheshire.core :as cheshire]
            [clojure.data.codec.base64 :as b64]
            [clojure.java.io :as io]
            [hiccup.core :as hiccup])
  (:import [javax.imageio ImageIO]))

(defn display [obj]
  (swap! (:display-queue @states/current-global-states) conj (mc/to-mime obj))
  nil)

;; Incanter Plot

(defrecord IncanterPlot [chart width height]

  mc/PMimeConvertible
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

(defrecord HtmlString [html]

  mc/PMimeConvertible
  (to-mime [_]
    (mc/stream-to-string
     {:text/html html})))

(defn html [html-str]
  (HtmlString. html-str))

(defn ^:deprecated make-html
  [html-str]
  (html html-str))

(defrecord HiccupHTML [html-data]

  mc/PMimeConvertible
  (to-mime [_]
    (mc/stream-to-string
     {:text/html (hiccup/html html-data)})))

(defn hiccup-html [html-data]
  (HiccupHTML. html-data))

;; Latex

(defrecord Latex [latex]

  mc/PMimeConvertible
  (to-mime [_]
    (mc/stream-to-string
     {:text/latex latex})))

(defn latex [latex-str]
  (Latex. latex-str))

(defn ^:deprecated make-latex
  [latex-str]
  (latex latex-str))


;; Markdown

(defrecord Markdown [markdown]

  mc/PMimeConvertible
  (to-mime [_]
    (mc/stream-to-string
     {:text/markdown markdown})))

(defn markdown [markdown-str]
  (Markdown. markdown-str))

(defn ^:deprecated make-markdown
  [markdown-str]
  (markdown markdown-str))
