(ns clojupyter
  (:require
   [clojupyter.misc.helper		:as helper]
   [clojupyter.misc.display		:as display]
   [clojupyter.misc.util		:as u]))

(def display			display/display)
(def hiccup-html		display/hiccup-html)
(def html			display/html)
(def latex			display/latex)
(def markdown			display/markdown)

(def add-dependencies		helper/add-dependencies)
(def add-javascript		helper/add-javascript)

(map (partial apply u/merge-docmeta)
     [[#'display		#'display/display]
      [#'hiccup-html		#'display/hiccup-html]
      [#'html			#'display/html]
      [#'latex			#'display/latex]
      [#'markdown		#'display/markdown]
      [#'add-dependencies	#'helper/add-dependencies]
      [#'add-javascript		#'helper/add-javascript]])

(def ^:dynamic *clojupyter-version*
  "Value is a map representing the version of clojupyter as a map with
  the keys `:major`, `:minor`, `:incremental`, and `qualifier`, where
  the former 3 are integers and the latter is a string.  Analoguous to
  `*clojure-version*`." 
  {:major 0, :minor 0, :incremental 0, :qualifier nil})
