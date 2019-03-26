(ns clojupyter.kernel.middleware.comm-test
  (:require
   [midje.sweet							:refer [fact =>]]
   [nrepl.core					:as nrepl]
   ,,
   [clojupyter.kernel.middleware		:as M]
   [clojupyter.kernel.cljsrv.nrepl-server	:as clojupyter-nrepl-server]
   [clojupyter.kernel.cljsrv.nrepl-comm		:as nrepl-comm]
   ,,
   [clojupyter.kernel.transport-test		:as TT]))


(def C-INFO-MSG  {:envelope
                  [(byte-array [50, 49, 55, 49])],
                  :delimiter "<IDS|MSG>",
                  :signature "bedcb36c798a0ebc28f3414b1272670435eb967ce464ca8bc67fa094468013fb",
                  :header {:msg_id "0507bafb98ee4f0180822610ffff81d9",
                           :username "username",
                           :session "21717f9ca89e4dc0843fbc01f6e394b3",
                           :msg_type "comm_info_request",
                           :version "5.2"},
                  :parent-header {},
                  :content {:target_name "jupyter.widget"}})

(fact "comm_info_request yields a comm_info_reply"
  (with-open [nrepl-server	(clojupyter-nrepl-server/start-nrepl-server)
              nrepl-conn	(nrepl/connect :port (:port nrepl-server))]
    (let [nrepl-comm		(nrepl-comm/make-nrepl-comm nrepl-server nrepl-conn)
          H			((comp M/wrapin-bind-msgtype
                                       M/wrap-base-handlers) TT/UNHANDLED)
          ctx			(TT/test-ctx {:nrepl-comm nrepl-comm} C-INFO-MSG)]
      (H ctx)
      (->> ctx :transport TT/sent :req first :msgtype)))
  =>
  "comm_info_reply")
