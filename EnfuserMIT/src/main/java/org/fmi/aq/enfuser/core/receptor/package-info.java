/**
 * This package takes care of the environmental profiling of a selected point
 * in time and space in the modelling area. The local are is scanned for obstacles
 * and blocks that affect local wind conditions (BlockAnalysis).
 * The environmental profiling (that is done in (EnvProfiler) in essence scans
 * for local emitters and uses the FastPlumeMath functions to compute pollutant
 * concentrations in the selected point of interest.
 *
 * The computational significance of the methods in this package is very high.
 * One of the themes in this package is to define the most effective way of
 * processing the computations (i.e., in zones as a function of distance from
 * the point of interest) without unnecessary repeats of unnecessary computations.
 * This is done via PartialGrid, PartialCombiner, rpTemp and WeightManager.
 *
 * TODO: this is one of the most important packages but unfortunately
 *it is also the most complex one.
 *
 */
package org.fmi.aq.enfuser.core.receptor;
