# Adding a Java Process Model — Detailed Guide

This guide walks through every file you need to create or modify when adding a **Java** process model to neqsim-native. The result is a function exported from the native shared library (`neqsim.dll` / `neqsim.so` / `neqsim.dylib`) that can be called directly from C/C++ or any language that supports C FFI.

For **Python** process models, see [python-processes.md](python-processes.md).

---

## Overview

```
           ┌──────────────────┐
           │  Your Java class │    @CEntryPoint annotation
           │  (process model) │ ─── exposes as C function ──►  neqsim.dll
           └──────────────────┘
                    │
                    │ calls
                    ▼
           ┌──────────────────┐
           │   neqsim Java    │    SRK/PR EoS, process
           │     library      │    equipment, flash calcs
           └──────────────────┘
```

When GraalVM's `native-image --shared` runs, it scans for every static method annotated with `@CEntryPoint` and generates an exported C function for it. The resulting shared library can be linked by any C/C++ application.

---

## Architecture — Key Concepts

### @CEntryPoint

Every function you want exported from the DLL must have `@CEntryPoint`:

```java
@CEntryPoint(name = "MY_function_name")
static void MY_function_name(IsolateThread thread,
        double input1, double input2,          // input parameters
        CDoublePointer output1, CIntPointer quality) {  // output pointers
    // implementation
}
```

Rules:
- Must be `static`
- First parameter is always `IsolateThread thread`
- Input parameters: `double`, `int`, `float`
- Output parameters: `CDoublePointer` or `CIntPointer` (written to by index)
- The last parameter is conventionally `CIntPointer quality` (1 = success, 0 = failure)

### Thread Safety

All `@CEntryPoint` methods must be thread-safe. The standard pattern is a static lock object:

```java
private static final Object ENTRYPOINT_LOCK = new Object();

@CEntryPoint(name = "MY_function")
static void MY_function(IsolateThread thread, /* ... */) {
    synchronized (ENTRYPOINT_LOCK) {
        // safe to use mutable static state here
    }
}
```

### Error Handling Pattern

All `@CEntryPoint` methods should follow this error handling pattern:

```java
try {
    // computation ...
    output.write(0, result);
    quality.write(0, 1);       // success
} catch (Exception e) {
    System.err.println("MY_function failed: " + e.getMessage());
    e.printStackTrace();
    output.write(0, -999.0);   // sentinel value
    quality.write(0, 0);       // failure
}
```

The caller should always check `quality` before using output values.

---

## Step-by-Step Guide

This example creates a hypothetical "Separator" model that runs a three-phase separator calculation and returns the oil, gas, and water flow rates.

### Step 1 — Create the Process Model Class

Create a new package and class:

```
java_graal/src/main/java/neqsim/process/separator/SeparatorProcess.java
```

