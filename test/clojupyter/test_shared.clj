(ns clojupyter.test-shared
  (:require
   [clojure.java.io				:as io]
   [clojure.spec.alpha				:as s]
   [clojure.string				:as str]
   [clojure.test.check				:as tc]
   [clojure.test.check.generators		:as gen 	:refer [sample]]
   [clojure.test.check.properties		:as prop]
   [io.simplect.compose						:refer [def- γ Γ π Π redefn]]
   ,,
   [clojupyter.install.local-specs		:as sp] ;; necessary for :local/filetype
   [clojupyter.install.filemap			:as fm]))

;;; ----------------------------------------------------------------------------------------------------
;;; INTERNAL
;;; ----------------------------------------------------------------------------------------------------

(defn- g-name*
  "Generator producing alphanum strings ('names') between 2 and 5 chars long."
  [minlen maxlen]
  (assert (and (<= 0 minlen) (< minlen maxlen)) "g-name: minlen must be a non-negative integer less than maxlen")
  (gen/fmap (Γ flatten (π apply str))
            (gen/tuple gen/char-alpha
                       (gen/vector (gen/frequency [[1 (gen/elements [\- \_])]
                                                   [5 gen/char-alphanumeric]])
                                   minlen maxlen))))

(def- g-path*
  "Generator producing file names consisting of 2 to 4 segments generated using `g-name`."
  (gen/let [s (g-name* 2 4)]
    (gen/return (io/file (str "/" (str/join "/" s))))))

(defn- g-string*
  ([minlen maxlen] (g-string* gen/char minlen maxlen))
  ([char-generator minlen maxlen]
   (gen/fmap (π apply str) (gen/vector char-generator minlen maxlen))))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ----------------------------------------------------------------------------------------------------

(def R gen/return)

(defmacro ==>
  "Implies" ;; Can't be a function since we want to not evaluate y when not x
  [x y]
  `(or (not ~x) (boolean ~y)))

(defn <==>
  "If-and-only-if"
  [x y]
  (and (==> x y) (==> y x)))

(defn g-alphanum
  [minlen maxlen]
  (gen/fmap (π apply str) (gen/vector gen/char-alphanumeric minlen maxlen)))

(defn g-combine-flag-and-val
  [g-flag g-val]
  (gen/let [flag g-flag
            value g-val
            assign? gen/boolean]
    (R [value (cond
                (not flag)
                ,, []
                (re-find #"^--" (str flag))
                ,, (if assign?
                     [(str flag "=" value)]
                     [flag value])
                :else
                ,, [(str flag value)])])))

(defn g-constant
  [v]
  (gen/elements [v]))

(def g-filetype
  (s/gen :local/filetype))

(defn g-file
  [string-generator]
  (gen/fmap io/file string-generator))

(def g-flag-double
  (gen/fmap (π str "--") (g-alphanum 2 10)))

(def g-flag-single
  (gen/fmap (π str "-") (g-alphanum 1 1)))

(defn g-hex-string
  [minlen maxlen]
  (gen/fmap (π apply str)
            (gen/vector (gen/elements [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9, \a \b \c \d \e \f])
                        minlen maxlen)))

(defn g-filemap
  [files]
  (let [files (remove nil? files)]
    (gen/let [types (gen/vector g-filetype (count files))]
      (->> (map vector files types)
           (into {})
           fm/filemap))))

(def g-filemap-random
  (gen/let [files (gen/vector g-path*)
            M (g-filemap files)]
    (R M)))

(def g-ident
  (g-name* 1 10))

(redefn g-name g-name*)

(def g-nil
  (g-constant nil))

(defn g-nilable
  [g]
  (gen/one-of [(gen/elements [nil]) g]))

(redefn g-path g-path*)

(def g-resource
  (gen/fmap (Γ (π str "file://") #(java.net.URL. %)) g-path))

(redefn g-string g-string*)
