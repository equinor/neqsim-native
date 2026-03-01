package neqsim.server;

import neqsim.util.WaterDewPoint;
import neqsim.process.python.PythonDewPointProcess;

/**
 * Dispatches incoming IPC requests to the actual NeqSim process model methods.
 *
 * <p>Each {@code run*} method is called by {@link NeqSimPipeServer} and
 * delegates to the corresponding process model. Thread-safety is handled
 * via per-function lock objects where needed.
 */
public class ProcessDispatcher {

    private static final Object WATER_DEW_LOCK = new Object();
    private static final Object WATER_GAS_LOCK = new Object();
    private static final Object PY_DEW_LOCK = new Object();

    /**
     * Run the water dew point calculation.
     *
     * @param pressure  System pressure [bar]
     * @param ppmWater  Water content [ppm]
     * @param out       Output array: [0] = dew point temperature [°C]
     * @return quality flag (1 = success, 0 = failure)
     */
    public static int runCalcWaterDewPoint(double pressure, double ppmWater, double[] out) {
        synchronized (WATER_DEW_LOCK) {
            try {
                double result = WaterDewPoint.calcWaterDewPoint(null, pressure, ppmWater);
                if (Double.isNaN(result)) {
                    out[0] = -999.0;
                    return 0;
                }
                out[0] = result;
                return 1;
            } catch (Exception e) {
                System.err.println("[ProcessDispatcher] calcWaterDewPoint failed: " + e.getMessage());
                e.printStackTrace();
                out[0] = -999.0;
                return 0;
            }
        }
    }

    /**
     * Run the water-in-gas calculation.
     *
     * @param pressure    System pressure [bar]
     * @param temperature System temperature [°C]
     * @param out         Output array: [0] = water content [ppm]
     * @return quality flag (1 = success, 0 = failure)
     */
    public static int runCalcWaterInGas(double pressure, double temperature, double[] out) {
        synchronized (WATER_GAS_LOCK) {
            try {
                double result = WaterDewPoint.calcWaterInGas(null, pressure, temperature);
                if (Double.isNaN(result)) {
                    out[0] = -999.0;
                    return 0;
                }
                out[0] = result;
                return 1;
            } catch (Exception e) {
                System.err.println("[ProcessDispatcher] calcWaterInGas failed: " + e.getMessage());
                e.printStackTrace();
                out[0] = -999.0;
                return 0;
            }
        }
    }

    /**
     * Run the Python dew point calculation.
     *
     * @param temperature   Gas temperature [°C]
     * @param pressure      Gas pressure [bara]
     * @param gasFlowRate   Gas flow rate [kg/hr]
     * @param out           Output array: [0] = dew point temperature [°C],
     *                                    [1] = gas density [kg/m³]
     * @return quality flag (1 = success, 0 = failure)
     */
    public static int runPyDewpoint(double temperature, double pressure,
                                     double gasFlowRate, double[] out) {
        synchronized (PY_DEW_LOCK) {
            try {
                double[] results = PythonDewPointProcess.runDewPointCalculation(
                        temperature, pressure, gasFlowRate);
                out[0] = results[0];
                out[1] = results[1];
                return 1;
            } catch (Exception e) {
                System.err.println("[ProcessDispatcher] PY_run_dewpoint failed: " + e.getMessage());
                e.printStackTrace();
                out[0] = -999.0;
                out[1] = -999.0;
                return 0;
            }
        }
    }
}
