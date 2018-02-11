(ns clojupyter.misc.display
  (:require [clojupyter.protocol.mime-convertible :as mc]
            [clojupyter.misc.states :as states]
            [hiccup.core :as hiccup]))

(defn display [obj]
  (swap! (:display-queue @states/current-global-states) conj (mc/to-mime obj))
  nil)


;; Html

(defrecord HiccupHTML [html-data]

  mc/PMimeConvertible
  (to-mime [_]
    (mc/stream-to-string
      {:text/html (hiccup/html html-data)})))

(defn hiccup-html [html-data]
  (HiccupHTML. html-data))


(defrecord HtmlString [html]

  mc/PMimeConvertible
  (to-mime [_]
    (mc/stream-to-string
     {:text/html html})))

(defn html [html-src]
  (if (string? html-src)
    (HtmlString. html-src)
    (HiccupHTML. html-src)))

(defn ^:deprecated make-html
  [html-str]
  (html html-str))


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
