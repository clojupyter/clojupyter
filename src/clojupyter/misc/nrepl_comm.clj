(ns clojupyter.misc.nrepl-comm
  (:require [clojupyter.misc.messages :refer :all]
            [clojure.tools.nrepl :as nrepl]
            [clojure.tools.nrepl.misc :as nrepl.misc]
            [cheshire.core :as cheshire]
            [clojupyter.protocol.nrepl-comm :as pnrepl]
            [clojupyter.protocol.zmq-comm :as pzmq]))

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
                      in-eval interrupted current-ns]
  pnrepl/PNreplComm
  (nrepl-trace [self]
    (-> (:nrepl-client self)
        (nrepl/message {:op :stacktrace
                        :session (:nrepl-session self)})
        nrepl/combine-responses
        doall)
    )
  (nrepl-interrupt [self]
    (reset! (:interrupted self) true)
    (-> (:nrepl-client self)
        (nrepl/message {:op :interrupt
                        :session (:nrepl-session self)}))
    )
  (nrepl-eval [self states zmq-comm code parent-header session-id signer ident]
    (let [pending (atom #{})
          command-id (nrepl.misc/uuid)
          result (atom {:result "nil"})
          io-sleep   10
          get-input (fn []
                      (input-request zmq-comm
                                     parent-header session-id signer ident))
          send-input (fn  [nrepl-client session pending]
                       (get-input)
                       (let [message (pzmq/zmq-read-raw-message zmq-comm :stdin-socket)
                             parsed-message (parse-message message)
                             input (get-in parsed-message [:content :value])
                             command-id (nrepl.misc/uuid)]
                         (swap! pending conj command-id)
                         (nrepl/message nrepl-client {:id command-id
                                                      :op "stdin"
                                                      :stdin (str input "\n")
                                                      :session session})))
          done?      (fn [{:keys [id status] :as msg} pending]
                       (let [pending? (@pending id)]
                         (swap! pending disj id)
                         (and (not pending?) (some #{"done"
                                                     "interrupted"
                                                     "error"}
                                                   status))))
          stdout     (fn [msg]
                       (Thread/sleep io-sleep)
                       (send-message zmq-comm :iopub-socket "stream"
                                     {:name "stdout" :text msg}
                                     parent-header {} session-id signer))
          stderr     (fn [msg]
                       (Thread/sleep io-sleep)
                       (send-message zmq-comm :iopub-socket "stream"
                                     {:name "stdout" :text msg}
                                     parent-header {} session-id signer))]
      (reset! (:in-eval self) true)
      (doseq [{:keys [ns out err status session ex value] :as msg}
              (nrepl/message (:nrepl-client self)
                             {:id command-id
                              :op :eval
                              :session (:nrepl-session self)
                              :code code})
              :while (and (not @interrupted)
                          (not (done? msg pending)))]
        (do
          (when ns (reset! (:current-ns self) ns))
          (when out (stdout out))
          (when err (stderr err))
          (when (some #{"need-input"} status)
            (send-input (:nrepl-client self) session pending))
          (when ex (swap! result assoc :ename ex))
          (when value (swap! result assoc :result value))))
      (reset! (:in-eval self) false)
      (reset! (:interrupted self) false)
      (doseq [data @(:display-queue states)]
        (send-message zmq-comm :iopub-socket "display_data"
                      {:data (cheshire/parse-string data true)
                       :metadata {}}
                      parent-header {} session-id signer))
      (reset! (:display-queue states) [])

      (when-let [ex (:ename @result)]
        (swap! result assoc :traceback
               (if (re-find #"StackOverflowError" ex) []
                   (stacktrace-string (pnrepl/nrepl-trace self)))))
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
