(ns clojupyter.kernel.init
  (:require
   [clojure.pprint						:refer [pprint]]
   [taoensso.timbre				:as log]
   ,,
   [clojupyter]
   [clojupyter.kernel.state			:as state]
   [clojupyter.middleware.log-traffic		:as log-traffic]
   [clojupyter.misc.config			:as cfg]
   [clojupyter.kernel.stacktrace		:as stacktrace]
   [clojupyter.misc.version			:as version]))

(defn init-global-state!
  "Initializes global state. May only be called once."
  []
  (cfg/init!)
  (log/set-level! (cfg/log-level))
  (alter-var-root #'clojupyter/*clojupyter-version* (constantly (version/version)))
  (println (str "Clojupyter: Version " (version/version-string) "."))
  (when-let [config-file (cfg/config-file)]
    (println (str "Clojupyter: Configuration read from " config-file "."))
    (println (str "Clojupyter configuration: "))
    (pprint (cfg/configuration)))
  (state/set-initial-state!)
  (stacktrace/init!)
  (log-traffic/init!))

