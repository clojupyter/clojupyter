(ns clojupyter.misc.kind-test
  (:require
   [clojupyter.misc.kind :as k]
   [clojure.string :as str]
   [midje.sweet                    :refer [=> facts]]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
   [scicloj.kindly-render.note.to-hiccup-js :as to-hiccup-js]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.tableplot.v1.plotly :as plotly]
   [tablecloth.api :as tc]
   [reagent.core]
   [scicloj.kindly-advice.v1.api :as kindly-advice]
   [hiccup.core :as hiccup]))

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
                            {:value :red}}}}}

  )

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
         first
         second
         
         ) "cytoscape") => true)




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
         first
        second 
         ) "plotly") => true)

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

(facts "kind/scittle works"
       (str/includes?        
        (->
         (k/kind-eval '(kind/scittle '(.log js/console "hello")))
         :html-data
         second
         (nth 6)
         second
         )
        "scittle.core.eval_string('(.log js/console \"hello\")')"
        )=> true)
       
(facts "kind/vega works"
       (str/starts-with? 
        (str (class (k/kind-eval '(kind/vega vega-spec))))
        "class clojupyter.misc.display$render_mime"

        ) => true)

(facts "kind/plotly works"
       (str/includes?
        (->
         (k/kind-eval '(kind/plotly plotly-data))
         :html-data
         (nth 2)
         first
         second
         )
        "var clojupyter_loaded_marker_plotly"
        ))

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
       ;bug report: https://github.com/scicloj/kindly-render/issues/27
       ;(k/kind-eval '(kind/video
       ;       {:youtube-id "DAQnvAgBma8"}
       )

(facts "kind/scittle is working"
  ; (k/kind-eval '(kind/scittle '(print "hello")))       
       )

(facts "kind/htmlwidgets-ggplotly is woking"
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


(comment
  (k/kind-eval '(kind/scittle
                 '(.log js/console "hello")))
  


  (to-hiccup-js/render {:form
                        '(kind/scittle
                          '(.log js/console "hello"))
                        
                        })
  


  (to-hiccup-js/render {:form

                        '(kind/reagent
                          ['(fn [{:keys [initial-value
                                         background-color]}]
                              
                              (let [*click-count (reagent.core/atom initial-value)]
                                (fn []
                                  [:div {:style {:background-color background-color}}
                                   "The atom " [:code "*click-count"] " has value: "
                                   @*click-count ". "
                                   [:input {:type "button" :value "Click me!"
                                            :on-click #(swap! *click-count inc)}]])))
                           {:initial-value 9
                            :background-color "#d4ebe9"}])})
  


  to-hiccup-js/*id-counter*
  
  to-hiccup-js/*id-prefix*

  (k/kind-eval
   '(kind/reagent
     ['(fn [{:keys [initial-value
                    background-color]}]
         (let [*click-count (reagent.core/atom initial-value)]
           (fn []
             [:div {:style {:background-color background-color}}
              "The atom " [:code "*click-count"] " has value: "
              @*click-count ". "
              [:input {:type "button" :value "Click me!"
                       :on-click #(swap! *click-count inc)}]])))
      {:initial-value 9
       :background-color "#d4ebe9"}]))

  )
