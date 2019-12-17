(ns clojupyter.kernel.handle-event.execute-test
  (:require [clojupyter.kernel.cljsrv :as srv]
            [clojupyter.kernel.handle-event.execute :as execute]
            [clojupyter.kernel.handle-event.shared-ops :as sh]
            [clojupyter.kernel.init :as init]
            [clojupyter.log :as log]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.test-shared :as ts]
            [clojure.spec.alpha :as s]
            [io.simplect.compose :refer [C p]]
            [io.simplect.compose.action :as a]
            [midje.sweet :as midje :refer [=> fact]]))

(fact
 "execute-request fails without a running execute process"
 (log/with-level :error
   (do (init/ensure-init-global-state!)
       (with-open [srv (srv/make-cljsrv)]
         (let [code "(list 1 2 3)"
               msg ((ts/s*message-header msgs/EXECUTE-REQUEST)
                    (merge (ts/default-execute-request-content) {:code code}))
               port :shell_port
               req {:req-message msg, :req-port port, :cljsrv srv}
               ;; note: execute/, not dispatch/: -- execute is in a separate process
               {:keys [enter-action leave-action] :as rsp} (execute/eval-request req)
               specs (a/step-specs leave-action)
               [input-spec & input-rest] (->> specs (filter (C :msgtype (p = msgs/EXECUTE-INPUT))))
               [reply-spec & reply-rest] (->> specs (filter (C :msgtype (p = msgs/EXECUTE-REPLY))))
               [result-spec & result-rest] (->> specs (filter (C :msgtype (p = msgs/EXECUTE-RESULT))))
               [history-spec & history-rest] (->> specs (filter (C :op (p = :add-history))))
               [exe-count-spec & exe-count-rest] (->> specs (filter (C :op (p = :inc-execute-count))))
               {input-message-to :message-to, input-msgtype :msgtype,
                input-message :message} input-spec
               {input-exe-count :execution_count} input-message
               {reply-message-to :message-to, reply-msgtype :msgtype,
                reply-message :message} reply-spec
               {reply-exe-count :execution_count, reply-status :status} reply-message
               {result-message-to :message-to, result-msgtype :msgtype,
                result-message :message} result-spec
               {result-exe-count :execution_count} result-message
               {history-data :data} history-spec]
           (and (sh/single-step-action? enter-action)
                (sh/successful-action? enter-action)
                (nil? input-rest)
                ;; INPUT-MSG
                (= input-message-to :iopub_port)
                (= input-msgtype msgs/EXECUTE-INPUT)
                (s/valid? ::msp/execute-input-content input-message)
                ;; REPLY-MSG
                (nil? reply-rest)
                (= reply-message-to port)
                (= reply-msgtype msgs/EXECUTE-REPLY)
                (s/valid? ::msp/execute-reply-content reply-message)
                ;; RESULT-MSG
                (nil? result-rest)
                (= result-message-to :iopub_port)
                (= result-msgtype msgs/EXECUTE-RESULT)
                (s/valid? ::msp/execute-result-content result-message)
                ;; HISTORY + EXE-COUNTER
                (nil? history-rest)
                (nil? exe-count-rest)
                (= "ok" reply-status)
                (integer? input-exe-count)
                (= input-exe-count reply-exe-count result-exe-count)
                (= code history-data))))))
 => true)