```java
package neqsim.process.separator;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SeparatorProcess {

    /** Lock to prevent concurrent entry (shared mutable state). */
    private static final Object ENTRYPOINT_LOCK = new Object();

    /**
     * Run a three-phase separator calculation.
     *
     * @param thread       GraalVM isolate thread
     * @param temperature  Feed temperature [°C]
     * @param pressure     Separator pressure [bara]
     * @param hc_flow_rate Hydrocarbon feed flow rate [kg/hr]
     * @param water_cut    Water cut [fraction, 0–1]
     * @param oil_flow     Pointer to write oil flow rate [kg/hr]
     * @param gas_flow     Pointer to write gas flow rate [kg/hr]
     * @param water_flow   Pointer to write water flow rate [kg/hr]
     * @param quality      Pointer to write quality flag (1 = success, 0 = failure)
     */
    @CEntryPoint(name = "SEP_run_separator")
    static void SEP_run_separator(
            IsolateThread thread,
            double temperature,
            double pressure,
            double hc_flow_rate,
            double water_cut,
            CDoublePointer oil_flow,
            CDoublePointer gas_flow,
            CDoublePointer water_flow,
            CIntPointer quality) {

        synchronized (ENTRYPOINT_LOCK) {
            try {
                // 1. Create the fluid system
                SystemSrkEos fluid = new SystemSrkEos(temperature, pressure);
                fluid.addComponent("methane", 0.70, "mol/sec");
                fluid.addComponent("ethane", 0.10, "mol/sec");
                fluid.addComponent("propane", 0.05, "mol/sec");
                fluid.addComponent("n-heptane", 0.15, "mol/sec");
                fluid.setMixingRule(2);

                // 2. Run TP flash
                ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
                ops.TPflash();
                fluid.initProperties();

                // 3. Extract results
                double gasRate = fluid.getPhase("gas").getFlowRate("kg/hr");
                double oilRate = fluid.getPhase("oil").getFlowRate("kg/hr");
                double waterRate = hc_flow_rate * water_cut;

                // 4. Write outputs
                oil_flow.write(0, oilRate);
                gas_flow.write(0, gasRate);
                water_flow.write(0, waterRate);
                quality.write(0, 1);

            } catch (Exception e) {
                System.err.println("SEP_run_separator failed: " + e.getMessage());
                e.printStackTrace();
                oil_flow.write(0, -999.0);
                gas_flow.write(0, -999.0);
                water_flow.write(0, -999.0);
                quality.write(0, 0);
            }
        }
    }
}
```

**Key points:**
- The function name (`SEP_run_separator`) becomes the exported C symbol
- Use a prefix convention: e.g. `SEP_`, `CALC_`, etc.
- All results are written to `CDoublePointer` / `CIntPointer` parameters
- The `synchronized` block prevents concurrent state corruption
- The `catch` block writes sentinel values and sets `quality = 0`

### Step 2 — Add Unit Tests

Create a test class:

```
java_graal/src/test/java/neqsim/process/separator/SeparatorProcessTest.java
```

```java
package neqsim.process.separator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.MockCDoublePointer;
import neqsim.MockCIntPointer;

public class SeparatorProcessTest {

    @Test
    public void testRunSeparator() {
        MockCDoublePointer oilFlow = new MockCDoublePointer();
        MockCDoublePointer gasFlow = new MockCDoublePointer();
        MockCDoublePointer waterFlow = new MockCDoublePointer();
        MockCIntPointer quality = new MockCIntPointer();

        SeparatorProcess.SEP_run_separator(
                null,           // IsolateThread — null in test
                25.0,           // temperature [°C]
                50.0,           // pressure [bara]
                100000.0,       // hc_flow_rate [kg/hr]
                0.1,            // water_cut
                oilFlow, gasFlow, waterFlow, quality);

        Assertions.assertEquals(1, quality.read(), "Quality should be 1 (success)");
        Assertions.assertTrue(oilFlow.read() > 0, "Oil flow should be positive");
        Assertions.assertTrue(gasFlow.read() > 0, "Gas flow should be positive");
    }
}
```

**Note:** `MockCDoublePointer` and `MockCIntPointer` are test utilities in the project that implement the `CDoublePointer` and `CIntPointer` interfaces for use outside GraalVM native-image. The `IsolateThread` parameter is `null` in tests — it's only used at runtime inside the native image.

### Step 3 — Update Reflection Configuration (If Needed)

If your model uses neqsim classes that are loaded via reflection (e.g., equation of state classes), add them to:

```
java_graal/src/main/resources/META-INF/native-image/reflect-config.json
```

```json
{
    "name": "neqsim.thermo.system.SystemSrkEos",
    "allPublicConstructors": true,
    "allPublicMethods": true
}
```

Classes already configured include: `SystemPrEos1978`, `SystemSrkEos`, `SystemThermo`, `ThermodynamicOperations`, `PhasePrEos`, `PhaseSrkEos`, `PhaseEos`, `Phase`.

### Step 4 — Build and Verify

