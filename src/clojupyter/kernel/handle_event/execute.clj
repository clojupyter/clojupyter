(ns clojupyter.kernel.handle-event.execute
  (:require [clojupyter.kernel.cljsrv :as cljsrv]
            [clojupyter.kernel.handle-event.ops
             :as
             ops
             :refer
             [definterceptor s*append-enter-action s*append-leave-action]]
            [clojupyter.kernel.jup-channels :refer [receive!! send!!]]
            [clojupyter.messages :as msgs]
            [clojupyter.plan :as pl :refer [s*bind-state s*when s*when-not]]
            [clojupyter.state :as state]
            [clojupyter.util :as u]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [io.pedestal.interceptor.chain :as ich]
            [io.simplect.compose :refer [C def- p P]]
            [io.simplect.compose.action :as a :refer [action step]]))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; STATE OPS
;;; ------------------------------------------------------------------------------------------------------------------------

(defn- s*append-pending		[v]		(pl/s*append-value :pending v))
(def-  get-pending				(pl/get-value :pending []))

(defn- s*append-stdout		[v]		(pl/s*append-value :stdout v))
(def-  get-stdout				(pl/get-value :stdout []))

(defn- s*append-stderr		[v]		(pl/s*append-value :stderr v))
(def-  get-stderr				(pl/get-value :stderr []))

(defn- s*update-interrupted	[f]		(pl/s*update-value :interrupted? f))
(def-  s*set-interrupted!			(s*update-interrupted (constantly true)))
(def-  s*clear-interrupted!			(s*update-interrupted (constantly false)))
(def-  interrupted?				(pl/get-value :interrupted? false))

(defn- s*update-ns		[f]		(pl/s*update-value :ns f))
(defn- s*set-ns			[v]		(s*update-ns (constantly v)))
(def-  get-ns					(pl/get-value :ns))

(defn- s*set-need-stacktrace	[v]		(pl/s*set-value :need-stacktrace? (boolean v)))
(def-  need-stacktrace?				(pl/get-value :need-stacktrace?))

(defn- s*update-result		[f]		(pl/s*update-value :result (fnil f {})))
(defn- s*set-result		[v]		(s*update-result (constantly v)))
(def-  get-result				(pl/get-value :result))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; STACKTRACE
;;; ------------------------------------------------------------------------------------------------------------------------

