(ns clojupyter.kernel.comm-atom
  "Implements support for Jupyter COMM objects which is the mechanism for synchronizing state between
  Jupyter clients (Notebook, Lab, others) and kernels such as Clojupyter.  COMM objects serve as the
  basis for interactive controls such as ipywidgets."
  (:require
   [clojupyter.log :as log]
   [clojupyter.kernel.comm-global-state :as comm-global-state]
   [clojupyter.protocol.mime-convertible :as mc]
   [clojupyter.state :as state]
   [clojupyter.util :as u]
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]
   [clojupyter.kernel.jup-channels :as jup]
   [clojupyter.messages :as msgs]
   [io.simplect.compose :refer [def- c C p P >->> >>->]])
  (:import [clojure.lang Atom]))

(def- WIDGET-VERSION-MAJOR		2)
(def- WIDGET-VERSION-MINOR		0)
(def- WIDGET-VERSION-INCR		0)
(def- WIDGET-MESSAGING-PROTO-VERSION	(format "%d.%d.%d" WIDGET-VERSION-MAJOR WIDGET-VERSION-MINOR WIDGET-VERSION-INCR))
(def  MESSAGE-METADATA			{:version WIDGET-MESSAGING-PROTO-VERSION})

;;; ------------------------------------------------------------------------------------------------------------------------
;;; COMM-ATOM
;;; ------------------------------------------------------------------------------------------------------------------------

