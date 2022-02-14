/**
 * 	The purpose of this package is to introduce memory-wise efficient datasets for two dimensional integer data. The idea is to hold multiple smaller grids (e.g. int[][]) within a larger grid structure; in case the smaller sub-grid can be represented by a single value then only one integer value will be stored in memory. This can save significant amount of memory for a high resolution dataset in case there are large areas that can be represented with a single unique grid value. A concrete example of such cases are a) roads and b) buildings.
 * •	These grids are used in osmLayer to hold the spatial reference mappings of roads and buildings in the area with a resolution of 5m.
 *
 */
package org.fmi.aq.essentials.tools.subgrid;
