(ns clojupyter
  (:require
   [beckon]
   [cheshire.core			:as cheshire]
   [clojure.data.json			:as json]
   [clojure.pprint			:as pp]
   [clojure.stacktrace			:as st]
   [clojure.string			:as str]
   [clojure.walk			:as walk]
   [nrepl.core				:as nrepl]
   [nrepl.server			:as nrepl.server]
   [taoensso.timbre			:as log]
   [zeromq.zmq				:as zmq]
   ,,
   [clojupyter.middleware.mime-values]
   [clojupyter.history			:as his]
   [clojupyter.messages			:refer :all]
   [clojupyter.nrepl-comm		:as nrepl-comm]
   [clojupyter.states			:as states]
   [clojupyter.zmq-comm			:as zmq-comm]
   [clojupyter.protocol.nrepl-comm	:as pnrepl]
   [clojupyter.protocol.zmq-comm	:as pzmq])
  (:gen-class :main true))

(defn- catching-exceptions*
  ([form finally-form]
   `(try ~form
         (catch Exception e#
           (do (log/error (with-out-str (st/print-stack-trace e# 20)))
               (throw e#)))
         (finally ~finally-form))))

(defmacro ^{:style/indent 1} catching-exceptions
  ([form]
   (catching-exceptions* form '(do)))
  ([form finally-form]
   (catching-exceptions* form finally-form)))

(defn- prep-config
  [args]
  (-> args first slurp json/read-str walk/keywordize-keys))

(defn- address
  [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(def clojupyter-middleware
  '[clojupyter.middleware.mime-values/mime-values])

(defn clojupyter-nrepl-handler
  []
  ;; dynamically load to allow cider-jack-in to work
  ;; see https://github.com/clojure-emacs/cider-nrepl/issues/447
  (require 'cider.nrepl)
  (apply nrepl.server/default-handler
         (map resolve
              (concat (var-get (ns-resolve 'cider.nrepl 'cider-middleware))
                      clojupyter-middleware))))

(defonce ^:dynamic ^:private *NREPL-SERVER-ADDR* nil)

(defn nrepl-server-addr
  []
  (str *NREPL-SERVER-ADDR*))

(defn start-nrepl-server
  []
  (let [srv (nrepl.server/start-server :handler (clojupyter-nrepl-handler))
        sock-addr (.getLocalSocketAddress (:server-socket srv))]
    (println (str "Started NREPL server on " sock-addr "."))
    (alter-var-root #'*NREPL-SERVER-ADDR* (constantly sock-addr))
    srv))

(defn- shutdown
  [{:keys [nrepl-comm states] :as S}]
  (reset! (:alive states) false)
  (nrepl.server/stop-server @(:nrepl-server nrepl-comm)))

(defn- configure-shell-handler
  [S]
  (let [respond-to-execute-request (execute-request-handler)] ;; needs to bind execution counter atom
    (fn [message]
      (log/debug "shell-handler: " :S S :message message)
      (assert (:socket S) (str "shell-handler: no socket found"))
      (let [msg-type (message-msg-type message)]
        (case msg-type
          "execute_request"	(respond-to-execute-request S msg-type message)
          "shutdown_request"    (do
                                  (respond-to-message S msg-type message)
                                  (shutdown S)
                                  (Thread/sleep 100))
          (respond-to-message S msg-type message))))))

(defn- configure-control-handler
  [S]
  (fn [message]
    (log/debug (str "control-handler: " :S S :message message))
    (let [msg-type (message-msg-type message)]
      (respond-to-message S msg-type message))))

(defn- process-event
  [{:keys [zmq-comm socket signer handler] :as S}]
  (log/debug (str "process-event: " :S S))
  (let [S-iopub	(assoc S :socket :iopub-socket)
        message	(-> (pzmq/zmq-read-raw-message zmq-comm socket 0) parse-message)]
    (log/debug (str "process-event: " :message message))
    (send-message S-iopub "status" (status-content "busy") message)
    (handler message)
    (send-message S-iopub "status" (status-content "idle") message)))

(defn- event-loop
  [{:keys [states] :as S}]
  (catching-exceptions
    (while @(:alive states)
      (process-event S))))

(defn- process-heartbeat
  [zmq-comm socket]
  (let [message (pzmq/zmq-recv zmq-comm socket)]
    (pzmq/zmq-send zmq-comm socket message)))

(defn- heartbeat-loop
  [{:keys [states zmq-comm]}]
  (catching-exceptions
    (while @(:alive states)
      (process-heartbeat zmq-comm :hb-socket))))

(defn- shell-loop
  [{:keys [nrepl-comm] :as S}]
  (let [socket        	:shell-socket
        S		(assoc S :socket socket)
        shell-handler	(configure-shell-handler S)
        sigint-handle 	(fn [] (pp/pprint (pnrepl/nrepl-interrupt nrepl-comm)))]
    (reset! (beckon/signal-atom "INT") #{sigint-handle})
    (event-loop (assoc S :handler shell-handler))))

(defn- control-loop
  [S]
  (let [socket          :control-socket
        S		(assoc S :socket socket)
        control-handler (configure-control-handler S)]
    (event-loop (assoc S :handler control-handler))))

(defn- run-kernel
  [config]
  (let [hb-addr			(address config :hb_port)
        shell-addr		(address config :shell_port)
        iopub-addr		(address config :iopub_port)
        control-addr		(address config :control_port)
        stdin-addr		(address config :stdin_port)
        key			(:key config)
        signer			(make-message-signer key)
        checker			(make-message-checker signer)]
    (let [states		(states/make-states)
          context		(zmq/context 1)
          shell-socket		(atom (doto (zmq/socket context :router)
                                        (zmq/bind shell-addr)))
          iopub-socket		(atom (doto (zmq/socket context :pub)
                                        (zmq/bind iopub-addr)))
          control-socket	(atom (doto (zmq/socket context :router)
                                        (zmq/bind control-addr)))
          stdin-socket		(atom (doto (zmq/socket context :router)
                                        (zmq/bind stdin-addr)))
          hb-socket		(atom (doto (zmq/socket context :rep)
                                        (zmq/bind hb-addr)))
          zmq-comm		(zmq-comm/make-zmq-comm shell-socket iopub-socket stdin-socket
                                                        control-socket hb-socket)]
      (with-open [nrepl-server    (start-nrepl-server)
                  nrepl-transport (nrepl/connect :port (:port nrepl-server))]
        (let [nrepl-client	(nrepl/client nrepl-transport Integer/MAX_VALUE)
              nrepl-session	(nrepl/new-session nrepl-client)
              nrepl-comm	(nrepl-comm/make-nrepl-comm nrepl-server nrepl-transport
                                                            nrepl-client nrepl-session)
              S			{:states states :zmq-comm zmq-comm :nrepl-comm nrepl-comm
                                 :signer signer :checker checker}
              status-sleep	1000]
          (catching-exceptions
              (do (future (shell-loop     S))
                  (future (control-loop   S))
                  (future (heartbeat-loop S))
                  ;; check every second if state
                  ;; has changed to anything other than alive
                  (while @(:alive states) (Thread/sleep status-sleep)))
            (do (doseq [socket [shell-socket iopub-socket control-socket hb-socket]]
                  (zmq/set-linger @socket 0)
                  (zmq/close @socket))
                (his/end-history-session (:history-session states) 5000)
                (System/exit 0))))))))

(defn -main
  [& args]
  (log/set-level! :debug)
  (run-kernel (prep-config args)))
