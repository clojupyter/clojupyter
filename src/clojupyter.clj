(ns clojupyter)

(def ^:dynamic *clojupyter-version*
  "Value is a map representing the version of clojupyter as a map with
  the keys `:major`, `:minor`, `:incremental`, and `qualifier`, where
  the former 3 are integers and the latter is a string.  Analoguous to
  `*clojure-version*`." 
  {:major 0, :minor 0, :incremental 0, :qualifier nil})
