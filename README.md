

## Auto-Reindent Proof-of-Concept

This is an **experimental branch** of clojupyter showing a proof-of-concept (PoC) for supporting automatic reformatting of Jupyter cells containing Clojure code.  Jupyter supports rudimentary indenting support out-of-the-box, in the client.  It is, however, not a very good solution for a Lisp-family language like Clojure.  This PoC shows leveraging the kernel to reformat cells.

This PoC is based on 
[Code Prettify](https://jupyter-contrib-nbextensions.readthedocs.io/en/latest/nbextensions/code_prettify/README_code_prettify.html) / [Autopep8](https://github.com/kenkoooo/jupyter-autopep8).

Here's an animated GIF showing the PoC in action:

<img src="https://github.com/klausharbo/clojupyter/blob/reindent-poc/images/2019-03-03%20clojupyter%20autoindent%20poc.gif" width="50%"/>

## To try it

* Add `nbextensions` to Jupyter: `conda install -c conda-forge jupyter_contrib_nbextensions``
* Configure `autopep8`:
  * Start Jupyter notebook: `jupyter notebook`
  * In Jupyter file browser: Select `Nbextensions` tab (or go to URL `http://localhost:8888/tree#nbextensions_configurator`)
  * Select `Autopep8`
  * Press `Enable`(blue button) if it not already enabled
  * In the section beginning with the text '*json defining library calls required to load*' add the text
  
    ```
    {
      "python": {
        "library": "import json\nimport autopep8",
        "prefix": "print(json.dumps(autopep8.fix_code(u",
        "postfix": ")))"
      },
      "clojure": {
        "library": "(str)",
        "prefix":  "(clojupyter.misc.util/reformat-form ",
        "postfix": " )"
      }
    }
    ```
    The exact text to insert (for `Anaconda3-2018.12`) can be found in `./nbextensions/code_prettify/autopep8.yaml`.
  * **Note**: For the change to take effect it appears that you must click outside the text editing area.
  * Select the *Files* tab
  * Optional: Go back to *Nbextensions* tab, select `autopep8`, and verify that your configuration changes have take effect:
    `autopep8` is enabled and the text you inserted is visible in '*json defining...*' section.
  * Open a Jupyter document with this version of the clojupyter kernel
  * `autopep8` should have added a new icon in the Jupyter toolbar: An icon showing a hammer.
  * Evaluate a cell to verify that the kernel is running
  * Select a cell with Clojure code that needs reformatting / autoindenting.  (In this version the cell must contain a single
    form. It should not be hard to extend this for arbitrary cells containing well-formed Clojure, less sure at this point if
    reindenting syntactically incorrect cells will work, maybe not.)
  * Click the `autopep8` icon (the hammer icon).
  * The cell should update to contain nicely formatted Clojure code.

I imagine there's a way to accomplish the above much simpler using the command line, but I don't know how (yet).

## Implementation

The implementation of this is embarrassingly simple, literally 2 lines of code (in `util.clj`)

```
(def reformat-form
  (rcomp read-string zp/zprint-str pr-str println)) 
```

and adding `autopep8` configuration to cause Jupyter to invoke `reformat-form`:

```
"clojure": {
   "library": "(str)",
   "prefix":  "(clojupyter.misc.util/reformat-form ",
   "postfix": " )"
}
```

as described above.  So there's no new communication with the kernel, the Jupyter extension simply causes evaluation of an expression which calculates the new content.  There may be downsides to this rather simplistic approach, but it's very effective and means that the extension can work with any kernel.

`reformat-form` is clearly much too simple, as it probably shouldn't do `read-string` but rather format the *text* of the cell as this will (I presume, judging from a cursory look at the `zprint` docs) preserve comments and forms skipped by the reader.  It's not clear that `zprint` is the best solution for the actual formatting, but it does a decent job for now.  Clearly any proper support would allow the use to control which library to be used for code formatting, and provide ways to control its configuration.

**Note**: This approach does in fact not require anything from the kernel so any user can in fact accomplish this be defining `reformat-form` in their Jupyter notebook.



**End of PoC**
---------------------------------------


# clojupyter
A Jupyter kernel for Clojure. This will let you run Clojure code from the
Jupyter console and notebook.
[![Build Status](https://travis-ci.org/clojupyter/clojupyter.svg?branch=master)](https://travis-ci.org/clojupyter/clojupyter)

![clojupyter](https://raw.github.com/roryk/clojupyter/master/images/demo.png)

### Getting Started 

See [this notebook](https://github.com/aria42/clojupyter/blob/mime-improvements/examples/html-demo.ipynb) for examples of how you can display HTML and use external Javascript:

<img src="https://raw.githubusercontent.com/aria42/clojupyter/mime-improvements/images/html-demo.png" width="100%"/>

You can also use existing JVM charting libraries since you can render any Java BufferedImage. Here's a [sample notebook](https://github.com/aria42/clojupyter/blob/mime-improvements/examples/incanter-demo.ipynb) using the Clojure-based [Incanter](https://github.com/incanter/incanter) library:

<img src="https://raw.githubusercontent.com/aria42/clojupyter/mime-improvements/images/incanter-demo.png" width="100%"/>

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

**Note** The Docker image below runs Clojure 1.8 and clojupyter-0.1.0.  Hopefully we'll be able to provide an up-to-date Docker image soon.

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

### To Do:
 * Shut down cleanly.
 * Do syntax checking. It currently returns nil on unbalanced form. Borrow cider-nrepl middleware for this.
 * Allow controls from Jupyter, including timeout and what classes of stack frames to show.
 * Test (implement?) interrupt handling. Default middleware for interruptible-eval is loaded.
 * Implement file load. Use cider-nrepl middleware.

### Collaboration
If you submit a pull request that ends up getting merged, we will give you commit access.
