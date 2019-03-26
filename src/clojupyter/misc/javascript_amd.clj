(ns clojupyter.misc.javascript-amd
  (:require
   [clojupyter.misc.display			:as display]
   [clojure.data.json				:as json]
   [clojure.spec.alpha				:as s]
   [clojure.string				:as str]
   [clojure.spec.test.alpha					:refer [instrument]]
   ,,
   [clojupyter.misc.util			:as u]
   ))

(s/def ::ident		keyword?)
(s/def ::exports	string?)
(s/def ::url		(s/and string? (partial re-find #"^https?://")))
(s/def ::jsdef		(s/keys :req-un [::ident ::exports ::url]))
(s/def ::jsdefs		(s/coll-of ::jsdef :kind vector?))

(defn- wrap-requirejs-config
  [s]
  (str "requirejs.config(" s ")"))

(defn- wrap-statements
  [& ss]
  (str (str/join ";" ss) ";"))

;;; ----------------------------------------------------------------------------------------------------
;;; EXTERNAL INTERFACE
;;; ----------------------------------------------------------------------------------------------------

(defn amd-wrap-require
  "Cf. docstring in namespace `clojupyter`."
  [ident-vec javascript-function]
  (wrap-statements
   (format "require(%s,%s)" (json/write-str (mapv name ident-vec)) javascript-function)))

(defn amd-add-javascript
  "Cf. docstring in namespace `clojupyter`."
  
  [jsdefs]
  (-> {:paths (->> jsdefs
                   (map (juxt :ident :url))
                   (into {}))
       :shim (->> jsdefs
                  (map (fn [{:keys [ident exports]}] {ident {:exports exports}}))
                  (into {}))}
      json/write-str
      wrap-requirejs-config))

(defn amd-add-javascript-html
  "Cf. docstring in namespace `clojupyter`."
  [jsdefs]
  (display/hiccup-html [:script (wrap-statements (amd-add-javascript jsdefs))]))

(do
  (s/fdef amd-add-javascript		:args (s/cat :jsdefs ::jsdefs))
  (s/fdef amd-add-javascript-html	:args (s/cat :jsdefs ::jsdefs))
  (s/fdef amd-wrap-require		:args (s/cat :ident-vec (s/coll-of ::ident :kind vector?)
                                                     :javascript-function string?))
  (instrument `amd-add-javascript)
  (instrument `amd-add-javascript-html)
  (instrument `and-wrap-require)
  (map (partial u/assoc-meta! :style/indent :defn)
       [#'amd-wrap-require #'wrap-statements #'wrap-requirejs-config]))


