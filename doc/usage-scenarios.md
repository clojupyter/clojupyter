# Usage Scenarios

Here you can read about different ways of using Clojupyter (Cλ).

Scenario overview:

| Scenario    | What                                           | Description                              | Recommended?
|-------------|------------------------------------------------|------------------------------------------| -----
| **CONDA**    | Simply do `conda install ...` to get started   | Easiest way to get started with Cλ       | Y
| **LIB**      | Include Cλ in your Clojure project             | Cλ kernel with custom functionality      | Y
| **DOCKER**   | Quickly check out Cλ                           | Try Cλ without installing anything       | N
| **ADHOC**    | Connect Cλ to running Clojure image  | Jupyter as ad-hoc interface to Clojure   | Future feature

Details of each scenario described below.

## CONDA: Use `conda` to install Clojupyter on Linux, MacOS and Windows

Clojupyter supports [Anaconda](https://www.anaconda.org) which is the installation method
[recommended by the Jupyter project](https://jupyter.readthedocs.io/en/latest/install.html#installing-jupyter-using-anaconda-and-conda).
If you are new to Clojupter and/or Clojure, we recommend that you use this method to install
Clojupyter.

Installing Clojupyter using Anaconda gets you a generic Clojupyter kernel providing essentially
unlimited functionality.  One of the key advantages to the Conda install is that, by leveraging the
infrastructure provided by [Anaconda](www.anaconda.org), it provides a very simple way to install
Clojupyter on both **Linux**, **MacOS** and **Windows**: All you need to do is (1) install
Anaconda and (2) use a single command to install Clojupyter.

See [Conda-installing Clojupyter](conda-installing.md) for details on how to install Clojupyter
using Anaconda.

The limitation of conda-installing Clojupyter is that the install artifacts, which are deployed in
the Anaconda Cloud, are generic and thus the installed kernel cannot include any libraries or code
you would like to use.  This does not prevent you from using any code or library you want: you
simply load it at the start of each session which is acceptable for many uses, not least when you
just starting out with Clojupyter.

If you to use Clojupyter kernels which have *custom* functionality built into the installed kernel,
you will probably want to review at the LIB usage scenario below and take a look at [Using
Clojupyter as a library](library.md).

Usage scenario **CONDA** overview:

| **FEATURE**             | **COMMENT**                                               |
|-------------------------|-----------------------------------------------------------|
| Headline                | Easiest way to get started with Clojupyter                |
| Audience                | End-users                                                 |
| What it gets you        | Generic Clojure/Clojupyter                                |
| How to install          | `conda install -c simplect clojupyter`                    |
| Supported platforms     | Linux, MacOS, Windows (all 64 bit)                        |
| Disadvantage            | No custom code in kernel - must be loaded in each session |


## LIB: Include Clojupyter in your Clojure project

Clojupyter is available as a library on [Clojars](https://clojars.org/) and can be used exactly like
any other library.  If you add a Clojupyter dependency to your project, your system will gain the
ability to install itself as a Clojupyter kernel.  You can use `clj` or your project uberjar to
manage your Clojupyter kernels (install, list, remove), or - if you use Leiningen - you can extend
your `project.clj` to control Clojupyter kernels using `lein`.

Clojupyter has no known platform-specific depedencies and should work on all platforms supported by
Clojure.  However, using Clojupyter in the LIB usage scenario necessarily requires installation of
Clojure on the platform in question.  Presently Clojure seems to have better support on Linux and
MacOS that it does on Windows - hopefully this will be rectified in the near future.  Users who
successfully install Clojure on Windows
(cf. [TDEPS-67](https://github.com/clojure/tools.deps.alpha/wiki/clj-on-Windows) for details on
improving Clojure's support for Windows) should be able to use Clojupyter.  Alternatively,
Windows-based users can use generic Clojupyter by installing it using Anaconda (see CONDA scenario
above).

Using Clojupyter as a library provides some of the same functionality as that provided by
[`lein-jupyter`](https://github.com/clojupyter/lein-jupyter), namely the ability to provision a
Clojupyter kernel with custom functionality, but without depending on
[Leiningen](https://leiningen.org/).  This enables the use of Clojupyter in projects which use the
[`tools.deps`](https://github.com/clojure/tools.deps.alpha) and
[Boot](https://github.com/boot-clj/boot) build tools.  Note that functionality provided does not
cover *all* of what is provided by `lein-jupyter` since it also provides the ability to use
[Parinfer](https://shaunlebron.github.io/parinfer/) with Jupyter notebooks which Clojupyter
currently doesn't.

See [Using Clojupyter as a library](library.md) for details on how use Clojupyter as a library and
install custom kernels.

Usage scenario **LIB** overview:

| **FEATURE**             | **COMMENT**  |
|-------------------------|--------------------------------------------|
| Headline                | Extend Clojupyter with custom functionality      |
| Audience                | End-users, Clojure developers                    |
| What it gets you        | Interact with Clojure applications using Jupyter |
| How to install          | `clj -m clojupyter.cmdline install --ident mykernel` <br>`lein clojupyter install --ident mykernel`       |
| Supported platforms     | Same as Clojure                                  |


## DOCKER: Try Clojupyter via a Docker image

If you have [Docker](https://www.docker.com/) and you simply want to try out Clojupyter without
installing anything, you can run the pre-built Clojupyter Docker image.  It has the full
functionality of Clojupyter, but since it is not well integrated into the host environment (it can
only 'see' notebooks in a certain sub-directory, for example), we **do not recommend** that you use
Clojupyter this way if you want to do real work - any of the other (above) ways of accessing
Clojupyter should be preferable to running it inside Docker.  For end-users new to Clojupyter we
recommend using Anaconda to install a generic Clojupyter (see CONDA scenario above) instead.  As you
gain experience you may want to install your own custom kernel, with additional libraries and
functionality preloaded (see LIB scenario above).

See [The Docker Way](docker.md) for details on how to try out Clojupyter using Docker.

Usage scenario **DOCKER** overview:

| **FEATURE**             | **COMMENT**  |
|-------------------------|--------------------------------------------|
| Headline                | Try Clojupyter without installing anything (requires Docker)  |
| Audience                | Prospective Clojupyter users                                  |
| What it gets you        | Ability to evaluate Clojure code in Jupyter                   |
| How to install          | See Installation guide                                        |
| Supported platforms     | Same as Docker                                                |


## ADHOC: Connect Clojupyter to runnning Clojure image

**Note:** *This usage scenario is not currently supported but is included for completeness' sake.
We hope to support it sometime in the future.*

In the future we want to enable connecting Jupyter to a running Clojure image - this would enable
using Jupyter as an ad-hoc user interface to Clojure by allowing expressions entered into Jupyter to
be evaluated in the remote Clojure instance.  Some development work is needed before remote
evaluation is possible, however, so we're not quite there yet.  See [TODO list on Clojupyter's
github page](https://github.com/clojupyter/clojupyter#todo) for an outline of planned features.

Usage scenario **ADHOC** overview:

| **FEATURE**             | **COMMENT**                                                   |
|-------------------------|---------------------------------------------------------------|
| Headline                | Integrate Jupyter into Clojure development workflows          |
| Audience                | Clojure developers                                            |
| What it gets you        | Ability to use Jupyter facilities in Clojure development      |
| How to install          | TBD (Generic/conda-installed Clojupyter should be sufficient) |
| Supported platforms     | Same as Clojure                                               |

