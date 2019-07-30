(ns clojupyter.util
  (:require
   [cheshire.core				:as cheshire]
   [clojure.java.io				:as io]
   [clojure.pprint				:as pp]
   [clojure.set					:as set]
   [clojure.spec.alpha				:as s]
   [clojure.string				:as str]
   [clojure.walk				:as walk]
   [io.simplect.compose						:refer [def- redefn π Π γ Γ λ]]
   [pandect.algo.sha256						:refer [sha256-hmac]]
   [zprint.core					:as zp]
   ,,
   [clojupyter.kernel.spec			:as sp]
   ))

(defn- call-if*
  "Takes a single argument.  If applying `pred` to the argument yields a truthy value returns the
  result of applying `f` to the argument, otherwise returns the argument itself."
  [pred f]
  #(if (pred %) (f %) %))

(defn- re-pattern+*
  [regex-string]
  (try (re-pattern (str regex-string)) (catch Throwable _ nil)))

(defn tildeize-filename*
  [user-homedir v]
  (if (instance? java.io.File v)
    (str/replace (str v) (str user-homedir) "~")
    v))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(def asset-resource-path
  (π str "clojupyter/assets/"))

(def as-sorted-map (Γ (π apply concat) (π apply sorted-map)))

(redefn call-if call-if*)

(defn ctx?
  [v]
  (s/valid? ::sp/ctx v))

(defn display-name->ident
  [s]
  (if-let [[_ m] (re-find #"^Clojure \(([^\)]+)\)$" s)] m s))

(defn falsey? [v] (or (= v nil) (= v false)))

(defn filename-in-dir
  [filename dir]
  (io/file (str (io/file dir) "/" (-> filename io/file .getName))))

(defn files-as-strings
  [user-homedir coll]
  (walk/postwalk (Γ (call-if (π instance? java.net.URL) str)
                    (π tildeize-filename* user-homedir))
                 (if (seq? coll) (vec coll) coll)))

(def file-ancestors
  "Given a file object returns a set containing the file itself and the transitive union of its
  parents.  Note: The ancestry is derived exclusively by traversing the *file values*, i.e. does not
  matter whether or not the files thus traversed exist in an underlying file system."
  (Γ (π iterate #(.getParentFile %))
     (π take-while (complement nil?))
     (π into #{})))

(defn file-ancestor-of
  [ancestor f]
  "Returns `true` if `f1` is an ancestor directory of `f2` and `false` otherwise, where 'ancestor'
  is defined as in `file-ancestors`, i.e. an ancestor of a file is the transitive union of itself
  and its parents. Note: The ancestry is derived exclusively by traversing the *file values*,
  i.e. does not matter whether or not the files thus traversed exist in an underlying file system."
  (contains? (file-ancestors f) ancestor))

(def file-input-stream
  (Γ io/file io/input-stream))

(def file-output-stream
  (Γ io/file io/output-stream))

(redefn json-str cheshire/generate-string)

(defn kernel-full-identifier
  [ident]
  (str "Clojure (" ident ")"))

(defn kernel-spec
  [dest-jar kernel-id-string]
  {:argv ["java" "-cp" (str dest-jar) "clojupyter.kernel.core" "{connection_file}"]
   :display_name (kernel-full-identifier kernel-id-string)
   :language "clojure"})

(defn log-messages
  ([log] (log-messages #{:info :warn :error} log))
  ([log-levels log]
   (->> log
        (filter (Γ :log/level (π contains? log-levels)))
        (filter :message)
        (mapv :message))))

(defn make-signer-checker
  [key]
  (let [mkchecker (fn [signer]
                    (fn [{:keys [signature header parent-header metadata content]}]
                      (let [our-signature (signer header parent-header metadata content)]
                        (= our-signature signature))))
        signer	(if (empty? key)
                  (constantly "")
                  (fn [header parent metadata content]
                    (let [res (apply str (map json-str [header parent metadata content]))]
                      (sha256-hmac res key))))]
    [signer (mkchecker signer)]))

(redefn parse-json-str cheshire/parse-string)

(defn pp-str
  [v]
  (with-out-str (pp/pprint v)))

(defn re-find+
  "If `regex` is a representing a correct regular expression, parsable by `re-find`, returns the
  result of applying `re-find` to the result of applying `str` to `s`, otherwise returns `nil`."
  [regex-string s]
  (when-let [re (re-pattern+* regex-string)]
    (re-find re (str s))))

(redefn re-pattern+ re-pattern+*)

(def reformat-form
  (Γ read-string zp/zprint-str pr-str println))

(def resource-path
  (π str "clojupyter/"))

(defn safe-byte-array-to-string
  [array]
  (apply str (map #(if (pos? %) (char %) \!) (seq array))))

(defn sanitize-string
  "Given a string, returns the string with all characters not matching `regex` removed."
  [regex]
  (Γ str
     (π filter (Γ str (π re-find regex)))
     (π apply str)
     str/lower-case))

(defn stream-to-string
  [map]
  (let [repr (java.io.StringWriter.)]
    (cheshire/generate-stream map repr)
    (str repr)))

(defn submap?
  "Returns `true` if all keys in `submap` map to equal values in `supermap`, and `false` otherwise."
  [submap supermap]
  (set/subset? (set submap) (set supermap)))

(redefn tildeize-filename tildeize-filename*)

(def truthy? (complement falsey?))

(defn >bytes
  [v]
  (cond
    (= (type v) (Class/forName "[B"))	v
    (string? v)	(.getBytes v)
    :else	(.getBytes (json-str v))))

