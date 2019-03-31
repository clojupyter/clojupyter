(ns clojupyter.kernel.util
  (:require
   [cheshire.core				:as cheshire]
   [clojure.pprint				:as pp]
   [clojure.spec.alpha				:as s]
   [java-time					:as jtm]
   [pandect.algo.sha256						:refer [sha256-hmac]]
   [taoensso.timbre				:as log]
   [zprint.core					:as zp]
   ,,
   [clojupyter.kernel.spec			:as sp]
   )
  (:import [java.time.format DateTimeFormatter]))

(defn ctx?
  [v]
  (s/valid? ::sp/ctx v))

(defn uuid
  []
  (str (java.util.UUID/randomUUID)))

(defn merge-docmeta
  "Add the values for keys `:doc` and `:arglists` from `refvar` to the
  meta of `var`."
  [var refvar]
  (alter-meta! var #(merge % (select-keys (meta refvar) [:doc :arglists]))))

(def json-str cheshire/generate-string)
(def parse-json-str cheshire/parse-string)

(defn >bytes
  [v]
  (cond
    (= (type v) (Class/forName "[B"))	v
    (string? v)	(.getBytes v)
    true	(.getBytes (json-str v))))

(defn now []
 (->> (.withNano (java.time.ZonedDateTime/now) 0)
      (jtm/format DateTimeFormatter/ISO_OFFSET_DATE_TIME)))

(defn pp-str
  [v]
  (with-out-str (pp/pprint v)))

(defn rcomp
  [& fs]
  (apply comp (reverse fs)))

(defn- falsey? [v] (or (= v nil) (= v false)))
(def truthy? (complement falsey?))

(defmacro with-debug-logging
  [[& args] & forms]
  `(let [uuid# (str "#" (subs (uuid) 0 8))]
     (log/debug uuid# "START" ~@args)
     (let [res# (do ~@forms)]
       (log/debug uuid# "END"  )
       res#)))

(def reformat-form
  (rcomp read-string zp/zprint-str pr-str println))

(defn stream-to-string
  [map]
  (let [repr (java.io.StringWriter.)]
    (cheshire/generate-stream map repr)
    (str repr)))

(defn safe-byte-array-to-string
  [array]
  (apply str (map #(if (pos? %) (char %) \!) (seq array))))

(defn set-var-indent!
  [indent-style var]
  (alter-meta! var #(assoc % :style/indent indent-style)))

(defn set-var-private!
  [var]
  (alter-meta! var #(assoc % :private true)))

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

(defn assoc-meta!
  [k v var]
  (alter-meta! var #(assoc % k v)))

(defmacro re-defn
  [name refname]
  `(do (def ~name ~refname)
       (alter-meta! (var ~name)
                    #(merge % {:arglists (-> (var ~refname) meta :arglists)
                                 :doc (-> (var ~refname) meta :doc)}))))


