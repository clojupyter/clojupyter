(ns clojupyter.misc.kind-test
  (:require [clojupyter.misc.display        :as display]
            [clojupyter.misc.helper     :as helper]
            [clojupyter.test-shared     :as ts]
            [clojupyter.misc.kind :as k]
            [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]
            
            [midje.sweet                    :refer [facts =>]])
  )

(def image
  (kind/image
   (->  "https://upload.wikimedia.org/wikipedia/commons/e/eb/Ash_Tree_-_geograph.org.uk_-_590710.jpg"
        (java.net.URL.)
        (javax.imageio.ImageIO/read))))

(facts "eval works for different kinds"
       (k/kind-eval '(+ 1 1)) => {:html-data "2"}
       (k/kind-eval '(kind/md "# 123")) => {:markdown ["# 123"]}
       (str/starts-with?
        (-> (k/kind-eval '(kind/image image)) class (.getName))
        "clojupyter.misc.display$render_mime") => true)