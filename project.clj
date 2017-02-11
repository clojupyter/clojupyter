(defproject clojupyter "0.1.0-SNAPSHOT"
  :description "An IPython kernel for executing Clojure code"
  :url "http://github.com/roryk/clojupyter"
  :license {:name "MIT"}
  :dependencies [[com.cemerick/pomegranate "0.3.1"]
                 [beckon "0.1.1"]
                 [cheshire "5.7.0"]
                 [cider/cider-nrepl "0.10.2"]
                 [clj-time "0.11.0"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [compliment "0.3.2"]
                 [fipp "0.6.4"]
                 [incanter "1.5.7"]
                 [incanter/jfreechart "1.0.13-no-gnujaxp"]
                 [mvxcvi/puget "1.0.0"]
                 [net.cgrand/sjacket "0.1.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]
                 [org.zeromq/jeromq "0.3.4"] ; "0.3.5" (modern) fails on zmq/bind.
                 [spyscope "0.1.5"]
                 [pandect "0.5.4"]]
  :aot [clojupyter.core]
  :main clojupyter.core
  :jvm-opts ["-Xmx250m"]
  :keep-non-project-classes true)
