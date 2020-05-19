(ns clojupyter.widgets.ipywidgets
  "Interactive widgets for clojupyter. It defines the widget constructors by parsing the json model published by ipywidgets project.
  It uses the json to produce default value maps, widget names, specs and constructors"
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [clojupyter.kernel.comm-atom :as ca]
   [clojupyter.state :as state]
   [clojupyter.util-actions :as u!]
   [clojure.spec.alpha :as s]
   [io.simplect.compose :refer [def-]]
   [clojupyter.log :as log]))

(def WIDGET-TARGET "jupyter.widget")
(def WIDGET-PROTOCOL-VERSION-MAJOR 2)
(def WIDGET-PROTOCOL-VERSION-MINOR 0)
(def- SPECS (-> "ipywidgets/schema/jupyterwidgetmodels.latest.json"
                io/resource
                slurp
                json/read-str))

(def- REPLACEMENTS {"b''" (byte-array 0)})

;;----------------------------------------------------------------------------------------------
;; Predicates
;;----------------------------------------------------------------------------------------------

(def- PREDICATES {"bool" boolean?
                  "int" integer?
                  "float" float?
                  "string" string?
                  "bytes" bytes?
                  "Date" (constantly true)}) ;; Date is not yet implemented.

(defn- min<max?
  [{:keys [min max]}]
  (< min max))

(defn- min<=val<=max?
  [{:keys [min max value]}]
  (<= min value max))

(defn- valid-exp-value?
  [{:keys [min max base value]}]
  (<= (Math/pow base min) value (Math/pow base max)))

(defn- valid-value-pair?
  [{:keys [min max] [lower upper] :value}]
  (<= min lower upper max))

(defn- valid-index?
  [{:keys [index _options_labels]}]
  (or (nil? index)
      (<= 0 index (dec (count _options_labels)))))

(defn- valid-index-range?
  [{[lower upper] :index labels :_options_labels}]
  (<= 0 lower upper (dec (count labels))))

(defn- valid-indicies?
  [{:keys [index _options_labels]}]
  (every? (set (range (count _options_labels))) index))

(def widget? ca/comm-atom?)
(def open? ca/open?)
(def closed? ca/closed?)

;;------------------------------------------------------------------------------------------
;; Special handling of selection widgets
;;------------------------------------------------------------------------------------------

