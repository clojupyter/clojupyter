(ns clojupyter.middleware.pprint
  (:require [clojure.pprint :refer [pprint *print-right-margin*]]
            [clojure.tools.nrepl.middleware.interruptible-eval :refer [*msg*]]
            [clojure.tools.nrepl.middleware.pr-values :refer [pr-values]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.middleware.session :as session]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.transport :as transport]
            [fipp.edn :as fipp]
            [puget.printer :as puget])
  (:import clojure.tools.nrepl.transport.Transport))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Modified from cider-nrepl/src/cider/nrepl/middleware/pprint.clj

(defn as-sym
  [x]
  (cond
    (symbol? x) x
    (string? x) (if-let [[_ ns sym] (re-matches #"(.+)/(.+)" x)]
                  (symbol ns sym)
                  (symbol x))))

(defn fipp-pprint [object]
  (fipp/pprint object {:width (or *print-right-margin* 72)}))

(defn puget-pprint [object]
  (puget/pprint object {:width (or *print-right-margin* 72)
                        :seq-limit *print-length*}))

(defn- resolve-pprint-fn
  [sym]
  (let [var (some-> sym as-sym resolve)]

    (when (or (nil? var) (not (var? var)))
      (throw (IllegalArgumentException. (format "%s is not resolvable as a var" sym))))

    @var))

(defn wrap-pprint-fn
  "Middleware that provides a common interface for other middlewares that need
  to perform customisable pretty-printing.

  A namespace-qualified name of the function to be used for printing can be
  optionally passed in the `:pprint-fn` slot, the default value being
  `clojure.pprint/pprint`.

  The `:pprint-fn` slot will be replaced with a closure that calls the given
  printing function with `*print-length*`, `*print-level*`, `*print-meta*`, and
  `clojure.pprint/*print-right-margin*` bound to the values of the
  `:print-length`, `:print-level`, `:print-meta`, and `:print-right-margin`
  slots respectively.

  Middlewares further down the stack can then look up the `:pprint-fn` slot and
  call it where necessary."
  [handler]
  (fn [{:keys [pprint-fn print-length print-level print-meta print-right-margin session]
        :or {pprint-fn 'clojure.pprint/pprint}
        :as msg}]
    (handler (assoc msg :pprint-fn (fn [object]
                                     (binding [*print-length* (or print-length (get @session #'*print-length*))
                                               *print-level* (or print-level (get @session #'*print-level*))
                                               *print-meta* (or print-meta (get @session #'*print-meta*))
                                               *print-right-margin* (or print-right-margin (get @session #'*print-right-margin*))]
                                       ((resolve-pprint-fn pprint-fn) object)))))))

(def wrap-pprint-fn-optional-arguments
  {"pprint-fn" "The namespace-qualified name of a single-arity function to use for pretty-printing. Defaults to `clojure.pprint/pprint`."
   "print-length" "Value to bind to `*print-length*` when pretty-printing. Defaults to the value bound in the current REPL session."
   "print-level" "Value to bind to `*print-level*` when pretty-printing. Defaults to the value bound in the current REPL session."
   "print-meta" "Value to bind to `*print-meta*` when pretty-printing. Defaults to the value bound in the current REPL session."
   "print-right-margin" "Value to bind to `clojure.pprint/*print-right-margin*` when pretty-printing. Defaults to the value bound in the current REPL session."})

(set-descriptor!
 #'wrap-pprint-fn
 {:requires #{#'session/session}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- pprint-writer
  [{:keys [session transport] :as msg}]
  (#'session/session-out :pprint-out (:id (meta session)) transport))

(defn pprint-reply
  [{:keys [pprint-fn session transport] :as msg} response]
  (with-open [writer (pprint-writer msg)]
    ;; Binding `*msg*` sets the `:id` slot when printing to an nREPL session
    ;; PrintWriter (as created by `pprint-writer`), which the client requires to
    ;; handle the response correctly.
    (binding [*msg* msg *out* writer]
      (let [value (:value response)
            print-fn (if (string? value) println pprint-fn)]
        (print-fn value))))
  (transport/send transport (response-for msg :pprint-sentinel {})))

(defn pprint-transport
  [{:keys [right-margin ^Transport transport] :as msg}]
  (reify Transport
    (recv [this] (.recv transport))
    (recv [this timeout] (.recv transport timeout))
    (send [this response]
      (when (contains? response :value)
        (pprint-reply msg response))
      (.send transport (dissoc response :value)))))

(defn wrap-pprint
  "Middleware that adds a pretty-printing option to the eval op.
  Passing a non-nil value in the `:pprint` slot will cause eval to call
  clojure.pprint/pprint on its result. The `:right-margin` slot can be used to
  bind `*clojure.pprint/*print-right-margin*` during the evaluation."
  [handler]
  (fn [{:keys [op pprint] :as msg}]
    (handler (cond-> msg
               (and (= op "eval") pprint)
               (assoc :transport (pprint-transport msg))))))

(set-descriptor!
 #'wrap-pprint
  {:requires #{"clone" #'pr-values #'wrap-pprint-fn}
   :expects #{"eval"}
   :handles
   {"pprint-middleware"
    {:doc "Enhances the `eval` op by adding pretty-printing. Not an op in itself."
     :optional (merge wrap-pprint-fn-optional-arguments
                      {"pprint" "If present and non-nil, pretty-print the result of evaluation."})}}})
