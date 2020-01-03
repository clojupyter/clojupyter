(ns clojupyter.misc.display
  (:require [clojupyter.kernel.handle-event.ops :as ops]
            [clojupyter.kernel.jup-channels :as jup]
            [clojupyter.messages :as msgs]
            [clojupyter.protocol.mime-convertible :as mc]
            [clojupyter.state :as state]
            [clojupyter.util :as u]
            [hiccup.core :as hiccup]
            [io.simplect.compose.action :refer [action step]]))

(defn display
  "Sends `obj` for display by Jupyter. Returns `:ok`."
  [obj]
  (if-let [{:keys [jup req-message]} (state/current-context)]
    (let [send-obj (-> obj mc/to-mime u/parse-json-str)
          port :iopub_port
          msgtype msgs/DISPLAY-DATA
          message (msgs/display-data-content send-obj {} {})]
      (-> (action (step [`jup/send!! jup port req-message msgtype message]
                        {:message-to port, :msgtype msgtype, :message message}))
          ops/s*append-leave-action
          state/swap-context!))
    (throw (Exception. "display: Evaluation context not found.")))
  :ok)

;; Html

(defrecord HiccupHTML [html-data]
  mc/PMimeConvertible
  (to-mime [_] (u/to-json-str {:text/html (hiccup/html html-data)})))

(defn hiccup-html
  "Output `html-data` as HTML."
  [html-data]
  (->HiccupHTML html-data))

(defrecord HtmlString [html]
  mc/PMimeConvertible
  (to-mime [_]
    (u/to-json-str
     {:text/html html})))

(defn html
  "Output `html-src`.  If `html-src` is a string output it as-is,
  assume it is in Hiccup format and format it as HTML."
  [html-src]
  (if (string? html-src)
    (->HtmlString html-src)
    (->HiccupHTML html-src)))

(defn ^:deprecated make-html
  [html-str]
  (html html-str))


;; Latex

(defrecord Latex [latex]
  mc/PMimeConvertible
  (to-mime [_]
    (u/to-json-str
     {:text/latex latex})))

(defn latex
  "Output `latex-str` as LaTeX."
  [latex-str]
  (->Latex latex-str))

(defn ^:deprecated make-latex
  [latex-str]
  (latex latex-str))


;; Markdown

(defrecord Markdown [markdown]
  mc/PMimeConvertible
  (to-mime [_]
    (u/to-json-str
     {:text/markdown markdown})))

(defn markdown
  "Output `markdown-str` as Markdown."
  [markdown-str]
  (->Markdown markdown-str))

(defn ^:deprecated make-markdown
  [markdown-str]
  (markdown markdown-str))

;; Vega Lite

(defn vega-lite
  [v]
  (u/to-json-str {:application/vnd.vegalite.v1+json v}))

(defn render-mime
  [mime-type v]
  (reify mc/PMimeConvertible
    (to-mime [_]
      (u/to-json-str (hash-map mime-type v)))))

