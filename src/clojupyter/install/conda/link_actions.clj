(ns clojupyter.install.conda.link-actions

  ;; The functions in this namespace are used to install Clojupyter using `conda install` on an
  ;; end-user machine.  Under normal circumstances it is never used by the user directly, but is
  ;; called by the Conda installer as part of the installation procedure.

  ;; Functions whose name begins with 's*' return a single-argument function accepting and returning
  ;; a state map.
  (:require
   [clojure.java.io				:as io]
   [clojure.set					:as set]
   [clojure.spec.alpha				:as s]
   [io.simplect.compose						:refer [def- γ Γ π Π λ]]
   [me.raynes.fs				:as fs]
   ,,
   [clojupyter.install.conda.env		:as env]
   [clojupyter.install.conda.specs		:as csp]
   [clojupyter.install.filemap			:as fm]
   [clojupyter.install.local-specs		:as lsp]
   [clojupyter.kernel.version			:as ver]
   [clojupyter.util-actions			:as u!])
  (:gen-class))

(def- classpath-urls
  (Γ #(java.lang.ClassLoader/getSystemClassLoader)
     #(.getURLs %)
     vec))

(def- classpath-clojupyter-jarfiles
  (Γ classpath-urls
     (π filter (Γ str (π re-find lsp/CONDA-JARNAME-RE)))
     vec))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn conda-clojupyter-kernel-dir
  "Action returning the directory to be used as kernel directory for the Conda-installed Clojupyter
  kernel."
  [prefix]
  (-> prefix
      (str "/share/jupyter/kernels/conda-clojupyter")
      io/file))

(defn conda-ensure-dir!
  "Action to ensure existence of `dir`."
  [dir]
  (let [dir (io/file dir)]
    (fs/mkdirs dir)
    (when-not (and (fs/directory? dir) (fs/writeable? dir))
      (throw (Exception. (str "Failed to create clojupyter conda install dir: " (or dir "nil") "."))))))

(defn conda-link-environment
  "Action return the data about the install environment needed to calculate how to Conda-install
  Clojupyter."
  ([] (conda-link-environment {}))
  ([{:keys [jarfile prefix]}]
   (let [ident (str "clojupyter=" (ver/version-string))
         prefix (or prefix (env/PREFIX))
         jarfiles (classpath-clojupyter-jarfiles)
         jarfile (or jarfile (when (-> jarfiles count (= 1))
                               (-> jarfiles first io/file)))
         items (if jarfile
                 (->> jarfile fs/parent file-seq (filter fs/file?)
                      (filter (Γ str (π re-find #"\.(png|jar)$")))
                      (into #{}))
                 #{})
         destdir (conda-clojupyter-kernel-dir prefix)
         env {:conda-link/destdir destdir
              :conda-link/ident ident
              :conda-link/items items
              :conda-link/item-filemap (fm/filemap items destdir)
              :conda-link/prefix prefix}]
     (if (s/valid? :conda-link/env env)
       env
       (u!/throw-info "conda-link-environment: internal error"
         {:env env, :explain-str (s/explain-str :conda-link/env env)})))))

(defn conda-verify-install
  "Action to check if the Clojupyter kernel appears to have been installed correctly."
  [destdir]
  (let [files (->> destdir
                   file-seq
                   (filter fs/file?)
                   (map #(.getName %))
                   (into #{}))]
    (set/subset? #{lsp/KERNEL-JSON
                   (->> lsp/LOGO-ASSET io/file .getName)
                   lsp/DEFAULT-TARGET-JARNAME}
                 files)))
