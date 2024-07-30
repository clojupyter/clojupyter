(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]))

(def lib 'clojupyter/clojupyter)
(def version (format "0.4.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))
(def resource-file "resources/clojupyter/assets/version.edn")

(defn clean [_]
  (b/delete {:path "target"}))

(defn update-version [_]
  (with-open [w (io/writer resource-file)]
    (binding [*print-length* false
              *out* w]
      (pr {:version version, :raw-version (b/git-count-revs nil)}))))

(defn- pom-template [version]
  [[:description "The next generation of clojure.java.jdbc: a new low-level Clojure wrapper for JDBC-based access to databases."]
   [:url "https://github.com/clojupyter/clojupyter"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://github.com/clojupyter/clojupyter/blob/master/LICENSE.txt"]]]
   [:scm
    [:url "https://github.com/clojupyter/clojupyter"]
    [:connection "scm:git:https://github.com/clojupyter/clojupyter.git"]
    [:developerConnection "scm:git:ssh://git@github.com/clojupyter/clojupyter.git"]
    [:tag (str "v" version)]]])

(defn jar [_]
  (clean nil)
  (update-version nil)
  (b/copy-dir {:src-dirs ["resources"]
               :target-dir class-dir})

  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :pom-data  (pom-template version)})

  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file
          :basis basis})
  (println jar-file))


(defn uber [_]
  (clean nil)
  (update-version nil)
  (b/copy-dir {:src-dirs ["resources"]
               :target-dir class-dir})

  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :pom-data  (pom-template version)})

  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis})
  (println uber-file))
