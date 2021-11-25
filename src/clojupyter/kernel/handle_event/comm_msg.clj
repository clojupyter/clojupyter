(ns clojupyter.kernel.handle-event.comm-msg
  (:require
   [clojupyter.kernel.comm-atom :as ca]
   [clojupyter.kernel.jup-channels :as jup]
   [clojupyter.log :as log]
   [clojupyter.messages :as msgs]
   [clojupyter.messages-specs :as msp]
   [clojupyter.state :as state]
   [clojupyter.util :as u]
   [clojupyter.util-actions :as u!]
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :refer [instrument]]
   [clojure.string :as str]
   [io.simplect.compose :refer [def- c C p P]]
   [io.simplect.compose.action :as a :refer [action step side-effect]]))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; MISC INTERNAL
;;; ------------------------------------------------------------------------------------------------------------------------

(def- IOPUB :iopub_port)
(def NO-OP-ACTION
  (action (step `[list] {:op :no-op})))

(defn- jupmsg-spec
  ([port msgtype content]
   (jupmsg-spec port msgtype nil content))
  ([port msgtype metadata content]
   (merge {:op :send-jupmsg, :port port, :msgtype msgtype, :content content}
          (when metadata
            {:metadata metadata}))))

(defn insert-binary-buffers
  [{:keys [state buffer_paths] :as data} buffers]
  (assert state)
  (if (and (seq buffer_paths) (seq buffers))
    (let [buffers (.-buffers buffers)
          [paths _] (msgs/leaf-paths string? keyword buffer_paths)
          repl_map (reduce merge (map hash-map paths buffers))]
      (msgs/insert-paths state repl_map))
    state))

(defn insert-comm-atoms
  [global-state comm-state]
  (first (msgs/leaf-paths #(and (string? %) (re-find #"^IPY_MODEL_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$" %))
                          #(let [[_ id] (re-find #"^IPY_MODEL_([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$" %)]
                            (get global-state (str/lower-case id)))
                          comm-state)))

(defmulti ^:private calc*
  (fn [msgtype _ _] msgtype))

(defmethod calc* :default
  [msgtype state ctx]
  (log/error "Unhandled message type: " msgtype)
  [NO-OP-ACTION state])

;;; ------------------------------------------------------------------------------------------------------------------------
;;; COMM MESSAGES - HANDLED PER `:method` field
;;; ------------------------------------------------------------------------------------------------------------------------

(defmulti handle-comm-msg
  "The `:method` field of COMM messages determines what needs to happen."
  (fn [method _ _] method))

(defmethod handle-comm-msg :default
  [method S ctx]
  (let [msgstr (str "HANDLE-COMM-MSG - bad method: '" method "'.")
        data {:S S, :ctx ctx}]
    (log/error msgstr data)
    [NO-OP-ACTION S]))

(defn handle-comm-msg-unknown
  [ctx S comm_id]
  (log/info (str "COMM - unknown comm-id: " comm_id))
  [NO-OP-ACTION S])

(defmethod handle-comm-msg msgs/COMM-MSG-REQUEST-STATE
  [_ S {:keys [req-message] :as ctx}]
  (let [jup (:jup @state/STATE)
        _ (assert (and req-message jup))
        _ (log/debug "Received COMM:REQUEST-STATE")
        method (msgs/message-comm-method req-message)
        comm-id (msgs/message-comm-id req-message)
        present? (contains? S comm-id)]
    (assert method)
    (assert comm-id)
    (assert (= method msgs/COMM-MSG-REQUEST-STATE))
    (if present?
      (let [comm-atom (get S comm-id)
            content (msgs/comm-msg-content comm-id {:method "update" :state (.sync-state comm-atom)})
            A (action (step [`jup/send!! jup IOPUB req-message msgs/COMM-MSG ca/MESSAGE-METADATA content]
                            (jupmsg-spec IOPUB msgs/COMM-MSG ca/MESSAGE-METADATA content)))]
        [A S])
      (handle-comm-msg-unknown ctx S comm-id))))

(defmethod handle-comm-msg msgs/COMM-MSG-UPDATE
  [_ S {:keys [req-message] :as ctx}]
  (assert req-message)
  (log/debug "Received COMM:UPDATE")
  (let [{{:keys [comm_id] {:keys [method state buffer_paths] :as data} :data} :content buffers :buffers} req-message]
    (assert comm_id)
    (assert state)
    (if-let [comm-atom (get S comm_id)]
      (let [state (insert-binary-buffers data buffers)
            state (insert-comm-atoms S state)
            A (action (side-effect #(swap! (.-comm-state_ comm-atom) merge state)
                                    {:op :update-agent :comm-id comm_id :new-state state}))]
          (log/debug "COMM:UPDATE Computed action: " A " and state: " S)
          (log/debug "COMM:UPDATE state: " state)
          (log/debug "COMM:UPDATE comm-id: " comm_id)
          [A nil])
      (do (log/warn "Received COMM-UPDATE with unknown comm_id: " comm_id " and state: " state)
          (handle-comm-msg-unknown ctx S comm_id)))))

