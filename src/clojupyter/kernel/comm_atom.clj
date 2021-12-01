(ns clojupyter.kernel.comm-atom
  "Implements support for Jupyter COMM objects which is the mechanism for synchronizing state between
  Jupyter clients (Notebook, Lab, others) and kernels such as Clojupyter.  COMM objects serve as the
  basis for interactive controls such as ipywidgets."
  (:require
   [clojupyter.log :as log]
   [clojupyter.protocol.mime-convertible :as mc]
   [clojupyter.state :as state]
   [clojupyter.util :as u]
   [clojupyter.util-actions :as u!]
   [clojure.pprint :as pp]
   [clojupyter.kernel.jup-channels :as jup]
   [clojupyter.messages :as msgs]
   [io.simplect.compose :refer [def-]]))

(def- WIDGET-VERSION-MAJOR		2)
(def- WIDGET-VERSION-MINOR		0)
(def- WIDGET-VERSION-INCR		0)
(def- WIDGET-MESSAGING-PROTO-VERSION	(format "%d.%d.%d" WIDGET-VERSION-MAJOR WIDGET-VERSION-MINOR WIDGET-VERSION-INCR))
(def  MESSAGE-METADATA			{:version WIDGET-MESSAGING-PROTO-VERSION})

;;; ------------------------------------------------------------------------------------------------------------------------
;;; COMM-ATOM
;;; ------------------------------------------------------------------------------------------------------------------------

(defprotocol comm-atom-proto
  (sync-state [comm-atom]
    "A map of attributes to be sent to front-end")
  (close! [comm-atom]
    "Removes the comm-atom from the global state and sends COMM-CLOSE to the front end."))

(declare comm-atom? send-comm-state! send-comm-open! simple-fmt jsonable?)

