(ns clojupyter.kernel.handle-event.shared-ops
  (:require [clojupyter.test-shared :as ts]
            [io.simplect.compose.action :as a]))

(defn empty-action?
  [a]
  (-> a a/steps count zero?))

(defn successful-action?
  [a]
  (and (a/completed? a) (a/success? a)))

(defn single-step-action?
  [a]
  (-> a a/steps count (= 1)))

(defn first-spec
  [a]
  (-> a a/step-specs first))

(defn actions-are-only-diff
  [a b]
  (= (dissoc a :enter-action :leave-action) (dissoc b :enter-action :leave-action)))
