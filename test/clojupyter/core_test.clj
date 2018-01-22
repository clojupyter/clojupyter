(ns clojupyter.core-test
  "Tests nrepl evaluation and error handling."
  {:author "Peter Denno"}
  (:require [cheshire.core :as cheshire]
            [clojupyter.core :refer :all]
            [clojupyter.misc.messages :refer :all]
            [clojupyter.misc.nrepl-comm :as nrepl-comm]
            [clojupyter.misc.states :as states]
            [clojupyter.misc.zmq-comm :as mzmq]
            [clojupyter.protocol.nrepl-comm :as pnrepl]
            [clojupyter.protocol.zmq-comm :as pzmq]
            [clojure.pprint :as p]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.server :as nrepl.server]
            [midje.sweet :refer :all]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

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
  (zmq-read-raw-message [self socket flag]
    (let [out (first @input)]
      (if (not-empty @input)
        (swap! input pop))
      out))
  (zmq-recv [self socket]
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

(fact "should be able to process heartbeat"
      (let [socket       :hb-socket
            states       (states/make-states)
            in           '(1 2 3 4)
            zmq-in       (atom in)
            zmq-out      (atom [])
            zmq-messages (atom [])
            zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)]
        (doseq [_ @zmq-in]
          (process-heartbeat zmq-comm socket))
        @zmq-out) => '(1 2 3 4))

(background
 [(around :facts
          (let [socket      :shell-socket
                signer      (get-message-signer "TEST-KEY")
                states      (states/make-states)
                zmq-in       (atom [])
                zmq-out      (atom [])
                zmq-messages (atom [])
                nrepl-comm   (make-mock-nrepl-comm)
                zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)
                handler      (configure-shell-handler states zmq-comm nrepl-comm
                                                      socket signer)] ?form))
  (before :facts (log/set-level! :error))])

(against-background
 [(before :facts (do (reset! zmq-in (list {:header
                                           (cheshire/generate-string
                                            {:msg_type "kernel_info_request"})
                                      }))
                     (doseq [_ @zmq-in]
                       (process-event states zmq-comm socket signer handler))))]
 (fact "should be able to process kernel-info-request"
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 0))
               [:content :execution_state]
               )
       => "busy"
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 1))
               [:content]
               )
       => {:status "ok",
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
                (get @zmq-messages 2))
               [:content :execution_state]
               )
       => "idle"))

(against-background
 [(before :facts (do
                   (def stopped (atom false))
                   (with-redefs [nrepl.server/stop-server (fn [a] (reset! stopped true))]
                     (reset! zmq-in (list
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
                                      }))
                     (doseq [_ @zmq-in]
                       (process-event states zmq-comm socket signer handler)))))]
 (fact "should be able to process shutdown-request"
       @stopped => true
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 0))
               [:content :execution_state])
       => "busy"
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 1))
               [:content])
       => {:restart ["content" "restart"],
           :status "ok"}
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 2))
               [:content :execution_state])
       => "idle"))

(background
 [(around :facts
          (with-open [nrepl-server       (start-nrepl-server)
                      nrepl-transport    (nrepl/connect :port (:port nrepl-server))]
            (let [nrepl-client       (nrepl/client nrepl-transport Integer/MAX_VALUE)
                  nrepl-session      (nrepl/new-session nrepl-client)
                  nrepl-comm (nrepl-comm/make-nrepl-comm nrepl-server nrepl-transport
                                                         nrepl-client nrepl-session)
                  socket      :shell-socket
                  signer      (get-message-signer "TEST-KEY")
                  states      (states/make-states)
                  zmq-in       (atom [])
                  zmq-out      (atom [])
                  zmq-messages (atom [])
                  zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)
                  handler      (configure-shell-handler
                                states zmq-comm nrepl-comm socket signer)]
              ?form)))
  (before :facts (log/set-level! :error))])

(against-background
 [(before :facts (do
                   (def stopped (atom false))
                   (with-redefs [nrepl.server/stop-server (fn [a] (reset! stopped true))]
                     (reset! zmq-in (list
                                      {:idents []
                                       :delimiter "<IDS|MSG>"
                                       :signature "4ecc6d9"
                                       :header
                                       (cheshire/generate-string
                                        {:username "username"
                                         :version "5.0"
                                         :msg_id "5B634675975849A3AA688D1B4C4C4D21"
                                         :msg_type "execute_request"
                                         :session "219D5A8FE4F54F7E888C6C68B3FD012F"})
                                       :parent-header
                                       (cheshire/generate-string
                                        {})
                                       :content
                                       (cheshire/generate-string
                                        {
                                         :store_history true
                                         :silent false
                                         :stop_on_error true
                                         :code "(println 10)"
                                         :user_expressions {}
                                         :allow_stdin true})
                                      }))
                     (doseq [_ @zmq-in]
                       (process-event states zmq-comm socket signer handler)))))]
 (fact "should be able to process execute-request"
       @stopped => false
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 0))
               [:content :execution_state])
       => "busy"
       (get-in ((comp parse-message mzmq/parts-to-message)
                (get @zmq-messages 1))
               [:content])
       => {:code "(println 10)"
           :execution_count 1}
       (get-in ((comp parse-message mzmq/parts-to-message)
               (get @zmq-messages 2))
              [:header :msg_type ])
       => "stream"
       (get-in ((comp parse-message mzmq/parts-to-message)
               (get @zmq-messages 2))
              [:content :text])
       => "10\n"
       (get-in ((comp parse-message mzmq/parts-to-message)
               (get @zmq-messages 3))
              [:header :msg_type ])
       => "execute_reply"
       (get-in ((comp parse-message mzmq/parts-to-message)
               (get @zmq-messages 3))
              [:content :execution_count])
       => 1
       ))
