MSGFILE=$PREFIX/.messages.txt
CJP_PKG_PATH="$CONDA_PREFIX/pkgs/$PKG_NAME-$PKG_VERSION-$PKG_BUILDNUM"
JARFILE="$CJP_PKG_PATH/info/recipe/install-items/clojupyter-standalone.jar"
java -cp "$JARFILE" clojupyter.cmdline conda-link --prefix="$PREFIX" --jarfile="$JARFILE" >> $MSGFILE
