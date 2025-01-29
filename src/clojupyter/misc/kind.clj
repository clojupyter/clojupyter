(ns clojupyter.misc.kind
   (:require
    [cheshire.core :as cheshire]
    [clojupyter.display :as display]
    [clojure.data.codec.base64 :as b64]
    [clojure.java.io :as io]
    [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
    [scicloj.kindly-render.shared.walk :as walk]
    [scicloj.kindly-advice.v1.api :as kindly-advice]
    [clojure.string :as str]
    [scicloj.kindly-render.notes.js-deps :as js-deps]
    [scicloj.kindly-render.note.to-hiccup-js :as to-hiccup-js]
    [malli.core :as m]
    [malli.error :as me]
    [scicloj.kindly.v4.kind :as kind]
    [malli.experimental.describe :as med]
    [clojupyter.misc.display :as dis])
   (:import
    [javax.imageio ImageIO]
    [java.security MessageDigest]))




 (defn- malli-schema-for [kind]
   (m/schema
    (case kind
      :kind/fn  [:map {:closed true}  [:kindly/f {:optional true} fn?]]
      :kind/reagent  [:map {:closed true}  [:html/deps {:optional true} [:sequential keyword?]]]
      [:map {:closed true}])))



 (defn- validate-options [note]
   (-> (malli-schema-for
        (:kind note))
       (m/validate (:kindly/options note))))

 (defn md5 [string]
   (let [digest (.digest (MessageDigest/getInstance "MD5") (.getBytes string "UTF-8"))]
     (apply str (map (partial format "%02x") digest))))


 (defn require-js
   "Generates a Hiccup representation of a `<script>` tag that dynamically loads a JavaScript library from  
   a given URL and ensures that a specific JavaScript object provided by the library is available before  
   executing a rendering command. This is used to include external JavaScript libraries in a Jupyter notebook environment.  
  
   **Parameters:**  
  
   - `url` (String): The URL of the JavaScript library to load.  
   - `js-object` (String): The name of the JavaScript object that the library defines (e.g., `'Plotly'`, `'Highcharts'`).  
   - `render-cmd` (String): The JavaScript code to execute after the library has been loaded and the object is available.  
  
   **Returns:**  
  
   - A Hiccup vector representing a `<script>` tag containing JavaScript code that loads the library and executes `render-cmd` once the library is loaded."
   [url render-cmd]
   (let [url-md5 (md5 url)
         render-cmd (str/replace render-cmd "XXXXX" url-md5)]
     [:script
      (format
       "  
  var clojupyter_loaded_marker_%s;  
  
  var currentScript_%s = document.currentScript;
  if (typeof clojupyter_loaded_marker_%s === 'undefined') {    
      clojupyter_loadScript_%s = src => new Promise(resolve => {  
      clojupyter_script_%s = document.createElement('script');  
      clojupyter_script_%s.src = src;  
      clojupyter_script_%s.async = false;
      clojupyter_script_%s.addEventListener('load', resolve);  
      document.head.appendChild(clojupyter_script_%s);  
      });  
   
     clojupyter_promise_%s=clojupyter_loadScript_%s('%s');  
       
     Promise.all([clojupyter_promise_%s]).then(() => {  
       console.log('%s loaded');  
       clojupyter_loaded_marker_%s = true;
       %s
        })  
       
     } else {
       console.log('%s already loaded');
       %s
     };  
     
  
 "
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5
       url-md5 url
       url-md5
       url-md5
       url-md5
       render-cmd
       url-md5
       render-cmd)]))


 (defn resolve-deps-tree [kinds options]
   (case (first kinds)
     :kind/reagent [{:js
                     ["https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"
                      "https://unpkg.com/react@18/umd/react.production.min.js"
                      "https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"
                      "https://cdn.jsdelivr.net/npm/d3-require@1"
                      "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.reagent.js"]}]
     :kind/scittle [{:js ["https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"
                          "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.cljs-ajax.js"
                          "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.reagent.js"]}]
     (js-deps/resolve-deps-tree kinds options)))


 (defn render-error-if-invalid-options [note]
   (if
    (validate-options note)
     nil
     [:div
      {:style "color:red"}
      (format
       "invalid options '%s' for kind %s : %s"
       (:kindly/options note)
       (:kind note)
       (me/humanize (m/explain
                     (malli-schema-for (:kind note))
                     (:kindly/options note))))]))

 (defn render-with-js [note render-cmd]
   (let [js-deps
         (->> (resolve-deps-tree
               (concat
                (-> note :kindly/options :html/deps)
                [(:kind note)])
               {})
              (map :js)
              flatten
              (remove nil?))]

     (concat
      (map-indexed
       #(require-js %2
                    "")
       (drop-last js-deps))

      [(require-js (last js-deps)
                   render-cmd)])))

 (defn require-deps-and-render
   "Generates a Hiccup representation to load the a JS library and execute a rendering command after it has been loaded.  
  
   **Parameters:**  
  
   - `render-cmd` (String): The JavaScript command to execute after js has been loaded.  
  
   **Returns:**  
  
   - A Hiccup vector that includes a `<script>` tag loading Plotly.js and executing the provided rendering command."
   [render-cmd note]
   (render-with-js note render-cmd))




 (defn highcharts->hiccup
   "Converts Highcharts chart data into a Hiccup vector that can render the chart within a Jupyter notebook using the Highcharts library. It sets up a `<div>` container and includes the necessary scripts to render the chart.  
  
   **Parameters:**  
  
   - `value` (Map): The Highcharts configuration object representing the chart to render.  
  
   **Returns:**  
  
   - A Hiccup vector containing a `<div>` with specified dimensions and a script that initializes the Highcharts chart with the provided configuration."
   [note]
   [:div {:style {:height "500px"
                  :width "500px"}}
    (require-deps-and-render (format "Highcharts.chart(currentScript_XXXXX.parentElement, %s);"
                                     (cheshire/encode (:value note)))
                             note)])

 (defn plotly->hiccup
   "Converts Plotly chart data into a Hiccup vector that can render the chart within a Jupyter notebook using the Plotly library.  
  
   **Parameters:**  
  
   - `value` (Map): The Plotly configuration object representing the chart to render.  
  
   **Returns:**  
  
   - A Hiccup vector containing a `<div>` with specified dimensions and a script that initializes the Plotly chart with the provided configuration."
   [note]

   [:div {:style {:height "500px"
                  :width "500px"}}
    (require-deps-and-render (format "Plotly.newPlot(currentScript_XXXXX.parentElement, %s);"
                                     (cheshire/encode (:value note)))
                             note)])

 (defn vega->hiccup [note]
   [:div
    (require-deps-and-render (format "vegaEmbed(currentScript_XXXXX.parentElement, %s);"
                                     (cheshire/encode (:value note)))
                             note)])

 (defn cytoscape>hiccup
   "Converts Cytoscape graph data into a Hiccup vector that can render the graph within a Jupyter notebook using the Cytoscape.js library.  
  
   **Parameters:**  
  
   - `value` (Map): The Cytoscape.js configuration object representing the graph to render.  
  
   **Returns:**  
  
   - A Hiccup vector containing a `<div>` with specified dimensions and a script that initializes the Cytoscape graph with the provided configuration."
   [note]
   [:div {:style {:height "500px"
                  :width "500px"}}
    (require-deps-and-render (format "  
                            value = %s;  
                            value['container'] = currentScript_XXXXX.parentElement;  
                            cytoscape(value);"
                                     (cheshire/encode (:value note)))
                             note)])

 (defn echarts->hiccup
   "Converts ECharts chart data into a Hiccup vector that can render the chart within a Jupyter notebook using the ECharts library.  
  
   **Parameters:**  
  
   - `value` (Map): The ECharts configuration object representing the chart to render.  
  
   **Returns:**  
  
   - A Hiccup vector containing a `<div>` with specified dimensions and a script that initializes the ECharts chart with the provided configuration."
   [note]
   [:div {:style {:height "500px"
                  :width "500px"}}
    (require-deps-and-render (format "  
                                    var myChart = echarts.init(currentScript_XXXXX.parentElement);  
                                    myChart.setOption(%s);"
                                     (cheshire/encode (:value note)))
 
                             note)])

 (defn tex->hiccup [note]
   [:div
    (require-deps-and-render (format
                              "katex.render(%s, currentScript_XXXXX.parentElement, {throwOnError: false});"
                              (cheshire/encode (format "$%s$"(first (:value note)))))
                             note)
    ])

 

 (defn scittle->hiccup [note]
   [:div
    (require-deps-and-render (format "scittle.core.eval_string('%s')" (str (:value note)))
                             note)])

 (defn scittle->hiccup-2 [note]
   (concat
    (require-deps-and-render "" note)
    [(->
      (to-hiccup-js/render {:value (:value note)})
      :hiccup)]
    [[:script "scittle.core.eval_script_tags()"]]))


 (defn reagent->hiccup [note]
   (let [id (gensym)]
     [:div

      (require-deps-and-render (format "scittle.core.eval_string('(require (quote [reagent.dom]))(reagent.dom/render %s (js/document.getElementById \"%s\"))')"
                                       (str (:value note))
                                       (str id))
                               note)
      [:div {:id (str id)}]]))

 (defn- default-to-hiccup-render
   "Provides a default rendering for notes by converting them into Hiccup format and preparing them for display in Clojupyter. It's a helper function used when no specific rendering method is available for a given kind.  
  
   **Parameters:**  
  
   - `note` (Map): A note containing information to render.  
  
   **Returns:**  
  
   - The `note` map augmented with `:clojupyter` and `:hiccup` keys, where `:clojupyter` contains the rendered HTML using `hiccup-html`, and `:hiccup` contains the Hiccup representation."
   [note]
   (to-hiccup/render note))
 

  (defn- render-recursively
    "Recursively renders a data structure into Hiccup format, applying a rendering function to each element in the structure. It is used for rendering collections like vectors, maps, sets, and sequences.  
  
   **Parameters:**  
  
   - `note` (Map): The note to render.  
   - `value` (Collection): The data structure to render recursively.  
   - `css-class` (String): The CSS class to apply to the rendered elements.  
   - `render` (Function): The function to apply to each element for rendering.  
  
   **Returns:**  
  
   - The `note` map augmented with `:clojupyter` containing the rendered HTML, and `:hiccup` containing the Hiccup representation."
    [note value css-class render]
    (walk/render-data-recursively note {:class css-class} value render))

 (defn- render-table-recursively
   "Recursively renders table data structures into Hiccup format.  
  
   **Parameters:**  
  
   - `note` (Map): The note containing the table data.  
   - `render` (Function): The function to apply to each element for rendering.  
  
   **Returns:**  
  
   - The `note` map augmented with `:clojupyter` containing the rendered HTML, and `:hiccup` containing the Hiccup representation.  
  
   **Note:**  
  
   - There is a typo fixed in the implementation where `:hjccup` is corrected to `:hiccup`.  
   - There is a TODO to address any known issues."
   [note render]
   (let [hiccup
        ;; TODO: https://github.com/scicloj/kindly-render/issues/23  
         (:hjccup (walk/render-table-recursively note render))]
     (assoc note :hiccup hiccup)))

 (defn render-js
   "Renders JavaScript-based visualizations by converting the visualization data into Hiccup format and preparing it for display in Clojupyter.  
  
   **Parameters:**  
  
   - `note` (Map): The note containing the visualization data.  
   - `value` (Any): The data to render.  
   - `->hiccup-fn` (Function): A function that takes `value` and returns a Hiccup vector.  
  
   **Returns:**  
  
   - The `note` map augmented with `:clojupyter` containing the rendered HTML, and `:hiccup` containing the Hiccup representation."
   [note ->hiccup-fn]
   (assoc note :hiccup (->hiccup-fn note)))

 (defmulti render-advice :kind)

 (defn render
   "Used to dispatch rendering to the appropriate `render-advice` method based on the `:kind` of the note.  
  
   **Parameters:**  
  
   - `note` (Map): The note containing the data and metadata to render.  
  
   **Returns:**  
  
   - The result of applying the appropriate `render-advice` method to the note."
   [note]

   (let

    [advised-note (walk/advise-render-style note render-advice)

     error-hiccup-or-nil (render-error-if-invalid-options advised-note)]
     (if error-hiccup-or-nil
       (assoc note
              :hiccup error-hiccup-or-nil)
       advised-note)))

 (defmethod render-advice :default
   [{:as note :keys [value kind]}]
   (let [hiccup (if kind
                  [:div
                   [:div "Unimplemented: " [:code (pr-str kind)]]
                   [:code (pr-str value)]]
                  (str value))]
     (assoc note
            :hiccup hiccup)))

 (defmethod render-advice :kind/plotly
   [note]
   (render-js note  plotly->hiccup))

 (defmethod render-advice :kind/cytoscape
   [note]
   (render-js note  cytoscape>hiccup))

 (defmethod render-advice :kind/highcharts
   [note]
   (render-js note   highcharts->hiccup))

 (defmethod render-advice :kind/echarts
   [note]
   (render-js note  echarts->hiccup))

 (defmethod render-advice :kind/scittle
   [note]
   (render-js note  scittle->hiccup-2))

 (defmethod render-advice :kind/reagent
   [note]
   (render-js note  reagent->hiccup))



 (defmethod render-advice :kind/image
   [{:as note :keys [value]}]
   (let [out (io/java.io.ByteArrayOutputStream.)
         v
         (if (sequential? value)
           (first value)
           value)
         _ (ImageIO/write v "png" out)
         hiccup [:img {:src (str "data:image/png;base64,"
                                 (-> out .toByteArray b64/encode String.))}]]


     (assoc note
            :hiccup hiccup)))

 

 (defmethod render-advice :kind/vega-lite
   [note]
   (render-js
    (assoc note :kind :kind/vega)
    vega->hiccup))


 (defmethod render-advice :kind/vega
   [note]
   (render-js note  vega->hiccup))


