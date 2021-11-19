(ns clojupyter.kernel.init
  (:require clojupyter
            [clojupyter.kernel.config :as cfg]
            [clojupyter.kernel.history :as hist]
            [clojupyter.kernel.stacktrace :as stacktrace]
            [clojupyter.log :as log]
            [clojupyter.state :as state]
            [clojupyter.util-actions :as u!]
            [clojupyter.zmq-util :as zutil]))

(defn- shutdown-hook
  []
  (try (u!/with-exception-logging
           (.close (state/zmq-context))
         (log/info "Shutdown-hook terminating."))
       (finally
         (Thread/sleep 10)
         (shutdown-agents))))

(defn- setup-shutdown-hook
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. shutdown-hook)))

(defn init!
  "Initializes global state."
  []
  (cfg/init!)
  (log/init!)
  (let [ver clojupyter/*version*]
    (log/info (str "Clojupyter version " ver ".")))
  (when-let [config-file (cfg/config-file)]
    (log/info (str "Configuration read from " config-file "."))
    (log/info (str "Configuration: ") (cfg/configuration)))
  (swap! state/STATE assoc :history-session (hist/start-history-session (hist/init-history)))
  (swap! state/STATE assoc :zmq-context (zutil/zcontext))
  (swap! state/STATE assoc :kernel-id (u!/uuid))
  (stacktrace/init!)
  (setup-shutdown-hook))
