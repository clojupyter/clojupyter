See [`README`](../README.md) to find Clojars coordinates of the current Clojupyter version.

# Using Clojupyter as a library

Your Clojure project can be turned into a Jupyter kernel by adding Clojupyter to your project dependencies.

Here's an example of how to do it. Let's have a simple Clojure project with Clojupyter included in its dependencies:

```
bash> tree .
.
├── deps.edn
└── src
    └── user.clj

1 directory, 2 files

bash> cat deps.edn
{:deps {clojupyter {:mvn/version "0.2.3"}}
 :aliases {:depstar
           {:extra-deps
            {seancorfield/depstar {:mvn/version "0.3.0"}}}}}
            
bash> cat src/user.clj
(ns user (:require [clojupyter.kernel.version :as ver]))

(defn user-ver []
  (ver/version-string-long))
```

Now create an uberjar using the `depstar` option from Clojupyter's command line interface. This way we ensure the Clojupyter is properly included.
```
bash> clojure -A:depstar -m hf.depstar.uberjar clojupyter-standalone.jar
Building uber jar: clojupyter-standalone.jar
{:warning "clashing jar item", :path "META-INF/sisu/javax.inject.Named", :strategy :noop}
...rest of warnings elided...
```
If everything's all right a new file `clojupyter-standalone.jar` is created:

```
bash> tree
.
├── clojupyter-standalone.jar
├── deps.edn
└── src
    └── user.clj

1 directory, 3 files
```

Now let's install this jar file as a Jupyter kernel under the name `mykernel-1`:

```
bash> clj -m clojupyter.cmdline install --ident mykernel-1 --jarfile clojupyter-standalone.jar
Clojupyter v0.2.3 - Install local

    Installed jar:	clojupyter-standalone.jar
    Install directory:	~/Library/Jupyter/kernels/mykernel-1
    Kernel identifier:	mykernel-1

    Installation successful.

exit(0)
```

and check the installation using `list-installs`:

```
bash> clj -m clojupyter.cmdline list-installs
Clojupyter v0.2.3 - All Clojupyter kernels

    |      IDENT |                                  DIR |
    |------------+--------------------------------------|
    | mykernel-1 | ~/Library/Jupyter/kernels/mykernel-1 |

exit(0)
```

If we start Jupyter Lab, we see that the kernel is a available as a Clojupyter kernel named
`mykernel-1`:

<img src="../images/2b4881cd-07d7-4229-b52f-34825cbc1b1e--mykernel-1.png" width="400px"/>

If we start the kernel we see that it contains the litte function we defined (cf. `user.clj` above):

<img src="../images/2b4881cd-07d7-4229-b52f-34825cbc1b1e--mykernel-1-eval.png" width="400px"/>

If we make a small change to `user.clj`:

```
bash> cat src/user.clj
(ns user (:require [clojupyter.kernel.version :as ver]))

(defn user-ver []
  (str "V2: "(ver/version-string-long))) ;; <== CHANGE IN THIS LINE
```

generate a new uberjar:

```
bash> clojure -A:depstar -m hf.depstar.uberjar kernel2.jar
Building uber jar: kernel2.jar
{:warning "clashing jar item", :path "META-INF/sisu/javax.inject.Named", :strategy :noop}
...warnings elided...
```

and install this as kernel with a new identifier:

```
bash> clj -m clojupyter.cmdline install --ident mykernel-2 --jarfile kernel2.jar
Clojupyter v0.2.3 - Install local

    Installed jar:	kernel2.jar
    Install directory:	~/Library/Jupyter/kernels/mykernel-2
    Kernel identifier:	mykernel-2

    Installation successful.

exit(0)
```

we now have a new kernel installed:

```
bash> clj -m clojupyter.cmdline list-installs
Clojupyter v0.2.3 - All Clojupyter kernels

    |      IDENT |                                  DIR |
    |------------+--------------------------------------|
    | mykernel-1 | ~/Library/Jupyter/kernels/mykernel-1 |
    | mykernel-2 | ~/Library/Jupyter/kernels/mykernel-2 |

exit(0)
```

which shows up in Jupyter Lab:

<img src="../images/2b4881cd-07d7-4229-b52f-34825cbc1b1e--mykernel-2.png" width="400px"/>

and behaves differently than the first edition of the kernel:

<img src="../images/2b4881cd-07d7-4229-b52f-34825cbc1b1e--mykernel-2-eval.png" width="400px"/>

Let's leave the system like we found it by removing the kernels:

```
bash> clj -m clojupyter.cmdline remove-installs-matching 'mykernel-[12]'
Clojupyter v0.2.3 - Remove installs

    Step: Delete ~/Library/Jupyter/kernels/mykernel-1
    Step: Delete ~/Library/Jupyter/kernels/mykernel-2

    Status: Removals successfully completed.

exit(0)
```

which leaves us without any Clojupyter kernels:

```
bash> clj -m clojupyter.cmdline list-installs
Clojupyter v0.2.3 - All Clojupyter kernels

    No kernels match ''.

exit(1)
```
