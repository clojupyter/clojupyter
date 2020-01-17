(ns clojupyter.zmq-specs
  (:require [clojure.spec.alpha :as s]
            [io.simplect.compose :refer [C p]])
  (:import [org.zeromq ZContext ZMQ$Socket]))

(s/def ::zcontext			(p instance? ZContext))
(s/def ::zsocket			(p instance? ZMQ$Socket))
(s/def ::two-tuple			(s/and vector? (C count (p = 2))))
