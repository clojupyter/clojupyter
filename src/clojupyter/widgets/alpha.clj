(ns clojupyter.widgets.alpha
  (:require
   [camel-snake-kebab.core :as csk]
   [clojupyter.kernel.comm-atom :as comm-atom]
   [clojupyter.kernel.jup-channels :as jup]
   [clojupyter.log :as log]
   [clojupyter.messages :as msgs]
   [clojupyter.state :as state]
   [clojupyter.util :as u]
   [clojupyter.util-actions :as u! :refer [uuid]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :refer [instrument unstrument]]
   [clojure.string :as str]
   [io.simplect.compose :refer [def- c C p P >->> >>->]]
   [io.simplect.compose.action :as act :refer [action side-effect step]]
   [clojure.string :as str]))

(use 'clojure.pprint)

(def WIDGET-TARGET "jupyter.widget")

;;; ------------------------------------------------------------------------------------------------------------------------
;;; LINK
;;; ------------------------------------------------------------------------------------------------------------------------

(defprotocol link-proto
  (linkees [link])
  (unlink [link]))

(defrecord Link [watched-atom_ watching-atom_ agent-key_]
  link-proto
  (unlink [_]
    (comm-atom/unwatch watched-atom_ agent-key_)))

(defn link
  "Link widgets `watched-atom` and `watchig-atom` such that a change to `watched-key` in
  `watched-atom` is propagated to `watching-key` in `watching-atom`.  Returns a `Link` which can be
  used to remove the linkage (using `unlink`)."
  [watched-atom watched-key watching-atom watching-key]
  (assert (comm-atom/comm-atom? watched-atom))
  (assert (comm-atom/comm-atom? watching-atom))
  (let [agent-key (gensym)]
    (comm-atom/watch watched-atom agent-key
                     (fn [_ _ old new]
                       (let [v (get new watched-key)]
                         (when (not= (get old watched-key) v)
                           (assoc watching-atom watching-key v)))))
    (->Link watched-atom watching-atom agent-key)))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; IntSliderModel
;;; ------------------------------------------------------------------------------------------------------------------------

(defrecord IntSliderModel [])

(defn intslider
  [int-value]
  (let [meta {:_model_module "@jupyter-widgets/controls"
              :_model_name "IntSliderModel"
              :_view_module_version "1.5.0"
              :_model_module_version "1.5.0"
              :min 0
              :max 100}
        {:keys [jup req-message]} (state/current-context)

        state (merge meta {:value int-value})]
    (comm-atom/create-and-insert jup req-message WIDGET-TARGET (u!/uuid) state)))

(s/fdef intslider
  :args (s/cat :int int?))
(instrument `intslider)

(defn intslider-getval
  [slider]
  (:value @slider))

(defn intslider-setval
  [slider int-val]
  (:value (comm-atom/state-set! slider {:value int-val})))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; DEFWIDGET
;;; ------------------------------------------------------------------------------------------------------------------------

(defmacro defwidget
  [widget-name model-name model-module-version view-module-version docstring attr-map]
  (assert (and (string? model-name)
               (> (count model-name) 5)
               (= "Model" (subs model-name (- (count model-name) 5) (count model-name))))
          (str "defwidget - model-name assumed to end in 'Model': " model-name))

  (let [attr-map (->> attr-map (remove (C first (P = '_model_name))) (into {}))
        widget-name! (symbol (str widget-name "!"))
        docstring (str "\n" docstring "\n"
                       "IPYWIDGET MODEL-MODULE-VERSION:\t" model-module-version "\n"
                       "IPYWIDGET VIEW-MODULE-VERSION:\t" view-module-version "\n")]
    `(do
       (defn ~widget-name
         ~(str "\nReturns a map representing an IPYWIDGET `" model-name "`.\n" docstring)
         ([]
          (~widget-name {}))
         ([{:keys ~(-> attr-map keys sort vec)}]
          (let ~(->> (for [[k default] attr-map]
                       [k `(or ~k ~default)])
                     (apply concat)
                     vec)
            (assoc ~(->> (for [[k default] attr-map]
                           [(keyword k) k])
                         (into {}))
                   :_model_name ~model-name
                   :_model_module_version ~model-module-version
                   :_view_module_version ~view-module-version))))
       (defn ~widget-name!
         ([state-map#]
          (let [{jup# :jup req-message# :req-message} (state/current-context)]
            (~widget-name! jup# req-message# WIDGET-TARGET (u!/uuid) state-map#)))
         ([jup# req-message# target-name# comm-id#]
          (~widget-name! jup# req-message# target-name# comm-id# {}))
         ([jup# req-message# target-name# comm-id# state-map#]
          (comm-atom/create-and-insert jup# req-message# target-name# comm-id# (~widget-name state-map#))))
       (alter-meta! (var ~widget-name!)
                    assoc :arglists ~(let [base-arglist '[jup req-message target-name comm-id]]
                                       `'[~base-arglist  ~(conj base-arglist {:keys (-> attr-map keys sort vec)})]))
       (alter-meta! (var ~widget-name!)
                    assoc :doc ~(str "Creates a COMM-ATOM with the result of calling `" widget-name "`.\n" docstring))
       (var ~widget-name))))

(println "alpha.clj:			update `defwidget` to extract model_name etc")

;;; ------------------------------------------------------------------------------------------------------------------------
;;; WIDGET DEFINITIONS
;;; ------------------------------------------------------------------------------------------------------------------------

(defwidget layout "LayoutModel" "1.2.0" "1.2.0"
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
"
  {
   _model_module         	 "@jupyter-widgets/base"
   _model_module_version 	 "1.2.0"
   _model_name           	 "LayoutModel"
   _view_module          	 "@jupyter-widgets/base"
   _view_module_version  	 "1.2.0"
   _view_name            	 "LayoutView"
   align_content         	 nil
   align_items           	 nil
   align_self            	 nil
   border                	 nil
   bottom                	 nil
   display               	 nil
   flex                  	 nil
   flex_flow             	 nil
   grid_area             	 nil
   grid_auto_columns     	 nil
   grid_auto_flow        	 nil
   grid_auto_rows        	 nil
   grid_column           	 nil
   grid_gap              	 nil
   grid_row              	 nil
   grid_template_areas   	 nil
   grid_template_columns 	 nil
   grid_template_rows    	 nil
   height                	 nil
   justify_content       	 nil
   justify_items         	 nil
   left                  	 nil
   margin                	 nil
   max_height            	 nil
   max_width             	 nil
   min_height            	 nil
   min_width             	 nil
   object_fit            	 nil
   object_position       	 nil
   order                 	 nil
   overflow              	 nil
   overflow_x            	 nil
   overflow_y            	 nil
   padding               	 nil
   right                 	 nil
   top                   	 nil
   visibility            	 nil
   width                 	 nil})

(defwidget accordion "AccordionModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes			[]
   _model_module		"@jupyter-widgets/controls"
   _model_module_version	"1.5.0"
   _model_name			"AccordionModel"
   _titles			{}
   _view_module			"@jupyter-widgets/controls"
   _view_module_version		"1.5.0"
   _view_name			"AccordionView"
   box_style			""
   children			[]
   layout			nil
   selected_index		0})

(defwidget audio "AudioModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "AudioModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "AudioView"
   autoplay              true
   controls              true
   format                "mp3"
   layout                nil ;; reference to new instance
   loop                  true
   value                 ""})

(defwidget bounded-float-text "BoundedFloatTextModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "BoundedFloatTextModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "FloatTextView"
   continuous_update     false
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   100.0
   min                   0.0
   step                  nil
   style                 nil ;; reference to new instance
   value                 0.0})

(defwidget bounded-int-text "BoundedIntTextModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "BoundedIntTextModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "IntTextView"
   continuous_update     false
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   100
   min                   0
   step                  1
   style                 nil ;; reference to new instance
   value                 0
   })

(defwidget box "BoxModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "BoxModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "BoxView"
   box_style             ""
   children              []
   layout                nil ;; reference to new instance
   })

(defwidget button "ButtonModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ButtonModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ButtonView"
   button_style          ""
   description           ""
   disabled              false
   icon                  ""
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   tooltip               ""})