(defn collect-stacktrace-strings
  "Return a nicely formatted string."
  [{:keys [stacktrace]}]
  (when stacktrace
    (let [skip-tags	#{"dup" "tooling" "repl"}
          relevant	(filter (C :flags
                                   (p into #{})
                                   (p set/intersection skip-tags)
                                   (p = #{}))
                                stacktrace)
          maxlen	(fn [k] (reduce max (map (C k count) relevant)))
          format-str	(str "%" (maxlen :file) "s: %5d %-" (maxlen :file) "s")]
      (vec (for [{:keys [file line name]} relevant]
             (format format-str file line name))))))

;;; ----------------------------------------------------------------------------------------------------
;;; NREPL EVAL
;;; ----------------------------------------------------------------------------------------------------

(defn s*interpret-nrepl-message
  [{:keys [ns out err ex mime-tagged-value status] :as msg}]
  (C (s*when ns
       (s*set-ns ns))
     (s*when out
       (s*append-stdout out))
     (s*when ex
       (s*update-result (P assoc :ename ex)))
     (s*when err
       (s*append-stderr err))
     (s*when mime-tagged-value
       (s*update-result (P assoc :result mime-tagged-value)))
     (s*when (some #{"interrupted"} status)
       (C (s*update-result (P assoc :ename "interrupted"))
          s*set-interrupted!))))

(defn s*interpret-nrepl-eval-results
  [nrepl-messages]
  (fn [S] (reduce (fn [Σ m] ((s*interpret-nrepl-message m) Σ)) S nrepl-messages)))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; EXECUTE INTERCEPTOR
;;; ------------------------------------------------------------------------------------------------------------------------

(defn- output-actions
  [send-step stream-name ss]
  (doall ;; strict evaluation is necessary here
   (for [s ss]
     (send-step :iopub_port msgs/STREAM (msgs/stream-message-content stream-name s)))))

(def- s*a-l s*append-leave-action)

(definterceptor ic*execute msgs/EXECUTE-REQUEST
  (s*bind-state {:keys [req-message jup cljsrv] :as ctx}
    (let [code (msgs/message-code req-message)
          jup-send (fn [sock-kw msgtype message]
                     (send!! jup sock-kw req-message msgtype message))
          jup-receive (fn [sock-kw]
                        (receive!! jup sock-kw))]
      (s*append-enter-action
       (step #(assoc % :nrepl-eval-result (cljsrv/nrepl-eval cljsrv jup-receive jup-send code))
             {:op :nrepl-eval :code code}))))
  (s*bind-state {:keys [jup req-message req-port nrepl-eval-result] :as ctx}
    (do (assert req-port "ic*execute: req-port not found")
        (s/assert :clojupyter.jupmsg-specs/jupmsg req-message)
        (let [{:keys [nrepl-messages
                      trace-result]}	nrepl-eval-result
              exe-count			(state/execute-count)
              code 				(msgs/message-code req-message)
              eval-interpretation		((s*interpret-nrepl-eval-results nrepl-messages) {})
              {:keys [interrupted?
                      stacktrace-strings
                      result stdout
                      stderr]}		eval-interpretation
              {:keys [ename]}		result
              silent?			(or (msgs/message-silent req-message) (u/code-empty? code))
              hushed?			(u/code-hushed? code)
              store-history?		(if silent? false (msgs/message-store-history? req-message))
              reply				(if ename
                                                  (msgs/execute-reply-content "error" exe-count
                                                                              {:traceback (collect-stacktrace-strings trace-result),
                                                                               :ename ename})
                                                  (msgs/execute-reply-content "ok" exe-count))
              send-step			(fn [sock-kw msgtype message]
                                          (step (fn [S] (send!! jup sock-kw req-message msgtype message) S)
                                                {:message-to sock-kw :msgtype msgtype :message message}))]
          identity
          (C (s*a-l (step identity {:op :no-op :interpretation {:interpretation eval-interpretation,
                                                                :ename ename, :silent? silent?, :hushed? hushed?,
                                                                :store-history? store-history?, :reply reply
                                                                :interrupted? interrupted?}}))
             (s*when interrupted?
               (s*a-l (send-step :iopub_port msgs/STREAM (msgs/stream-message-content "stderr" "*Interrupted*\n"))))
             (s*when stdout
               (s*a-l (apply action (output-actions send-step "stdout" stdout))))
             (s*when stderr
               (s*a-l (apply action (output-actions send-step "stderr" stderr))))
             (s*when-not silent?
               (s*a-l (send-step :iopub_port msgs/EXECUTE-INPUT (msgs/execute-input-msg-content exe-count code))))
             (s*a-l (send-step req-port msgs/EXECUTE-REPLY reply))
             (s*when-not silent?
               (if ename
                 (s*a-l (send-step :iopub_port msgs/ERROR reply))
                 (s*when-not hushed?
                   (s*a-l (send-step :iopub_port msgs/EXECUTE-RESULT
                                     (msgs/execute-result-content (u/parse-json-str (:result result) true) exe-count))))))
             (s*when store-history?
               (s*a-l (step [`state/add-history! code]
                            {:op :add-history, :data code})))
             (s*when-not silent?
               (s*a-l (step [`state/inc-execute-count!]
                            {:op :inc-execute-count}))))))))

(println "execute.clj:			review incremental printing")

(defn eval-request
  ([ctx] (eval-request ctx [ic*execute]))
  ([ctx interceptors]
   (let [ctx' (ops/set-enter-action ctx (action nil))]
     (ich/execute ctx' (conj interceptors ops/action-interceptor)))))

