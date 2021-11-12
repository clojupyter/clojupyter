(ns clojupyter.kernel.logo
  (:require [clojupyter.display :as dis]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import javax.imageio.ImageIO))

(def ^:private LOGO-PATH	"clojupyter/assets/logo-350x80.png")
(def ^:private LICENSE-PATH	"clojupyter/assets/license.txt")

(defn- logo-dimensions
  [filename]
  (when-let [[_ x y] (re-find #"(\d+)x(\d+).png$" filename)]
    (mapv edn/read-string [x y])))

(defn logo-resource
  "Returns a clojupyter logo as a `java.io.resource`."
  []
  (or (io/resource LOGO-PATH)
      (->> LOGO-PATH (str "./resources/") io/file)))

(defn license-resource
  "Returns the clojupyter license as a `java.io.resource`."
  []
  (or (io/resource LICENSE-PATH)
      (->> LICENSE-PATH (str "./resources/") io/file)))

(defn logo-image
  "Returns a clojupyter logo as a `java.awt.image.BufferedImage`."
  []
  (ImageIO/read ^java.net.URL (logo-resource)))


(defn render-license
  []
  (dis/render-mime :text/plain (-> (license-resource) slurp)))
