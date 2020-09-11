(ns clojupyter.tools-actions
  (:require [clojupyter.kernel.os :as os]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [io.simplect.compose :refer [C call-if p P]]
            [me.raynes.fs :as fs])
  (:import java.time.format.DateTimeFormatter))

(def version
  (binding [*read-eval* false]
    (-> (slurp "project.clj")
        read-string
        (nth 2))))

(def version-map
  (let [[_ major minor incremental qualifier]
        (re-find #"(\p{Digit}+)\.(\p{Digit}+)\.(\p{Digit}+)(-.+)?$" version)]
    {:major major
     :minor minor
     :incremental incremental
     :qualifier (or qualifier "")}))

(def version-short
  (re-find #"\p{Digit}+\.\p{Digit}+\.\p{Digit}+" version))

(defn default-ident
  ([] (default-ident version))
  ([ver]
   (str "clojupyter-" ver)))

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

(def normalized-file
  (call-if (complement nil?) (C io/file fs/normalized)))

(defn set-var-private!
  [var]
  (alter-meta! var (P assoc :private true)))

(defn throw-info
  ([msg] (throw-info msg {}))
  ([msg m]
   (throw (ex-info msg (assoc m :msg msg)))))

(defn uuid
  "Returns a random UUID as a string."
  []
  (str (java.util.UUID/randomUUID)))

(defn delete-files-recursively!
  [f]
  (letfn [(delete-f [^java.io.File file]
            (when (.isDirectory file)
              (doseq [child-file (.listFiles file)]
                (delete-f child-file)))
            (io/delete-file file))]
    (when f
      (if (string? f)
        (delete-files-recursively! (io/file f))
        (delete-f f)))))

(defn create-temp-directory!
  "Creates a new readable/writable directory and returns its name as a `java.io.File`.  Throws an
  exception if the temp directory could not be created."
  []
  (letfn [(sys-tmpdir [] (io/file (System/getProperty "java.io.tmpdir")))]
    (if-let [sysdir (sys-tmpdir)]
      (let [tmpdir (io/file (str sysdir "/clojure-tmp-" (uuid)))]
        (if (.mkdir tmpdir)
          tmpdir
          (throw-info (str "Failed to create temp dir: " tmpdir)
                       {:sysdir sysdir, :tmpdir tmpdir})))
      (throw (Exception. "Failed to get location for temp files (java.io.tmpdir).")))))

(defmacro with-temp-directory!
  "Evaluates `body` with `dir` bound to a newly created, readable and writable directory.  Upon exit
  all files and directories in the temp directory are deleted unless `keep-files?` has a truthy
  value."
  [[dir & {:keys [keep-files?]}] & body]
  `(let [tmpdir# (create-temp-directory!), ~dir tmpdir#]
     (try (do ~@body)
          (finally
            (when-not ~keep-files?
              (delete-files-recursively! tmpdir#))))))


(def version
  (binding [*read-eval* false]
    (-> (slurp "project.clj")
        read-string
        (nth 2))))

(def version-map
  (let [[_ major minor incremental qualifier]
        (re-find #"(\p{Digit}+)\.(\p{Digit}+)\.(\p{Digit}+)(-.+)?$" version)]
    {:major major
     :minor minor
     :incremental incremental
     :qualifier (if qualifier qualifier "")}))

(def version-short
  (re-find #"\p{Digit}+\.\p{Digit}+\.\p{Digit}+" version))
