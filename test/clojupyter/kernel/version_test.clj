(ns clojupyter.kernel.version-test
  (:require
   [midje.sweet							:refer [fact facts =>]]
   ,,
   [clojupyter.kernel.util			:as u]
   [clojupyter.kernel.version			:as ver]))

(def ^:private select-stdvals (juxt :version :major :minor :incremental :qualifier
                                    :qual-suffix :full-version :formatted-version))
(def ^:private test-version-map (u/rcomp ver/version-map select-stdvals))
(def ^:private tvmap test-version-map)

(facts
 "version requires both :version and :raw-version"
 (ver/version-map {})
 => nil
 (ver/version-map {:version "0.1.2"})
 => nil
 (ver/version-map {:raw-version "abcd"})
 => nil)

(facts
 "Version data derived correctly"
 (tvmap {:version "0.1.2" :raw-version "abcd"})
 => ["0.1.2" 0 1 2 nil nil "0.1.2" "clojupyter-v0.1.2"]
 (tvmap {:version "0.1.2-SNAPSHOT" :raw-version "abcd"})
 => ["0.1.2-SNAPSHOT" 0 1 2 "SNAPSHOT" "abcd" "0.1.2-SNAPSHOT@abcd" "clojupyter-v0.1.2-SNAPSHOT@abcd"]
 (tvmap {:version "0.1.2-SNAPSHOT" :raw-version "abcd-DIRTY"})
 => ["0.1.2-SNAPSHOT" 0 1 2 "SNAPSHOT" "abcd#" "0.1.2-SNAPSHOT@abcd#" "clojupyter-v0.1.2-SNAPSHOT@abcd#"])

