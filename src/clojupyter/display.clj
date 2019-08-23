(ns clojupyter.display
  (:require
   [io.simplect.compose						:refer [redefn]]
   ,,
   [clojupyter.misc.display			:as dis]))

(redefn display		dis/display)
(redefn hiccup-html	dis/hiccup-html)
(redefn html		dis/html)
(redefn latex		dis/latex)
(redefn markdown	dis/markdown)
(redefn render-mime	dis/render-mime)
