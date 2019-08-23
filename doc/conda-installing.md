# Conda-installing Clojupyter

Table of Contents

* [Quickstart](#quickstart)
* [Using Conda: Installing Clojupyter](#using-conda-installing-clojupyter)
* [Using Conda: Upgrading Clojupyter](#using-conda-upgrading-clojupyter)
* [Using Conda: Uninstalling Clojupyter](#using-conda-uninstalling-clojupyter)
* [Supported Platforms when using Conda (Linux, MacOS, Windows)](#supported-platforms-when-using-conda)

Installing Clojupyter using Conda is the easiest way to get started with Clojupyter, but it does
have some limitations, you may want to consult [Clojupyter Usage Scenarios](usage-scenarios.md)
understand your options and the pros and cons of each of them.

## Quickstart

The Quickstart way to install Clojupyter using Conda means doing 2 things:

1. Install [Anaconda](https://anaconda.com)
1. Install Clojupyter using the package management tool `conda` which is included in Anaconda

### Installing Anaconda

Go to the [Anaconda web site](https://www.anaconda.com), Click
[Download](https://www.anaconda.com/distribution/), select your preferred platform, and download the
`Python 3.7` edition of the Anaconda Distribution.

### Installing Clojypyter

Once Anaconda is installed you use the included `conda` package management tool to install
Clojupyter from Anaconda Cloud.  Exactly how you run `conda` depends on the platform you are using,
consult the Anaconda documentation.

With access to `conda` installing Clojupyter (which is available on the `simplect` channel in
Anaconda Cloud) is straightforward: 

```
> conda install -y -c simplect clojupyter
...elided...
Successfully installed Clojupyter into ~/anaconda3/share/jupyter/kernels/conda-clojupyter.\
\n\nexit(0)\n'
done
>
```

This should work on all supported platforms: Linux, MacOS and Windows.

More details on installing below.

## Using Conda: Installing Clojupyter

You use the `conda` command to manage Conda environments and packages; this obviously includes
Clojupyter.  Conda is a full-featured package manager, in this document we focus on the very limited
set of things you need to know to manage a conda-install instance of Clojupyter.  There are features
in conda which might be of interest to Clojupyter users (among other things conda allows you to
manage 'environments' which enable you have multiple versions of your installed software; this can
be used to switch between different version of Java, Clojure and Clojupyter), but we believe most
Clojupyter users with more advanced requirements will want to switch to self-managed kernels and so
we'll not spend much time on the possibilities and features of Conda.  If you are interested [see
the technical documentation conda](https://docs.conda.io/projects/conda/en/latest/index.html).

Clojupyter is available [as the `clojupyter` package on the `simplect` channel in Anaconda
Cloud](https://anaconda.org/simplect/clojupyter).

To do a basic install, use the `conda` subcommand `install`:

```
> conda install -y -c simplect clojupyter
Collecting package metadata (repodata.json): done
Solving environment: done

## Package Plan ##

  environment location: ~/anaconda3

  added / updated specs:
    - clojupyter


The following NEW packages will be INSTALLED:

  clojupyter         simplect/osx-64::clojupyter-0.2.3snapshot-2
  maven              conda-forge/osx-64::maven-3.6.0-0


Preparing transaction: done
Verifying transaction: done
Executing transaction: | b'Clojupyter v0.2.3-SNAPSHOT - \n\n \
Successfully installed Clojupyter into ~/anaconda3/share/jupyter/kernels/conda-clojupyter.\
\n\nexit(0)\n'
done
>
```

Conda organizes software into **packages** which have **versions** which can be built and deployed
multiple times with a **build number**, and delivers it on **channels**.  Clojupyter is available on
the `simplect` channel which you will always have to specify since it is not among the default
channels in Anaconda Cloud (you can configure `conda` to use the channel by default, though, see
[Conda configuration](https://docs.conda.io/projects/conda/en/latest/user-guide/configuration/index.html)
for details).

You can install a package simply by specifying the package name, or be more specific and indicate
which version and/or build number you want to install.  The way to express the package/version/build
is known as a **package_spec**.

To install the most recent version of Clojupyter:

```
> conda install -c simplect clojupyter
```

To install a specific version (giving you the build with the highest build number):

```
> conda install -c simplect clojupyter=0.2.3snapshot
```

To install a specific build of a specific version (here: build number 2):

```
> conda install -c simplect clojupyter=0.2.3snapshot=2
```

Conda will normally prompt you to confirm the action it is about to take; you can tell it to skip
prompting using the `-y` flag.

Using both version and buildnum enables you tell conda to go back and forth between versions as
needed, including *downgrades* (note the warning from conda in the middle of the output):

```
> conda list | grep clojupyter
clojupyter                0.2.3snapshot                 2    simplect

> conda install -y -c simplect clojupyter=0.2.3snapshot=1
Collecting package metadata (repodata.json): done
Solving environment: done

## Package Plan ##

  environment location: ~/anaconda3

  added / updated specs:
    - clojupyter==0.2.3snapshot=1


The following packages will be DOWNGRADED:

  clojupyter                                0.2.3snapshot-2 --> 0.2.3snapshot-1


Preparing transaction: done
Verifying transaction: done
Executing transaction: \ b'Clojupyter v0.2.3-SNAPSHOT@ ... \
Successfully installed Clojupyter into ...
done

> conda list | grep clojupyter
clojupyter                0.2.3snapshot                 1    simplect
>
```

In the `conda list` output the columns left to right are 'Package Name', 'Package Version', 'Package
Build Number', and 'Channel Name'.

Conda will occasionally want to upgrade other components than Clojupyter (including conda itself)
when you use package management commands related to Clojupyter.  If you want to control how conda
upgrades components and dependencies consult the `conda` technical documentation for the relevant
command.


## Using Conda: Upgrading Clojupyter

Conda enables you upgrade packages using the `conda` subcommand `update`:

```
> conda list | grep clojupyter
clojupyter                0.2.3snapshot                 1    simplect

> conda update -c simplect clojupyter
Collecting package metadata (repodata.json): done
Solving environment: done

## Package Plan ##

  environment location: ~/anaconda3

  added / updated specs:
    - clojupyter


The following packages will be UPDATED:

  clojupyter                                0.2.3snapshot-1 --> 0.2.3snapshot-2


Proceed ([y]/n)? y

Preparing transaction: done
Verifying transaction: done
Executing transaction: \ b'Clojupyter v0.2.3-SNAPSHOT@cd18-DIRTY - Conda Unlink\n\n    0 files found in ~/anaconda3/share/jupyter/kernels/conda-clojupyter.\n    Conda unlink completed successfully.\n\nexit(0)\n'
- b'Clojupyter v0.2.3-SNAPSHOT - \n\n    Successfully installed Clojupyter into ~/anaconda3/share/jupyter/kernels/conda-clojupyter.\n\nexit(0)\n'
done
>
```

Conda update will upgrade your installed Clojupyter to the highest build number of the highest
version.  If you want something other than, use `conda install` and use the package_spec to specify
exactly which version and build number you want.

## Using Conda: Uninstalling Clojupyter

You use the `conda` subcommand `remove` to uninstall Clojupyter:

```
> conda list | grep clojupyter
clojupyter                0.2.3snapshot                 2    simplect

> conda remove clojupyter
Collecting package metadata (repodata.json): done
Solving environment: done

## Package Plan ##

  environment location: ~/anaconda3

  removed specs:
    - clojupyter


The following packages will be REMOVED:

  clojupyter-0.2.3snapshot-2
  maven-3.6.0-0


Proceed ([y]/n)? y

Preparing transaction: done
Verifying transaction: done
Executing transaction: \ b'Clojupyter v0.2.3-SNAPSHOTY - Conda Unlink\n\n    \
0 files found in ~/anaconda3/share/jupyter/kernels/conda-clojupyter.\n    \
Conda unlink completed successfully.\n\nexit(0)\n'
done
>
```

## Supported Platforms when using Conda

Anaconda Cloud supports a number of platforms, the list includes `linux-32`, `linux-64`
`linux-aarch64`, `linux-armv6l`, `linux-armv7l`, `linux-ppc64le`, `osx-64`, `win-32`, and `win-64`.

At this time Clojupyter's conda platform support comprises `linux-64`, `osx-64` and `win-64`.  It
should be fairly straightforward to support `linux-32` and `win-32` (has not been tried), support
for other of the above platform might be harder.
