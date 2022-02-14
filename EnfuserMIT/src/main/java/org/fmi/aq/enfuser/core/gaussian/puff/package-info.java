/**
 * Together with enfuser.fpm these packages form the Gaussian modelling classes.
 *the puff package concentrates of Gaussian Puff modelling, i.e.,
 * a single Lagrangian particle modelling.
 * Overall, there is the PuffPlatform that takes care of the modelling of puffs
 * (PuffCarrier), which are being “injected" by emitters (using the Injector class).
 * For performance reasons, not all emitters are allowed to release a large amount
 * of puffs independently: for this reason an intermediary emitter class is used
 * to combine and control the releases of certain emitter types (RangedMapEmitter).
 *
 * •Note: In a modelling run the puff computations with PuffPlatform are performed
 * first in a separate phase. Once done, the modelling proceeds into “profiling phase"
 * (see package org.fmi.aq.enfuser.profiler)
 *
 * •The model can also be used without Gaussian Puff modelling (e.g., for quicker
 * long-term model runs with enfuserGUI). In such cases only the Gaussian Plume
 * assessment is being used but with a longer assessment radius of up to 8km.  *
 */
package org.fmi.aq.enfuser.core.gaussian.puff;
