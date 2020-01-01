(ns clojupyter.install.conda.unlink-test
  (:require [clojupyter.install.conda.unlink :as unlink]
            [clojupyter.install.conda.conda-specs :as csp]
            [clojupyter.install.filemap :as fm]
            [clojupyter.install.local :as local]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.plan :as pl]
            [clojupyter.test-shared :as ts]
            [clojupyter.test-shared-generators :as shg :refer [==> R]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [io.simplect.compose :refer [C p]]
            [me.raynes.fs :as fs]
            [midje.sweet :refer [=> fact]]))

(use 'clojure.pprint)

(def LSP-DEPEND [csp/DEPEND-DUMMY lsp/DEPEND-DUMMY])

(def QC-ITERS 500)

(def g-unlink-env
  (gen/let [prefix (gen/fmap (C (p str/join "/" ) (p str "/"))
                             (gen/vector (shg/g-alphanum 2 7) 1 4))
            kdir shg/g-path
            filemap (shg/g-filemap [prefix kdir])]
    (R {:conda-link/prefix prefix,
        :conda-unlink/kernel-dir kdir
        :local/filemap filemap})))

(s/def ::unlink-op		#{`fs/delete-dir})
(s/def ::unlink-step		(s/tuple ::unlink-op :local/file))
(s/def ::unlink-spec		(s/coll-of ::unlink-step :kind vector? :min-count 0 :max-count 1))

(def prop--unlink-only-kerneldir
  (prop/for-all [env g-unlink-env]
    (let [{:keys [:conda-unlink/kernel-dir :local/filemap]} env
          res ((C pl/s*set-do-execute (unlink/s*generate-unlink-actions env)) {})
          ok! (pl/executing? res)
          spec (pl/get-action-spec res)]
      (and (s/valid? :conda-unlink/env env)
           (==> ok! (s/valid? ::unlink-spec spec))
           (==> ok! (fm/dir filemap kernel-dir))
           (==> ok! (-> spec count (= 1)))
           (==> ok! (= (-> spec first second) kernel-dir))))))

(fact "Unlink only kerneldir"
  (:pass? (tc/quick-check QC-ITERS prop--unlink-only-kerneldir))
  => true)

