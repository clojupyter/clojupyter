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
      (ca/create-and-insert jup req-msg target comm-id (merge (def-widget spec) state-map)))))

(defn- widget-name
  [spec]
  (let [name (get-in spec ["model" "name"])]
    (assert (and (string? name)
                 (> (count name) 5)
                 (= "Model" (subs name (- (count name) 5) (count name)))))
    (csk/->kebab-case-symbol (subs name 0 (- (count name) 5)))))

(doall (for [spec SPECS]
         (let [w-name (widget-name spec)
               w-cons (make-widget spec)]
           (eval `(def ~w-name ~w-cons)))))
