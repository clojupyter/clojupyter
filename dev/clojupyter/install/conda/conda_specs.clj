(ns clojupyter.install.conda.conda-specs
  (:require [clojupyter.install.local-specs :as lsp]
            [clojure.spec.alpha :as s]
            [io.simplect.compose :refer [def- p]]))

(def DEPEND-DUMMY
  "Namespaces which depend on keyword-based definitions in this name refer to this value."
  nil)

(def DEPEND
  "Ensures dependency due to use of `:local/...` keywords.  Do not delete."
  [lsp/DEPEND-DUMMY])

;;; ----------------------------------------------------------------------------------------------------
;;; CONDA CONFIG (YAML)
;;; ----------------------------------------------------------------------------------------------------

(s/def ::natint					(s/and int? (complement neg?)))
(s/def ::reqs					(s/coll-of string? :kind vector?))

(s/def :conda-config-about/description		string?)
(s/def :conda-config-about/home			string?)
(s/def :conda-config-about/license		string?)
(s/def :conda-config-build/number		::natint)
(s/def :conda-config-package/name		string?)
(s/def :conda-config-package/version		string?)
(s/def :conda-config-requirements/build		::reqs)
(s/def :conda-config-requirements/run		::reqs)
(s/def :conda-config-source/folder		string?)

(s/def :conda-config/about			(s/keys :req [:conda-config-about/description
                                                              :conda-config-about/home
                                                              :conda-config-about/license]))
(s/def :conda-config/build			(s/keys :req [:conda-config-build/number]))
(s/def :conda-config/package			(s/keys :req [:conda-config-package/name
                                                              :conda-config-package/version]))
(s/def :conda-config/requirements		(s/keys :req [:conda-config-requirements/build
                                                              :conda-config-requirements/run]))
(s/def :conda-config/source			(s/keys :req [:conda-config-source/folder]))
(s/def :conda/config				(s/keys :req [:conda-config/about
                                                              :conda-config/build
                                                              :conda-config/package
                                                              :conda-config/requirements
                                                              :conda-config/source]))

;;; ----------------------------------------------------------------------------------------------------
;;; ENV
;;; ----------------------------------------------------------------------------------------------------

(s/def :conda-build-env/conda-exe		:local/file)
(s/def :conda-build-env/filemap			:local/filemap)
(s/def :conda-build-env/kernel-dir		(s/and string? (complement (p re-find #"/"))))
(s/def :conda-build-env/resource-copyspec	(s/map-of string? string?))

(s/def :conda-build/env				(s/keys :req [:conda-build-env/conda-exe
                                                              :conda-build-env/filemap
                                                              :conda-build-env/kernel-dir
                                                              :conda-build-env/resource-copyspec]))

(defn- build-asset
  [n]
  (str "clojupyter/assets/conda-build/" n))

(def- BUILD-SH	"build.sh")
(def- POST-SH	"post-link.sh")
(def- PRE-SH	"pre-unlink.sh")
(def- BLD-BAT	"bld.bat")
(def- POST-BAT	"post-link.bat")
(def- PRE-BAT	"pre-unlink.bat")

(def DEFAULT-BUILD-ENV
  {:conda-build-env/kernel-dir			"install-items"
   :conda-build-env/resource-copyspec		{(build-asset BUILD-SH) BUILD-SH
                                                 (build-asset POST-SH) POST-SH
                                                 (build-asset PRE-SH) PRE-SH
                                                 (build-asset BLD-BAT) BLD-BAT
                                                 (build-asset POST-BAT) POST-BAT
                                                 (build-asset PRE-BAT) PRE-BAT}})

;;; ----------------------------------------------------------------------------------------------------
;;; PARAMS
;;; ----------------------------------------------------------------------------------------------------

(s/def :conda-build-params/buildnum		::natint)

(s/def :conda-build/params			(s/keys :req [:conda-build-params/buildnum
                                                              :conda-build-params/filemap
                                                              :local/ident
                                                              :local/source-jarfiles]))


;;; ----------------------------------------------------------------------------------------------------
;;; CONDA LINK ENVIRONMENT
;;; ----------------------------------------------------------------------------------------------------

(s/def :conda-link/destdir			:local/file)
(s/def :conda-link/ident			:local/ident)
(s/def :conda-link/items			(s/coll-of :local/file :kind set?))
(s/def :conda-link/item-filemap			:local/filemap)
(s/def :conda-link/prefix			string?)

(s/def :conda-link/env				(s/keys :req [:conda-link/destdir
                                                              :conda-link/ident
                                                              :conda-link/items
                                                              :conda-link/item-filemap
                                                              :conda-link/prefix]))

;;; ----------------------------------------------------------------------------------------------------
;;; CONDA UNLINK
;;; ----------------------------------------------------------------------------------------------------

(s/def :conda-unlink/kernel-dir		:local/file)
(s/def :conda-unlink/env		(s/keys :req [:conda-link/prefix
                                                      :conda-unlink/kernel-dir
                                                      :local/filemap]))
