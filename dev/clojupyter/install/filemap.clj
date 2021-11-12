(ns clojupyter.install.filemap
  (:require [clojupyter.tools-actions :as u!]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [io.simplect.compose :refer [p P]]))

(use 'clojure.pprint)

(declare filemap filemap?)

(defn- fstype
  [v]
  (when (instance? java.io.File v)
    (let [v ^java.io.File v]
      (cond
        (not (.exists v))	nil
        (.isFile v)		:filetype/file
        (.isDirectory v)	:filetype/directory
        :else			:filetype/other))))

(defprotocol filemap-proto
  (dir		[_ nm])
  (entity	[_ nm])
  (exists	[_ nm] [_ nm default])
  (file		[_ nm])
  (get-map	[_])
  (names	[_]))

(deftype FileMap [_m]
  filemap-proto
  (dir		[fm nm]		(when-let [typ (exists fm nm)]
                                  (when (= typ :filetype/directory)
                                    nm)))
  (entity	[fm nm]		(when (exists fm nm)
                                  nm))
  (exists	[fm nm]		(exists fm nm nil))
  (exists	[_ nm default]	(clojure.core/get _m nm default))
  (file		[fm nm]		(when-let [typ (exists fm nm)]
                                  (when (= typ :filetype/file)
                                    nm)))
  (get-map	[_]		_m)
  (names	[_]		(keys _m))
  Object
  (toString	[_]		(str "#filemap" (with-out-str (print _m)) ""))
  (equals	[fm v] 		(boolean
                                 (when (filemap? v)
                                   (= _m (get-map v))))))

(alter-meta! #'->FileMap (P assoc :private true))

(defmethod print-method FileMap
  [^FileMap fm ^java.io.Writer w]
  (if pp/*print-pretty*
    (binding [*out* (pp/get-pretty-writer w)
              pp/*print-suppress-namespaces* true]
      (pp/pprint-logical-block
       (.write w "#filemap")
       (pp/pprint-logical-block (pp/write (get-map fm)))))
    (.write w (str fm))))

(def filemap? (p instance? FileMap))

(defn- coerce-to-map
  [v]
  (cond
    (instance? java.io.File v)	{v (fstype v)}
    (nil? v)			{}
    (map? v)			v
    (filemap? v)		(get-map v)
    (string? v)			(recur (io/file v))
    (seq? v)			(recur (apply filemap v))
    (vector? v)			(recur (apply filemap v))
    (set? v)			(recur (apply filemap v))
    :else			(u!/throw-info (str "Can't be coerced to map: " v) {:fm v})))

(defn filemap
  ([] (->FileMap {}))
  ([fm]
   (->FileMap (coerce-to-map fm)))
  ([fm1 fm2]
   (filemap (merge (coerce-to-map fm1) (coerce-to-map fm2))))
  ([fm1 fm2 & fms]
   (apply filemap (filemap fm1 fm2) fms)))

