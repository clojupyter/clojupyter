(ns clojupyter.misc.kind
  (:require
   [cheshire.core :as cheshire]
   [clojupyter.display :as display]
   [scicloj.kindly.v4.kind  :as kind]
   [clojure.data.codec.base64 :as b64]
   [clojure.java.io :as io]
   [scicloj.kindly-advice.v1.api :as kindly-advice]
   [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
   [scicloj.kindly-render.note.to-hiccup-js :as to-hiccup-js]
   [scicloj.kindly-render.shared.util :as util]
   [scicloj.kindly-render.shared.walk :as walk]

   [hiccup.core :as hiccup])
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

(def require-echarts
  [:script    "
  if (typeof echarts === 'undefined') {
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/echarts@5.4.1/dist/echarts.min.js';
    document.head.appendChild(script);
  }"])


(defn highcharts->hiccup [value]
  [:div {:style {:height "500px"
                 :width "500px"}}
   require-highcharts
   [:script (format "Highcharts.chart(document.currentScript.parentElement, %s);"
                    (cheshire/encode value))]])


(defn plotly->hiccup [value]
  [:div {:style {:height "500px"
                 :width "500px"}}
   require-plotly
   [:script (format "Plotly.newPlot(document.currentScript.parentElement, %s);"
                    (cheshire/encode value))]])

(defn cytoscape>hiccup [value]

  [:div {:style {:height "500px"
                 :width "500px"}}
   require-cytoscape
   [:script (format "
  {
  value = %s;
  value['container'] = document.currentScript.parentElement;
  cytoscape(value);
  };"
                    (cheshire/encode value))]])

(defn echarts->hiccup [value]
  [:div {:style {:height "500px"
                 :width "500px"}}
   [:script require-echarts]
   [:script (format "
            {
            var myChart = echarts.init(document.currentScript.parentElement);
            myChart.setOption(%s);
            };"
                    (cheshire/encode value))]])



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
  (walk/render-table-recursively note render))



(defn kind-eval [form]

  (let [value (eval form)]
    (if (var? value)
      value
      (:clojupyter (render {:value value
                            :form form})))
    ;(println :advising--meta-form (meta form))
    ;(println :advising--meta-value (meta value ))
    ))


