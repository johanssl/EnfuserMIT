/**
 *  This package is for the model’s internal data packaging and data structures.
 * When the model loads, almost all information (using Feeds) will be contained
 * within the objects defined in this class. For data there is a multi-tier structure:
 * Variables (VariableData), Sources and Layers. A layer can either hold an
 * Observation or a gridded dataset (GeoGrid). Another important data container
 * described here is the fast-access set for meteorological data
 * (HashedMet that holds Met objects).
 * •	Layers and Sources are introduced in datapack.source
 *
 * •	This package contains the main() –method for the modelling system: EnfuserLaunch.
 *       The given arguments control in which way the model launches
 *       (e.g., server, miner, setup). In case an unknown runmode has been given,
 *       a launch from addons plugin is attempted.
 *
 */
package org.fmi.aq.enfuser.datapack.main;
