(ns clojupyter.misc.helper-test
  (:require [clojupyter.misc.display		:as display]
            [clojupyter.misc.helper		:as helper]
            [midje.sweet					:refer [facts =>]]))

(facts "add-javascript should generate correct hiccup HTML"
       (helper/add-javascript "http://host.com/some-lib.js") =>
       (display/->HiccupHTML [:script {:src "http://host.com/some-lib.js"}]))
