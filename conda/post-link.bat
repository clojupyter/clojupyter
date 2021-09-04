@echo off

echo {"env": {"CLASSPATH": "%PREFIX:\=/%/Library/%PKG_NAME%-%PKG_VERSION%/%PKG_NAME%-%PKG_VERSION%.jar;%PREFIX:\=/%/Library/%PKG_NAME%-%PKG_VERSION%/plugins/enabled/*;${CLASSPATH}"}, > %PREFIX%\share\jupyter\kernels\%PKG_NAME%-%PKG_VERSION%\kernel.json
echo "argv": ["java", "clojupyter.kernel.core", "{connection_file}"], >> %PREFIX%\share\jupyter\kernels\%PKG_NAME%-%PKG_VERSION%\kernel.json
echo "display_name": "Clojure (%PKG_NAME%-%PKG_VERSION%)", >> %PREFIX%\share\jupyter\kernels\%PKG_NAME%-%PKG_VERSION%\kernel.json
echo "language": "clojure", >> %PREFIX%\share\jupyter\kernels\%PKG_NAME%-%PKG_VERSION%\kernel.json
echo "interrupt_mode": "message"} >> %PREFIX%\share\jupyter\kernels\%PKG_NAME%-%PKG_VERSION%\kernel.json
