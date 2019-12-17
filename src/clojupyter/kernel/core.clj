(ns clojupyter.kernel.core
  (:gen-class)
  (:require beckon
            [clojupyter.kernel.cljsrv :as cljsrv]
            [clojupyter.kernel.config :as config]
            [clojupyter.kernel.handle-event-process :as hep :refer [start-handle-event-process]]
            [clojupyter.kernel.init :as init]
            [clojupyter.kernel.jup-channels :refer [jup? make-jup]]
            [clojupyter.jupmsg-specs :as jsp]
            [clojupyter.log :as log]
            [clojupyter.messages :as msgs]
            [clojupyter.messages-specs :as msp]
            [clojupyter.shutdown :as shutdown]
            [clojupyter.state :as state]
            [clojupyter.util :as u]
            [clojupyter.util-actions :as u!]
            [clojupyter.zmq :as cjpzmq]
            [clojupyter.zmq.heartbeat-process :as hb]
            [clojure.core.async :as async :refer [<!! buffer chan]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :refer [instrument]]
            [clojure.walk :as walk]
            [io.simplect.compose :refer [def- curry c C p P >->> >>->]]
            ))

(def- address
  (curry 2 (fn [config service]
             (let [svc (service config)]
               (assert svc (str "core/address: " service " not found"))
               (str (:transport config) "://" (:ip config) ":" svc)))))

(defn- set-interrupt-handler
  [cljsrv]
  (reset! (beckon/signal-atom "INT")
          [#(log/debug "interrupt received")
           #(cljsrv/nrepl-interrupt cljsrv)]))

(defn run-kernel
  [jup term cljsrv]
  (state/ensure-initial-state!)
  (u!/with-exception-logging
      (let [proto-ctx {:cljsrv cljsrv, :jup jup, :term term}]
        (set-interrupt-handler cljsrv)
        (start-handle-event-process proto-ctx))))

(s/fdef run-kernel
  :args (s/cat :jup jup?, :term shutdown/terminator?, :cljsrv cljsrv/cljsrv?))
(instrument `run-kernel)

(def- LOG-COUNTER
  "Enumerates all transducer output, showing the order of events."
  (atom 0))

(defn- logging-transducer
  [id]
  (fn [v]
    (when (config/log-traffic?)
      (log/debug (str "logxf." (swap! LOG-COUNTER inc) "(" id "):") (log/ppstr v)))
    v))

(defn- wrap-skip-shutdown-tokens
  [f]
  (fn [v]
    (if (shutdown/is-token? v)
      v
      (f v))))

(defn- inbound-channel-transducer
  [port checker]
  (u!/wrap-report-and-absorb-exceptions
   (msgs/transducer-error port)
   (C (wrap-skip-shutdown-tokens (C (p msgs/frames->jupmsg checker)
                                    (p msgs/jupmsg->kernelreq port)))
      (logging-transducer (str "INBOUND:" port)))))

(defn- outbound-channel-transducer
  [port signer]
  (u!/wrap-report-and-absorb-exceptions
   (msgs/transducer-error port)
   (C (wrap-skip-shutdown-tokens (p msgs/kernelrsp->jupmsg port signer))
      (logging-transducer (str "OUTBOUND:" port))
      (wrap-skip-shutdown-tokens msgs/jupmsg->frames))))

(defn- start-zmq-socket-forwarding
  "Starts threads forwarding traffic between ZeroMQ sockets and core.async channels.  Returns a
  2-tuple of `jup` and `term` which respectively provide access to communicating with Jupyter and
  terminating Clojupyter."
  [ztx config]
  (u!/with-exception-logging
      (let [bufsize 25 ;; leave plenty of space - we don't want blocking on termination
            term (shutdown/make-terminator bufsize)
            get-shutdown (partial shutdown/notify-on-shutdown term)
            sess-key (s/assert ::msp/key (:key config))
            [signer checker] (u/make-signer-checker sess-key)
            in-ch (fn [port] (get-shutdown (chan (buffer bufsize)
                                                 (map (inbound-channel-transducer port checker)))))
            out-ch (fn [port] (get-shutdown (chan (buffer bufsize)
                                                  (map (outbound-channel-transducer port signer)))))]
        (letfn [(start-fwd [port addr sock-type]
                  (cjpzmq/start ztx port addr term
                                {:inbound-ch (in-ch port), :outbound-ch (out-ch port),
                                 :zmq-socket-type sock-type}))]
          (let [[ctrl-in ctrl-out]	(let [port :control_port]
                                          (start-fwd port (address config port) :router))
                [shell-in shell-out]	(let [port :shell_port]
                                          (start-fwd port (address config port) :router))
                [iopub-in iopub-out]	(let [port :iopub_port]
                                          (start-fwd port (address config port) :pub))
                [stdin-in stdin-out]	(let [port :stdin_port]
                                          (start-fwd port (address config port) :dealer))
                jup			(make-jup ctrl-in  ctrl-out
                                                  shell-in shell-out
                                                  iopub-in iopub-out
                                                  stdin-in stdin-out)]
            (hb/start-hb ztx (address config :hb_port) term)
            [jup term])))
      (log/debug "start-zmq-socket-fwd returning")))

(defn- start-clojupyter
  "Starts Clojupyter including threads forwarding traffic between ZMQ sockets and core.async channels."
  [ztx config]
  (u!/with-exception-logging
      (do (log/info "Clojupyter config" (log/ppstr config))
          (when-not (s/valid? ::msp/jupyter-config config)
            (log/error "Command-line arguments do not conform to specification."))
          (init/ensure-init-global-state!)
          (let [[jup term] (start-zmq-socket-forwarding ztx config)
                wait-ch	(shutdown/notify-on-shutdown term (chan 1))]
            (with-open [cljsrv (cljsrv/make-cljsrv)]
              (run-kernel jup term cljsrv)
              (<!! wait-ch)
              (log/debug "start-clojupyter: wait-signal received"))))
      (log/debug "start-clojupyter returning")))

(defn- finish-up
  []
  (state/end-history-session))

(defn- parse-jupyter-arglist
  [arglist]
  (-> arglist first slurp u/parse-json-str walk/keywordize-keys))

(defn -main
  "Main entry point for Clojupyter when spawned by Jupyter.  Creates the ZeroMQ context which lasts
  the entire life of the process."
  ;; When developing it is useful to be able to create the ZMQ context once and for all. This is the
  ;; key distinction between `-main` and `start-clojupyter` which assumes that the ZMQ context has
  ;; already been created.
  [& arglist]
  (init/ensure-init-global-state!)
  (log/debug "-main starting" (log/ppstr {:arglist arglist}))
  (try (let [ztx (state/zmq-context)
             config (parse-jupyter-arglist arglist)]
         (start-clojupyter ztx config))
       (finish-up)
       (finally
         (log/info "Clojupyter terminating (sysexit)")
         (Thread/sleep 100)
         (System/exit 0))))

(instrument `run-kernel)
