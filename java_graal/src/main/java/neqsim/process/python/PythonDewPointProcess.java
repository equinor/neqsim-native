package neqsim.process.python;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;

/**
 * Thin wrapper that exposes the Python dew point model as a C-callable
 * entry point.  All GraalPy boilerplate lives in
 * {@link PythonProcessRunner} — adding a new Python process only
 * requires a class like this one.
 *
 * <p>To add another Python model, copy this class and change:
 * <ol>
 *   <li>The script name ({@code "dewpoint_process.py"})</li>
 *   <li>The function name ({@code "calculate_dewpoint"})</li>
 *   <li>The {@code @CEntryPoint} name and parameters</li>
 * </ol>
 */
public class PythonDewPointProcess {

    private static final String SCRIPT   = "dewpoint_process.py";
    private static final String FUNCTION = "calculate_dewpoint";
    private static final Object ENTRYPOINT_LOCK = new Object();

    // ----------------------------------------------------------------
    //  Public API (callable from JVM tests without native pointers)
    // ----------------------------------------------------------------

    /**
     * Run the dew point calculation using the embedded Python model.
     *
     * @param temperature   Gas temperature [°C]
     * @param pressure      Gas pressure [bara]
     * @param gasFlowRate   Gas flow rate [kg/hr]
     * @return double array: [0] = dew point temperature [°C],
     *                       [1] = gas density [kg/m³]
     * @throws Exception if the Python evaluation fails
     */
    public static double[] runDewPointCalculation(
            double temperature, double pressure, double gasFlowRate) throws Exception {
        return PythonProcessRunner.run(SCRIPT, FUNCTION,
                temperature, pressure, gasFlowRate);
    }

    // ----------------------------------------------------------------
    //  C Entry Point (exported in the native shared library)
    // ----------------------------------------------------------------

    /**
     * Native entry point for the Python dew point calculation.
     *
     * <p>Callable from C/C++ as:
     * <pre>
     *   PY_run_dewpoint_calculation(thread,
     *       temperature, pressure, gas_flow_rate,
     *       &amp;dew_point, &amp;gas_density, &amp;quality);
     * </pre>
     *
     * @param thread                The GraalVM isolate thread handle.
     * @param temperature           Gas temperature [°C].
     * @param pressure              Gas pressure [bara].
     * @param gas_flow_rate         Gas flow rate [kg/hr].
     * @param dew_point_temperature Pointer to write the hydrocarbon dew point [°C].
     * @param gas_density           Pointer to write the gas density [kg/m³].
     * @param quality               Pointer to write 1 (success) or 0 (failure).
     */
    @CEntryPoint(name = "PY_run_dewpoint_calculation")
    static void PY_run_dewpoint_calculation(
            IsolateThread thread,
            double temperature,
            double pressure,
            double gas_flow_rate,
            CDoublePointer dew_point_temperature,
            CDoublePointer gas_density,
            CIntPointer quality) {

        synchronized (ENTRYPOINT_LOCK) {
            try {
                double[] results = runDewPointCalculation(
                        temperature, pressure, gas_flow_rate);

                dew_point_temperature.write(0, results[0]);
                gas_density.write(0, results[1]);
                quality.write(0, 1);

            } catch (Exception e) {
                System.err.println("PY_run_dewpoint_calculation failed: " + e.getMessage());
                e.printStackTrace();
                dew_point_temperature.write(0, -999.0);
                gas_density.write(0, -999.0);
                quality.write(0, 0);
            }
        }
    }
}
