#include <iostream>
#include "neqsim.h"  // Include the generated header file for the shared library

int main() {
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    // Create the GraalVM isolate and isolate thread
    int create_result = graal_create_isolate(nullptr, &isolate, &thread);
    if (create_result != 0) {
        std::cerr << "Error creating GraalVM isolate!" << std::endl;
        return create_result;
    }

    // Call the calcWaterDewPoint function and store the result
    double dew_point = calcWaterDewPoint(
        thread,       // GraalVM isolate thread
        50.0,         // Example pressure (bar)
        100.0         // Example water content (ppm)
    );
    std::cout << "Result of calcWaterDewPoint function call: " << dew_point << " C" << std::endl;

    // Call the calcWaterInGas function and store the result
    double water_in_gas = calcWaterInGas(
        thread,       // GraalVM isolate thread
        50.0,         // Example pressure (bar)
        10.0          // Example temperature (C)
    );
    std::cout << "Result of calcWaterInGas function call: " << water_in_gas << " ppm" << std::endl;

    // Destroy the GraalVM isolate
    int destroy_result = graal_detach_all_threads_and_tear_down_isolate(thread);
    if (destroy_result != 0) {
        std::cerr << "Error destroying GraalVM isolate!" << std::endl;
        return destroy_result;
    }

    return 0;
}