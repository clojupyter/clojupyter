(ns clojupyter.core-test
  "Tests nrepl evaluation and error handling."
  {:author "Peter Denno"}
  (:require [clojupyter.core :refer :all]
            [clojure.pprint :refer (pprint)]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [zeromq.zmq :as zmq]
            [clojure.tools.nrepl :as repl]
            [clojure.tools.nrepl.server :refer [stop-server]])
  (:import (clojure.lang Compiler$CompilerException)))

(defn test-start 
  "Start (e.g. from a CIDER REPL) either an NREPL (:nrepl-only? true) or a full
  kernel (no args). The latter uses information in resources/connect.json and, e.g.,
  jupyter-console --existing=$HOME/clojupyter/resources/connect.json --debug"
  [& {:keys [repl-only? nrepl-only? ]}]
  (if repl-only?
    (start-nrepl)
    (-main (-> "connect.json" io/resource io/file))))

(defn test-stop
  []
  (when-let [server @the-nrepl]
    (stop-server server)
    (reset! the-nrepl nil)))

(defn test-disconnect 
  "This can be called from jupyter to disconnect. It attempts to set things back to the way they 
   would be before -main is called. As of this writing, it won't be pretty but it WILL disconnect."
  []
  (test-stop)
  (let [pargs (-> "connect.json" io/resource io/file list prep-config)
        hb-addr (address pargs :hb_port)
        shell-addr (address pargs :shell_port)
        iopub-addr (address pargs :iopub_port)
        context (zmq/context 1)]
    (doto (:hb @jup-sockets)
      (zmq/unbind hb-addr)
      (zmq/disconnect hb-addr)
      (zmq/close))
    (doto (:sh @jup-sockets)
      (zmq/unbind shell-addr)
      (zmq/disconnect shell-addr)
      (zmq/close))
    (doto (:io @jup-sockets)
      (zmq/unbind iopub-addr)
      (zmq/disconnect iopub-addr)
      (zmq/close))))

(defn test-send-form 
  [ & {:keys [form stacktrace clone session ls]}]
  (with-open [conn (repl/connect :port (:port @the-nrepl))]
    (doall 
     (repl/combine-responses        
      (let [transport (repl/client conn 10000)
            session (or session @nrepl-session)]
        (if session
          (cond form       (repl/message transport {:op "eval" :session session :code form}),
                ls         (repl/message transport {:op "ls-sessions"}),
                stacktrace (repl/message transport {:op "stacktrace" :session session}),
                clone      (repl/message transport {:op :clone}))
          :no-session))))))
