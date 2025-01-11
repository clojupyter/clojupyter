(ns clojupyter.misc.kind
  (:require
   [cheshire.core :as cheshire]
   [clojupyter.display :as display]
   [clojure.data.codec.base64 :as b64]
   [clojure.java.io :as io]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
   [scicloj.kindly-render.shared.walk :as walk]
   [scicloj.kindly-advice.v1.api :as kindly-advice]

   [clojure.string :as str])
  (:import
   [javax.imageio ImageIO]))

(defn require-js [url js-object render-cmd]
  [:script 
   (format 
    "
    loadScript_%s = src => new Promise(resolve => {
    script_%s = document.createElement('script');
    script_%s.src = src;
    script_%s.addEventListener('load', resolve);
    document.head.appendChild(script_%s);
  });
  if (typeof %s === 'undefined') {  
     promise_%s=loadScript_%s('%s')
     
     Promise.all([promise_%s]).then(() => {
       console.log('%s loaded');
        })
     
     };
 %s  

 "
 js-object js-object js-object 
 js-object js-object js-object js-object
 js-object url js-object js-object 
    render-cmd)])


(defn require-cytoscape [render-cmd]
  (require-js "https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.30.4/cytoscape.min.js"
              "cytoscape",render-cmd))


(defn require-plotly [render-cmd]
  (require-js "https://cdn.plot.ly/plotly-2.35.2.min.js"
              "Plotly"
              render-cmd
              ))

(defn require-highcharts [render-cmd]
  (require-js "https://code.highcharts.com/highcharts.js",
              "Highcharts",render-cmd)
  )

(defn require-echarts [render-cmd]
  (require-js "https://cdn.jsdelivr.net/npm/echarts@5.4.1/dist/echarts.min.js"
              "echarts",render-cmd))


(defn highcharts->hiccup [value]
  [:div {:style {:height "500px"
                 :width "500px"}}
   (require-highcharts (format "Highcharts.chart(document.currentScript.parentElement, %s);"
                               (cheshire/encode value)))
   ])


(defn plotly->hiccup [value]
  [:div {:style {:height "500px"
                 :width "500px"}}
   (require-plotly (format "Plotly.newPlot(document.currentScript.parentElement, %s);"
                           (cheshire/encode value)))
   ])

(defn cytoscape>hiccup [value]

  [:div {:style {:height "500px"
                 :width "500px"}}
   (require-cytoscape (format "
                        {
                        value = %s;
                        value['container'] = document.currentScript.parentElement;
                        cytoscape(value);
                        };"
                              (cheshire/encode value)))
   ])

(defn echarts->hiccup [value]
  [:div {:style {:height "500px"
                 :width "500px"}}
   (require-echarts (format "
                                {
                                var myChart = echarts.init(document.currentScript.parentElement);
                                myChart.setOption(%s);
                                };"
                            (cheshire/encode value)))
   ])



(defn- default-to-hiccup-render [note]
 (let [hiccup
        (->
         (to-hiccup/render note)
         :hiccup)]
    (assoc note
           :clojupyter (display/hiccup-html hiccup)
           :hiccup hiccup)))

(defn- render-non-nestable [note clojupyter]
  (assoc note
         :clojupyter clojupyter
         :hiccup [:div {:style "color:red"} (format "nested rendering of %s not possible in Clojupyter" (:kind note))]))

(defn- render-recursively [note value css-class render]
  (let [hiccup
        (:hiccup (walk/render-data-recursively note {:class css-class} value render))]
    (assoc note
           :clojupyter (display/hiccup-html hiccup)
           :hiccup hiccup)))

(defn- render-table-recursively [note render]
  (let [hiccup
        ;; TODO: https://github.com/scicloj/kindly-render/issues/23
        (:hjccup (walk/render-table-recursively note render))]
    (assoc note
           :clojupyter (display/hiccup-html hiccup)
           :hiccup hiccup)))


(defn render-js [note value ->hiccup-fn]
  (let [hiccup
        (->
         (->hiccup-fn value))]
    (assoc note
           :clojupyter (display/hiccup-html hiccup)
           :hiccup hiccup)))


(defmulti render-advice :kind)

