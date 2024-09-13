include <iostream>
#include <cstring>  // For strcpy_s
#include "neqsim.h"  // Include the local header file
using namespace std;

int main() {
    // Define argc and dynamically allocate memory for argv
    int const argc = 1;
    char** argv = new char* [argc];  // Dynamically allocate an array of char pointers
    // Allocate memory for the first argument (let's say "program")
    argv[0] = new char[8];
    strcpy_s(argv[0], 8, "program");  // Copy "program" to argv[0]
    // Call run_main with argc and argv
    int result = run_main(argc, argv);    // Output the result of run_main
    cout << "run_main returned: " << result << endl;

    graal_isolate_t* isolate = nullptr;
    graal_isolatethread_t* thread = nullptr;
    // Create the GraalVM isolate and isolate thread
    int create_result = graal_create_isolate(nullptr, &isolate, &thread);
    if (create_result != 0) {
        std::cerr << "Error creating GraalVM isolate!" << std::endl;
        return create_result;
    }
    float sum_result = makeup(thread, 5.0, 25.0);
    std::cout << "Result of makeup function call: " << sum_result << std::endl;


    cout << "Thank you. Exiting." << endl;
    return 0;
}
