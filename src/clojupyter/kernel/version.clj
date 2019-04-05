(ns clojupyter.kernel.version
  (:require
   [clojure.edn					:as edn]
   [clojure.java.io				:as io]))

(def ^:private VERSION-FILENAME "version.edn")

(defn- read-digits
  [s]
  (edn/read-string (str "10r" s)))

(def ^:private version-data
  (fn []
    (when-let [f (or (io/resource VERSION-FILENAME)
                     (io/file (str "resources/" VERSION-FILENAME))
                     (throw (Exception. "version.edn not found")))]
      (-> f slurp edn/read-string))))

(defn version-string*
  "Returns clojupyter's version as a string."
  ([] (version-string* (version-data)))
  ([{:keys [version]}]
   (str (or version "0.0.0"))))

(defn version-map
  [{:keys [version raw-version] :as M}]
  (when (and (string? version) (string? raw-version))
    (let [[_ major minor incr qual] (re-find #"^(\d+)\.(\d+)\.(\d+)(?:-(.*))?$" version)
          major (read-digits (or major "0"))
          minor (read-digits (or minor "0"))
          incr (read-digits (or incr "0"))
          [_ raw-suffix raw-dirty?] (re-find #"^([^-]+)(-DIRTY)?$" raw-version)
          qual-suffix (when qual
                        (if raw-suffix
                          (str (subs raw-suffix 0 4) (when raw-dirty? "#"))
                          qual))
          full-version (str major "." minor "." incr
                            (when qual
                              (str "-" qual))
                            (when qual-suffix
                              (str "@" qual-suffix)))]
      (merge M {:major major, :minor minor, :incremental incr, :qualifier qual, :qual-suffix qual-suffix
                :full-version full-version, :formatted-version (str "clojupyter-v" full-version)}))))

(defn version
  "Returns version information as a map with the keys `:major`,
  `:minor`, `:incremental`, and `qualifier`, where the former 3 are
  integers and the latter is a string.  Analoguous to
  `*clojure-version*`." 
  []
  (version-map (version-data)))