(defwidget button-style "ButtonStyleModel" "1.5.0" "1.2.0"
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
"
  {
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ButtonStyleModel"
   _view_module          "@jupyter-widgets/base"
   _view_module_version  "1.2.0"
   _view_name            "StyleView"
   button_color          nil
   font_weight           ""
   })

(defwidget check-box "CheckboxModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "CheckboxModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "CheckboxView"
   description           ""
   description_tooltip   nil
   disabled              false
   indent                true
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   value                 false
   })

(defwidget color-picker  "ColorPickerModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ColorPickerModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ColorPickerView"
   concise               false
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   value                 "black"
   })

(defwidget combobox "ComboboxModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ComboboxModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ComboboxView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   ensure_option         false
   layout                nil ;; reference to new instance
   options               []
   placeholder           "\u200b"
   style                 nil ;; reference to new instance
   value                 ""})

(defwidget controller-axis "ControllerAxisModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ControllerAxisModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ControllerAxisView"
   layout                nil ;; reference to new instance
   value                 0.0
   })

(defwidget controller "ControllerModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ControllerModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ControllerView"
   axes                  []
   buttons               []
   connected             false
   index                 0
   layout                nil ;; reference to new instance
   mapping               ""
   name                  ""
   timestamp             0.0})

