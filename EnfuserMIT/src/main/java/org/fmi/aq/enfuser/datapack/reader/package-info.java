/**
 *  A package for supporting classes required to load information from file
 * to the model’s internal data structures.
 *
 * In addition to Feeds that take care
 * of dynamic datasets, this package is responsible for accessing historical
 * archives for similar data without Feeds.
 *
 *  One particular point of interest is the class VarConversion that is used to
 * distinguish the variable type that is being read from file as well as the
 * conversion process to the model’s units. (Note: VarConversion is tightly
 * connected with VariableAssociations). Another important feature in this
 * package is the Loader. Together with LoadTask, the Loader takes care of the
 * initial model loading operation using multithreading. This loading also
 * includes large datasets which do not utilize Feeds, such as the osmLayers.
 *
 */
package org.fmi.aq.enfuser.datapack.reader;
