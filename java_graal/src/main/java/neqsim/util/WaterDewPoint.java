package neqsim.util;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.IsolateThread;

public class WaterDewPoint {

    /**
     * Calculates the water dew point temperature for a given pressure and water content (ppm).
     * This method is exposed as a C entry point for native access via GraalVM.
     *
     * @param thread    GraalVM isolate thread (required for native entry points)
     * @param pressure  The system pressure in bar
     * @param ppmWater  Water content in parts per million (ppm)
     * @return          Calculated dew point temperature in degrees Celsius
     */
    @CEntryPoint(name = "calcWaterDewPoint")
    public static double calcWaterDewPoint(IsolateThread thread, double pressure, double ppmWater) {
        // Create a new thermodynamic system

        SystemInterface testSystem = new SystemSrkCPAstatoil(260.15, pressure);
        testSystem.addComponent("CO2", 0.02);
        testSystem.addComponent("nitrogen", 0.01);
        testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
        testSystem.addComponent("ethane", 0.05);
        testSystem.addComponent("propane", 0.01);
        testSystem.addComponent("i-butane", 0.005);
        testSystem.addComponent("n-butane", 0.005);
        testSystem.addComponent("water", ppmWater * 1e-6);
        testSystem.setMixingRule(10);
        testSystem.setMultiPhaseCheck(true);

        // Perform the water dew point calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        try {
            ops.waterDewPointTemperatureMultiphaseFlash();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Get the calculated dew point temperature
        double dewPointTemperature = testSystem.getTemperature("C");

        return dewPointTemperature;
    }

    /**
     * Calculates the water content in gas at a given pressure and temperature.
     * This method is exposed as a C entry point for native access via GraalVM.
     *
     * @param thread      GraalVM isolate thread (required for native entry points)
     * @param pressure    The system pressure in bar
     * @param temperature The system temperature in degrees Celsius
     * @return            Water content in gas phase in parts per million (ppm)
     */
    @CEntryPoint(name = "calcWaterInGas")
    public static double calcWaterInGas(IsolateThread thread, double pressure, double temperature) {
        // Create a new thermodynamic system
        double ppmWater = 100.0;
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + temperature, pressure);
        testSystem.addComponent("CO2", 0.02);
        testSystem.addComponent("nitrogen", 0.01);
        testSystem.addComponent("methane", 0.9 - ppmWater * 1e-6);
        testSystem.addComponent("ethane", 0.05);
        testSystem.addComponent("propane", 0.01);
        testSystem.addComponent("i-butane", 0.005);
        testSystem.addComponent("n-butane", 0.005);
        testSystem.addComponent("water", ppmWater * 1e-6);
        testSystem.setMixingRule(10);

        // Perform the water dew point calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        try {
            ops.saturateWithWater();
        } catch (Exception e) {
            e.printStackTrace();
        }
        double waterContent = testSystem.getPhase("gas").getComponent("water").getx() * 1e6;

        return waterContent;
    }

    /**
     * Main method for example usage of the dew point and water-in-gas calculations.
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        // Example usage
       System.out.println("Calculated water dew point temperature: " );

        System.out.println("Calculated water content in gas: " );
    }

}
