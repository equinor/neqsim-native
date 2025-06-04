# Johan Castberg
## `JCA_get_Frms`

### 📥 Input Parameters

| Name          | Type   | Description                                                                 |
|---------------|--------|-----------------------------------------------------------------------------|
| `fluid_type`  | `int`  | Castberg fluid type: 1 -> Skrugard, 2 -> Havis, 3 -> Drivis                 |
| `gas_flow`    | `float`| Gas flow rate from MPFM [Sm3/hr]                                            |
| `oil_flow`    | `float`| Oil flow rate from MPFM [Sm3/hr]                                            |
| `water_flow`  | `float`| Water flow rate from MPFM [Sm3/hr]                                          |
| `pressure`    | `float`| Pressure downstream subsea choke [barg]                                    |
| `temperature` | `float`| Temperature downstream subsea choke [C]                                    |
| `A`           | `float`| Cross-sectional area of riser downstream choke [m2]                        |

### 📤 Output Parameters

| Name         | Type    | Description                                 |
|--------------|---------|---------------------------------------------|
| `F_rms`       | `float` | Calculates Frms [N] from min(1, 5 * (1 - gvf)) * D^1.6 * Frms_const * rho_l^0.6 * vel_mix^1.2                                     |
| `F_rms_max`   | `float` | Calculates Frms_max [N] from ((VF * -0.6308929) + 63.86127) * sin(VF / 11.486086) - 13765.806 / (VF + 26.99966) + (WC * 0.27049398) + 551.5218 for VF > 2.4. VF < 1: Frms_max=430, 1<VF<2.4: Frms_max=147.                                |
| `quality`    | `int` | Indicator of good (1) or bad (0) calculation. | 

# RAIA

## `RAIA_run_process_simulation`

Runs the RAIA process simulation with the specified input parameters and writes the results to the provided pointers.

### 🧩 Process Steps:
1. Sets the input parameters for the simulation.  
2. Establishes the RAIA process model.  
3. Runs the process simulation in a separate thread with a timeout.  
4. Calculates:
   - Mass balance  
   - TVP (True Vapor Pressure)  
   - RVP (Reid Vapor Pressure)  
   - Dew point temperature  
   - Methane content  
   - Wobbe Index (WI) of the export gas  
   - Quality an integer parameter indicating sucessfull (return 1) or bad calculation (return 0)
6. Writes the calculated results to the provided `CDoublePointer` objects and CIntPointer object for quality

---

### 📥 Parameters

| Name | Description |
|------|-------------|
| `thread` | The isolate thread for the native entry point. |
| `pda_flow` | Hydrocarbon flow rate for PDA (kg/hr). |
| `seat_flow` | Hydrocarbon flow rate for Seat (kg/hr). |
| `gavea_flow` | Hydrocarbon flow rate for Gavea (kg/hr). |
| `reboiler_temperature` | NGL column reboiler temperature (°C). |
| `fourth_stage_heater_temperature` | Fourth stage heater temperature (°C). |
| `expander_out_pressure` | Expander outlet pressure (bara). |
| `number_of_hx` | Number of heat exchangers in the dew point process (1 or 2). |
| `massbalance` | Pointer to write the mass balance error (%). |
| `tvpcalc` | Pointer to write the TVP of the export oil (bara). |
| `rvpcalc` | Pointer to write the RVP of the export oil (bara). |
| `dewpoint_gas_export` | Pointer to write the dew point temperature of the export gas (°C). |
| `methane_in_export_gas` | Pointer to write the methane content in the export gas (mol%). |
| `wi_export_gas` | Pointer to write the Wobbe Index of the export gas (MJ/m³). |
| `quality` | Pointer to write the quality of the calculation indicating bad (0) or success (1). |
