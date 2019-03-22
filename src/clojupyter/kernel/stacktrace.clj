(ns clojupyter.kernel.stacktrace
  (:require
   [clojupyter.misc.config		:as cfg]))

(def ^:dynamic ^:private *stacktrace-printing-enabled* false)

(defn set-print-stacktraces!
  "It appears that `cider-nrepl` sometimes triggers an uncaught
  exception.  Until the root cause of the problem is found and fixed,
  printing stacktrace can be switched on and off.

  If called with a truthy values *enables* printing of
  stacktraces (the default), otherwise disables it.

  Returns `true` if enabled, `false` otherwise."
  [enable?]
  (alter-var-root #'*stacktrace-printing-enabled* (constantly (if enable? true false))))

(defn printing-stacktraces?
  "Returns `true` if stacktraces are printed, `false` otherwise."
  []
  *stacktrace-printing-enabled*)

(defn init!
  []
  (set-print-stacktraces! (cfg/print-stacktraces?)))

