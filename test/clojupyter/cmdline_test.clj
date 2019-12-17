(ns clojupyter.cmdline-test
  (:require [clojupyter.cmdline :as cmdline]
            [clojupyter.install.local :as local]
            [clojupyter.install.local-specs :as lsp]
            [clojupyter.test-shared :as ts]
            [clojupyter.test-shared-generators :as shg :refer [==> R]]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [io.simplect.compose :refer [P]]))

(use 'clojure.pprint)

(def LSP-DEPEND
  "Ensures dependency due to use of `:local/...` keywords.  Do not delete."
  lsp/DEPEND-DUMMY)

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
  (gen/frequency [[5 (shg/g-alphanum 1 8)]
                  [5 (shg/g-name 1 8)]
                  [1 (shg/g-constant "")]
                  [1 gen/string]]))

(def g-local-install-icon-value
  (gen/frequency [[9 (shg/g-alphanum 1 8)]
                  [1 (shg/g-constant "")]
                  [1 gen/string]]))

(def g-local-install-ident-flag
  (gen/elements [nil "-i" "--ident"]))

(def g-local-install-ident-value
  (gen/frequency [[9 (shg/g-alphanum 1 8)]
                  [1 (shg/g-constant "")]
                  [1 gen/string]]))

(def g-local-install-jarfile-flag
  (gen/elements [nil "-j" "--jarfile"]))

(def g-local-install-jarfile-value
  (gen/let [nm (gen/frequency [[5 (shg/g-constant "clojupyter-standalone.jar")]
                               [1 (gen/fmap (P str ".jar") (shg/g-name 1 10))]
                               [1 gen/string]])
            path (gen/frequency [[3 (shg/g-constant ".")]
                                 [1 shg/g-path]])]
    (R (str path "/" nm))))

(def g-local-install-skip-icon-tags-flag
  (gen/elements [nil "--skip-icon-tags"]))

(def g-random-flag
  (gen/frequency [[9 shg/g-nil]
                  [1 shg/g-flag-double]
                  [1 shg/g-flag-single]]))

(def g-local-install-cmdline
  (gen/let [host-flag g-local-install-host-flag
            [botval bot] (shg/g-combine-flag-and-val g-local-install-icon-bot-flag
                                                    g-local-install-icon-value)
            [topval top] (shg/g-combine-flag-and-val g-local-install-icon-top-flag
                                                    g-local-install-icon-value)
            [identval ident] (shg/g-combine-flag-and-val g-local-install-ident-flag
                                                        g-local-install-ident-value)
            [jarval jar] (shg/g-combine-flag-and-val g-local-install-jarfile-flag
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
