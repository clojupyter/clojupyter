(ns clojupyter
  (:require
   [clojupyter.misc.display		:as display]
   [clojupyter.misc.helper		:as helper]
   [clojupyter.misc.javascript-amd	:as jsamd]
   [clojupyter.misc.util		:as u]))

(def display			display/display)
(def hiccup-html		display/hiccup-html)
(def html			display/html)
(def latex			display/latex)
(def markdown			display/markdown)

(def add-dependencies		helper/add-dependencies)

(def ^{:arglists '([jsdefs])}
  amd-add-javascript
  "Returns a string representing a Javascript `config()` statement for
  RequireJS (cf. https://requirejs.org) providing access to Javascript
  libraries using Asynchronuous Module Definitions (AMDs).

  `jsdefs` must be a vector of `jsdef` (cf. `clojure.spec` definition
  `::jsdef` in namespace `clojupyter.javascript-amd`).

  Example:

    (amd-add-javascript
       [{:ident :hicharts :exports \"Highcharts\" :url \"https://code.highcharts.com/highcharts\"}])

  The above expression yields a RequireJS configuration providing
  access to the Highcharts Javascript library available at
  `code.highcharts.com` and referenced using `:hicharts` using
  `wrap-require`.  The library exports the symbol `Highcharts`.

  Given the above RequireJS configuration, the Highcharts library can
  be accessed using `wrap-require` like this:

    (amd-wrap-require [:hicharts]
      (format \"function(hc){hc.chart('%s',%s)}\" nodeid (json/write-str data)

  where the function's formal parameter `hc` is bound to the exported
  symbol `Highcharts` from the Highchart library which is referenced
  by `:hicharts`.  The number of ident-bindings in `wrap-require` and
  the number of arguments in the Javascript function must be the same,
  and referenced symbols are bound in order."
  jsamd/amd-add-javascript)

(def ^{:arglists '([jsdefs])}
  amd-add-javascript-html
  "Returns a string representing a Javascript RequireJS `require()` statement.
  Cf. `add-javascript` for usage information."
  jsamd/amd-add-javascript-html)

(def ^{:arglists '([ident-vec javascript-function])}
  amd-wrap-require
  "Same as `add-javascript` except the returned string is embedded in a
  `script` element wrapped in a `hiccup-html` form.
  Cf. `add-javascript` for details."
  jsamd/amd-wrap-require)

(def ^:dynamic *clojupyter-version*
  "Value is a map representing the version of clojupyter as a map with
  the keys `:major`, `:minor`, `:incremental`, and `qualifier`, where
  the former 3 are integers and the latter is a string.  Analoguous to
  `*clojure-version*`." 
  {:major 0, :minor 0, :incremental 0, :qualifier nil})
