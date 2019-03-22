(ns clojupyter.middleware.complete-test
  (:require
   [clojure.spec.alpha				:as s]
   [midje.sweet							:refer :all]
   [nrepl.core					:as nrepl]
   ,,
   [clojupyter.kernel.state			:as state]
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


(def COMPL-MSG  {:envelope
                 [(byte-array [50, 49, 55, 49])],
                 :delimiter "<IDS|MSG>",
                 :signature "1afd457871e1c443d0fcb2564cf0de9767568bc4b6a7c10cc98cc4929f17da82",
                 :header {:msg_id "2545ad80bcda442aa3754c7c52271e86",
                          :username "username",
                          :session "21717f9ca89e4dc0843fbc01f6e394b3",
                          :msg_type "complete_request",
                          :version "5.2"},
                 :parent-header {},
                 :content {:code "(requir '[clojupyter :refer [display html]])", :cursor_pos 7}})

(def IS-COMPL-MSG  {:envelope
                    [(byte-array [50, 49, 55, 49])],
                    :delimiter "<IDS|MSG>",
                    :signature "1afd457871e1c443d0fcb2564cf0de9767568bc4b6a7c10cc98cc4929f17da82",
                    :header {:msg_id "2545ad80bcda442aa3754c7c52271e86",
                     :username "username",
                     :session "21717f9ca89e4dc0843fbc01f6e394b3",
                     :msg_type "is_complete_request",
                     :version "5.2"},
                    :parent-header {},
                    :content {:code "(println 42)"}})

(fact "complete_request yields a complete_reply"
  (with-open [nrepl-server	(clojupyter-nrepl-server/start-nrepl-server)
              nrepl-conn	(nrepl/connect :port (:port nrepl-server))]
    (let [nrepl-comm		(nrepl-comm/make-nrepl-comm nrepl-server nrepl-conn)
          H			((comp M/wrapin-bind-msgtype
                                       M/wrap-base-handlers) TT/UNHANDLED)
          ctx			(TT/test-ctx {:nrepl-comm nrepl-comm} COMPL-MSG)]
      (H ctx)
      [(->> ctx :transport TT/sent :req first :msgtype)
       (->> ctx :transport TT/sent :req first :message :matches
            (into #{}) (some (partial contains? #{"require"})))]))
  =>
  ["complete_reply" true])

(fact "is_complete_request yields a is_complete_reply"
  (with-open [nrepl-server	(clojupyter-nrepl-server/start-nrepl-server)
              nrepl-conn	(nrepl/connect :port (:port nrepl-server))]
    (let [nrepl-comm		(nrepl-comm/make-nrepl-comm nrepl-server nrepl-conn)
          H			((comp M/wrapin-bind-msgtype
                                       M/wrap-base-handlers) TT/UNHANDLED)
          ctx			(TT/test-ctx {:nrepl-comm nrepl-comm} IS-COMPL-MSG)]
      (H ctx)
      (->> ctx :transport TT/sent :req first :msgtype)))
  =>
  "is_complete_reply")