(defwidget dom-widget "DOMWidgetModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "DOMWidgetModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            nil
   layout                nil ;;reference to new instance
   value                 ""
   })

(defwidget date-picket "DatePickerModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "DatePickerModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "DatePickerView"
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   value                 nil})

(defwidget description-style "DescriptionStyleModel" "1.5.0" "1.2.0"
  "
| Attribute             | Type   | Default                     | Help                                                 |
| _model_module         | string | '@jupyter-widgets/controls' |                                                      |
| _model_module_version | string | '1.5.0'                     |                                                      |
| _model_name           | string | 'DescriptionStyleModel'     |                                                      |
| _view_module          | string | '@jupyter-widgets/base'     |                                                      |
| _view_module_version  | string | '1.2.0'                     |                                                      |
| _view_name            | string | 'StyleView'                 |                                                      |
| description_width     | string | ''                          | Width of the description to the side of the control. |
"
  {
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "DescriptionStyleModel"
   _view_module          "@jupyter-widgets/base"
   _view_module_version  "1.2.0"
   _view_name            "StyleView"
   description_width     ""
   })

(defwidget directional-link "DirectionalLinkModel" "1.5.0" "1.5.0"
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
"
  {
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "DirectionalLinkModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            nil
   source                []
   target                []
   })

(defwidget dropdown "DropdownModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "DropdownModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "DropdownView"
   description           ""
   description_tooltip   nil
   disabled              false
   index                 nil
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   })

(defwidget file-upload "FileUploadModel" "1.5.0" "1.5.0"
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
"
  {
   _counter              0
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "FileUploadModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "FileUploadView"
   accept                ""
   button_style          ""
   data                  []
   description           ""
   description_tooltip   nil
   disabled              false
   error                 ""
   icon                  "upload"
   layout                nil ;; reference to new instance
   metadata              []
   multiple              false
   style                 nil ;; reference to new instance
   })

(defwidget float-log-slider "FloatLogSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "FloatLogSliderModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "FloatLogSliderView"
   base                  10.0
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   4.0
   min                   0.0
   orientation           "horizontal"
   readout               true
   readout_format        ".3g"
   step                  0.1
   style                 nil ;; reference to new instance
   value                 1.0})

(defwidget float-progress-model "FloatProgressModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "FloatProgressModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ProgressView"
   bar_style             ""
   description           ""
   description_tooltip   nil
   layout                nil ;; reference to new instance
   max                   100.0
   min                   0.0
   orientation           "horizontal"
   style                 nil ;; reference to new instance
   value                 0.0})

(defwidget float-range-slider "FloatRangeSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "FloatRangeSliderModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "FloatRangeSliderView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   100.0
   min                   0.0
   orientation           "horizontal"
   readout               true
   readout_format        ".2f"
   step                  0.1
   style                 nil ;; reference to new instance
   value                 [0.0, 1.0]})

