(ns clojupyter.cmdline.api
  (:require
   [clojure.pprint					:refer [pprint]]
   [clojure.string			:as str]
   [io.simplect.compose					:refer [def- γ Γ π Π]]))

(defn	output		[s]		(Π update-in [:cmdline/output] #(conj (or % []) s)))
(defn	outputs		[ss]		(fn [S] (reduce (fn [SS s] ((output s) SS)) S ss)))
(defn	set-error	[e]		(Π assoc :cmdline/error e))
(defn	set-exit-code	[c]		(Π assoc :cmdline/exit-code c))
(defn	set-header	[s]		(Π assoc :cmdline/header s))
(defn	set-prefix	[s]		(Π assoc :cmdline/prefix s))
(defn	set-result	[v]		(Π assoc :cmdline/result v))
(defn	update-result	[f]		(Π update :cmdline/result f))
(defn	assoc-result	[nm v]		(update-result #(assoc % nm v)))

(def ppoutput
  (Γ #(with-out-str (pprint %)) str/split-lines outputs))

(def- set-initial-state
  (Γ (set-header "")
     (set-prefix "")
     (set-exit-code 0)))

(def initial-state (set-initial-state {}))

