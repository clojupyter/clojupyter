(defproject clojupyter/clojupyter "0.4.0-SNAPSHOT"
  :description "A Jupyter kernel for Clojure"
  :license     {:name "MIT"}
  :url         "https://github.com/clojupyter/clojupyter"
  :scm         {:name "git" :url "https://github.com/clojupyter/clojupyter"}
  :source-paths      ["src"]
  :resource-paths    ["resources"]

  :dependencies      [;; Core
                      [org.clojure/clojure "1.10.1"]
                      ;; Networking
                      [nrepl "0.8.1"]
                      [org.zeromq/jeromq "0.5.2"]
                      [org.clojure/core.async "1.3.618"]
                      [cider/cider-nrepl "0.26.0"]
                      ;; Extra
                      [camel-snake-kebab "0.4.1"]
                      [cheshire "5.10.0"]
                      [com.grammarly/omniconf "0.4.2"]
                      [clojure.java-time "0.3.2"]
                      [hiccup "1.0.5"]
                      [io.aviso/pretty "0.1.37"]        ;; Exceptions pretty printing
                      [org.clojure/data.codec "0.1.1"]  ;; Base64 encoder/decoder
                      [pandect "0.6.1"]                 ;; Message signing
                      ;; Dependencies Management
                      [com.cemerick/pomegranate "1.1.0"]
                      ;; Logging
                      [com.taoensso/timbre "5.1.2"]
                      [com.fzakaria/slf4j-timbre "0.3.19"]
                      [org.slf4j/log4j-over-slf4j "1.7.30"]
                      [org.slf4j/jul-to-slf4j "1.7.30"]
                      [org.slf4j/jcl-over-slf4j "1.7.30"]
                      ;; History
                      [org.clojure/java.jdbc "0.7.11"]
                      [org.xerial/sqlite-jdbc "3.36.0.3"]
                      ;; Framework
                      [io.pedestal/pedestal.interceptor "0.5.8"]
                      [io.simplect/compose "0.7.27"]
                      [net.cgrand/parsley "0.9.3" :exclusions [org.clojure/clojure]]
                      [org.clojars.trptcolin/sjacket "0.1.1.1" :exclusions [org.clojure/clojure net.cgrand.parsley]]
                      ;; Dev
                      [io.forward/yaml "1.0.9" :exclusions [org.flatland/ordered]]
                      [me.raynes/fs "1.4.6"]
                      [net.cgrand/regex "1.1.0" :exclusions [org.clojure/clojure net.cgrand.parsley]]
                      [org.clojure/data.json "2.3.1"]
                      [org.clojure/tools.cli "0.4.2"]
                      [org.flatland/ordered "1.5.7"]
                      [slingshot "0.12.2"]
                      [zprint "0.4.15"]]

  :profiles    {:dev     {:dependencies [[midje "1.9.9" :exclusions [org.clojure/clojure]]
                                         [org.clojure/test.check "1.1.0"]]
                          :plugins      [[lein-midje "3.2.2"]
                                         [lein-metajar "0.1.1"]]}

                :metajar {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}


  :main        clojupyter.kernel.core)
