(ns clojupyter.install.local-actions
  ;; This namespace contains the actions needed to install Clojupyter locally on a machine.  It is
  ;; also used by the Conda build which build Clojupyter in a temporary directory and the packs
  ;; up the result into a Conda package.

  ;;Functions whose name begins with 's*' return a single-argument function accepting and
  ;; returning a state map.
  (:require
   [clojure.java.io				:as io]
   [clojure.java.shell				:as sh]
   [clojure.set					:as set]
   [clojure.spec.alpha				:as s]
   [clojure.string				:as str]
   [clojure.walk				:as walk]
   [io.simplect.compose						:refer [call-if def- sdefn sdefn- redefn
                                                                        >>-> >->> π Π γ Γ λ]]
   [me.raynes.fs				:as fs]
   ,,
   [clojupyter.install.filemap			:as fm]
   [clojupyter.install.local-specs		:as lsp]
   [clojupyter.kernel.os			:as os]
   [clojupyter.kernel.version			:as ver]
   [clojupyter.util				:as u]
   [clojupyter.util-actions			:as u!]
   ))

(use 'clojure.pprint)

(def DEFAULT-RESOURCE-NAMES (->> (concat lsp/SCRIPT-ASSETS [lsp/LOGO-ASSET]) (into #{})))
(def resources->resourcemap (Γ (π map (juxt identity io/resource)) (π into {})))

(defmulti  kernels-dir (fn [loc] [loc (os/operating-system)]))
(defmethod kernels-dir [:user :linux] [_] 	(fs/expand-home "~/.local/share/jupyter/kernels"))
(defmethod kernels-dir [:host :linux] [_] 	(io/file "/usr/local/share/jupyter/kernels"))
(defmethod kernels-dir [:user :macos] [_] 	(fs/expand-home "~/Library/Jupyter/kernels"))
(defmethod kernels-dir [:host :macos] [_]	(io/file "/usr/local/share/jupyter/kernels"))
(letfn [(getenv! [nm] (or (System/getenv nm)
                         (throw (Exception. (str "Could not retrieve '" nm "' env variable.")))))]
  (defmethod kernels-dir [:user :windows] [_] 	(io/file (str (getenv! "APPDATA") "/jupyter")))
  (defmethod kernels-dir [:host :windows] [_] 	(io/file (str (getenv! "PROGRAMDATA") "/jupyter"))))
(u!/set-var-private! #'kernels-dir)

(def- find-imagemagick-convert #(u!/find-executable "convert"))

;;; ----------------------------------------------------------------------------------------------------
;;; INTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn all-kernels-dirs*
  "Returns a list of the parent directories of the directories into which Jupyter kernels are
  installed on the current Operating System.."
  []
  (map kernels-dir [:user :host]))

(def- kernel-json-display-name*
  (Γ slurp u/parse-json-str (Π get "display_name")))

(defn- kernel-json-info
  [kernel-json]
  (let [display-name (kernel-json-display-name* kernel-json)]
    {:kernel/ident (u/display-name->ident display-name)
     :kernel/display-name display-name
     :kernel/dir (fs/parent kernel-json)}))

(defn- customize-icon-cmd
  [convert-exe {:keys [north south] :as tags-map} destfile]
  (let [input-file (str destfile), output-file input-file]
    (concat [(str convert-exe) input-file  "-fill" "white"]
            (when north
              ["-gravity" "North" "-annotate" "+0+0" (str north)])
            (when south
              ["-gravity" "South" "-annotate" "+0+0" (str south)])
            [output-file])))

(defn- default-kernel-dir
  [loc ident]
  (io/file (str (kernels-dir loc) "/" ((u/sanitize-string lsp/IDENT-CHAR-REGEX) ident))))

(defn- find-files-re
  [regex]
  (Γ io/file file-seq (π filter (Γ str (π re-find regex))) (π map fs/normalized)))
(def- find-icon-files
  (find-files-re #"logo-64x64\.png$"))
(def- find-standalone-jars
  (find-files-re #"clojupyter.*-standalone\.jar$"))
(def find-kernel-json-files
  (find-files-re #"kernel\.json$"))

(def installed-clojupyter-kernels
  (Γ all-kernels-dirs*
     (π mapcat (Γ fs/expand-home find-kernel-json-files))
     (π map kernel-json-info)
     (π filter (Γ :kernel/display-name str (π re-find #"^Clojure")))))

(defn user-homedir
  []
  (io/file (System/getProperty "user.home")))
;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(redefn all-kernels-dirs all-kernels-dirs*)

(defn copy-resource-to-file!
  "Action to copy the Java/Clojure resource `resource-name` to `file-name`."
  [resource-name file-name]
  (with-open [in (-> resource-name io/resource io/input-stream)
              out (io/output-stream file-name)]
      (io/copy in out)))

(defn customize-icon-file!
  "Action to add text to the top and/or bottom of a icon file.  Requires an Imagemagick 'convert'
  executable to work."
  [convert-exe tags-map iconfile]
  (let [cmdline (customize-icon-cmd convert-exe tags-map iconfile)
        {:keys [exit err] :as res} (apply sh/sh cmdline)]
    (if (zero? exit)
      :ok
      (u!/throw-info (str "Customizing icons failed: " err)
        (merge {:cmdline cmdline, :tags-map tags-map, :iconfile iconfile} res)))))

(defn generate-kernel-json-file!
  "Action to generate the `kernel.json` needed for a Jupyter kernel to work."
  [install-dir kernel-id-string]
  (let [jsonfile (io/file (str install-dir "/" lsp/KERNEL-JSON))
        jarfile (-> (str install-dir "/" lsp/DEFAULT-TARGET-JARNAME) io/file fs/normalized )]
    (->> (u/kernel-spec jarfile kernel-id-string)
         u/json-str
         (spit jsonfile))))

(defn get-install-environment
  "Action returning the data needed to calculate how to install Clojupyter on the local machine."
  []
  (let [convert-exe (find-imagemagick-convert)
        version-map (ver/version)
        host-kdir(kernels-dir :host)
        user-kdir (kernels-dir :user)
        jarfiles (find-standalone-jars fs/*cwd*)
        other-files #{convert-exe host-kdir user-kdir} 
        kernels (installed-clojupyter-kernels)
        kernels-map (->> kernels (map (juxt :kernel/ident identity)) (into {}))
        res (merge {
                    :local/default-ident ((u/sanitize-string lsp/IDENT-CHAR-REGEX) (u!/default-ident version-map))
                    :local/filemap (fm/filemap jarfiles other-files)
                    :local/host-kernel-dir host-kdir
                    :local/installed-kernels kernels-map
                    :local/jarfiles jarfiles
                    :local/logo-resource lsp/LOGO-ASSET
                    :local/resource-map (resources->resourcemap DEFAULT-RESOURCE-NAMES)
                    :local/user-homedir (user-homedir)
                    :local/user-kernel-dir user-kdir
                    :version/version-map version-map
                    }
                   (when convert-exe
                     {:local/convert-exe convert-exe}))]
    (if (s/valid? :local/install-env res)
      res
      (u!/throw-info "Internal error: Bad install-env"
        {:install-env res, :explain-str (s/explain-str :local/install-env res)}))))

(defn remove-kernel-environment
  "Returns the data needed to remove kernels."
  []
  (let [kernel-dir-parents (all-kernels-dirs)
        kernelmap (->> kernel-dir-parents
                       (mapcat (find-files-re #"kernel\.json$"))
                       (into #{})
                       (map (juxt identity (Γ slurp u/parse-json-str walk/keywordize-keys)))
                       (into {}))
        filemap (->> kernel-dir-parents
                     (mapcat (Γ file-seq doall))
                     fm/filemap)
        res {:local/kerneldir-parents kernel-dir-parents
             :local/kernelmap kernelmap
             :local/filemap filemap}]
    (if (s/valid? :local/remove-env res)
      res
      (u!/throw-info "remove-kernel-environment: internal error" 
        {:res res, :explain-str (s/explain-str :local/remove-env res)}))))
