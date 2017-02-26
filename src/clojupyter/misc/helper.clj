(ns clojupyter.misc.helper
  (:require [cemerick.pomegranate :as pg]))

(defn add-dependencies
  [dependencies & {:keys [repositories]
                   :or {repositories {"central" "http://repo1.maven.org/maven2/"
                                      "clojars" "http://clojars.org/repo"}}
                   }]
  (pg/add-dependencies :coordinates `[~dependencies]
                       :repositories repositories))
