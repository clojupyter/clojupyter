@echo off
set CJP_MSGFILE=%PREFIX%\.messages.txt
set CJP_SRCDIR=%RECIPE_DIR%\install-items
set CJP_KERNELDIR=%PREFIX%\share\jupyter\kernels\conda-clojupyter
set CJP_JARFILE=%CJP_SRCDIR%\clojupyter-standalone.jar
set CJP_PNGFILE=%CJP_SRCDIR%\logo-64x64.png

mkdir %CJP_KERNELDIR% >> %CJP_MSGFILE% 2>&1
if %errorlevel% neq 0 (
   echo Mkdir of kernel dir failed with exit code %errorlevel% >> %CJP_MSGFILE% 2>&1
   exit /b %errorlevel%
)

copy /B %CJP_JARFILE% %CJP_KERNELDIR% >> %CJP_MSGFILE% 2>&1
if %errorlevel% neq 0 (
   echo Jarfile copy failed with exit code %errorlevel% >> %CJP_MSGFILE% 2>&1
   exit /b %errorlevel%
)

copy /B %CJP_PNGFILE% %CJP_KERNELDIR% >> %CJP_MSGFILE% 2>&1
if %errorlevel% neq 0 (
   echo Logofile copy failed with exit code  %errorlevel% >> %CJP_MSGFILE% 2>&1
   exit /b %errorlevel%
)
