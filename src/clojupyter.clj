(ns clojupyter
  (:require [clojupyter.kernel.logo :as logo]
            [clojupyter.kernel.version :as ver]))

(def ^:dynamic *logo*		(logo/logo-image))
(def ^:dynamic *license*	(logo/render-license))

(def ^:dynamic *version*
  "Value is a map representing the version of clojupyter as a map with the keys `:version/major`,
  `:version/minor`, `:version/incremental`, and `qualifier`, where the former 3 are integers and the latter
  is a string.  Analoguous to `*clojure-version*`."
  ver/NO-VERSION)
