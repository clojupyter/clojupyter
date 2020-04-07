(ns clojupyter.widgets.ipywidgets
  (:require
   [camel-snake-kebab.core :as csk]
   [clojupyter.kernel.comm-atom :as ca]
   [clojupyter.state :as state]
   [clojupyter.util-actions :as u!]
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [io.simplect.compose :refer [def- c C p P]]))

;; The strings defining the widgets are Emacs Org-Mode tables.  They were created by pasting data
;; from the tables in
;;
;;     https://github.com/jupyter-widgets/ipywidgets/blob/master/packages/schema/jupyterwidgetmodels.latest.md
;;
;; from Goole Chrome into Emacs and subsequently processed by Emacs using
;; `org-table-create-or-convert-from-region` (`C-c |` in `org-mode`, but can be done in any mode).

(def WIDGET-TARGET "jupyter.widget")
(def WIDGET-PROTOCOL-VERSION-MAJOR 2)
(def WIDGET-PROTOCOL-VERSION-MINOR 0)

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

(def- REPLACEMENTS {"null" 	nil
                    "false"	false
                    "true" 	true
                    "[]" 	[]
                    "{}" 	{}
                    "[0, 0]"	[0 0]
                    "[0, 1]"	[0 1]
                    "[0.0, 1.0]" [0.0 1.0]})