(defmethod handle-comm-msg msgs/COMM-MSG-CUSTOM
  [_ S {:keys [req-message] :as ctx}]
  (assert req-message)
  (let [{{:keys [comm_id] {{event :event :as content} :content :keys [method]} :data} :content} req-message
        buffers (msgs/message-buffers req-message)]
    (assert comm_id)
    (assert (= method msgs/COMM-MSG-CUSTOM))
    (log/debug "Received COMM-CUSTOM -- comm_id: " comm_id " content: " content "buffers: " buffers)
    (if-let [comm-atom (get S comm_id)]
      (let [k (keyword (str "on-" event))
            state @comm-atom
            callback (get-in state [:callbacks k] (constantly nil))]
        (if (fn? callback)
          (let [A (action (side-effect #(callback comm-atom content buffers) {:op :callback :comm-id comm_id :content content}))]
            [A nil])
          ;; If callback attr is not a fn, we assume it's a collection of fns.
          (let [call (fn [] (doseq [f callback]
                              (f comm-atom content buffers)))
                A (action (side-effect call {:op :callback :comm-id comm_id :content content}))]
            [A nil])))
      (handle-comm-msg-unknown ctx S comm_id))))

(defmethod calc* msgs/COMM-MSG
  [_ S {:keys [req-message] :as ctx}]
  (assert req-message)
  (let [method (msgs/message-comm-method req-message)]
    (assert method)
    (try (handle-comm-msg method S ctx)
      (catch Exception ex (log/error "Error while handling comm-msg: " (.getMessage ex))))))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; COMM-OPEN, COMM-CLOSE
;;; ------------------------------------------------------------------------------------------------------------------------
;;FIXME: If :target_name does not exist, return COMM_CLOSE as reply.
(defmethod calc* msgs/COMM-OPEN
  [_ S {:keys [req-message] :as ctx}]
  (assert (and req-message ctx))
  (let [jup (:jup @state/STATE)
        {{:keys [comm_id target_module target_name buffers]
                {:keys [state buffer_paths] :as data} :data :as content} :content}
        ,, req-message]
    (assert jup)
    (assert S)
    (assert (s/valid? ::msp/target_name target_name))
    (assert (s/valid? ::msp/target_module target_module))
    (assert (map? state))
    (assert (string? comm_id))
    (assert (vector? buffer_paths))
    (if-let [present? (contains? S comm_id)]
      (do (log/warn "COMM-OPEN - already present")
          [NO-OP-ACTION S])
      (let [state (insert-binary-buffers data buffers)
            state (insert-comm-atoms S state)
            content (msgs/comm-open-content comm_id data {:target_module target_module :target_name target_name})
            comm-atom (ca/create comm_id target_name (set (keys state)) state)
            A (action (step nil {:op :comm-add :port IOPUB :msgtype msgs/COMM-OPEN :content content}))
            S' (assoc S comm_id comm-atom)]
        [A S']))))

(defmethod calc* msgs/COMM-CLOSE
  [_ S {:keys [req-message] :as ctx}]
  (assert (and req-message ctx))
  (let [jup (:jup @state/STATE)
        {{:keys [comm_id data]} :content} req-message]
    (assert jup)
    (assert S)
    (assert (map? data))
    (assert (string? comm_id))
    (if (contains? S comm_id)
      (let [_ (log/debug "Received COMM-CLOSE with known comm_id: " comm_id " and data: " data)
            content (msgs/comm-close-content comm_id {})
            A (action (step nil {:op :comm-remove :port IOPUB :msgtype msgs/COMM-CLOSE :content content}))
            S' (dissoc S comm_id)]
        [A S'])
      (do (log/debug "Received COMM-CLOSE with unknown comm_id: " comm_id)
        [NO-OP-ACTION S]))))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; COMM-INFO-REQUEST
;;; ------------------------------------------------------------------------------------------------------------------------

(defmethod calc* msgs/COMM-INFO-REQUEST
  [_ S {:keys [req-message req-port] :as ctx}]
  (assert (and req-message req-port ctx))
  (let [msgtype msgs/COMM-INFO-REPLY
        jup (:jup @state/STATE)
        _ (assert jup)
        content (msgs/comm-info-reply-content (->> (for [comm-id (keys S)]
                                                     [comm-id (get S comm-id)])
                                                   (into {})))
        A (action (step [`jup/send!! jup req-port req-message msgtype content]
                        (jupmsg-spec req-port msgtype content)))]
    [A S]))

;; COMM-INFO-REPLY is never received
;; If it were to happen the message would fail in the call to `calc*`

(defn calc
  [& args]
  ;; `spec` & `instrument` seem to struggle with (redefinitions of) multi-methods
  ;; Circumvent using plain fn
  (apply calc* args))

(defn comm-state?
  [x]
  (and (map? x)
       (or (empty? x) (every? u/uuid? (keys x)))))

(s/fdef calc
  :args (s/cat :msgtype #{msgs/COMM-CLOSE msgs/COMM-INFO-REPLY msgs/COMM-INFO-REQUEST msgs/COMM-MSG msgs/COMM-OPEN}
               :handler-state comm-state?
               :ctx (s/and map? (P get :req-message) (P get :jup)))
  :ret (s/and vector?
              (C count (p = 2))
              (C first a/action?)
              (C second (s/nilable comm-state?))))
(instrument `calc)

;;; ------------------------------------------------------------------------------------------------------------------------
;;; EXTERNAL
;;; ------------------------------------------------------------------------------------------------------------------------

(defn handle-message
  "Handles `req-message` and returns `Action-State` 2-tuple (first element is Action, second is
  State)."
  [state {:keys [req-message] :as ctx}]
  (let [msgtype (msgs/message-msg-type req-message)]
    (calc msgtype state ctx )))
