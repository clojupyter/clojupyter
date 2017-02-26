(ns clojupyter.core-test
  "Tests nrepl evaluation and error handling."
  {:author "Peter Denno"}
  (:require [clojure.test :refer :all]
            [clojure.tools.nrepl.server :as nrepl.server]
            [clojure.pprint :as p]
            [clojupyter.core :refer :all]
            [clojupyter.misc.messages :refer :all]
            [clojupyter.misc.zmq-comm :as mzmq]
            [clojupyter.protocol.zmq-comm :as pzmq]
            [clojupyter.protocol.nrepl-comm :as pnrepl]
            [clojupyter.misc.states :as states]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]
            [cheshire.core :as cheshire]))

(defrecord MockZmqComm [input output messages current-message]
  pzmq/PZmqComm
  (zmq-send [self socket message]
    (swap! output conj message)
    (swap! current-message conj message)
    (swap! messages conj @current-message)
    (reset! current-message []))
  (zmq-send [self socket message zmq-flag]
    (swap! output conj message)
    (swap! current-message conj message))
  (zmq-read-raw-message [self socket]
    (let [out (first @input)]
      (if (not-empty @input)
        (swap! input pop))
      out))
  (zmq-recv [self socket]
    (let [out (first @input)]
      (if (not-empty @input)
        (swap! input pop))
      out))
  (zmq-recv-all [self socket]
    (let [out (first @input)]
      (if (not-empty @input)
        (swap! input pop))
      out)))

(defn make-mock-zmq-comm [input output message]
  (MockZmqComm. input output message (atom [])))

(defrecord MockNreplComm [nrepl-server]
  pnrepl/PNreplComm
  (nrepl-trace [self])
  (nrepl-interrupt [self])
  (nrepl-eval [self states zmq-comm code parent-header session-id signer ident])
  (nrepl-complete [self code]))

(defn make-mock-nrepl-comm []
  (MockNreplComm. (atom nil)))

(deftest test-process-heartbeat
  (let [socket       :hb-socket
        states       (states/make-states)
        in           '(1 2 3 4)
        zmq-in       (atom in)
        zmq-out      (atom [])
        zmq-messages (atom [])
        zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)]
    (doseq [_ @zmq-in]
      (process-heartbeat zmq-comm socket))
    (is (= in @zmq-out))))

(deftest test-kernel-info-request
  (let [socket      :hb-socket
        signer      (get-message-signer "TEST-KEY")
        states      (states/make-states)
        in          (list
                     {:header
                      (cheshire/generate-string
                       {
                        :msg_type "kernel_info_request"
                        })
                      }
                     )
        zmq-in       (atom in)
        zmq-out      (atom [])
        zmq-messages (atom [])
        nrepl-comm   (make-mock-nrepl-comm)
        zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)
        handler      (configure-shell-handler states zmq-comm nrepl-comm
                                              socket signer)]
    (try
      (log/set-level! :error)
      (doseq [_ @zmq-in]
        (process-event states zmq-comm socket signer handler))
      (is (= "busy" (get-in ((comp parse-message mzmq/parts-to-message)
                             (get @zmq-messages 0))
                            [:content :execution_state]
                            )))
      (is (= {:status "ok",
              :protocol_version "5.0",
              :implementation "clojupyter",
              :language_info
              {:name "clojure",
               :version "1.8.0",
               :mimetype "text/x-clojure",
               :file_extension ".clj"},
              :banner "Clojupyters-0.1.0",
              :help_links []}
             (get-in ((comp parse-message mzmq/parts-to-message)
                      (get @zmq-messages 1))
                     [:content]
                     )))
      (is (= "idle" (get-in ((comp parse-message mzmq/parts-to-message)
                             (get @zmq-messages 2))
                            [:content :execution_state]
                            ))))))

(deftest test-shutdown-request
  (let [socket      :hb-socket
        signer      (get-message-signer "TEST-KEY")
        states      (states/make-states)
        in          (list
                     {:idents []
                      :header
                      (cheshire/generate-string
                       {
                        :msg_type "shutdown_request"
                        :session  "123456"
                        })
                      :content
                      (cheshire/generate-string
                       {:restart false}
                       )
                      }
                     )
        zmq-in       (atom in)
        zmq-out      (atom [])
        zmq-messages (atom [])
        nrepl-comm   (make-mock-nrepl-comm)
        zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)
        handler      (configure-shell-handler states zmq-comm nrepl-comm
                                              socket signer)]
    (try
      (log/set-level! :error)
      (def stopped (atom false))
      (with-redefs [nrepl.server/stop-server (fn [a] (reset! stopped true))]
        (doseq [_ @zmq-in]
          (process-event states zmq-comm socket signer handler)))
      (is (= @stopped true))
      (is (= "busy" (get-in ((comp parse-message mzmq/parts-to-message)
                             (get @zmq-messages 0))
                            [:content :execution_state]
                            )))
      (is (= {:restart ["content" "restart"],
              :status "ok"}
             (get-in ((comp parse-message mzmq/parts-to-message)
                      (get @zmq-messages 1))
                     [:content]
                     )))
      (is (= "idle" (get-in ((comp parse-message mzmq/parts-to-message)
                             (get @zmq-messages 2))
                            [:content :execution_state]
                            ))))))