(defprotocol comm-atom-proto
  (target [comm-atom]
    "The Jupyter target name to which the COMM-ATOM belongs.")
  (comm-id [comm-atom]
    "The Jupyter protocol ID of the COMM-ATOM.")
  (model-ref [comm-atom]
    "The COMM-ATOM's Jupyter Model reference identifier (a string).")
  (origin-message [comm-atom]
    "The message that caused the COMM-ATOM to be created.")
  (sub-state [comm-atom]
    "A map of attributes to be sent to front-end")
  (close! [comm-atom]
      "Removes the comm-atom from the global state and sends COMM-CLOSE to the front end.")
  (send! [comm-atom msg]
    "Sends custom message to front-end. Msg must be a map serializable to JSON.")
  (state-set! [comm-atom comm-state]
    "Sets the value `comm-atom` to be `comm-state` by updating the global state and sending COMM `update`
  message.  `comm-state` must be a map serializable to JSON.  Returns `comm-atom`.")
  (state-update! [comm-atom comm-state]
    "Merges `comm-state` into current value of `comm-atom`.")
  (watch [comm-atom key fn]
    "Add watch to `comm-atom` which will subsequently be called when the value of `comm-atom` is
    updated.  The observer `fn` fn must be a fn of 4 args: a key, the reference, its old-state, its
    new-state, identical to those for `watch` functions. cf. `add-watch` for details.  Returns `comm-atom`.")
  (unwatch [comm-atom key]
    "Remove watch added with key `key` from `comm-atom`.  Returns `comm-atom`.")
  (validate [comm-atom fn]
    "Add validator function `fn` to `comm-atom` which will be called when the `comm-atom` is created or changed.
    The validator fn is a single argument (the `comm-atom` updated state) predicate"))

(declare comm-atom? send-comm-state! send-comm-open! simple-fmt jupfld)

(deftype CommAtom
    [comm-state_ jup_ target-name_ reqmsg_ cid_ viewer-keys]

  comm-atom-proto
  (target [_]
    target-name_)
  (comm-id [_]
    cid_)
  (model-ref [_]
    (str "IPY_MODEL_" cid_))
  (origin-message [_]
    reqmsg_)
  (sub-state [_]
    (select-keys @comm-state_ viewer-keys))
  (close! [comm-atom]
    (let [id (comm-id comm-atom)
          content (msgs/comm-close-content id {})]
      (jup/send!! (jupfld comm-atom) :iopub_port (origin-message comm-atom) msgs/COMM-CLOSE MESSAGE-METADATA content)
      (state/comm-state-swap! (P comm-global-state/comm-atom-remove id)))
      nil)
  (send! [comm-atom msg]
    (assert (map? msg))
    (let [content (msgs/update-comm-msg (comm-id comm-atom) msgs/COMM-MSG-CUSTOM (target comm-atom) msg)]
      (jup/send!! (jupfld comm-atom) :iopub_port (origin-message comm-atom) msgs/COMM-MSG MESSAGE-METADATA content))
      msg)
  (state-set! [comm-atom comm-state]
    (assert (map? comm-state))
    (reset! comm-state_ comm-state)
    (send-comm-state! comm-atom (sub-state comm-atom))
    comm-atom)
  (state-update! [comm-atom comm-state]
    (assert (map? comm-state))
    (let [cur-state @comm-atom
          new-state (merge cur-state comm-state)
          ca-spec (:spec (meta cur-state))
          new-state (if-let [{problems :clojure.spec.alpha/problems} (and ca-spec (s/explain-data ca-spec new-state))]
                      (loop [state new-state
                            problems problems]
                        (if (seq problems)
                          (let [{:keys [in val pred]} (first problems)]
                            (if (and (number? val) (or (= 'float? pred) (= float? pred))) ;; Spec can return the fn or its symbol.
                              (recur (update-in state in float) (rest problems))
                              (recur state (rest problems))))
                          state))
                      new-state)]
      (state-set! comm-atom new-state)))
  (watch [_ key f]
    (assert (fn? f))
    (add-watch comm-state_ key f))
  (unwatch [_ key]
    (remove-watch comm-state_ key))
  (validate [_ f]
    (assert (fn? f))
    (set-validator! comm-state_ f))


  mc/PMimeConvertible
  (to-mime [_]
    (u/json-str {:text/plain
                 ,, (str "[" cid_ "]=" @comm-state_)
                 :application/vnd.jupyter.widget-view+json
                 ,, {:version_major WIDGET-VERSION-MAJOR
                     :version_minor WIDGET-VERSION-MINOR
                     :model_id cid_}}))

  (toString [comm-atom]
    (simple-fmt comm-atom))

  clojure.lang.IAtom
  (compareAndSet [comm-atom old new]
    (if (compare-and-set! comm-state_ old new)
      (do (state-set! comm-atom @comm-state_)
          true)
      false))
  (reset [comm-atom v]
    (state-set! comm-atom v)
    v)
  (swap [comm-atom f]
    (let [state (.swap ^Atom comm-state_ f)]
      (state-set! comm-atom @comm-state_)
      state))
  (swap [comm-atom f arg]
    (let [state (.swap ^Atom comm-state_ f arg)]
      (state-set! comm-atom @comm-state_)
      state))
  (swap [comm-atom f arg1 arg2]
    (let [state (.swap ^Atom comm-state_ f arg1 arg2)]
      (state-set! comm-atom @comm-state_)
      state))
  (swap [comm-atom f arg1 arg2 args]
    (let [state (.swap ^Atom comm-state_ f arg1 arg2 args)]
      (state-set! comm-atom @comm-state_)
      state))

  clojure.lang.IDeref
  (deref [_]
    @comm-state_)

  clojure.lang.IFn
  (invoke [comm-state f]
    (f @comm-state)))

(defn- jupfld
  [^CommAtom comm-atom]
  (.-jup_ comm-atom))

(defn- send-comm-state! [^CommAtom comm-atom, comm-state]
  (assert (map? comm-state))
  (let [content (msgs/update-comm-msg (comm-id comm-atom) msgs/COMM-MSG-UPDATE (target comm-atom) comm-state)]
    (jup/send!! (jupfld comm-atom) :iopub_port (origin-message comm-atom) msgs/COMM-MSG MESSAGE-METADATA content)))

(defn- send-comm-open! [^CommAtom comm-atom, comm-state]
  (assert (map? comm-state))
  (let [content (msgs/comm-open-content (comm-id comm-atom)
                                        {:state comm-state :buffer_paths []}
                                        {:target_name (target comm-atom)})]
    (jup/send!! (jupfld comm-atom) :iopub_port (origin-message comm-atom) msgs/COMM-OPEN MESSAGE-METADATA content)))

(defn- short-comm-id
  [^CommAtom comm-atom]
  (let [comm-id (str (comm-id comm-atom))
        [_ id] (re-find #"^([^-]+)" comm-id)]
    (or id comm-id "??")))

(defn- prefix
  [comm-atom]
  (str "CommAtom@" (short-comm-id comm-atom) ":"))

(defn- simple-fmt
  [comm-atom]
  (str "#<" (prefix comm-atom) (short-comm-id comm-atom) ":" @comm-atom ">"))

(defn- print-prettily
  [^CommAtom comm-atom ^java.io.Writer w]
  (binding [*out* (pp/get-pretty-writer w)]
    (pp/pprint-logical-block
     (print "#<")
     (pp/pprint-logical-block
       (print (prefix comm-atom))
       (pp/pprint-newline :linear)
       (pp/pprint-logical-block (pp/write @comm-atom :length 100)))
     (print ">"))))

(defmethod print-method CommAtom
  [^CommAtom comm-atom ^java.io.Writer w]
  (if pp/*print-pretty*
    (print-prettily comm-atom w)
    (.write w ^String (simple-fmt comm-atom))))

(defmethod pp/simple-dispatch CommAtom
  [^CommAtom comm-atom]
  (if pp/*print-pretty*
    (print-prettily comm-atom *out*)
    (print (simple-fmt comm-atom))))

(defn create
  [jup req-message target-name comm-id viewer-keys comm-state]
  (assert jup req-message)
  (->CommAtom (atom comm-state) jup target-name req-message comm-id viewer-keys))

(defn insert
  [comm-atom]
  (let [comm-id (comm-id comm-atom)]
    (state/comm-state-swap! (P comm-global-state/comm-atom-add comm-id comm-atom))
    (send-comm-open! comm-atom (sub-state comm-atom))
    comm-atom))

(defn create-and-insert
  [jup req-message target-name comm-id viewer-keys comm-state]
  (insert (create jup req-message target-name comm-id viewer-keys comm-state)))

(def comm-atom?
  (p instance? CommAtom))

(defn open?
  [comm-atom]
  (let [id (comm-id comm-atom)
        S (state/comm-state-get)]
    (comm-global-state/known-comm-id? S id)))

(def closed? (complement open?))    
