(ns clojupyter.kernel.os
  (:require [clojure.string :as str]))

(def ^:private SUPPORTED-OPERATING-SYSTEMS #{:linux :macos :windows})

(defn osname [] (-> (System/getProperty "os.name") str/lower-case str/trim))

(defn- os? [idstr] (not (neg? (.indexOf ^String (osname) ^String idstr))))

(defn operating-system
  "Returns a keyword representing the operating system. Returns `nil` if the operating system is not
  known (known: `:macos`, `:linux`)."
  []
  (cond
    (os? "mac")		:macos
    (os? "linux")	:linux
    (os? "windows")	:windows
    true		nil))

(defn supported-os?
  "Returns `true` if-and-only-if the operating system is known to be supported by clojupyter, and
  `false` otherwise."
  []
  (contains? SUPPORTED-OPERATING-SYSTEMS (operating-system)))

