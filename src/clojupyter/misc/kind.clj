(ns clojupyter.misc.kind
   (:require
    [clojupyter.misc.display :as dis]
    [clojure.string :as str]
    [scicloj.kindly-advice.v1.api :as kindly-advice]
    [scicloj.kindly-render.note.to-hiccup :as to-hiccup]
    [scicloj.kindly-render.note.to-hiccup-inline-js :as to-hiccup-inline-js]
    [scicloj.kindly-render.shared.walk :as walk]))


 (defmulti render-advice :kind)

 (defn render [note]
   (walk/advise-render-style note render-advice))

 (defmethod render-advice :default [note]
   (to-hiccup/render note))

 (defmethod render-advice :kind/plotly [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/cytoscape [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/highcharts [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/echarts [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/scittle [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/reagent [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/image [note]
   (to-hiccup/render note))

 (defmethod render-advice :kind/vega-lite [note]
   (to-hiccup-inline-js/render note))

 (defmethod render-advice :kind/vega [note]
   (to-hiccup-inline-js/render note))
 
(defmethod render-advice :kind/tex [note]
  (to-hiccup-inline-js/render note))
  
(defmethod render-advice :kind/md [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/dataset [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/md [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/code [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/pprint [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/hidden [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/video [note]
  (to-hiccup/render note))

(defmethod render-advice :kind/html [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/vector [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/map [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/set [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/seq [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/hiccup [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/table [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/fn [note]
  (to-hiccup/render (assoc note :render-fn render)))

(defmethod render-advice :kind/var [note]
  (to-hiccup/render note))

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
  (let [value (eval form)]
    (if (or (render-as-clojupyter form value)
            (var? value))
      value
      (->
       (render {:value value
                :form form})
       :hiccup
       dis/->HiccupHTML))))


