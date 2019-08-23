@echo off
set MSGFILE=%PREFIX%\.messages.txt
set CJP_PKG_PATH=%CONDA_PREFIX%\pkgs\%PKG_NAME%-%PKG_VERSION%-%PKG_BUILDNUM%
set JARFILE=%CJP_PKG_PATH%\info\recipe\install-items\clojupyter-standalone.jar
echo "post-link.bat: JARFILE=%JARFILE%" >> %MSGFILE%
java -cp %JARFILE% clojupyter.cmdline conda-link "--prefix=%PREFIX%" "--jarfile=%JARFILE%" >> %MSGFILE% 2>&1
