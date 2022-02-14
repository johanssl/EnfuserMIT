/**
 *  This package introduces classes that make it possible to perform faster
 * profiler-computations in the defined modelling area. Since the model performs
 * the computations in a sequence of time steps (e.g., one hour or 30min time steps),
 * the fast grid classes “prepare" the emission sources in the area into a pre-computed
 * grid structure (HashedEmissionGrid).
 *
 * • For each time step the grids are updated, however, the local environment obstacle
 * and block scan needs to be done only once since BlockAnalysis objects are not time-dependent.
 * • The HashedEmissionGrid can be responsible for the most memory usage when the model is used
 * o Tip: The HashedEmissionGrid benefits from “sparse" emitters and if needed all emitter
 * grids can be virtually made sparser (e.g., only one of 4,9, or 16 cells acts
 * as an source of emissions and contains the emissions of other inactive cells nearby)  *
 */
package org.fmi.aq.enfuser.core.fastgrids;
