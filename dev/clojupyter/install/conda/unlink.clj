(ns clojupyter.install.conda.unlink
  (:require [clojupyter.cmdline.api :as cmdline]
            [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.install.filemap :as fm]
            [clojupyter.install.log :as log]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.plan
             :as
             pl
             :refer
             [s*bind-state s*log-error s*log-info s*set-value s*when s*when-not]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :refer [instrument]]
            [io.simplect.compose :refer [C p sdefn]]
            [me.raynes.fs :as fs]))

(def DEPEND [csp/DEPEND-DUMMY lsp/DEPEND-DUMMY])

(sdefn s*generate-unlink-actions (s/cat :env :conda-unlink/env)
  "Returns a function which, given a state, updates the state with actions to remove (unlink) the
  Conda-install Clojupyter kernel."
  [env]
  (let [{:keys [:conda-link/prefix :conda-unlink/kernel-dir :local/filemap]} env]
    (C (s*set-value :conda-unlink/env env)
       (s*log-info {:message (str "Step: Delete " kernel-dir)})
       (if (fm/dir filemap kernel-dir)
         (pl/s*action-append [`fs/delete-dir kernel-dir])
         (s*log-error {:message (str "Kernel directory '" kernel-dir "' not found.")
                       :type :unlink-kerneldir-not-found
                       :filemap filemap, :kernel-dir kernel-dir})))))

(defn s*report-unlink-actions
  "Returns a function which, given a state, updates the state with information about the attempt to
  remove the Conda-installed Clojupyter kernel."
  [kernel-dir result post-filemap]
  (s*bind-state S
    (C (cmdline/set-result (assoc result :conda-unlink/post-filemap post-filemap))
       (let [{:keys [:plan/execute-success? :plan/completed?]} result
             log (pl/get-log S)]
         (cond
           execute-success?
           ,, (C (cmdline/output (str (->> (fm/names post-filemap)
                                           (filter (p fm/exists post-filemap))
                                           count)
                                      " files found in " kernel-dir "."))
                 (cmdline/output (str "Conda unlink completed successfully."))
                 (cmdline/set-exit-code 0))
           (pl/halted? S)
           ,, (C (cmdline/output "Unlinking not performed (halted).")
                 (log/s*report-log log)
                 (cmdline/set-exit-code 1))
           :else
           ,, (C (cmdline/outputs ["Conda unlink failed." ""])
                 (log/s*report-log log)
                 (let [files (->> (fm/names post-filemap)
                                  (filter (p fm/exists post-filemap))
                                  (map (C (p fm/exists post-filemap) (p str "  - "))))]
                   (s*when (-> files count pos?)
                     (C (cmdline/output "" "Files still exist:")
                        (cmdline/outputs files)
                        (cmdline/output ""))))
                 (cmdline/set-exit-code 1)))))))

(sdefn s*conda-unlink (s/cat :env :conda-unlink/env)
  "Returns a function which, given a state, updates the state with actions to remove a Conda-installed
  Clojupyter kernel and then executes those actions."
  [{:keys [skip-execute?]} env]
  (C (cmdline/set-header "Conda Unlink")
     (cmdline/set-exit-code 0)
     (s*when-not skip-execute? pl/s*set-do-execute)
     (s*generate-unlink-actions env)
     pl/s*execute pl/s*report
     ;; KH 20190725: We don't want to call s*report-unlink-actions here
     ;;              Reason: We want a updated filemap to verify the unlinking which is
     ;;                      impure and hence is relegated to cmdline.clj
     ))

(instrument `s*generate-unlink-actions)

