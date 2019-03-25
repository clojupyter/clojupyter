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

(defn wrap-require
  [ident-vec javascript-function]
  (wrap-statements
   (format "require(%s,%s)" (json/write-str (mapv name ident-vec)) javascript-function)))

(defn add-javascript
  [jsdefs]
  (-> {:paths (->> jsdefs
                   (map (juxt :ident :url))
                   (into {}))
       :shim (->> jsdefs
                  (map (fn [{:keys [ident exports]}] {ident {:exports exports}}))
                  (into {}))}
      json/write-str
      wrap-requirejs-config))

(defn add-javascript-html
  [jsdefs]
  (display/hiccup-html [:script (wrap-statements (add-javascript jsdefs))]))

(do
  (s/fdef add-javascript		:args (s/cat :jsdefs ::jsdefs))
  (s/fdef add-javascript-html		:args (s/cat :jsdefs ::jsdefs))
  (s/fdef wrap-require			:args (s/cat :ident-vec (s/coll-of ::ident :kind vector?)
                                                     :javascript-function string?))
  (instrument `add-javascript)
  (instrument `add-java-script-html)
  (instrument `wrap-require)
  (map (partial u/assoc-meta! :style/indent :defn)
       [#'wrap-require #'wrap-statements #'wrap-requirejs-config]))


