(ns clojupyter.kernel.cljsrv
  (:require
   [clojure.pprint			:as pp]
   [io.simplect.compose					:refer [def-]]
   [nrepl.core				:as nrepl]
   [nrepl.server]
   ,,
   [clojupyter.messages		:as msgs]
   [clojupyter.kernel.nrepl-middleware	:as mw]
   [clojupyter.log			:as log]
   [clojupyter.util-actions		:as u!]
   [clojupyter.util :as u]))

(defprotocol nrepl-server-proto
  (nrepl-complete [self code])
  (nrepl-doc [self sym])
  (nrepl-eval [self jup-receive jup-send code])
  (nrepl-get-input [self jup-receive jup-send])
  (nrepl-interrupt [self])
  (nrepl-server-addr [self])
  (nrepl-trace [self]))

(def clojupyter-nrepl-middleware
  `[mw/mime-values])

(defn clojupyter-nrepl-handler
  []
  ;; dynamically load to allow cider-jack-in to work
  ;; see https://github.com/clojure-emacs/cider-nrepl/issues/447
  (require 'cider.nrepl)
  (apply nrepl.server/default-handler
         (map resolve
              (concat (var-get (ns-resolve 'cider.nrepl 'cider-middleware))
                      clojupyter-nrepl-middleware))))

(defonce ^:dynamic ^:private *NREPL-SERVER-ADDR* nil)

(defn start-nrepl-server
  []
  (let [srv (nrepl.server/start-server :handler (clojupyter-nrepl-handler))
        sock-addr (.getLocalSocketAddress ^java.net.ServerSocket (:server-socket srv))]
    (log/info (str "Started nREPL server on " sock-addr "."))
    (alter-var-root #'*NREPL-SERVER-ADDR* (constantly sock-addr))
    srv))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; NREPL-SERVER
;;; ------------------------------------------------------------------------------------------------------------------------

(defn final-message?
  [m]
  (some #{"interrupted" "done" "error"} (:status m)))

(defn need-input-message?
  [m]
  (and (map? m) (contains? (into #{} (:status m)) "need-input")))

(defn exception-message?
  [m]
  (and (map? m) (find m :ex)))

(defn- nrepl-server-addr*
  []
  (str *NREPL-SERVER-ADDR*))

(defn- nrepl-trace*
  [{:keys [nrepl-client]}]
  (-> nrepl-client
      (nrepl/message {:op :stacktrace})
      nrepl/combine-responses
      doall))

(defn- nrepl-interrupt*
  [{:keys [nrepl-client]}]
  (-> nrepl-client
      (nrepl/message {:op :interrupt})
      nrepl/combine-responses
      doall))

(defn- nrepl-complete*
  [{:keys [nrepl-client]} code]
  (->> (nrepl/message nrepl-client {:op :complete, :symbol code})
       nrepl/combine-responses
       :completions
       (mapv :candidate)))

(defn- nrepl-doc*
  [{:keys [nrepl-client]} sym]
  (let [code (format "(clojure.core/with-out-str (clojure.repl/doc %s))" sym)]
    (apply str (-> nrepl-client
                   (nrepl/message {"op" "eval", "code" code})
                   nrepl/response-values))))

(defn- nrepl-get-input*
  [jup-receive jup-send]
  (jup-send :stdin_port msgs/INPUT-REQUEST (msgs/input-request-content "Enter value: "))
  (msgs/message-value (:req-message (jup-receive :stdin_port))))


(defn- stdin-message-content
  [s]
  {:op "stdin" :stdin (str s \newline)})

(defn- eval-reducer
  "Returns a reducing function for processing nrepl-messages by reacting to `need-input` responses
  from the nrepl-server by calling `get-input` which must be a zero-argument function returning a
  string entered by the user in response to the input request.  `client` must an nrepl-client with a
  session and sufficiently long timeout."
  [client get-input]
  (fn [[state Σ] msg]
    (let [Σ' (conj Σ msg)]
      (cond
        (final-message? msg)		(reduced [state Σ'])
        (exception-message? msg)	[(assoc state ::need-stacktrace? true) Σ']
        (need-input-message? msg)	(do (->> (get-input) stdin-message-content (nrepl/message client))
                                            ;; we drop `need-input` messages as they are handled here:
                                            [state Σ])
        :else				[state Σ']))))

(defn- nrepl-eval*
  [{:keys [nrepl-client] :as self} jup-receive jup-send code]
  (let [get-input #(nrepl-get-input self jup-receive jup-send)
        msgs (nrepl/message nrepl-client {:id (u!/uuid), :op :eval, :code code})
        init-state {}, init-Σ []
        [state Σ] (reduce (eval-reducer nrepl-client get-input) [init-state init-Σ] msgs)]
    (merge {:nrepl-messages Σ}
           (when (find state ::need-stacktrace?)
             {:trace-result (nrepl-trace self)}))))

(def- FMT "#ClojureServer")

(defrecord ClojureServer [nrepl-server nrepl-transport nrepl-base-client nrepl-client]
  nrepl-server-proto
  (nrepl-complete [self code]
    (nrepl-complete* self code))
  (nrepl-doc [self sym]
    (nrepl-doc* self sym))
  (nrepl-eval [self jup-receive jup-send code]
    (nrepl-eval* self jup-receive jup-send code))
  (nrepl-get-input [self jup-receive jup-send]
    (nrepl-get-input* jup-receive jup-send))
  (nrepl-interrupt [self]
    (nrepl-interrupt* self))
  (nrepl-server-addr [self]
    (nrepl-server-addr*))
  (nrepl-trace [self]
    (nrepl-trace* self))
  Object
  (toString [_] FMT)
  java.io.Closeable
  (close [_]
    (nrepl.server/stop-server nrepl-server)))

(defn cljsrv?
  [v]
  (instance? ClojureServer v))

(u!/set-var-private! #'->ClojureServer)

(u/define-simple-record-print ClojureServer (constantly FMT))

(defn make-cljsrv ^ClojureServer
  []
  (let [nrepl-server		(start-nrepl-server)
        nrepl-transport		(nrepl/connect :port (:port nrepl-server))
        nrepl-base-client	(nrepl/client nrepl-transport Integer/MAX_VALUE)
        nrepl-client		(nrepl/client-session nrepl-base-client)]
    (->ClojureServer nrepl-server nrepl-transport nrepl-base-client nrepl-client)))
