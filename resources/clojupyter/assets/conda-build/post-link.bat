@echo off
set CJP_KERNEL_DIR=%PREFIX%\share\jupyter\kernels\conda-clojupyter
set CJP_JARFILE=%CJP_KERNEL_DIR%\clojupyter-standalone.jar
set CJP_CMD=conda-link
java -cp %CJP_JARFILE% clojupyter.cmdline %CJP_CMD% --prefix=%PREFIX% --jarfile=%CJP_JARFILE%
