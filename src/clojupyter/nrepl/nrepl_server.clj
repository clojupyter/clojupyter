(ns clojupyter.nrepl.nrepl-server
  (:require
   [clojupyter.nrepl-middleware.mime-values	:as mv]))

(def clojupyter-nrepl-middleware
  `[mv/mime-values])

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

(defn nrepl-server-addr
  []
  (str *NREPL-SERVER-ADDR*))

(defn start-nrepl-server
  []
  (let [srv (nrepl.server/start-server :handler (clojupyter-nrepl-handler))
        sock-addr (.getLocalSocketAddress (:server-socket srv))]
    (println (str "Clojupyter: Started nREPL server on " sock-addr "."))
    (alter-var-root #'*NREPL-SERVER-ADDR* (constantly sock-addr))
    srv))

