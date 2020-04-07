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
(s/def ::continous_update boolean?)
(s/def ::description string?)
(s/def ::description_tooltip (s/nilable string?))
(s/def ::disabled boolean?)
(s/def ::width string?)
(s/def ::height string?)

;; Specs for audio/video widgets
(s/def ::autoplay boolean?)
(s/def ::controls boolean?)
(s/def ::format string?)
(s/def ::loop boolean?)

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

(s/def ::audio (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::autoplay ::controls ::format ::layout ::loop :bytes/value]))
(s/def ::video (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::autoplay ::controls ::format ::height ::layout ::loop
                                                       :bytes/value ::width])))
(s/def ::image (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::format ::height ::layout :bytes/value ::width])))
(s/def ::bounded-float-text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip
                                                                    ::disabled ::layout :float/max :float/min :float-nil/step ::style :float/value])))
(s/def ::bounded-int-text (s/merge ::base-widget (s/keys :req-un [::_dom_classes ::continuous_update ::description ::description_tooltip
                                                                  :disabled ::layout :int/max :int/min :int/step ::style :int/value]))
