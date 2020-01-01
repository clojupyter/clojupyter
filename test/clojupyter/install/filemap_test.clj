(ns clojupyter.install.filemap-test
  (:require [clojupyter.install.filemap :as fm :refer [filemap get-map]]
            [clojupyter.test-shared :as ts]
            [clojupyter.test-shared-generators :as shg :refer [R]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [io.simplect.compose :refer [def-]]
            [midje.sweet :as midje :refer [=> fact]]))

(def- QC-ITERS 500)

(def g-filemap-key
  shg/g-path)

(def g-filemap-value
  (gen/elements [nil :filetype/directory :filetype/file :filetype/other]))

(def g-random-filemap
  (gen/let [N (gen/choose 0 100)
            fmkeys (gen/vector g-filemap-key N)
            fmvals (gen/vector g-filemap-value N)]
    (R (filemap (into {} (map vector fmkeys fmvals))))))

(def prop--filemap-monoid
  (prop/for-all [fm1 g-random-filemap
                 fm2 g-random-filemap
                 fm3 g-random-filemap]
    ;; Base properties
    (= fm1 fm1) (= fm2 fm2) (= fm3 fm3)
    (= {} (get-map (filemap)))
    (= fm1 (filemap (get-map fm1)))
    ;; Monoid properties:
    (= fm1 (filemap fm1 (filemap)))
    (= fm1 (filemap (filemap) fm1))
    (= (filemap fm1 fm2 fm3)
       (filemap (filemap fm1 fm2) fm3)
       (filemap fm1 (filemap fm2 fm3)))))

(fact "Filemap is a monoid"
  (:pass? (tc/quick-check QC-ITERS prop--filemap-monoid))
  => true)
