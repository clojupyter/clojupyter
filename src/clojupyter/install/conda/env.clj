(ns clojupyter.install.conda.env
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [io.simplect.compose :refer [C]]))

(defn- mkcomment
  [platforms comment]
  (let [platforms (if (= platforms :all) [:macos :linux :windows] platforms)]
    (str/join \newline [comment
                        nil
                        (str "  Platforms: " (str/join ", " (map (C name str/upper-case) platforms)))])))

(defmacro ^:private def-conda-var
  [nm platforms comment]
  (let [comment (mkcomment platforms comment)]
    `(do
       (defn ~nm
         ~comment
         []
         (System/getenv ~(name nm)))
       (defn ~(symbol (str nm "!"))
         ~(str comment "\n  Throws an Exception if not found.")
         []
         (or (System/getenv ~(name nm))
             (throw (Exception. ~(str "CONDA environment variable undefined: " nm))))))))

;;; ----------------------------------------------------------------------------------------------------
;;; CONDA ENVIRONMENT VARIABLES
;;; ----------------------------------------------------------------------------------------------------

(def-conda-var ARCH :all
  "Either 32 or 64, to specify whether the build is 32-bit or
  64-bit. The value depends on the ARCH environment variable and
  defaults to the architecture the interpreter running conda was
  compiled with.")

(def-conda-var PREFIX :all
  "Build prefix to which the build script should install.")

(def-conda-var DIRTY :all
  "Set to 1 if the `--dirty` flag is passed to the conda-build
  command. May be used to skip parts of a build script conditionally
  for faster iteration time when developing recipes. For example,
  downloads, extraction and other things that need not be repeated.")

(def-conda-var PKG_BUILDNUM :all
  "Build number of the package being built.")

(def-conda-var PKG_NAME :all
  "Name of the package being built.")

(def-conda-var PKG_VERSION :all
  "Version of the package being built.")

(def-conda-var PKG_BUILD_STRING :all
  "Complete build string of the package being built, including
  hash. EXAMPLE: py27h21422ab_0 . Conda-build 3.0+.")

(def-conda-var PKG_HASH :all
  "Hash of the package being built, without leading h. EXAMPLE:
  21422ab . Conda-build 3.0+.")

(def-conda-var RECIPE_DIR :all
  "Directory of the recipe.")

(def-conda-var SRC_DIR :all
  "Path to where source is unpacked or cloned. If the source file is
  not a recognized file type (`zip`, `tar`, `tar.bz2`, or `tar.xz`)
  this is a directory containing a copy of the source file.")

;;; ----------------------------------------------------------------------------------------------------
;;; WINDOWS-ONLY
;;; ----------------------------------------------------------------------------------------------------

(def-conda-var CYGWIN_PREFIX [:windows]
  "Same as `PREFIX`, but as a Unix-style path, such as `/cygdrive/c/path/to/prefix`.")

(def-conda-var LIBRARY_BIN [:windows]
  "`<build prefix>\\Library\\bin`")

(def-conda-var LIBRARY_INC [:windows]
  "`<build prefix>\\Library\\include`")

(def-conda-var LIBRARY_LIB [:windows]
  "`<build prefix>\\Library\\lib`")

(def-conda-var LIBRARY_PREFIX [:windows]
  "`<build prefix>\\Library`")

(def-conda-var SCRIPTS [:windows]
  "`<build prefix>\\Scripts`")

(def-conda-var VS_MAJOR [:windows]
  "`The major version number of the Visual Studio version activated
  within the build, such as 9.`")

(def-conda-var VS_VERSION [:windows]
  "`The version number of the Visual Studio version activated within
  the build, such as 9.0.`")

(def-conda-var VS_YEAR [:windows]
  "The release year of the Visual Studio version activated within the
  build, such as 2008.")

;;; ----------------------------------------------------------------------------------------------------
;;; MacOS + Linux
;;; ----------------------------------------------------------------------------------------------------

(def-conda-var HOME [:macos :linux]
  "Standard `$HOME` environment variable.")

(def-conda-var PKG_CONFIG_PATH	[:macos :linux]
  "Path to pkgconfig directory.")

;;; ----------------------------------------------------------------------------------------------------
;;; MacOS-only
;;; ----------------------------------------------------------------------------------------------------

(def-conda-var CFLAGS [:macos]
  "`-arch` flag.")

(def-conda-var CXXFLAGS [:macos]
  "Same as `CFLAGS`.")

(def-conda-var LDFLAGS [:macos]
  "Same as `CFLAGS`.")

(def-conda-var MACOSX_DEPLOYMENT_TARGET [:macos]
  "Same as the Anaconda Python macOS deployment target. Currently
  10.6.")

(def-conda-var OSX_ARCH [:macos]
  "`i386` or `x86_64`, depending on Python build.")

;;; ----------------------------------------------------------------------------------------------------
;;; Linux-only
;;; ----------------------------------------------------------------------------------------------------

(def-conda-var LD_RUN_PATH [:linux]
  "`<build prefix>/lib`")

;;; ----------------------------------------------------------------------------------------------------
;;; Other
;;; ----------------------------------------------------------------------------------------------------

(def-conda-var GIT_BUILD_STR :all
  "String that joins `GIT_DESCRIBE_NUMBER` and `GIT_DESCRIBE_HASH` by an
  underscore.")

(def-conda-var GIT_DESCRIBE_HASH :all
  "The current commit short-hash as displayed from git describe
  `--tags`.")

(def-conda-var GIT_DESCRIBE_NUMBER :all
  "String denoting the number of commits since the most recent tag.")

(def-conda-var GIT_DESCRIBE_TAG :all
  "String denoting the most recent tag from the current commit, based
  on the output of git describe `--tags`.")

(def-conda-var GIT_FULL_HASH :all
  "String with the full SHA1 of the current `HEAD`.")

(def-conda-var HG_BRANCH :all
  "String denoting the presently active branch.")

(def-conda-var HG_BUILD_STR :all
  "String that joins HG_NUM_ID and HG_SHORT_ID by an underscore.")

(def-conda-var HG_LATEST_TAG :all
  "String denoting the most recent tag from the current commit.")

(def-conda-var HG_LATEST_TAG_DISTANCE :all
  "String denoting number of commits since the most recent tag.")

(def-conda-var HG_NUM_ID :all
  "String denoting the revision number.")

(def-conda-var HG_SHORT_ID :all
  "String denoting the hash of the commit.")

(def-conda-var CONDA_PY :all
  "The Python version used to build the package. Should be 27, 34, 35
  or 36.")

(def-conda-var CONDA_NPY :all
  "The NumPy version used to build the package, such as 19, 110 or 111.")

(def-conda-var CONDA_PREFIX :all
  "The path to the conda environment used to build the package, such
  as `/path/to/conda/env`. Useful to pass as the environment prefix
  parameter to various conda tools, usually labeled `-p` or `--prefix`.")

(def-conda-var FEATURE_NOMKL :all
  "Adds the `nomkl` feature to the built package.  Accepts 0 for off and
  1 for on.")

(def-conda-var FEATURE_DEBUG :all
  "Adds the debug feature to the built package. Accepts 0 for off and 1 for on.")

(def-conda-var FEATURE_OPT :all
  "Adds the opt feature to the built package. Accepts 0 for off and 1 for on.")

;;; ----------------------------------------------------------------------------------------------------
;;; AUXILIARY FUNCTIONS
;;; ----------------------------------------------------------------------------------------------------

(defn pkg-name-ver-num!
  "Returns the string consisting of `PKG_NAME`, `PKG_VERSION` and
  `PKG_BUILDNUM` joined by '-' characters."
  []
  (str (PKG_NAME!) "-" (PKG_VERSION!) "-" (PKG_BUILDNUM!)))

(defn pkg-dir!
  "Returns a file object referring to the package directory in the
  `pkgs` directory in `PREFIX`."
  []
  (io/file (str (PREFIX!) "/pkgs/" (pkg-name-ver-num!))))
