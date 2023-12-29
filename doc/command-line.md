# Using Clojupyter from the command line

**NOTE** The commands listed in this document show how to use Clojupyter's command line facilities
which you will normally only need if you are using Clojupyter as a library.  Although the commands
are available in the installed Clojupyter jarfile (see details below), you will mostly likely not
need them if you are using a conda-installed Clojupyter.  If you want to how to install and manage a
Clojupyter kernel using Anaconda, see [Conda-installing Clojupyter](conda-installing.md).

## Table of contents

* [Getting usage information](#getting-usage-information)
* [Using the Clojupyter command line](#using-the-clojupyter-command-line)

* Available commands
  * [help](#help)
  * [install](#install)
  * [list-commands](#list-commands)
  * [list-installs-matching](#list-installs-matching)
  * [list-installs](#list-installs)
  * [remove-install](#remove-install)
  * [remove-installs-matching](#remove-installs-matching)
  * [version](#version)

* Development-only commands (not relevant for most users)
  * [conda-build](#conda-build)
  * [conda-link](#conda-link)
  * [conda-unlink](#conda-unlink)
  * [list-dvl-commands](#list-dvl-commands)
  * [supported-os?](#supported-os)

## Getting usage information

Often, the easiest way to get information about a command is simply to list the help information
available directly from Clojupyter itself:

```
> clj -M -m clojupyter.cmdline help version
WARNING: parse-boolean already refers to: #'clojure.core/parse-boolean in namespace: omniconf.core, being replaced by: #'omniconf.core/parse-boolean
Clojupyter v0.4.319@319 - Help

    Use command 'list-commands' to see a list of available commands.

    Use command 'help <cmd>' to get documentation for individual commands.

    Docstring for 'version':

        Clojupyter cmdline command: Lists Clojupyter version information.

          Note that this function is designed to be used from the command line and is normally not called
          from the REPL although this does in fact work.  Note also that the function itself, if used
          directly from the REPL, returns a data structure containing a vector of strings which will be sent
          to standard output, whereas the cmdline command itself actually sends the strings to stdout.

          COMMAND ARGUMENTS:

            - None

          FLAG/OPTIONS:

            - None

          EXAMPLE USE:

            > clj -m clojupyter.cmdline version
            Clojupyter v0.2.3 - Version

                     #:version{:major 0,
                               :minor 2,
                               :incremental 3,
                               :qualifier "SNAPSHOT",
                               :lein-v-raw "cd18-DIRTY"}

            exit(0)
            >

exit(0)
```

## Using the Clojupyter command line

All Clojupyter's command line facilities reside in the namespace `clojupyter.cmdline` and build on
Clojure's command line interface which means that the function `clojupyter.cmdline/-main` is the
main entry point.

The command line interface consists of 'commands' serving various purposes such as *listing*
(`list-installs`, `list-installs-matching`), *installing* (`install`), and *removing*
(`remove-install`, `remove-installs-matching`) Clojupyter kernels.  All the commands are described
in the sections below, but you'll find the details of their use in the docstrings of functions in
`clojupyter.cmdline`.  From the cmdline you can access the command documentation using the `help`
command.

How you access the commands depends on your setup and preferences.  Perhaps the most straightforward
way is simply to use the `clj` wrapper which has been part of the Clojure distribution since v1.9:

```
> clj -m clojupyter.cmdline list-installs-matching test
Clojupyter v0.2.3-SNAPSHOT - Clojupyter kernels matching the regular expression 'test'.

    |  IDENT |                              DIR |
    |--------+----------------------------------|
    | test-1 | ~/Library/Jupyter/kernels/test-1 |
    | test-2 | ~/Library/Jupyter/kernels/test-2 |
    | test-3 | ~/Library/Jupyter/kernels/test-3 |

exit(0)
>
```

If you use Leiningen you may prefer to define a Leiningen *alias* enabling you to use `lein` to
access Clojupyter commands.  Adding this to `project.clj`

```edn
:aliases	{"clojupyter"			["run" "-m" "clojupyter.cmdline"]}
```

allows you to use the command

```
> lein clojupyter list-installs-matching test
```

and get the same result as above.

It is also possible to use the command line facilities directly in an 'uberjar' containing Clojupyter

```
> java -cp ./target/clojupyter-0.2.3-SNAPSHOT-standalone.jar clojupyter.cmdline list-installs-matching test
```

Normally you probably won't want to use that method, but it is convenient if you have a Clojupyter
`jar`-file lying around somewhere and you want to know its version:

```
> java -cp ~/anaconda3/share/jupyter/kernels/conda-clojupyter/clojupyter-standalone.jar clojupyter.cmdline version
Clojupyter v0.2.3 - Version

         {:version/major 0,
          :version/minor 2,
          :version/incremental 3,
          :version/qualifier "SNAPSHOT",
          :version/lein-v-Raw "cd18-DIRTY"}

exit(0)
>
```

In addition to providing access to various facilities for managing Clojupyter kernels, the command
line interface is also used in the tool chain providing Anaconda installation: All the code for
building, linking (installing) and unlinking (removing) conda-installed Clojupyter kernels use the
command line interface.  Using Clojure allows us to leverage Java's platform independence to support
conda-based installation on Linux, MacOS and Windows.  In fact, behind the scenes, conda's package
management machinery uses Clojupyter's own jarfile to install and uninstall the Clojupyter kernel
which includes the jarfile itself!

## Available commands

The commands appear in alphabetical order.

### `help`

The `help` command provides access to the docstrings of the command functions, which provides the
most details on the command options.  The `help` can be invoked from the cmdline like this

```
> clj -m clojupyter.cmdline help version
Clojupyter v0.2.3 - Help

    Use command 'list-commands' to see a list of available commands.

    Use command 'help <cmd>' to get documentation for individual commands.

    Docstring for 'version':

        Clojupyter cmdline command: Lists Clojupyter version information.

          Note that this function is designed to be used from the command line and is normally not called
          from the REPL although this does in fact work.  Note also that the function itself, if used
          directly from the REPL, returns a data structure containing a vector of strings which will be sent
          to standard output, whereas the cmdline command itself actually sends the strings to stdout.

          ...elided...
exit(0)
>
```

### `install`

The `install` command is used to install Clojupyter locally on the current machine.  The command
works both in Clojupyter's own repo, and if you are using Clojupyter as library included in another
project.

```
Clojupyter cmdline command: Installs a Clojuputer kernel on the local host based on the contents of
          the code repository in the current directory.

          Note that this function is designed to be used from the command line and is normally not called
          from the REPL although this does in fact work.  Note also that the function itself, if used
          directly from the REPL, returns a data structure containing a vector of strings which will be sent
          to standard output, whereas the cmdline command itself actually sends the strings to stdout.  The
          function receives its arguments as string values.

          OPTIONS:

            -h, --host:         Install kernel such that it is available to all users on the host.  If not
                                specified installs the kernel in the Jupyter kernel directory of the current
                                user.  See platform documentation for details on the location of host-wide and
                                user-specific Jupyter kernel directories.

            -i, --ident:        String to be used as identifier for the kernel.

            -j, --jarfile:      Filename of the jarfile, which must be a standalone jar containing Clojupyter,
                                to be installed.  If the not specified, uses any standalone jarfile found in
                                the current directory or one of its subdirectories, provided a single such
                                file is found.  If zero or multiple standalone jarfiles are found an error is
                                raised.
```

If you want to know how to install Clojupyter when using it as a library, see [Using Clojupyter as a
library](library.md).

#### Kernel identifiers

Fundamentally, you manage Clojupyter kernel using 'kernel identifiers' which are enforced to be
unique on the machine.  The identifiers are names which must consist of only latin letters, digits,
dashes (\-), underscores (\_), and full stops (\.).  You use kernel identifiers when you list,
install, and remove Clojupyter kernels.

#### Installation jarfile

A Clojupyter install invariably needs a standalone jarfile containing Clojupyter.  The `install`
allows you control which jarfile is used, but - since you are almost always installing from a
project repository - you do not have to specify the jarfile: If the file tree of the current
directory contains exactly one standalone jarfile, that will be used as default.  If none such file
is found, or if more than one is found an error will be raised.  

The output of the `install` always shows you which jarfile was installed.

#### Installation location

Jupyter supports two types of installation locations: *shared* and *current user only* (non-shared),
the exact location is platform dependent.  The default installation location is *current user only*:
Most of the time the installation location is of little significance since most computers these days
have only a single user.

**NOTE** Conda-installations work somewhat differently because they depend on Conda to manage the
location environment.  To keep things simple, a Clojupyter conda install is always done into the
shared, conda-managed environment with *default kernel identifier*.  This means that only a single
Clojupyter kernel can be installed at a time *in any one Conda environment*.  However, since Conda
support environment management, including creation, creation and switching between multiple
coexisting environment, you can effectively have multiple conda-installed Clojupyter kernels, but
only one in each distinct conda environment.

#### Default install

If you use `install` without any options you get a default install: Identifier based on the version
string, installed in the Jupyter kernel directory under the user home directory (exactly where is
platform dependent).

#### Example

```
> clj -M -m clojupyter.cmdline install --ident mykernel -h
Clojupyter v0.2.3 - Install Clojupyter

    Installed jar:      ~/lab/clojure/clojupyter/target/clojupyter-0.2.3-SNAPSHOT-standalone.jar
    Install directory:  /usr/local/share/jupyter/kernels/mykernel
    Kernel identifier:  mykernel

    Installation successful.

exit(0)
>
```

### `list-commands`

The `list-commands` command simply lists the commands available.

Example: 
```
> clj -M -m clojupyter.cmdline list-commands
Clojupyter v0.2.3 - List commands

    Clojupyter commands:

       - help
       - install
       - list-commands
       - list-installs
       - list-installs-matching
       - remove-installs-matching
       - remove-install
       - version

    You can invoke Clojupyter commands like this:

       clj -m clojupyter.cmdline <command>

    or, if you have set up lein configuration, like this:

       lein clojupyter <command>

    See documentation for details.

exit(0)
>
```

### `list-installs`

The `list-installs` command list the Clojupyter kernels installed on the current machine.  For each
install, the kernel identifier and installation location is show.

Example:
```
> clj -M -m clojupyter.cmdline list-installs
Clojupyter v0.2.3 - All Clojupyter kernels

    |    IDENT |                                DIR |
    |----------+------------------------------------|
    |      abc |      ~/Library/Jupyter/kernels/abc |
    | mykernel | ~/Library/Jupyter/kernels/mykernel |
    |   test-1 |   ~/Library/Jupyter/kernels/test-1 |
    |   test-2 |   ~/Library/Jupyter/kernels/test-2 |
    |   test-3 |   ~/Library/Jupyter/kernels/test-3 |

exit(0)
>
```

### `list-installs-matching`

The `list-installs-matching` command works similarly to the `list-installs` command, except it take
a single argument representing a regular expression to match aganst installed kernels' identifiers
to select which kernels to include in the listing.

Example:

```
> clj -M -m clojupyter.cmdline list-installs-matching test
Clojupyter v0.2.3 - Clojupyter kernels matching the regular expression 'test'.

    |  IDENT |                              DIR |
    |--------+----------------------------------|
    | test-1 | ~/Library/Jupyter/kernels/test-1 |
    | test-2 | ~/Library/Jupyter/kernels/test-2 |
    | test-3 | ~/Library/Jupyter/kernels/test-3 |

exit(0)
>
```

### `remove-install`

The `remove-install` command removed a specific Clojupyter kernel.  The command takes a single,
mandatory argument: the identifier of the Clojupyter kernel to be removed.

Note: Kernel removal only affect Clojupyter kernels; non-Clojupyter Jupyter kernels installed on
your machine are not affected.

Example:

```
> clj -M -m clojupyter.cmdline remove-install test-2
Clojupyter v0.2.3 - Remove kernel 'test-2'

    Step: Delete ~/Library/Jupyter/kernels/test-2

    Status: Removals successfully completed.

exit(0)
>
```

### `remove-installs-matching`

The `remove-installs-matching` command works similarly to `remove-install`, except it removes a set
of Clojupyter kernels, namely those whose kernel identifies matches the single, mandatory regular
expression argument.

Example:

```
> clj -M -m clojupyter.cmdline remove-installs-matching test
Clojupyter v0.2.3 - Remove installs

    Step: Delete ~/Library/Jupyter/kernels/test-2
    Step: Delete ~/Library/Jupyter/kernels/test-1
    Step: Delete ~/Library/Jupyter/kernels/test-3

    Status: Removals successfully completed.

exit(0)
>
```

### `version`

Unsurprisingly the `version` command provides version information about the Clojupyter kernel.

Example:

```
> clj -M -m clojupyter.cmdline version
Clojupyter v0.2.3 - Version

         #:version{:major 0,
                   :minor 2,
                   :incremental 3,
                   :qualifier \"SNAPSHOT\",
                   :lein-v-raw \"cd18-DIRTY\"}

exit(0)
>
```

## Development-only commands

Some commands exist solely to support development and deployment of Clojupyter, cursory
documentation for some of them can be found below.  The command `list-dvl-commands` lists
all development-only commands.

### `conda-build`

The `conda-build` automates the construction of Clojupyter packages for deployment to [Anaconda
Cloud](https://anaconda.org) which enable Clojupyter users to get started using Clojupyter simply by
installing Anaconda (the recommended way to install Jupyter) and then doing `conda install` - see
[Conda-Installing Clojupyter](conda-installing.md) for more information about installing Clojupyter
from Anaconda Cloud.

Clojupyter conda distribution comprises 3 platforms: Linux, MacOs, and Windows, all in 64-bit
editions.  A 

### `conda-link`

The command `conda-link` is used by the conda package managment system and is not intended for
direct use by users, but is exclusively called by the conda package management system to install
Clojupyter on the end-user machine.  See function docstring for details.

### `conda-unlink`

The command `conda-unlink` is used by the conda package managment system and is not intended for
direct use by users, but is exclusively called by the conda package management system to remove
Clojupyter from the end-user machine.  See function docstring for details.

### `list-dvl-commands`

The `list-dvl-commands` command list available commands.  This is should always be complete, whereas
this document not necessarily covers every command available.

### `supported-os?`

See function docstring.
