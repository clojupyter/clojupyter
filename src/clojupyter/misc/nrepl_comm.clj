(ns clojupyter.misc.nrepl-comm
  (:require [cheshire.core :as cheshire]
            [clojupyter.protocol.nrepl-comm :as pnrepl]
            [clojupyter.misc.messages :refer :all]
            [clojupyter.protocol.zmq-comm :as pzmq]
            [clojure.pprint :as pp]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.misc :as nrepl.misc]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]))

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
    (-> (:nrepl-client self)
        (nrepl/message {:op :stacktrace
                        :session (:nrepl-session self)})
        nrepl/combine-responses
        doall))
  (nrepl-interrupt [self]
    (do
      (reset! interrupted true)
      (if (not @need-input)
        (-> (:nrepl-client self)
            (nrepl/message {:op :interrupt
                            :session (:nrepl-session self)})
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
  (nrepl-eval [self states zmq-comm code parent-header session-id signer ident]
    (let [pending (atom #{})
          command-id (nrepl.misc/uuid)
          result (atom {:result "nil"})
          io-sleep   10
          get-input (fn [] (input-request zmq-comm parent-header session-id signer ident))
          pass-input-to-nrepl (fn [nrepl-client session pending]
                                (reset! need-input true)
                                (get-input)
                                ;; polling for input
                                (loop [message (pzmq/zmq-read-raw-message
                                                zmq-comm :stdin-socket
                                                (:no-block zmq/socket-options))]
                                  (let [command-id (nrepl.misc/uuid)]
                                    (if (not @interrupted)
                                      ;; not interrupted
                                      (if (not (nil? message))
                                        ;; got a message continue
                                        (let [parsed-message (parse-message message)
                                              input (get-in parsed-message [:content :value])]
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
                         (and pending? (some #{"interrupted" "done" "error"} status))
                         )
                       )
          stdout     (fn [msg]
                       (send-message zmq-comm :iopub-socket "stream"
                                     {:name "stdout" :text msg}
                                     parent-header {} session-id signer))
          stderr     (fn [msg]
                       (send-message zmq-comm :iopub-socket "stream"
                                     {:name "stdout" :text msg}
                                     parent-header {} session-id signer))]
      (doseq [{:keys [ns out err status session ex value] :as msg}
              (nrepl/message (:nrepl-client self)
                             {:id command-id
                              :op :eval
                              :session (:nrepl-session self)
                              :code code})
              :while (not (done? msg pending))]
        (if (not @interrupted)
          (do
            (log/info "nrepl status " status)
            (when ns  (reset! current-ns ns))
            (when out (stdout out))
            (when err (stderr err))
            (when ex (swap! result assoc :ename ex))
            (when value (swap! result assoc :result value))
            (when (some #{"need-input"} status) (pass-input-to-nrepl
                                                 nrepl-client session pending))
            (Thread/sleep io-sleep)
            )
          )
        )

      ;; report and reset interrupt
      (when @interrupted (swap! result assoc :ename "interrupted"))
      (reset! interrupted false)

      ;; send display data and reset display queue
      (doseq [data @(:display-queue states)]
        (send-message zmq-comm :iopub-socket "display_data"
                      {:data (cheshire/parse-string data true)
                       :metadata {}}
                      parent-header {} session-id signer))
      (reset! (:display-queue states) [])

      ;; set traceback for when there are exceptions or interrupted
      (when-let [ex (:ename @result)]
        (swap! result assoc :traceback
               (if (re-find #"StackOverflowError" ex) []
                   (stacktrace-string (pnrepl/nrepl-trace self)))))
      (log/info "eval-result: " (with-out-str (pp/pprint @result)))
      @result))
  (nrepl-complete [self code]
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
           (into [])))))

(defn make-nrepl-comm [nrepl-server nrepl-transport
                       nrepl-client nrepl-session]
  (NreplComm. nrepl-server nrepl-transport
              nrepl-client nrepl-session
              (atom nil) (atom nil) (atom (str *ns*))))
