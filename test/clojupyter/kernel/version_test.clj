(ns clojupyter.kernel.version-test
  (:require
   [clojure.string				:as str]
   [clojure.test.check				:as tc]
   [clojure.test.check.generators		:as gen 	:refer [sample]]
   [clojure.test.check.properties		:as prop]
   [io.simplect.compose						:refer [def- γ Γ π Π >>-> >->>]]
   [midje.sweet							:refer [fact facts =>]]
   ,,
   [clojupyter.kernel.version			:as ver]
   ,,
   [clojupyter.test-shared					:refer :all]))

(use 'clojure.pprint)

(def- QC-ITERS 500)

(facts
 "version requires :version"
 (ver/version {})
 => nil
 (ver/version {:version "0.1.2"})
 => {:version/major 0, :version/minor 1, :version/incremental 2, :version/qualifier nil}
 (ver/version {:raw-version "abcd"})
 => nil)

(facts
 "Version data derived correctly"
 (ver/version {:version "0.1.2" :raw-version "abcd"})
 => {:version/major 0, :version/minor 1, :version/incremental 2
     :version/qualifier nil, :version/lein-v-raw "abcd"}
 (ver/version {:version "0.1.2-SNAPSHOT" :raw-version "abcd"})
 => {:version/major 0, :version/minor 1, :version/incremental 2
     :version/qualifier "SNAPSHOT", :version/lein-v-raw "abcd"}
 (ver/version {:version "0.1.2-SNAPSHOT" :raw-version "abcd-DIRTY"})
 => {:version/major 0, :version/minor 1, :version/incremental 2
     :version/qualifier "SNAPSHOT", :version/lein-v-raw "abcd-DIRTY"})

(def ^:private VER1 {:version/major 1, :version/minor 2, :version/incremental 3
                     :version/qualifier "SNAPSHOT", :version/lein-v-raw "abcd-DIRTY"})

(facts "version-string"
 (ver/version-string VER1)
 => "1.2.3-SNAPSHOT"
 (ver/version-string (dissoc VER1 :version/lein-v-raw))
 => "1.2.3-SNAPSHOT"
 (ver/version-string (dissoc VER1 :version/qualifier))
 => "1.2.3"
 (ver/version-string (dissoc VER1 :version/major :version/qualifier :version/lein-v-raw))
 => "0.2.3"
 (ver/version-string (dissoc VER1 :version/minor :version/qualifier :version/lein-v-raw))
 => "1.0.3"
 (ver/version-string (dissoc VER1 :version/incremental :version/qualifier :version/lein-v-raw))
 => "1.2.0"
  (ver/version-string (dissoc VER1 :version/qualifier :version/lein-v-raw))
 => "1.2.3")

(facts "version-string-long"
 (ver/version-string-long VER1)
 => "1.2.3-SNAPSHOT@abcd-DIRTY")

(facts "version-string-short"
 (ver/version-string-short VER1)
 => "1.2.3*"
 (ver/version-string-short (dissoc VER1 :version/lein-v-raw))
 => "1.2.3*"
 (ver/version-string-short (dissoc VER1 :version/qualifier))
 => "1.2.3*"
 (ver/version-string-short (dissoc VER1 :version/qualifier :version/lein-v-raw))
 => "1.2.3")

;;; ----------------------------------------------------------------------------------------------------
;;; TEST.CHECK TESTS
;;; ----------------------------------------------------------------------------------------------------

(def g-ver-component 
  (gen/fmap #(Math/abs %) gen/small-integer))

(def g-qualifier
  (g-nilable
   (gen/frequency [[5 (g-constant "SNAPSHOT")]
                   [3 (gen/elements ["ALPHA1" "ALPHA2" "BETA1" "BETA2" "RC1" "RC2"])]
                   [1 (gen/fmap (Γ (π apply str) str/upper-case)
                                (gen/vector gen/char-alphanumeric 3 8))]])))

(def g-lein-v
  (g-nilable
   (gen/fmap (fn [[hexvec qual]] (str hexvec qual))
             (gen/tuple (g-hex-string 4 4)
                        (g-nilable (g-constant "-DIRTY"))))))

(def g-version
  (gen/let [qual g-qualifier
            lein-v g-lein-v
            M (gen/hash-map :version/major g-ver-component
                            :version/minor g-ver-component
                            :version/incremental g-ver-component)]
    (R (merge M
              (when qual {:version/qualifier qual})
              (when lein-v {:version/lein-v-raw lein-v})))))

(def prop--version-basic
  (prop/for-all [v g-version]
    (and ((every-pred :version/major :version/minor :version/incremental) v)
         (let [qual (:version/qualifier v)]
           (==> qual (string? qual)))
         (let [lein-v (:version/lein-v-raw v)]
           (==> lein-v (and (string? lein-v)
                            (re-find #"^[\dabcdef]+(-DIRTY)?$" lein-v)))))))

(fact "version-basic"
  (fact (:pass? (tc/quick-check QC-ITERS prop--version-basic))
    => true))

