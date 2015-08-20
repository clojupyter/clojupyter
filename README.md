# clojupyter
A Jupyter kernel for Clojure. This will let you run Clojure code from the
IPython console and notebook.

![ipython-clojure](https://raw.github.com/roryk/ipython-clojure/master/images/demo.gif)

## installation

1. git clone https://github.com/roryk/ipython-clojure
2. cd ipython-clojure
3. make

This will install a clojupyter executable and a configuration file to tell
Jupyter how to use it in ~/.ipython/kernels/clojure.
2. run the repl with `ipython console --profile clojure`

## status
Should work for simple stuff in the REPL. Doesn't handle errors or any type
of complex data from Clojure. Also does not handle changing namespaces.
