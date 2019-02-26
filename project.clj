(defproject clojupyter "0.1.1-SNAPSHOT"
  :description "An IPython kernel for executing Clojure code"
  :url "http://github.com/roryk/clojupyter"
  :license {:name "MIT"}
  :aot [clojupyter.core]
  :main clojupyter.core
  :jvm-opts ["-Xmx2000m"]
  :keep-non-project-classes true
  :profiles {:dev {:dependencies [[midje "1.9.6" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "3.2.1"]]}}
  :plugins				[[lein-tools-deps	"0.4.1"]]
  :middleware				[lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config		{:config-files		[:install :user :project]}
)

