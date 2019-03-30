(ns clojupyter.misc.leiningen
  (:require
   [clojure.edn				:as edn]
   [clojure.string			:as str]
   [clojure.java.io			:as io]
   [clojure.java.shell			:as sh]
   [java-time				:as jtm] 
   ,,
   [clojupyter.kernel.util		:as u]
   [clojupyter.kernel.version		:as version])
  (:gen-class))

;;; ----------------------------------------------------------------------------------------------------
;;; BUILD CLOJUPYTER EXECUTABLE
;;; ----------------------------------------------------------------------------------------------------

(def ^:private INSTALL-DIR-RELATIVE		"clojure")
(def ^:private VERSION-RESOURCE			"version.edn")
(def ^:private JAR-RE-PATTERN			 #"/clojupyter.*standalone.*\.jar$")
(def ^:private CLOJUPYTER-TEMPLATE-RESOURCE	"clojupyter.template")
(def ^:private KERNEL-JSON			"kernel.json")

(defn- print-prefix
  [s]
  (str s ": "))

(defn- target-path
  []
  "./target")

(defn- standalone-jarfile
  []
  (let [fs (->> (target-path)
                io/file
                file-seq
                (filter (u/rcomp str (partial re-find JAR-RE-PATTERN))))]
    (case (count fs)
      0		(throw (Exception. "Uberjar not found in ./target"))
      1		(first fs)
      ,,	(throw (ex-info (str "Multiple standalone jar files found in " (target-path) ".")
                         {:files fs})))))

(defn- write-binary
  [outfile]
  (with-open [outstream (-> outfile io/output-stream)
              template-instream (if-let [r (io/resource CLOJUPYTER-TEMPLATE-RESOURCE)]
                                  (io/input-stream r)
                                  (throw (ex-info (str "clojupyter template resource not found")
                                           {:missing-resource-name CLOJUPYTER-TEMPLATE-RESOURCE})))]
    (io/copy template-instream outstream)
    (io/copy (io/input-stream (standalone-jarfile)) outstream)))

(defn- make-executable-for-everybody
  [f]
  (.setExecutable f true false))

(defn- write-clojupyter-exe
  [{:keys [prefix]} outfile]
  (println (str prefix "Writing clojupyter executable to '" (str outfile) "'."))
  (write-binary outfile)
  (make-executable-for-everybody outfile))

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

(defn- exe-name
  []
  (case (operating-system)
    :MacOS	"clojupyter"
    :Linux	"clojupyter"
    nil))

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

(defn- kernel-installdir
  [rel]
  (str rel "/" INSTALL-DIR-RELATIVE))

(defn- install-dir
  []
  (assert-homedir)
  (when-let [relpath (case (operating-system)
                       :MacOS	(kernel-installdir "Library/Jupyter/kernels")
                       :Linux	(kernel-installdir ".local/share/jupyter/kernels")
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
    (ex-info (str "copy-jarfile: " jarfile " not found.") {:jarfile jarfile}))
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
(def ^:private LOGO64-RESOURCE	(let [nm LOGO64-FILENAME]
                                  (or #_(io/resource nm)
                                      (io/file "resources/" nm))))
(def ^:private LOGO32-RESOURCE	(let [nm LOGO32-FILENAME]
                                  (or #_(io/resource nm)
                                      (io/file "resources/" nm))))

(defn- convert-cmdline
  [{:keys [major minor incremental qual-suffix]} destfile]
  (let [ver (str major "." minor "." incremental)]
    [CONVERT-EXE
     (str LOGO64-RESOURCE)
     "-fill" "white"
     "-gravity" "South" "-annotate" "+0+0" ver
     "-gravity" "North" "-annotate" "+0+0" qual-suffix
     destfile]))

(defn- write-icons
  [{:keys [prefix]} destdir version-map]
  (let [destfile (str destdir "/" LOGO64-FILENAME)]
    (println (str prefix "Copying icons to " destdir "."))
    (io/copy LOGO32-RESOURCE (io/file (str destdir "/" LOGO32-FILENAME)))
    (let [cmdline (convert-cmdline version-map destfile)
          {:keys [exit stdout err]} (apply sh/sh cmdline)]
      (if (zero? exit)
        :ok
        (ex-info (str "Copying icons failed: " err)
          {:exit-code exit, :stdout stdout, :err err, :cmdline cmdline})))))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL INTERFACE
;;; ----------------------------------------------------------------------------------------------------

(defn clojupyter-install
  ""
  []
  (let [prefix (print-prefix "clojupyter-install")
        opts {:prefix prefix}
        destdir (install-dir)
        jarfile (jar-name)
        dest-jarfile (io/file (str destdir "/" (.getName jarfile)))
        {:keys [formatted-version] :as version-map} (version/version)]
    (ensure-destdir opts dest-jarfile)
    (copy-jarfile opts jarfile dest-jarfile)
    (write-kernel-spec opts destdir dest-jarfile formatted-version)
    (write-icons opts destdir version-map)
    (println (str prefix "Done (ok)."))
    (println)
    (System/exit 0)))

(defn check-os-support
  "Reports whether present operating system is supported by clojupyter.
  Exits with 0 exit code if operating is supported and 1 otherwise.

  Call via lein:

        `bash$ lein check-os-support`"
  [& args]
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
