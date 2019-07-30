(ns clojupyter.util-actions
  (:require
   [clojure.java.io				:as io]
   [clojure.java.shell				:as sh]
   [clojure.string				:as str]
   [clojure.walk				:as walk]
   [io.simplect.compose						:refer [call-if def- redefn γ Γ π Π λ μ ρ]]
   [java-time					:as jtm]
   [me.raynes.fs				:as fs]
   [slingshot.slingshot						:refer [throw+ try+]]
   ,,
   [clojupyter.kernel.os			:as os]
   [clojupyter.kernel.version			:as ver]
   [clojupyter.util				:as u])
  (:import [java.time.format DateTimeFormatter]))

(def ^:dynamic *actually-exit?* true)

(defn exiting-on-completion*
  ;; needs to be external due inclusion in macro expansion
  [thunk]
  (try+ (thunk)
        (catch [::terminate true] {:keys [::exit-code] :as obj}
          (when *actually-exit?*
            (System/exit exit-code)))))

(def- homedir-as-tilde*
  (Π str/replace (System/getProperty "user.home") "~"))

(defn- set-indent-style!*
  [var style]
  (alter-meta! var (Π assoc :style/indent style)))

(defn- throw-info*
  ([msg] (throw-info* msg {}))
  ([msg m]
   (throw (ex-info msg (assoc m :msg msg)))))

(def- tildeize-filename*
  (call-if (π instance? java.io.File)
           (Γ fs/normalized str homedir-as-tilde*)))

(defn- uuid*
  "Returns a random UUID as a string."
  []
  (str (java.util.UUID/randomUUID)))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn assert-singleton-collection!
  [v]
  (if (and (coll? v) (-> v count (= 1)))
    v
    (throw (Exception. (str "Not a singleton collection:" v)))))

(defn assoc-meta!
  [k v var]
  (alter-meta! var #(assoc % k v)))

(defn create-temp-diretory!
  "Creates a new readable/writable directory and returns its name as a `java.io.File`.  Throws an
  exception if the temp directory could not be created."
  []
  (letfn [(sys-tmpdir [] (io/file (System/getProperty "java.io.tmpdir")))]
    (if-let [sysdir (sys-tmpdir)]
      (let [tmpdir (io/file (str sysdir "/clojure-tmp-" (uuid*)))]
        (if (.mkdir tmpdir)
          tmpdir
          (throw-info* (str "Failed to create temp dir: " tmpdir)
            {:sysdir sysdir, :tmpdir tmpdir})))
      (throw (Exception. "Failed to get location for temp files (java.io.tmpdir).")))))

(defn delete-files-recursively!
  [f]
  (letfn [(delete-f [file]
            (when (.isDirectory file)
              (doseq [child-file (.listFiles file)]
                (delete-f child-file)))
            (io/delete-file file))]
    (when f
      (if (string? f)
        (delete-files-recursively! (io/file f))
        (delete-f f)))))

(defn default-ident 
  ([] (default-ident (ver/version)))
  ([ver]
   (str "clojupyter-" (ver/version-string-long ver))))

(defmacro  exiting-on-completion
  [& body]
  `(exiting-on-completion* (fn [] ~@body)))

(def files-as-strings
  (π walk/postwalk (Γ (call-if (π instance? java.net.URL) str)
                      tildeize-filename*)))

(defn file-filetype
  [f]
  (cond
    (fs/file? f)		:filetype/file
    (fs/directory? f)		:filetype/directory
    :else			nil))

(defmulti  find-executable (fn [_] (os/operating-system)))
(letfn [(find-exe [exe]
          (let [{:keys [out err exit]} (sh/sh "/usr/bin/which" exe)]
            (when (zero? exit)
              (-> out str/trim io/file)))) ]
  (defmethod find-executable :macos [exe]
    (find-exe exe))
  (defmethod find-executable :linux [exe]
    (find-exe exe)))
(defmethod find-executable :windows [exe]
  (let [{:keys [out err exit] :as search-result} (sh/sh "where" exe)]
    (when (zero? exit)
      (-> out str/trim str/split-lines first io/file))))
(defmethod find-executable :default [_]
  (throw (Exception. (str "find-executable: Not supported on " (str/upper-case (os/osname))))))

(redefn homedir-as-tilde homedir-as-tilde*)

(defn java-util-data-now
  []
  (new java.util.Date))

(defn merge-docmeta
  "Add the values for keys `:doc` and `:arglists` from `refvar` to the
  meta of `var`."
  [var refvar]
  (alter-meta! var #(merge % (select-keys (meta refvar) [:doc :arglists]))))

(def normalized-file
  (call-if (complement nil?) (Γ io/file fs/normalized)))

(defn now []
  (->> (.withNano (java.time.ZonedDateTime/now) 0)
       (jtm/format DateTimeFormatter/ISO_OFFSET_DATE_TIME)))

(defn set-defn-indent!
  [& vars]
  (doseq [var vars]
    (set-indent-style!* var :defn)))

(redefn set-indent-style! set-indent-style!*)

(defn set-var-private!
  [var]
  (alter-meta! var (Π assoc :private true)))

(defn set-var-indent!
  [indent-style var]
  (alter-meta! var #(assoc % :style/indent indent-style)))

(defn side-effecting!
  "Returns argument after applying presumably side-effectful `f` to it."
  [f]
  (fn [S] (f) S))

(redefn ^{:style/indent :defn} throw-info throw-info*)

(redefn tildeize-filename tildeize-filename*)

(redefn uuid uuid*)

(defmacro without-exiting
  [& body]
  `(binding [*actually-exit?* false]
     ~@body))

(defmacro with-debug-logging
  [[& args] & forms]
  `(let [uuid# (str "#" (subs (uuid) 0 8))]
     (log/debug uuid# "START" ~@args)
     (let [res# (do ~@forms)]
       (log/debug uuid# "END"  )
       res#)))

(defmacro with-temp-directory!
  "Evaluates `body` with `dir` bound to a newly created, readable and writable directory.  Upon exit
  all files and directories in the temp directory are deleted unless `keep-files?` has a truthy
  value."
  [[dir & {:keys [keep-files?]}] & body]
  `(let [tmpdir# (create-temp-diretory!), ~dir tmpdir#]
     (try (do ~@body)
          (finally
            (when-not ~keep-files?
              (delete-files-recursively! tmpdir#))))))

(set-defn-indent! #'exiting-on-completion #'without-exiting #'with-temp-directory!
                  #'with-debug-logging)
