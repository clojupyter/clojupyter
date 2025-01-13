(ns clojupyter.misc.kind-test
  (:require
   [clojupyter.misc.kind :as k]
   [clojure.string :as str]
   [midje.sweet                    :refer [=> facts]]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
   [scicloj.kindly.v4.kind :as kind]
   [tablecloth.api :as tc]
   [scicloj.kindly-advice.v1.api :as kindly-advice]))

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

(defn fetch-dataset [dataset-name]
  (-> dataset-name
      (->> (format "https://vincentarelbundock.github.io/Rdatasets/csv/%s.csv"))
      (tc/dataset {:key-fn (fn [k]
                             (-> k
                                 str/lower-case
                                 (str/replace #"\." "-")
                                 keyword))})
      (tc/set-dataset-name dataset-name)))

(def iris
  (fetch-dataset "datasets/iris"))


(facts "eval works for different kinds"
       (k/kind-eval '(+ 1 1)) => {:html-data "2"}

       (k/kind-eval '(kind/md "# 123")) => {:markdown ["# 123"]}

       (str/starts-with?
        (-> (k/kind-eval '(kind/image image)) class (.getName))
        "clojupyter.misc.display$render_mime") => true

       (-> (k/kind-eval '[(kind/image image) (kind/image image)])
           :html-data
           (nth 3)
           (nth 2)
           (nth 2))
       => "nested rendering of :kind/image not possible in Clojupyter"


       (str/includes?
        (->
         (k/kind-eval  '^:kind/cytoscape cs)
         :html-data
         (nth 2)
         second) "cytoscape") => true)


(facts "kind/fn works as expected"
       (str/includes?
        (->
         (k/kind-eval  '(-> iris
                            (plotly/layer-point {:=x :sepal-width
                                                 :=y :sepal-length
                                                 :=color :species
                                                 :=mark-size 10})))
         :html-data
         (nth 2)
         second) "Plotly") => true)

(facts "kind/table works"
       (->
        (k/kind-eval '(kind/table {:column-names [:a :b] :row-vectors [[1 2]]}))
        :html-data
        first) => :table)





(facts "nil return nil"
       (k/kind-eval 'nil) => nil)


(facts "kind/map works"
       (k/kind-eval '(kind/map {:a 1})) 
       =>
       {:html-data
        [:div
         {:class "kind-map"}
         [:div {:style {:border "1px solid grey", :padding "2px"}} ":a"]
         [:div {:style {:border "1px solid grey", :padding "2px"}} "1"]]}
       
       (k/kind-eval '{:a 1})
       => {:html-data
           [:div
            {:class "kind-map"}
            [:div {:style {:border "1px solid grey", :padding "2px"}} ":a"]
            [:div {:style {:border "1px solid grey", :padding "2px"}} "1"]]})

(facts "kind/hidden returns nothing"
       (k/kind-eval
        '(kind/hidden "(+ 1 1)")) =>
       {:html-data nil})


       



;; Getting these pass would brings us closer to "kind compliancy"
(facts "kind/fragment works"

      ;;  (k/kind-eval
      ;;   '(->> ["purple" "darkgreen" "brown"]
      ;;         (mapcat (fn [color]
      ;;                   [(kind/md (str "### subsection: " color))
      ;;                    (kind/hiccup [:div {:style {:background-color color
      ;;                                                :color "lightgrey"}}
      ;;                                  [:big [:p color]]])]))
      ;;         kind/fragment))

      ;;  (k/kind-eval
      ;;   '(->> (range 3)
      ;;         kind/fragment))
       )

(facts "kind/code is working"

       ;;bug submitted: https://github.com/scicloj/kindly-render/issues/26
       ;(k/kind-eval '(kind/code "(defn f [x] {:y (+  x 9)})"))

       
       ;(kindly-advice/advise {:value (kind/code "(defn f [x] {:y (+  x 9)})")})

       ;(to-hiccup/render {:value (kind/code "(defn f [x] {:y (+  x 9)})")})
       ;;=> {:value ["(defn f [x] {:y (+  x 9)})"],
       ;;    :meta-kind :kind/code,
       ;;    :kindly/options {},
       ;;    :kind :kind/code,
       ;;    :advice [[:kind/code {:reason :metadata}] [:kind/vector {:reason :predicate}] [:kind/seq {:reason :predicate}]],
       ;;    :deps #{:kind/code},
       ;;    :hiccup [:pre {:class "kind-code"} [:code {:class "sourceCode"} nil]]}
       )


(facts "image inside hiccup should not crash"
      ;;  (k/kind-eval
      ;;   '(kind/hiccup [:div.clay-limit-image-width
      ;;                 raw-image]))
       )


(facts "kind/fn works as expected - 2"

      ;; Getting these pass would brings us closer to "kind compliancy"

      ;;  (k/kind-eval
      ;;   '(kind/fn
      ;;      {:kindly/f (fn [{:keys [x y]}]
      ;;                   (+ x y))
      ;;       :x 1
      ;;       :y 2}))


      ;;  (k/kind-eval
      ;;   '(kind/fn
      ;;      {:x (range 3)
      ;;       :y (repeatedly 3 rand)}
      ;;      {:kindly/f tc/dataset}))
       )


(facts "kind/video is working"
       ;bu report: https://github.com/scicloj/kindly-render/issues/27
       ;(k/kind-eval '(kind/video
       ;       {:youtube-id "DAQnvAgBma8"}
       )
