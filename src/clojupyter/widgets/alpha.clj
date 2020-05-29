(ns clojupyter.widgets.alpha
  (:require
   [camel-snake-kebab.core :as csk]
   [clojupyter.kernel.comm-atom :as ca]
   [clojupyter.kernel.jup-channels :as jup]
   [clojupyter.log :as log]
   [clojupyter.messages :as msgs]
   [clojupyter.state :as state]
   [clojupyter.util :as u]
   [clojupyter.util-actions :as u! :refer [uuid]]
   [clojupyter.widgets.ipywidgets :as ipy]
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :refer [instrument unstrument]]
   [clojure.string :as str]
   [io.simplect.compose :refer [def- c C p P >->> >>->]]
   ))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; DISPLAY
;;; ------------------------------------------------------------------------------------------------------------------------
#_
(defn display
  [comm-atom]
  (log/debug "display -- " (log/ppstr {:comm-atom comm-atom}))
  (if (ca/comm-atom? comm-atom)
    (let [{:keys [jup req-message]} (state/current-context)]
      (assert (and jup req-message))
      (jup/send!! jup :iopub_port req-message msgs/DISPLAY-DATA
                  (msgs/display-data-content (ca/comm-id comm-atom))))
    (throw (Exception. (str "Not a comm-atom: " comm-atom)))))

;;; ------------------------------------------------------------------------------------------------------------------------
;;; INTERACT
;;; ------------------------------------------------------------------------------------------------------------------------

(defn interactive
  [output-widget f wmap]
  (let [agent-key (gensym)
        cur (atom (into {} (for [[k w] wmap] [k (get @w :value)])))
        set-value! (fn [] (swap! output-widget assoc :value (f @cur)))]
    (doseq [[k w] wmap]
      (assert (ca/comm-atom? w))
      (ca/watch w agent-key
                (fn [_ _ _ new]
                  (try (swap! cur assoc k (get new :value))
                       (set-value!)
                       (catch Exception e
                         (log/error (str "Error in interactive-agent@" agent-key ": " e)
                                    (log/ppstr {:cur @cur :k k :new new})))))))
    (set-value!)
    (ipy/v-box {:children (vec (concat (vals wmap) [output-widget]))})))

(defn interact!
  [f w0 & ws]
  (let [widgs (vec (cons w0 ws))
        out (ipy/label {:value (str (apply f (map (comp :value deref) widgs)))})
        observe! (fn [idx] (ca/watch (nth widgs idx) :value
                              (fn [k _ {o-val :value} {n-val :value}]
                                (when (not= o-val n-val)
                                  (let [v (apply f (map #(if (= %1 idx) n-val (get @%2 :value)) (range) widgs))]
                                    (swap! out assoc :value (str v)))))))]
    (doseq [i (range (count widgs))] (observe! i))
    (ipy/v-box {:children (conj widgs out)})))

(defn tie!
  ([f source target] (tie! f source target :value :value))
  ([f source target source-attr target-attr]
    (add-watch source (gensym :on-change)
      (fn [_ _ old new]
        (when (not= (get old source-attr)
                    (get new source-attr))
          (swap! target assoc target-attr
            (f (get new source-attr))))))))
