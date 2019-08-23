(ns clojupyter.install.conda.yaml

  ;; This namespace contains functionality to generate the YAML data needed for the `meta.yaml` file
  ;; used to control Conda builds.

  (:require
   [clojure.spec.alpha				:as s]
   [clojure.spec.test.alpha					:refer [instrument]]
   [clojure.string				:as str]
   [clojure.walk				:as walk]
   [io.simplect.compose						:refer [def- sdefn sdefn- π Π γ Γ λ]]
   [yaml.core					:as yaml]
   ,,
   [clojupyter.install.conda.specs		:as csp]
   [clojupyter.kernel.version			:as ver]
   [clojupyter.util				:as u]
   ))

(def- BUILD-REQS ["openjdk=8" "maven"])
(def- RUN-REQS	(vec (concat BUILD-REQS ["notebook>=4.4.0" "ipywidgets>=7.0" "widgetsnbextension"])))

(def unqualify-kws (π walk/postwalk (u/call-if keyword? (Γ name keyword))))

(def- esc-chars
  "Escape all single quote characters (\\')."
  (π walk/postwalk
     (u/call-if string?
                (Γ (Π str/replace "%" "%37")
                   (Π str/replace "'" "%39")))))

(def- unesc-chars
  "Unescape all single quote characters (\\')."
  (π walk/postwalk
     (u/call-if string?
        (Γ (Π str/replace "%37" "%")
           (Π str/replace "%39" "'")))))

(def escaped-yaml-string
  "Encode a value as YAML escaping certain characters in strings."
  (Γ esc-chars
     (Π yaml/generate-string :dumper-options {:flow-style :block})
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

(sdefn yaml-string (s/cat :opts (s/? (s/keys :opt-un [::build-reqs ::run-reqs]))
                          :ver :version/version-map
                          :build-num :conda-config-build/number
                          :kernel-dir :conda-config-source/folder)
  "Returns the string to be written to the `meta.yaml` needed to Conda-build Clojupyter."
  ([version-map build-num kernel-dir] (yaml-string {} version-map build-num kernel-dir))
  ([{:keys [build-reqs run-reqs]} version-map build-num kernel-dir]
   (let [build-reqs (or build-reqs BUILD-REQS)
         run-reqs (or run-reqs RUN-REQS)]
     (->> (conda-configuration version-map build-num kernel-dir build-reqs run-reqs)
          unqualify-kws
          escaped-yaml-string))))

(instrument `yaml-string)
