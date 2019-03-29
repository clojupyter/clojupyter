(ns clojupyter.kernel.logo
  (:require
   [clojure.edn			:as edn]
   [clojure.java.io		:as io]
   ,,
   [clojupyter.display		:as dis])
  (:import
   [javax.imageio ImageIO]))

(def ^:private logo-filename	"(clojupyter) 350x80.png")
(def ^:private license-filename	"LICENSE.TXT")

(defn- logo-dimensions
  [filename]
  (let [[_ x y] (re-find #"(\d+)x(\d+).png$" filename)]
    (mapv edn/read-string [x y])))

(defn logo-resource
  "Returns a clojupyter logo as a `java.io.resource`."
  []
  (or (io/resource logo-filename)
      (->> logo-filename (str "./resources/") io/file)))

(defn license-resource
  "Returns the clojupyter license as a `java.io.resource`."
  []
  (or (io/resource license-filename)
      (->> license-filename (str "./resources/") io/file)))

(defn logo-image
  "Returns a clojupyter logo as a `java.awt.image.BufferedImage`."
  []
  (ImageIO/read (logo-resource)))


(defn render-license
  []
  (dis/render-mime :text/plain (-> (license-resource) slurp)))
