(defproject clojupyter "0.2.2"
  :description "A Jupyter kernel for Clojure"
  :url "http://github.com/clojupyter/clojupyter"
  :license {:name "MIT"}

  ;; The aliases below can be invoked with 'lein <alias>'
  :aliases      {"clojupyter-install"           ["run" "-m" "clojupyter.misc.leiningen/clojupyter-install"]
                 "check-os-support"             ["run" "-m" "clojupyter.misc.leiningen/check-os-support"]
                 "check-install-dir"            ["run" "-m" "clojupyter.misc.leiningen/check-install-dir"]
                 "update-version-edn"           ["v" "cache" "resources" "edn"]}

  :dependencies [[beckon                        "0.1.1"]
                 [cheshire                      "5.8.1"]
                 [cider/cider-nrepl             "0.21.1"]
                 [clojure.java-time             "0.3.2"]
                 [com.cemerick/pomegranate      "1.1.0"]
                 [com.grammarly/omniconf        "0.3.2"]
                 [com.taoensso/timbre           "4.10.0"]
                 [net.cgrand/parsley            "0.9.3" :exclusions [org.clojure/clojure]]
                 [net.cgrand/regex              "1.1.0" :exclusions [org.clojure/clojure]]
                 [net.cgrand/sjacket            "0.1.1" :exclusions [org.clojure/clojure net.cgrand.parsley]]
                 [org.clojure/clojure           "1.10.0"]
                 [org.clojure/data.codec        "0.1.1"]
                 [org.clojure/data.json         "0.2.6"]
                 [org.clojure/java.jdbc         "0.7.9"]
                 [org.clojure/tools.cli         "0.4.2"]
                 [org.xerial/sqlite-jdbc        "3.25.2"]
                 [nrepl                         "0.6.0"]
                 [org.zeromq/cljzmq             "0.1.4" :exclusions [org.zeromq/jzmq]]
                 [org.zeromq/jeromq             "0.5.0"]
                 [pandect                       "0.6.1"]
                 [hiccup                        "1.0.5"]
                 [zprint                        "0.4.15"]]
  :main                         clojupyter.kernel.core
  :keep-non-project-classes     true
  :uberjar-exclusions           [#"clojupyter.*leiningen"]
  :profiles     {:dev           {:dependencies [[midje "1.9.6" :exclusions [org.clojure/clojure]]]
                                 :plugins [[lein-midje "3.2.1"]
                                           [com.roomkey/lein-v "7.0.0"]]}
                 :uberjar       {:aot :all}})
