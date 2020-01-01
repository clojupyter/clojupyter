(ns clojupyter.display
  (:require [clojupyter.misc.display :as dis]
            [io.simplect.compose :refer [redefn]]))

(redefn display		dis/display)
(redefn hiccup-html	dis/hiccup-html)
(redefn html		dis/html)
(redefn latex		dis/latex)
(redefn markdown	dis/markdown)
(redefn render-mime	dis/render-mime)
