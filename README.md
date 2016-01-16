## clojupyter
A Jupyter kernel for Clojure. This will let you run Clojure code from the
IPython console and notebook.

![clojupyter](https://raw.github.com/roryk/clojupyter/master/images/demo.gif)

### installation

1. git clone https://github.com/roryk/clojupyter
2. cd clojupyter
3. make
4. make install

This will install a clojupyter executable and a configuration file to tell
Jupyter how to use it in ~/.ipython/kernels/clojure.

run the REPL with:

```bash
jupyter-console --kernel=clojure
```

or the notebook with:

```bash
jupyter-notebook
```

and select the Clojure kernel.

### status
Should work for simple stuff in the REPL. Doesn't handle errors or any type
of complex data from Clojure. Also does not handle changing namespaces. We'd really like help with this.

### collaboration
If you submit a pull request that ends up getting merged, we will give you commit access.
