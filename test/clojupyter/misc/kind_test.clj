(ns clojupyter.misc.kind-test
  (:require
   [clojupyter.misc.kind :as k]
   [clojure.string :as str]
   [midje.sweet  :refer [=> facts]]
   [scicloj.kindly.v4.kind :as kind]
   ))


(def raw-image
  (->  "https://upload.wikimedia.org/wikipedia/commons/e/eb/Ash_Tree_-_geograph.org.uk_-_590710.jpg"
       (java.net.URL.)
       (javax.imageio.ImageIO/read)))

(def image
  (kind/image raw-image))


(def cs
  (kind/cytoscape
   {:elements {:nodes [{:data {:id "a" :parent "b"} :position {:x 215 :y 85}}
                       {:data {:id "b"}}
                       {:data {:id "c" :parent "b"} :position {:x 300 :y 85}}
                       {:data {:id "d"} :position {:x 215 :y 175}}
                       {:data {:id "e"}}
                       {:data {:id "f" :parent "e"} :position {:x 300 :y 175}}]
               :edges [{:data {:id "ad" :source "a" :target "d"}}
                       {:data {:id "eb" :source "e" :target "b"}}]}
    :style [{:selector "node"
             :css {:content "data(id)"
                   :text-valign "center"
                   :text-halign "center"}}
            {:selector "parent"
             :css {:text-valign "top"
                   :text-halign "center"}}
            {:selector "edge"
             :css {:curve-style "bezier"
                   :target-arrow-shape "triangle"}}]
    :layout {:name "preset"
             :padding 5}}))



(facts "eval works for different kinds"
       (k/kind-eval '(+ 1 1)) => {:html-data "2"}

       (k/kind-eval '(kind/md "# 123")) => {:html-data [:div {:class "kind-md"} [:h1 {:id "123"} "123"]]}


       (str/starts-with?
        (-> (k/kind-eval '(kind/image image))
            :html-data
            second
            :src)
        "data:image/png;base64,") => true



       (str/includes?
        (->
         (k/kind-eval  '^:kind/cytoscape cs)
         :html-data
         (nth 2)
         first
         second) "cytoscape") => true)

