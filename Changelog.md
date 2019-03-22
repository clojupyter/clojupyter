# Changelog

## 0.2.2

### Refactor Round 3 (2019-03-15)

* Change code structure towards having an open, extensible architecture (more is needed to make extensible)
  * Introduce **handler-based middleware structure** similar to [nREPL](https://nrepl.org) and
  [Ring](https://github.com/ring-clojure/ring)
    * Enable higher degree of **separation of concerns**, thus enabling adding features in a
    modular fashion
    * Introduce **layered message handling**: Incoming messages pass through a define sequence of
    middleware handlers with outbound message passing through the same set of handlers in
    reverse order, yielding a layered message-processing architecture
    * Introduce  `handler`, `middleware`, and `transport` abstractions similar to those of
    [employed in nREPL](https://nrepl.org/nrepl/0.6.0/design/transports.html), but adapted
    to fit into the multichannel context of Jupyter (cf. `send-stdin`, `send-iopub`, and
    `send-req` in `clojupyter.transport`)
  * **Convert existing functionality to middleware-based structure**, messaging-handling is now defined
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
    ```

  * Move kernel-specific functionality into `clojupyter.kernel.*`:
    * `state.clj`: Move to `clojupyter.kernel.state`.
    * `stacktrace.clj`: Move to `clojupyter.kernel.stacktrace`
    * `history.clj`: Move to `clojupyter.kernel.history`
  * Make functionality provided by `clojupyter.misc.display` and `clojupyter.misc.helper`
    available in namespace `clojupyter`
    * Longer term `clojupyter` should contain functions for core functionality. Eventually
      use of `clojupyter.misc.display` and `clojupyter.misc.helper` should be deprecated.
* Add support for configuration file, loaded at startup
  * Read from `~/Library/Preferences/clojupyter.edn` on MacOS and `~/.config/clojupyter.edn` on
  Linux (per XDG Base Directory specification)
  * Control printing of stacktraces (workaround, cf elsewhere) in configuration file
  * Control log level in configuration file
  * Control ZMQ traffic logging in configuration file
* Improve tests
  * Convert existing tests to work with new structure
  * Add tests for middleware providing checks for basic message responses
* Miscellaneous
  * Upgrade Jupyter messing protocol version from "5.0" to "5.3" (no
  apparent impact)
  * Provide access to clojupyter version in namespace
    `clojupyter.misc.version`, value defined in `resources/version.edn`.
  * Stacktraces - introduce (temporary) workaround until uncaught exceptions in nrepl are fixed
    * Introduce control over whether stacktraces are printed (in `clojupyter.kernel.stacktrace`)
    * Disable stacktrace printing by default (error message informs user of how turn it on)
  * Introduce namespace `clojupyter` as main user API entrypoint, replacing `clojupyter.core`
  * Eliminate namespace `clojupyter.core`
  * Move `mime-values` nREPL middleware to `clojure.nrepl-middleware.mime-values` to avoid confusion
    with clojupyter middleware
  * Move location history file to `~/Library/Caches` on MacOS and `~/.local/share` (per XDG Base
    Directory specification)
  * Simplified kernel code structure
  * Replace `clj-time` with `clojure.java-time`
  * Rename `idents` in ZMQ messages to `envelope`
  * Define names for Jupyter message identifiers (in `clojupyter.misc.jupyter`)
  * Eliminate `zmq-comm` protocol - not needed
  * Use `:aot` only in `uberajr` profile in `project.clj`

### Refactor Round 2 (2019-03-01)

* Improve code structure / organization
  * **Use a single atom for all global state.** Provide abstraction for manipulating
    global state.  Eliminate `states`.
  * Use **accessor functions for socket access**. Avoids storing sockets in `Zmq_Comm`.
   Accessor functions for `stdin-socket` and `iopub-socket` in passed-around map `S`.
  * Eliminate atoms containing sockets - not necessary since they are used, not updated.
  * Remove depencency on `async.core` - not used.   
* `core.clj`
  * Replace `configure-shell-handler` and `configure-control-handler` with `make-handler`.  Their
   role is the same, the main difference that `execution-counter` is incremented on the shell
   socket.  Why not simply use the same handler?
  * Integrate creation of signer and checker functions.
  * Simplify `run-kernel` based on above.
  * Separate code related to `nrepl-server` and Clojupyter middlerware into separate namespace:
   `clojupyter.misc.nrepl-server`.
* `messages.clj`
  * Considerably simplify `make-shell-handler` and `make-control-handler`: Unify into one.
  * Use consolidation of global state to turn `handle-execute-request` into regular responding
   function defined with `defresponse`.
  * Add `with-debug-logging`
  * Add accessor functions for socket access.
  * Integrate creation of signer and checker.
  * Integrate `status-content` and `pyin-content` into point-of-use.
  l* Use abstractions to access global state.
* `nrepl_comm.clj`  
   * Refactor giant `defrecord` into smaller functions, no change in logic.
* `history.clj`
   * Move determination of max history length here.
* `nrepl_server.clj`(added)
   * `nrepl`-related code from `core.clj`.
* `state.clj`
   * Rename from `states.clj` - add single atom for all of global state.
   * Add functions to manipulate global state.
* `zmq_comm.clj`
   * Use accessor functions instead of access-by-socket-name.
* `util.clj`
   * Add `with-debug-logging` macro.
   * Add `reformat-form`.  Very early experiment with auto-indent / auto-reformat of cells based on
     [Code Prettify](https://jupyter-contrib-nbextensions.readthedocs.io/en/latest/nbextensions/code_prettify/README_code_prettify.html)
     / [Autopep8](https://github.com/kenkoooo/jupyter-autopep8). Very primitive / early cell reformatting
     prototype appears to work (cf. `./nbextensions/code_prettify/autopep8.yaml`). More work needed (quite a bit), 
     but it looks like there's a fairly straightforward solution.
* `user.clj`
  * Added to support experiments with `reformat-form`.

### Refactor Round 1 (2018-02-27)

Changes at revision `2913dc17` relative to Clojupyter `master` latest commit per 15 February 2019 (`994f680c`): 

* Ensure compatibility with **Clojure v1.9** and **Clojure v1.10**
* Enable **`nrepl`-based access** from interactive development environments such as CIDER/Cursive: 
  Leave `:value` unchanged, add `:mime-tagged-value` instead
* **Update dependencies**, not least move to `nrepl` v0.5.3 and `cider-nrepl` v0.20.0
* Improve code structure / organization
  * Use a map for passing largely unchanging values around
  * Abstract message content access: Use `message-*` functions
  * Make functions used only locally `:private`
* `core.clj`
  * Refactor `configure-{shell,control}-handler` to use multi-method instead (`respond-to-message`)
  * Use macro `catching-exceptions` to improve code readability
  * Refactor `process-heartbeat` away
  * Shorten arglists using a map instead of individual args
  * Add `nrepl-server-addr` to retrieve `nrepl` server port to enable connection from interactive
   development environment
* `messages.clj`
  * Use multi-method `respond-to-message` dispatching on request type to eliminate individually
    named response functions such as `input-request`, `comm-open-reply`, `kernel-info-reply`, etc.
  * Use macro `defresponse` to further reduce code size
  * Refactor `shutdown-reply` separating shutdown actions and responding to shutdown request
  * Refactor away content-calculating functions to get better locality permitted by much smaller response
    generating functions
  * Refactor `send-message` and `send-router-message` to reduce code redundancy and make code easier to follow
  * Refactor `execute-request-handler` a bit, add comments
  * Rename `get-message-{signer,checker}` to `make-message-{signer,checker}`
  * Move some generic helper functions to `util.clj`
* `middleware/mime_values.clj`
  * Assoc `:mime-tagged-value` to result of `to-mime` (instead of mime-tagging `:value`
    directly): Enables using the `nrepl` server using regular clients
* `protocol/mime_convertible.clj`
  * Print `nil` as `nil` (instead of not printing anything)
* `stacktrace.clj`
  * Add mechanism to control whether stacktraces are printed or not as it appears
    that `cider-nrepl` occasionally triggers uncaught exceptions. Most likely linked to upgrade of
    `nrepl` and/or `cider-nrepl`; the cause of the problem not understood as yet.
* `core_test.clj`
  * Update tests
* Miscellaneous code cleanup
  * Send error messages to Jupyter stream `stderr` instead of `stdout`
  * Use `->Class`-forms, e.g. `->HiccupHTML` instead of `HiccupHTML.`, to allow interactive class updates
  * Extra `log/debug` here and there
  * Various reformatting here and there
* Examples
  * `html-demo.ipynb`
    * Update to use Vega Lite instead of Highcharts (which seems to be broken in both updated version
     and current `HEAD` of `master` using Clojure 1.8)
  * `incanter-demo.ipynb`
    * Update to print Clojure version
