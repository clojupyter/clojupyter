(ns clojupyter.cmdline-test
  (:require
   [clojure.java.io				:as io]
   [clojure.set					:as set]
   [clojure.spec.alpha				:as s]
   [clojure.string				:as str]
   [clojure.test.check				:as tc]
   [clojure.test.check.generators		:as gen 	:refer [sample generate]]
   [clojure.test.check.properties		:as prop]
   [io.simplect.compose						:refer [def- γ Γ π Π >>-> >->>]]
   [me.raynes.fs				:as fs]
   [midje.sweet							:refer [fact facts =>]]
   ,,
   [clojupyter.cmdline				:as cmdline]
   [clojupyter.install.local			:as local]
   [clojupyter.install.local-actions		:as local!]
   [clojupyter.install.plan					:refer :all]
   [clojupyter.util				:as u]
   [clojupyter.util-actions			:as u!]
   ,,
   [clojupyter.test-shared					:refer :all]
   [clojupyter.kernel.version-test				:refer [g-version]]))

(use 'clojure.pprint)

;;; ----------------------------------------------------------------------------------------------------
;;; CMDLINE - LOCAL INSTALL
;;; ----------------------------------------------------------------------------------------------------

(def g-local-install-host-flag
  (gen/elements [nil "-h" "--host"]))

(def g-local-install-icon-bot-flag
   (gen/elements [nil "--icon-bot"]))

(def g-local-install-icon-top-flag
  (gen/elements [nil "--icon-top"]))

(def g-local-install-icon-value
  (gen/frequency [[5 (g-alphanum 1 8)]
                  [5 (g-name 1 8)]
                  [1 (g-constant "")]
                  [1 gen/string]]))

(def g-local-install-icon-value
  (gen/frequency [[9 (g-alphanum 1 8)]
                  [1 (g-constant "")]
                  [1 gen/string]]))

(def g-local-install-ident-flag
  (gen/elements [nil "-i" "--ident"]))

(def g-local-install-ident-value
  (gen/frequency [[9 (g-alphanum 1 8)]
                  [1 (g-constant "")]
                  [1 gen/string]]))

(def g-local-install-jarfile-flag
  (gen/elements [nil "-j" "--jarfile"]))

(def g-local-install-jarfile-value
  (gen/let [nm (gen/frequency [[5 (g-constant "clojupyter-standalone.jar")]
                               [1 (gen/fmap (Π str ".jar") (g-name 1 10))]
                               [1 gen/string]])
            path (gen/frequency [[3 (g-constant ".")]
                                 [1 g-path]])]
    (R (str path "/" nm))))

(def g-local-install-skip-icon-tags-flag
  (gen/elements [nil "--skip-icon-tags"]))

(def g-random-flag
  (gen/frequency [[9 g-nil]
                  [1 g-flag-double]
                  [1 g-flag-single]]))

(def g-local-install-cmdline
  (gen/let [host-flag g-local-install-host-flag
            [botval bot] (g-combine-flag-and-val g-local-install-icon-bot-flag
                                                 g-local-install-icon-value)
            [topval top] (g-combine-flag-and-val g-local-install-icon-top-flag
                                                 g-local-install-icon-value)
            [identval ident] (g-combine-flag-and-val g-local-install-ident-flag
                                                     g-local-install-ident-value)
            [jarval jar] (g-combine-flag-and-val g-local-install-jarfile-flag
                                                 g-local-install-jarfile-value)
            random-flag g-random-flag
            host (R (if host-flag [host-flag] []))
            random (R (if random-flag [random-flag] []))]
    {:cmdline (->> [bot host ident jar random top]
                   shuffle
                   (apply concat)
                   vec)
     :host-flag host-flag, :host host, :bot bot, :botval botval :top top, :topval topval,
     :ident ident, :identval identval, :jar jar, :jarval jarval, :random-flag random-flag, :random random}))

(def prop--local-install-cmdline
  (prop/for-all [{:keys [random-flag host-flag cmdline top topval bot botval
                         ident identval jar jarval]} g-local-install-cmdline]
    (let [{:keys [options errors] :as res}
          ,,(cmdline/parse-install-local-cmdline cmdline)
          {:keys [host icon-bot icon-top jarfile skip-icon-tags]} options
          ok? (not errors)
          opts (when ok? (cmdline/build-user-opts res))]
      (pprint {:errors errors, :options options})
      (==> random-flag errors)
      (==> (and ok? (-> bot count pos?)) (= botval icon-bot))
      (==> (and ok? (-> top count pos?)) (= topval icon-top))
      (==> (and ok? (-> ident count pos?)) (= identval ident))
      (==> (and ok? (-> jar count pos?)) (= jarval (str jarfile)))
      (==> ok? (s/valid? :local/user-opts opts)))))
