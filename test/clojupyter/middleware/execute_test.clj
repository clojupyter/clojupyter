(ns clojupyter.middleware.execute-test
  (:require
   [clojure.spec.alpha				:as s]
   [midje.sweet							:refer :all]
   [nrepl.core					:as nrepl]
   ,,
   [clojupyter.kernel.state			:as state]
   [clojupyter.kernel.init			:as init]
   [clojupyter.middleware			:as M]
   [clojupyter.middleware.base			:as B]
   [clojupyter.misc.jupyter			:as jup]
   [clojupyter.misc.spec			:as sp]
   [clojupyter.misc.util			:as u]
   [clojupyter.nrepl.nrepl-server		:as clojupyter-nrepl-server]
   [clojupyter.nrepl.nrepl-comm			:as nrepl-comm]
   [clojupyter.nrepl.nrepl-comm			:as pnrepl]
   [clojupyter.transport			:as T]
   ,,
   [clojupyter.transport-test			:as TT]))

(def EXE-MSG {:envelope
              [(byte-array [52, 102, 98, 53, 54, 99, 53, 48, 49, 100, 51, 51, 52, 48, 100, 51,
                            57, 56, 98, 98, 56, 100, 51, 55, 52, 50, 99, 100, 57, 101, 53, 48])],
              :delimiter "<IDS|MSG>",
              :signature
              "5a98d552a77da7f1595030a8771f4ef861a5ca406d13b1001d4acfa7a58ace88",
              :header
              {:msg_id "e93eb6ef140940518bb379ae9c962a7d",
               :username "username",
               :session "4fb56c501d3340d398bb8d3742cd9e50",
               :msg_type "execute_request",
               :version "5.2"},
              :parent-header {},
              :content
              {:code "(+ 1 2 3)",
               :silent false,
               :store_history true,
               :user_expressions {},
               :allow_stdin true,
               :stop_on_error true}})

(with-state-changes [(before :facts (init/init-global-state!))]
  (fact "execute_request yields execute_input and execute_result on iopub and execute_reply on :req"
    (with-open [nrepl-server	(clojupyter-nrepl-server/start-nrepl-server)
                nrepl-conn	(nrepl/connect :port (:port nrepl-server))]
      (let [nrepl-comm		(nrepl-comm/make-nrepl-comm nrepl-server nrepl-conn)
            H			((comp M/wrapin-bind-msgtype
                                       M/wrap-base-handlers) TT/UNHANDLED)
            ctx			(TT/test-ctx {:nrepl-comm nrepl-comm} EXE-MSG)]
        (H ctx)
        (let [sent (-> ctx :transport TT/sent)]
          (mapv (partial get-in sent)
                [[:iopub 0 :msgtype] [:iopub 0 :message :code]
                 [:iopub 1 :msgtype] [:iopub 1 :message :data]
                 [:req 0 :msgtype]   [:req 0 :message :status]]))))
    =>
    ["execute_input" "(+ 1 2 3)"
     "execute_result" {:text/plain "6"}
     "execute_reply" "ok"]))