(defmethod render-advice :kind/md
  [note]
  (default-to-hiccup-render note))

(defmethod render-advice :kind/tex
  [note]
  (render-js note tex->hiccup))
  

 

(defmethod render-advice :kind/dataset
  [note]
  (default-to-hiccup-render note))

(defmethod render-advice :kind/code
  [note]
  (default-to-hiccup-render note))

(defmethod render-advice :kind/pprint
  [note]
  (default-to-hiccup-render note))

(defmethod render-advice :kind/hidden
  [note]
  (default-to-hiccup-render note))

(defmethod render-advice :kind/video
  [note]
  (default-to-hiccup-render note))



(defmethod render-advice :kind/html
  [{:as note :keys [value]}]
  (assoc note
         :hiccup (first value)))

(defmethod render-advice :kind/hiccup
  [note]
  (let [hiccup
        (:hiccup (walk/render-hiccup-recursively note render))]
    (assoc note
           :hiccup hiccup)))

(defmethod render-advice :kind/vector
  [{:as note :keys [value]}]
  (render-recursively note value "kind-vector" render))

(defmethod render-advice :kind/map
  [{:as note :keys [value]}]
  (render-recursively note (apply concat value) "kind-map" render))

(defmethod render-advice :kind/set
  [{:as note :keys [value]}]
  (render-recursively note value "kind-set" render))

