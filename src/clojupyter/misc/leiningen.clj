(ns clojupyter.misc.leiningen
  (:require
   [clojure.string			:as str]
   [clojure.java.io			:as io]
   [clojure.java.shell			:as sh]
   [clojure.tools.cli			:as cli]
   ,,
   [clojupyter.kernel.util		:as u]
   [clojupyter.kernel.version		:as version])
  (:gen-class))

(def ^:private KERNEL-JSON			"kernel.json")

(defn- print-prefix
  [s]
  (str s ": "))

;;; ----------------------------------------------------------------------------------------------------
;;; OS SUPPORT
;;; ----------------------------------------------------------------------------------------------------

(defn- osname [] (-> (System/getProperty "os.name") str/lower-case str/trim))
(defn- os? [idstr] (fn [] (not (neg? (.indexOf (osname) idstr)))))

(def ^:private mac? (os? "mac"))
(def ^:private linux? (os? "linux"))

(defn- supported-os?
  []
  (or (mac?)
      #_(linux?)))

(defn- operating-system []
  (cond
    (mac?)	:MacOS
    (linux?)	:Linux
    true	nil))

(defn- jar-name
  ([] (jar-name (version/version)))
  ([{:keys [version]}]
   (io/file (str "target/" "clojupyter-" version "-standalone.jar"))))

;;; ----------------------------------------------------------------------------------------------------
;;; INSTALL
;;; ----------------------------------------------------------------------------------------------------

(defn- user-homedir
  []
  (or (-> "user.home" System/getProperty io/file)
      (throw (Exception. "'user.home' system property not found."))))

(defn- assert-homedir
  []
  (assert (.exists (user-homedir)) (str "User home directory not found: '" (user-homedir) "'.")))

(defn- install-dir
  []
  (assert-homedir)
  (when-let [relpath (case (operating-system)
                       :MacOS	"Library/Jupyter/kernels"
                       :Linux	".local/share/jupyter/kernels"
                       nil)]
    (io/file (str (user-homedir) "/" relpath))))

(defn- ensure-destdir
  [{:keys [prefix]} ^java.io.File destfile]
  (let [parent (io/file (.getParent destfile))]
    (when-not (.exists parent)
      (println (str prefix "Creating directory " parent "."))
      (io/make-parents destfile))))

(defn- copy-jarfile
  [{:keys [prefix]} jarfile dest-jarfile]
  (when-not (.exists jarfile)
    (throw (ex-info (str "copy-jarfile: " jarfile " not found.") {:jarfile jarfile})))
  (println (str prefix "Copying " jarfile " to " (.getParent dest-jarfile) "."))
  (io/copy jarfile dest-jarfile))

(defn- kernel-spec
  [dest-jar kernel-name-qualifier]
  {:argv ["java" "-jar" (str dest-jar) "{connection_file}"]
   :display_name (str "Clojure (" kernel-name-qualifier ")")
   :language "clojure"})

(defn- write-kernel-spec
  [{:keys [prefix]} destdir dest-jarfile kernel-name-qualifier]
  (assert (instance? java.io.File destdir) (str "Destdir not a file object: " destdir " (" (type destdir) ")."))
  (let [kernel-json-file (->> KERNEL-JSON (str destdir "/") io/file)]
    (println (str prefix "Writing kernel spec to '" kernel-json-file "'."))
     (->> (kernel-spec dest-jarfile kernel-name-qualifier)
          u/json-str
          (spit kernel-json-file))))

;;; ----------------------------------------------------------------------------------------------------
;;; ICONS
;;; ----------------------------------------------------------------------------------------------------

(def ^:private CONVERT-EXE	"convert")
(def ^:private LOGO32-FILENAME	"logo-32x32.png")
(def ^:private LOGO64-FILENAME	"logo-64x64.png")
(def ^:private LOGO64-RESOURCE	(io/file (str "resources/" LOGO64-FILENAME)))
(def ^:private LOGO32-RESOURCE	(io/file (str "resources/" LOGO32-FILENAME)))

(defn- convert-cmdline
  [{:keys [major minor incremental qual-suffix]} destfile]
  (let [ver (str major "." minor "." incremental)]
    [CONVERT-EXE
     (str LOGO64-RESOURCE)
     "-fill" "white"
     "-gravity" "South" "-annotate" "+0+0" ver
     "-gravity" "North" "-annotate" "+0+0" (or qual-suffix "")
     destfile]))

(defn- write-icons
  [{:keys [prefix]} tag-icons? destdir version-map]
  (let [destfile (str destdir "/" LOGO64-FILENAME)]
    (println (str prefix "Copying icons to " destdir "."))
    (io/copy LOGO32-RESOURCE (io/file (str destdir "/" LOGO32-FILENAME)))
    (if tag-icons?
      (let [cmdline (convert-cmdline version-map destfile)
            {:keys [exit stdout err]} (apply sh/sh cmdline)]
        (if (zero? exit)
          :ok
          (throw (Exception. (str "Copying icons failed: " err)))))
      (io/copy LOGO64-RESOURCE (io/file (str destdir "/" LOGO64-FILENAME))))))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL INTERFACE
;;; ----------------------------------------------------------------------------------------------------

(def KERNEL-DIR-REGEX "^[\\.\\w\\d-#@]+$")

(def ^:private INSTALL-OPTIONS
  [[nil "--jupyter-kernel-dir JUPYTER-KERNEL-DIR"
    (str "Relative name of install directory (must match regex " KERNEL-DIR-REGEX ").")
    :default "clojupyter"
    :parse-fn (fn [v] (if (= v ":version") (str "clojupyter-" (:version (version/version))) v))
    :validate [(partial re-find (re-pattern KERNEL-DIR-REGEX))]]
   [nil "--tag-icons"
    "If specified: Add text to icons indicating clojupyter's version."
    :default false
    :parse-fn (constantly true)]])

(defn- parse-install-cmdline
  [opts args]
  (let [{:keys [options errors] :as result} (cli/parse-opts args INSTALL-OPTIONS)]
    (if (some u/truthy? errors) 
      result
      options)))

(defn clojupyter-install
  ""
  [& args]
  (let [prefix (print-prefix "clojupyter-install")
        opts {:prefix prefix}
        {:keys [errors jupyter-kernel-dir tag-icons summary]} (parse-install-cmdline opts args)]
    (if (some u/truthy? errors)
      (do
        (println [:errors errors])
        (println (str prefix "Error parsing command line."))
        (doseq [err (remove nil? errors)]
          (println (str prefix err)) )
        (println (str prefix "Command line summary:"))
        (println summary)
        (System/exit 1))
      (let [destdir (io/file (str (install-dir) "/" jupyter-kernel-dir))
            jarfile (jar-name)
            dest-jarfile (io/file (str destdir "/" (.getName jarfile)))
            {:keys [formatted-version] :as version-map} (version/version)]
        (ensure-destdir opts dest-jarfile)
        (copy-jarfile opts jarfile dest-jarfile)
        (write-kernel-spec opts destdir dest-jarfile formatted-version)
        (write-icons opts tag-icons destdir version-map)
        (println (str prefix "Done (ok)."))
        (System/exit 0)))))

(defn check-os-support
  "Reports whether present operating system is supported by clojupyter.
  Exits with 0 exit code if operating is supported and 1 otherwise.

  Call via lein:

        `bash$ lein check-os-support`"
  []
  (let [supp? (supported-os?)
        pfx (print-prefix "check-os-support")]
    (if-let [os (operating-system)]
      (print (str pfx "Operating system seems to be " (name os) ". "))
      (print (str pfx "Unknown operating system (reported as '" (osname) "'). ")))
    (println (if supp? "Supported." "Not supported."))
    (let [exit (if supp? 0 1)]
      (println (str pfx "Exit (" exit ").") )
      (System/exit exit))))

(defn check-install-dir
  []
  (let [prefix (print-prefix "check-install-dir")
        dir (install-dir)]
    (print (str prefix "Install directory is '" dir "' "))
    (println (if (.exists dir) "(exists)." "(will be created at install)." ))))
