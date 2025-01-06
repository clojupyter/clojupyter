(ns clojupyter.misc.kind
  (:require
   [cheshire.core :as cheshire]
   [clojupyter.display :as display]
   [scicloj.kindly.v4.kind  :as kind]
   [clojure.data.codec.base64 :as b64]
   [clojure.java.io :as io]
   [scicloj.kindly-advice.v1.api :as kindly-advice]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
   [scicloj.kindly-render.note.to-hiccup-js :as to-hiccup-js]) 
  (:import
   [javax.imageio ImageIO]))

(def require-cytoscape
  [:script    "
  if (typeof cytoscape === 'undefined') {
    const script = document.createElement('script');
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.30.4/cytoscape.min.js';
    document.head.appendChild(script);
  }"])


(def require-plotly
  [:script    "
  if (typeof Plotly === 'undefined') {
    const script = document.createElement('script');
    script.src = 'https://cdn.plot.ly/plotly-2.35.2.min.js';
    document.head.appendChild(script);
  }"])

(def require-highcharts
  [:script    "
  if (typeof Highcharts === 'undefined') {
    const script = document.createElement('script');
    script.src = 'https://code.highcharts.com/highcharts.js';
    document.head.appendChild(script);
  }"])


(defn display-highcharts [value]
  (display/hiccup-html
   [:div {:style {:height "500px"
                  :width "500px"}}
    require-highcharts
    [:script (format "Highcharts.chart(document.currentScript.parentElement, %s);"
                     (cheshire/encode value))]]))


(defn display-plotly [value] 
  (display/hiccup-html
   [:div {:style {:height "500px"
                  :width "500px"}}
    require-plotly
    [:script (format "Plotly.newPlot(document.currentScript.parentElement, %s);"
                     (cheshire/encode value))]]))

(defn display-cytoscape [value]
  
    (display/hiccup-html
     [:div {:style {:height "500px"
                    :width "500px"}}
      require-cytoscape
      [:script (format "
  {
  value = %s;
  value['container'] = document.currentScript.parentElement;
  cytoscape(value);
  };"                      
    (cheshire/encode value))]]))


(defn display-default [value]
  ;;(println :display-default--value value)
  (let [hiccup
        (->
         (to-hiccup/render {:value value})
         :hiccup)]

    ;;(println :display-default--hiccup hiccup)
    (display/hiccup-html hiccup)))


(defn advise->clojupyter [{:keys [kind value] :as advise}]
  ;(println :advise->clojupyter--advise advise)
  ;;(println :advise->clojupyter--kind kind)
  ;;(println :advise->clojupyter--value value)
  (let [hiccup
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

          :kind/plotly (display-plotly (:data value))
          :kind/highcharts (display-highcharts value)
          :kind/cytoscape (display-cytoscape value)


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


          (display-default value)

          )
           
           ]
    
    hiccup
    ))


(defn kind-eval [form]
  
  (let [value (eval form)]
    (if (var? value)
      value
      (let [advise  (kindly-advice/advise {:form form :value value})]
        (advise->clojupyter advise)))
    ;(println :advising--meta-form (meta form))
    ;(println :advising--meta-value (meta value ))
    ))

