(ns clojupyter.kernel.middleware.inspect-test
  (:require
   [clojure.repl]
   [midje.sweet							:refer [fact =>]]
   [nrepl.core					:as nrepl]
   ,,
   [clojupyter.kernel.middleware		:as M]
   [clojupyter.kernel.cljsrv.nrepl-server	:as clojupyter-nrepl-server]
   [clojupyter.kernel.cljsrv.nrepl-comm		:as nrepl-comm]
   ,,
   [clojupyter.kernel.transport-test		:as TT]))


(def INSP-MSG  {:envelope
                [(byte-array [50, 49, 55, 49])],
                :delimiter "<IDS|MSG>",
                :signature "bd65a6e7077888981ea7afb4e820c20c28a8722d3d268d722c29e67fbdedc81b",
                :header {:msg_id "a71ea799e6cd4ffa886d44c7e04d5b9e",
                         :username "username",
                         :session "21717f9ca89e4dc0843fbc01f6e394b3",
                         :msg_type "inspect_request",
                         :version "5.2"},
                :parent-header {},
                :content {:code "(println 99)", :cursor_pos 8, :detail_level 0}})

(fact "inspect_request yields an inspect_reply"
  (with-open [nrepl-server	(clojupyter-nrepl-server/start-nrepl-server)
              nrepl-conn	(nrepl/connect :port (:port nrepl-server))]
    (let [nrepl-comm		(nrepl-comm/make-nrepl-comm nrepl-server nrepl-conn)
          H			((comp M/wrapin-bind-msgtype
                                       M/wrap-base-handlers) TT/UNHANDLED)
          ctx			(TT/test-ctx {:nrepl-comm nrepl-comm} INSP-MSG)]
      (H ctx)
      [(->> ctx :transport TT/sent :req first :msgtype)
       (->> ctx :transport TT/sent :req first :message :data :text/plain (re-find #"^clojure.core/println\n")
            )]))
  =>
  ["inspect_reply" "clojure.core/println\n"])
