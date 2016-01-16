(defproject ipython-clojure "0.1.0-SNAPSHOT"
  :description "An IPython kernel for executing Clojure code"
  :url "http://github.com/roryk/ipython-clojure"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.zeromq/jeromq "0.3.3"]
                 [cheshire "5.3.1"]
                 [clj-time "0.7.0"]
                 [pandect "0.5.3"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]
                 [org.clojure/data.json "0.2.4"]]
  :aot [ipython-clojure.core]
  :main ipython-clojure.core
  :jvm-opts ["-Xmx250m"]
  :keep-non-project-classes true)
