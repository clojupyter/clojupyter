(defproject clojupyter/clojupyter "0.4.0-SNAPSHOT"
  :description "A Jupyter kernel for Clojure"
  :license     {:name "MIT"}
  :url         "https://github.com/clojupyter/clojupyter"
  :scm         {:name "git" :url "https://github.com/clojupyter/clojupyter"}
  :source-paths      ["src"]
  :resource-paths    ["resources"]

  :dependencies      [;; Core
                      [org.clojure/clojure "1.10.3"]
                      ;; Networking
                      [nrepl "0.8.3"]
                      [org.zeromq/jeromq "0.5.2"]
                      [org.clojure/core.async "1.4.627"]
                      [cider/cider-nrepl "0.27.2"]
                      ;; Extra
                      [camel-snake-kebab "0.4.2"]
                      [cheshire "5.10.1"]
                      [com.grammarly/omniconf "0.4.3"]
                      [clojure.java-time "0.3.3"]
                      [hiccup "1.0.5"]
                      [io.aviso/pretty "1.1"]        ;; Exceptions pretty printing
                      [pandect "1.0.2"]                 ;; Message signing
                      ;; Dependencies Management
                      [com.cemerick/pomegranate "1.1.0"]
                      ;; Logging
                      [com.taoensso/timbre "5.1.2"]
                      [com.fzakaria/slf4j-timbre "0.3.21"]
                      [org.slf4j/log4j-over-slf4j "1.7.32"]
                      [org.slf4j/jul-to-slf4j "1.7.32"]
                      [org.slf4j/jcl-over-slf4j "1.7.32"]
                      ;; History
                      [org.clojure/java.jdbc "0.7.12"]
                      [org.xerial/sqlite-jdbc "3.36.0.3"]
                      ;; Framework
                      [io.pedestal/pedestal.interceptor "0.5.9"]
                      [io.simplect/compose "0.7.27"]
                      [net.cgrand/parsley "0.9.3" :exclusions [org.clojure/clojure]]
                      [org.clojars.trptcolin/sjacket "0.1.1.1" :exclusions [org.clojure/clojure net.cgrand.parsley]]
                      ;; Dev
                      [net.cgrand/regex "1.1.0" :exclusions [org.clojure/clojure net.cgrand.parsley]]
                      [slingshot "0.12.2"]
                      [zprint "1.1.2"]]

  :profiles    {:dev     {:dependencies [[midje "1.9.9" :exclusions [org.clojure/clojure]]
                                         [org.clojure/test.check "1.1.0"]
                                         [io.forward/yaml "1.0.9" :exclusions [org.flatland/ordered]]
                                         [me.raynes/fs "1.4.6"]
                                         [org.clojure/tools.cli "0.4.2"]
                                         [org.flatland/ordered "1.5.7"]]

                          :plugins      [[lein-midje "3.2.2"]
                                         [org.clojars.nighcoder/lein-metajar "0.1.3"]
                                         [com.roomkey/lein-v "7.2.0"]]

                          :source-paths ["dev"]}

                :metajar {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                          :aot [clojupyter.kernel.core]}}

  :main        clojupyter.kernel.core

  :aliases      {"clojupyter"            ["run" "-m" "clojupyter.cmdline"]
                 "update-version-edn"    ["v" "cache" "resources/clojupyter/assets" "edn"]})
