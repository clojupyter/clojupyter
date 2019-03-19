(ns clojupyter.misc.nrepl-comm
  (:require
   [cheshire.core			:as cheshire]
   [clojure.pprint			:as pp]
   [nrepl.core				:as nrepl]
   [nrepl.misc				:as nrepl.misc]
   [taoensso.timbre			:as log]
   [zeromq.zmq				:as zmq]
   ,,
   [clojupyter.misc.messages				:refer :all]
   [clojupyter.protocol.nrepl-comm	:as pnrepl]
   [clojupyter.protocol.zmq-comm	:as pzmq]
   [clojupyter.misc.stacktrace		:as stacktrace]
   [clojupyter.misc.util		:as u]))

(defn stacktrace-string
  "Return a nicely formatted string."
  [msg]
  (when-let [st (:stacktrace msg)]
    (let [clean (->> st
                     (filter (fn [f] (not-any? #(= "dup" %) (:flags f))))
                     (filter (fn [f] (not-any? #(= "tooling" %) (:flags f))))
                     (filter (fn [f] (not-any? #(= "repl" %) (:flags f))))
                     (filter :file))
          max-file (apply max (map count (map :file clean)))
          max-name (apply max (map count (map :name clean)))]
      (map #(format (str "%" max-file "s: %5d %-" max-name "s")
                    (:file %) (:line %) (:name %))
           clean))))

(defrecord NreplComm [nrepl-server nrepl-transport nrepl-client nrepl-session
                      interrupted need-input current-ns]
  pnrepl/PNreplComm
  (nrepl-trace [self]
    (log/debug "nrepl-trace: " :self self)
    (-> (:nrepl-client self)
        (nrepl/message {:op :stacktrace, :session (:nrepl-session self)})
        nrepl/combine-responses
        doall))
  (nrepl-interrupt [self]
    (log/debug "nrepl-interrupt: " :self self)
    (do
      (reset! interrupted true)
      (if (not @need-input)
        (-> (:nrepl-client self)
            (nrepl/message {:op :interrupt, :session (:nrepl-session self)})
            nrepl/combine-responses
            doall)
        ;; a special case here
        ;; seems like the interrupt :op
        ;; does not work when the repl server
        ;; is waiting for input
        ;; therefore do nothing and pretent (read-line)
        ;; return nil
        )
      ))
  (nrepl-eval [self {:keys [states zmq-comm signer] :as S} code parent-message]
    (log/debug "nrepl-eval: " :S S :code code :parent-message parent-message)
    (let [pending (atom #{})
          command-id (u/uuid)
          result (atom {:result "nil"})
          S-iopub	(assoc S :socket :iopub-socket)
          io-sleep   10
          get-input (fn [] (input-request S parent-message))
          pass-input-to-nrepl (fn [nrepl-client session pending]
                                (reset! need-input true)
                                (get-input)
                                ;; polling for input
                                (loop [message (pzmq/zmq-read-raw-message
                                                zmq-comm :stdin-socket
                                                (:no-block zmq/socket-options))]
                                  (let [command-id (u/uuid)]
                                    (if (not @interrupted)
                                      ;; not interrupted
                                      (if (not (nil? message))
                                        ;; got a message continue
                                        (let [parsed-message (build-message message)
                                              input (message-value parsed-message)]
                                          (swap! pending conj command-id)
                                          (log/info "got input " message)
                                          (nrepl/message nrepl-client {:id command-id
                                                                       :op "stdin"
                                                                       :stdin (str input "\n")
                                                                       :session session})
                                          (log/info "sent nrepl input" input))
                                        ;; got no message try again
                                        (recur (pzmq/zmq-read-raw-message
                                                zmq-comm :stdin-socket
                                                (:no-block zmq/socket-options))))
                                      ;; interrupted
                                      (do (nrepl/message nrepl-client {:id command-id
                                                                       :op "stdin"
                                                                       :stdin "\n"
                                                                       :session session})
                                          (reset! interrupted false)
                                          (log/info "interrupted during waiting for input")))
                                    ))
                                (reset! need-input false))
          done?      (fn [{:keys [id status] :as msg} pending]
                       (let [pending? (@pending id)]
                         (swap! pending disj id)
                         (and pending? (some #{"interrupted" "done" "error"} status))))
          stdout     (fn [msg]
                       (send-message S-iopub "stream" {:name "stdout" :text msg} parent-message))
          stderr     (fn [msg]
                       (send-message S-iopub "stream" {:name "stderr" :text msg} parent-message))]
      (doseq [{:keys [ns out err status session ex mime-tagged-value] :as msg}
              (nrepl/message (:nrepl-client self)
                             {:id command-id
                              :op :eval
                              :session (:nrepl-session self)
                              :code code})
              :while (not (done? msg pending))]
        (log/debug (str "received nrepl" :msg msg))
        (when-not @interrupted
          (log/info "nrepl status " status)
          (when ns  (reset! current-ns ns))
          (when out (stdout out))
          (when err (stderr err))
          (when ex (swap! result assoc :ename ex))
          (when mime-tagged-value (swap! result assoc :result mime-tagged-value))
          (when (some #{"need-input"} status) (pass-input-to-nrepl nrepl-client session pending))
          (Thread/sleep io-sleep)))

      ;; report and reset interrupt
      (when @interrupted (swap! result assoc :ename "interrupted"))
      (reset! interrupted false)

      ;; send display data and reset display queue
      (doseq [data @(:display-queue states)]
        (send-message S-iopub "display_data"
                      {:data (cheshire/parse-string data true), :metadata {}}
                      parent-message))
      (reset! (:display-queue states) [])

      ;; set traceback for when there are exceptions or interrupted
      (when-let [ex (:ename @result)]
        (swap! result assoc :traceback 
               (if (stacktrace/printing-stacktraces?)
                 (if (re-find #"StackOverflowError" ex)
                   ["Stack overflow error (stacktrace not available)."]
                   (stacktrace-string (pnrepl/nrepl-trace self))) 
                 ["Stacktrace disabled (enable using 'clojupyter.misc.stacktrace/set-print-stacktraces!')."])))
      (log/info "eval-result: " (with-out-str (pp/pprint @result)))
      @result))
  (nrepl-complete [self code]
    (log/debug "nrepl-complete: " :self self :code code)
    (let [ns @current-ns
          result (-> (:nrepl-client self)
                     (nrepl/message {:op :complete
                                     :session (:nrepl-session self)
                                     :symbol code
                                     :ns ns})
                     nrepl/combine-responses)]
      (->> result
           :completions
           (map :candidate)
           (into []))))
  (nrepl-doc [self sym]
    (log/debug "nrepl-doc: " :self self :sym sym)
    (let [code (str "(clojure.repl/doc " sym ")")
          result (-> (:nrepl-client self)
                     (nrepl/message {:op :eval, :code code})
                     nrepl/combine-responses)]
    (:out result))))

(defn make-nrepl-comm [nrepl-server nrepl-transport
                       nrepl-client nrepl-session]
  (NreplComm. nrepl-server nrepl-transport
              nrepl-client nrepl-session
              (atom nil) (atom nil) (atom (str *ns*))))
