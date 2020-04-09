(ns clojupyter.widgets.widgets-specs
  (:require [clojure.spec.alpha :as s]))

;; General specs for widgets
(s/def ::_model_module string?)
(s/def ::_model_module_version string?)
(s/def ::_model_name string?)
(s/def ::_view_module string?)
(s/def ::_view_module_version string?)
(s/def ::_view_name string?)

(s/def ::base-widget (s/keys :req-un [::_model_module ::_model_module_version ::_model_name
                                      ::_view_module ::_view_module_version  ::_view_name]))

(s/def ::_dom_classes (s/coll-of string? :kind vector?))
(s/def ::continuous_update boolean?)
(s/def ::description string?)
(s/def ::description_tooltip (s/nilable string?))
(s/def ::disabled boolean?)
(s/def ::width string?)
(s/def ::height string?)
(s/def ::box_style #{"success" "info" "warning" "danger" ""})
(s/def ::button_style #{"primary" "success" "info" "warning" "danger" ""})
(s/def ::icon string?)
(s/def ::tooltip string?)
(s/def ::button_color (s/nilable string?))
(s/def ::font_weight string?)
(s/def ::indent boolean?)
(s/def ::concise boolean?)
(s/def ::ensure_option boolean?)
(s/def ::options (s/coll-of string? :kind vector?))
(s/def ::placeholder string?)
(s/def ::connected boolean?)
(s/def ::name string?)
(s/def ::timestamp float?)
(s/def ::description_width string?)
(s/def ::source vector?)
(s/def ::target vector?)
(s/def ::_counter integer?)
(s/def ::accept string?)
(s/def ::multiple boolean?)
(s/def ::base float?)
(s/def ::orientation #{"horizontal" "vertical"})
(s/def ::readout boolean?)
(s/def ::readout_format string?)
(s/def ::bar_style ::box_style)
(s/def ::interval integer?)
(s/def ::_options_labels (s/coll-of string? :kind vector?))
(s/def ::bar_color (s/nilable string?))
(s/def ::rows integer?)
(s/def ::handle_color (s/nilable string?))
(s/def ::selected_index (s/nilable integer?))
(s/def ::icons (s/coll-of string? :kind vector?))
(s/def ::toltips (s/coll-of string? :kind vector?))
(s/def ::outputs vector?)
(s/def ::msg_id string?)

;; Specs for audio/video widgets
(s/def ::autoplay boolean?)
(s/def ::controls boolean?)
(s/def ::format string?)
(s/def ::loop boolean?)
(s/def ::_playing boolean?)
(s/def ::_repeat boolean?)
(s/def ::show_repeat boolean?)

;; Disambiguate for keys with same name, but different value types
(s/def :bytes/value bytes?)

(s/def :float/min float?)
(s/def :float/max float?)
(s/def :float/value float?)
(s/def :float-nil/step (s/nilable float?))
(s/def :float/step float?)

(s/def :int/max integer?)
(s/def :int/min integer?)
(s/def :int/value integer?)
(s/def :int/step integer?)
(s/def :int/index integer?)
(s/def :int-nil/index (s/nilable integer?))
(s/def :int-nil/rows (s/nilable integer?))

(s/def :bool/value boolean?)

(s/def :str/value string?)

(s/def :vec/value vector?)
(s/def :vec-int/index (s/coll-of integer? :kind vector?))
(s/def :vec/index vector?)

(s/def ::audio (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::autoplay ::controls ::format ::layout ::loop :bytes/value])))
(s/def ::video (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::autoplay ::controls ::format ::height ::layout ::loop
                                                       :bytes/value ::width])))
(s/def ::image (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::format ::height ::layout :bytes/value ::width])))
(s/def ::bounded-float-text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip
                                                                    ::disabled ::layout :float/max :float/min :float-nil/step ::style :float/value])))
(s/def ::bounded-int-text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip
                                                                  :disabled ::layout :int/max :int/min :int/step ::style :int/value])))
(s/def ::box (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::box_style ::children ::layout])))
(s/def ::button (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::button_style ::description ::disabled ::icon ::layout ::style ::tooltip])))
(s/def ::button-style (s/merge ::base-widget (s/keys :req-un [::button_color ::font_weight])))
(s/def ::check-box (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::disabled ::indent ::layout ::style
                                                           ::bool/value])))
(s/def ::color-picker (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::concise ::description ::description_tooltip ::disabled ::layout ::syle
                                                              ::str/value])))
(s/def ::combobox (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::ensure_option
                                                          ::layout ::placeholder ::style ::value])))
