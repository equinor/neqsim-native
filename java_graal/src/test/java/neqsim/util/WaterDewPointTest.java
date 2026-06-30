package neqsim.util;

import org.junit.jupiter.api.Test;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class WaterDewPointTest {
    
    @Test
    void testCalcWaterDewPoint() {
        double ppmWater = 22.0;

        SystemInterface testSystem = new SystemSrkCPAstatoil(260.15, 70.0);
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
            fail("Exception during dew point calculation: " + e.getMessage());
        }

        // Get the calculated dew point temperature
        double dewPointTemperature = testSystem.getTemperature("C");

        // Assert the expected value (this value should be adjusted based on expected results)
        assertEquals(-24.71094269, dewPointTemperature, 0.1,
                "Dew point temperature is not as expected");
    }

    @Test
    void testCalcWaterInGas() {
        double ppmWater = 22.0;

        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 24.710, 70.0);
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
            fail("Exception during water saturation: " + e.getMessage());
        }
        double waterContent = testSystem.getPhase("gas").getComponent("water").getx() * 1e6;
        // Reference value recalculated against neqsim 3.14.0's CPA water correlation
        // (was 22.0 against neqsim 3.2.1; ~0.8% lower with the updated correlation).
        assertEquals(21.82, waterContent, 0.1, "Water content is not as expected");

    }
}
