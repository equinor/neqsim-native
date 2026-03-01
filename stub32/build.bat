@echo off
REM =====================================================================
REM  build.bat — Build the 32-bit NeqSim stub DLL and 64-bit server EXE
REM
REM  Prerequisites:
REM    - Visual Studio Build Tools 2022 with C++ desktop workload
REM    - GraalVM JDK (with native-image) on PATH / JAVA_HOME
REM
REM  Usage:
REM    build.bat           Build both server EXE and 32-bit stub DLL
REM    build.bat stub      Build only the 32-bit stub DLL
REM    build.bat server    Build only the 64-bit server EXE
REM =====================================================================
setlocal enabledelayedexpansion

set ROOT=%~dp0..
set STUB_DIR=%~dp0
set GRAAL_DIR=%ROOT%\java_graal
set TARGET=%STUB_DIR%dist

REM -- Determine what to build ----------------------------------------
set BUILD_SERVER=1
set BUILD_STUB=1
if /i "%1"=="stub"   set BUILD_SERVER=0
if /i "%1"=="server" set BUILD_STUB=0

REM -- Create output directory ----------------------------------------
if not exist "%TARGET%" mkdir "%TARGET%"

REM ===================================================================
REM  1) Build the 64-bit server EXE via Maven native-image
REM ===================================================================
if %BUILD_SERVER%==1 (
    echo.
    echo ====================================================================
    echo  Building 64-bit neqsim_server.exe  [GraalVM native-image]
    echo ====================================================================
    echo.

    pushd "%GRAAL_DIR%"
    call mvnw.cmd -B package -Pnative-server-windows --file pom.xml -ntp
    if errorlevel 1 (
        echo ERROR: Server EXE build failed.
        popd
        exit /b 1
    )
    popd

    REM Copy server EXE to dist
    copy /y "%GRAAL_DIR%\target\neqsim_server.exe" "%TARGET%\" >nul 2>&1

    echo.
    echo  Server EXE built: %TARGET%\neqsim_server.exe
)

REM ===================================================================
REM  2) Build the 32-bit stub DLL
REM ===================================================================
if %BUILD_STUB%==1 (
    echo.
    echo ====================================================================
    echo  Building 32-bit neqsim.dll  [C stub]
    echo ====================================================================
    echo.

    REM Set up 32-bit MSVC environment (x86)
    call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" x86

    pushd "%STUB_DIR%"
    cl /nologo /W4 /O2 /LD /D NEQSIM_STUB_EXPORTS ^
       neqsim_stub.c ws2_32.lib ^
       /Fe:"%TARGET%\neqsim.dll" ^
       /Fo:"%TARGET%\neqsim_stub.obj" ^
       /link /DEF:neqsim_stub.def /IMPLIB:"%TARGET%\neqsim.lib"
    if errorlevel 1 (
        echo ERROR: Stub DLL build failed.
        popd
        exit /b 1
    )
    popd

    REM Copy header
    copy /y "%STUB_DIR%\neqsim_stub.h" "%TARGET%\neqsim.h" >nul 2>&1

    echo.
    echo  Stub DLL built:
    echo    %TARGET%\neqsim.dll   (32-bit)
    echo    %TARGET%\neqsim.lib   (import library)
    echo    %TARGET%\neqsim.h     (header)
)

echo.
echo ====================================================================
echo  Build complete.  Output in: %TARGET%
echo.
echo  To use:
echo    1. Place neqsim_server.exe and neqsim.dll in the same directory
echo    2. Your 32-bit application links against neqsim.lib / includes neqsim.h
echo    3. The stub DLL auto-launches the server on first call
echo ====================================================================

endlocal
