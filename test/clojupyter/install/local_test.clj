(ns clojupyter.install.local-test
  (:require [clojupyter.install.filemap :as fm]
            [clojupyter.install.local :as local]
            [clojupyter.install.local-actions :as local!]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.kernel.version-test :refer [g-version]]
            [clojupyter.plan :as pl]
            [clojupyter.test-shared :as ts]
            [clojupyter.test-shared-generators :as shg :refer [<==> ==> R]]
            [clojupyter.tools :as u]
            [clojupyter.tools-actions :as u!]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [io.simplect.compose :refer [C def- p P]]
            [me.raynes.fs :as fs]
            [midje.sweet :refer [=> fact]]))

(use 'clojure.pprint)

(def LSP-DEPEND
  "Ensures dependency due to use of `:local/...` keywords.  Do not delete."
  lsp/DEPEND-DUMMY)

(def- QC-ITERS 500)

;;; ----------------------------------------------------------------------------------------------------
;;; REMOVE INSTALL
;;; ----------------------------------------------------------------------------------------------------

(def g-clojupyter-jar
  (gen/let [path shg/g-path
            typ (gen/frequency [[9 (shg/g-constant "jar")]
                                [1 (shg/g-alphanum 3 3)]])
            nm (gen/frequency [[9 (shg/g-constant "clojupyter-standalone")]
                               [1 (shg/g-alphanum 1 20)]])]
    (R (io/file (str path "/" nm "." typ)))))

(def g-kerneldir-parents
  "Generator producing 2 path names representing the :user and :host kerneldir-parent directories."
  (gen/such-that (p apply not=) (gen/vector shg/g-path 2 2)))

(def g-remove-env
  "Generator producig the environment map needed to generator kernel removal action."
  (gen/let [kerneldir-parents g-kerneldir-parents
            N (gen/choose 1 10)
            dirnames (gen/vector (gen/elements kerneldir-parents) N)
            idents (gen/vector (shg/g-name 3 10) N)]
    (let [kerneldirs (map (fn [dir id] (io/file (str dir "/" id))) dirnames idents)
          jsons (for [dir kerneldirs] (io/file (str dir "/" lsp/KERNEL-JSON)))
          kernelmap (->> (for [[ident json] (map vector idents jsons)]
                           (let [jarfile (-> json .getParent (str "/" lsp/DEFAULT-TARGET-JARNAME))]
                             [json (u/kernel-spec jarfile ident)]))
                         (into {}))
          res {:kerneldir-parents kerneldir-parents
               :kerneldirs (into #{} kerneldirs)
               :idents (into #{} idents)
               :jsons (into #{} jsons)
               :env #:local{:kerneldir-parents kerneldir-parents
                            :kernelmap kernelmap,
                            :filemap (fm/filemap)}}]
      res)))

(def prop--generated-remove-env-correct
  "Properties of correct removal environment."
  (prop/for-all [{:keys [env kerneldirs kerneldir-parents jsons]} g-remove-env]
    (and (s/valid? :local/remove-env env)
         (-> env count pos?)
         (-> kerneldirs count pos?)
         (-> kerneldir-parents count pos?)
         (= jsons (->> env :local/kernelmap keys (into #{})))
         (= kerneldir-parents (:local/kerneldir-parents env))
         (->> (for [dir kerneldirs]
                (some (P u/file-ancestor-of dir) kerneldir-parents))
              (every? true?)))))

(fact "Generated remove-environments are correct"
  (:pass? (tc/quick-check QC-ITERS prop--generated-remove-env-correct))
  => true)

(def prop--remove-only-matching-kerneldirs
  "Properties of correctly generated kernel removal action."
  (prop/for-all [{:keys [kerneldir-parents kerneldirs idents env]} g-remove-env
                 ;; Select some arbitrary strings to try in addition to the kernel idents
                 ;; They are very unlikely to be one of the idents which are generated using shg/g-name
                 extra-idents (gen/set gen/string {:min-elements 10 :max-elements 20})]
    (every? true?
            (for [id (set/union idents extra-idents)]
              (let [matching? (boolean (some (p u/re-find+ id) idents))
                    delete-step? (C first (p = `fs/delete-dir))
                    S ((C pl/s*set-do-execute (local/s*generate-remove-action id env)) {})
                    action-spec (pl/get-action-spec S)
                    doing? (pl/executing? S)
                    ]
                (and
                 ;; 1A) If we match something then we do something
                 ;; 1B) If we do something then we matched something
                 true
                 (<==> matching? doing?)
                 ;; 2) If we do something, it's deleting
                 (==> doing? (->> action-spec (remove delete-step?) count zero?))
                 ;; 3) If we delete something, it's one of the kernel directories
                 (==> doing?
                      (->> action-spec
                           (filter delete-step?)
                           (map (C second (p contains? kerneldirs)))
                           (every? true?)))))))))

(fact "Remove only matching kerneldirs"
  (:pass? (tc/quick-check QC-ITERS prop--generated-remove-env-correct))
  => true)

;;; ----------------------------------------------------------------------------------------------------
;;; LOCAL INSTALL - USER OPTS
;;; ----------------------------------------------------------------------------------------------------

(def g-jarfile
  (gen/fmap #(io/file (str % "/" lsp/DEFAULT-TARGET-JARNAME)) shg/g-path))

(def g-local-install-user-opts
  (gen/let [deletions? gen/boolean
            destdir? gen/boolean
            destdir (shg/g-nilable shg/g-path)
            gen-json? gen/boolean
            loc (gen/elements [:loc/user :loc/host])
            cust? gen/boolean
            jarfiles (gen/set g-jarfile {:min-elements 0, :max-elements 1})
            filemap (shg/g-filemap (set/union jarfiles #{destdir}))]
    (let [res (merge {:local/allow-deletions? deletions?
                      :local/allow-destdir? destdir?
                      :local/filemap filemap
                      :local/generate-kernel-json? gen-json?
                      :local/loc loc
                      :local/source-jarfiles jarfiles
                      :local/target-jarname lsp/DEFAULT-TARGET-JARNAME}
                     (when destdir
                       {:local/destdir destdir}))]
      (if (s/valid? :local/user-opts res)
        (R res)
        (u!/throw-info "g-local-install-user-opts: internal error"
          {:res res, :explain-str (s/explain-str :local/user-opts res)})))))

;;; ----------------------------------------------------------------------------------------------------
;;; LOCAL INSTALL - INSTALL-ENV
;;; ----------------------------------------------------------------------------------------------------

(def g-convert-exe
  (gen/fmap #(io/file (str % "/convert")) shg/g-path))

(def g-install-resources
  (let [res-names local!/DEFAULT-RESOURCE-NAMES]
    (gen/let [resources (gen/vector shg/g-resource (count res-names))]
      (R (into {} (map vector res-names resources))))))

(def g-installed-kernel-info
  (gen/hash-map :kernel/ident (shg/g-name 1 10)
                :kernel/dir shg/g-path
                :kernel/display-name (shg/g-name 1 10)))

(def g-installed-kernels
  (gen/let [kernels (gen/vector g-installed-kernel-info 0 10)]
    (->> kernels (map (juxt :kernel/ident identity)) (into {}))))

(def g-local-install-env
  (gen/let [convert-exe g-convert-exe
            default-ident shg/g-ident
            host-kernel-dir shg/g-path
            installed-kernels g-installed-kernels
            resource-map g-install-resources
            jarfiles (gen/set g-jarfile {:max-elements 10})
            logo-resource (shg/g-constant lsp/LOGO-ASSET)
            user-kernel-dir shg/g-path
            user-homedir shg/g-path
            version-map (shg/g-constant {})
            ,,
            jarfilemap (R (->> (map (P vector :filetype/file) jarfiles)
                               (into {})
                               fm/filemap))
            filemap (shg/g-filemap [host-kernel-dir user-kernel-dir])
            version-map g-version]
    (let [filemap (fm/filemap jarfilemap filemap)
          env {:local/convert-exe convert-exe
               :local/default-ident default-ident
               :local/filemap filemap
               :local/host-kernel-dir host-kernel-dir
               :local/installed-kernels installed-kernels
               :local/resource-map resource-map
               :local/jarfiles jarfiles
               :local/logo-resource logo-resource
               :local/user-homedir user-homedir
               :local/user-kernel-dir user-kernel-dir
               :version/version-map version-map}]
      (if (s/valid? :local/install-env env)
        (R env)
        (u!/throw-info "g-local-install-env: internal error"
          {:env env, :explain-str (s/explain-str :local/install-env env)})))))

;;; ----------------------------------------------------------------------------------------------------
;;; LOCAL INSTALL - INSTALL SPEC
;;; ----------------------------------------------------------------------------------------------------

(def prop--install-spec
  (prop/for-all [opts g-local-install-user-opts
                 env g-local-install-env]
    (let [spec (local/install-spec opts env)
          u-destdir (:local/destdir opts)]
      (and
       (= (:local/allow-deletions? opts) (:local/allow-deletions? spec))
       (= (:local/allow-destdir? opts) (:local/allow-destdir? spec))
       (= (:local/convert-exe env) (:local/convert-exe spec))
       (==> u-destdir (= u-destdir (:local/destdir spec)))
       (==> (not u-destdir)
            (and (==> (= (:local/loc env) :loc/host)
                      (u/file-ancestor-of (:local/host-kernel-dir env) (:local/destdir spec)))
                 (==> (= (:local/loc env) :loc/user)
                      (u/file-ancestor-of (:local/user-kernel-dir env) (:local/destdir spec)))))
       (u/submap? (fm/get-map (:local/filemap opts)) (fm/get-map (:local/filemap spec)))
       (u/submap? (fm/get-map (:local/filemap env)) (fm/get-map (:local/filemap spec)))
       (string? (:local/ident spec))
       (if (:local/ident opts)
         (= (:local/ident opts) (:local/ident spec))
         (= (:local/default-ident env) (:local/ident spec)))
       (= (:local/installed-kernels env) (:local/installed-kernels spec))
       (= (:local/logo-resource env) (:local/logo-resource spec))
       (= (:local/resource-map env) (:local/resource-map spec))
       (<= (-> opts :local/source-jarfiles count) 1)
       (if (-> opts :local/source-jarfiles count pos?)
         (= (:local/source-jarfiles opts) (:local/source-jarfiles spec))
         (= (:local/jarfiles env) (:local/source-jarfiles spec)))
       (s/valid? :version/version-map (:version/version-map spec))))))

(fact "Basic install-spec"
  (:pass? (tc/quick-check QC-ITERS prop--install-spec))
  => true)

;;; ----------------------------------------------------------------------------------------------------
;;; LOCAL INSTALL - GENERATE INSTALL ACTIONS
;;; ----------------------------------------------------------------------------------------------------

(def prop--generate-install-actions
  (prop/for-all [opts g-local-install-user-opts
                 env g-local-install-env]
    (let [spec (local/install-spec opts env)
          res ((C pl/s*set-do-execute
                  (local/s*generate-install-effects spec)) {})
          aspec (pl/get-action-spec res)
          aspec-ops (->> aspec (map first) (into #{}))
          ok!	(pl/executing? res)
          stop! (not ok!)]
      (and (s/valid? :local/user-opts opts)
           (s/valid? :local/install-env env)
           (s/valid? :local/install-spec spec)
           (==> (-> spec :local/source-jarfiles count zero?) stop!)
           (==> (-> spec :local/source-jarfiles count (> 1)) stop!)
           (==> ok! (contains? aspec-ops `local!/copy-resource-to-file!))
           (==> (and ok! (:local/generate-kernel-json? opts))
                (contains? aspec-ops `local!/generate-kernel-json-file!))
           (==> ok! (->> aspec
                         (filter (every-pred (C first (p = `io/copy))
                                             (C (P nth 2) str (p re-find #"jar$"))))
                         count
                         (= 1)))
           (==> (contains? (->> spec :local/installed-kernels keys (into #{})) (:local/ident spec))
                stop!)))))

(fact "Check s*generate-install-effects"
  (:pass? (tc/quick-check QC-ITERS prop--generate-install-actions))
  => true)
