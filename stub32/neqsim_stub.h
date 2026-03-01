/**
 * neqsim_stub.h — Public API for the 32-bit NeqSim stub DLL.
 *
 * This header mirrors the function signatures produced by the GraalVM
 * native-image shared library (neqsim.dll / neqsim.h) so that existing
 * 32-bit C/C++ callers can link against this stub with no code changes.
 *
 * Under the hood, each call is forwarded over a local TCP socket to the
 * 64-bit neqsim_server.exe which hosts the real NeqSim process models.
 */
#ifndef NEQSIM_STUB_H
#define NEQSIM_STUB_H

#ifdef __cplusplus
extern "C" {
#endif

#ifdef NEQSIM_STUB_EXPORTS
#define NEQSIM_API __declspec(dllexport)
#else
#define NEQSIM_API __declspec(dllimport)
#endif

/* ------------------------------------------------------------------ */
/* GraalVM-compatible typedefs (opaque in the stub)                    */
/* ------------------------------------------------------------------ */
typedef void* graal_isolate_t;
typedef void* graal_isolatethread_t;

/* ------------------------------------------------------------------ */
/* Isolate lifecycle — thin wrappers around server connection           */
/* ------------------------------------------------------------------ */

/**
 * Create a connection to the NeqSim server.
 * Starts neqsim_server.exe automatically if it is not already running.
 *
 * @param params  Ignored (kept for API compatibility, pass NULL).
 * @param isolate Receives an opaque handle (unused in stub, set to dummy).
 * @param thread  Receives an opaque handle (unused in stub, set to dummy).
 * @return 0 on success, non-zero on error.
 */
NEQSIM_API int graal_create_isolate(
    void*                  params,
    graal_isolate_t*       isolate,
    graal_isolatethread_t* thread);

/**
 * Disconnect from the server.
 * @return 0 on success.
 */
NEQSIM_API int graal_detach_thread(graal_isolatethread_t thread);

/**
 * Tear down the server connection (same as detach for the stub).
 * @return 0 on success.
 */
NEQSIM_API int graal_tear_down_isolate(graal_isolatethread_t thread);

/* ------------------------------------------------------------------ */
/* Process simulation functions                                        */
/* ------------------------------------------------------------------ */

/**
 * Calculate the water dew point temperature for a given pressure and
 * water content.
 *
 * @param thread    Isolate thread handle (ignored by stub).
 * @param pressure  System pressure [bar].
 * @param ppmWater  Water content [ppm].
 * @param result    Pointer to write the dew point temperature [°C].
 * @param quality   Pointer to write 1 (success) or 0 (failure).
 */
NEQSIM_API void calcWaterDewPoint(
    graal_isolatethread_t thread,
    double  pressure,
    double  ppmWater,
    double* result,
    int*    quality);

/**
 * Calculate the water content in gas at a given pressure and temperature.
 *
 * @param thread      Isolate thread handle (ignored by stub).
 * @param pressure    System pressure [bar].
 * @param temperature System temperature [°C].
 * @param result      Pointer to write the water content [ppm].
 * @param quality     Pointer to write 1 (success) or 0 (failure).
 */
NEQSIM_API void calcWaterInGas(
    graal_isolatethread_t thread,
    double  pressure,
    double  temperature,
    double* result,
    int*    quality);

/**
 * Run the Python-based hydrocarbon dew point calculation.
 *
 * @param thread                Isolate thread handle (ignored by stub).
 * @param temperature           Gas temperature [°C].
 * @param pressure              Gas pressure [bara].
 * @param gas_flow_rate         Gas flow rate [kg/hr].
 * @param dew_point_temperature Pointer to write the dew point [°C].
 * @param gas_density           Pointer to write the gas density [kg/m³].
 * @param quality               Pointer to write 1 (success) or 0 (failure).
 */
NEQSIM_API void PY_run_dewpoint_calculation(
    graal_isolatethread_t thread,
    double  temperature,
    double  pressure,
    double  gas_flow_rate,
    double* dew_point_temperature,
    double* gas_density,
    int*    quality);

#ifdef __cplusplus
}
#endif

#endif /* NEQSIM_STUB_H */
