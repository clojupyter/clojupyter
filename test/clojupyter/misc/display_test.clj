(ns clojupyter.misc.display-test
  (:require [midje.sweet :refer :all]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [clojupyter.misc.display :as display]
            [clojupyter.misc.mime-convertible]
            [clojupyter.protocol.mime-convertible :as mc]))

(defn pretty-maker [maker]
  #(-> % maker mc/to-mime (cheshire/decode true)))

(facts "Should be able to produce html"
       ((pretty-maker display/make-html) "<h1>hello world</h1>") => {:text/html "<h1>hello world</h1>"})

(facts "Should be able to produce latex"
       ((pretty-maker display/make-latex) "$\\sin{x}$") => {:text/latex "$\\sin{x}$"})

(facts "Should be able to produce markdown"
       ((pretty-maker display/make-markdown) "# hello world") => {:text/markdown "# hello world"})

(facts "Should be able to produce from hiccup html"
       ((pretty-maker display/hiccup-html) [:p "some text"]) => {:text/html "<p>some text</p>"}
       ((pretty-maker display/html) [:p "some text"]) => {:text/html "<p>some text</p>"})
