package neqsim.process.python;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import neqsim.MockCDoublePointer;
import neqsim.MockCIntPointer;

import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CIntPointer;

/**
 * Tests for the Python-based dew point process model.
 *
 * <p>The "integration" tests require GraalPy on the classpath
 * (available when running with GraalVM JDK or via the Maven
 * polyglot dependencies).
 */
public class PythonDewPointProcessTest {

    /**
     * Test the high-level Java API that wraps the Python model.
     * Verifies that the Python script loads, executes, and returns
     * reasonable values for a typical North Sea gas.
     */
    @Test
    @Tag("integration")
    public void testRunDewPointCalculation() throws Exception {
        double[] results = PythonDewPointProcess.runDewPointCalculation(
                25.0,    // temperature [°C]
                50.0,    // pressure [bara]
                1000.0   // gas flow rate [kg/hr]
        );

        Assertions.assertEquals(2, results.length,
                "Should return [dew_point_temperature, gas_density]");

        double dewPoint   = results[0];
        double gasDensity = results[1];

        System.out.printf("Dew point temperature : %.2f °C%n", dewPoint);
        System.out.printf("Gas density           : %.2f kg/m³%n", gasDensity);

        // Sanity checks — exact values depend on neqsim version
        Assertions.assertTrue(dewPoint > -100 && dewPoint < 50,
                "Dew point should be in a reasonable range, got: " + dewPoint);
        Assertions.assertTrue(gasDensity > 0,
                "Gas density should be positive, got: " + gasDensity);
    }

    /**
     * Test the @CEntryPoint method using mock pointers (runs on JVM).
     */
    @Test
    @Tag("integration")
    public void testCEntryPointWithMockPointers() {
        CDoublePointer dewPointPtr  = new MockCDoublePointer();
        CDoublePointer gasDensPtr   = new MockCDoublePointer();
        CIntPointer    qualityPtr   = new MockCIntPointer();

        // Call the entry-point method directly (thread arg is ignored on JVM)
        PythonDewPointProcess.PY_run_dewpoint_calculation(
                null,           // IsolateThread — unused in JVM mode
                25.0,           // temperature [°C]
                50.0,           // pressure [bara]
                1000.0,         // gas flow rate [kg/hr]
                dewPointPtr,
                gasDensPtr,
                qualityPtr);

        int quality = qualityPtr.read();
        System.out.println("Quality: " + quality);
        System.out.println("Dew point: " + dewPointPtr.read());
        System.out.println("Gas density: " + gasDensPtr.read());

        Assertions.assertEquals(1, quality,
                "Calculation should succeed (quality = 1)");
        Assertions.assertTrue(dewPointPtr.read() > -100,
                "Dew point should be reasonable");
        Assertions.assertTrue(gasDensPtr.read() > 0,
                "Gas density should be positive");
    }
}
