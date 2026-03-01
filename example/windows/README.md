# Windows Examples — How to Compile

All examples link against the `neqsim.dll` shared library built from the `java_graal/` directory. Adjust the paths below to match your checkout location.

## Prerequisites

- **Visual Studio Build Tools** (or full Visual Studio) with the **"Desktop development with C++"** workload
- The native DLL must already be built (see [java_graal/README.md](../../java_graal/README.md))

Open a **Developer Command Prompt** (or run `vcvarsall.bat x64`) before compiling.

```cmd
set TARGET=..\..\java_graal\target
```

## Water Dew Point — C Example (`test_native.c`)

Calls `calcWaterDewPoint` and `calcWaterInGas` (Java models, always available).

```cmd
cl test_native.c /I%TARGET% /Fe:%TARGET%\test_native.exe /link /LIBPATH:%TARGET% neqsim.lib
%TARGET%\test_native.exe
```

## Python Dew Point — C++ Example (`py_dewpoint_calc.cpp`)

> **Note:** Requires a **with-python** build (`-Pnative-windows,with-python`). Default builds will return `quality=0`.

The `py_dewpoint_calc.cpp` file is in the `example/linux/` directory (it works on both Linux and Windows).

```cmd
cl /EHsc ..\..\example\linux\py_dewpoint_calc.cpp /I%TARGET% /Fe:%TARGET%\py_dewpoint_calc.exe /link /LIBPATH:%TARGET% neqsim.lib
%TARGET%\py_dewpoint_calc.exe
```

## Runtime

Make sure `neqsim.dll` (and any companion DLLs produced by the build, such as `java.dll`, `jvm.dll`, etc.) are on the `PATH` or in the same directory as the executable.
