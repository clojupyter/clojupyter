(ns clojupyter.kernel.history-test
  (:require [clojupyter.kernel.history :as sut]
            [midje.sweet :refer :all]
            [clojure.java.io :as io])
  (:import (java.nio.file Files Paths)))

(def test-db (str (Files/createTempDirectory
                   "test_data_"
                   (into-array java.nio.file.attribute.FileAttribute [])) "/test.db"))

(against-background
 [(around :facts (let [db (sut/init-history test-db)]
                   ?form))
  (after :facts (.delete (io/file test-db)))]

 (facts "history functions should works"

        (fact "history-init should create db file"
              (.exists (io/file test-db)) => true)
        (fact "session should start from 1"
              (let [session (sut/start-history-session db)]
                (:session-id session)
                ) => 1)
        (fact "session should increase from 1"
              (let [session_1 (sut/start-history-session db)
                    session_2 (sut/start-history-session db)
                    session_3 (sut/start-history-session db)]
                [(:session-id session_1) (:session-id session_2) (:session-id session_3)]
                ) => [1 2 3])
        (fact "session should record command history"
              (let [session (sut/start-history-session db)]
                (-> session
                    (sut/add-history 1 "t1")
                    (sut/add-history 2 "t2"))
                (sut/get-history session)
                ) => [{:session 1 :line 1 :source "t1"}
                      {:session 1 :line 2 :source "t2"}])
        (fact "end ession should truncate history to max size"
              (let [session (sut/start-history-session db)]
                (-> session
                    (sut/add-history 1 "t1")
                    (sut/add-history 2 "t2")
                    (sut/add-history 3 "t3")
                    (sut/add-history 4 "t4")
                    (sut/end-history-session 2)
                    )
                (sut/get-history session)
                ) => [{:session 1 :line 3 :source "t3"}
                      {:session 1 :line 4 :source "t4"}])
        ))
