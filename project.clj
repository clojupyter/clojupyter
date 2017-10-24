(defproject clojupyter "0.1.0"
  :description "An IPython kernel for executing Clojure code"
  :url "http://github.com/roryk/clojupyter"
  :license {:name "MIT"}
  :dependencies [[beckon "0.1.1"]
                 [cheshire "5.7.0"]
                 [cider/cider-nrepl "0.15.1"]
                 [clj-time "0.11.0"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [com.cemerick/pomegranate "0.3.1"]
                 [com.taoensso/timbre "4.8.0"]
                 [net.cgrand/parsley "0.9.3" :exclusions [org.clojure/clojure]]
                 [net.cgrand/sjacket "0.1.1" :exclusions [org.clojure/clojure
                                                          net.cgrand.parsley]]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.395"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]
                 [org.zeromq/jeromq "0.3.4"] ; "0.3.5" (modern) fails on zmq/bind.
                 [pandect "0.5.4"]]
  :aot [clojupyter.core]
  :main clojupyter.core
  :jvm-opts ["-Xmx250m"]
  :keep-non-project-classes true)
