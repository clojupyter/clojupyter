(ns clojupyter.misc.kind
  (:require
   [cheshire.core :as cheshire]
   [clojupyter.display :as display]
   [clojupyter.util :as u]
   [clojure.data.codec.base64 :as b64]
   [clojure.java.io :as io]
   [scicloj.kindly-advice.v1.api :as kindly-advice]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]) 
  (:import
   [javax.imageio ImageIO]))

(defn advise->clojupyter [{:keys [kind value] :as advise}]
  ;(println :advise->clojupyter--advise advise)
  ;(println :advise->clojupyter--kind kind)
  ;;(println :advise->clojupyter--value value)
  (case kind
    ;; clojupyter specific

    :kind/md (display/markdown value)
    :kind/tex (display/latex value)
    :kind/vega-lite (display/render-mime :application/vnd.vegalite.v3+json value)
    :kind/hiccup (display/hiccup-html value)
    :kind/image
    (let [out (io/java.io.ByteArrayOutputStream.)
          v (first value)]
      (ImageIO/write v "png" out)
      (display/render-mime :image/png
                           (-> out .toByteArray b64/encode String.)))

    :kind/html
    (display/html (first value))


    ;; generic and use diplay/hiccup-html

    :kind/plotly (display/hiccup-html
                  [:div {:style {:height "500px"
                                 :width "500px"}}
                   [:script {:src "https://cdn.plot.ly/plotly-2.35.2.min.js"
                             :charset "utf-8"}]
                   [:script (format "Plotly.newPlot(document.currentScript.parentElement, %s);"
                                    (cheshire/encode (:data value)))]])

    :kind/echarts
    (display/hiccup-html
     [:div {:style {:height "500px"
                    :width "500px"}}
      [:script {:src "https://cdn.jsdelivr.net/npm/echarts@5.4.1/dist/echarts.min.js"
                :charset "utf-8"}]
      [:script (format "
          {
          var myChart = echarts.init(document.currentScript.parentElement);
          myChart.setOption(%s);
          };"
                       (cheshire/encode value))]])


    :kind/dataset
    (->
     (to-hiccup/render {:value value})
     :hiccup
     display/hiccup-html)



    value))



(defn kind-eval [form]
  
  (let [value (eval form)]
    (if (var? value)
      value
      (let [advise  (kindly-advice/advise {:form form :value value})]
        (advise->clojupyter advise)))
    ;(println :advising--meta-form (meta form))
    ;(println :advising--meta-value (meta value ))
    ))
