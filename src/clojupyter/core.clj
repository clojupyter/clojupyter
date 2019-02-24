(ns clojupyter.core
  (:require [beckon]
            [cheshire.core :as cheshire]
            [clojupyter.middleware.mime-values]
            [clojupyter.misc.zmq-comm :as zmq-comm]
            [clojupyter.misc.nrepl-comm :as nrepl-comm]
            [clojupyter.misc.states :as states]
            [clojupyter.misc.history :as his]
            [clojupyter.misc.messages :refer :all]
            [clojupyter.protocol.zmq-comm :as pzmq]
            [clojupyter.protocol.nrepl-comm :as pnrepl]
            [clojure.data.json :as json]
            [clojure.pprint :as pp]
            [clojure.stacktrace :as st]
            [clojure.string	:as str]
            [nrepl.core :as nrepl]
            [nrepl.server :as nrepl.server]
            [clojure.walk :as walk]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq])
  (:gen-class :main true))

(defonce VERSION "55")

(defn- prep-config [args]
  (-> args
      first
      slurp
      json/read-str
      walk/keywordize-keys))

(defn- address [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(def clojupyter-middleware
  '[clojupyter.middleware.mime-values/mime-values])

(defn clojupyter-nrepl-handler []
  ;; dynamically load to allow cider-jack-in to work
  ;; see https://github.com/clojure-emacs/cider-nrepl/issues/447
  (require 'cider.nrepl)
  (apply nrepl.server/default-handler
         (map resolve
              (concat (var-get (ns-resolve 'cider.nrepl 'cider-middleware))
                      clojupyter-middleware))))

(defonce ^:dynamic ^:private *NREPL-SERVER-ADDR* nil)

(defn nrepl-server-addr [] (str *NREPL-SERVER-ADDR*))

(defn start-nrepl-server []
  (let [srv (nrepl.server/start-server :handler (clojupyter-nrepl-handler))
        sock-addr (.getLocalSocketAddress (:server-socket srv))]
    (println (str "Started NREPL server on " sock-addr "."))
    (alter-var-root #'*NREPL-SERVER-ADDR* (constantly sock-addr))
    srv))

(defn- exception-handler [e]
  (log/error (with-out-str (st/print-stack-trace e 20))))

(defn- make-execute-request-handler
  [{:keys [states zmq-comm nrepl-comm socket] :as S}]
  (let [execution-count (atom 1N)]
    (fn [{:keys [message signer]}]
      (let [session-id (get-in message [:header :session])
            ident (:idents message)
            parent-header (:header message)
            code (get-in message [:content :code])
            silent (str/ends-with? code ";")]
        (send-message zmq-comm :iopub-socket "execute_input"
                      (pyin-content @execution-count message)
                      parent-header {} session-id signer)
        (let [nrepl-resp (pnrepl/nrepl-eval nrepl-comm states zmq-comm
                                            code parent-header
                                            session-id signer ident)
              {:keys [result ename traceback]} nrepl-resp
              error (if ename
                      {:status "error"
                       :ename ename
                       :evalue ""
                       :execution_count @execution-count
                       :traceback traceback})]
          (send-router-message zmq-comm :shell-socket "execute_reply"
                               (if error
                                 error
                                 {:status "ok"
                                  :execution_count @execution-count
                                  :user_expressions {}})
                               parent-header
                               session-id
                               {}
                               signer ident)
          (if error
            (send-message zmq-comm :iopub-socket "error"
                          error parent-header {} session-id signer)
            (when-not (or (= result "nil") silent)
              (send-message zmq-comm :iopub-socket "execute_result"
                            {:execution_count @execution-count
                             :data (cheshire/parse-string result true)
                             :metadata {}}
                            parent-header {} session-id signer)))
          (his/add-history (:history-session states) @execution-count code)
          (swap! execution-count inc))))))

(defn- configure-shell-handler [S]
  (let [execute-request-handler (make-execute-request-handler S)]
    (fn [message]
      (let [msg-type (get-in message [:header :msg_type])
            S (assoc S :message message :msg-type msg-type)]
        (case msg-type
          "execute_request" (execute-request-handler S)
          (respond-to-message msg-type S))))))

(defn- configure-control-handler [S]
  (fn [message]
    (let [msg-type (get-in message [:header :msg_type])]
      (respond-to-message (assoc S :message message :msg-type msg-type) msg-type))))

(defn- process-event [{:keys [states zmq-comm socket signer handler]}]
  (let [message        (pzmq/zmq-read-raw-message zmq-comm socket 0)
        parsed-message (parse-message message)
        parent-header  (:header parsed-message)
        session-id     (:session parent-header)]
    (send-message zmq-comm :iopub-socket "status"
                  (status-content "busy") parent-header {} session-id signer)
    (handler parsed-message)
    (send-message zmq-comm :iopub-socket "status"
                  (status-content "idle") parent-header {} session-id signer)))

(defn- event-loop [{:keys [states zmq-comm socket signer handler] :as S}]
  (try
    (while @(:alive states)
      (process-event S))
    (catch Exception e
      (exception-handler e))))

(defn- process-heartbeat [zmq-comm socket]
  (let [message (pzmq/zmq-recv zmq-comm socket)]
    (pzmq/zmq-send zmq-comm socket message)))

(defn- heartbeat-loop [{:keys [states zmq-comm]}]
  (try
    (while @(:alive states)
      (process-heartbeat zmq-comm :hb-socket))
    (catch Exception e
      (exception-handler e))))

(defn- shell-loop [{:keys [states zmq-comm nrepl-comm signer checker] :as S}]
  (let [socket        	:shell-socket
        S		(assoc S :socket socket)
        shell-handler	(configure-shell-handler S)
        sigint-handle 	(fn [] (pp/pprint (pnrepl/nrepl-interrupt nrepl-comm)))]
    (reset! (beckon/signal-atom "INT") #{sigint-handle})
    (event-loop (assoc S :handler shell-handler))))

(defn- control-loop [{:keys [states zmq-comm nrepl-comm signer checker] :as S}]
  (let [socket          :control-socket
        S		(assoc S :socket socket)
        control-handler (configure-control-handler S)]
    (event-loop (assoc S :handler control-handler))))

(defn- run-kernel [config]
  (let [hb-addr			(address config :hb_port)
        shell-addr		(address config :shell_port)
        iopub-addr		(address config :iopub_port)
        control-addr		(address config :control_port)
        stdin-addr		(address config :stdin_port)
        key			(:key config)
        signer			(get-message-signer key)
        checker			(get-message-checker signer)]
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
              status-sleep  1000]
          (try
            (future (shell-loop     S))
            (future (control-loop   S))
            (future (heartbeat-loop S))
            ;; check every second if state
            ;; has changed to anything other than alive
            (while @(:alive states) (Thread/sleep status-sleep))
            (catch Exception e
              (exception-handler e))
            (finally (doseq [socket [shell-socket iopub-socket control-socket hb-socket]]
                       (zmq/set-linger @socket 0)
                       (zmq/close @socket))
                     (his/end-history-session (:history-session states) 5000)
                     (System/exit 0))))))))

(defn -main [& args]
  (log/set-level! :debug)
  (run-kernel (prep-config args)))