(s/def ::controller-axis (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::layout ::value])))
(s/def ::controller (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::axes ::buttons ::connected :int/index ::layout ::name ::timestamp])))
(s/def ::dom-widget (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::layout ::value])))
(s/def ::date-picker (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::disabled ::layout ::style ::date-nil/value])))
(s/def ::description-style (s/merge ::base-widget (s/keys :req-un [::description_width])))
(s/def ::directional-link (s/merge ::base-widget (s/keys :req-un [::source ::target])))
(s/def ::dropdown (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::disabled ::int-nil/index ::layout ::style])))
(s/def ::file-upload (s/merge ::base-widget (s/keys :req-un [::_counter ::_dom_classes ::accept ::button_style ::data ::description ::description_tooltip
                                                             ::disabled ::error ::icon ::layout ::metadata ::multiple ::style])))
(s/def ::float-log-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::base ::continous_update ::description ::description_tooltip ::disabled
                                                                  ::layout :float/max :float/min ::orientation ::readout ::readout_format :float/step
                                                                  ::style :float/value])))
(s/def ::float-progress (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::bar_style ::description ::description_tooltip ::layout :float/max :float/min
                                                                ::orientation ::style :float/value])))
(s/def ::float-range-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout
                                                                    :float/max :float/min ::orientation ::readout ::readout_format :float/step ::style :vec/value])))
(s/def ::float-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout :float/max
                                                              :float/min ::orientation ::readout ::readout_format :float/step ::style :float/value])))
(s/def ::float-text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout :float/step
                                                            ::style :float/value])))
(s/def ::grid-box (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::box_style ::children ::layout])))
(s/def ::h-box (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::box_style ::children ::layout])))
(s/def ::html-math (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::layout ::placeholder ::style :str/value])))
(s/def ::html (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::layout ::placeholder ::style :str/value])))
(s/def ::image (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::format ::height ::layout :bytes/value ::width])))
(s/def ::int-progress (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::bar_style ::description ::description_tooltip ::layout :int/max :int/min ::orientation
                                                              ::style ::value])))
(s/def ::int-range-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout :int/max
                                                                  :int/min ::orientation ::readout ::readout_format :int/step ::style :vec/value])))
(s/def ::int-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout :int/max :int/min
                                                            ::orientation ::readout ::readout_format :int/step ::style :int/value])))
(s/def ::int-text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout :int/step ::style
                                                          :int/value])))
(s/def ::label (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::layout ::placeholder ::style :str/value])))
(s/def ::link (s/merge ::base-widget (s/keys :req-un [::source ::target])))
(s/def ::password (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout ::placeholder ::style
                                                          :str/value])))
(s/def ::play (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_playing ::_repeat ::description ::description_tooltip ::disabled ::interval ::layout :int/max :int/min
                                                      ::show_repeat :int/step ::style :int/value])))
(s/def ::progress_style (s/merge ::base-widget (s/keys :req-un [::bar_color ::description_width])))
(s/def ::radio-buttons (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_options_labels ::description ::description_tooltip ::disabled :int-nil/index ::layout ::style])))
(s/def ::select (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_options_labels ::description ::description_tooltip ::disabled :int-nil/index ::layout ::rows ::style])))
(s/def ::select-multiple (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_options_labels ::description ::description_tooltip ::disabled :vec-int/index
                                                                 ::layout ::rows ::style])))
(s/def ::selection-range-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_options_labels ::continuous_update ::description ::description_tooltip ::disabled
                                                                        :vec/index ::layout ::orientation ::readout ::style])))
(s/def ::selection-slider (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_options_labels ::continuous_update ::description ::description_tooltip ::disabled :int/index
                                                                  ::layout ::orientation ::readout ::style])))
(s/def ::slider-style (s/merge ::base-widget (s/keys :req-un [::description_width ::handle_color])))
(s/def ::tab (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_titles ::box_style ::children ::layout ::selected_index])))
(s/def ::text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout ::placeholder ::style :str/value])))
(s/def ::textarea (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip ::disabled ::layout ::placeholder :int-nil/rows
                                                          ::style :str/value])))
(s/def ::toggle-button (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::button_style ::description ::description_tooltip ::disabled ::icon ::layout ::style ::tooltip :bool/value])))
(s/def ::toggle-buttons (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::_options_labels ::button_style ::description ::description_tooltip ::disabled ::icons ::int-nil/index
                                                                ::layout ::style ::tooltips])))
(s/def ::v-box (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::box_style ::children ::layout])))
(s/def ::valid (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::description ::description_tooltip ::disabled ::layout ::readout ::style :bool/value])))
(s/def ::video (s/merge ::base-widget (s/key :req-un [::_dom_classes ::autoplay ::controls ::format ::height ::layout ::loop :bytes/value ::width])))
(s/def ::output (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::layout ::msg_id ::outputs])))
