# clojupyter
A Jupyter kernel for Clojure. This will let you run Clojure code from the
IPython console and notebook.

![ipython-clojure](https://raw.github.com/roryk/ipython-clojure/master/images/demo.gif)

## installation
This will install clojupyter and a configuration file in ~/.ipython/kernels/clojure.

1. make

4. run the repl with `ipython console --profile clojure`

## status
Should work for simple stuff in the REPL. Doesn't handle errors or any type
of complex data from Clojure. Also does not handle changing namespaces.
