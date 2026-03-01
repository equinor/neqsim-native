#include <stdio.h>
#include "neqsim.h"

int main() {
    graal_isolate_t* isolate = NULL;
    graal_isolatethread_t* thread = NULL;

    printf("=== NeqSim Native Test ===\n\n");

    /* 1. Create the GraalVM isolate */
    printf("Creating GraalVM isolate... ");
    int rc = graal_create_isolate(NULL, &isolate, &thread);
    if (rc != 0) {
        printf("FAILED (code %d)\n", rc);
        return rc;
    }
    printf("OK\n\n");

    /* 2. Call calcWaterDewPoint */
    double dew_point = calcWaterDewPoint(thread, 50.0, 100.0);
    printf("calcWaterDewPoint(pressure=50 bar, ppmWater=100):\n");
    printf("  Dew point = %.4f C\n\n", dew_point);

    /* 3. Call calcWaterInGas */
    double water_in_gas = calcWaterInGas(thread, 50.0, 10.0);
    printf("calcWaterInGas(pressure=50 bar, temperature=10 C):\n");
    printf("  Water content = %.4f ppm\n\n", water_in_gas);

    /* 4. Tear down */
    printf("Tearing down isolate... ");
    graal_tear_down_isolate(thread);
    printf("OK\n");

    printf("\n=== All tests passed! ===\n");
    return 0;
}
