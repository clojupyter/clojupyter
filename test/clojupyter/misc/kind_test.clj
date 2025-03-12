(ns clojupyter.misc.kind-test
  (:require
   [clojupyter.misc.kind :as k]
   [clojure.string :as str]
   [clojure.string :as s]
   [hiccup.core :as hiccup]
   [midje.sweet                    :refer [=> facts]]
   [reagent.core]
   [scicloj.kindly-advice.v1.api :as kindly-advice]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
   [scicloj.kindly-render.note.to-hiccup-js :as to-hiccup-js]
   [scicloj.kindly-render.shared.walk :as walk]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.tableplot.v1.plotly :as plotly]
   [scicloj.metamorph.ml.rdatasets :as rdatasets]
   [tablecloth.api :as tc]))


(def raw-image
  (->  "https://upload.wikimedia.org/wikipedia/commons/e/eb/Ash_Tree_-_geograph.org.uk_-_590710.jpg"
       (java.net.URL.)
       (javax.imageio.ImageIO/read)))

(defn multi-nth
  [v indexes]
  (reduce (fn [coll idx]
            (nth coll idx))
          v
          indexes))


(def image
  (kind/image raw-image))

(def vl-spec
  {:encoding
   {:y {:field "y", :type "quantitative"},
    :size {:value 400},
    :x {:field "x", :type "quantitative"}},
   :mark {:type "circle", :tooltip true},
   :width 400,
   :background "floralwhite",
   :height 100,
   :data {:values "x,y\n1,1\n2,-4\n3,9\n", :format {:type "csv"}}})

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

(def vega-spec
  {:$schema "https://vega.github.io/schema/vega/v5.json"
   :width 400
   :height 200
   :padding 5
   :data {:name "table"
          :values [{:category :A :amount 28}
                   {:category :B :amount 55}
                   {:category :C :amount 43}
                   {:category :D :amount 91}
                   {:category :E :amount 81}
                   {:category :F :amount 53}
                   {:category :G :amount 19}
                   {:category :H :amount 87}]}
   :signals [{:name :tooltip
              :value {}
              :on [{:events "rect:mouseover"
                    :update :datum}
                   {:events "rect:mouseout"
                    :update "{}"}]}]
   :scales [{:name :xscale
             :type :band
             :domain {:data :table
                      :field :category}
             :range :width
             :padding 0.05
             :round true}
            {:name :yscale
             :domain {:data :table
                      :field :amount}
             :nice true
             :range :height}]
   :axes [{:orient :bottom :scale :xscale}
          {:orient :left :scale :yscale}]
   :marks {:type :rect
           :from {:data :table}
           :encode {:enter {:x {:scale :xscale
                                :field :category}
                            :width {:scale :xscale
                                    :band 1}
                            :y {:scale :yscale
                                :field :amount}
                            :y2 {:scale :yscale
                                 :value 0}}
                    :update {:fill
                             {:value :steelblue}}
                    :hover {:fill
                            {:value :red}}}}})

(def plotly-data
  (let [n 20
        walk (fn [bias]
               (->> (repeatedly n #(-> (rand)
                                       (- 0.5)
                                       (+ bias)))
                    (reductions +)))]
    {:data [{:x (walk 1)
             :y (walk -1)
             :z (map #(* % %)
                     (walk 2))
             :type :scatter3d
             :mode :lines+markers
             :opacity 0.2
             :line {:width 10}
             :marker {:size 20
                      :colorscale :Viridis}}]}))

(def people-as-maps
  (->> (range 29)
       (mapv (fn [_]
               {:preferred-language (["clojure" "clojurescript" "babashka"]
                                     (rand-int 3))
                :age (rand-int 100)}))))

(def people-as-vectors
  (->> people-as-maps
       (mapv (juxt :preferred-language :age))))

(def people-as-dataset
  (tc/dataset people-as-maps))

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




















;; Getting these pass would increase the "kind compatibility"


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



(facts "kind/htmlwidgets-ggplotly is working"
       ;; (k/kind-eval
       ;;  '(kind/htmlwidgets-ggplotly {}))
       )

(facts "kind/edn is working"
       ;;  (k/kind-eval
       ;;   '(kind/edn {}))
       )

(facts "kind/smile-model is working"
       ;;   (k/kind-eval
       ;;    '(kind/smile-model {}))
       )



