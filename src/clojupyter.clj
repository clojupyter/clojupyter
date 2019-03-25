(ns clojupyter
  (:require
   [clojupyter.misc.display		:as display]
   [clojupyter.misc.helper		:as helper]
   [clojupyter.misc.javascript-amd	:as jsamd]
   [clojupyter.misc.util		:as u]))

(def display			display/display)
(def hiccup-html		display/hiccup-html)
(def html			display/html)
(def latex			display/latex)
(def markdown			display/markdown)

(def add-dependencies		helper/add-dependencies)

(def amd-add-javascript		jsamd/add-javascript)
(def amd-add-javascript-html	jsamd/add-javascript-html)
(def amd-wrap-require		jsamd/wrap-require)

(map (partial apply u/merge-docmeta)
     [[#'display			#'display/display]
      [#'hiccup-html			#'display/hiccup-html]
      [#'html				#'display/html]
      [#'latex				#'display/latex]
      [#'markdown			#'display/markdown]
      ,,
      [#'add-dependencies		#'helper/add-dependencies]
      ,,
      [#'amd-add-javascript		#'jsamd/add-javascript]
      [#'amd-add-javascript-html	#'jsamd/add-javascript-html]
      [#'amd-wrap-require		#'jsamd/wrap-require]])

(def ^:dynamic *clojupyter-version*
  "Value is a map representing the version of clojupyter as a map with
  the keys `:major`, `:minor`, `:incremental`, and `qualifier`, where
  the former 3 are integers and the latter is a string.  Analoguous to
  `*clojure-version*`." 
  {:major 0, :minor 0, :incremental 0, :qualifier nil})
