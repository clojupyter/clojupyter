(ns clojupyter.install.conda.build
  (:require [clojupyter.cmdline.api :as cmdline]
            [clojupyter.install.conda.build-actions :as build!]
            [clojupyter.install.conda.yaml :as pkg-yaml]
            [clojupyter.install.filemap :as fm]
            [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.install.local :as local]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.install.log :as log]
            [clojupyter.kernel.version :as ver]
            [clojupyter.plan :as pl]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :refer [instrument]]
            [clojure.string :as str]
            [io.simplect.compose :refer [C def- p sdefn sdefn-]]
            [io.simplect.compose.action :as action]))

(use 'clojure.pprint)

(def LSP-DEPEND [csp/DEPEND-DUMMY lsp/DEPEND-DUMMY])

(defn- conda-link-opts
  [ident filemap source-jarfiles blddir kernel-dir]
  (assoc lsp/DEFAULT-USER-OPTS
         :local/source-jarfiles source-jarfiles
         :local/destdir (io/file (str blddir "/" kernel-dir))
         :local/filemap filemap
         :local/ident ident
         :local/generate-kernel-json? false))

(sdefn- s*generate-conda-build-effects (s/cat :blddir :local/file
                                              :install-env :local/install-env
                                              :build-env :conda-build/env
                                              :build-params :conda-build/params)
  "Returns a function which, given a state, adds effects to the state which will perform a conda
  build."
  [blddir install-env build-env build-params]
  (let [{:keys [:conda-build-params/buildnum :conda-build-params/filemap
                :local/ident :local/source-jarfiles]}
        ,, build-params
        {:keys [:conda-build-env/kernel-dir, :conda-build-env/resource-copyspec,
                :conda-build-env/conda-exe, :conda-build-env/filemap]}
        ,, build-env
        {:keys [:version/version-map]}
        ,, install-env
        install-opts (conda-link-opts ident filemap source-jarfiles blddir kernel-dir)
        install-spec (local/install-spec install-opts install-env)
        yaml-string (pkg-yaml/yaml-string version-map buildnum kernel-dir)]
    (letfn [(dirfile [dir n] (->> n str io/file .getName (str dir "/" ) io/file)) ]
      (C (pl/s*log-debug {:conda-build/blddir blddir
                          :conda-build/buildnum buildnum
                          :conda-build/build-env build-env
                          :conda-build/build-params build-params
                          :conda-build/install-env install-env
                          :conda-build/install-opts install-opts
                          :conda-build/install-spec install-spec
                          :conda-build/yaml-string yaml-string})
         (local/s*generate-install-effects install-spec)
         ;; COPY RESOURCE (LINK SCRIPT FILES)
         (apply C (for [[rnm nm] resource-copyspec]
                    (pl/s*action-append [`build!/copy-template-file! rnm (dirfile blddir nm)])))
         ;; GENERATE YAML CONFIGURATION FILE
         (pl/s*action-append [`build!/output-yaml-file! blddir yaml-string])
         ;; BUILD CONDA
         (if (fm/file filemap conda-exe)
           ;; This effect needs access to threaded state is thus instantiated using a function
           ;; instead of the syntax-quoted version:
           (pl/s*action-append (action/step (build!/s*do-conda-build! conda-exe blddir)
                                            [:do-conda-build-in blddir]))
           (pl/s*log-error {:message "Conda executable not found."
                            :type :conda-exe-not-found}))))))

(def- PRE "    ")
(def- SEP (str PRE (apply str (repeat 120 \-))))
(def- err-output (C (p str/split-lines)
                    (p remove (p re-find #"numpy|symlink_conda.*is deprecated"))
                    (p mapv (p str PRE "conda-build: "))))

(def- output-location (C (p str/split-lines)
                         (p map (C (p re-find #"^anaconda upload (.+)") second))
                         (p remove nil?)))

(def s*report-conda-build
  "Returns a function which, given a state, use the cmdline api to add information about the conda
  build."
  (pl/s*bind-state S
    (let [{:keys [:conda-build/build-cmd-exit-code
                  :conda-build/build-cmd-err
                  :conda-build/build-cmd-out]} (-> S pl/get-action-result action/output)
          log (pl/get-log S)
          build-cmd-exit-code (or build-cmd-exit-code 1)]
      (cond
        (pl/halted? S)
        ,, (C (cmdline/output "Conda build generation stopped.")
              (log/s*report-log log)
              (cmdline/set-result S)
              (cmdline/set-exit-code 1))
        (and (pl/execute-success? S) (zero? build-cmd-exit-code))
        ,, (let [outloc (output-location build-cmd-out)]
             (C (cmdline/output "Conda build completed successfully.")
                (if (-> outloc count (= 1))
                  (cmdline/output (str "Conda file output to " (first outloc)) )
                  (cmdline/output "Location of output file not found in conda-build output."))
                (cmdline/set-result {:install-location outloc})
                (cmdline/set-exit-code 0)))
        build-cmd-err
        ,, (C (cmdline/output "Conda build failed.")
              (pl/s*when-not (zero? build-cmd-exit-code)
                (C (cmdline/outputs ["" (str "Conda build: exit(" build-cmd-exit-code ").")
                                     "" "Conda build error output:" SEP])
                   (cmdline/outputs (err-output build-cmd-err))
                   (cmdline/outputs [SEP ""])))
              (cmdline/set-result S)
              (cmdline/set-exit-code 1))
        :else (C (cmdline/output "Conda build failed for unknown reasons.")
                 (log/s*report-log log)
                 (cmdline/set-result S)
                 (cmdline/set-exit-code 1))))))

;;; ----------------------------------------------------------------------------------------------------
;;; USED FROM CMDLINE
;;; ----------------------------------------------------------------------------------------------------

(sdefn s*conda-build (s/cat :opts (s/keys :opt-un [::skip-execute?])
                            :blddir :local/file
                            :install-env :local/install-env
                            :build-env :conda-build/env
                            :build-params :conda-build/params)
  "Returns a function which, given a state, will calculate the actions required to perform a conda
  build, execute the actions, and finally report on the result."
  [{:keys [skip-execute?]} blddir install-env build-env build-params]
  (C (if skip-execute? pl/s*set-dont-execute pl/s*set-do-execute)
     (s*generate-conda-build-effects blddir install-env build-env build-params)
     pl/s*execute))

(instrument `s*generate-conda-build-effects)
(instrument `s*conda-build)
