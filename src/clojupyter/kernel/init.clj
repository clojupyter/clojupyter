(ns clojupyter.kernel.init
  (:require
   [clojure.pprint						:refer [pprint]]
   [taoensso.timbre				:as log]
   ,,
   [clojupyter]
   [clojupyter.kernel.state			:as state]
   [clojupyter.kernel.middleware.log-traffic	:as log-traffic]
   [clojupyter.kernel.config			:as cfg]
   [clojupyter.kernel.stacktrace		:as stacktrace]
   [clojupyter.kernel.version			:as version]))

(defn init-global-state!
  "Initializes global state. May only be called once."
  []
  (cfg/init!)
  (log/set-level! (cfg/log-level))
  (let [ver (version/version)]
    (alter-var-root #'clojupyter/*version* (constantly ver))
    (println (str "Clojupyter: Version " (:formatted-version ver) ".")))
  (when-let [config-file (cfg/config-file)]
    (println (str "Clojupyter: Configuration read from " config-file "."))
    (println (str "Clojupyter configuration: "))
    (pprint (cfg/configuration)))
  (state/set-initial-state!)
  (stacktrace/init!)
  (log-traffic/init!))

