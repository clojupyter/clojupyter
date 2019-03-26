(ns clojupyter.misc.mime-convertible-test
  (:require
   [clojupyter.misc.mime-convertible]
   [clojupyter.protocol.mime-convertible			:refer [to-mime]]
   [midje.sweet							:refer [fact]]
   [clojure.java.io				:as io])
  (:import [javax.imageio ImageIO]
           [java.awt.image BufferedImage]))

(fact "Should render strings, keywords, and numbers"
  (to-mime 1)	=> "{\"text/plain\":\"1\"}"
  (to-mime "2")	=> "{\"text/plain\":\"\\\"2\\\"\"}"
  (to-mime :1)	=> "{\"text/plain\":\":1\"}"
  (to-mime ::1)	=> "{\"text/plain\":\":clojupyter.misc.mime-convertible-test/1\"}"
  (to-mime nil)	=> "{\"text/plain\":\"nil\"}")

(fact "Should render lazy sequences"
  (to-mime (map inc [1 2 3])) => "{\"text/plain\":\"(2 3 4)\"}"
  (to-mime (map keyword ["a" "b" "c"])) => "{\"text/plain\":\"(:a :b :c)\"}")

(deftype Custom [])

(defmethod print-method Custom
  [v ^java.io.Writer w]
  (.write w "my-custom"))

(fact "Should render deftype toString"
  (to-mime (Custom.)) => "{\"text/plain\":\"my-custom\"}")

(fact "BufferedImage to-mime should at least not blow up"
  (-> (io/input-stream "images/demo.png")
      ImageIO/read
      to-mime
      string?) => true)
