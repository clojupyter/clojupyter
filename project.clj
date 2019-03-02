(defproject clojupyter "0.2.1-SNAPSHOT"
  :description "An IPython kernel for executing Clojure code"
  :url "http://github.com/roryk/clojupyter"
  :license {:name "MIT"}
  :dependencies [[beckon			"0.1.1"]
                 [cheshire			"5.8.1"]
                 [cider/cider-nrepl		"0.20.0"]
                 [clj-time			"0.15.0"]
                 [com.cemerick/pomegranate 	"1.1.0"]
                 [com.taoensso/timbre 		"4.10.0"]
                 [net.cgrand/parsley 		"0.9.3" :exclusions [org.clojure/clojure]]
                 [net.cgrand/regex 		"1.1.0" :exclusions [org.clojure/clojure]]
                 [net.cgrand/sjacket 		"0.1.1" :exclusions [org.clojure/clojure net.cgrand.parsley]]
                 [org.clojure/clojure 		"1.10.0"]
                 [org.clojure/data.codec 	"0.1.1"]
                 [org.clojure/data.json		"0.2.6"]
                 [org.clojure/java.jdbc		"0.7.9"]
                 [org.xerial/sqlite-jdbc	"3.25.2"]
                 [nrepl 			"0.5.3"]
                 [org.zeromq/cljzmq 		"0.1.4" :exclusions [org.zeromq/jzmq]]
                 [org.zeromq/jeromq 		"0.5.0"]
                 [pandect			"0.6.1"]
                 [hiccup 			"1.0.5"]]
  :aot [clojupyter.core]
  :main clojupyter.core
  :jvm-opts ["-Xmx2000m"]
  :keep-non-project-classes true

  :profiles {:dev {:dependencies [[midje "1.9.6" :exclusions [org.clojure/clojure]]]
                   :plugins [[lein-midje "3.2.1"]]}}
  )


