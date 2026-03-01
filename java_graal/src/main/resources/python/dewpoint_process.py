"""
Hydrocarbon dew point calculation using neqsim via GraalPy.

This script demonstrates how to write process models in Python that call
neqsim Java classes directly through GraalPy's built-in Java interop.
No JVM bridge (JPype) is needed — GraalPy runs on the same VM as neqsim.

Usage:
    This file is loaded by the Java wrapper (PythonDewPointProcess.java)
    via the GraalVM Polyglot API.  The function `calculate_dewpoint` is
    called from the @CEntryPoint method exposed in the native shared
    library (DLL / .so / .dylib).
"""

import java

# Access neqsim Java classes directly via GraalPy interop
SystemSrkEos = java.type("neqsim.thermo.system.SystemSrkEos")
ThermodynamicOperations = java.type(
    "neqsim.thermodynamicoperations.ThermodynamicOperations"
)


def calculate_dewpoint(temperature, pressure, gas_flow_rate):
    """
    Calculate the hydrocarbon dew point temperature and gas density
    for a typical natural gas mixture.

    Args:
        temperature:   Gas temperature [°C]
        pressure:      Gas pressure [bara]
        gas_flow_rate: Gas flow rate [kg/hr]

    Returns:
        A list: [dew_point_temperature (°C), gas_density (kg/m³)]
    """
    # 1) Create a natural gas fluid (typical North Sea composition)
    # SystemSrkEos constructor takes temperature in Kelvin
    fluid = SystemSrkEos(temperature + 273.15, pressure)
    fluid.addComponent("methane", 0.85, "mol/sec")
    fluid.addComponent("ethane", 0.06, "mol/sec")
    fluid.addComponent("propane", 0.03, "mol/sec")
    fluid.addComponent("n-butane", 0.01, "mol/sec")
    fluid.addComponent("n-pentane", 0.005, "mol/sec")
    fluid.addComponent("n-hexane", 0.002, "mol/sec")
    fluid.addComponent("nitrogen", 0.02, "mol/sec")
    fluid.addComponent("CO2", 0.023, "mol/sec")
    fluid.setMixingRule(2)  # Classic mixing rule for SRK
    fluid.setTotalFlowRate(gas_flow_rate, "kg/hr")

    # 2) Run TP flash at the given conditions
    ops = ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    # 3) Read gas-phase density (phase 0 = lightest / vapor phase)
    gas_density = fluid.getPhase(0).getDensity("kg/m3")

    # 4) Calculate hydrocarbon dew point temperature at the given pressure
    dew_point_temp = -999.0
    try:
        fluid_clone = fluid.clone()
        ops2 = ThermodynamicOperations(fluid_clone)
        ops2.dewPointTemperatureFlash()
        dew_point_temp = fluid_clone.getTemperature("C")
    except Exception as e:
        print(f"Dew point flash failed: {e}")

    return [dew_point_temp, gas_density]
