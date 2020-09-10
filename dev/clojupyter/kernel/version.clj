(ns clojupyter.kernel.version
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [io.simplect.compose :refer [C def- p]]))

(s/def :version/major			int?)
(s/def :version/minor			int?)
(s/def :version/incremental		int?)
(s/def :version/qualifier		string?)
(s/def :version/lein-v-raw		string?)

(s/def :version/version-map		(s/keys :req [:version/major :version/minor :version/incremental]
                                                :opt [:version/qualifier :version/lein-v-raw]))

(def NO-VERSION  {:version/major 0, :version/minor 0, :version/incremental 0, :version/qualifier ""})

(def- VERSION-FILENAME "clojupyter/assets/version.edn")

(def- read-digits (C (p str "10r") edn/read-string))

(def- lein-v-version-data (C #(io/resource VERSION-FILENAME)
                             slurp
                             edn/read-string))

(defn- normalize-version
  "Returns a map with `major`, `minor` and `incremental` components defaulted to '0' if not present."
  [{:keys [:version/major :version/minor :version/incremental :version/qualifier :version/lein-v-raw] :as m}]
  (merge m {:version/major (or major 0)
            :version/minor (or minor 0)
            :version/incremental (or incremental 0)}))

(defn version
  "Returns version information as a map with the keys `:version/major`,
  `:version/minor`, `:version/incremental`, and `:version/qualifier`, where the former 3 are
  integers and the latter is a string.  Analoguous to `*clojure-version*`."
  ([] (version (lein-v-version-data)))
  ([{:keys [version raw-version]}]
   (when (string? version)
     (let [[_ major minor incr qual] (re-find #"^(\d+)\.(\d+)\.(\d+)(?:-(.*))?$" version)
           major (read-digits (or major "0"))
           minor (read-digits (or minor "0"))
           incr (read-digits (or incr "0"))]
       (merge {:version/major major, :version/minor minor, :version/incremental incr}
              (when qual
                {:version/qualifier qual})
              (when raw-version
                {:version/lein-v-raw raw-version}))))))

(defn version-string
  "Returns a string representing the information in `version-map` including `major`, `minor`, `incremental`,
  and `qualifier` components, but excluding the `lein-v-raw` component."
  ([] (version-string (version)))
  ([version-map]
   (let [{:keys [:version/major :version/minor :version/incremental :version/qualifier :version/lein-v-raw]}
         ,, (normalize-version version-map)]
     (str major "." minor "." incremental
          (when qualifier (str "-" qualifier))))))

(defn version-string-short
  "Returns a string representing the information in `version-map` components `major`, `minor`, and
  `incremental`.  If either `qualifier` or `lein-v-raw` components are non-nil, an `*` character is
  added to the end."
  ([] (version-string-short (version)))
  ([{:keys [:version/qualifier :version/lein-v-raw] :as version-map}]
   (str (version-string (select-keys version-map [:version/major :version/minor :version/incremental]))
        (when (or qualifier lein-v-raw) "*"))))

(defn version-string-long
  "Returns a string representing the information in all components of `version-map`: `major`, `minor`,
  `incremental`, `qualifier`, and `lein-v-raw` components."
  ([] (version-string-long (version)))
  ([{:keys [:version/lein-v-raw] :as version-map}]
   (str (version-string version-map)
        (when lein-v-raw (str "@" lein-v-raw)))))