(defwidget float-slider "FloatSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "FloatSliderModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "FloatSliderView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   100.0
   min                   0.0
   orientation           "horizontal"
   readout               true
   readout_format        ".2f"
   step                  0.1
   style                 nil ;; reference to new instance
   value                 0.0})

(defwidget float-text "FloatTextModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "FloatTextModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "FloatTextView"
   continuous_update     false
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   step                  nil
   style                 nil ;; reference to new instance
   value                 0.0
   })

(defwidget grid-box "GridBoxModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "GridBoxModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "GridBoxView"
   box_style             ""
   children              []
   layout                nil ;; reference to new instance
   })

(defwidget hbox "HBoxModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "HBoxModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "HBoxView"
   box_style             ""
   children              []
   layout                nil ;; reference to new instance
   })

(defwidget html-math-model "HTMLMathModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "HTMLMathModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "HTMLMathView"
   description           ""
   description_tooltip   nil
   layout                nil ;; reference to new instance
   placeholder           "\u200b"
   style                 nil ;; reference to new instance
   value                 ""
   })

(defwidget html "HTMLModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "HTMLModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "HTMLView"
   description           ""
   description_tooltip   nil
   layout                nil ;; reference to new instance
   placeholder           "\u200b"
   style                 nil ;; reference to new instance
   value                 ""})

(defwidget image "ImageModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ImageModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ImageView"
   format                "png"
   height                ""
   layout                nil ;; reference to new instance
   value                ""
   width                 ""
   })

(defwidget int-progress "IntProgressModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "IntProgressModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ProgressView"
   bar_style             ""
   description           ""
   description_tooltip   nil
   layout                nil ;; reference to new instance
   max                   100
   min                   0
   orientation           "horizontal"
   style                 nil ;; reference to new instance
   value                 0
   })

(defwidget int-range-slider "IntRangeSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "IntRangeSliderModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "IntRangeSliderView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   100
   min                   0
   orientation           "horizontal"
   readout               true
   readout_format        "d"
   step                  1
   style                 nil ;; reference to new instance
   value                 [0, 1]
   })

(defwidget int-slider-model "IntSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "IntSliderModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "IntSliderView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   max                   100
   min                   0
   orientation           "horizontal"
   readout               true
   readout_format        "d"
   step                  1
   style                 nil ;; reference to new instance
   value                 0

   })

(defwidget int-text "IntTextModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "IntTextModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "IntTextView"
   continuous_update     false
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   step                  1
   style                 nil ;; reference to new instance
   value                 0
   }
  )

(defwidget label "LabelModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "LabelModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "LabelView"
   description           ""
   description_tooltip   nil
   layout                nil ;; reference to new instance
   placeholder           "\u200b"
   style                 nil ;; reference to new instance
   value                 ""

   })

(defwidget link "LinkModel" "1.5.0" "1.5.0"
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
"
  {
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "LinkModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            nil
   source                []
   target                []})

(defwidget password "PasswordModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "PasswordModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "PasswordView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   placeholder           "\u200b"
   style                 nil ;; reference to new instance
   value                 ""})

(defwidget play "PlayModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "PlayModel"
   _playing              false
   _repeat               false
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "PlayView"
   description           ""
   description_tooltip   nil
   disabled              false
   interval              100
   layout                nil ;; reference to new instance
   max                   100
   min                   0
   show_repeat           true
   step                  1
   style                 nil ;; reference to new instance
   value                 0})

(defwidget progress-style "ProgressStyleModel" "1.5.0" "1.2.0"
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
"
  {
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ProgressStyleModel"
   _view_module          "@jupyter-widgets/base"
   _view_module_version  "1.2.0"
   _view_name            "StyleView"
   bar_color             nil
   description_width     ""})

(defwidget radio-buttons "RadioButtonsModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "RadioButtonsModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "RadioButtonsView"
   description           ""
   description_tooltip   nil
   disabled              false
   index                 nil
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   })

(defwidget select "SelectModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "SelectModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "SelectView"
   description           ""
   description_tooltip   nil
   disabled              false
   index                 nil
   layout                nil ;; reference to new instance
   rows                  5
   style                 nil ;; reference to new instance
   })

