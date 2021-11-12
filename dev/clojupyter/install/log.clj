(ns clojupyter.install.log
  (:require [clojupyter.cmdline.api :as cmdline]
            [clojupyter.tools :as u]
            [io.simplect.compose :refer [C p]]))

(defn s*report-log
  "Returns a function which, given a state, updates the state with user information about messages in
  the log with level `levels`."
  ([log] (s*report-log #{:info :warn :error} log))
  ([levels log]
   (let [msgs (u/log-messages levels log)]
     (C (if (-> msgs count pos?)
          (C (cmdline/outputs [(str "Log messages (" (count msgs) "):") ""])
             (cmdline/outputs (mapv (p str "  ") msgs)))
          (cmdline/output "Nothing found in the log."))
        (cmdline/output "")))))

