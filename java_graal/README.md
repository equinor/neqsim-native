![NeqSim Logo](https://github.com/equinor/neqsim/blob/master/docs/wiki/neqsimlogocircleflatsmall.png)
# NeqSim Native
This project compiles NeqSim simulation models into a native executable or shared library using GraalVM that can be used directly or integrated into eg. C/C++ programs. An example of use is implementation of NeqSim models in process control systems.

Process models can be written in **Java** or **Python**. Both approaches produce the same kind of native shared library — the caller (C/C++, MATLAB, etc.) cannot tell which language was used. The default build includes Java models only; Python support is available as an opt-in build option.

Learn and ask questions in [Discussions for use and development of NeqSim](https://github.com/equinor/neqsim/discussions).

## Getting Started
1. Install the [GraalVM JDK 25+](https://www.graalvm.org/). On Linux use: sdk install java 25-graal
2. Review the [GraalVM Getting Started Guide](https://www.graalvm.org/latest/docs/getting-started/)
3. Review [native compilation documentation](https://www.graalvm.org/latest/reference-manual/native-image/) with GraalVM

The project is built into native code (e.g., shared libraries or executables) using the Maven build system. All dependencies are specified in the `pom.xml` file and resolved from Maven Central.

## Commands

To compile the project to native code (on Windows use mvnw.cmd):

### Default builds (no Python — recommended)
```bash
./mvnw -Pnative-linux-lean package               # Linux
mvnw.cmd -Pnative-windows-lean package           # Windows
./mvnw -Pnative-macos-lean package               # macOS
```

### With Python support (optional, larger and slower)
```bash
./mvnw -Pnative-linux,with-python package        # Linux
mvnw.cmd -Pnative-windows,with-python package    # Windows
./mvnw -Pnative-macos,with-python package        # macOS
```

### 32-bit server EXE (for 32-bit stub support — Windows only)
```bash
mvnw.cmd -Pnative-server-windows package         # builds neqsim_server.exe
```

> The server EXE is used together with the 32-bit stub DLL in `stub32/`.
> See [stub32/README.md](../stub32/README.md) for details.

| Variant | Profiles | Size | Build time | Python models |
|---------|----------|------|------------|---------------|
| **Default** | `native-<os>-lean` | ~100 MB | ~2 min | No (`PY_*` return `quality=0`) |
| **With Python** | `native-<os>,with-python` | ~330 MB | ~40 min | Yes (`PY_*` functions work) |

> **Note:** Sizes above are before compression. CI release builds are compressed with [UPX](https://upx.github.io/) (`--best --lzma`), reducing Linux/Windows binaries by ~50%. macOS builds are not compressed (UPX does not support `.dylib`). To compress a local build: `upx --best --lzma target/neqsim.dll` (or `.so`).

A shared library is created by activating this in the `pom.xml` file:

```xml
<sharedLibrary>true</sharedLibrary>
```

The shared library or executable will be placed in the `target` directory.

See [documentation](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-native-shared-library/).
The shared libraries and header files can be integrated into third party C/C++ programs.

## Writing Process Models

### Approach 1: Java (existing models)

Java models use `@CEntryPoint` directly on the model class. For a detailed step-by-step guide on adding new Java models, see **[doc/java-processes.md](../doc/java-processes.md)**. See the existing `WaterDewPoint.java` for a complete example.

### Approach 2: Python via GraalPy (new)

Process models can also be written in Python. GraalPy (Python 3.12 on the GraalVM Truffle framework) runs Python on the **same VM** as neqsim — no JVM bridge, no subprocess, no serialisation overhead.

The architecture has two layers:

1. **Python script** (`src/main/resources/python/dewpoint_process.py`):
   ```python
   import java
   SystemSrkEos = java.type("neqsim.thermo.system.SystemSrkEos")
   ThermodynamicOperations = java.type(
       "neqsim.thermodynamicoperations.ThermodynamicOperations")

   def calculate_dewpoint(temperature, pressure, gas_flow_rate):
       fluid = SystemSrkEos(temperature, pressure)
       fluid.addComponent("methane", 0.85, "mol/sec")
       # ... add more components, run flash, return results
       return [dew_point_temp, gas_density]
   ```

2. **Java wrapper** (`PythonDewPointProcess.java`) — a thin class that loads the Python script via the Polyglot API and exposes `@CEntryPoint`:
   ```java
   @CEntryPoint(name = "PY_run_dewpoint_calculation")
   static void PY_run_dewpoint_calculation(IsolateThread thread,
           double temperature, double pressure, double gas_flow_rate,
           CDoublePointer dew_point_temperature, CDoublePointer gas_density,
           CIntPointer quality) {
       // Create GraalPy context, eval script, call function, write results
   }
   ```

Both approaches compile to the **same shared library** — the DLL/`.so`/`.dylib` exports functions from Java and Python models side by side.

### Key Differences

| | Java approach | Python approach |
|---|---|---|
| **Model language** | Java | Python (via GraalPy) |
| **neqsim access** | Direct Java API | `java.type()` interop |
| **`@CEntryPoint`** | On the model itself | On a thin Java wrapper |
| **Build variant** | Both default and with-python | With-python only (`with-python` profile) |
| **Performance** | Native speed | Near-native (Truffle JIT) |
| **Dependencies** | `svm` + `polyglot` | + `python` runtime |

## Example

In the [example folder](https://github.com/equinor/neqsim-native/tree/main/example), we demonstrate how the shared library can be used on both Windows and Linux/Unix systems.

## Adding a New Python Model

For a detailed step-by-step guide on adding new Python process models, see **[doc/python-processes.md](../doc/python-processes.md)**.



