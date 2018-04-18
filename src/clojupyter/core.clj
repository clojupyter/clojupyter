(ns clojupyter.core
  (:require [beckon]
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
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.server :as nrepl.server]
            [clojure.walk :as walk]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq])
  (:gen-class :main true))

(defn prep-config [args]
  (-> args
      first
      slurp
      json/read-str
      walk/keywordize-keys))

(defn address [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(def clojupyter-middleware
  '[clojupyter.middleware.mime-values/mime-values])

(defn clojupyer-nrepl-handler []
  ;; dynamically load to allow cider-jack-in to work
  ;; see https://github.com/clojure-emacs/cider-nrepl/issues/447
  (require 'cider.nrepl)
  (apply nrepl.server/default-handler
         (map resolve
              (concat (var-get (ns-resolve 'cider.nrepl 'cider-middleware))
                      clojupyter-middleware))))

(defn start-nrepl-server []
  (nrepl.server/start-server
   :handler (clojupyer-nrepl-handler)))

(defn exception-handler [e]
  (log/error (with-out-str (st/print-stack-trace e 20))))

(defn configure-shell-handler [states zmq-comm nrepl-comm socket signer]
  (let [execute-request (execute-request-handler states zmq-comm nrepl-comm socket)]
    (fn [message]
      (let [msg-type (get-in message [:header :msg_type])]
        (case msg-type
          "execute_request"     (execute-request   message signer)
          "kernel_info_request" (kernel-info-reply zmq-comm
                                                   socket message signer)
          "history_request"     (history-reply     states zmq-comm
                                                   socket message signer)
          "shutdown_request"    (shutdown-reply    states zmq-comm nrepl-comm
                                                   socket message signer)
          "comm_info_request"   (comm-info-reply   zmq-comm
                                                   socket message signer)
          "comm_msg"            (comm-msg-reply    zmq-comm
                                                   socket message signer)
          "is_complete_request" (is-complete-reply zmq-comm
                                                   socket message signer)
          "complete_request"    (complete-reply    zmq-comm nrepl-comm
                                                   socket message signer)
          "comm_open"           (comm-open-reply   zmq-comm
                                                   socket message signer)
          (do
            (log/error "Message type" msg-type "not handled yet. Exiting.")
            (log/error "Message dump:" message)
            (System/exit -1)))))))

(defn configure-control-handler [states zmq-comm nrepl-comm socket signer]
  (fn [message]
    (let [msg-type (get-in message [:header :msg_type])]
      (case msg-type
        "kernel_info_request" (kernel-info-reply zmq-comm
                                                 socket message signer)
        "shutdown_request"    (shutdown-reply    states zmq-comm nrepl-comm
                                                 socket message signer)
        (do
          (log/error "Message type" msg-type "not handled yet. Exiting.")
          (log/error "Message dump:" message)
          (System/exit -1))))))

(defn process-event [states zmq-comm socket signer handler]
  (let [message        (pzmq/zmq-read-raw-message zmq-comm socket 0)
        parsed-message (parse-message message)
        parent-header  (:header parsed-message)
        session-id     (:session parent-header)]
    (send-message zmq-comm :iopub-socket "status"
                  (status-content "busy") parent-header {} session-id signer)
    (handler parsed-message)
    (send-message zmq-comm :iopub-socket "status"
                  (status-content "idle") parent-header {} session-id signer)))

(defn event-loop [states zmq-comm socket signer handler]
  (try
    (while @(:alive states)
      (process-event states zmq-comm socket signer handler))
    (catch Exception e
      (exception-handler e))))

(defn process-heartbeat [zmq-comm socket]
  (let [message (pzmq/zmq-recv zmq-comm socket)]
    (pzmq/zmq-send zmq-comm socket message)))

(defn heartbeat-loop [states zmq-comm]
  (try
    (while @(:alive states)
      (process-heartbeat zmq-comm :hb-socket))
    (catch Exception e
      (exception-handler e))))

(defn shell-loop [states zmq-comm nrepl-comm signer checker]
  (let [socket        :shell-socket
        shell-handler (configure-shell-handler states zmq-comm nrepl-comm socket signer)
        sigint-handle (fn [] (pp/pprint (pnrepl/nrepl-interrupt nrepl-comm)))]
    (reset! (beckon/signal-atom "INT") #{sigint-handle})
    (event-loop states zmq-comm socket signer shell-handler)))

(defn control-loop [states zmq-comm nrepl-comm signer checker]
  (let [socket          :control-socket
        control-handler (configure-control-handler states zmq-comm nrepl-comm socket signer)]
    (event-loop states zmq-comm socket signer control-handler)))

(defn run-kernel [config]
  (let [hb-addr      (address config :hb_port)
        shell-addr   (address config :shell_port)
        iopub-addr   (address config :iopub_port)
        control-addr (address config :control_port)
        stdin-addr   (address config :stdin_port)
        key          (:key config)
        signer       (get-message-signer key)
        checker      (get-message-checker signer)]
    (let [states  (states/make-states)
          context (zmq/context 1)
          shell-socket   (atom (doto (zmq/socket context :router)
                                 (zmq/bind shell-addr)))
          iopub-socket   (atom (doto (zmq/socket context :pub)
                                 (zmq/bind iopub-addr)))
          control-socket (atom (doto (zmq/socket context :router)
                                 (zmq/bind control-addr)))
          stdin-socket   (atom (doto (zmq/socket context :router)
                                 (zmq/bind stdin-addr)))
          hb-socket      (atom (doto (zmq/socket context :rep)
                                 (zmq/bind hb-addr)))
          zmq-comm       (zmq-comm/make-zmq-comm shell-socket iopub-socket stdin-socket
                                                 control-socket hb-socket)]
      (with-open [nrepl-server    (start-nrepl-server)
                  nrepl-transport (nrepl/connect :port (:port nrepl-server))]
        (let [nrepl-client  (nrepl/client nrepl-transport Integer/MAX_VALUE)
              nrepl-session (nrepl/new-session nrepl-client)
              nrepl-comm    (nrepl-comm/make-nrepl-comm nrepl-server nrepl-transport
                                                        nrepl-client nrepl-session)
              status-sleep  1000]
          (try
            (future (shell-loop     states zmq-comm nrepl-comm signer checker))
            (future (control-loop   states zmq-comm nrepl-comm signer checker))
            (future (heartbeat-loop states zmq-comm))
            ;; check every second if state
            ;; has changed to anything other than alive
            (while @(:alive states) (Thread/sleep status-sleep))
            (catch Exception e
              (exception-handler e))
            (finally (doseq [socket [shell-socket iopub-socket control-socket hb-socket]]
                       (zmq/set-linger @socket 0)
                       (zmq/close @socket))
                     (his/end-history-session (:history-session states) 5000)
                     (System/exit 0)
                     )))))))

(defn -main [& args]
  (log/set-level! :error)
  (run-kernel (prep-config args)))