```bash
# Run tests first
cd java_graal
mvnw.cmd -B test --file pom.xml -ntp

# Build the shared library (Windows example)
mvnw.cmd -Pnative-windows-lean package

# Check that the symbol is exported
dumpbin /exports target/neqsim.dll | findstr SEP_
```

On Linux:
```bash
./mvnw -Pnative-linux-lean package
nm -D target/neqsim.so | grep SEP_
```

### Step 5 — Document the API

Add a section to [doc/README.md](README.md) with the input/output parameter tables:

```markdown
## `SEP_run_separator`

### Parameters

| Name | Description |
|------|-------------|
| `thread` | The isolate thread for the native entry point. |
| `temperature` | Feed temperature (°C). |
| `pressure` | Separator pressure (bara). |
| `hc_flow_rate` | Hydrocarbon feed flow rate (kg/hr). |
| `water_cut` | Water cut (fraction 0–1). |
| `oil_flow` | Pointer to write the oil flow rate (kg/hr). |
| `gas_flow` | Pointer to write the gas flow rate (kg/hr). |
| `water_flow` | Pointer to write the water flow rate (kg/hr). |
| `quality` | Pointer to write the quality flag (1 = success, 0 = failure). |
```

### Step 6 — Add a C++ Example (Optional)

Create `example/linux/separator_calc.cpp`:

```cpp
#include <iostream>
#include <cassert>
#include "neqsim.h"

int main() {
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    int rc = graal_create_isolate(nullptr, &isolate, &thread);
    if (rc != 0) {
        std::cerr << "Failed to create GraalVM isolate" << std::endl;
        return 1;
    }

    double oil_flow = 0, gas_flow = 0, water_flow = 0;
    int quality = 0;

    SEP_run_separator(thread,
        25.0,       // temperature [°C]
        50.0,       // pressure [bara]
        100000.0,   // hc_flow_rate [kg/hr]
        0.1,        // water_cut
        &oil_flow, &gas_flow, &water_flow, &quality);

    std::cout << "Quality: " << quality << std::endl;
    std::cout << "Oil:     " << oil_flow << " kg/hr" << std::endl;
    std::cout << "Gas:     " << gas_flow << " kg/hr" << std::endl;
    std::cout << "Water:   " << water_flow << " kg/hr" << std::endl;

    assert(quality == 1);
    graal_tear_down_isolate(thread);
    return 0;
}
```

---

## File Reference — Summary

### Files you create (per Java process model)

| File | Purpose |
|---|---|
| `java_graal/src/main/java/neqsim/process/<name>/<Name>Process.java` | The process model with `@CEntryPoint` |
| `java_graal/src/test/java/neqsim/process/<name>/<Name>ProcessTest.java` | Unit test |

### Files you may need to update

| File | What to add |
|---|---|
| `java_graal/src/main/resources/META-INF/native-image/reflect-config.json` | Reflection entries for neqsim classes used by the model |
| `doc/README.md` | API parameter docs |

---

## Existing Java Models — Reference

Study this working example for the complete pattern:

| Model | Main class | `@CEntryPoint` names |
|---|---|---|
| **Water Dew Point** | `neqsim.util.WaterDewPoint` | `calcWaterDewPoint`, `calcWaterInGas` |

---

## Tips

### Model complexity

- **Simple models** (few inputs, single flash): put everything in one class, pass all parameters directly
- **Complex models** (many inputs, multi-step process): consider a separate configuration class with default values

### Performance

- Models run as native code — no JVM startup, no GC pauses in steady state
- The `synchronized` block serialises concurrent calls to the same model. If you need parallelism, use per-call state instead of shared fields.

### Error handling

- Always write sentinel values (e.g., `-999.0`) and `quality = 0` on failure
- Print stack traces to stderr so they appear in logs but don't crash the caller
- The caller should always check `quality` before using output values

### Naming conventions

- Use a **prefix** for related functions: `SEP_`, `CALC_`, `PY_`, etc.
- Keep names descriptive: `SEP_run_separator`, not `SEP_run`
- Python wrapper functions use the `PY_` prefix convention
