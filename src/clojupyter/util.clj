(ns clojupyter.util
  (:require [cheshire.core :as cheshire]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [io.simplect.compose :refer [C def- p redefn]]
            [pandect.algo.sha256 :refer [sha256-hmac]]
            [zprint.core :as zp]
            [clojupyter.log :as log]))

(def- CHARSET "UTF8")

(defn- call-if*
  "Takes a single argument.  If applying `pred` to the argument yields a truthy value returns the
  result of applying `f` to the argument, otherwise returns the argument itself."
  [pred f]
  #(if (pred %) (f %) %))

(def- json-str* cheshire/generate-string)

(defn- re-pattern+*
  [regex-string]
  (try (re-pattern (str regex-string)) (catch Throwable _ nil)))

(defn- tildeize-filename*
  [user-homedir v]
  (if (instance? java.io.File v)
    (str/replace (str v) (str user-homedir) "~")
    v))

(defn- bytes->string*
  [bytes]
  (String. bytes "UTF-8"))

(defn- string->bytes*
  [v]
  (cond
    (= (type v) (Class/forName "[B"))
    ,, v
    (string? v)
    ,, (.getBytes ^String v CHARSET)
    :else
    ,, (.getBytes (json-str* v) CHARSET)))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(def IDSMSG "<IDS|MSG>")
(def IDSMSG-BYTES (string->bytes* IDSMSG))
(def SEGMENT-ORDER [:envelope :delimiter :signature :header :parent-header :metadata :content])

(def asset-resource-path
  (p str "clojupyter/assets/"))

(def as-sorted-map (C (p apply concat) (p apply sorted-map)))

(redefn bytes->string bytes->string*)

(redefn call-if call-if*)

(defn code-empty?
  [s]
  (-> s str str/trim count zero?))

(defn code-hushed?
  [s]
  (-> s str str/trimr (str/ends-with? ";")))

(def delimiter-frame?
  "Accepts a single argument which must be a byte-array, returns `true` the array represents the
  Jupyter 'delimiter' frame (consisting of the bytes of the text '<IDS|MSG>' encoded as UTF-8);
  otherwise returns `false`."
  (let [delim ^bytes IDSMSG-BYTES
        n-delim (count delim)]
    (fn [^bytes other-byte-array]
      (if (= n-delim (count other-byte-array))
        (loop [idx 0]
          (cond
            (= idx n-delim)
            ,, true
            (= (aget delim idx) (aget other-byte-array idx))
            ,, (recur (inc idx))
            :else
            ,, false))
        false))))

(defn display-name->ident
  [s]
  (if-let [[_ m] (re-find #"^Clojure \(([^\)]+)\)$" s)] m s))

(defn falsey? [v] (or (= v nil) (= v false)))

(defn filename-in-dir
  [filename dir]
  (io/file (str (io/file dir) "/" (-> filename io/file .getName))))

(defn files-as-strings
  [user-homedir coll]
  (walk/postwalk (C (call-if (p instance? java.net.URL) str)
                    (p tildeize-filename* user-homedir))
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

(def file-input-stream
  (C io/file io/input-stream))

(def file-output-stream
  (C io/file io/output-stream))

(redefn json-str json-str*)

(defn kernel-full-identifier
  [ident]
  (str "Clojure (" ident ")"))

(defn kernel-spec
  [dest-jar kernel-id-string]
  {:argv ["java" "-cp" (str dest-jar) "clojupyter.kernel.core" "{connection_file}"]
   :display_name (kernel-full-identifier kernel-id-string)
   :language "clojure"
   :interrupt_mode "message"})

(defmacro ^{:style/indent :defn} define-simple-record-print
  [name format-fn]
  (if (symbol? name)
    (let [fmtfn (gensym "fn-")]
      `(let [~fmtfn ~format-fn]
         (defmethod print-method ~name
           [rec# w#]
           (.write w# (~fmtfn rec#)))
         (defmethod pp/simple-dispatch ~name
           [rec#]
           (print (~fmtfn rec#)))))
    (throw (Exception. (str "define-simple-record-print: invalid form")))))

(defn log-messages
  ([log] (log-messages #{:info :warn :error} log))
  ([log-levels log]
   (->> log
        (filter (C :log/level (p contains? log-levels)))
        (filter :message)
        (mapv :message))))

(defn make-signer-checker
  [key]
  (let [mkchecker (fn mkchecker [signer]
                    (fn [{:keys [header parent-header metadata content]} signature]
                      (or (empty? key)         ; Disable signature checking when no session key
                        (let [payload-vec [header parent-header metadata content]
                            check-signature (signer payload-vec)]
                        (log/debug "Checking message with key" key)
                        (= check-signature (bytes->string* signature))))))
        signer	(if (empty? key)
                  (constantly "")
                  (fn signer [payload-vec]
                    (sha256-hmac (apply str payload-vec) key)))]
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
  (C read-string zp/zprint-str pr-str println))

(def resource-path
  (p str "clojupyter/"))

(defn safe-byte-array-to-string
  [array]
  (apply str (map #(if (pos? %) (char %) \!) (seq array))))

(defn sanitize-string
  "Given a string, returns the string with all characters not matching `regex` removed."
  [regex]
  (C str
     (p filter (C str (p re-find regex)))
     (p apply str)
     str/lower-case))

(defn to-json-str
  "Returns JSON representation (string) of `v`."
  [v]
  (let [repr (java.io.StringWriter.)]
    (cheshire/generate-stream v repr)
    (str repr)))

(defn submap?
  "Returns `true` if all keys in `submap` map to equal values in `supermap`, and `false` otherwise."
  [submap supermap]
  (set/subset? (set submap) (set supermap)))

(redefn tildeize-filename tildeize-filename*)

(def truthy? (complement falsey?))

(redefn get-bytes string->bytes*)
