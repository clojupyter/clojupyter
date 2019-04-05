# Changelog

## v0.2.2

* [x] Enhancement: **Upgrade to nREPL v0.6** - eliminate uncaught stacktrace error
* [x] Introduce **modular message handling** incl new tests
* [x] Add retrievable version including timestamp, printed at startup (banner)
* [x] Add **config file** to control features such as log level
* [x] Address (some) **reported issues**
  * `#83: Hanging after evaluating cell with syntax error`
  * `#84: Kernel dies on empty code string`
  * `#30: Dead kernel on startup (comm_info_request)` (don't terminate on unknown messages)
  * `#69: FileNotFoundException Could not locate... on classpath.`
  * `#76: add-javascript broken?`
  * `#58: AOT+old tools.reader causing problems using latest ClojureScript`
  * `#85: Consider the silent and store_history keys of an execute request`
* [x] **Improve basic install**
  * [x] Eliminate **shell script in `clojupyter` install artifact** (call java directly)
  * [x] Add **clojupyter icons** (in Jupyter Lab Launcher, top right corner in Jupyter Notebook) 
  * [x] Add **version numbered icons**
  * [x] **Replace shell scripts** with lein functions for basic build operations
    * `clojupyter-install`
    * `check-os-support`
    * `check-install-dir`
    * `update-version-edn`
  * [x] Add **version-specific kernel install directory**
  * [x] Make **default kernel install directory `clojupyter`** (instead of `clojure`)
  * [x] Add `make` targets
    * `update-version-edn`
    * `install-version` (install plain icon)
    * `install-version-tag-icons` (install version-tagged icon, depends on `convert` from  `imagemagick` package)
* [x] Create **updated Docker image** for current version
* [x] Update TODO in `README.md`: add tentative near-term roadmap
* [x] Clean out obsolete issues on github
* [x] Delete unused branches on github
* [x] Establish numbered releases
* [x] Update example notebooks
* [x] Change code structure towards having an **open, extensible architecture** (more work is needed to make it
  extensible) 
  * [x] Introduce **handler-based middleware structure** similar to [nREPL](https://nrepl.org) and
    [Ring](https://github.com/ring-clojure/ring)
  * [x] Enable higher degree of **separation of concerns**, thus enabling adding features in a
    modular fashion
  * [x] Introduce **layered message handling**: Incoming messages pass through a define sequence of
    middleware handlers with outbound message passing through the same set of handlers in
    reverse order, yielding a layered message-processing architecture
  * [x] Introduce  `handler`, `middleware`, and `transport` abstractions similar to those of
    [employed in nREPL](https://nrepl.org/nrepl/0.6.0/design/transports.html), but adapted
    to fit into the multichannel context of Jupyter (cf. `send-stdin`, `send-iopub`, and
    `send-req` in `clojupyter.transport`)
  * [x] **Convert existing functionality to middleware-based structure**, messaging-handling is now defined
    by core message handler (in `clojupyter.middleware`):

    ```
    (def wrap-base-handlers
      (comp wrap-execute-request
            wrap-comm-msg
            wrap-comm-info
            wrap-comm-open
            wrap-is-complete-request
            wrap-complete-request
            wrap-kernel-info-request
            wrap-inspect-request
            wrap-history-request
            wrap-shutdown-request))

    (def wrap-jupyter-messaging
      (comp wrapout-encode-jupyter-message
            wrapout-construct-jupyter-message))

    (def default-wrapper
      (comp wrapin-verify-request-bindings
            wrapin-bind-msgtype
            wrap-print-messages
            wrap-jupyter-messaging
            wrap-busy-idle
            wrap-base-handlers))

    (def default-handler
      (default-wrapper not-implemented-handler))
    ```
    The modular structure will make it much easier to add functionality going forward.
    
  * [x] **Reorganize code structure**:
  ```
  src
  ├── clojupyter
  │   ├── display.clj
  │   ├── javascript
  │   │   └── alpha.clj
  │   ├── kernel
  │   │   ├── cljsrv
  │   │   │   ├── nrepl_comm.clj
  │   │   │   ├── nrepl_middleware.clj
  │   │   │   └── nrepl_server.clj
  │   │   ├── config.clj
  │   │   ├── core.clj
  │   │   ├── history.clj
  │   │   ├── init.clj
  │   │   ├── jupyter.clj
  │   │   ├── logo.clj
  │   │   ├── middleware
  │   │   │   ├── base.clj
  │   │   │   ├── comm.clj
  │   │   │   ├── complete.clj
  │   │   │   ├── execute.clj
  │   │   │   ├── history.clj
  │   │   │   ├── inspect.clj
  │   │   │   └── log_traffic.clj
  │   │   ├── middleware.clj
  │   │   ├── spec.clj
  │   │   ├── stacktrace.clj
  │   │   ├── state.clj
  │   │   ├── transport
  │   │   │   └── zmq.clj
  │   │   ├── transport.clj
  │   │   ├── util.clj
  │   │   └── version.clj
  │   ├── misc
  │   │   ├── display.clj
  │   │   ├── helper.clj
  │   │   ├── leiningen.clj
  │   │   └── mime_convertible.clj
  │   └── protocol
  │       └── mime_convertible.clj
  └── clojupyter.clj

  8 directories, 32 files
  ```
* [x] Add support for **configuration file**, loaded at startup
  * [x] Read from `~/Library/Preferences/clojupyter.edn` on MacOS and `~/.config/clojupyter.edn` on
  Linux (per XDG Base Directory specification)
  * [x] Move location history file to `~/Library/Caches` on MacOS and `~/.local/share` (per XDG Base
    Directory specification)
  * [x] Control printing of stacktraces (workaround, base issue addressed by upgrade to nREPL 0.6) in configuration file
  * [x] Control log level in configuration file
  * [x] Control ZMQ traffic logging in configuration file
* [x] **Improve tests**
  * [x] Convert existing tests to work with new structure
  * [x] Add tests for middleware providing checks for basic message responses
* [x] Miscellaneous
  * [x] Upgrade Jupyter messing protocol to v5.3 (from v5.0) - no apparent impact
  * [x] Replace `clj-time` with `clojure.java-time`
  * [x] Rename `idents` in ZMQ messages to `envelope`
  * [x] Define names for Jupyter message identifiers (in `clojupyter.misc.jupyter`)
  * [x] Eliminate `zmq-comm` protocol - not needed
  * [x] Use `:aot` only in `uberajr` profile in `project.clj`

## v0.2.1

* [x] Improve code structure / organization
  * [x] **Use a single atom for all global state.** Provide abstraction for manipulating
    global state.  Eliminate `states`.
  * [x] Use **accessor functions for socket access**. Avoids storing sockets in `Zmq_Comm`.
   Accessor functions for `stdin-socket` and `iopub-socket` in passed-around map `S`.
  * [x] Eliminate atoms containing sockets - not necessary since they are used, not updated.
  * [x] Remove depencency on `async.core` - not used.   
* [x] `core.clj`
  * [x] Replace `configure-shell-handler` and `configure-control-handler` with `make-handler`.  Their
   role is the same, the main difference that `execution-counter` is incremented on the shell
   socket.  Why not simply use the same handler?
  * [x] Integrate creation of signer and checker functions.
  * [x] Simplify `run-kernel` based on above.
  * [x] Separate code related to `nrepl-server` and Clojupyter middlerware into separate namespace:
   `clojupyter.misc.nrepl-server`.
* [x] `messages.clj`
  * [x] Considerably simplify `make-shell-handler` and `make-control-handler`: Unify into one.
  * [x] Use consolidation of global state to turn `handle-execute-request` into regular responding
   function defined with `defresponse`.
  * [x] Add `with-debug-logging`
  * [x] Add accessor functions for socket access.
  * [x] Integrate creation of signer and checker.
  * [x] Integrate `status-content` and `pyin-content` into point-of-use.
  * [x] Use abstractions to access global state.
* [x] `nrepl_comm.clj`  
   * [x] Refactor giant `defrecord` into smaller functions, no change in logic.
* [x] `history.clj`
   * [x] Move determination of max history length here.
* [x] `nrepl_server.clj`(added)
   * [x] `nrepl`-related code from `core.clj`.
* [x] `state.clj`
   * [x] Rename from `states.clj` - add single atom for all of global state.
   * [x] Add functions to manipulate global state.
* [x] `zmq_comm.clj`
   * [x] Use accessor functions instead of access-by-socket-name.
* [x] `util.clj`
   * [x] Add `with-debug-logging` macro.
   * [x] Add `reformat-form`.  Very early experiment with auto-indent / auto-reformat of cells based on
     [Code Prettify](https://jupyter-contrib-nbextensions.readthedocs.io/en/latest/nbextensions/code_prettify/README_code_prettify.html)
     / [Autopep8](https://github.com/kenkoooo/jupyter-autopep8). Very primitive / early cell reformatting
     prototype appears to work (cf. `./nbextensions/code_prettify/autopep8.yaml`). More work needed (quite a bit), 
     but it looks like there's a fairly straightforward solution.
* [x] `user.clj`
  * [x] Added to support experiments with `reformat-form`.

Changes at revision `2913dc17` relative to Clojupyter `master` latest commit per 15 February 2019 (`994f680c`): 

* [x] Ensure compatibility with **Clojure v1.9** and **Clojure v1.10**
* [x] Enable **`nrepl`-based access** from interactive development environments such as CIDER/Cursive: 
  Leave `:value` unchanged, add `:mime-tagged-value` instead
* [x] **Update dependencies**, not least move to `nrepl` v0.5.3 and `cider-nrepl` v0.20.0
* [x] Improve code structure / organization
  * [x] Use a map for passing largely unchanging values around
  * [x] Abstract message content access: Use `message-*` functions
  * [x] Make functions used only locally `:private`
* [x] `core.clj`
  * [x] Refactor `configure-{shell,control}-handler` to use multi-method instead (`respond-to-message`)
  * [x] Use macro `catching-exceptions` to improve code readability
  * [x] Refactor `process-heartbeat` away
  * [x] Shorten arglists using a map instead of individual args
  * [x] Add `nrepl-server-addr` to retrieve `nrepl` server port to enable connection from interactive
   development environment
* [x] `messages.clj`
  * [x] Use multi-method `respond-to-message` dispatching on request type to eliminate individually
    named response functions such as `input-request`, `comm-open-reply`, `kernel-info-reply`, etc.
  * [x] Use macro `defresponse` to further reduce code size
  * [x] Refactor `shutdown-reply` separating shutdown actions and responding to shutdown request
  * [x] Refactor away content-calculating functions to get better locality permitted by much smaller response
    generating functions
  * [x] Refactor `send-message` and `send-router-message` to reduce code redundancy and make code easier to follow
  * [x] Refactor `execute-request-handler` a bit, add comments
  * [x] Rename `get-message-{signer,checker}` to `make-message-{signer,checker}`
  * [x] Move some generic helper functions to `util.clj`
* [x] `middleware/mime_values.clj`
  * [x] Assoc `:mime-tagged-value` to result of `to-mime` (instead of mime-tagging `:value`
    directly): Enables using the `nrepl` server using regular clients
* [x] `protocol/mime_convertible.clj`
  * [x] Print `nil` as `nil` (instead of not printing anything)
* [x] `stacktrace.clj`
  * [x] Add mechanism to control whether stacktraces are printed or not as it appears
    that `cider-nrepl` occasionally triggers uncaught exceptions. Most likely linked to upgrade of
    `nrepl` and/or `cider-nrepl`; the cause of the problem not understood as yet.
* [x] `core_test.clj`
  * [x] Update tests
* [x] Miscellaneous code cleanup
  * [x] Send error messages to Jupyter stream `stderr` instead of `stdout`
  * [x] Use `->Class`-forms, e.g. `->HiccupHTML` instead of `HiccupHTML.`, to allow interactive class updates
  * [x] Extra `log/debug` here and there
  * [x] Various reformatting here and there
* [x] Examples
  * [x] `html-demo.ipynb`
    * [x] Update to use Vega Lite instead of Highcharts (which seems to be broken in both updated version
     and current `HEAD` of `master` using Clojure 1.8)
  * [x] `incanter-demo.ipynb`
    * [x] Update to print Clojure version
