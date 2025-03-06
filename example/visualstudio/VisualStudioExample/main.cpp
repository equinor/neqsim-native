#include <iostream>
#include <cstdlib>   // For std::exit
#include <stdexcept> // For std::invalid_argument, std::out_of_range
#include <string>    // For std::string
#include "neqsim.h"  // Local header file (ensure it is in your include path)

using std::cout;
using std::cerr;
using std::endl;
using std::string;

// Forward declaration of the process routine.
// (The function implementation should be in a corresponding source file or linked library.)
double calcWaterDewPoint(graal_isolatethread_t* thread, double param1, double param2);

double calcWaterInGas(graal_isolatethread_t* thread, double param1, double param2);

int main(int argc, char* argv[]) {
    // Initialize GraalVM isolate and thread pointers.
    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;

    // Create the GraalVM isolate and thread.
    int create_result = graal_create_isolate(nullptr, &isolate, &thread);
    if (create_result != 0) {
        cerr << "Error creating GraalVM isolate!" << endl;
        return create_result;
    }

    // Call the process function.
    //double waterdp = calcWaterInGas(
    //        thread, -20.0, 70.0);
    double results = calcWaterDewPoint(thread, 20.0, 70.0);

    // Display the results.
    cout << "-------------------------------------" << endl;
    //cout << "waterdp = " << waterdp << endl;


    return 0;
}
