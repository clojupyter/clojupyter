(ns clojupyter.cmdline
  (:require
   [clojure.edn					:as edn]
   [clojure.java.io				:as io]
   [clojure.pprint						:refer [pprint]]
   [clojure.set					:as set]
   [clojure.spec.alpha				:as s]
   [clojure.spec.test.alpha					:refer [instrument]]
   [clojure.string				:as str]
   [clojure.tools.cli				:as cli]
   [io.simplect.compose						:refer [def- redefn sdefn sdefn- >->> >>-> π Π γ Γ λ]]
   ,,
   [clojupyter.cmdline.api			:as cmdline]
   [clojupyter.install.conda.build		:as conda-build]
   [clojupyter.install.conda.build-actions	:as conda-build!]
   [clojupyter.install.conda.link		:as link]
   [clojupyter.install.conda.link-actions	:as link!]
   [clojupyter.install.conda.unlink		:as unlink]
   [clojupyter.install.conda.unlink-actions	:as unlink!]
   [clojupyter.install.filemap			:as fm]
   [clojupyter.install.local			:as local]
   [clojupyter.install.local-actions		:as local!]
   [clojupyter.install.local-specs		:as lsp]
   [clojupyter.install.plan					:refer :all]
   [clojupyter.kernel.os			:as os]
   [clojupyter.kernel.version			:as ver]
   [clojupyter.util				:as u]
   [clojupyter.util-actions			:as u!		:refer [side-effecting!]])
  (:gen-class))

(defmulti ^:private handle-cmd (fn [_ cmdname] cmdname))

(defn- parse-cmdline
  [cmdline-opts args]
  (let [{:keys [summary options errors] :as result} (cli/parse-opts args cmdline-opts :strict true)]
    (merge result
           {:cmdline-args args, :cmdline-opts cmdline-opts}
           (when (some u/truthy? errors)
             {:error-messages (concat ["Error parsing command line:" ""]
                                      (map (π str "  ") errors)
                                      ["" "Options summary:" ""]
                                      (str/split-lines summary)
                                      [""])}))))

;;; ----------------------------------------------------------------------------------------------------
;;; TRIVIAL COMMANDS
;;; ----------------------------------------------------------------------------------------------------

(defn- s*eval
  [s]
  (let [res (->> (try (-> s read-string eval)
                      (catch Throwable e (str "Error: " e)))
                 pprint
                 with-out-str
                 str/split-lines)]
    (Γ (cmdline/set-header "EVAL")
       (cmdline/set-prefix "   ")
       (cmdline/outputs [(str s " => ") ""])
       (cmdline/outputs res)
       (cmdline/set-exit-code 0))))

(defn- s*getenv
  [env-var]
  (Γ (cmdline/set-header "GETENV")
     (cmdline/outputs ["" (str "getenv(" env-var ") = " (System/getenv env-var)) ""])
     (cmdline/set-exit-code 0)))

