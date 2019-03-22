(ns clojupyter.middleware
  (:require
   [clojupyter.middleware.base		:as base]
   [clojupyter.middleware.comm		:as comm]
   [clojupyter.middleware.complete	:as complete]
   [clojupyter.middleware.execute	:as execute]
   [clojupyter.middleware.history	:as history]
   [clojupyter.middleware.inspect	:as inspect]
   [clojupyter.middleware.log-traffic	:as log-traffic]
   ))

;;; ----------------------------------------------------------------------------------------------------
;;; MIDDLEWARE
;;; ----------------------------------------------------------------------------------------------------

(def wrap-busy-idle			base/wrap-busy-idle)
(def wrap-kernel-info-request		base/wrap-kernel-info-request)
(def wrap-kernel-info-request		base/wrap-kernel-info-request)
(def wrap-shutdown-request		base/wrap-shutdown-request)
(def wrap-shutdown-request		base/wrap-shutdown-request)
(def wrapin-bind-msgtype		base/wrapin-bind-msgtype)
(def wrapin-verify-request-bindings	base/wrapin-verify-request-bindings)
(def wrapout-construct-jupyter-message	base/wrapout-construct-jupyter-message)
(def wrapout-encode-jupyter-message	base/wrapout-encode-jupyter-message)

(def wrap-print-messages		log-traffic/wrap-print-messages)


(def wrap-comm-info			comm/wrap-comm-info)
(def wrap-comm-msg			comm/wrap-comm-msg)
(def wrap-comm-open			comm/wrap-comm-open)

(def wrap-complete-request		complete/wrap-complete-request)
(def wrap-is-complete-request		complete/wrap-is-complete-request)

(def wrap-execute-request		execute/wrap-execute-request)

(def wrap-history-request		history/wrap-history-request)

(def wrap-inspect-request		inspect/wrap-inspect-request)

;;; ----------------------------------------------------------------------------------------------------
;;; HANDLERS
;;; ----------------------------------------------------------------------------------------------------

(def not-implemented-handler		base/not-implemented-handler)

;;; ----------------------------------------------------------------------------------------------------
;;; MIDDLEWARE
;;; ----------------------------------------------------------------------------------------------------

(def wrap-base-handlers
  (comp wrap-execute-request
        wrap-comm-msg
        wrap-comm-info
        wrap-comm-open
        wrap-is-complete-request
        wrap-complete-request
        wrap-kernel-info-request
        wrap-inspect-request
        wrap-history-request
        wrap-shutdown-request))

(def wrap-jupyter-messaging
  (comp wrapout-encode-jupyter-message
        wrapout-construct-jupyter-message))

(def default-wrapper
  (comp wrapin-verify-request-bindings
        wrapin-bind-msgtype
        wrap-print-messages
        wrap-jupyter-messaging
        wrap-busy-idle
        wrap-base-handlers))

(def default-handler
  (default-wrapper not-implemented-handler))
