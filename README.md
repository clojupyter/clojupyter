## clojupyter
A Jupyter kernel for Clojure. This will let you run Clojure code from the
Jupyter console and notebook.

![clojupyter](https://raw.github.com/roryk/clojupyter/master/images/demo.png)

### Getting Started 

See [this notebook](https://raw.github.com/roryk/clojupyter/master/examples/html-demo.ipynb) for examples of what you can do and [this one](https://raw.github.com/roryk/clojupyter/master/examples/incanter-demo.ipynb) for an example using Incanter for plotting. Below are some screen shots to whet your appetite:

![](https://raw.githubusercontent.com/aria42/clojupyter/mime-improvements/images/html-demo.png =600x400)

![](https://raw.githubusercontent.com/aria42/clojupyter/mime-improvements/images/incanter-demo.png =600x400)


### Installation

1. git clone https://github.com/clojupyter/clojupyter
2. cd clojupyter
3. make
4. make install

This will install a clojupyter executable and a configuration file to tell
Jupyter how to use clojupyter in from jupyter's user kernel location (
`~/.local/share/jupyter/kernels` on linux and `~/Library/Jupyter/kernels`
on Mac).

run the REPL with:

```bash
jupyter-console --kernel=clojure
```

or the notebook with:

```bash
jupyter-notebook
```

and select the Clojure kernel.

#### The Docker way

[A Docker image](https://github.com/kxxoling/jupyter-clojure-docker)
is also made to make the installation easier, and isolate the environment cleaner.
What you need to do is:

1. [Install Docker](https://docs.docker.com/engine/installation/) based on your platform.
2. Run ``docker run --rm -p 8888:8888 kxxoling/jupyter-clojure-docker`` to have clojupyter
   installed on your OS.

The first time you start a container would pull the Docker image, which takes time.

More specificated introduction and usage guide is on [the home page of the Docker image](https://github.com/kxxoling/jupyter-clojure-docker).


#### Installation on Windows

_All these following commands must run in bash (recommend git bash)_

1. Install MinGW, install packages: mingw32-base, mingw-developer-toolkit
2. Add the absolute path of 'MinGW/bin' to the path system environment variable.
3. Rename 'MinGW/bin/mingw32-make.exe' to 'MinGW/bin/make.exe'
4. `git clone https://github.com/roryk/clojupyter`
5. `cd clojupyter`
6. `make`
7. Copy two files 'clojupyter/resources/kernel.json' and 'clojupyter/bin/clojupyter' to the folder '%APPDATA%/jupyter/kernels/clojure' *(create folder if missing)*
8. Edit 'kernel.json' line 2: 

>   "argv": ["bash", "**full-path-to-APPDATA/clojupyter/bin/clojupyter**", "{connection_file}"]

_If you want run jupyter in cmd, replace "bash" to the full path of bash.exe_


### Removing the stale OSX kernel cache
Recently `clojupyter` was renamed from `ipython-clojure` because `clojupyter` is more accurate and,
more importantly, sounds cooler. OSX caches the kernels you choose in `$USER/Library/jupyter/kernels`,
so it will look for `ipython-clojure` even if you've installed the new version. Remove
the `clojure` directory in there and it will pick up the `clojupyter` executable instead if
you're having some troubles after installing.

### To Do:
 * Shut down cleanly.
 * Do syntax checking. It currently returns nil on unbalanced form. Borrow cider-nrepl middleware for this.
 * Allow controls from Jupyter, including timeout and what classes of stack frames to show.
 * Test (implement?) interrupt handling. Default middleware for interruptible-eval is loaded.
 * Implement file load. Use cider-nrepl middleware.

### Collaboration
If you submit a pull request that ends up getting merged, we will give you commit access.