(defmethod render-advice :kind/seq
  [{:as note :keys [value]}]
  (render-recursively note value "kind-seq" render))

(defmethod render-advice :kind/table
  [note]
  (render-table-recursively note render))

(defmethod render-advice :kind/fn
  [{:keys [value form] :as note}]

  (let [new-note
        (if (vector? value)
          (let [f (first value)]
            (render {:value (apply f (rest value))
                     :form form}))

          (let [f (or (:kindly/f value)
                      (-> note :kindly/options :kindly/f))]
            (render {:value (f (dissoc value :kindly/f))
                     :form form})))]

    (assoc note
           :hiccup (:hiccup new-note))))

(defmethod render-advice :kind/var
  [{:keys [value form] :as note}]
  (let [sym (second value)
        s (str "#'" (str *ns*) "/" sym)]
    (assoc note
           :hiccup s)))

(defn- render-as-clojupyter
  "Determines whether a given value should be rendered directly by Clojupyter without further processing. It checks if the value is `nil`, a known displayable type, or already a rendered MIME type.  
  
   **Parameters:**  
  
   - `form` (Any): The form that was evaluated.  
   - `value` (Any): The evaluated value of the form.  
  
   **Returns:**  
  
   - `true` if the value should be rendered directly by Clojupyter; `false` otherwise."
  [form value]
  (let [kindly-advice (kindly-advice/advise {:form form
                                             :value value})]
    (or
     (nil? value)
     (str/starts-with? (.getName (class value)) "clojupyter.misc.display$render_mime")
     (contains?
      #{clojupyter.misc.display.Latex
        clojupyter.misc.display.HiccupHTML
        clojupyter.misc.display.Markdown
        clojupyter.misc.display.HtmlString
        java.awt.image.BufferedImage}
      (class value)))))

