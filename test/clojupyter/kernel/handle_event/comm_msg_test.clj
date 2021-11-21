(ns clojupyter.kernel.handle-event.comm-msg-test
  (:require
   [clojupyter.kernel.handle-event :as he]
   [clojupyter.kernel.handle-event.comm-msg :as comm-msg]
   [clojupyter.kernel.handle-event.shared-ops :as sh]
   [clojupyter.log :as log]
   [clojupyter.messages :as msgs]
   [clojupyter.messages-generators-test :as mg]
   [clojupyter.messages-specs :as msp]
   [clojupyter.state :as state]
   [clojupyter.test-shared :as ts]
   [clojupyter.util-actions :as u!]
   [clojure.pprint :as pp]
   [clojure.spec.alpha :as s]
   [clojure.test.check :as tc]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [io.simplect.compose.action :as a]
   [midje.sweet :as midje :refer [=> fact]]

   [clojupyter.kernel.comm-atom :as ca]))

(def QC-ITERS 50)

(def prop--comm-open-updates-state-yielding-comm-open
  (prop/for-all [{:keys [content]} mg/g-comm-open-content]
    (let [req-msgtype msgs/COMM-OPEN
          req-msg ((ts/s*message-header req-msgtype) content)
          comm-id (:comm_id content)
          req-port :shell_port
          S {}
          ctx {:req-message req-msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)
          specs (a/step-specs action)
          {op1 :op port1 :port msgtype1 :msgtype content1 :content} (first specs)]
      (and (sh/single-step-action? action)
           (not (contains? S comm-id))
           (contains? S' comm-id)
           (= S (dissoc S' comm-id))
           (= op1 :comm-add)
           (= port1 :iopub_port)
           (= msgtype1 msgs/COMM-OPEN)
           (= (:target_name content) (:target_name content1))
           (= (:target_module content) (:target_module content1))
           (s/valid? ::msp/comm-open-content content1)))))

(fact
 "COMM-OPEN updates states and yields and COMM-OPEN"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-open-updates-state-yielding-comm-open)))
 => true)

