(ns clojupyter.install.conda.build-actions
  (:require [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.install.filemap :as fm]
            [clojupyter.plan :as pl]
            [clojupyter.tools-actions :as u!]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [io.simplect.compose :refer [C def- p]]))

(def DEPEND [csp/DEPEND-DUMMY])

(def- bat-file?		(C str (p re-find #"\.bat$") boolean))
(def- nl->crnl		(C str/split-lines (p str/join "\r\n")))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn get-build-environment
  "Returns a map representing the information about the build environment needed to build a conda
  package."
  [source-jarfile]
  (let [conda-exe (u!/find-executable "conda")
        source-jarfile (u!/normalized-file source-jarfile)
        env (merge csp/DEFAULT-BUILD-ENV
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
  (pl/s*bind-state S
    (let [{:keys [exit out err]} (sh/with-sh-dir blddir (sh/sh (str conda-exe) "build" "."))]
      (C (pl/s*set-values :conda-build/build-cmd-out out
                       :conda-build/build-cmd-err err
                       :conda-build/build-cmd-exit-code exit)
         (if (zero? exit)
           (pl/s*log-info {:message "conda-build successful"})
           (pl/s*log-error {:message (str "Error: conda-build terminated with exit-code " exit)
                         :type :bad-conda-build-exit}))))))
