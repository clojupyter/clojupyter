(ns clojupyter.core-test
  "Tests nrepl evaluation and error handling."
  {:author "Peter Denno"}
  (:require
   [cheshire.core				:as cheshire]
   [clojure.pprint				:as p]
   [midje.sweet							:refer :all]
   [nrepl.core					:as nrepl]
   [nrepl.server				:as nrepl.server]
   [taoensso.timbre				:as log]
   [zeromq.zmq					:as zmq]
   ,,
   [clojupyter.core						:refer :all]
   [clojupyter.misc.messages			:as messages	:refer :all]
   [clojupyter.misc.nrepl-comm			:as nrepl-comm]
   [clojupyter.misc.states			:as states]
   [clojupyter.misc.util			:as u		:refer [rcomp]]
   [clojupyter.misc.zmq-comm			:as mzmq]
   [clojupyter.protocol.nrepl-comm		:as pnrepl]
   [clojupyter.protocol.zmq-comm		:as pzmq]
   ))

(def json cheshire/generate-string)

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
  (nrepl-eval [self S code parent-message])
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
        @zmq-out) => [1 2 3 4])

(background
 [(around :facts
          (let [socket       :shell-socket
                signer       (make-message-signer "TEST-KEY")
                checker      (make-message-checker "TEST-KEY")
                states       (states/make-states)
                zmq-in       (atom [])
                zmq-out      (atom [])
                zmq-messages (atom [])
                nrepl-comm   (make-mock-nrepl-comm)
                zmq-comm     (make-mock-zmq-comm zmq-in zmq-out zmq-messages)
                S	     {:states states :zmq-comm zmq-comm :nrepl-comm nrepl-comm
                              :signer signer :checker checker :socket socket}
                handler      (configure-shell-handler S)
                S	     (assoc S :handler handler)
                rcv	     (rcomp mzmq/parts-to-message build-message)]
            ?form))
  (before :facts (log/set-level! :error))])

(against-background
 [(before :facts (do (reset! zmq-in (list {:header (json {:msg_type "kernel_info_request"})}))
                     (doseq [_ @zmq-in]
                       (process-event S))))]
 (fact "should be able to process kernel-info-request"
       (mapv (rcomp rcv :content #(select-keys % [:status :execution_state])) @zmq-messages)
       => [{:execution_state "busy"}
           {:status "ok"}
           {:execution_state "idle"}]
       (-> (get @zmq-messages 1) rcv (get-in [:content])
           ((juxt :protocol_version :status :implementation
                  (rcomp :language_info :name)
                  (rcomp :language_info :mimetype)
                  (rcomp :language_info :file_extension)
                  (rcomp :language_info :version))))
       => [messages/PROTOCOL-VERSION "ok" "clojupyter" "clojure" "text/x-clojure" ".clj"
           (apply format "%d.%d.%d" ((juxt :major :minor :incremental) *clojure-version*))]))

(against-background
   [(before :facts (do
                     (def stopped (atom false))
                     (with-redefs [nrepl.server/stop-server (fn [a] (reset! stopped true))]
                       (reset! zmq-in (list
                                       {:idents [],
                                        :header (json {:msg_type "shutdown_request", :session  "123456"}),
                                        :content (json {:restart false})}))
                       (doseq [_ @zmq-in]
                         (process-event S)))))]
   (fact "should be able to process shutdown-request"
         @stopped
         => true
         (mapv (rcomp rcv :content #(select-keys % [:execution_state :status :restart :protocol_version]))
               @zmq-messages)
         => [{:execution_state "busy"}
             {:status "ok", :restart false}
             {:execution_state "idle"}]))

(background
   [(around :facts
            (with-open [nrepl-server       (start-nrepl-server)
                        nrepl-transport    (nrepl/connect :port (:port nrepl-server))]
              (let  [nrepl-client  (nrepl/client nrepl-transport Integer/MAX_VALUE)
                     nrepl-session (nrepl/new-session nrepl-client)
                     nrepl-comm    (nrepl-comm/make-nrepl-comm nrepl-server nrepl-transport nrepl-client nrepl-session)
                     socket        :shell-socket
                     signer        (make-message-signer "TEST-KEY")
                     checker       (make-message-checker "TEST-KEY")
                     states        (states/make-states)
                     zmq-in        (atom [])
                     zmq-out       (atom [])
                     zmq-messages  (atom [])
                     zmq-comm      (make-mock-zmq-comm zmq-in zmq-out zmq-messages)
                     S	     	  {:states states :zmq-comm zmq-comm :nrepl-comm nrepl-comm
                                   :signer signer :checker checker :socket socket}
                     handler       (configure-shell-handler S)
                     S	     	  (assoc S :handler handler)
                     rcv		  (rcomp mzmq/parts-to-message build-message)]
                ?form)))])

(against-background
 [(before :facts (do (log/set-level! :error)
                     (def stopped (atom false))
                     (with-redefs [nrepl.server/stop-server (fn [a] (reset! stopped true))]
                       (reset! zmq-in (list {:idents []
                                             :delimiter "<IDS|MSG>"
                                             :signature "4ecc6d9"
                                             :header (json {:username "username"
                                                            :version "5.0"
                                                            :msg_id "5B634675975849A3AA688D1B4C4C4D21"
                                                            :msg_type "execute_request"
                                                            :session "219D5A8FE4F54F7E888C6C68B3FD012F"})
                                             :parent-header (json {})
                                             :content (json {:store_history true
                                                             :silent false
                                                             :stop_on_error true
                                                             :code "(println 10)"
                                                             :user_expressions {}
                                                             :allow_stdin true})}))
                       (doseq [_ @zmq-in]
                         (process-event S)))))]
 (fact "should be able to process execute-request"
       @stopped
       => false
       (mapv (rcomp rcv :content) @zmq-messages)
       => [{:execution_state "busy"}
           {:code "(println 10)" :execution_count 1}
           {:name "stdout" :text "10\n"}
           {:execution_count 1 :status "ok" :user_expressions {}}
           {:data {:text/plain "nil"} :execution_count 1 :metadata {}}
           {:execution_state "idle"}]
       ))
