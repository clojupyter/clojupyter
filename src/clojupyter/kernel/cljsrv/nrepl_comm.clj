(ns clojupyter.kernel.cljsrv.nrepl-comm
  (:require
   [clojure.pprint			:as pp		:refer [pprint]]
   [nrepl.core				:as nrepl]
   [taoensso.timbre			:as log]
   ,,
   [clojupyter.kernel.jupyter		:as jup]
   [clojupyter.kernel.stacktrace	:as stacktrace]
   [clojupyter.kernel.state		:as state]
   [clojupyter.kernel.transport		:as tp]
   [clojupyter.kernel.util		:as u]
   ))

(defprotocol nrepl-comm-proto
  (nrepl-trace [self])
  (nrepl-interrupt [self])
  (nrepl-eval [self zt code])
  (nrepl-complete [self code])
  (nrepl-doc [self sym]))

(defn- stacktrace-string
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

(defn- nrepl-trace*
  [self]
  (u/with-debug-logging ["nrepl-trace: " :self self]
      (-> (:nrepl-client self)
          (nrepl/message {:op :stacktrace, :session (:nrepl-session self)})
          nrepl/combine-responses
          doall)))

(defn- nrepl-interrupt*
  [self interrupted need-input]
  (u/with-debug-logging ["nrepl-interrupt: " :self self]
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
      ;; therefore do nothing and pretend (read-line)
      ;; return nil
      )))

(defn- nrepl-complete*
  [self code current-ns]
  (u/with-debug-logging ["nrepl-complete: " :self self :code code]
    (->> (do (-> (:nrepl-client self)
                 (nrepl/message {:op :complete, :session (:nrepl-session self)
                                 :symbol code, :ns @current-ns})
                 nrepl/combine-responses))
         :completions
         (mapv :candidate))))

(defn- nrepl-doc*
  [{:keys [nrepl-session nrepl-client] :as self} sym]
  (u/with-debug-logging ["nrepl-doc" sym]
    (let [code (format "(clojure.core/with-out-str (clojure.repl/doc %s))" sym)]
      (apply str (-> nrepl-client
                     (nrepl/message {"op" "eval", "code" code})
                     nrepl/response-values)))))

;;; ----------------------------------------------------------------------------------------------------
;;; NREPL EVAL
;;; ----------------------------------------------------------------------------------------------------

(defn- done?
  [{:keys [id status] :as msg} pending]
  (let [pending? (@pending id)]
    (swap! pending disj id)
    (and pending? (some #{"interrupted" "done" "error"} status))))

(defn- std-out-err
  [zt message stream-name]
  (tp/send-iopub zt "stream" {:name stream-name :text message}))

(defn- stdout
  [zt message]
  (std-out-err zt message "stdout" ))

(defn- stderr
  [zt message]
  (std-out-err zt message "stderr"))

(defn- message? [v] (not (nil? v)))

(defn- input-request
  [{:keys [transport] :as ctx}]
  (tp/send-stdin transport jup/INPUT-REQUEST {:prompt ">> ", :password false}))

(defn- pass-input-to-nrepl
  [zt nrepl-client session interrupted need-input pending]
  (reset! need-input true)
  ;; SEND INPUT REQUEST
  (input-request zt)
  ;; READ MESSAGES UNTIL INPUT REPLY ARRIVES
  (loop [message (tp/receive-stdin zt)]
    (let [command-id (u/uuid)]
      (if @interrupted
        (do (log/info "interrupted during waiting for input")
            (nrepl/message nrepl-client {:id command-id, :op "stdin",
                                         :stdin "\n", :session session})
            (reset! interrupted false))
        (do (if (message? message) ;; MESSAGE ARRIVED?
              ,, (let [input (jup/message-value message)]
                   (swap! pending conj command-id)
                   (log/info "got input " message)
                   (nrepl/message nrepl-client {:id command-id, :op "stdin",
                                                :stdin (str input "\n"),
                                                :session session})
                   (log/info "sent nrepl input" input))
                ,, (recur (tp/receive-stdin zt)))))))
  (reset! need-input false)
  :ok)

(defn- reset-interrupt!
  [interrupted result]
  (when @interrupted (swap! result assoc :ename "interrupted"))
  (reset! interrupted false))

(defn- send-and-reset-display-queue!
  [zt]
  (doseq [data (state/get-and-clear-display-queue!)]
    (tp/send-iopub zt "display_data" {:data (u/parse-json-str data true), :metadata {}})))

(defn- set-stacktrace!
  [self result]
  (when-let [ex (:ename @result)]
    (swap! result assoc :traceback
           (if (stacktrace/printing-stacktraces?)
             (if (re-find #"StackOverflowError" ex)
               ["Stack overflow error (stacktrace not available)."]
               (stacktrace-string (nrepl-trace self)))
             ["Stacktrace disabled (enable using `clojupyter.kernel.stacktrace/set-print-stacktraces!`)."]))))

(defn- react-to-message!
  [zt {:keys [ns out err status session ex mime-tagged-value] :as msg}
   nrepl-client current-ns interrupted need-input pending result]
  (log/debug (str "received nrepl" :msg msg))
  (when-not @interrupted
    (log/info "nrepl status " status)
    (when ns  (reset! current-ns ns))
    (when out (stdout zt out))
    (when err (stderr zt err))
    (when ex (swap! result assoc :ename ex))
    (when mime-tagged-value (swap! result assoc :result mime-tagged-value))
    (when (some #{"need-input"} status)
      (pass-input-to-nrepl zt nrepl-client session interrupted need-input pending))
    (Thread/sleep 10)))

(defn- nrepl-eval*
  [self zt code nrepl-client current-ns interrupted need-input]
  (u/with-debug-logging ["nrepl-eval: " :code code]
    (let [pending	(atom #{})
          command-id	(u/uuid)
          result	(atom {:result nil})]
      (doseq [msg (nrepl/message (:nrepl-client self)
                                 {:id command-id
                                  :op :eval
                                  :session (:nrepl-session self)
                                  :code code})
              :while (not (done? msg pending))]
        (react-to-message! zt msg nrepl-client current-ns interrupted need-input pending result))
      (reset-interrupt! interrupted result)
      (send-and-reset-display-queue! zt)
      (set-stacktrace! self result)
      (log/info "eval-result: " (with-out-str (pprint @result)))
      @result)))

(defrecord NreplComm [nrepl-server nrepl-transport nrepl-client nrepl-session
                      interrupted need-input current-ns]
  nrepl-comm-proto
  (nrepl-trace [self]
    (nrepl-trace* self))
  (nrepl-interrupt [self]
    (nrepl-interrupt* self interrupted need-input))
  (nrepl-complete [self code]
    (nrepl-complete* self code current-ns))
  (nrepl-doc [self sym]
    (nrepl-doc* self sym))
  (nrepl-eval [self zt code]
    (nrepl-eval* self zt code nrepl-client current-ns interrupted need-input)))

(alter-meta! #'->NreplComm #(assoc % :private true))

(defn make-nrepl-comm
  [nrepl-server nrepl-transport]
  (let [nrepl-client	(nrepl/client nrepl-transport Integer/MAX_VALUE)
        nrepl-session	(nrepl/new-session nrepl-client)]
    (->NreplComm nrepl-server nrepl-transport
                 nrepl-client nrepl-session
                 (atom nil) (atom nil) (atom (str *ns*)))))
