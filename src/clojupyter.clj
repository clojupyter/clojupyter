(ns clojupyter
  (:require [clojure.java.io :as io])
  (:import javax.imageio.ImageIO))


(def ^:dynamic *logo*		(ImageIO/read (io/resource "clojupyter/assets/logo-350x80.png")))
(def ^:dynamic *license*	(slurp (io/resource "clojupyter/assets/license.txt")))

(def ^:dynamic *version*
  "Value is a map representing the version of clojupyter as a map with the keys `:version/major`,
  `:version/minor`, `:version/incremental`, and `qualifier`, where the former 3 are integers and the latter
  is a string.  Analoguous to `*clojure-version*`."
  (-> (io/resource "clojupyter/assets/version.edn")
      slurp
      read-string
      :version))