(defn- expand-options
  [{opts :options :as state-map}]
  (if (seq opts)
    (cond
      (map? opts)
      (assoc state-map :_options_labels (->> (keys opts) (map name) vec)
                       :_options_values (->> (vals opts) vec))

      (and (every? #(= 2 (count %)) opts) (every? coll? opts))
      (assoc state-map :_options_labels (->> opts (map first) vec)
                       :_options_values (->> opts (map second) vec))

      (or (every? string? opts) (every? keyword? opts))
      (assoc state-map :_options_labels (->> opts (map name) vec)
                       :_options_values (->> opts (map name) vec)))
    (assoc state-map :_options_labels [] :_options_values [])))

(defn- index-from-value
  [{value :value values :_options_values :as state-map}]
  (if (coll? value)
    (assoc state-map :index (vec (map #(.indexOf values %) value)))
    (assoc state-map :index (.indexOf values value))))

(defn- value-from-index
  [{index :index values :_options_values :as state-map}]
  (if (coll? index)
    (assoc state-map :value (vec (map (partial nth values) index)))
    (assoc state-map :value (nth values index))))

(defn- selection-watcher
  [_ ref {old-options :options old-index :index old-value :value} {new-options :options new-index :index new-value :value :as new-state}]
  (cond
    (not= old-options new-options) (swap! ref (comp value-from-index  expand-options))
    (not= old-index new-index) (swap! ref value-from-index)
    (not= old-value new-value) (swap! ref index-from-value)))

;;------------------------------------------------------------------------------------------
;; Constructors
;;------------------------------------------------------------------------------------------

(defn- def-widget
  "Returns a default widget map from json spec."
  [{attrs "attributes"}]
  (reduce merge
    (for [{n "name" v "default"} attrs]
      (if (or (= n "layout") (= n "style"))
        ;; We avoid cyclic dependencies by passing nil to layout and style keys. (make-widget fn already depends on def-widget fn)
        ;; Strict following of the widgets schema, require us to pass a reference to the default layout and style widget instead.
        ;; Currently, a widget with null reference to layout and style look and act the same way as a widget with reference to default layout and style widgets.
        {(keyword n) nil}
        {(keyword n) (get REPLACEMENTS v v)}))))

(defn- widget-name
  "Returns the widget name of a json schema spec.
  spec is a map of attributes that define the widget.
  Transforms the model name from e.g. IntSliderModel to int-slider"
  [spec]
  (let [name (get-in spec ["model" "name"])]
    (assert (and (string? name)
                 (> (count name) 5)
                 (= "Model" (subs name (- (count name) 5) (count name)))))
    (csk/->kebab-case-symbol (subs name 0 (- (count name) 5)))))

(defn- make-widget
  "Returns a fn that builds and returns a widget of type defined by spec.
  spec is a map of attributes that define de widget."
  [spec]
  (fn constructor
    ([] (constructor {}))
    ([state-map]
     (let [{jup :jup req-msg :req-message} (state/current-context)]
       (constructor jup req-msg WIDGET-TARGET (u!/uuid) state-map)))
    ([jup req-msg target comm-id]
     (constructor jup req-msg target comm-id {}))
    ([jup req-msg target comm-id state-map]
     (let [{d-index :index :as d-widget} (def-widget spec)
           viewer-keys (set (keys d-widget))
           w-name (widget-name spec)
           full-k (keyword "clojupyter.widgets.ipywidgets" (str w-name))
           widget (ca/create jup req-msg target comm-id viewer-keys (merge (with-meta d-widget {:spec full-k}) state-map))
           valid-spec? (partial s/valid? full-k)]
      ;; When widget is a selector, we need to sync :options, _options_values, _options_labels, :index and :value
      ;; We manage that by attaching a watcher fn to keep them in sync
       (when (#{'dropdown 'radio-buttons 'select 'selection-slider 'selection-range-slider 'toggle-buttons 'select-multiple}
               w-name)
         (swap! widget expand-options)
         (if (not= d-index (:index @widget))
           (swap! widget value-from-index)
           (when (:value @widget)
             (swap! widget index-from-value)))
         (ca/watch widget :internal-consistency selection-watcher))
       (ca/validate widget
         (condp contains? w-name
            #{'bounded-float-text 'bounded-int-text 'float-progress
              'float-slider 'int-slider 'int-progress 'play}        (every-pred valid-spec? min<=val<=max? min<max?)
            #{'float-range-slider 'int-range-slider}                (every-pred valid-spec? valid-value-pair? min<max?)
            #{'dropdown 'selection-slider 'select}                  (every-pred valid-spec? valid-index?)
            #{'select-multiple}                                     (every-pred valid-spec? valid-indicies?)
            #{'selection-range-slider}                              (every-pred valid-spec? valid-index-range?)
            #{'float-log-slider}                                    (every-pred valid-spec? valid-exp-value? min<max?)
            valid-spec?))
       (ca/insert widget)))))

(defn- spec-widget!
  "Defines the specs for widgets interpreted from the spec map."
  [{attrs "attributes" :as spec}]
  (let [n (widget-name spec)
        k-ns (str (ns-name *ns*) \= n)
        q-keys (->> attrs
                    (map #(% "name"))
                    (map (partial keyword k-ns))
                    vec)]

    (eval `(s/def ~(keyword (str (ns-name *ns*)) (str n)) (s/keys :req-un ~q-keys)))

    ;; Iterate the attributes and define a spec for every k/v pair of widget.
    (doall
      (for [{type "type" nilable? "allow_none" items "items" widget "widget" k-name "name" enum "enum"} attrs]
        (let [full-k (keyword k-ns k-name)]
          (cond

            (seq enum)
            (eval `(s/def ~full-k ~(if nilable?
                                     (s/nilable (set enum))
                                     (set enum))))

            (and (not= "array" type)
                 (not= "reference" type)
                 (not (nil? type)))
            (eval `(s/def ~full-k ~(if nilable?
                                     (s/nilable (PREDICATES type))
                                     (PREDICATES type))))

            (= "reference" type)
            (let [ref (csk/->kebab-case-symbol widget)
                  ref-k (keyword (str (ns-name *ns*)) (str ref))]
              (eval `(s/def ~full-k ~(s/nilable (s/and widget? #(s/valid? ref-k @%))))))

            (= "array" type)
            (let [array-item-type (get items "type")]
              (case array-item-type

                "object" (eval `(s/def ~full-k ~(s/coll-of map? :kind vector?)))

                "reference" (eval `(s/def ~full-k ~(s/coll-of (case (get items "widget")
                                                                "Axis" (s/and widget? #(s/valid? ::controller-axis @%))
                                                                "Button" (s/and widget? #(s/valid? ::controller-button @%))
                                                                "Widget" widget?
                                                                (log/warn "Can't generate spec for " full-k " of type array of " array-item-type))
                                                              :kind vector?)))

                ;; When type is array and the items map is missing, the spec is ambigous:
                ;; it can be a pair of ints, a pair of floats, or a pair of widget/trait_name
                nil (cond
                      (= n 'float-range-slider)
                      (eval `(s/def ~full-k ~(s/and (s/coll-of float? :kind vector? :count 2)
                                                    (partial apply <=))))

                      (or (= n 'selection-range-slider)
                          (= n 'int-range-slider))
                      (eval `(s/def ~full-k ~(s/and (s/coll-of int? :kind vector? :count 2)
                                                    (partial apply <=))))

                      :else ;; pair of [widget, trait_name].
                      (eval `(s/def ~full-k ~(fn [[w attr & rest :as v]]
                                               (boolean (and (widget? w)
                                                             (keyword? attr)
                                                             (contains? @w attr)
                                                             (nil? rest)
                                                             (vector? v)))))))

                ;; Vector of known types, boolean, string, int etc.
                (eval `(s/def ~full-k ~(if nilable?
                                         (s/nilable (s/coll-of (PREDICATES array-item-type) :kind vector?))
                                         (s/coll-of (PREDICATES array-item-type) :kind vector?))))))
            :else (log/warn "Can't generate spec for " full-k " of type" type)))))
    nil))

(doall (for [spec SPECS]
         (let [w-name (widget-name spec)
               w-cons (make-widget spec)]
           (eval `(def ~w-name ~w-cons))
           (spec-widget! spec))))