(deftype CommAtom
    [comm-id target sync-keys comm-state_]

  comm-atom-proto
  (sync-state [_]
    (select-keys @comm-state_ sync-keys))
  (close! [comm-atom]
    (let [content (msgs/comm-close-content comm-id {})
          jup (:jup @state/STATE)
          {origin-message :req-message :or {origin-message {:header {}}}} (first (:cur-ctx @state/STATE))]
      (jup/send!! jup :iopub_port origin-message msgs/COMM-CLOSE MESSAGE-METADATA content)
      (swap! state/STATE update :comms dissoc comm-id)
      nil))

  java.io.Closeable
  (close [comm-atom]
    (msgs/leaf-paths comm-atom? #(.close %) (sync-state comm-atom))
    (close! comm-atom))

  mc/PMimeConvertible
  (to-mime [_]
    (u/json-str {:text/plain
                 ,, (str "[" comm-id "]=" (:_model_name @comm-state_))
                 :application/vnd.jupyter.widget-view+json
                 ,, {:version_major WIDGET-VERSION-MAJOR
                     :version_minor WIDGET-VERSION-MINOR
                     :model_id comm-id}}))

  (toString [comm-atom]
    (simple-fmt comm-atom))

  clojure.lang.IAtom
  (compareAndSet [comm-atom old new]
    (assert (jsonable? (select-keys new sync-keys)))
    (if (= @comm-atom old)
      (do (reset! comm-atom new)
          true)
      false))
  (reset [comm-atom v]
    (assert (jsonable? (select-keys v sync-keys)))
    (send-comm-state! comm-atom (select-keys v sync-keys))
    (reset! comm-state_ v)
    v)
  (swap [comm-atom f]
    (let [state @comm-atom
          n-state (f state)]
      (when (compare-and-set! comm-atom state n-state)
        n-state)))
  (swap [comm-atom f arg]
    (let [state @comm-atom
          n-state (f state arg)]
      (when (compare-and-set! comm-atom state n-state)
        n-state)))
  (swap [comm-atom f arg1 arg2]
    (let [state @comm-atom
          n-state (f state arg1 arg2)]
      (when (compare-and-set! comm-atom state n-state)
          n-state)))
  (swap [comm-atom f arg1 arg2 args]
    (let [state @comm-atom
          n-state (apply f (cons state (cons arg1 (cons arg2 args))))]
      (when (compare-and-set! comm-atom state n-state)
        n-state)))

  clojure.lang.IDeref
  (deref [_]
    @comm-state_)

  clojure.lang.IRef
  (getValidator [_]
    (get-validator comm-state_))
  (setValidator [_ p]
    (set-validator! comm-state_ p))

  (getWatches [_]
    (.getWatches comm-state_))
  (addWatch [_ key f]
     (add-watch comm-state_ key f))
  (removeWatch [_ key]
    (remove-watch comm-state_ key)))

(defn- send-comm-state! [^CommAtom comm-atom, comm-state]
  (assert (and (jsonable? comm-state) (map? comm-state)))
  (log/debug "Sending comm-state for widget with comm_id " (.-comm-id comm-atom))
  (let [content (msgs/comm-msg-content (.-comm-id comm-atom) {:method "update" :state comm-state})
        jup (:jup @state/STATE)
        {origin-message :req-message :or {origin-message {:header {}}}} (first (:cur-ctx @state/STATE))]
    (jup/send!! jup :iopub_port origin-message msgs/COMM-MSG MESSAGE-METADATA content)))

(defn- send-comm-open! [^CommAtom comm-atom, comm-state]
  (assert (and (jsonable? comm-state) (map? comm-state)))
  (let [content (msgs/comm-open-content (.-comm-id comm-atom)
                                        {:state comm-state}
                                        {:target_name (.-target comm-atom)})
        jup (:jup @state/STATE)
        {origin-message :req-message :or {origin-message {:header {}}}} (first (:cur-ctx @state/STATE))]
    (log/debug "Sending comm-open for widget with comm-id " (.-comm-id comm-atom))
    (jup/send!! jup :iopub_port origin-message msgs/COMM-OPEN MESSAGE-METADATA content)))


(defn- short-comm-id
  [^CommAtom comm-atom]
  (let [comm-id (str (.-comm-id comm-atom))
        [_ id] (re-find #"^([^-]+)" comm-id)]
    (or id comm-id "??")))

(defn- prefix
  [comm-atom]
  (str "CommAtom@" (short-comm-id comm-atom) ":"))

(defn- simple-fmt
  [comm-atom]
  (str "#<" (prefix comm-atom) (:_model_name @comm-atom) ">"))

(defn- print-prettily
  [^CommAtom comm-atom ^java.io.Writer w]
  (binding [*out* (pp/get-pretty-writer w)]
    (pp/pprint-logical-block
     (print "#<")
     (pp/pprint-logical-block
       (print (prefix comm-atom))
       (pp/pprint-newline :linear)
       (pp/pprint-logical-block (pp/write @comm-atom)))
     (print ">"))))

(defmethod print-method CommAtom
  [^CommAtom comm-atom ^java.io.Writer w]
  (.write w ^String (simple-fmt comm-atom)))

(defmethod pp/simple-dispatch CommAtom
  [^CommAtom comm-atom]
  (if pp/*print-pretty*
    (print-prettily comm-atom *out*)
    (print (simple-fmt comm-atom))))

(defn create
  [comm-id target sync-keys comm-state]
  (->CommAtom comm-id target sync-keys (atom comm-state)))

(defn insert
  [comm-atom]
  (let [comm-id (.-comm-id comm-atom)]
    (send-comm-open! comm-atom (sync-state comm-atom))
    (swap! state/STATE update :comms assoc comm-id comm-atom)
    comm-atom))

(defn create-and-insert
  [comm-id target sync-keys comm-state]
  (insert (create comm-id target sync-keys comm-state)))

(def comm-atom?
  (p instance? CommAtom))

(defn open?
  [comm-atom]
  (let [id (.-comm-id comm-atom)]
    (contains? (:comms @state/STATE)) id))

(def closed? (complement open?))

(defn jsonable?
  [v]
  (or (string? v)
      (keyword? v) ;; Implicitly cast to str
      (symbol? v) ;; Implicitly cast to str
      (integer? v)
      (float? v)
      (rational? v) ;; Implicitly cast to double
      (boolean? v)
      (nil? v)
      (bytes? v)
      (comm-atom? v)
      (and (vector? v) (every? jsonable? v))
      (and (list? v) (every? jsonable? v)) ;; Implicitly cast to vector
      (and (instance? clojure.lang.LazySeq v) (every? jsonable? v)) ;; Implicitly cast to vector
      (and (map? v)
           (every? #(or (string? %) (keyword? %) (symbol? %)) (keys v))
           (every? jsonable? (vals v)))))

(defn base-widget
  ([state] (base-widget state (u!/uuid)))
  ([state comm-id]
   (let [target "jupyter.widget"
         sync-keys (set (keys state))]
     (create-and-insert comm-id target sync-keys state))))
