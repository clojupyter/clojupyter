# Changelog

## v0.1.4

Changes relative to Clojupyter `master` latest commit per 15 February 2019 (`994f680c`):

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
