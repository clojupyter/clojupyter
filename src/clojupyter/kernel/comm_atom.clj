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
  (state-set! [comm-atom comm-state]
    "Sets the value `comm-atom` to be `comm-state` by updating the global state and sending COMM `update`
  message.  `comm-state` must be a map serializable to JSON.  Returns `comm-atom`.")
  (state-update! [comm-atom comm-state]
    "Merges `comm-state` into current value of `comm-atom`.")
  (watch [comm-atom key fn]
    "Add watch to `comm-atom` which will subsequently be called when the value of `comm-atom` is
    updated.  The observer `fn` fn must be a fn of 4 args: a key, the reference, its old-state, its
    new-state, identical to those for `watch` functions (internally CommAtom uses an agent for
    update notification), cf. `add-watch` for details.  Returns `comm-atom`.")
  (unwatch [comm-atom key]
    "Remove watch added with key `key` from `comm-atom`.  Returns `comm-atom`."))

(declare agentfld comm-atom? send-comm-msg! send-comm-open! simple-fmt update-agent!)

(deftype CommAtom
    [comm-state_ jup_ target-name_ reqmsg_ cid_ agent_]

  comm-atom-proto
  (target [_]
    target-name_)
  (comm-id [_]
    cid_)
  (model-ref [_]
    (str "IPY_MODEL_" cid_))
  (origin-message [_]
    reqmsg_)
  (state-set! [comm-atom comm-state]
    (assert (map? comm-state))
    (reset! comm-state_ comm-state)
    (update-agent! comm-atom comm-state)
    (send-comm-msg! comm-atom comm-state)
    comm-atom)
  (state-update! [comm-atom comm-state]
    (assert (map? comm-state))
    (state-set! comm-atom (merge @comm-atom comm-state)))
  (watch [comm-atom key f]
    (assert (fn? f))
    (add-watch (agentfld comm-atom) key f))
  (unwatch [comm-atom key]
    (remove-watch (agentfld comm-atom) key))


  mc/PMimeConvertible
  (to-mime [_]
    (u/json-str {:text/plain
                 ,, (str "[" cid_ "]=" @agent_)
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
    (state-set! comm-atom (.swap ^Atom comm-state_ f)))
  (swap [comm-atom f arg]
    (state-set! comm-atom (.swap ^Atom comm-state_ f arg)))
  (swap [comm-atom f arg1 arg2]
    (state-set! comm-atom (.swap ^Atom comm-state_ f arg1 arg2)))
  (swap [comm-atom f arg1 arg2 args]
    (state-set! comm-atom (.swap ^Atom comm-state_ f arg1 arg2 args)))

  clojure.lang.IDeref
  (deref [_]
    @comm-state_)

  clojure.lang.IFn
  (invoke [comm-state f]
    (f @comm-state)))

(defn- jupfld
  [^CommAtom comm-atom]
  (.-jup_ comm-atom))

(defn- agentfld
  [^CommAtom comm-atom]
  (.-agent_ comm-atom))

(defn- send-comm-msg! [^CommAtom comm-atom, comm-state]
  (assert (map? comm-state))
  (let [content (msgs/update-comm-msg (comm-id comm-atom) msgs/COMM-MSG-UPDATE (target comm-atom) comm-state)]
    (jup/send!! (jupfld comm-atom) :iopub_port (origin-message comm-atom) msgs/COMM-MSG MESSAGE-METADATA content)))

(defn- send-comm-open! [^CommAtom comm-atom, comm-state]
  (assert (map? comm-state))
  (let [content (msgs/comm-open-content (comm-id comm-atom)
                                        {:state comm-state :buffer_paths []}
                                        {:target_name (target comm-atom)})]
    (jup/send!! (jupfld comm-atom) :iopub_port (origin-message comm-atom) msgs/COMM-OPEN MESSAGE-METADATA content)))

(defn- update-agent! [^CommAtom comm-atom, comm-state]
  (let [pre-state @comm-atom]
    (send (agentfld comm-atom) (constantly comm-state))
    (when-let [err (agent-error (agentfld comm-atom))]
      (let [err-info  {:comm-atom comm-atom
                       :pre-state pre-state
                       :new-state comm-state
                       :error-str (str err)
                       :error err}]
        (log/error "update-agent: error in agent" (log/ppstr err-info))
        (log/error "restarting using pre-state")
        (restart-agent (agentfld comm-atom) pre-state)))))

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
       (pp/pprint-logical-block (pp/write @comm-atom)))
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
  [jup req-message target-name comm-id comm-state]
  (assert jup req-message)
  (->CommAtom (atom comm-state) jup target-name req-message comm-id (agent comm-state)))

(defn insert
  [comm-atom]
  (let [comm-id (comm-id comm-atom)]
    (state/comm-state-swap! (P comm-global-state/comm-atom-add comm-id comm-atom))
    (send-comm-open! comm-atom @comm-atom)
    comm-atom))

(defn create-and-insert
  [jup req-message target-name comm-id comm-state]
  (insert (create jup req-message target-name comm-id comm-state)))

(def comm-atom?
  (p instance? CommAtom))