(defn render [note]
  (walk/advise-render-style note render-advice))

(defmethod render-advice :default [{:as note :keys [value kind]}]
  (let [hiccup (if kind

                 [:div
                  [:div "Unimplemented: " [:code (pr-str kind)]]
                  [:code (pr-str value)]]
                 (str value))]
    (assoc note
           :clojupyter (display/hiccup-html hiccup)
           :hiccup hiccup)))



(defmethod render-advice :kind/plotly [{:as note :keys [value]}]
  (render-js note value plotly->hiccup))

(defmethod render-advice :kind/cytoscape [{:as note :keys [value]}]
  (render-js note value cytoscape>hiccup))

(defmethod render-advice :kind/highcharts [{:as note :keys [value]}]
  (render-js note value  highcharts->hiccup))

(defmethod render-advice :kind/echarts [{:as note :keys [value]}]
  (render-js note value echarts->hiccup))


(defmethod render-advice :kind/image [{:as note :keys [value]}]
  (let [out (io/java.io.ByteArrayOutputStream.)
        v (first value)
        clojupyter (do (ImageIO/write v "png" out)
                       (display/render-mime :image/png
                                            (-> out .toByteArray b64/encode String.)))]
    (render-non-nestable note clojupyter)))

(defmethod render-advice :kind/vega-lite [{:as note :keys [value]}]
  (render-non-nestable note (display/render-mime :application/vnd.vegalite.v3+json value))
  )

(defmethod render-advice :kind/md [{:as note :keys [value]}]
  (render-non-nestable note (display/markdown value)))

(defmethod render-advice :kind/tex [{:as note :keys [value]}]
  (render-non-nestable note (display/latex value)))


(defmethod render-advice :kind/dataset [note]
  (default-to-hiccup-render note))

(defmethod render-advice :kind/code [note]
  (default-to-hiccup-render note))


(defmethod render-advice :kind/pprint [note]
  (default-to-hiccup-render note))


(defmethod render-advice :kind/html [{:as note :keys [value]}]
  (assoc note
         :clojupyter (display/html (first value))
         :hiccup (first value)))


(defmethod render-advice :kind/hiccup [note]
  (let [hiccup
        (:hiccup (walk/render-hiccup-recursively note render))]
    (assoc note
           :clojupyter (display/hiccup-html hiccup)
           :hiccup hiccup)))


(defmethod render-advice :kind/vector [{:as note :keys [value]}]
  (render-recursively note value "kind-vector" render))

(defmethod render-advice :kind/map [{:as note :keys [value]}]
  (render-recursively note (apply concat value) "kind-map" render))

(defmethod render-advice :kind/set [{:as note :keys [value]}]
  (render-recursively note value "kind-set" render))
  

(defmethod render-advice :kind/seq [{:as note :keys [value]}]
  (render-recursively note value "kind-seq" render))

(defmethod render-advice :kind/table [note]
  (render-table-recursively note render))



(defmethod render-advice :kind/fn [{:keys [value form]}]
  (let [f (second (last value))
        note (render {:value (f value)
                      :form form})]
    
    (assoc note
           :hiccup (:hiccup note)
           :clojupyter (display/hiccup-html (:hiccup note)))))

(defn- render-as-clojupyter [form value]
  (let [kindly-advice (kindly-advice/advise {:form form
                                             :value value})])
  (or
   (nil? value)
   (str/starts-with? (.getName (class value)) "clojupyter.misc.display$render_mime")
   (contains?
    #{clojupyter.misc.display.Latex
      clojupyter.misc.display.HiccupHTML
      clojupyter.misc.display.Markdown
      clojupyter.misc.display.HtmlString
      java.awt.image.BufferedImage}
    (class value))
   ;(= [:kind/map {:reason :predicate}] (-> advice :advice first))
   
   
   
   ))




(defn kind-eval [form]
  ;(println :kind-eval--form form)

  (let [value (eval form)]
    ;(println :kind-eval--value value)
    ;(println :kind-eval--value-class (class value))
    ;(println :kind-eval--advise kindly-advice)

    (if (or (render-as-clojupyter form value)
            (var? value))
      value
      (:clojupyter (render {:value value
                            :form form})))))




