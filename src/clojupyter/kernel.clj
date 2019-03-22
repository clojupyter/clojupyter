(ns clojupyter.kernel
  (:require
   [beckon]
   [clojure.pprint			:as pp		:refer [pprint]]
   [clojure.stacktrace			:as stacktrace]
   [clojure.walk			:as walk]
   [nrepl.core				:as nrepl]
   [taoensso.timbre			:as log]
   [zeromq.zmq				:as zmq]
   ,,
   [clojupyter.misc.jupyter		:as jup]
   [clojupyter.middleware		:as m]
   [clojupyter.nrepl.nrepl-comm		:as nrepl-comm]
   [clojupyter.nrepl.nrepl-comm		:as pnrepl]
   [clojupyter.nrepl.nrepl-server	:as clojupyter-nrepl-server]
   [clojupyter.kernel.state		:as state]
   [clojupyter.transport		:as tp]
   [clojupyter.transport.zmq		:as tpz]
   [clojupyter.kernel.init		:as init]
   [clojupyter.misc.util		:as u]
   )
  (:gen-class))

(defn- with-exception-logging*
  ([form finally-form]
   `(try ~form
         (catch Exception e#
           (do (log/error (with-out-str (stacktrace/print-stack-trace e# 20)))
               (throw e#)))
         (finally ~finally-form))))

(defmacro ^{:style/indent 1, :private true} with-exception-logging
  ([form]
   (with-exception-logging* form '(do)))
  ([form finally-form]
   (with-exception-logging* form finally-form)))

(defn- address
  [config service]
  (str (:transport config) "://" (:ip config) ":" (service config)))

(defn- handle-message
  [{:keys [parent-message] :as ctx}]
  (m/default-handler ctx)
  (when (= (u/message-msg-type parent-message) jup/SHUTDOWN-REQUEST)
    (state/terminate! ctx)))

(defn process-event
  ;; Not private to enable testing
  [{:keys [transport] :as proto-ctx}]
  (u/with-debug-logging ["process-event"]
    (log/debug "process-event " proto-ctx)
    (let [parent-message	(tp/receive-req transport)
          ctx			(tp/bind-parent-message proto-ctx parent-message)]
      (handle-message ctx))))

(defn- handler-loop
  [proto-ctx]
  (with-exception-logging
      (while (not (state/terminated?))
        (process-event proto-ctx))))

(defn- heartbeat-loop
  [socket]
  (with-exception-logging
      (while (not (state/terminated?))
        (zmq/send socket (zmq/receive socket)))))

(defn- mksocket
  [context addrs type nm]
  (doto (zmq/socket context type)
    (zmq/bind (get addrs nm))))

(defn- set-interrupt-handler
  [nrepl-comm]
  (reset! (beckon/signal-atom "INT") #{#(pp/pprint (pnrepl/nrepl-interrupt nrepl-comm))}))

(defn- wait-while-alive
  []
  (while (not (state/terminated?))
    (Thread/sleep 1000)))

(defn- make-sockets
  [config]
  (let [context (zmq/context 1)
        addrs   (->> [:control_port :shell_port :stdin_port :iopub_port :hb_port]
                     (map #(vector % (address config %)))
                     (into {}))]
    (map (partial apply mksocket context addrs)
         [[:router :control_port] [:router :shell_port] [:router :stdin_port]
          [:pub :iopub_port] [:rep :hb_port]])))

(defn- run-kernel
  [config]
  (let [[CT SH IN IO HB]	(make-sockets config)]
    (with-open [nrepl-server	(clojupyter-nrepl-server/start-nrepl-server)
                nrepl-conn	(nrepl/connect :port (:port nrepl-server))]
      (let [nrepl-comm		(nrepl-comm/make-nrepl-comm nrepl-server nrepl-conn)
            [signer checker]	(u/make-signer-checker (:key config))
            proto-ctx		{:nrepl-comm nrepl-comm, :signer signer, :checker checker}
            mktrans 		#(tpz/make-zmq-transport proto-ctx % IN IO)]
        (log/debug "run-kernel" proto-ctx)
        (set-interrupt-handler nrepl-comm)
        (with-exception-logging
            (do (future (handler-loop (assoc proto-ctx :transport (mktrans SH))))
                (future (handler-loop (assoc proto-ctx :transport (mktrans CT))))
                (future (heartbeat-loop HB))
                (wait-while-alive))
          (do ;; finally
            (doseq [socket [SH IO CT HB]]
              (zmq/set-linger socket 0)
              (zmq/close socket))
            (state/end-history-session)
            (System/exit 0)))))))

(defn- parse-jupyter-arglist
  [arglist]
  (-> arglist first slurp u/parse-json-str walk/keywordize-keys))

(defn -main
  [& arglist]
  (init/init-global-state!)
  (run-kernel (parse-jupyter-arglist arglist)))
