(defproject clojupyter "0.4.0"
  :description "A Jupyter kernel for Clojure"
  :license     {:name "MIT"}
  :url         "https://github.com/clojupyter/clojupyter"

  :scm         {:name "git" :url "https://github.com/clojupyter/clojupyter"}
  :source-paths      [] ;; provided by lein-tools-deps
  :resource-paths    [] ;; provided by lein-tools-deps
  :profiles    {:dev     {:dependencies [[midje "1.9.9" :exclusions [org.clojure/clojure]]
                                         [me.raynes/fs "1.4.6"]
                                         [io.forward/yaml "1.0.10" :exclusions [org.flatland/ordered]]
                                         [org.flatland/ordered "1.5.9"]
                                         [org.clojure/tools.cli "0.4.2"]
                                         [org.clojure/test.check "1.1.0"]]
                          :plugins      [[lein-midje "3.2.2"]
                                         [lein-metajar "0.1.1"]]
                          :source-paths ["dev"]}

                :uberjar {:aot :all}
                :metajar {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}

  :main        clojupyter.kernel.core

  ;; Use tools-deps for dependencies:
  :plugins     [[lein-tools-deps "0.4.5"]]
  :middleware  [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config  {:config-files [:install :user :project]}

  ;; The aliases below can be invoked with 'lein <alias>':
  :aliases      {"clojupyter"           ["run" "-m" "clojupyter.cmdline"]})