(def prop--comm-close-with-unknown-leaves-state-yielding-nothing
  (prop/for-all [{:keys [content]} mg/g-comm-close-content]
    (let [req-msgtype msgs/COMM-CLOSE
          content (assoc content :data {}) ;; don't send comm-state on close
          req-msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          S {}
          ctx {:req-message req-msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)]
      (and (= action comm-msg/NO-OP-ACTION)
           (= S S')))))

(fact
 "COMM-CLOSE with unknown comm-id does not change state"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-close-with-unknown-leaves-state-yielding-nothing)))
 => true)

(def prop--comm-close-with-known-removes-it-yielding-comm-close
  (prop/for-all [{:keys [content]} mg/g-comm-close-content]
    (let [req-msgtype msgs/COMM-CLOSE
          content (assoc content :data {}) ;; don't send comm-state on close
          req-msg ((ts/s*message-header req-msgtype) content)
          comm-id (:comm_id content)
          req-port :shell_port
          state {:x (gensym)}
          comm (ca/create comm-id "target-name" #{:x} state)
          S {comm-id comm}
          ctx {:req-message req-msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)
          specs (a/step-specs action)
          {op1 :op, port1 :port, msgtype1 :msgtype content :content} (first specs)
          {:keys [comm_id]} content]
      (and (= 1 (count specs))
           (contains? S comm-id)
           (not (contains? S' comm-id))
           (= op1 :comm-remove)
           (= port1 :iopub_port)
           (= msgtype1 msgs/COMM-CLOSE)
           (s/valid? ::msp/comm-close-content content)
           (= comm-id comm_id)))))

(fact
 "COMM-CLOSE with known comm-id updates state and yields COM-CLOSE"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-close-with-known-removes-it-yielding-comm-close)))
 => true)

(def prop--comm-info-request-does-not-change-state-yielding-comm-info-reply
  (prop/for-all [{:keys [content]} mg/g-comm-info-request-content]
    (let [req-msgtype msgs/COMM-INFO-REQUEST
          msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          S {}
          ctx {:req-message msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)
          {:keys [op port msgtype content]} (sh/first-spec action)]
      (and (sh/single-step-action? action)
           (= S S')
           (= op :send-jupmsg)
           (= req-port port)
           (= msgtype msgs/COMM-INFO-REPLY)
           (s/valid? ::msp/comm-info-reply-content content)))))

(fact
  "COMM-INFO-REQUEST does not change state and yields a single COMM-INFO-REPLY"
  (log/with-level :error
    (:pass? (tc/quick-check QC-ITERS prop--comm-info-request-does-not-change-state-yielding-comm-info-reply)))
  => true)

(def prop--comm-msg-unknown-does-not-change-state-and-yields-nothing
  (prop/for-all [{:keys [content]} mg/g-comm-message-content]
    (let [req-msgtype msgs/COMM-MSG
          method msgs/COMM-MSG-REQUEST-STATE
          content (-> content
                      (dissoc :data)
                      (assoc-in [:data :method] method))
          comm-id (:comm_id content)
          msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          S {}
          ctx {:req-message msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)]
      (and (not (contains? S comm-id))
           (= action comm-msg/NO-OP-ACTION)
           (= S S')))))

(fact
 "COMM-MSG-REQUEST-STATE with unknown comm-id does not change state and yields no messages"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-msg-unknown-does-not-change-state-and-yields-nothing)))
 => true)

(def prop--comm-request-state-yields-comm-update-message-on-iopub
  (prop/for-all [{:keys [content]} mg/g-comm-message-content]
    (let [req-msgtype msgs/COMM-MSG
          data {:method msgs/COMM-MSG-REQUEST-STATE}
          content (assoc content :data data)
          req-msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          uuid (msgs/message-comm-id req-msg)
          state {:some-key (gensym)}
          comm (ca/create uuid "target-name" #{:some-key} state)
          S {uuid comm}
          ctx {:req-message req-msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)
          {:keys [op port msgtype content]}  (sh/first-spec action)
          rsp-message ((ts/s*message-header msgtype) content)]
      (and (sh/single-step-action? action)
           (= S S')
           (= op :send-jupmsg)
           (= port :iopub_port)
           (= msgtype msgs/COMM-MSG)
           (= uuid (msgs/message-comm-id rsp-message))
           (= msgs/COMM-MSG-UPDATE (msgs/message-comm-method rsp-message))
           (= state (msgs/message-comm-state rsp-message))
           (s/valid? ::msp/comm-message-content content)))))

(fact
 "COMM-MSG-REQUEST-STATE with known comm-id yields COMM-UPDATE message on iopub_port"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-request-state-yields-comm-update-message-on-iopub)))
 => true)

(def prop--comm-state-can-be-updated-using-comm-msg
  (prop/for-all [{:keys [content]} mg/g-comm-message-content]
    (let [req-msgtype msgs/COMM-MSG
          post-val (gensym)
          K :some-key
          post-comm-state {K post-val}
          data {:method msgs/COMM-MSG-UPDATE
                :state post-comm-state
                :buffer_paths []}
          content (assoc content :data data)
          req-msg ((ts/s*message-header req-msgtype) content)
          uuid (msgs/message-comm-id req-msg)
          pre-comm-state (ca/create uuid "target-name" #{:some-key} (msgs/message-comm-state req-msg))
          req-port :shell_port
          S {uuid pre-comm-state}
          ctx {:req-message req-msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)
          {:keys [op comm-id new-state] :as specs} (sh/first-spec action)]
      (and (sh/single-step-action? action)
           (= new-state post-comm-state)
           (= op :update-agent)
           (= uuid comm-id)
           (contains? S uuid)
           (nil? S')
           (= (get S uuid) pre-comm-state)))))


(fact
 "COMM-MSG-UPDATE with known comm-id updates state yielding no actions"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-state-can-be-updated-using-comm-msg)))
 => true)

(def prop--comm-update-unknown-does-not-change-state-and-yields-nothing
  (prop/for-all [{:keys [content]} mg/g-comm-message-content]
    (let [req-msgtype msgs/COMM-MSG
          method msgs/COMM-MSG-UPDATE
          content (assoc-in content [:data :method] method)
          comm-id (:comm_id content)
          msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          S {}
          ctx {:req-message msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)]
      (and (not (contains? S comm-id))
           (= action comm-msg/NO-OP-ACTION)
           (= S S')))))

(fact
 "COMM-MSG-UPDATE with unknown comm-id does not change state and yields no messages"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-update-unknown-does-not-change-state-and-yields-nothing)))
 => true)

(def prop--comm-open-known-id-leaves-state-yielding-nothing
  (prop/for-all [{:keys [content]} mg/g-comm-open-content]
    (let [req-msgtype msgs/COMM-OPEN
          req-msg ((ts/s*message-header req-msgtype) content)
          comm-id (:comm_id content)
          req-port :shell_port
          pre-comm (ca/create comm-id "target-name" #{:some-key} {:some-key :some-val})
          S {comm-id pre-comm}
          ctx {:req-message req-msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)]
      (and (= action comm-msg/NO-OP-ACTION)
           (contains? S comm-id)
           (contains? S' comm-id)
           (= S S')))))

(fact
 "COMM-OPEN with known comm-id does not change states and yields no message"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-open-known-id-leaves-state-yielding-nothing)))
 => true)

(def prop--comm-custom-unknown-does-not-change-state-and-yields-nothing
  (prop/for-all [{:keys [content]} mg/g-comm-message-content]
    (let [req-msgtype msgs/COMM-MSG
          method msgs/COMM-MSG-CUSTOM
          data {:method method :content {:event "bar"}}
          content (assoc content :data data)
          comm-id (:comm_id content)
          msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          S {}
          ctx {:req-message msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)]
      (and (not (contains? S' comm-id))
           (= action comm-msg/NO-OP-ACTION)
           (= S S')))))

(fact
 "COMM-MSG-CUSTOM with unknown comm-id does not change state and yields no messages"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-custom-unknown-does-not-change-state-and-yields-nothing)))
 => true)

(def prop--comm-custom-known-does-calls-callback
  (prop/for-all [{:keys [content]} mg/g-comm-message-content]
    (let [req-msgtype msgs/COMM-MSG
          method msgs/COMM-MSG-CUSTOM
          data {:method method :content {:event "foo"}}
          content (assoc content :data data)
          comm-id (:comm_id content)
          msg ((ts/s*message-header req-msgtype) content)
          req-port :shell_port
          state {:some-key :some-val :callbacks {:on-foo (fn [_ _ _] :foo)}}
          pre-comm (ca/create comm-id "target-name" #{:some-key} state)
          S {comm-id pre-comm}
          ctx {:req-message msg, :req-port req-port, :jup :must-be-present}
          _ (swap! state/STATE assoc :jup :must-be-present)
          [action S'] (comm-msg/calc req-msgtype S ctx)
          {post-comm-id :comm-id op :op :as specs} (sh/first-spec action)]
      (and (contains? S comm-id)
           (nil? S')
           (= op :callback)
           (= post-comm-id comm-id)))))

(fact
 "COMM-MSG-CUSTOM with known comm-id calls the comms-atom's callback fn"
 (log/with-level :error
   (:pass? (tc/quick-check QC-ITERS prop--comm-custom-known-does-calls-callback)))
 => true)
