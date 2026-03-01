# Adding Python Process Models

This guide explains how to write neqsim process models in Python and expose them as C-callable functions in the native shared library (DLL / `.so` / `.dylib`). Python models sit alongside Java models in the same binary — callers cannot tell which language was used.

> **Note:** Python support is not included in the default build. To include Python models, add the `with-python` profile to your build command (e.g. `mvnw.cmd -Pnative-windows,with-python package`). See [java_graal/README.md](../java_graal/README.md) for details.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [How neqsim Is Accessed from Python](#how-neqsim-is-accessed-from-python)
- [Step-by-Step: Adding a New Python Process](#step-by-step-adding-a-new-python-process)
  - [Step 1 — Write the Python Script](#step-1--write-the-python-script)
  - [Step 2 — Create the Java Wrapper](#step-2--create-the-java-wrapper)
  - [Step 3 — Add a Unit Test](#step-3--add-a-unit-test)
  - [Step 4 — Add a C++ Example (Optional)](#step-4--add-a-c-example-optional)
  - [Step 5 — Document the API](#step-5--document-the-api)
- [How the DLL Is Built](#how-the-dll-is-built)
- [How the DLL Is Called from C/C++](#how-the-dll-is-called-from-cc)
- [File Reference](#file-reference)
- [Tips and Troubleshooting](#tips-and-troubleshooting)

---

## Overview

```
┌────────────────────────────────────────────────────────────┐
│  Python script  (.py)                                      │
│  ● Uses neqsim Java classes via java.type()                │
│  ● Pure process-model logic — no boilerplate               │
├────────────────────────────────────────────────────────────┤
│  PythonProcessRunner.java  (shared, never changes)         │
│  ● Loads .py from classpath, creates GraalPy context       │
│  ● Calls the named Python function, returns double[]       │
├────────────────────────────────────────────────────────────┤
│  YourProcess.java  (thin wrapper — ~40 lines per model)    │
│  ● @CEntryPoint → exports function in the DLL              │
│  ● Calls PythonProcessRunner.run(...)                      │
├────────────────────────────────────────────────────────────┤
│  native-image --shared  (GraalVM 25)                       │
│  ● Compiles Java + Python into one .dll / .so / .dylib     │
└────────────────────────────────────────────────────────────┘
```

## Architecture

The project uses **GraalVM 25** with two key technologies:

| Technology | Role |
|---|---|
| **GraalVM `native-image`** | Compiles Java + Python into a single native shared library (no JVM required at runtime) |
| **GraalPy** | Python 3.12 implementation running on the same VM as Java (Truffle framework) |

**Why a Java wrapper is required:** GraalVM's `native-image` requires `@CEntryPoint` annotations on static Java methods to export functions in the shared library. Python cannot define these annotations directly. The wrapper is intentionally thin — typically ~40 lines — because all the GraalPy boilerplate is in the shared `PythonProcessRunner` class.

## How neqsim Is Accessed from Python

GraalPy provides built-in Java interop via the `java` module. This is **not** JPype or any bridge — it runs on the same VM:

```python
import java

# Access any Java class by its fully qualified name
SystemSrkEos = java.type("neqsim.thermo.system.SystemSrkEos")
ThermodynamicOperations = java.type(
    "neqsim.thermodynamicoperations.ThermodynamicOperations"
)

# Use them exactly like in Java
fluid = SystemSrkEos(25.0, 50.0)
fluid.addComponent("methane", 0.85, "mol/sec")
fluid.setMixingRule(2)

ops = ThermodynamicOperations(fluid)
ops.TPflash()

density = fluid.getPhase("gas").getDensity("kg/m3")
```

---

## Step-by-Step: Adding a New Python Process

### Step 1 — Write the Python Script

Place your script in `java_graal/src/main/resources/python/`:

```
java_graal/src/main/resources/python/my_flash_model.py
```

Example:

```python
import java

SystemSrkEos = java.type("neqsim.thermo.system.SystemSrkEos")
ThermodynamicOperations = java.type(
    "neqsim.thermodynamicoperations.ThermodynamicOperations"
)

def run_flash(temperature, pressure, methane_frac, ethane_frac):
    """
    Run a TP flash and return [gas_density, liquid_density, gas_fraction].
    """
    fluid = SystemSrkEos(temperature, pressure)
    fluid.addComponent("methane", methane_frac, "mol/sec")
    fluid.addComponent("ethane", ethane_frac, "mol/sec")
    fluid.setMixingRule(2)
    fluid.setMultiPhaseCheck(True)

    ops = ThermodynamicOperations(fluid)
    ops.TPflash()

    gas_density = fluid.getPhase("gas").getDensity("kg/m3")
    liquid_density = (
        fluid.getPhase("oil").getDensity("kg/m3")
        if fluid.getNumberOfPhases() > 1
        else 0.0
    )
    gas_fraction = fluid.getPhase("gas").getBeta()

    return [gas_density, liquid_density, gas_fraction]
```

**Rules:**
- The function must return a **list of floats** (`double[]` in Java).
- Use `java.type()` to access neqsim classes.
- No need for boilerplate — `PythonProcessRunner` handles context creation.

### Step 2 — Create the Java Wrapper

Create a new Java class in `java_graal/src/main/java/neqsim/process/python/`:

```
java_graal/src/main/java/neqsim/process/python/PythonFlashProcess.java
```

The wrapper has two parts:
1. A public Java method (for tests)
2. A `@CEntryPoint` method (for the DLL export)

```java
package neqsim.process.python;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;

public class PythonFlashProcess {

    private static final String SCRIPT   = "my_flash_model.py";  // ← matches filename
    private static final String FUNCTION = "run_flash";           // ← matches Python function
    private static final Object ENTRYPOINT_LOCK = new Object();

    /** Callable from Java / tests. */
    public static double[] runFlash(double temperature, double pressure,
                                     double methaneFrac, double ethaneFrac) throws Exception {
        return PythonProcessRunner.run(SCRIPT, FUNCTION,
                temperature, pressure, methaneFrac, ethaneFrac);
    }

    /** Exported in the shared library as PY_run_flash. */
    @CEntryPoint(name = "PY_run_flash")
    public static void PY_run_flash(
            IsolateThread thread,
            double temperature,
            double pressure,
            double methane_frac,
            double ethane_frac,
            CDoublePointer gas_density,
            CDoublePointer liquid_density,
            CDoublePointer gas_fraction,
            CIntPointer quality) {
        synchronized (ENTRYPOINT_LOCK) {
            try {
                double[] results = runFlash(temperature, pressure,
                        methane_frac, ethane_frac);
                gas_density.write(results[0]);
                liquid_density.write(results[1]);
                gas_fraction.write(results[2]);
                quality.write(1);
            } catch (Exception e) {
                System.err.println("PY_run_flash error: " + e.getMessage());
                gas_density.write(-999.0);
                liquid_density.write(-999.0);
                gas_fraction.write(-999.0);
                quality.write(0);
            }
        }
    }
}
```

### Step 3 — Add a Unit Test

Create a test in `java_graal/src/test/java/neqsim/process/python/`:

```java
package neqsim.process.python;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.MockCDoublePointer;
import neqsim.MockCIntPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;

public class PythonFlashProcessTest {

    @Test
    @Tag("integration")
    public void testRunFlash() throws Exception {
        double[] results = PythonFlashProcess.runFlash(25.0, 50.0, 0.9, 0.1);

        Assertions.assertEquals(3, results.length);
        Assertions.assertTrue(results[0] > 0, "Gas density should be positive");
        Assertions.assertTrue(results[1] > 0, "Liquid density should be positive");
        Assertions.assertTrue(results[2] >= 0 && results[2] <= 1,
                "Gas fraction should be between 0 and 1");
    }

    @Test
    @Tag("integration")
    public void testCEntryPoint() {
        CDoublePointer gasDens = new MockCDoublePointer();
        CDoublePointer liqDens = new MockCDoublePointer();
        CDoublePointer gasFrac = new MockCDoublePointer();
        CIntPointer quality    = new MockCIntPointer();

        PythonFlashProcess.PY_run_flash(
                null, 25.0, 50.0, 0.9, 0.1,
                gasDens, liqDens, gasFrac, quality);

        Assertions.assertEquals(1, quality.read());
        Assertions.assertTrue(gasDens.read() > 0);
    }
}
```

Tests tagged `@Tag("integration")` are **excluded** from the default `mvn test` run (which uses Temurin JDK). They run when you use GraalVM JDK or pass `-Dsurefire.excludedGroups=` to include them.

### Step 4 — Add a C++ Example (Optional)

Create `example/linux/py_flash_calc.cpp`:

```cpp
#include <iostream>
#include <cassert>
#include "neqsim.h"

int main() {
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    int rc = graal_create_isolate(nullptr, &isolate, &thread);
    if (rc != 0) return rc;

    double gas_density = 0, liquid_density = 0, gas_fraction = 0;
    int quality = 0;

    PY_run_flash(thread,
        25.0, 50.0, 0.9, 0.1,
        &gas_density, &liquid_density, &gas_fraction, &quality);

    std::cout << "Gas density: " << gas_density << " kg/m3" << std::endl;
    std::cout << "Quality: " << quality << std::endl;

    assert(quality == 1);
    graal_tear_down_isolate(thread);
    return 0;
}
```

### Step 5 — Document the API

Add the function's input/output parameter table to `doc/README.md`.

---

## How the DLL Is Built

The Maven build compiles everything into a single shared library. Python models require the `with-python` profile to include the GraalPy runtime:

```bash
# Default builds (no Python — recommended for most users):
./mvnw -Pnative-linux-lean package               # Linux
mvnw.cmd -Pnative-windows-lean package           # Windows
./mvnw -Pnative-macos-lean package               # macOS

# With Python support (required for Python models):
./mvnw -Pnative-linux,with-python package        # Linux
mvnw.cmd -Pnative-windows,with-python package    # Windows
./mvnw -Pnative-macos,with-python package        # macOS
```

> **Important:** Default builds still export `PY_*` functions (the C header is the same), but calling them at runtime returns `quality=0` with the error message: *"Python runtime is not available. Build with the 'with-python' Maven profile to enable Python process models."*

What happens during a **with-python** build:

1. **Maven compiles Java** — all Java sources including `PythonProcessRunner.java` and your wrapper classes
2. **Maven packages resources** — Python scripts from `src/main/resources/python/` are included in the JAR
3. **`native-image --shared`** runs (via `native-maven-plugin`):
   - Scans for all `@CEntryPoint` methods (Java and Python wrappers alike)
   - Embeds the GraalPy runtime and your Python scripts
   - Produces: `neqsim.dll` (Windows), `neqsim.so` (Linux), or `neqsim.dylib` (macOS)
   - Also produces: `neqsim.h` (C header with all exported functions) and `neqsim.lib` (Windows import library)

**Output files** (in `java_graal/target/`):

| File | Description |
|---|---|
| `neqsim.dll` / `neqsim.so` / `neqsim.dylib` | The shared library containing all Java + Python models |
| `neqsim.h` | Auto-generated C header — includes all `@CEntryPoint` functions |
| `graal_isolate.h` | GraalVM isolate types (`graal_isolate_t`, `graal_isolatethread_t`) |
| `neqsim.lib` | Windows import library (for linking with MSVC) |

## How the DLL Is Called from C/C++

Every call follows the same 3-step pattern, regardless of whether the underlying model is Java or Python:

### 1. Initialize the GraalVM isolate (once)

```c
#include "neqsim.h"

graal_isolate_t* isolate = NULL;
graal_isolatethread_t* thread = NULL;
graal_create_isolate(NULL, &isolate, &thread);
```

### 2. Call the function

```c
double dew_point = 0, gas_density = 0;
int quality = 0;

PY_run_dewpoint_calculation(thread,
    25.0,           /* temperature [°C]  */
    50.0,           /* pressure [bara]   */
    1000.0,         /* gas flow [kg/hr]  */
    &dew_point,
    &gas_density,
    &quality);

if (quality == 1) {
    printf("Dew point: %.2f °C\n", dew_point);
    printf("Gas density: %.2f kg/m³\n", gas_density);
}
```

### 3. Clean up (when done)

```c
graal_tear_down_isolate(thread);
```

## File Reference

### Files you create (per Python model)

| File | Purpose |
|---|---|
| `java_graal/src/main/resources/python/<model>.py` | The Python process model |
| `java_graal/src/main/java/neqsim/process/python/Python<Name>.java` | Thin Java wrapper (~40 lines) |
| `java_graal/src/test/java/neqsim/process/python/Python<Name>Test.java` | Unit test |

### Files that never change

| File | Purpose |
|---|---|
| `java_graal/src/main/java/neqsim/process/python/PythonProcessRunner.java` | Shared utility — loads scripts, creates GraalPy context, calls functions |
| `java_graal/pom.xml` | Already has `polyglot` and `python` dependencies |

### Files to update only if 32-bit support is needed

| File | What to add |
|---|---|
| `stub32/neqsim_stub.h` | Function declaration with `NEQSIM_API` |
| `stub32/neqsim_stub.c` | `#define FUNC_...` + RPC forwarding function |
| `stub32/neqsim_stub.def` | Export name |
| `java_graal/.../NeqSimPipeServer.java` | Function ID constant + `case` + handler method |
| `java_graal/.../ProcessDispatcher.java` | Lock object + forwarding method |

## Tips and Troubleshooting

### Reflection configuration
If your Python script accesses neqsim classes that are not already in `src/main/resources/META-INF/native-image/reflect-config.json`, add them:

```json
{
    "name": "neqsim.thermo.system.SystemSrkEos",
    "allPublicConstructors": true,
    "allPublicMethods": true
}
```

### Testing
- Tests tagged `@Tag("integration")` require GraalVM JDK (they need the GraalPy runtime)
- Default `mvn test` skips them (uses Temurin JDK in CI)
- To run locally: install GraalVM 25+ and run `./mvnw test -Dsurefire.excludedGroups=`

### Binary size
Adding GraalPy increases the shared library size. If you need a smaller binary, you can disable the Python JIT compiler:
```
--engine.WarnInterpreterOnly=false -Dpolyglot.engine.WarnInterpreterOnly=false
```

### Key differences between Java and Python approaches

| | Java approach | Python approach |
|---|---|---|
| **Model language** | Java | Python (via GraalPy) |
| **neqsim access** | Direct Java API | `java.type()` interop |
| **`@CEntryPoint`** | On the model itself | On a thin Java wrapper |
| **Build variant** | Both default and with-python | With-python only (`with-python` profile) |
| **Performance** | Native speed | Near-native (Truffle JIT) |
| **Dependencies** | `svm` + `polyglot` | + `python` runtime |

### Platform support

| Platform | GraalPy support |
|---|---|
| Linux x86-64 | Tier 1 (fully supported) |
| macOS ARM64 | Tier 2 (supported) |
| Windows x86-64 | Tier 3 (experimental) |
