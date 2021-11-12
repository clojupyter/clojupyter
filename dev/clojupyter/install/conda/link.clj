(ns clojupyter.install.conda.link
  (:require [clojupyter.cmdline.api :as cmdline]
            [clojupyter.install.conda.link-actions :as link!]
            [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.install.filemap :as fm]
            [clojupyter.install.local-actions :as local!]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.install.log :as log]
            [clojupyter.plan :as pl]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [io.simplect.compose :refer [C p]]))

(def DEPEND [csp/DEPEND-DUMMY])

(defn- s*create-destdir
  "Returns a function which, given a state, updates the state with actions to create the Conda
  Clojupyter kernel directory."
  [item-filemap destdir]
  (case (fm/exists item-filemap destdir)
    :filetype/file	
    ,, (pl/s*log-error {:message (str "Destination directory is a file: " destdir)
                     :type :destdir-is-a-file})
    :filetype/directory
    ,, (pl/s*log-warn {:message (str "Destination directory already exists: " destdir)
                    :type :destdir-exists})
    nil
    ,, (pl/s*action-append [`link!/conda-ensure-dir! destdir])
    (pl/s*log-error {:message "s*generate-link-actions: internal error"
                  :type :internal-error})))

(defn- s*copy-items
  "Returns a function which, given a state, updates the state with actions to copy files into the
  kernel directory."
  [items item-filemap destdir]
  (apply C (for [item items]
             (if (fm/file item-filemap item)
               (let [destfile (->> ^java.io.File item .getName (str destdir "/") io/file)]
                 (pl/s*action-append [`io/copy item destfile]))
               (pl/s*log-error {:message (str "Install item not found:" item)
                             :type :install-item-not-found
                             :item item, :item-filemap item-filemap})))))

(defn- s*generate-link-actions
  "Returns a function which, given a state, updates the state with actions to install Clojupyter."
  ([] {s*generate-link-actions {}})
  ([env-opts install-env]
   (let [#:conda-link{:keys [destdir item-filemap items ident]} install-env]
     (pl/s*action-append [`local!/generate-kernel-json-file! destdir ident]))))

;;; ----------------------------------------------------------------------------------------------------
;;; USED FROM CMDLINE
;;; ----------------------------------------------------------------------------------------------------

(defn s*report-link
  "Returns a function which, given a state, uses the cmdline api to update the state with information
  about the attempt to install Clojupyter under Conda.  NOTE: This function is called by the conda
  install procedure, in almost no cases will it be called by a user directly."
  [destdir destdir-filemap]
  (pl/s*bind-state S
    (let [log (pl/get-log S)
          filenames (->> (fm/names destdir-filemap)
                         (map (p fm/file destdir-filemap))
                         (remove nil?)
                         (map #(.getName ^java.io.File %))
                         (into #{}))
          expected #{"kernel.json"
                     (->> lsp/LOGO-ASSET io/file .getName)
                     lsp/DEFAULT-TARGET-JARNAME}
          ok? (set/subset? expected filenames)
          missing (set/difference expected filenames)
          fmt (C (p map (p str  "  - ")) sort)]
      (C (if ok?
           (C (cmdline/output (str "Successfully installed Clojupyter into " destdir "."))
              (cmdline/set-result {:destdir destdir})
              (cmdline/set-exit-code 0))
           (C (cmdline/output (str "Clojupyter installation into " destdir " failed."))
              (log/s*report-log log)
              (cmdline/output "")
              (if (-> filenames count pos?)
                (C (cmdline/outputs [(str "Found the following files in " destdir ":")])
                   (cmdline/outputs (fmt filenames)))
                (cmdline/outputs ["" "No files found in installation directory."]))
              (cmdline/outputs ["" "Expected but not found:" ""])
              (cmdline/outputs (fmt missing))
              (cmdline/output "")
              (cmdline/set-result (merge S {:conda-link/found filenames,
                                            :conda-link/expected expected,
                                            :conda-link/missing missing}))
              (cmdline/set-exit-code 1)))))))

(defn s*conda-link
  "Returns a function which, given a state, calculates the actions needed to install Clojupyter under
  Conda, and executes those actions.  NOTE: This function is called by the conda install procedure,
  in almost no cases will it be called by a user directly."
  [{:keys [skip-execute?] :as opts} install-env]
  (let [#:conda-install{:keys [destdir]} install-env]
    (C (pl/s*log-debug {:conda-link/env-opts opts
                     :conda-link/install-env install-env})
       (pl/s*when-not skip-execute?
         pl/s*set-do-execute)
       (s*generate-link-actions opts install-env)
       pl/s*execute)))
