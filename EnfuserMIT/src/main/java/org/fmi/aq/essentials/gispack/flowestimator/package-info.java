/**
 * 	This package holds all classes for estimation, definition and handling of traffic flows for RoadPiece elements within the osmLayer. The package offers two approaches: a simple generic flow patterns based on local properties and the ComponentLineFlow approach, where existing flow data is represented as lines and this information is stored to RoadPieces. In the latter case the flows are generated based on peak-hour components (e.g., peak morning hour, mid-day and evening). The FlowEstimator describes the general framework for flow patterns (the vehicle types and how many different day-of-week profiles are in use).
 */
package org.fmi.aq.essentials.gispack.flowestimator;