(declare CMDS DVL-CMDS)
(defn- s*help-cmd
  [args]
  (if (= 1 (count args))
    (let [arg (first args)
          cmd? (contains? (into #{} (concat CMDS DVL-CMDS)) arg)
          cmdline-ns (-> 'clojupyter.cmdline find-ns)
          docstr (when cmd? (->> arg symbol (ns-resolve cmdline-ns)  meta :doc))]
      (cmdline/outputs
       (cond
         (not cmd?)	[(str "'" arg "' does not appear to be a Clojupyter command.")]
         (not docstr)	[(str "No docstring found for command '" arg "'.")]
         :else		(concat [(str "Docstring for '" arg "':") ""]
                                (->> docstr
                                     str/split-lines
                                     (map (π str "    ")))))))
    (cmdline/outputs ["" "Usage: <clojupyter> help [command]"])))

(defn- s*help
  [& args]
  (Γ (cmdline/set-header "Help")
     (cmdline/outputs ["Use command 'list-commands' to see a list of available commands." ""
                       "Use command 'help <cmd>' to get documentation for individual commands." ""])
     (s*when (-> args count pos?)
       (s*help-cmd args))
     (cmdline/set-exit-code 0)))

(defn- s*list-commands
  [cmds]
  (fn []
    (Γ (cmdline/set-header "List commands")
       (cmdline/set-prefix "Command: ")
       (cmdline/set-result {:cmds cmds})
       (cmdline/outputs ["Clojupyter commands:" ""])
       (cmdline/outputs (mapv (π str "   - ") cmds))
       (cmdline/set-prefix "")
       (cmdline/outputs [""
                         "You can invoke Clojupyter commands like this:"
                         ""
                         "   clj -m clojupyter.cmdline <command>"
                         ""
                         "or, if you have set up lein configuration, like this:"
                         ""
                         "   lein clojupyter <command>"
                         ""
                         "See documentation for details."]))))

(defn- s*supported-os?
  []
  (let [supp?	(os/supported-os?)
        supp	(if supp? "Supported." "Not supported.")
        ex	(if supp? 0 1)]
    (Γ (cmdline/set-header "Operating System")
       (cmdline/set-result {:osname (os/osname), :supported-os? supp?})
       (cmdline/output (if-let [os (os/operating-system)]
                         (str "OS seems to be " (str/upper-case (name os)) ". " supp)
                         (str "Unknown OS (reported as '" (os/osname) "'). " supp))))))

(defn- s*unknown-command
  [cmd]
  (Γ (cmdline/set-header (str "Unknown command '" cmd "'"))
     (cmdline/output "Use 'list-commands' to see available commands.")
     (cmdline/set-result {:unknown-command cmd})
     (cmdline/set-exit-code 1)))

(defn- s*version
  []
  (let [ver (ver/version)]
    (Γ (cmdline/set-header "Version")
       (cmdline/set-prefix "  ")
       (cmdline/set-result {:version ver})
       (cmdline/outputs (->> ver
                             pprint
                             with-out-str
                             str/split-lines
                             (map (π str "   ")))))))

(defn- s*list-installs-matching
  [regex-string]
  (local/s*list-installs-matching (local!/get-install-environment) regex-string))

(defn- s*list-installs
  []
  (Γ (s*list-installs-matching "")
     (cmdline/set-header "All Clojupyter kernels")))

;;; ----------------------------------------------------------------------------------------------------
;;; REMOVE KERNELS
;;; ----------------------------------------------------------------------------------------------------

(defn- s*remove-installs-matching
  ([regex-string] (s*remove-installs-matching {} regex-string))
  ([{:keys [dont-execute]} regex-string]
   (let [env (local!/remove-kernel-environment)]
     (Γ (s*when-not dont-execute s*set-do-execute)
        (local/s*generate-remove-action regex-string env)
        s*execute
        local/s*report-remove))))

(defn- s*remove-install
  [regex-string]
  (Γ (s*remove-installs-matching (str "^" regex-string "$"))
     (cmdline/set-header (str "Remove kernel '" regex-string "'"))))

;;; ----------------------------------------------------------------------------------------------------
;;; LOCAL INSTALL
;;; ----------------------------------------------------------------------------------------------------

(def- TOPMIN 1)
(def- TOPMAX 8)
(def- topbot-validate (Γ count #(<= TOPMIN % TOPMAX)))

(defn- topbot-help
  [s topmin topmax]
  (str "Text at the " s " of icon (<= " topmin " length " topmax ")."))

(def- INSTALL-OPTIONS
  [["-h" "--host"
    "Install at host-level, shared among all users."
    :default false]
   [nil "--icon-top ICON-TOP-STRING"
    (topbot-help "top" TOPMIN TOPMAX)
    :validate [topbot-validate]]
   [nil "--icon-bot ICON-BOTTOM-STRING"
    (topbot-help "bottom" TOPMIN TOPMAX)
    :validate [topbot-validate]]
   ["-i" "--ident KERNEL-IDENT"
    (str "Kernel identifier as shown in Jupyter, a string matching the regex #\"" lsp/IDENT-REGEX "\".")
    :validate [(π re-find lsp/IDENT-REGEX)]]
   ["-j" "--jarfile JARFILE"
    "JAR file to use for installation, must be a '.jar' file."
    :validate [(Γ str (π re-find #".jar$"))]
    :parse-fn io/file]
   [nil "--skip-icon-tags"
    "If specified does not add icon tags."
    :default false]])

(s/def ::host			boolean?)
(s/def ::icon-bot		string?)
(s/def ::icon-top		string?)
(s/def ::ident			string?)
(s/def ::jarfile		string?)
(s/def ::loc			#{:user :host})
(s/def ::skip-icon-tags		boolean?)
(s/def ::options		(s/keys :req-un [::host ::skip-icon-tags]
                                        :opt-un [::icon-bot ::icon-top ::ident]))
(s/def ::parse-result		(s/keys :req-un [::options]))

(def- KEYMAP
  {:icon-bot		:local/icon-bot
   :icon-top		:local/icon-top
   :host		:local/loc
   :ident		:local/ident
   :jarfile		:local/source-jarfile
   :skip-icon-tags	:local/customize-icons?})

(def- HOSTMAP {true  :loc/host, false :loc/user})

(sdefn build-user-opts (s/cat :parse-result ::parse-result)
  [{{:keys [host jarfile] :as parse-opts} :options :as parse-result}]
  (let [jarfiles (if jarfile #{jarfile} #{})
        user-opts (-> (merge lsp/DEFAULT-USER-OPTS
                             (set/rename-keys parse-opts KEYMAP)
                             {:local/loc (get HOSTMAP host)})
                      (update :local/customize-icons? #(not %))
                      (assoc :local/filemap (fm/filemap jarfiles)
                             :local/source-jarfiles jarfiles)
                      (dissoc :local/source-jarfile))]
    (when-not (s/valid? :local/user-opts user-opts)
      (u!/throw-info "Internal error: Invalid cmdline parse result."
        {:parse-result parse-result, :user-opts user-opts,
         :explain-str (s/explain-str :local/user-opts user-opts)}))
    user-opts))

(def parse-install-local-cmdline (π parse-cmdline INSTALL-OPTIONS))

(sdefn- s*install (s/nilable (s/coll-of string? :type vector?))
  [& args]
  (let [{:keys [error-messages arguments] :as result} (parse-install-local-cmdline args)
        result (assoc result :cmdline-args args)]
    (Γ (cmdline/set-header "Install Clojupyter")
       (cond
         error-messages
         ,, (Γ (cmdline/set-exit-code 1)
               (cmdline/outputs error-messages))
         (-> arguments count pos?)
         ,, (Γ (cmdline/set-exit-code 1)
               (cmdline/outputs [(str "Command line arguments not permitted: " arguments)
                                 "To specify a kernel identifier use the \"--ident\" option."]))
         :else
         ,, (local/s*install {}
                             (build-user-opts result)
                             (local!/get-install-environment))))))

;;; ----------------------------------------------------------------------------------------------------
;;; BUILD CONDA PACKAGE
;;; ----------------------------------------------------------------------------------------------------

(def- BUILD-CONDA-OPTIONS
  [["-b" "--buildnum BUILD-NUM"
    "Conda build number, must be a non-negative integer."
    :validate [(every-pred int? (complement neg?))]
    :parse-fn #(when (re-find #"^\d+$" %) (edn/read-string %))]
   ["-j" "--jarfile JARFILE"
    "JAR file to use for installation."
    :parse-fn io/file]])

(def parse-build-conda-cmdline (π parse-cmdline BUILD-CONDA-OPTIONS))

(defn s*conda-build
  [& args]
  (u!/with-temp-directory! [blddir :keep-files? true]
    (let [install-env (local!/get-install-environment)
          {error-messages :error-messages
           {:keys [buildnum jarfile]} :options
           :as parse-result}
          ,, (parse-build-conda-cmdline args)
          jarfile (u!/normalized-file jarfile)
          build-env (conda-build!/get-build-environment jarfile)
          build-params {:conda-build-params/buildnum buildnum,
                        :conda-build-params/filemap (fm/filemap jarfile)
                        :local/ident (str (u!/uuid))
                        :local/source-jarfiles (if jarfile #{jarfile} #{})}]
      (Γ (cmdline/set-header "Build Conda package")
         (if (or error-messages (not (s/valid? :conda-build-params/buildnum buildnum)))
           (Γ (cmdline/set-error {:message "Error parsing command line.", :parse-result parse-result})
              (cmdline/outputs error-messages)
              (cmdline/set-result {:bad-buildnum buildnum, :args args})
              (cmdline/set-exit-code 1))
           (Γ (conda-build/s*conda-build blddir install-env build-env build-params)
              conda-build/s*report-conda-build))))))

;;; ----------------------------------------------------------------------------------------------------
;;; CONDA LINK
;;; ----------------------------------------------------------------------------------------------------

(def- CONDA-LINK-OPTIONS
  [["-p" "--prefix=prefix"
    "Conda prefix, where to find conda package files (cf. Conda build documentation for details)."
    :parse-fn #(when (-> % count pos?) %)]
   ["-j" "--jarfile=jarfile"
    "TEST ONLY.  Filename of jar file to install, must end in '.jar'. If not specified: Look in PREFIX dir."
    :validate [(every-pred (Γ count pos?) (π re-find #".+\.jar$"))]]
   ["-n" "--no-actions"
    "TEST ONLY.  If specified: Calculate only, do not perform install actions."
    :default false]])

(def- parse-conda-link-cmdline (π parse-cmdline CONDA-LINK-OPTIONS))

(defn s*conda-link
  [& args]
  (let [{error-messages :error-messages, arguments :arguments
         {:keys [prefix jarfile no-actions]} :options :as parse-result}
        ,, (parse-conda-link-cmdline args)
        arguments? (-> arguments count pos?)]
    (if (or error-messages arguments?)
      (Γ (cmdline/set-header "Conda Link")
         (cmdline/set-result (assoc parse-result :conda-link/cmdline-args args))
         (cmdline/outputs error-messages)
         (cmdline/set-exit-code 1))
      (fn [S]
        (let [opts {:prefix prefix :jarfile jarfile, :skip-execute? no-actions}
              install-env (link!/conda-link-environment opts)
              #:conda-link{:keys [destdir]} install-env
              result ((link/s*conda-link opts install-env) S)
              destdir-filemap (->> destdir file-seq doall fm/filemap)
              S' (assoc S :conda-link/destdir-filemap destdir-filemap)]
          ((link/s*report-link destdir destdir-filemap) S'))))))

;;; ----------------------------------------------------------------------------------------------------
;;; CONDA UNLINK
;;; ----------------------------------------------------------------------------------------------------

(def- CONDA-UNLINK-OPTIONS
  [[nil "--prefix=PREFIX"
    "Conda prefix."]
   ["-n" "--no-actions"
    "TEST ONLY.  If specified: Calculate only, do not perform delete actions."
    :default false]])

(defn s*conda-unlink
  [& args]
  (let [{error-messages :error-messages, arguments :arguments,
         {:keys [prefix no-actions]} :options :as parse-result}
        ,, (parse-cmdline CONDA-UNLINK-OPTIONS args)
        env (unlink!/get-unlink-environment prefix)
        {:keys [:conda-unlink/kernel-dir]} env
        arguments? (-> arguments count pos?)]
    (Γ (cmdline/set-header "Conda Unlink")
       (if (or error-messages arguments?)
         (Γ (cmdline/set-result (assoc parse-result :conda-unlink/cmdline-args args))
            (cmdline/outputs error-messages)
            (s*when arguments?
              (cmdline/output (str "Arguments not allowed: " arguments))))
         (let [result ((unlink/s*conda-unlink {:skip-execute? no-actions} env) {})
               filemap (-> kernel-dir io/file file-seq doall fm/filemap)]
           (unlink/s*report-unlink-actions kernel-dir result filemap))))))

;;; ----------------------------------------------------------------------------------------------------
;;; MAIN
;;; ----------------------------------------------------------------------------------------------------

(defn s*argc-failure
  [usage-string exit-code]
  (Γ (cmdline/output (str usage-string))
     (cmdline/set-exit-code exit-code)))

(defn invoke-s*fn
  [cmd-fn argc args error-exit-code usage-string]
  (let [exit-code (or error-exit-code 1)
        usage-string (or usage-string "Bad argument list.")
        f (if (and argc (not= argc (count args)))
            (s*argc-failure usage-string error-exit-code)
            (apply cmd-fn args))]
    (f cmdline/initial-state)))

(defmacro define-cmds
  [var defs]
  `(do
     (def ~var ~(mapv first defs))
     ~@(for [[nm [argc f exit-code msg]] defs]
         `(defmethod handle-cmd ~nm [[_# & args#] _#]
            (invoke-s*fn ~f ~argc args# ~exit-code ~msg)))))

(define-cmds CMDS
  {
   "help"			[nil s*help]
   "install"			[nil s*install]
   "list-commands"		[0 (s*list-commands CMDS)]
   "list-installs"		[0 s*list-installs]
   "list-installs-matching"	[1 s*list-installs-matching 1
                                 "Usage: Specify a regular expression to match with kernel identifier."]
   "remove-installs-matching"	[1 s*remove-installs-matching 1
                                 "Usage: ... remove-installs-matching <ident-regex-string>"]
   "remove-install"		[1 s*remove-install 1
                                 "Usage: ... remove-install <ident>"]
   "version"			[0 s*version]
   })

(define-cmds DVL-CMDS
  ;; Relevant for Clojupyter development only
  {
   "conda-build"		[nil s*conda-build]		;; Build package for distribution via conda
   "conda-link"			[nil s*conda-link]		;; Used by conda install procedure on end-user machine
   "conda-unlink"		[nil s*conda-unlink]		;; Used by conda uninastall procedure on end-user machine
   "eval"			[1 s*eval]			;; For debugging
   "getenv"			[1 s*getenv]			;; For debugging
   "list-dvl-commands"		[0 (s*list-commands DVL-CMDS)]	;; In case you forget
   "supported-os?"		[0 s*supported-os?]		;; Not really used
   })

(defmethod handle-cmd :default
  [args cmd]
  ((s*unknown-command cmd) cmdline/initial-state))

(defn- format-report
  [version-map {:keys [:cmdline/exit-code :cmdline/header :cmdline/output :cmdline/prefix]}]
  (->> (concat [(str "Clojupyter v" (ver/version-string-long version-map) " - " header)  ""]
               (->> output (map (π str "  " prefix "  ")))
               ["" (str "exit(" exit-code ")")])
       (str/join "\n")))

(defn- -main*
  [& cmdline-args]
  (let [cmd (first cmdline-args)
        {:keys [:cmdline/exit-code] :as result} (handle-cmd cmdline-args cmd)]
    (println (format-report (ver/version) result))
    (flush)
    exit-code))

(defn -main
  [& cmdline-args]
  (try (let [exit-code (apply -main* cmdline-args)]
         (System/exit (or exit-code -1)))
       (catch Throwable e
         (println (str "(-main) Error occurred: " e))
         (flush)
         (System/exit 1))))

(instrument `build-user-opts)
(instrument `s*install)

;;; ----------------------------------------------------------------------------------------------------
;;; REPL CONVENIENCE FUNCTIONS
;;; ----------------------------------------------------------------------------------------------------
;;;
;;; The functions in this namespace are designed to be used from the command line.  However, it is
;;; sometimes convenient to use them in the REPL.  The state-threading functions require you to
;;; write
;;;
;;;    ((s*install "--ident" "myclojupyter") {})
;;;
;;; The functions below enable the omission of the explicit initial state.  Usage is equvalent to
;;; that of the command line, e.g.
;;;
;;;    (install "--ident" "myclojupyter")
;;;
;;; and so on.
;;;

(def list-commands
  "Clojupyter cmdline command: Lists the available Clojupyter cmdline commands. Note that this
  function is designed to be used from the command line and is normally not called from
  REPL (although this does in fact work).

  COMMAND ARGUMENTS: 	None

  FLAG/OPTIONS: 	None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline list-commands
    Clojupyter v0.2.3 - List commands

        Clojupyter commands:

           - help
           - install
           - list-commands
           - list-installs
           - list-installs-matching
           - remove-installs-matching
           - remove-install
           - version

        You can invoke Clojupyter commands like this:

           clj -m clojupyter.cmdline <command>

        or, if you have set up lein configuration, like this:

           lein clojupyter <command>

        See documentation for details.

    exit(0)
    >"
  #(((s*list-commands CMDS)) {}))

(def list-installs
  "Clojupyter cmdline command: Lists the Clojupyter kernels installed on the local host, giving the
  kernel identifier and kernel directory for each installed Clojupyter kernel.  Non-Clojupyter
  kernels are not included.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:	None

  OPTIONS:		None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline list-installs
    Clojupyter v0.2.3 - All Clojupyter kernels

        |    IDENT |                                DIR |
        |----------+------------------------------------|
        |      abc |      ~/Library/Jupyter/kernels/abc |
        | mykernel | ~/Library/Jupyter/kernels/mykernel |
        |   test-1 |   ~/Library/Jupyter/kernels/test-1 |
        |   test-2 |   ~/Library/Jupyter/kernels/test-2 |
        |   test-3 |   ~/Library/Jupyter/kernels/test-3 |

    exit(0)
    >"
  #((s*list-installs) {}))

(defn list-installs-matching
  "Clojupyter cmdline command: Lists the Clojupyter kernels install on the local host whose
  *identifier* matches the mandatory string argument when interpreted as a regular expression.

  It is an error if no string argument is provided, or if the provided string is not a legal regular
  expression as understood by `clojure.core/re-pattern`.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    1. Mandatory string representing a regular expression to be interpreted by `clojure.core/re-pattern`,

  OPTIONS:		None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline list-installs-matching test
    Clojupyter v0.2.3 - Clojupyter kernels matching the regular expression 'test'.

        |  IDENT |                              DIR |
        |--------+----------------------------------|
        | test-1 | ~/Library/Jupyter/kernels/test-1 |
        | test-2 | ~/Library/Jupyter/kernels/test-2 |
        | test-3 | ~/Library/Jupyter/kernels/test-3 |

    exit(0)
    >"
  [& args]
  ((apply s*list-installs-matching args) {}))

(defn install
  "Clojupyter cmdline command: Installs a Clojuputer kernel on the local host based on the contents of
  the code repository in the current directory.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.  The
  function receives its arguments as string values.

  OPTIONS:

    -h, --host:         Install kernel such that it is available to all users on the host.  If not
                        specified installs the kernel in the Jupyter kernel directory of the current
                        user.  See platform documentation for details on the location of host-wide and
                        user-specific Jupyter kernel directories.

    --icon-top:         Add specified text to the top of the Clojupyter icon shown in Jupyter.  Length
                        must be between 1 and 8 characters.

    --icon-bot:         Add specified text to the bottom of the Clojupyter icon shown in Jupyter.
                        Length must be between 1 and 8 characters.  For generic Clojupyter kernels,
                        the Clojupyter version number is often shown at the bottom of the icon.

    -i, --ident:        String to be used as identifier for the kernel.

    -j, --jarfile:      Filename of the jarfile, which must be a standalone jar containing Clojupyter,
                        to be installed.  If the not specified, uses any standalone jarfile found in
                        the current directory or one of its subdirectories, provided a single such
                        file is found.  If zero or multiple standalone jarfiles are found an error is
                        raised.

    --skip-icon-tags:   Do not add text to icons. Applies even when values are provided using
                        `--icon-top` and `--icon-bot` are provided.

  EXAMPLE USE:

    > clj -m clojupyter.cmdline install --ident mykernel -h
    Clojupyter v0.2.3 - Install Clojupyter

        Installed jar:      ~/lab/clojure/clojupyter/target/clojupyter-0.2.3-SNAPSHOT-standalone.jar
        Install directory:  /usr/local/share/jupyter/kernels/mykernel
        Kernel identifier:  mykernel

        Installation successful.

    exit(0)
    >"
  [& args]
  ((apply s*install args) {}))

(defn remove-install
  "Clojupyter cmdline command: Removes a kernel by matching its kernel identifier to the string argument given.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    1. Kernel identifier of kernel.

  FLAG/OPTIONS:

    - None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline remove-install test-2
    Clojupyter v0.2.3 - Remove kernel 'test-2'

        Step: Delete /Users/klaush/Library/Jupyter/kernels/test-2

        Status: Removals successfully completed.

    exit(0)
    >"
  [& args]
  ((apply s*remove-install args) {}))

(defn remove-installs-matching
  "Clojupyter cmdline command: Removes kernels by matching kernel identifiers to the regular
  expression given as argument.

  The string must be a legal as interpreted by `clojure.core/re-pattern`.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    1. Mandatory string representing a regular expression to be interpreted by `clojure.core/re-pattern`,

  FLAG/OPTIONS:

    - None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline remove-installs-matching test
    Clojupyter v0.2.3 - Remove installs

        Step: Delete /Users/klaush/Library/Jupyter/kernels/test-2
        Step: Delete /Users/klaush/Library/Jupyter/kernels/test-1
        Step: Delete /Users/klaush/Library/Jupyter/kernels/test-3

        Status: Removals successfully completed.

    exit(0)
    >"
  [& args]
  ((apply s*remove-installs-matching args) {}))

(def version
  "Clojupyter cmdline command: Lists Clojupyter version information.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    - None

  FLAG/OPTIONS:

    - None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline version
    Clojupyter v0.2.3 - Version

             #:version{:major 0,
                       :minor 2,
                       :incremental 3,
                       :qualifier \"SNAPSHOT\",
                       :lein-v-raw \"cd18-DIRTY\"}

    exit(0)
    >"
 #((s*version) {}))

;;; ----------------------------------------------------------------------------------------------------
;;; INTERNAL/DEVELOPMENT-ONLY
;;; ----------------------------------------------------------------------------------------------------

(defn conda-build
  "Clojupyter development cmdline command: Build a conda installation package for Clojupyter to be
  deployed in Anaconda cloud.

  This command is intended for USE ONLY BY THE DEVELOPERS OF CLOJUPYTER for building the deployment
  packages allowing end-users to install generic Clojupyter kernels on their machine using
  `conda install` only.  If you are not a Clojupyter developer you probably don't want to use this
  command.

  The `conda-build` command is designed to be used on the platform of the package being built: You
  build a Linux package on a Linux machine, a MacOS package on a Mac, and a Windows package on a PC
  running Windows.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  PROCESSES

  1. The `conda-build` command spawns a `conda` process to perform the actual build in a temporary 
     directory.

  2. If icon tags are to customised an additional process from the Imagemgick package is spawned to
     make the changes to the icon bitmaps.

  See PREREQUISITES for details.

  EXECUTION TIME

  Note that execution time for conda builds is considerable (often >60s) and that no output is
  produced until the process is complete - patience is required.

  PREREQUISITES:

    1. Conda installed and available on the path (executable: `conda`/`conda.exe`).
    2. If icon tags are needed: Imagemagick 'convert' installed and available on the path
       (executable: `convert`/`convert.exe`).

  COMMAND ARGUMENTS:

    - None

  FLAG/OPTIONS:

    -b, --build-num     The conda build number.  Must be a string representing a positive integer
                        (lexically: a non-empty sequence of decimal digits).

    -j, --jarfile.      The jarfile to be included in the package.  If not specified a default
                        standalone jarfile is located, see local install for details.

  The example below show a build on MacOS, builds on Linux and Windows are very similar.

  EXAMPLE USE:

    > uname -rv
    18.6.0 Darwin Kernel Version 18.6.0: Thu Apr 25 23:16:27 PDT 2019; root:xnu-4903.261.4~2/RELEASE_X86_64

    > conda --version
    conda 4.7.10

    > convert --version
    Version: ImageMagick 7.0.8-35 Q16 x86_64 2019-03-26 https://imagemagick.org
    Copyright: © 1999-2019 ImageMagick Studio LLC
    License: https://imagemagick.org/script/license.php
    Features: Cipher DPC HDRI
    Delegates (built-in): bzlib cairo fftw fontconfig freetype gvc jbig jng jp2 jpeg lzma pangocairo png rsvg tiff webp xml zlib

    > time clj -m clojupyter.cmdline conda-build -b99
    Clojupyter v0.2.3-SNAPSHOT - Build Conda package

        Conda build completed successfully.
        Conda file output to ~/anaconda3/conda-bld/osx-64/clojupyter-0.2.3snapshot-99.tar.bz2

    exit(0)

    real    1m1.643s
    user    1m23.009s
    sys     0m3.301s
    >"
  [& args]
  ((apply s*conda-build args) {}))

(defn conda-link
  "Clojupyter development cmdline command used by the conda package managment system.

  This command is not intended for direct use, but is exclusively called by the conda package
  management system to install Clojupyter on the end-user machine.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    - None

  FLAG/OPTIONS:

    -p, --prefix        Conda PREFIX, controls into which Conda environment to install Clojupyter.

    -j, --jarfile.      Jarfile to used for build.  ONLY RELEVANT FOR TEST.

    -n, --no-actions    If specified: Do not make any changes to Conda environment.  ONLY RELEVANT 
                        FOR TEST.

  EXAMPLE USE:          N/A"
  [& args]
  ((apply s*conda-link args) {}))

(defn conda-unlink
  "Clojupyter development cmdline command used by the conda package managment system.

  This command is not intended for direct use, but is exclusively called by the conda package
  management system to remove Clojupyter from the end-user machine.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    - None

  FLAG/OPTIONS:

    -p, --prefix        Conda PREFIX, controls into which Conda environment to install Clojupyter.

    -n, --no-actions    If specified: Do not make any changes to Conda environment.  ONLY RELEVANT 
                        FOR TEST.

  EXAMPLE USE:          N/A"
  [& args]
  ((apply s*conda-unlink args) {}))

(def list-dvl-commands
  "Clojupyter development cmdline command: Lists available developer-only commands.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    - None

  FLAG/OPTIONS:

    - None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline list-dvl-commands
    Clojupyter v0.2.3 - List commands

        Clojupyter commands:

           - conda-build
           - conda-link
           - conda-unlink
           - eval
           - getenv
           - list-dvl-commands
           - supported-os?

        You can invoke Clojupyter commands like this:

           clj -m clojupyter.cmdline <command>

        or, if you have set up lein configuration, like this:

           lein clojupyter <command>

        See documentation for details.

    exit(0)
    >"
  #(((s*list-commands DVL-CMDS)) {}))

(def supported-os?
  "Clojupyter development cmdline command: Lists information about the Operating System of the current
  machine.  Used to test Cloupyter OS support, otherwise not very useful.

  Note that this function is designed to be used from the command line and is normally not called
  from the REPL although this does in fact work.  Note also that the function itself, if used
  directly from the REPL, returns a data structure containing a vector of strings which will be sent
  to standard output, whereas the cmdline command itself actually sends the strings to stdout.

  COMMAND ARGUMENTS:

    - None

  FLAG/OPTIONS:

    - None

  EXAMPLE USE:

    > clj -m clojupyter.cmdline supported-os?
    Clojupyter v0.2.3 - Operating System

        OS seems to be MACOS. Supported.

    exit(0)
    >"
  #((s*supported-os?) {}))

(defn help
  "Clojupyter development cmdline command: Provides help for Clojupyter commands.

  SUMMARY
  
    - Use command 'list-commands' to see a list of available commands.
    - Use command 'help <cmd>' to get documentation for individual commands.

  EXAMPLE USE:
  
    > clj -m clojupyter.cmdline help version
    Clojupyter v0.2.3 - Help

        Use command 'list-commands' to see a list of available commands.

        Use command 'help <cmd>' to get documentation for individual commands.

        Docstring for 'version':

            Clojupyter cmdline command: Lists Clojupyter version information.

              Note that this function is designed to be used from the command line and is normally not called
              from the REPL although this does in fact work.  Note also that the function itself, if used
              directly from the REPL, returns a data structure containing a vector of strings which will be sent
              to standard output, whereas the cmdline command itself actually sends the strings to stdout.

              COMMAND ARGUMENTS:

                - None

              FLAG/OPTIONS:

                - None

              EXAMPLE USE:

                > clj -m clojupyter.cmdline version
                Clojupyter v0.2.3 - Version

                         #:version{:major 0,
                                   :minor 2,
                                   :incremental 3,
                                   :qualifier \"SNAPSHOT\",
                                   :lein-v-raw \"cd18-DIRTY\"}

                exit(0)
                >

    exit(0)
    >"
  [& args]
  ((apply s*help args) {}))
