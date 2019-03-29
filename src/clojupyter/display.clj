(ns clojupyter.display
  (:require
   [clojupyter.misc.display			:as dis]
   [clojupyter.kernel.util			:as u]))

(u/re-defn display		dis/display)
(u/re-defn hiccup-html		dis/hiccup-html)
(u/re-defn html			dis/html)
(u/re-defn latex		dis/latex)
(u/re-defn markdown		dis/markdown)
(u/re-defn render-mime		dis/render-mime)
