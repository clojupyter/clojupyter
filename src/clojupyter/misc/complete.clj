(ns clojupyter.misc.complete
  (:require [net.cgrand.sjacket.parser :as p]))

(defn complete? [code]
  (not (some  #(= :net.cgrand.parsley/unfinished %)
              (map :tag (tree-seq :tag
                                  :content
                                  (p/parser code))))))
