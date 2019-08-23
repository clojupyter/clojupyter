(ns clojupyter.install.conda.build-actions

  ;; Clojupyter supports installs using Conda, the recommended way to install Jupyter, and thus
  ;; provides a convenient way for end-users to install a generic Clojupyter (i.e. a kernel
  ;; providing simply a Clojure and Clojupyter along with the libraries needed to provide Clojupyter.

  ;; This namespace provides functions for building the Conda package needed to distribute
  ;; Clojupyter using Conda.  Most users will not need to use it because Conda packages are build
  ;; and made available as part of the Clojupyter release processs.  You therefore most likely do
  ;; not to use this namespace.

  ;; Note/Disclaimer: Clojupyter's Conda support is still under development.

  ;; Functions whose name begins with 's*' return a single-argument function accepting and returning
  ;; a state map.

  (:require
   [clojure.java.io				:as io]
   [clojure.java.shell				:as sh]
   [clojure.spec.alpha				:as s]
   [clojure.string				:as str]
   [io.simplect.compose						:refer [def- γ Γ π Π]]
   ,,
   [clojupyter.install.conda.specs		:as sp]
   [clojupyter.install.filemap			:as fm]
   [clojupyter.install.plan					:refer :all]
   [clojupyter.util-actions			:as u!]))

(def- bat-file?		(Γ str (π re-find #"\.bat$") boolean))
(def- nl->crnl		(Γ str/split-lines (π str/join "\r\n")))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn get-build-environment
  "Returns a map representing the information about the build environment needed to build a conda
  package."
  [source-jarfile]
  (let [conda-exe (u!/find-executable "conda")
        source-jarfile (u!/normalized-file source-jarfile)
        env (merge sp/DEFAULT-BUILD-ENV
                   {:conda-build-env/conda-exe conda-exe
                    :conda-build-env/filemap (fm/filemap conda-exe source-jarfile)})]
    (if (s/valid? :conda-build/env env)
      env
      (u!/throw-info "get-build-environment: internal err"
        {:env env, :explain-str (s/explain-str :conda-build/env env)}))))

(defn copy-template-file!
  "Action to copy a conda script file, converting it DOS/Windows text format if it is a .BAT file."
  [from to]
  (let [xf (if (bat-file? from) nl->crnl identity)]
    (->> from io/resource slurp xf (spit to))))

(defn output-yaml-file!
  "Action to output the conda 'meta.yaml' file."
  [blddir yaml-string]
  (spit (io/file (str blddir "/meta.yaml")) yaml-string))

(defn s*do-conda-build!
  "Action to perform the actual conda build."
  [conda-exe blddir]
  (s*bind-state S
    (let [{:keys [exit out err]} (sh/with-sh-dir blddir (sh/sh (str conda-exe) "build" "."))]
      (Γ (s*set-values :conda-build/build-cmd-out out
                       :conda-build/build-cmd-err err
                       :conda-build/build-cmd-exit-code exit)
         (if (zero? exit)
           (s*log-info {:message "conda-build successful"})
           (s*log-error {:message (str "Error: conda-build terminated with exit-code " exit)
                         :type :bad-conda-build-exit}))))))
