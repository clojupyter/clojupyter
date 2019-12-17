(ns clojupyter.plan
  (:require [clojupyter.util-actions :as u!]
            [io.simplect.compose :refer [C call-if invoke p P]]
            [io.simplect.compose.action :as action :refer [action]])
  (:import io.simplect.compose.action.Action))

;;; ----------------------------------------------------------------------------------------------------
;;; PLAN OPERATIONS - FUNDAMENTAL
;;; ----------------------------------------------------------------------------------------------------

(defn   get-value
  ([nm] (P get nm))
  ([nm default-value] (P get nm default-value)))
(defn	s*update-value		[nm f]		(P update-in [nm] f))
(def	get-values				identity)
(defn	s*remove-value		[nm]		(P dissoc nm))

;;; ----------------------------------------------------------------------------------------------------
;;; PLAN OPERATIONS - ADDITIONAL
;;; ----------------------------------------------------------------------------------------------------

(defn	s*append-value		[nm v]		(s*update-value nm #((fnil conj []) % v)))
(defn	s*set-value		[nm v]		(s*update-value nm (constantly v)))
(defn	s*set-values		[& args]	(fn [S] (reduce (fn [SS [nm v]] ((s*set-value nm v) SS)) S (partition 2 2 args))))

(defn	s*update-effects	[f]		(s*update-value :plan/effects f))
(def	get-effects				(get-value :plan/effects []))
(def	s*clear-effects				(s*update-effects (constantly [])))

(def	s*set-do-execute			(s*set-value :plan/execute? true))
(def	s*set-dont-execute			(s*set-value :plan/execute? false))
(def	executing?				(C (get-value :plan/execute?) boolean))
(def	halted?					(complement executing?))

(defn	s*update-log		[f]		(s*update-value :plan/log f))
(def	get-log					(get-value :plan/log []))
(defn	s*add-log		[level entry]	(s*update-log #((fnil conj []) % (assoc entry :log/level level))))
(defn	s*log-debug		[entry]		(s*add-log :debug entry))
(defn	s*log-info		[entry]		(s*add-log :info entry))
(defn	s*log-warn		[entry]		(s*add-log :warn entry))
(defn	s*log-error		[entry]		(C (s*add-log :error entry) s*set-dont-execute))

(defn   s*when			[v f]		(if v f identity))
(defn	s*when-not		[v f]		(if v identity f))

(defn	s*action-append		[a]		(s*update-value :plan/action #(action % a)))
(defn	s*action-prepend	[a]		(s*update-value :plan/action #(action a %)))

(defn	s*set-reporter		[r]		(s*set-value :plan/reporter r))
(def	get-reporter				(get-value :plan/reporter identity))

(defn	s*set-action		[a]		(s*set-value :plan/action a))
(def	get-action				(get-value :plan/action (action/action)))
(def	get-action-spec				(C get-action (call-if (complement nil?) action/step-specs)))
(defn	s*set-action-result	[res]		(s*set-value :action/result res))
(def	get-action-result			(get-value :action/result))

(def	s*ok					identity)

(defmacro s*bind-state
  [binding-expr expr]
  `(fn [S#]
     (let [~binding-expr S#]
       (~expr S#))))

(defn s*when-executing
  [s*fn]
  (s*bind-state S
    (s*when (executing? S)
      s*fn)))

(defn s*when-halted
  [s*fn]
  (s*bind-state S
    (s*when (halted? S)
      s*fn)))

(u!/set-defn-indent! #'s*bind-state #'s*when #'s*when-executing #'s*when-halted #'s*when-not)

;;; ----------------------------------------------------------------------------------------------------
;;; EXECUTE
;;; ----------------------------------------------------------------------------------------------------

(defn	s*set-execute-success!	[v]		(s*set-value :plan/execute-success? (boolean v)))
(def	execute-success?			(get-value :plan/execute-success? false))
(def	execute-complete?			(C (get-value :plan/execute-status) (p = :complete)))

(def s*execute
  "Invoke action in `S`. Returns S updated with indication of the results of invoking the action.
  Note that execute only occurs if state is not `halted?`, which is the default.  Use
  `s*set-do-execute` to enable invocation."
  (s*bind-state S
    (let [action ^Action (get-action S)]
      (if (halted? S)
	(C (s*log-info {:message "Execute: Not executing, skipping action."})
           (s*set-values :plan/execute-status :not-started)
           (s*set-execute-success! false))
        (let [res (invoke action)]
          (C (s*set-action-result res)
             (s*remove-value :plan/action)
             (s*set-value :plan/execute-status (if (action/completed? res) :complete :incomplete))
             (s*set-execute-success! (action/success? res))))))))

(def s*report
  (s*bind-state S
    (if (executing? S)
      (get-reporter S)
      (s*log-info {:message "Report: Not executing action, skipping status report."}))))

(def s*execute-and-report
  (C s*execute s*report))
