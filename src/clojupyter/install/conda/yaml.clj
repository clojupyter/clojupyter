(ns clojupyter.install.conda.yaml
  (:require [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.kernel.version :as ver]
            [clojupyter.util :as u]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :refer [instrument]]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [io.simplect.compose :refer [C def- p P sdefn]]
            [yaml.core :as yaml]))

(def DEPEND [csp/DEPEND-DUMMY])

(def- JUP-DEPS ["ipywidgets" "jupyterlab" "notebook" "qtconsole" "widgetsnbextension"])
(def- BUILD-DEPS ["openjdk"])
(def- RUN-DEPS	(vec (concat BUILD-DEPS JUP-DEPS)))

(def unqualify-kws (p walk/postwalk (u/call-if keyword? (C name keyword))))

(def- esc-chars
  "Escape all single quote characters (\\')."
  (p walk/postwalk
     (u/call-if string?
                (C (P str/replace "%" "%37")
                   (P str/replace "'" "%39")))))

(def- unesc-chars
  "Unescape all single quote characters (\\')."
  (p walk/postwalk
     (u/call-if string?
        (C (P str/replace "%37" "%")
           (P str/replace "%39" "'")))))

(def escaped-yaml-string
  "Encode a value as YAML escaping certain characters in strings."
  (C esc-chars
     (P yaml/generate-string :dumper-options {:flow-style :block})
     unesc-chars))

(def- VERSION-REGEX #"[\w\d\.]")
(def- sanitize-ver (u/sanitize-string VERSION-REGEX))

(defn- conda-configuration
  "Returns the EDN equivalent of the `meta.yaml` file needed to Conda-build Clojupyter."
  [version-map buildnum kernel-dir build-reqs run-reqs]
  (let [version-string (ver/version-string version-map)]
    {:conda-config/about	{:conda-config-about/description "Clojupyter - Run Clojure in Jupyter"
                                 :conda-config-about/home "https://github.com/clojupyter/clojupyter"
                                 :conda-config-about/license "MIT"}
     :conda-config/build 	{:conda-config-build/number buildnum
                                 :conda-config-build/string (str buildnum)}
     :conda-config/package	{:conda-config-package/name "clojupyter"
                                 :conda-config-package/version (sanitize-ver version-string)}
     :conda-config/requirements	{:conda-config-requirements/build build-reqs
                                 :conda-config-requirements/run run-reqs}
     :conda-config/source	{:conda-config-source/folder kernel-dir}}))

(sdefn yaml-string (s/cat :opts (s/? (s/keys :opt-un [::build-deps ::run-deps]))
                          :ver :version/version-map
                          :build-num :conda-config-build/number
                          :kernel-dir :conda-config-source/folder)
  "Returns the string to be written to the `meta.yaml` needed to Conda-build Clojupyter."
  ([version-map build-num kernel-dir] (yaml-string {} version-map build-num kernel-dir))
  ([{:keys [build-deps run-deps]} version-map build-num kernel-dir]
   (let [build-deps (or build-deps BUILD-DEPS)
         run-deps (or run-deps RUN-DEPS)]
     (->> (conda-configuration version-map build-num kernel-dir build-deps run-deps)
          unqualify-kws
          escaped-yaml-string))))

(instrument `yaml-string)
