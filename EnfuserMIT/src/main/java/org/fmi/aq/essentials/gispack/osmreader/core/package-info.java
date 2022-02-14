/**
 * 	This large package describes the necessary elements and processes that are used to turn a raw OSM-dataset (text-file) into an osmLayer. Initially, the raw data is stored as Nodes, Ways and Relations in a temporary data structure (OsmTempPack). Using the static OSMprocessor this temporary package is turned into am osmLayer.
 * •	Tags and AllTags classes define all the used land-use classifications.
 * •	Building and BuildingEstimator define the methods and data structures used for all building elements.
 * •	RadiusList and RadiusListElement provide tools for fast search methods (e.g., find the closest road with a given location)
 * •	Materials define all the possible datasets an osmLayer can hold as gridded data.
 *
 */
package org.fmi.aq.essentials.gispack.osmreader.core;
