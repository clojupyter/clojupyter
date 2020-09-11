(ns clojupyter.install.local-actions
  (:require [clojupyter.install.filemap :as fm]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.kernel.os :as os]
            [clojupyter.kernel.version :as ver]
            [clojupyter.tools :as u]
            [clojupyter.tools-actions :as u!]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [io.simplect.compose :refer [C def- p P redefn]]
            [me.raynes.fs :as fs]))

(use 'clojure.pprint)

(def LSP-DEPEND
  "Ensures dependency due to use of `:local/...` keywords.  Do not delete."
  lsp/DEPEND-DUMMY)

(def DEFAULT-RESOURCE-NAMES (->> (concat lsp/SCRIPT-ASSETS [lsp/LOGO-ASSET]) (into #{})))
(def resources->resourcemap (C (p map (juxt identity io/resource)) (p into {})))

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
  (C slurp u/parse-json-str (P get "display_name")))

(defn- kernel-json-info
  [kernel-json]
  (let [display-name (kernel-json-display-name* kernel-json)]
    {:kernel/ident (u/display-name->ident display-name)
     :kernel/display-name display-name
     :kernel/dir (fs/parent kernel-json)}))

(defn- default-kernel-dir
  [loc ident]
  (io/file (str (kernels-dir loc) "/" ((u/sanitize-string lsp/IDENT-CHAR-REGEX) ident))))

(defn- find-files-re
  [regex]
  (C io/file file-seq (p filter (C str (p re-find regex))) (p map fs/normalized)))
(def- find-standalone-jars
  (find-files-re #"clojupyter.*-standalone\.jar$"))
(def find-kernel-json-files
  (find-files-re #"kernel\.json$"))

(def installed-clojupyter-kernels
  (C all-kernels-dirs*
     (p mapcat (C fs/expand-home find-kernel-json-files))
     (p map kernel-json-info)
     (p filter (C :kernel/display-name str (p re-find #"^Clojure")))))

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
                       (map (juxt identity (C slurp u/parse-json-str walk/keywordize-keys)))
                       (into {}))
        filemap (->> kernel-dir-parents
                     (mapcat (C file-seq doall))
                     fm/filemap)
        res {:local/kerneldir-parents kernel-dir-parents
             :local/kernelmap kernelmap
             :local/filemap filemap}]
    (if (s/valid? :local/remove-env res)
      res
      (u!/throw-info "remove-kernel-environment: internal error"
        {:res res, :explain-str (s/explain-str :local/remove-env res)}))))
