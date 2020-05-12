(ns clojupyter.widgets.ipywidgets
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [clojupyter.kernel.comm-atom :as ca]
   [clojupyter.state :as state]
   [clojupyter.util-actions :as u!]
   [clojure.spec.alpha :as s]
   [io.simplect.compose :refer [def-]]))

(def WIDGET-TARGET "jupyter.widget")
(def WIDGET-PROTOCOL-VERSION-MAJOR 2)
(def WIDGET-PROTOCOL-VERSION-MINOR 0)
(def SPECS (-> "ipywidgets/schema/jupyterwidgetmodels.latest.json"
                io/resource
                slurp
                json/read-str))
#_
(defn widget-display-data
  ([model-ref {:keys [metadata transient version-major version-minor]}]
   (let [version-major (or version-major WIDGET-PROTOCOL-VERSION-MAJOR)
         version-minor (or version-minor WIDGET-PROTOCOL-VERSION-MINOR)
         metadata (or metadata {})
         transient (or transient {})]
     {:application/vnd.jupyter.widget-view+json
      {:model_id model-ref
       :version_major version-major
       :version_minor version-minor}
      :text/plain (str "display_data: " model-ref)})))

(def- REPLACEMENTS {"b''" (byte-array 0)})

(def- PREDICATES {"bool" boolean?
                  "int" integer?
                  "float" float?
                  "string" string?
                  "bytes" bytes?
                  "Date" (constantly true)}) ;; Date is not yet implemented.

(defn min<max?
  [{:keys [min max]}]
  (< min max))

(defn min<=val<=max?
  [{:keys [min max value]}]
  (<= min value max))

(defn valid-exp-value?
  [{:keys [min max base value]}]
  (<= (Math/pow base min) value (Math/pow base max)))

(defn valid-value-pair?
  [{:keys [min max] [lower upper] :value}]
  (<= min lower upper max))

(defn valid-index?
  [{:keys [index _options_labels]}]
  (or (nil? index)
      (<= 0 index (dec (count _options_labels)))))

(defn valid-index-range?
  [{[lower upper] :index labels :_options_labels}]
  (<= 0 lower upper (dec (count labels))))

(defn valid-indicies?
  [{:keys [index _options_labels]}]
  (every? (set (range (count _options_labels))) index))

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

(defn- def-widget
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
  [spec]
  (let [name (get-in spec ["model" "name"])]
    (assert (and (string? name)
                 (> (count name) 5)
                 (= "Model" (subs name (- (count name) 5) (count name)))))
    (csk/->kebab-case-symbol (subs name 0 (- (count name) 5)))))

(defn- make-widget
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
           widget (ca/create jup req-msg target comm-id viewer-keys (merge d-widget state-map))
           w-name (widget-name spec)
           full-k (keyword "clojupyter.widgets.ipywidgets" (str w-name))
           valid-spec? (partial s/valid? full-k)]
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

            (or (not= "array" type)
                (not= "reference" type)
                (not (nil? type)))
            (eval `(s/def ~full-k ~(if nilable?
                                     (s/nilable (PREDICATES type))
                                     (PREDICATES type))))

            (= "reference" type)
            (eval `(s/def ~full-k (s/nilable (keyword (str (ns-name *ns*)) (str ~n)))))

            (= "array" type)
            (let [array-item-type (get items "type")]
              (case array-item-type
                "object" nil ;; Not yet implemented

                "reference" (eval `(s/def ~full-k ~(case (get items "widget")
                                                     "Axis" ::controller-axis
                                                     "Button" ::controller-button
                                                     "Widget" nil ;; Not yet implemented. TODO: Add a predicate for any widget
                                                     nil))) ;; TODO: Add logging if unknown type of reference

                ;; When type is array and the items map is missing, the spec is ambigous:
                ;; it can be a pair of ints, a pair of floats, or a pair of widget/trait_name
                nil (cond
                      (= n 'float-range-slider)
                      (eval `(s/def ~full-k ~(s/and (s/coll-of float? :kind vector? :count 2)
                                                    (partial apply <=)))) ;; TODO: Check to see if tuple is strictly sorted

                      (or (= n 'selection-range-slider)
                          (= n 'int-range-slider))
                      (eval `(s/def ~full-k ~(s/and (s/coll-of int? :kind vector? :count 2)
                                                    (partial apply <=)))) ;; TODO: Check to see if tuple is strictly sorted

                      :else
                      nil) ;; pair of [widget, trait_name]. TODO: Implement

                (eval `(s/def ~full-k ~(if nilable?
                                         (s/nilable (s/coll-of (PREDICATES array-item-type) :kind vector?))
                                         (s/coll-of (PREDICATES array-item-type) :kind vector?))))))))))
    nil))

(doall (for [spec SPECS]
         (let [w-name (widget-name spec)
               w-cons (make-widget spec)]
           (eval `(def ~w-name ~w-cons))
           (spec-widget! spec))))
