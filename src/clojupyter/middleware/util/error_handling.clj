(ns clojupyter.middleware.util.error-handling
  "Utilities to safely reply to op requests and help deal with the
  errors/exceptions that might arise from doing so."
  (:refer-clojure :exclude [error-handler])
  (:require [clojure.set :as set]
            [clojure.stacktrace :as stacktrace]
            [clojure.tools.nrepl.transport :as transport]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojupyter.middleware.stacktrace :as cider-stacktrace]
            [clojure.walk :as walk])
  (:import java.io.InputStream
           clojure.lang.RT))

;;; UTILITY FUNCTIONS

(defn error-summary
  "Takes a `java.lang.Exception` as `ex` and returns a map summarizing
  the exception. If present, the varargs are converted to a set and
  used as the value for the :status key."
  [ex & statuses]
  (merge {:ex (str (class ex))
          :err (with-out-str (stacktrace/print-cause-trace ex))}
         (when statuses {:status (set statuses)})))

(defn pp-stacktrace
  "Takes a `java.lang.Exception` as `ex` and a pretty-print function
  as `pprint-fn`, then returns a pretty-printed version of the
  exception that can be rendered by CIDER's stacktrace viewer."
  [ex pprint-fn]
  {:pp-stacktrace (cider-stacktrace/analyze-causes ex pprint-fn)})

(defn base-error-response
  "Takes a CIDER-nREPL message as `msg`, an Exception `ex`, and a
  non-collection vararg of `statuses`.  This will return the standard
  response for CIDER-nREPL sync-op errors that can be rendered by
  CIDER's stacktrace viewer. N.B., statuses such as `:done` and
  `:<op-name>-error` are NOT automatically added"
  [msg ex & statuses]
  (response-for msg (merge (apply error-summary ex statuses)
                           (pp-stacktrace ex (:pprint-fn msg)))))

(defn- normalize-status
  "Accepts various representations of an nREPL reply message's status
  and normalizes them to a set. Accepts and normalizes as follows:

  - nil => empty set
  - set => returns the set
  - coll => set representation of coll's items
  - single item (kw, string, int, etc.) => set containing single item"
  [status]
  (cond (nil? status) #{}
        (set? status) status
        (coll? status) (set status)
        :else (set [status])))

(defn- selector
  "Selector used for dispatch on both the `op` and `error` handler
  multimethods. The handlers expect one of the following:

  - map => A map that will form the basis of the nREPL reply
    message.
  - fn (NOT ifn's) => A fn with arity 1 for ops and 2 for errors, must
    return a map that will form the basis of the nREPL reply message.
  - coll => The coll will be turned into a set which is used as the
    reply message's status.
  - kw => Wrapped in a set and used as the reply message's status.
  - ::default => used as the default error handler, which simply adds
    a reasonably named keyword (ie, `:<op>-error`) to the status."
  [input & _]
  (cond (= ::default input) :default
        (fn? input) :function
        (map? input) :inline-reply
        (coll? input)  :status-coll
        (keyword? input) :status-item))

(defn- shallow-bencodable?
  "Returns false if `item`'s type can't be bencoded as defined by the
  algorithm in `clojure.tools.nrepl.bencode/write-bencode`. Does not
  examine the elements of a collection to ensure that the enclosed
  elements are also bencodable, and so you probably actually want to
  use `deep-bencodable-or-fail` or write something similar."
  [item] 
  (cond
    (instance? (RT/classForName "[B") item) :bytes
    (instance? InputStream item) :input-stream
    (integer? item) :integer
    (string? item)  :string
    (symbol? item)  :named
    (keyword? item) :named
    (map? item)     :map
    (or (nil? item) (coll? item) (.isArray (class item))) :list
    :else false))

(defn- deep-bencodable-or-fail
  "Walks through the data structure provided by `item` and returns
  true if it -- and all nested elements -- are bencodable as defined
  by the algorithm in `clojure.tools.nrepl.bencode/write-bencode`. If
  any part of `input` is not bencodable, will throw an
  `IllegalArgumentException`. See `cider-nrepl` bug #332 at
  https://github.com/clojure-emacs/cider-nrepl/issues/332 for further
  details."
  [item]
  (walk/prewalk
   #(if (shallow-bencodable? %)
      %
      (throw (IllegalArgumentException. (format "Can't bencode %s: %s" (.getName (class %)) %))))
   item)
  true) ;; Need to actually return truthy since `nil` is bencodable

;;; ERROR HANDLER - see selector docstring

(defmulti error-handler selector)

(defmethod error-handler :inline-reply [answer msg e]
  (let [reply           (base-error-response msg e)
        terminal-status (set/union #{:done} (normalize-status (:status answer)))]
    (response-for msg (assoc reply :status terminal-status))))

(defmethod error-handler :function [f msg e]
  (error-handler (f msg e) msg e))

(defmethod error-handler :status-coll [statuses msg e]
  (error-handler {:status (set statuses)} msg e))

(defmethod error-handler :status-item [status msg e]
  (error-handler {:status (set [status])} msg e))

(defmethod error-handler :default [_ msg e]
  (error-handler (keyword (str (:op msg) "-error")) msg e))

;;; OP HANDLER - see selector docstring

(defmulti op-handler selector)

(defmethod op-handler :inline-reply
  [answer msg]
  (let [terminal-status (set/union #{:done} (normalize-status (:status answer)))]
    ;; Check the bencodability of `answer` (the current transport can
    ;; only send non-bencodable data if stored under the `:value`
    ;; key). If non-bencodable elements exist, throw an exception.
    (deep-bencodable-or-fail (dissoc answer :value))
    ;; If bencodable, create a terminated reply message.
    (response-for msg (assoc answer :status terminal-status))))

(defmethod op-handler :function [f msg]
  (op-handler (f msg) msg))

(defmethod op-handler :status-coll [statuses msg]
  (op-handler {:status (set statuses)} msg))

(defmethod op-handler :status-item [status msg]
  (op-handler {:status (set [status])} msg))

;;; SAFE TRANSPORT WRAPPER

(defmacro with-safe-transport
  "This will safely handle all the transport calls mapped out in the
  `wrap-<middleware>` functions. All checked exceptions will be
  caught, analyzed by the `cider.nrepl.middleware.stacktrace`
  middleware, and an error message will be returned to the client with
  a stacktrace renderable by the default CIDER stacktrace viewer.
  Takes the default pass-through handler as well as a list of pairings
  between op names and actions used to process the ops as
  varargs. Actions can either be expressed as a 2-item vector with the
  head being the op-action and the tail being the error-action, or if
  the default error handler is sufficient, then the op name can be
  paired directly to the op-action.

  Actions can be functions, maps, non-associate collections, and
  single items such as kw's, strings, numbers, etc. The utilization of
  each type is discussed above in the `selector` method."
  [pass-through & pairings]
  `(fn [{op# :op transport# :transport :as msg#}]
     (if-let [action# (get (hash-map ~@pairings) op#)]
       (let [[op-action# err-action#]  (if (vector? action#) action# [action# ::default])]
         (try (transport/send transport# (op-handler op-action# msg#))
              (catch Exception e# (transport/send transport# (error-handler err-action# msg# e#)))))
       (~pass-through msg#))))