(defwidget select-multiple "SelectMultipleModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "SelectMultipleModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "SelectMultipleView"
   description           ""
   description_tooltip   nil
   disabled              false
   index                 []
   layout                nil ;; reference to new instance
   rows                  5
   style                 nil ;; reference to new instance
   })

(defwidget selection-range-slider "SelectionRangeSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "SelectionRangeSliderModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "SelectionRangeSliderView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   index                 [0, 0]
   layout                nil ;; reference to new instance
   orientation           "horizontal"
   readout               true
   style                 nil ;; reference to new instance
   })

(defwidget selection-slider "SelectionSliderModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "SelectionSliderModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "SelectionSliderView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   index                 0
   layout                nil ;; reference to new instance
   orientation           "horizontal"
   readout               true
   style                 nil ;; reference to new instance
   })

(defwidget slider-style "SliderStyleModel" "1.5.0" "1.2.0"
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
"
  {
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "SliderStyleModel"
   _view_module          "@jupyter-widgets/base"
   _view_module_version  "1.2.0"
   _view_name            "StyleView"
   description_width     ""
   handle_color          nil})

(defwidget tab "TabModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "TabModel"
   _titles               {}
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "TabView"
   box_style             ""
   children              []
   layout                nil ;; reference to new instance
   selected_index        0})

(defwidget text "TextModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "TextModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "TextView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   placeholder           "\u200b"
   style                 nil ;; reference to new instance
   value                 ""})

(defwidget textarea "TextareaModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "TextareaModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "TextareaView"
   continuous_update     true
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   placeholder           "\u200b"
   rows                  nil
   style                 nil ;; reference to new instance
   value                 ""})

(defwidget toggle-button "ToggleButtonModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ToggleButtonModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ToggleButtonView"
   button_style          ""
   description           ""
   description_tooltip   nil
   disabled              false
   icon                  ""
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   tooltip               ""
   value                 false})

(defwidget toggle-buttons "ToggleButtonsModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ToggleButtonsModel"
   _options_labels       []
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ToggleButtonsView"
   button_style          ""
   description           ""
   description_tooltip   nil
   disabled              false
   icons                 []
   index                 nil
   layout                nil ;; reference to new instance
   style                 nil ;; reference to new instance
   tooltips              []})

(defwidget vbox "VBoxModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "VBoxModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "VBoxView"
   box_style             ""
   children              []
   layout                nil ;; reference to new instance
   })

(defwidget valid "ValidModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "ValidModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "ValidView"
   description           ""
   description_tooltip   nil
   disabled              false
   layout                nil ;; reference to new instance
   readout               "Invalid"
   style                 nil ;; reference to new instance
   value                 false})

(defwidget video "VideoModel" "1.5.0" "1.5.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/controls"
   _model_module_version "1.5.0"
   _model_name           "VideoModel"
   _view_module          "@jupyter-widgets/controls"
   _view_module_version  "1.5.0"
   _view_name            "VideoView"
   autoplay              true
   controls              true
   format                "mp4"
   height                ""
   layout                nil ;; reference to new instance
   loop                  true
   value                 ""
   width                 ""})

(defwidget output "OutputModel" "1.0.0" "1.0.0"
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
"
  {
   _dom_classes          []
   _model_module         "@jupyter-widgets/output"
   _model_module_version "1.0.0"
   _model_name           "OutputModel"
   _view_module          "@jupyter-widgets/output"
   _view_module_version  "1.0.0"
   _view_name            "OutputView"
   layout                nil ;; reference to new instance
   msg_id                ""
   outputs               []})

;;; ------------------------------------------------------------------------------------------------------------------------
;;; INTERACT
;;; ------------------------------------------------------------------------------------------------------------------------

(defn interact
  [main-w f wmap]
  (let [agent-key (gensym)
        cur (atom (into {} (for [[k w] wmap] [k (get w :value)])))]
    (doseq [[k w] wmap]
      (assert (comm-atom/comm-atom? w))
      (comm-atom/watch w agent-key
                       (fn [_ _ _ new]
                         (swap! cur assoc k (get new :value))
                         (assoc main-w :value (f @cur)))))
    cur))