(defn kind-eval
  "Evaluates a Clojure form and returns a value suitable for display in Clojupyter. If the evaluated value is of a type that Clojupyter can render directly, it is returned as-is. Otherwise, it applies custom rendering logic to prepare the value for display.  
  
   **Parameters:**  
  
   - `form` (Any): The Clojure form to evaluate.  
  
   **Returns:**  
  
   - The evaluated value if it's suitable for direct display.  
   - The custom-rendered content if additional processing is needed for display in Clojupyter.  
  
   **Behavior:**  
  
   1. Evaluates `form` to obtain `value`.  
   2. Uses `render-as-clojupyter` to determine if `value` can be displayed directly.  
   3. If `value` is suitable for direct display or is a Clojure var, it is returned.  
   4. Otherwise, it constructs a `note` with `:value` and `:form`, renders it with the `render` function, and returns the `:clojupyter` rendering."
  [form]
  ;(println :kind-eval--form form)  
  
  (let [value (eval form)]
    ;(println :kind-eval--value value)  
    ;(println :kind-eval--value-class (class value))  
    ;(println :kind-eval--advise kindly-advice)  
    (def value value)
    (def form form)

    (if (or (render-as-clojupyter form value)
            (var? value))
      value
      (->

       (render {:value value
                :form form})
       :hiccup
       dis/->HiccupHTML))))


(comment
  [:map {:closed true} [:width :int?
                        :height :int?]]

  (m/explain (m/schema  [:map {:closed true}
                         [:style [:map  {:closed true
                                         :description "HTML styling of the plot"}
                                  [:width {:description "Width in px of the plot"} int?]
                                  [:height {:description "Height in px of the plot"} int?]]]])
             {:style {:width 1
                      :height 2}}))