(defn- wrap-strchk
  [strfn]
  #(if (string? %) (strfn %) %))

(def- clean-default-string
  (C #(if (= % "b''") nil %)
     (wrap-strchk #(if (re-find #"^\d+$" %) (edn/read-string (str "10r" %)) %))
     (wrap-strchk #(if (re-find #"^\d+\.\d" %) (edn/read-string %) %))
     (wrap-strchk #(str/replace % "'" ""))
     (wrap-strchk #(if (re-find #"^reference to" %) nil %))
     (wrap-strchk #(if (re-find #"^array of" %) [] %))
     #(get REPLACEMENTS % %)))

(defn- parse-orgtable-with-headers
  [s]
  (with-open [r (java.io.BufferedReader. (java.io.StringReader. s))]
    (let [strip-empty (fn [ss]
                        (assert (= (count ss) 5))
                        (assert (= (first ss) ""))
                        (vec (rest ss)))
           process-line (C (P str/split #"\|") (p mapv str/trim) strip-empty)
          lines (->> (line-seq r) (remove (p = "")))
          hdr (->> lines first process-line (map (C str/lower-case keyword)))]
      (assert (= hdr [:attribute :type :default :help]))
      (->> lines
           (drop 1)
           (map process-line)
           (map (p zipmap hdr))
           (map (P update :default clean-default-string))
           doall
           (s/assert (s/coll-of (s/keys :req-un [::attribute ::type ::default ::help])))))))

(defn defipywidget*
  [docstring]
  (let [attr-defs (parse-orgtable-with-headers docstring)
        attr-map (->> attr-defs (map (juxt (C :attribute symbol) :default)) (into {}))
        model-name (get attr-map '_model_name)
        _ (assert (and (string? model-name)
                       (> (count model-name) 5)
                       (= "Model" (subs model-name (- (count model-name) 5) (count model-name))))
                  (str "defipywidget - model-name assumed to end in 'Model': " model-name))
        widget-name (-> model-name
                        (subs 0 (- (count model-name) 5))
                        csk/->kebab-case-symbol)
        map-name (symbol (str widget-name "-map"))
        model-module-version (get attr-map '_model_module_version)
        view-module-version (get attr-map '_view_module_version)
        docstring (str "\n" docstring "\n"
                       "IPYWIDGET MODEL-MODULE-VERSION:\t" model-module-version "\n"
                       "IPYWIDGET VIEW-MODULE-VERSION:\t" view-module-version "\n")]
    `(do
       (defn ~map-name
         ~(str "\nReturns a map representing an IPYWIDGET `" model-name "`.\n" docstring)
         ([]
          (~map-name {}))
         ([{:keys ~(-> attr-map keys sort vec)}]
          ~(->> (for [[k default] attr-map]
                  [(keyword k) `(or ~k ~default)])
                (into {}))))
       (defn ~widget-name
         ([]
          (~widget-name {}))
         ([state-map#]
          (let [{jup# :jup req-message# :req-message} (state/current-context)]
            (~widget-name jup# req-message# WIDGET-TARGET (u!/uuid) state-map#)))
         ([jup# req-message# target-name# comm-id#]
          (~widget-name jup# req-message# target-name# comm-id# {}))
         ([jup# req-message# target-name# comm-id# state-map#]
          (ca/create-and-insert jup# req-message# target-name# comm-id# (~map-name state-map#))))
       #_(alter-meta! (var ~widget-name)
                    assoc :arglists ~(let [base-arglist '[jup req-message target-name comm-id]]
                                       `'[~base-arglist  ~(conj base-arglist {:keys (-> attr-map keys sort vec)})]))
       #_(alter-meta! (var ~widget-name)
                    assoc :doc ~(str "Creates a COMM-ATOM with the result of calling `" map-name "`.\n" docstring))
       (var ~map-name))))

(defmacro defipywidget
  [org-table-string]
  (defipywidget* org-table-string))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; WIDGET DEFINITIONS
;;; ------------------------------------------------------------------------------------------------------------------------

(defipywidget ;; layout
"
| Attribute             | Type                                                                                                                                                  | Default                 | Help                                                             |
| _model_module         | string                                                                                                                                                | '@jupyter-widgets/base' | The namespace for the model.                                     |
| _model_module_version | string                                                                                                                                                | '1.2.0'                 | A semver requirement for namespace version containing the model. |
| _model_name           | string                                                                                                                                                | 'LayoutModel'           |                                                                  |
| _view_module          | string                                                                                                                                                | '@jupyter-widgets/base' |                                                                  |
| _view_module_version  | string                                                                                                                                                | '1.2.0'                 |                                                                  |
| _view_name            | string                                                                                                                                                | 'LayoutView'            |                                                                  |
| align_content         | null or string (one of 'flex-start', 'flex-end', 'center', 'space-between', 'space-around', 'space-evenly', 'stretch', 'inherit', 'initial', 'unset') | null                    | The align-content CSS attribute.                                 |
| align_items           | null or string (one of 'flex-start', 'flex-end', 'center', 'baseline', 'stretch', 'inherit', 'initial', 'unset')                                      | null                    | The align-items CSS attribute.                                   |
| align_self            | null or string (one of 'auto', 'flex-start', 'flex-end', 'center', 'baseline', 'stretch', 'inherit', 'initial', 'unset')                              | null                    | The align-self CSS attribute.                                    |
| border                | null or string                                                                                                                                        | null                    | The border CSS attribute.                                        |
| bottom                | null or string                                                                                                                                        | null                    | The bottom CSS attribute.                                        |
| display               | null or string                                                                                                                                        | null                    | The display CSS attribute.                                       |
| flex                  | null or string                                                                                                                                        | null                    | The flex CSS attribute.                                          |
| flex_flow             | null or string                                                                                                                                        | null                    | The flex-flow CSS attribute.                                     |
| grid_area             | null or string                                                                                                                                        | null                    | The grid-area CSS attribute.                                     |
| grid_auto_columns     | null or string                                                                                                                                        | null                    | The grid-auto-columns CSS attribute.                             |
| grid_auto_flow        | null or string (one of 'column', 'row', 'row dense', 'column dense', 'inherit', 'initial', 'unset')                                                   | null                    | The grid-auto-flow CSS attribute.                                |
| grid_auto_rows        | null or string                                                                                                                                        | null                    | The grid-auto-rows CSS attribute.                                |
| grid_column           | null or string                                                                                                                                        | null                    | The grid-column CSS attribute.                                   |
| grid_gap              | null or string                                                                                                                                        | null                    | The grid-gap CSS attribute.                                      |
| grid_row              | null or string                                                                                                                                        | null                    | The grid-row CSS attribute.                                      |
| grid_template_areas   | null or string                                                                                                                                        | null                    | The grid-template-areas CSS attribute.                           |
| grid_template_columns | null or string                                                                                                                                        | null                    | The grid-template-columns CSS attribute.                         |
| grid_template_rows    | null or string                                                                                                                                        | null                    | The grid-template-rows CSS attribute.                            |
| height                | null or string                                                                                                                                        | null                    | The height CSS attribute.                                        |
| justify_content       | null or string (one of 'flex-start', 'flex-end', 'center', 'space-between', 'space-around', 'inherit', 'initial', 'unset')                            | null                    | The justify-content CSS attribute.                               |
| justify_items         | null or string (one of 'flex-start', 'flex-end', 'center', 'inherit', 'initial', 'unset')                                                             | null                    | The justify-items CSS attribute.                                 |
| left                  | null or string                                                                                                                                        | null                    | The left CSS attribute.                                          |
| margin                | null or string                                                                                                                                        | null                    | The margin CSS attribute.                                        |
| max_height            | null or string                                                                                                                                        | null                    | The max-height CSS attribute.                                    |
| max_width             | null or string                                                                                                                                        | null                    | The max-width CSS attribute.                                     |
| min_height            | null or string                                                                                                                                        | null                    | The min-height CSS attribute.                                    |
| min_width             | null or string                                                                                                                                        | null                    | The min-width CSS attribute.                                     |
| object_fit            | null or string (one of 'contain', 'cover', 'fill', 'scale-down', 'none')                                                                              | null                    | The object-fit CSS attribute.                                    |
| object_position       | null or string                                                                                                                                        | null                    | The object-position CSS attribute.                               |
| order                 | null or string                                                                                                                                        | null                    | The order CSS attribute.                                         |
| overflow              | null or string                                                                                                                                        | null                    | The overflow CSS attribute.                                      |
| overflow_x            | null or string (one of 'visible', 'hidden', 'scroll', 'auto', 'inherit', 'initial', 'unset')                                                          | null                    | The overflow-x CSS attribute (deprecated).                       |
| overflow_y            | null or string (one of 'visible', 'hidden', 'scroll', 'auto', 'inherit', 'initial', 'unset')                                                          | null                    | The overflow-y CSS attribute (deprecated).                       |
| padding               | null or string                                                                                                                                        | null                    | The padding CSS attribute.                                       |

| right                 | null or string                                                                                                                                        | null                    | The right CSS attribute.                                         |
| top                   | null or string                                                                                                                                        | null                    | The top CSS attribute.                                           |
| visibility            | null or string (one of 'visible', 'hidden', 'inherit', 'initial', 'unset')                                                                            | null                    | The visibility CSS attribute.                                    |
| width                 | null or string                                                                                                                                        | null                    | The width CSS attribute.                                         |
")

(defipywidget ;; accordion
"
| Attribute             | Type                                                       | Default                     | Help                                                                                                                              |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element                                                                                         |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                                                                                                                   |
| _model_module_version | string                                                     | '1.5.0'                     |                                                                                                                                   |
| _model_name           | string                                                     | 'AccordionModel'            |                                                                                                                                   |
| _titles               | object                                                     | {}                          | Titles of the pages                                                                                                               |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                                                                                                                   |
| _view_module_version  | string                                                     | '1.5.0'                     |                                                                                                                                   |
| _view_name            | string                                                     | 'AccordionView'             |                                                                                                                                   |
| box_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the box.                                                                                             |
| children              | array of reference to Widget widget                        | []                          | List of widget children                                                                                                           |
| layout                | reference to Layout widget                                 | reference to new instance   |                                                                                                                                   |
| selected_index        | null or number (integer)                                   | 0                           | The index of the selected page. This is either an integer selecting a particular sub-widget, or None to have no widgets selected. |
")

(defipywidget ;; audio
"
| Attribute             | Type                       | Default                     | Help                                                                                |
| _dom_classes          | array of string            | []                          | CSS classes applied to widget DOM element                                           |
| _model_module         | string                     | '@jupyter-widgets/controls' |                                                                                     |
| _model_module_version | string                     | '1.5.0'                     |                                                                                     |
| _model_name           | string                     | 'AudioModel'                |                                                                                     |
| _view_module          | string                     | '@jupyter-widgets/controls' |                                                                                     |
| _view_module_version  | string                     | '1.5.0'                     |                                                                                     |
| _view_name            | string                     | 'AudioView'                 |                                                                                     |
| autoplay              | boolean                    | true                        | When true, the audio starts when it's displayed                                     |
| controls              | boolean                    | true                        | Specifies that audio controls should be displayed (such as a play/pause button etc) |
| format                | string                     | 'mp3'                       | The format of the audio.                                                            |
| layout                | reference to Layout widget | reference to new instance   |                                                                                     |
| loop                  | boolean                    | true                        | When true, the audio will start from the beginning after finishing                  |
| value                 | Bytes                      | b''                         | The media data as a byte string.                                                    |
")

(defipywidget ;; bounded-float-text
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'BoundedFloatTextModel'     |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'FloatTextView'             |                                                                                                              |
| continuous_update     | boolean                              | false                       | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| max                   | number (float)                       | 100.0                       | Max value                                                                                                    |
| min                   | number (float)                       | 0.0                         | Min value                                                                                                    |
| step                  | null or number (float)               | null                        | Minimum step to increment the value                                                                          |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | number (float)                       | 0.0                         | Float value                                                                                                  |
")

(defipywidget ;; bounded-int-text
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'BoundedIntTextModel'       |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'IntTextView'               |                                                                                                              |
| continuous_update     | boolean                              | false                       | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| max                   | number (integer)                     | 100                         | Max value                                                                                                    |
| min                   | number (integer)                     | 0                           | Min value                                                                                                    |
| step                  | number (integer)                     | 1                           | Minimum step to increment the value                                                                          |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | number (integer)                     | 0                           | Int value                                                                                                    |
")

(defipywidget ;; box
"
| Attribute             | Type                                                       | Default                     | Help                                      |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                           |
| _model_module_version | string                                                     | '1.5.0'                     |                                           |
| _model_name           | string                                                     | 'BoxModel'                  |                                           |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                           |
| _view_module_version  | string                                                     | '1.5.0'                     |                                           |
| _view_name            | string                                                     | 'BoxView'                   |                                           |
| box_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the box.     |
| children              | array of reference to Widget widget                        | []                          | List of widget children                   |
| layout                | reference to Layout widget                                 | reference to new instance   |                                           |
")

(defipywidget ;; button
"
| Attribute             | Type                                                                  | Default                     | Help                                              |
| _dom_classes          | array of string                                                       | []                          | CSS classes applied to widget DOM element         |
| _model_module         | string                                                                | '@jupyter-widgets/controls' |                                                   |
| _model_module_version | string                                                                | '1.5.0'                     |                                                   |
| _model_name           | string                                                                | 'ButtonModel'               |                                                   |
| _view_module          | string                                                                | '@jupyter-widgets/controls' |                                                   |
| _view_module_version  | string                                                                | '1.5.0'                     |                                                   |
| _view_name            | string                                                                | 'ButtonView'                |                                                   |
| button_style          | string (one of 'primary', 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the button.          |
| description           | string                                                                | ''                          | Button label.                                     |
| disabled              | boolean                                                               | false                       | Enable or disable user changes.                   |
| icon                  | string                                                                | ''                          | Font-awesome icon name, without the 'fa-' prefix. |
| layout                | reference to Layout widget                                            | reference to new instance   |                                                   |
| style                 | reference to ButtonStyle widget                                       | reference to new instance   |                                                   |
| tooltip               | string                                                                | ''                          | Tooltip caption of the button.                    |
")

(defipywidget ;; button-style
"
| Attribute             | Type           | Default                     | Help                     |
| _model_module         | string         | '@jupyter-widgets/controls' |                          |
| _model_module_version | string         | '1.5.0'                     |                          |
| _model_name           | string         | 'ButtonStyleModel'          |                          |
| _view_module          | string         | '@jupyter-widgets/base'     |                          |
| _view_module_version  | string         | '1.2.0'                     |                          |
| _view_name            | string         | 'StyleView'                 |                          |
| button_color          | null or string | null                        | Color of the button      |
| font_weight           | string         | ''                          | Button text font weight. |
")

(defipywidget ;; check-box
"
| Attribute             | Type                                 | Default                     | Help                                                                |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                           |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                     |
| _model_module_version | string                               | '1.5.0'                     |                                                                     |
| _model_name           | string                               | 'CheckboxModel'             |                                                                     |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                     |
| _view_module_version  | string                               | '1.5.0'                     |                                                                     |
| _view_name            | string                               | 'CheckboxView'              |                                                                     |
| description           | string                               | ''                          | Description of the control.                                         |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).              |
| disabled              | boolean                              | false                       | Enable or disable user changes.                                     |
| indent                | boolean                              | true                        | Indent the control to align with other controls with a description. |
| layout                | reference to Layout widget           | reference to new instance   |                                                                     |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                              |
| value                 | boolean                              | false                       | Bool value                                                          |
")

(defipywidget ;; color-picker
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'ColorPickerModel'          |                                                        |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'ColorPickerView'           |                                                        |
| concise               | boolean                              | false                       | Display short version with just a color selector.      |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes.                        |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
| value                 | string                               | 'black'                     | The color value.                                       |
")

(defipywidget ;; combobox
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'ComboboxModel'             |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'ComboboxView'              |                                                                                                              |
| continuous_update     | boolean                              | true                        | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| ensure_option         | boolean                              | false                       | If set, ensure value is in options. Implies continuous_update=False.                                         |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| options               | array of string                      | []                          | Dropdown options for the combobox                                                                            |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed                                                      |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | string                               | ''                          | String value                                                                                                 |
")

(defipywidget ;; controller-axis
"
| Attribute             | Type                       | Default                     | Help                                      |
| _dom_classes          | array of string            | []                          | CSS classes applied to widget DOM element |
| _model_module         | string                     | '@jupyter-widgets/controls' |                                           |
| _model_module_version | string                     | '1.5.0'                     |                                           |
| _model_name           | string                     | 'ControllerAxisModel'       |                                           |
| _view_module          | string                     | '@jupyter-widgets/controls' |                                           |
| _view_module_version  | string                     | '1.5.0'                     |                                           |
| _view_name            | string                     | 'ControllerAxisView'        |                                           |
| layout                | reference to Layout widget | reference to new instance   |                                           |
| value                 | number (float)             | 0.0                         | The value of the axis.                    |
")

(defipywidget ;; controller
"
| Attribute             | Type                                | Default                     | Help                                                  |
| _dom_classes          | array of string                     | []                          | CSS classes applied to widget DOM element             |
| _model_module         | string                              | '@jupyter-widgets/controls' |                                                       |
| _model_module_version | string                              | '1.5.0'                     |                                                       |
| _model_name           | string                              | 'ControllerModel'           |                                                       |
| _view_module          | string                              | '@jupyter-widgets/controls' |                                                       |
| _view_module_version  | string                              | '1.5.0'                     |                                                       |
| _view_name            | string                              | 'ControllerView'            |                                                       |
| axes                  | array of reference to Axis widget   | []                          | The axes on the gamepad.                              |
| buttons               | array of reference to Button widget | []                          | The buttons on the gamepad.                           |
| connected             | boolean                             | false                       | Whether the gamepad is connected.                     |
| index                 | number (integer)                    | 0                           | The id number of the controller.                      |
| layout                | reference to Layout widget          | reference to new instance   |                                                       |
| mapping               | string                              | ''                          | The name of the control mapping.                      |
| name                  | string                              | ''                          | The name of the controller.                           |
| timestamp             | number (float)                      | 0.0                         | The last time the data from this gamepad was updated. |
")

(defipywidget ;; dom-widget
"
| Attribute             | Type                       | Default                     | Help                                      |
| _dom_classes          | array of string            | []                          | CSS classes applied to widget DOM element |
| _model_module         | string                     | '@jupyter-widgets/controls' |                                           |
| _model_module_version | string                     | '1.5.0'                     |                                           |
| _model_name           | string                     | 'DOMWidgetModel'            |                                           |
| _view_module          | string                     | '@jupyter-widgets/controls' |                                           |
| _view_module_version  | string                     | '1.5.0'                     |                                           |
| _view_name            | null or string             | null                        | Name of the view.                         |
| layout                | reference to Layout widget | reference to new instance   |                                           |
| value                 | Bytes                      | b''                         | The media data as a byte string.          |
")

(defipywidget ;; date-picker
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'DatePickerModel'           |                                                        |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'DatePickerView'            |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes.                        |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
| value                 | null or Date                         | null                        |                                                        |
")

(defipywidget ;; description-style
"
| Attribute             | Type   | Default                     | Help                                                 |
| _model_module         | string | '@jupyter-widgets/controls' |                                                      |
| _model_module_version | string | '1.5.0'                     |                                                      |
| _model_name           | string | 'DescriptionStyleModel'     |                                                      |
| _view_module          | string | '@jupyter-widgets/base'     |                                                      |
| _view_module_version  | string | '1.2.0'                     |                                                      |
| _view_name            | string | 'StyleView'                 |                                                      |
| description_width     | string | ''                          | Width of the description to the side of the control. |
")

(defipywidget ;; directional-link
"
| Attribute             | Type           | Default                     | Help                                   |
| _model_module         | string         | '@jupyter-widgets/controls' |                                        |
| _model_module_version | string         | '1.5.0'                     |                                        |
| _model_name           | string         | 'DirectionalLinkModel'      |                                        |
| _view_module          | string         | '@jupyter-widgets/controls' |                                        |
| _view_module_version  | string         | '1.5.0'                     |                                        |
| _view_name            | null or string | null                        | Name of the view.                      |
| source                | array          | []                          | The source (widget, 'trait_name') pair |
| target                | array          | []                          | The target (widget, 'trait_name') pair |
")

(defipywidget ;; dropdown
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'DropdownModel'             |                                                        |
| _options_labels       | array of string                      | []                          | The labels for the options.                            |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'DropdownView'              |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes                         |
| index                 | null or number (integer)             | null                        | Selected index                                         |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
")

(defipywidget ;; file-upload
"
| Attribute             | Type                                                                  | Default                     | Help                                                   |
| _counter              | number (integer)                                                      | 0                           |                                                        |
| _dom_classes          | array of string                                                       | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                                                                | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                                                                | '1.5.0'                     |                                                        |
| _model_name           | string                                                                | 'FileUploadModel'           |                                                        |
| _view_module          | string                                                                | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                                                                | '1.5.0'                     |                                                        |
| _view_name            | string                                                                | 'FileUploadView'            |                                                        |
| accept                | string                                                                | ''                          | File types to accept, empty string for all             |
| button_style          | string (one of 'primary', 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the button.               |
| data                  | array                                                                 | []                          | List of file content (bytes)                           |
| description           | string                                                                | ''                          | Description of the control.                            |
| description_tooltip   | null or string                                                        | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                                                               | false                       | Enable or disable button                               |
| error                 | string                                                                | ''                          | Error message                                          |
| icon                  | string                                                                | 'upload'                    | Font-awesome icon name, without the 'fa-' prefix.      |
| layout                | reference to Layout widget                                            | reference to new instance   |                                                        |
| metadata              | array                                                                 | []                          | List of file metadata                                  |
| multiple              | boolean                                                               | false                       | If True, allow for multiple files upload               |
| style                 | reference to ButtonStyle widget                                       | reference to new instance   |                                                        |
")

(defipywidget ;; float-log-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'FloatLogSliderModel'       |                                                                   |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'FloatLogSliderView'        |                                                                   |
| base                  | number (float)                           | 10.0                        | Base for the logarithm                                            |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is holding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| max                   | number (float)                           | 4.0                         | Max value for the exponent                                        |
| min                   | number (float)                           | 0.0                         | Min value for the exponent                                        |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current value of the slider next to it.               |
| readout_format        | string                                   | '.3g'                       | Format for the readout                                            |
| step                  | number (float)                           | 0.1                         | Minimum step in the exponent to increment the value               |
| style                 | reference to SliderStyle widget          | reference to new instance   |                                                                   |
| value                 | number (float)                           | 1.0                         | Float value                                                       |
")

(defipywidget ;; float-progress
"
| Attribute             | Type                                                               | Default                     | Help                                                   |
| _dom_classes          | array of string                                                    | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                                                             | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                                                             | '1.5.0'                     |                                                        |
| _model_name           | string                                                             | 'FloatProgressModel'        |                                                        |
| _view_module          | string                                                             | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                                                             | '1.5.0'                     |                                                        |
| _view_name            | string                                                             | 'ProgressView'              |                                                        |
| bar_style             | null or string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the progess bar.          |
| description           | string                                                             | ''                          | Description of the control.                            |
| description_tooltip   | null or string                                                     | null                        | Tooltip for the description (defaults to description). |
| layout                | reference to Layout widget                                         | reference to new instance   |                                                        |
| max                   | number (float)                                                     | 100.0                       | Max value                                              |
| min                   | number (float)                                                     | 0.0                         | Min value                                              |
| orientation           | string (one of 'horizontal', 'vertical')                           | 'horizontal'                | Vertical or horizontal.                                |
| style                 | reference to ProgressStyle widget                                  | reference to new instance   |                                                        |
| value                 | number (float)                                                     | 0.0                         | Float value                                            |
")

(defipywidget ;; float-range-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'FloatRangeSliderModel'     |                                                                   |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'FloatRangeSliderView'      |                                                                   |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is sliding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| max                   | number (float)                           | 100.0                       | Max value                                                         |
| min                   | number (float)                           | 0.0                         | Min value                                                         |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current value of the slider next to it.               |
| readout_format        | string                                   | '.2f'                       | Format for the readout                                            |
| step                  | number (float)                           | 0.1                         | Minimum step to increment the value                               |
| style                 | reference to SliderStyle widget          | reference to new instance   |                                                                   |
| value                 | array                                    | [0.0, 1.0]                  | Tuple of (lower, upper) bounds                                    |
")

(defipywidget ;; float-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'FloatSliderModel'          |                                                                   |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'FloatSliderView'           |                                                                   |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is holding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| max                   | number (float)                           | 100.0                       | Max value                                                         |
| min                   | number (float)                           | 0.0                         | Min value                                                         |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current value of the slider next to it.               |
| readout_format        | string                                   | '.2f'                       | Format for the readout                                            |
| step                  | number (float)                           | 0.1                         | Minimum step to increment the value                               |
| style                 | reference to SliderStyle widget          | reference to new instance   |                                                                   |
| value                 | number (float)                           | 0.0                         | Float value                                                       |
")

(defipywidget ;; float-text
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'FloatTextModel'            |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'FloatTextView'             |                                                                                                              |
| continuous_update     | boolean                              | false                       | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| step                  | null or number (float)               | null                        | Minimum step to increment the value                                                                          |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | number (float)                       | 0.0                         | Float value                                                                                                  |
")

(defipywidget ;; grid-box
"
| Attribute             | Type                                                       | Default                     | Help                                      |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                           |
| _model_module_version | string                                                     | '1.5.0'                     |                                           |
| _model_name           | string                                                     | 'GridBoxModel'              |                                           |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                           |
| _view_module_version  | string                                                     | '1.5.0'                     |                                           |
| _view_name            | string                                                     | 'GridBoxView'               |                                           |
| box_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the box.     |
| children              | array of reference to Widget widget                        | []                          | List of widget children                   |
| layout                | reference to Layout widget                                 | reference to new instance   |                                           |
")

(defipywidget ;; h-box
"
| Attribute             | Type                                                       | Default                     | Help                                      |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                           |
| _model_module_version | string                                                     | '1.5.0'                     |                                           |
| _model_name           | string                                                     | 'HBoxModel'                 |                                           |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                           |
| _view_module_version  | string                                                     | '1.5.0'                     |                                           |
| _view_name            | string                                                     | 'HBoxView'                  |                                           |
| box_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the box.     |
| children              | array of reference to Widget widget                        | []                          | List of widget children                   |
| layout                | reference to Layout widget                                 | reference to new instance   |                                           |
")

(defipywidget ;; html-math
"
| Attribute             | Type                                 | Default                     | Help                                                    |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element               |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                         |
| _model_module_version | string                               | '1.5.0'                     |                                                         |
| _model_name           | string                               | 'HTMLMathModel'             |                                                         |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                         |
| _view_module_version  | string                               | '1.5.0'                     |                                                         |
| _view_name            | string                               | 'HTMLMathView'              |                                                         |
| description           | string                               | ''                          | Description of the control.                             |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).  |
| layout                | reference to Layout widget           | reference to new instance   |                                                         |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                  |
| value                 | string                               | ''                          | String value                                            |
")

(defipywidget ;; html
"
| Attribute             | Type                                 | Default                     | Help                                                    |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element               |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                         |
| _model_module_version | string                               | '1.5.0'                     |                                                         |
| _model_name           | string                               | 'HTMLModel'                 |                                                         |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                         |
| _view_module_version  | string                               | '1.5.0'                     |                                                         |
| _view_name            | string                               | 'HTMLView'                  |                                                         |
| description           | string                               | ''                          | Description of the control.                             |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).  |
| layout                | reference to Layout widget           | reference to new instance   |                                                         |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                  |
| value                 | string                               | ''                          | String value                                            |
")

(defipywidget ;; image
"
| Attribute             | Type                       | Default                     | Help                                                                     |
| _dom_classes          | array of string            | []                          | CSS classes applied to widget DOM element                                |
| _model_module         | string                     | '@jupyter-widgets/controls' |                                                                          |
| _model_module_version | string                     | '1.5.0'                     |                                                                          |
| _model_name           | string                     | 'ImageModel'                |                                                                          |
| _view_module          | string                     | '@jupyter-widgets/controls' |                                                                          |
| _view_module_version  | string                     | '1.5.0'                     |                                                                          |
| _view_name            | string                     | 'ImageView'                 |                                                                          |
| format                | string                     | 'png'                       | The format of the image.                                                 |
| height                | string                     | ''                          | Height of the image in pixels. Use layout.height for styling the widget. |
| layout                | reference to Layout widget | reference to new instance   |                                                                          |
| value                 | Bytes                      | b''                         | The media data as a byte string.                                         |
| width                 | string                     | ''                          | Width of the image in pixels. Use layout.width for styling the widget.   |
")

(defipywidget ;; int-progress
"
| Attribute             | Type                                                       | Default                     | Help                                                   |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                                                     | '1.5.0'                     |                                                        |
| _model_name           | string                                                     | 'IntProgressModel'          |                                                        |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                                                     | '1.5.0'                     |                                                        |
| _view_name            | string                                                     | 'ProgressView'              |                                                        |
| bar_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the progess bar.          |
| description           | string                                                     | ''                          | Description of the control.                            |
| description_tooltip   | null or string                                             | null                        | Tooltip for the description (defaults to description). |
| layout                | reference to Layout widget                                 | reference to new instance   |                                                        |
| max                   | number (integer)                                           | 100                         | Max value                                              |
| min                   | number (integer)                                           | 0                           | Min value                                              |
| orientation           | string (one of 'horizontal', 'vertical')                   | 'horizontal'                | Vertical or horizontal.                                |
| style                 | reference to ProgressStyle widget                          | reference to new instance   |                                                        |
| value                 | number (integer)                                           | 0                           | Int value                                              |
")

(defipywidget ;; int-range-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'IntRangeSliderModel'       |                                                                   |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'IntRangeSliderView'        |                                                                   |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is sliding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| max                   | number (integer)                         | 100                         | Max value                                                         |
| min                   | number (integer)                         | 0                           | Min value                                                         |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current value of the slider next to it.               |
| readout_format        | string                                   | 'd'                         | Format for the readout                                            |
| step                  | number (integer)                         | 1                           | Minimum step that the value can take                              |
| style                 | reference to SliderStyle widget          | reference to new instance   | Slider style customizations.                                      |
| value                 | array                                    | [0, 1]                      | Tuple of (lower, upper) bounds                                    |
")

(defipywidget ;; int-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'IntSliderModel'            |                                                                   |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'IntSliderView'             |                                                                   |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is holding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| max                   | number (integer)                         | 100                         | Max value                                                         |
| min                   | number (integer)                         | 0                           | Min value                                                         |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current value of the slider next to it.               |
| readout_format        | string                                   | 'd'                         | Format for the readout                                            |
| step                  | number (integer)                         | 1                           | Minimum step to increment the value                               |
| style                 | reference to SliderStyle widget          | reference to new instance   |                                                                   |
| value                 | number (integer)                         | 0                           | Int value                                                         |
")

(defipywidget ;; int-text
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'IntTextModel'              |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'IntTextView'               |                                                                                                              |
| continuous_update     | boolean                              | false                       | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| step                  | number (integer)                     | 1                           | Minimum step to increment the value                                                                          |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | number (integer)                     | 0                           | Int value                                                                                                    |
")

(defipywidget ;; label
"
| Attribute             | Type                                 | Default                     | Help                                                    |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element               |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                         |
| _model_module_version | string                               | '1.5.0'                     |                                                         |
| _model_name           | string                               | 'LabelModel'                |                                                         |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                         |
| _view_module_version  | string                               | '1.5.0'                     |                                                         |
| _view_name            | string                               | 'LabelView'                 |                                                         |
| description           | string                               | ''                          | Description of the control.                             |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).  |
| layout                | reference to Layout widget           | reference to new instance   |                                                         |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                  |
| value                 | string                               | ''                          | String value                                            |
")

(defipywidget ;; link
"
| Attribute             | Type           | Default                     | Help                                   |
| _model_module         | string         | '@jupyter-widgets/controls' |                                        |
| _model_module_version | string         | '1.5.0'                     |                                        |
| _model_name           | string         | 'LinkModel'                 |                                        |
| _view_module          | string         | '@jupyter-widgets/controls' |                                        |
| _view_module_version  | string         | '1.5.0'                     |                                        |
| _view_name            | null or string | null                        | Name of the view.                      |
| source                | array          | []                          | The source (widget, 'trait_name') pair |
| target                | array          | []                          | The target (widget, 'trait_name') pair |
")

(defipywidget ;; password
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'PasswordModel'             |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'PasswordView'              |                                                                                                              |
| continuous_update     | boolean                              | true                        | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed                                                      |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | string                               | ''                          | String value                                                                                                 |
")

(defipywidget ;; play
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'PlayModel'                 |                                                        |
| _playing              | boolean                              | false                       | Whether the control is currently playing.              |
| _repeat               | boolean                              | false                       | Whether the control will repeat in a continous loop.   |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'PlayView'                  |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes                         |
| interval              | number (integer)                     | 100                         | The maximum value for the play control.                |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| max                   | number (integer)                     | 100                         | Max value                                              |
| min                   | number (integer)                     | 0                           | Min value                                              |
| show_repeat           | boolean                              | true                        | Show the repeat toggle button in the widget.           |
| step                  | number (integer)                     | 1                           | Increment step                                         |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
| value                 | number (integer)                     | 0                           | Int value                                              |
")

(defipywidget ;; progress-style
"
| Attribute             | Type           | Default                     | Help                                                 |
| _model_module         | string         | '@jupyter-widgets/controls' |                                                      |
| _model_module_version | string         | '1.5.0'                     |                                                      |
| _model_name           | string         | 'ProgressStyleModel'        |                                                      |
| _view_module          | string         | '@jupyter-widgets/base'     |                                                      |
| _view_module_version  | string         | '1.2.0'                     |                                                      |
| _view_name            | string         | 'StyleView'                 |                                                      |
| bar_color             | null or string | null                        | Color of the progress bar.                           |
| description_width     | string         | ''                          | Width of the description to the side of the control. |
")

(defipywidget ;; radio-buttons
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'RadioButtonsModel'         |                                                        |
| _options_labels       | array of string                      | []                          | The labels for the options.                            |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'RadioButtonsView'          |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes                         |
| index                 | null or number (integer)             | null                        | Selected index                                         |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
")

(defipywidget ;; select
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'SelectModel'               |                                                        |
| _options_labels       | array of string                      | []                          | The labels for the options.                            |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'SelectView'                |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes                         |
| index                 | null or number (integer)             | null                        | Selected index                                         |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| rows                  | number (integer)                     | 5                           | The number of rows to display.                         |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
")

(defipywidget ;; select-multiple
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'SelectMultipleModel'       |                                                        |
| _options_labels       | array of string                      | []                          | The labels for the options.                            |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'SelectMultipleView'        |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes                         |
| index                 | array of number (integer)            | []                          | Selected indices                                       |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| rows                  | number (integer)                     | 5                           | The number of rows to display.                         |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
")

(defipywidget ;; selection-range-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'SelectionRangeSliderModel' |                                                                   |
| _options_labels       | array of string                          | []                          | The labels for the options.                                       |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'SelectionRangeSliderView'  |                                                                   |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is holding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| index                 | array                                    | [0, 0]                      | Min and max selected indices                                      |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current selected label next to the slider             |
| style                 | reference to DescriptionStyle widget     | reference to new instance   | Styling customizations                                            |
")

(defipywidget ;; selection-slider
"
| Attribute             | Type                                     | Default                     | Help                                                              |
| _dom_classes          | array of string                          | []                          | CSS classes applied to widget DOM element                         |
| _model_module         | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _model_module_version | string                                   | '1.5.0'                     |                                                                   |
| _model_name           | string                                   | 'SelectionSliderModel'      |                                                                   |
| _options_labels       | array of string                          | []                          | The labels for the options.                                       |
| _view_module          | string                                   | '@jupyter-widgets/controls' |                                                                   |
| _view_module_version  | string                                   | '1.5.0'                     |                                                                   |
| _view_name            | string                                   | 'SelectionSliderView'       |                                                                   |
| continuous_update     | boolean                                  | true                        | Update the value of the widget as the user is holding the slider. |
| description           | string                                   | ''                          | Description of the control.                                       |
| description_tooltip   | null or string                           | null                        | Tooltip for the description (defaults to description).            |
| disabled              | boolean                                  | false                       | Enable or disable user changes                                    |
| index                 | number (integer)                         | 0                           | Selected index                                                    |
| layout                | reference to Layout widget               | reference to new instance   |                                                                   |
| orientation           | string (one of 'horizontal', 'vertical') | 'horizontal'                | Vertical or horizontal.                                           |
| readout               | boolean                                  | true                        | Display the current selected label next to the slider             |
| style                 | reference to DescriptionStyle widget     | reference to new instance   | Styling customizations                                            |
")

(defipywidget ;; slider-style
"
| Attribute             | Type           | Default                     | Help                                                 |
| _model_module         | string         | '@jupyter-widgets/controls' |                                                      |
| _model_module_version | string         | '1.5.0'                     |                                                      |
| _model_name           | string         | 'SliderStyleModel'          |                                                      |
| _view_module          | string         | '@jupyter-widgets/base'     |                                                      |
| _view_module_version  | string         | '1.2.0'                     |                                                      |
| _view_name            | string         | 'StyleView'                 |                                                      |
| description_width     | string         | ''                          | Width of the description to the side of the control. |
| handle_color          | null or string | null                        | Color of the slider handle.                          |
")

(defipywidget ;; tab
"
| Attribute             | Type                                                       | Default                     | Help                                                                                                                              |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element                                                                                         |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                                                                                                                   |
| _model_module_version | string                                                     | '1.5.0'                     |                                                                                                                                   |
| _model_name           | string                                                     | 'TabModel'                  |                                                                                                                                   |
| _titles               | object                                                     | {}                          | Titles of the pages                                                                                                               |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                                                                                                                   |
| _view_module_version  | string                                                     | '1.5.0'                     |                                                                                                                                   |
| _view_name            | string                                                     | 'TabView'                   |                                                                                                                                   |
| box_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the box.                                                                                             |
| children              | array of reference to Widget widget                        | []                          | List of widget children                                                                                                           |
| layout                | reference to Layout widget                                 | reference to new instance   |                                                                                                                                   |
| selected_index        | null or number (integer)                                   | 0                           | The index of the selected page. This is either an integer selecting a particular sub-widget, or None to have no widgets selected. |
")

(defipywidget ;; text
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'TextModel'                 |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'TextView'                  |                                                                                                              |
| continuous_update     | boolean                              | true                        | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed                                                      |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | string                               | ''                          | String value                                                                                                 |
")

(defipywidget ;; textarea
"
| Attribute             | Type                                 | Default                     | Help                                                                                                         |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element                                                                    |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _model_module_version | string                               | '1.5.0'                     |                                                                                                              |
| _model_name           | string                               | 'TextareaModel'             |                                                                                                              |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                                                                              |
| _view_module_version  | string                               | '1.5.0'                     |                                                                                                              |
| _view_name            | string                               | 'TextareaView'              |                                                                                                              |
| continuous_update     | boolean                              | true                        | Update the value as the user types. If False, update on submission, e.g., pressing Enter or navigating away. |
| description           | string                               | ''                          | Description of the control.                                                                                  |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description).                                                       |
| disabled              | boolean                              | false                       | Enable or disable user changes                                                                               |
| layout                | reference to Layout widget           | reference to new instance   |                                                                                                              |
| placeholder           | string                               | '\u200b'                    | Placeholder text to display when nothing has been typed                                                      |
| rows                  | null or number (integer)             | null                        | The number of rows to display.                                                                               |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                                                                       |
| value                 | string                               | ''                          | String value                                                                                                 |
")

(defipywidget ;; toggle-button
"
| Attribute             | Type                                                                  | Default                     | Help                                                   |
| _dom_classes          | array of string                                                       | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                                                                | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                                                                | '1.5.0'                     |                                                        |
| _model_name           | string                                                                | 'ToggleButtonModel'         |                                                        |
| _view_module          | string                                                                | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                                                                | '1.5.0'                     |                                                        |
| _view_name            | string                                                                | 'ToggleButtonView'          |                                                        |
| button_style          | string (one of 'primary', 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the button.               |
| description           | string                                                                | ''                          | Description of the control.                            |
| description_tooltip   | null or string                                                        | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                                                               | false                       | Enable or disable user changes.                        |
| icon                  | string                                                                | ''                          | Font-awesome icon.                                     |
| layout                | reference to Layout widget                                            | reference to new instance   |                                                        |
| style                 | reference to DescriptionStyle widget                                  | reference to new instance   | Styling customizations                                 |
| tooltip               | string                                                                | ''                          | Tooltip caption of the toggle button.                  |
| value                 | boolean                                                               | false                       | Bool value                                             |
")

(defipywidget ;; toggle-buttons
"
| Attribute             | Type                                                                          | Default                     | Help                                                                    |
| _dom_classes          | array of string                                                               | []                          | CSS classes applied to widget DOM element                               |
| _model_module         | string                                                                        | '@jupyter-widgets/controls' |                                                                         |
| _model_module_version | string                                                                        | '1.5.0'                     |                                                                         |
| _model_name           | string                                                                        | 'ToggleButtonsModel'        |                                                                         |
| _options_labels       | array of string                                                               | []                          | The labels for the options.                                             |
| _view_module          | string                                                                        | '@jupyter-widgets/controls' |                                                                         |
| _view_module_version  | string                                                                        | '1.5.0'                     |                                                                         |
| _view_name            | string                                                                        | 'ToggleButtonsView'         |                                                                         |
| button_style          | null or string (one of 'primary', 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the buttons.                               |
| description           | string                                                                        | ''                          | Description of the control.                                             |
| description_tooltip   | null or string                                                                | null                        | Tooltip for the description (defaults to description).                  |
| disabled              | boolean                                                                       | false                       | Enable or disable user changes                                          |
| icons                 | array of string                                                               | []                          | Icons names for each button (FontAwesome names without the fa- prefix). |
| index                 | null or number (integer)                                                      | null                        | Selected index                                                          |
| layout                | reference to Layout widget                                                    | reference to new instance   |                                                                         |
| style                 | reference to ToggleButtonsStyle widget                                        | reference to new instance   |                                                                         |
| tooltips              | array of string                                                               | []                          | Tooltips for each button.                                               |
")

(defipywidget ;; v-box
"
| Attribute             | Type                                                       | Default                     | Help                                      |
| _dom_classes          | array of string                                            | []                          | CSS classes applied to widget DOM element |
| _model_module         | string                                                     | '@jupyter-widgets/controls' |                                           |
| _model_module_version | string                                                     | '1.5.0'                     |                                           |
| _model_name           | string                                                     | 'VBoxModel'                 |                                           |
| _view_module          | string                                                     | '@jupyter-widgets/controls' |                                           |
| _view_module_version  | string                                                     | '1.5.0'                     |                                           |
| _view_name            | string                                                     | 'VBoxView'                  |                                           |
| box_style             | string (one of 'success', 'info', 'warning', 'danger', '') | ''                          | Use a predefined styling for the box.     |
| children              | array of reference to Widget widget                        | []                          | List of widget children                   |
| layout                | reference to Layout widget                                 | reference to new instance   |                                           |
")

(defipywidget ;; valid
"
| Attribute             | Type                                 | Default                     | Help                                                   |
| _dom_classes          | array of string                      | []                          | CSS classes applied to widget DOM element              |
| _model_module         | string                               | '@jupyter-widgets/controls' |                                                        |
| _model_module_version | string                               | '1.5.0'                     |                                                        |
| _model_name           | string                               | 'ValidModel'                |                                                        |
| _view_module          | string                               | '@jupyter-widgets/controls' |                                                        |
| _view_module_version  | string                               | '1.5.0'                     |                                                        |
| _view_name            | string                               | 'ValidView'                 |                                                        |
| description           | string                               | ''                          | Description of the control.                            |
| description_tooltip   | null or string                       | null                        | Tooltip for the description (defaults to description). |
| disabled              | boolean                              | false                       | Enable or disable user changes.                        |
| layout                | reference to Layout widget           | reference to new instance   |                                                        |
| readout               | string                               | 'Invalid'                   | Message displayed when the value is False              |
| style                 | reference to DescriptionStyle widget | reference to new instance   | Styling customizations                                 |
| value                 | boolean                              | false                       | Bool value                                             |
")

(defipywidget ;; video
"
| Attribute             | Type                       | Default                     | Help                                                                                |
| _dom_classes          | array of string            | []                          | CSS classes applied to widget DOM element                                           |
| _model_module         | string                     | '@jupyter-widgets/controls' |                                                                                     |
| _model_module_version | string                     | '1.5.0'                     |                                                                                     |
| _model_name           | string                     | 'VideoModel'                |                                                                                     |
| _view_module          | string                     | '@jupyter-widgets/controls' |                                                                                     |
| _view_module_version  | string                     | '1.5.0'                     |                                                                                     |
| _view_name            | string                     | 'VideoView'                 |                                                                                     |
| autoplay              | boolean                    | true                        | When true, the video starts when it's displayed                                     |
| controls              | boolean                    | true                        | Specifies that video controls should be displayed (such as a play/pause button etc) |
| format                | string                     | 'mp4'                       | The format of the video.                                                            |
| height                | string                     | ''                          | Height of the video in pixels.                                                      |
| layout                | reference to Layout widget | reference to new instance   |                                                                                     |
| loop                  | boolean                    | true                        | When true, the video will start from the beginning after finishing                  |
| value                 | Bytes                      | b''                         | The media data as a byte string.                                                    |
| width                 | string                     | ''                          | Width of the video in pixels.                                                       |
")

(defipywidget ;; output
"
| Attribute             | Type                       | Default                   | Help                                          |
| _dom_classes          | array of string            | []                        | CSS classes applied to widget DOM element     |
| _model_module         | string                     | '@jupyter-widgets/output' |                                               |
| _model_module_version | string                     | '1.0.0'                   |                                               |
| _model_name           | string                     | 'OutputModel'             |                                               |
| _view_module          | string                     | '@jupyter-widgets/output' |                                               |
| _view_module_version  | string                     | '1.0.0'                   |                                               |
| _view_name            | string                     | 'OutputView'              |                                               |
| layout                | reference to Layout widget | reference to new instance |                                               |
| msg_id                | string                     | ''                        | Parent message id of messages to capture      |
| outputs               | array of object            | []                        | The output messages synced from the frontend. |
")
