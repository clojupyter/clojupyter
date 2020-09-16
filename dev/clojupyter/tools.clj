(ns clojupyter.tools
  (:require [io.simplect.compose :refer [C def- p redefn]]
            [clojure.walk :as walk]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [clojure.set :as set]))

(defn call-if
  "Takes a single argument.  If applying `pred` to the argument yields a truthy value returns the
  result of applying `f` to the argument, otherwise returns the argument itself."
  [pred f]
  #(if (pred %) (f %) %))

(defn kernel-full-identifier
  [ident]
  (str "Clojure (" ident ")"))

(defn display-name->ident
  [s]
  (if-let [[_ m] (re-find #"^Clojure \(([^\)]+)\)$" s)] m s))

(defn tildeize-filename
  [user-homedir v]
  (if (instance? java.io.File v)
    (str/replace (str v) (str user-homedir) "~")
    v))

(defn files-as-strings
  [user-homedir coll]
  (walk/postwalk (C (call-if (p instance? java.net.URL) str)
                    (p tildeize-filename user-homedir))
                 (if (seq? coll) (vec coll) coll)))

(def file-ancestors
  "Given a file object returns a set containing the file itself and the transitive union of its
  parents.  Note: The ancestry is derived exclusively by traversing the *file values*, i.e. does not
  matter whether or not the files thus traversed exist in an underlying file system."
  (C (p iterate #(.getParentFile ^java.io.File %))
     (p take-while (complement nil?))
     (p into #{})))

(defn file-ancestor-of
  [ancestor f]
  "Returns `true` if `f1` is an ancestor directory of `f2` and `false` otherwise, where 'ancestor'
  is defined as in `file-ancestors`, i.e. an ancestor of a file is the transitive union of itself
  and its parents. Note: The ancestry is derived exclusively by traversing the *file values*,
  i.e. does not matter whether or not the files thus traversed exist in an underlying file system."
  (contains? (file-ancestors f) ancestor))

(redefn json-str cheshire/generate-string)
(redefn parse-json-str cheshire/parse-string)

(defn kernel-spec
  [dest-jar kernel-id-string]
  {:argv ["java" "-cp" (str dest-jar) "clojupyter.kernel.core" "{connection_file}"]
   :display_name (kernel-full-identifier kernel-id-string)
   :language "clojure"
   :interrupt_mode "message"})

(defn log-messages
  ([log] (log-messages #{:info :warn :error} log))
  ([log-levels log]
   (->> log
        (filter (C :log/level (p contains? log-levels)))
        (filter :message)
        (mapv :message))))

(defn re-pattern+
  [regex-string]
  (try (re-pattern (str regex-string)) (catch Throwable _ nil)))

(defn sanitize-string
  "Given a string, returns the string with all characters not matching `regex` removed."
  [regex]
  (C str
     (p filter (C str (p re-find regex)))
     (p apply str)
     str/lower-case))

(defn falsey? [v] (or (= v nil) (= v false)))
(def truthy? (complement falsey?))

(defn re-find+
  "If `regex` is a representing a correct regular expression, parsable by `re-find`, returns the
  result of applying `re-find` to the result of applying `str` to `s`, otherwise returns `nil`."
  [regex-string s]
  (when-let [re (re-pattern+ regex-string)]
    (re-find re (str s))))

(defn submap?
  "Returns `true` if all keys in `submap` map to equal values in `supermap`, and `false` otherwise."
  [submap supermap]
  (set/subset? (set submap) (set supermap)))
