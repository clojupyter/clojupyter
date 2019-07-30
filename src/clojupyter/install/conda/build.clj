(ns clojupyter.install.conda.build

  ;; Clojupyter supports installs using Conda, the recommended way to install Jupyter, and thus
  ;; provides a convenient way for end-users to install a generic Clojupyter (i.e. a kernel
  ;; providing simply a Clojure and Clojupyter along with the libraries needed to provide
  ;; Clojupyter).

  ;; This namespace provides functions for building the Conda package needed to distribute
  ;; Clojupyter using Conda.  Most users will not need to use it because Conda packages are build
  ;; and made available as part of the Clojupyter release processs.  You therefore most likely do
  ;; not to use this namespace.

  ;; Functions whose name begins with 's*' return a single-argument function accepting and returning
  ;; a state map.

  ;; Note/Disclaimer: Clojupyter's Conda support is still under development.

  (:require
   [clojure.java.io				:as io]
   [clojure.spec.alpha				:as s]
   [clojure.spec.test.alpha					:refer [instrument]]
   [clojure.string				:as str]
   [io.simplect.compose						:refer [def- sdefn sdefn- π Π γ Γ λ]]
   [io.simplect.compose.action			:as action]
   ,,
   [clojupyter.cmdline.api			:as cmdline]
   [clojupyter.install.conda.build-actions	:as build!]
   [clojupyter.install.conda.specs		:as csp]
   [clojupyter.install.conda.yaml		:as pkg-yaml]
   [clojupyter.install.filemap			:as fm]
   [clojupyter.install.local			:as local]
   [clojupyter.install.local-specs		:as lsp]
   [clojupyter.install.log			:as log]
   [clojupyter.install.plan					:refer :all]
   [clojupyter.kernel.version			:as ver]))

(use 'clojure.pprint)

(defn- conda-link-opts
  [ident filemap source-jarfiles blddir kernel-dir]
  (assoc lsp/DEFAULT-USER-OPTS
         :local/source-jarfiles source-jarfiles
         :local/destdir (io/file (str blddir "/" kernel-dir))
         :local/filemap filemap
         :local/icon-bot (ver/version-string-short)
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
      (Γ (s*log-debug {:conda-build/blddir blddir
                       :conda-build/buildnum buildnum
                       :conda-build/build-env build-env
                       :conda-build/build-params build-params
                       :conda-build/install-env install-env
                       :conda-build/install-opts install-opts
                       :conda-build/install-spec install-spec
                       :conda-build/yaml-string yaml-string})
         (local/s*generate-install-effects install-spec)
         ;; COPY RESOURCE (LINK SCRIPT FILES)
         (apply Γ (for [[rnm nm] resource-copyspec]
                    (s*action-append [`build!/copy-template-file! rnm (dirfile blddir nm)])))
         ;; GENERATE YAML CONFIGURATION FILE
         (s*action-append [`build!/output-yaml-file! blddir yaml-string])
         ;; BUILD CONDA
         (if (fm/file filemap conda-exe)
           ;; This effect needs access to threaded state is thus instantiated using a function
           ;; instead of the syntax-quoted version:
           (s*action-append (action/step (build!/s*do-conda-build! conda-exe blddir)
                                         [:do-conda-build-in blddir]))
           (s*log-error {:message "Conda executable not found."
                         :type :conda-exe-not-found}))))))

(def- PRE "    ")
(def- SEP (str PRE (apply str (repeat 120 \-))))
(def- err-output (Γ (π str/split-lines)
                    (π remove (π re-find #"numpy|symlink_conda.*is deprecated"))
                    (π mapv (π str PRE "conda-build: "))))

(def- output-location (Γ (π str/split-lines)
                         (π map (Γ (π re-find #"^anaconda upload (.+)") second))
                         (π remove nil?)))

(def s*report-conda-build
  "Returns a function which, given a state, use the cmdline api to add information about the conda
  build."
  (s*bind-state S
    (let [{:keys [:conda-build/build-cmd-exit-code
                  :conda-build/build-cmd-err
                  :conda-build/build-cmd-out]} (-> S get-action-result action/output)
          log (get-log S)
          build-cmd-exit-code (or build-cmd-exit-code 1)]
      (cond
        (halted? S)
        ,, (Γ (cmdline/output "Conda build generation stopped.")
              (log/s*report-log log)
              (cmdline/set-result S)
              (cmdline/set-exit-code 1))
        (execute-success? S)
        ,, (let [outloc (output-location build-cmd-out)]
             (Γ (cmdline/output "Conda build completed successfully.")
                (if (-> outloc count (= 1))
                  (cmdline/output (str "Conda file output to " (first outloc)) )
                  (cmdline/output "Location of output file not found in conda-build output."))
                (cmdline/set-result {:install-location outloc})
                (cmdline/set-exit-code 0)))
        build-cmd-err
        ,, (Γ (cmdline/output "Conda build failed.")
              (s*when-not (zero? build-cmd-exit-code)
                (Γ (cmdline/outputs ["" (str "Conda build: exit(" build-cmd-exit-code ").")
                                     "" "Conda build error output:" SEP])
                   (cmdline/outputs (err-output build-cmd-err))
                   (cmdline/outputs [SEP ""])))
              (cmdline/set-result S)
              (cmdline/set-exit-code 1))
        :else (Γ (cmdline/output "Conda build failed for unknown reasons.")
                 (log/s*report-log log)
                 (cmdline/set-result S)
                 (cmdline/set-exit-code 1))))))

;;; ----------------------------------------------------------------------------------------------------
;;; USED FROM CMDLINE
;;; ----------------------------------------------------------------------------------------------------

(sdefn s*conda-build (s/cat :blddir :local/file
                                    :install-env :local/install-env
                                    :build-env :conda-build/env
                                    :build-params :conda-build/params)
  "Returns a function which, given a state, will calculate the actions required to perform a conda
  build, execute the actions, and finally report on the result."
  [blddir install-env build-env build-params]
  (Γ s*set-do-execute
     (s*generate-conda-build-effects blddir install-env build-env build-params)
     s*execute))

(instrument `s*generate-conda-build-effects)
(instrument `s*conda-build)
