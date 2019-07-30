(ns clojupyter.install.log
  ;; Functions whose name begins with 's*' return a single-argument function accepting and returning
  ;; a state map.
  (:require
   [io.simplect.compose						:refer [def- γ Γ π Π]]
   ,,
   [clojupyter.cmdline.api			:as cmdline]
   [clojupyter.util				:as u]))

(defn s*report-log
  "Returns a function which, given a state, updates the state with user information about messages in
  the log with level `levels`."
  ([log] (s*report-log #{:info :warn :error} log))
  ([levels log]
   (let [msgs (u/log-messages levels log)]
     (Γ (if (-> msgs count pos?)
          (Γ (cmdline/outputs [(str "Log messages (" (count msgs) "):") ""])
             (cmdline/outputs (mapv (π str "  ") msgs)))
          (cmdline/output "Nothing found in the log."))
        (cmdline/output "")))))

