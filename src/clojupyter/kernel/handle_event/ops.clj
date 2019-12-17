(ns clojupyter.kernel.handle-event.ops
  (:require [clojupyter.kernel.jup-channels :refer [send!!]]
            [clojupyter.messages :as msgs]
            [io.pedestal.interceptor :as ic]
            [io.pedestal.interceptor.chain :as ich]
            [io.simplect.compose :refer [p P]]
            [io.simplect.compose.action :as a :refer [action step]]))

(use 'clojure.pprint)

(defn- append-action		[ctx k a]	(update-in ctx [k] (P action a)))
(defn- get-action		[ctx k]		(get ctx k (action nil)))
(defn- set-action		[ctx k a]	(assoc ctx k a))

(defn- append-enter-action	[ctx a]		(append-action ctx :enter-action a))
(defn- append-leave-action	[ctx a]		(append-action ctx :leave-action a))
(defn get-enter-action		[ctx]		(get-action ctx :enter-action))
(defn set-enter-action		[ctx a]		(set-action ctx :enter-action a))
(defn get-leave-action		[ctx]		(get-action ctx :leave-action))
(defn set-leave-action		[ctx a]		(set-action ctx :leave-action a))

(defn s*set-response
  [msgtype message]
  (fn [{:keys [jup req-message req-port] :as ctx}]
    (assert req-port "s*set-response: req-port not found")
    (set-leave-action ctx (action (step (fn [S]
                                          (send!! jup req-port req-message msgtype message)
                                          S)
                                        {:message-to req-port :msgtype msgtype :message message})))))

(defn s*append-enter-action
  [a]
  (fn [ctx] (append-enter-action ctx a)))

(defn s*append-leave-action
  [a]
  (fn [ctx] (append-leave-action ctx a)))

(defn call-if-msgtype
  [f msgtype {:keys [req-message] :as ctx}]
  (if (= msgtype (msgs/message-msg-type req-message))
    (f ctx)
    ctx))

(defmacro definterceptor
  [nm msgtype enter-fn leave-fn]
  (let [enter (-> nm (str "-enter") symbol)
        leave (-> nm (str "-leave") symbol)]
    `(do
       (def ~enter ~enter-fn)
       (def ~leave ~leave-fn)
       (def ~nm (ic/interceptor
                 {:handles-message ~(-> msgtype resolve var-get)
                  :name ~(->> nm name (keyword (-> *ns* ns-name name)))
                  :enter (p call-if-msgtype ~enter ~msgtype)
                  :leave (p call-if-msgtype ~leave ~msgtype)})))))

(def action-interceptor
  (ic/interceptor
   {:name ::action-interceptor
    :enter (fn [ctx]
             (let [action (get-enter-action ctx)
                   action-result (action)]
               (if (a/success? action-result)
                 (-> (set-enter-action ctx action-result)
                     (merge (a/output action-result)))
                 (throw (ex-info (str "Action failed: " action-result)
                          {:action action, :action-result action-result})))))}))

(defn call-interceptor
  ([input interceptors]
   (call-interceptor input (conj interceptors action-interceptor) action-interceptor))
  ([input interceptors response-interceptor]
   (ich/execute input (conj interceptors response-interceptor))))
