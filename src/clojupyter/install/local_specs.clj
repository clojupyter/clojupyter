(ns clojupyter.install.local-specs
  (:require
   [clojure.spec.alpha			:as s]
   [clojure.string			:as str]
   [io.simplect.compose					:refer [def- γ Γ π Π]]
   ,,
   [clojupyter.install.filemap		:as fm]))

(def IDENT-CHAR-REGEX-STR	"[\\w\\d-_\\.=]")
(def IDENT-CHAR-REGEX		(re-pattern IDENT-CHAR-REGEX-STR))
(def IDENT-REGEX		(re-pattern (str "^" IDENT-CHAR-REGEX-STR "+$")))
(def DEFAULT-TARGET-JARNAME	"clojupyter-standalone.jar")
(def KERNEL-JSON		"kernel.json")
(def CONDA-JARNAME-RE		(re-pattern (str (str/replace DEFAULT-TARGET-JARNAME "." "\\.") "$")))
(def LOGO-ASSET			"clojupyter/assets/logo-64x64.png")
(def SCRIPT-ASSETS		["clojupyter/assets/conda-build/build.sh"
                                 "clojupyter/assets/conda-build/post-link.sh"
                                 "clojupyter/assets/conda-build/pre-unlink.sh"
                                 "clojupyter/assets/conda-build/bld.bat"
                                 "clojupyter/assets/conda-build/post-link.bat"
                                 "clojupyter/assets/conda-build/pre-unlink.bat"
                                 ])

(s/def :local/file				(π instance? java.io.File))
(s/def :local/filetype				(s/nilable #{:filetype/file :filetype/directory}))
(s/def :local/resource				(s/nilable (π instance? java.net.URL)))

(s/def :local/allow-deletions?			boolean?)
(s/def :local/allow-destdir?			boolean?)
(s/def :local/convert-exe			:local/file)
(s/def :local/destdir				:local/file)
(s/def :local/file-copyspec			(s/map-of :local/file string?))		;; file to name relative to destdir
(s/def :local/filemap				fm/filemap?)
(s/def :local/host-kernel-dir			:local/file)
(s/def :local/icon-bot				string?)
(s/def :local/icon-top				string?)
(s/def :local/ident				(s/and string? (π re-find IDENT-REGEX)))
(s/def :local/default-ident			:local/ident)
(s/def :local/installed-kernel-info		(s/keys :req [:kernel/ident :kernel/display-name :kernel/ident]))
(s/def :local/installed-kernels			(s/map-of string? :local/installed-kernel-info))
(s/def :local/jarfiles				(s/nilable (s/coll-of :local/file)))
(s/def :local/loc				#{:loc/user :loc/host})
(s/def :local/logo-resource			string?)
(s/def :local/resource-copyspec			(s/map-of string? string?))	;; resource name to name relative to destdir
(s/def :local/resource-map			(s/map-of string? (s/nilable :local/resource)))
(s/def :local/customize-icons?			boolean?)
(s/def :local/generate-kernel-json?		boolean?)
(s/def :local/source-jarfiles			(s/coll-of :local/file))
(s/def :local/target-jarname			string?)
(s/def :local/user-homedir			:local/file)
(s/def :local/user-kernel-dir			:local/file)

(s/def :local/install-env			(s/keys :req [
                                                              :local/default-ident
                                                              :local/filemap
                                                              :local/host-kernel-dir
                                                              :local/installed-kernels
                                                              :local/resource-map
                                                              :local/jarfiles
                                                              :local/logo-resource
                                                              :local/user-homedir
                                                              :local/user-kernel-dir
                                                              :version/version-map
                                                              ]
                                                        :opt [:local/convert-exe]))
(s/def :local/user-opts				(s/keys :req [
                                                              :local/allow-deletions?
                                                              :local/allow-destdir?
                                                              :local/customize-icons?
                                                              :local/filemap
                                                              :local/generate-kernel-json?
                                                              :local/icon-bot
                                                              :local/icon-top
                                                              :local/loc
                                                              :local/source-jarfiles
                                                              :local/target-jarname
                                                              ]
                                                        :opt [
                                                              :local/destdir
                                                              ]))

(s/def :local/install-spec			(s/keys :req [
                                                              :local/allow-deletions?
                                                              :local/allow-destdir?
                                                              :local/customize-icons?
                                                              :local/destdir
                                                              :local/filemap
                                                              :local/file-copyspec
                                                              :local/icon-bot
                                                              :local/icon-top
                                                              :local/ident
                                                              :local/installed-kernels
                                                              :local/generate-kernel-json?
                                                              :local/logo-resource
                                                              :local/resource-copyspec
                                                              :local/resource-map
                                                              :version/version-map
                                                              ]
                                                        :opt [:local/convert-exe]))

(def DEFAULT-USER-OPTS
  {:local/allow-deletions?		false
   :local/allow-destdir?		false
   :local/customize-icons?		false
   :local/icon-bot			""
   :local/icon-top			""
   :local/filemap			(fm/filemap)
   :local/loc				:loc/user
   :local/generate-kernel-json?		true
   :local/source-jarfiles		#{}
   :local/target-jarname		DEFAULT-TARGET-JARNAME})

(s/def :kernel-json/argv		(s/coll-of string? :kind vector?))
(s/def :kernel-json/display_name	string?)
(s/def :kernel-json/language		string?)
(s/def :kernel-json/info		(s/keys :req-unq [:kernel-json/argv
                                                          :kernel-json/display_name
                                                          :kernel-json/language]))

(s/def :local/kerneldir-parents		(s/coll-of :local/file))
(s/def :local/kernelmap			(s/map-of :local/file :kernel-json/info))
(s/def :local/remove-env		(s/keys :req [:local/kernelmap
                                                      :local/kerneldir-parents
                                                      :local/filemap]))
