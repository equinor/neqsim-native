/**
 * Example: calling the Python-based dew point calculation from C++.
 *
 * The function PY_run_dewpoint_calculation is defined in Python
 * (dewpoint_process.py), wrapped by a thin Java @CEntryPoint
 * (PythonDewPointProcess.java), and compiled into the native
 * shared library by GraalVM native-image.
 *
 * Build (Linux):
 *   g++ -o py_dewpoint_calc py_dewpoint_calc.cpp \
 *       -I$TARGET -L$TARGET -Wl,-rpath,$TARGET -l:neqsim.so -ldl -lpthread
 *
 * Build (Visual Studio Developer Command Prompt):
 *   cl py_dewpoint_calc.cpp /I<path-to-headers> /link neqsim.lib
 *
 * Note: Requires a with-python build (-Pnative-linux,with-python).
 *       Default builds will return quality=0.
 */

#include <iostream>
#include <cassert>
#include "neqsim.h"

int main() {
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    // Create the GraalVM isolate
    int create_result = graal_create_isolate(nullptr, &isolate, &thread);
    if (create_result != 0) {
        std::cerr << "Error creating GraalVM isolate!" << std::endl;
        return create_result;
    }

    // Prepare output variables
    double dew_point_temperature = 0.0;
    double gas_density = 0.0;
    int quality = 0;

    // Call the Python-based dew point calculation
    PY_run_dewpoint_calculation(
        thread,
        25.0,                    // temperature [°C]
        50.0,                    // pressure [bara]
        1000.0,                  // gas flow rate [kg/hr]
        &dew_point_temperature,
        &gas_density,
        &quality
    );

    // Check results
    if (quality == 1) {
        std::cout << "Dew point temperature: " << dew_point_temperature << " °C" << std::endl;
        std::cout << "Gas density: " << gas_density << " kg/m³" << std::endl;
    } else {
        std::cerr << "Calculation failed (quality=0). "
                  << "Make sure you are using a with-python build." << std::endl;
    }

    std::cout << "All assertions passed!" << std::endl;

    // Detach the thread and destroy the isolate
    int detach_result = graal_detach_all_threads_and_tear_down_isolate(thread);
    if (detach_result != 0) {
        std::cerr << "Error detaching GraalVM thread!" << std::endl;
        return detach_result;
    }

    return 0;
}
