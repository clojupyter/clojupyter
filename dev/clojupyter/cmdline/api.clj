(ns clojupyter.cmdline.api
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [io.simplect.compose :refer [c C def- P]]))

(defn	output		[s]		(P update-in [:cmdline/output] #(conj (or % []) s)))
(defn	outputs		[ss]		(fn [S] (reduce (fn [SS s] ((output s) SS)) S ss)))
(defn	set-error	[e]		(P assoc :cmdline/error e))
(defn	set-exit-code	[c]		(P assoc :cmdline/exit-code c))
(defn	set-header	[s]		(P assoc :cmdline/header s))
(defn	set-prefix	[s]		(P assoc :cmdline/prefix s))
(defn	set-result	[v]		(P assoc :cmdline/result v))
(defn	update-result	[f]		(P update :cmdline/result f))
(defn	assoc-result	[nm v]		(update-result #(assoc % nm v)))

(def ppoutput
  (C #(with-out-str (pprint %)) str/split-lines outputs))

(def- set-initial-state
  (C (set-header "")
     (set-prefix "")
     (set-exit-code 0)))

(def initial-state (set-initial-state {}))

