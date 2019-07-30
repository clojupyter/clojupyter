(ns clojupyter.install.conda.unlink

  ;; The functions in this namespace are used to remove Clojupyter from an end-user machine on which
  ;; it is installed using `conda install`.  Under normal circumstances it is never used by the user
  ;; directly, but is called by the Conda installer as part of the removal procedure.

  ;; Functions whose name begins with 's*' return a single-argument function accepting and returning
  ;; a state map.
  (:require
   [clojure.spec.alpha				:as s]
   [clojure.spec.test.alpha					:refer [instrument]]
   [io.simplect.compose						:refer [def- sdefn sdefn- γ Γ π Π]]
   [me.raynes.fs				:as fs]
   ,,
   [clojupyter.cmdline.api			:as cmdline]
   [clojupyter.install.conda.specs		:as csp]
   [clojupyter.install.filemap			:as fm]
   [clojupyter.install.log			:as log]
   [clojupyter.install.plan					:refer :all]))

(sdefn s*generate-unlink-actions (s/cat :env :conda-unlink/env)
  "Returns a function which, given a state, updates the state with actions to remove (unlink) the
  Conda-install Clojupyter kernel."
  [env]
  (let [{:keys [:conda-link/prefix :conda-unlink/kernel-dir :local/filemap]} env]
    (Γ (s*set-value :conda-unlink/env env)
       (s*log-info {:message (str "Step: Delete " kernel-dir)})
       (if (fm/dir filemap kernel-dir)
         (s*action-append [`fs/delete-dir kernel-dir])
         (s*log-error {:message (str "Kernel directory '" kernel-dir "' not found.")
                       :type :unlink-kerneldir-not-found
                       :filemap filemap, :kernel-dir kernel-dir})))))

(defn s*report-unlink-actions
  "Returns a function which, given a state, updates the state with information about the attempt to
  remove the Conda-installed Clojupyter kernel."
  [kernel-dir result post-filemap]
  (s*bind-state S
    (Γ (cmdline/set-result (assoc result :conda-unlink/post-filemap post-filemap))
       (let [{:keys [:plan/execute-success? :plan/completed?]} result
             log (get-log S)]
         (cond
           execute-success?
           ,, (Γ (cmdline/output (str (->> (fm/names post-filemap)
                                           (filter (π fm/exists post-filemap))
                                           count)
                                      " files found in " kernel-dir "."))
                 (cmdline/output (str "Conda unlink completed successfully."))
                 (cmdline/set-exit-code 0))
           (halted? S)
           ,, (Γ (cmdline/output "Unlinking not performed (halted).")
                 (log/s*report-log log)
                 (cmdline/set-exit-code 1))
           :else
           ,, (Γ (cmdline/outputs ["Conda unlink failed." ""])
                 (log/s*report-log log)
                 (let [files (->> (fm/names post-filemap)
                                  (filter (π fm/exists post-filemap))
                                  (map (Γ (π fm/exists post-filemap) (π str "  - "))))]
                   (s*when (-> files count pos?)
                     (Γ (cmdline/output "" "Files still exist:")
                        (cmdline/outputs files)
                        (cmdline/output ""))))
                 (cmdline/set-exit-code 1)))))))

(sdefn s*conda-unlink (s/cat :env :conda-unlink/env)
  "Returns a function which, given a state, updates the state with actions to remove a Conda-installed
  Clojupyter kernel and then executes those actions."
  [{:keys [skip-execute?]} env]
  (Γ (cmdline/set-header "Conda Unlink")
     (cmdline/set-exit-code 0)
     (s*when-not skip-execute? s*set-do-execute)
     (s*generate-unlink-actions env)
     s*execute s*report
     ;; KH 20190725: We don't want to call s*report-unlink-actions here
     ;;              Reason: We want a updated filemap to verify the unlinking which is
     ;;                      impure and hence is relegated to cmdline.clj
     ))

(instrument `s*generate-unlink-actions)

