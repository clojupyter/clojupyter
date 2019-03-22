(ns clojupyter.misc.version
  (:require
   [clojure.edn					:as edn]
   [clojure.java.io				:as io]))

(defn- read-digits
  [s]
  (edn/read-string (str "10r" s)))

(def ^:private version-data
  (memoize
   (fn []
     (when-let [f (io/resource "version.edn")]
       (-> f slurp edn/read-string)))))

(defn version-string
  "Returns clojupyter's version as a string."
  ([] (version-string (version-data)))
  ([{:keys [version]}]
   (str (or version "0.0.0"))))

(defn- version-map
  [s]
  (when s
    (if-let [[_ major minor incr qual] (re-find #"^(\d+)\.(\d+)\.(\d+)(?:-(.*))?$" s)]
      {:major (read-digits major), :minor (read-digits minor),
       :incremental (read-digits incr), :qualifier qual}
      {:major 0, :minor 0, :incremental 0, :qualifier nil})))

(def version
  "Returns version information as a map with the keys `:major`,
  `:minor`, `:incremental`, and `qualifier`, where the former 3 are
  integers and the latter is a string.  Analoguous to
  `*clojure-version*`." 
  (memoize
   (fn []
     (let [data (version-data)]
       (merge data (version-map (version-string data)))))))
