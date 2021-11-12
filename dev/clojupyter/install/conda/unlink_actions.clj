(ns clojupyter.install.conda.unlink-actions
  "The functions in this namespace are used to remove Clojupyter from an end-user machine on which
  it is installed using `conda install`.  Under normal circumstances it is never used by the user
  directly, but is called by the Conda installer as part of the removal procedure.

  Functions whose name begins with 's*' return a single-argument function accepting and returning
  a state map."
  (:require [clojupyter.install.conda.env :as env]
            [clojupyter.install.conda.link-actions :as link!]
            [clojupyter.install.filemap :as fm]
            [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.tools-actions :as u!]
            [clojure.spec.alpha :as s]))

(def LSP-DEPEND [csp/DEPEND-DUMMY lsp/DEPEND-DUMMY])

(use 'clojure.pprint)

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn get-unlink-environment
  "Action returning the data about the environment needed to remove the Conda-installed Clojupyter
  kernel."
  ([] (get-unlink-environment (env/PREFIX)))
  ([prefix]
   (let [prefix (or prefix (env/PREFIX))
         kernel-dir (link!/conda-clojupyter-kernel-dir prefix)
         env {:conda-link/prefix prefix
              :conda-unlink/kernel-dir kernel-dir
              :local/filemap (fm/filemap prefix kernel-dir)}]
     (if (s/valid? :conda-unlink/env env)
       env
       (u!/throw-info "get-unlink-environment: internal error"
         {:prefix prefix, :env env,
          :explain-str (s/explain-str :conda-unlink/env env)})))))

