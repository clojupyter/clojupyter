(ns clojupyter.misc.helper
  (:require [cemerick.pomegranate :as pg]
            [clojupyter.misc.display :as display]))

(defn add-javascript
  "add a single javascript to front-end, must be last form of cell input

  Example:
    (add-javascript \"https://cdnjs.cloudflare.com/ajax/libs/d3/4.12.2/d3.js\")
  "
  [script-src]
  (display/hiccup-html [:script {:src script-src}]))

(defn add-dependencies
  [dependencies & {:keys [repositories]
                   :or {repositories {"central" "https://repo1.maven.org/maven2/"
                                      "clojars" "https://clojars.org/repo"}}}]

  (pg/add-dependencies :coordinates `[~dependencies]
                       :repositories repositories))
