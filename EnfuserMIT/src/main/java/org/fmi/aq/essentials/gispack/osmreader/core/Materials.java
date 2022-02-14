/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.io.File;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import java.util.ArrayList;

/**
 *In this static class the fixed information layers that are commonly present
 * on osmLayers are described. Also their data types, default resolution and
 * some other properties are defined.
 * @author johanssl
 */
public class Materials {

    //default byte geogrids
    public final static String BGN_ELEVATION = "elevation";
    public final static String BGN_VEGE = "vege_depth";
    public final static String BGN_POP = "population";
    public final static String BGN_POPDEN1 = "popdens_1km";
    public final static String BGN_POPDEN6 = "popdens_6km";
    public final static String BGN_GHSBUILT = "ghs_built";
    public final static String BGN_GHS_POP = "ghs_pop";
    public final static String BGN_PLACEHOLDER = "placeholder";
    public final static String BGN_PLACEHOLDER2 = "placeholder2";
    public final static String BGN_ZEROMASK = "zeromask";
    public final static String BGN_ELEGRADIENT = "elevation_gradient";
    public final static String BGN_LOCALELEV = "elevation_local";
    public final static String BGN_BUILD_HEIGHT = "building_height";
    public static final String DEF_WB_CAT = "household";
    //then some custom sources
    public final static String SOURCE_COLMAP = "colormap";
    public final static String SOURCE_INFRACOLMAP = "infra_colormap";
    public final static String SOURCE_ROAD_FLOW = "roadflow";
    public final static String SOURCE_OSM = "landuse_osm";

    public final static String GGRID = "ggrid";
    public final static String NONGRID = "non_grid";
    
    //for osmLayer quality control checks. For some types warn if missing or
    //the resolution is less than desired.
    private final static String WARN_IF= "WARN_IF";
    public static final String MISSING= "MISSING";
    public static final String POOR_RES= "POOR_RES";
     
    public final static int IND_TYPENAME =0;
    public final static int IND_DATATYPE =1;
    public final static int IND_DEF_RES = 2;
    public final static int IND_QC_CHECKS = 3;
    
    public final static String[][] ALL_SOURCES = {
        
        {SOURCE_COLMAP      ,NONGRID    ,"10"   ,WARN_IF+MISSING+POOR_RES  },
        {SOURCE_INFRACOLMAP ,NONGRID    ,"10"   ,WARN_IF+MISSING+POOR_RES},
        {SOURCE_ROAD_FLOW   ,NONGRID    ,"-1"   ,WARN_IF},
        {SOURCE_OSM         ,NONGRID    ,"5"    ,WARN_IF},
        
        {BGN_VEGE           ,GGRID      ,"10"   ,WARN_IF+MISSING+POOR_RES},
        {BGN_ELEVATION      ,GGRID      ,"30"   ,WARN_IF+MISSING+POOR_RES},
        {BGN_POP            ,GGRID      ,"100"  ,WARN_IF+MISSING},
        {BGN_POPDEN1        ,GGRID      ,"100"  ,WARN_IF+MISSING },
        {BGN_POPDEN6        ,GGRID      ,"100"  ,WARN_IF+MISSING},
        {BGN_GHSBUILT       ,GGRID      ,"30"   ,WARN_IF+MISSING+POOR_RES},
        {BGN_GHS_POP        ,GGRID      ,"260"  ,WARN_IF+MISSING+POOR_RES},
        {BGN_BUILD_HEIGHT   ,GGRID      ,"10"   ,null},
        {BGN_PLACEHOLDER    ,GGRID      ,"20"   ,null},
        {BGN_PLACEHOLDER2   ,GGRID      ,"20"   ,null},
        {BGN_ZEROMASK       ,GGRID      ,"10"   ,null},
        {DEF_WB_CAT         ,GGRID      ,"20"   ,WARN_IF+MISSING},
        {BGN_ELEGRADIENT    ,GGRID      ,"30"   ,null},
        {BGN_LOCALELEV      ,GGRID      ,"30"   ,null},
    };
    
    //this defines a sub directory name for additional data, other than the raw OSM
    public static final String ADD_DATA_SUBDIR = "addData" + File.separator;
    

    
    public static String[] listAvailableMaterials(OsmLayer ol, boolean gridsOnly) {

        ArrayList<String> temp = new ArrayList<>();
        for (String[] line : ALL_SOURCES) {
            String type = line[IND_DATATYPE];
            String key = line[IND_TYPENAME];

            if (type.equals(GGRID)) {

                ByteGeoGrid bg = ol.getByteGeo(key);
                if (bg != null) {
                    temp.add(key);
                }

            } else {//not a grid type

                if (gridsOnly) {
                    continue;//not going to be added.
                }
                //check all cases manually
                if (key.equals(SOURCE_COLMAP)) {
                    if (ol.colMap != null) {
                        temp.add(key);
                    }
                } else if (key.equals(SOURCE_INFRACOLMAP)) {
                    if (ol.nearInfraRedMap != null) {
                        temp.add(key);
                    }
                } else if (key.equals(SOURCE_ROAD_FLOW)) {
                    if (ol.hasTrafficFlows) {
                        temp.add(key);
                    }
                } else {
                    temp.add(key);
                }
            }
        }

        //find other grids that are not already present
        for (String key : ol.defaultBgs.keySet()) {
            if (!temp.contains(key)) {
                temp.add(key);
            }
        }
        
        String[] list = new String[temp.size()];
        return temp.toArray(list);
    }
    
    /**
     * Get the default resolution for a data type name. Returns -1 if not gridded
     * data type or unknown.
     * @param key the data type name.
     * @return resolution in meters.
     */
    public static double getDefaultResolution(String key) {
        for (String[] line : ALL_SOURCES) {
            String res = line[IND_DEF_RES];
            String kkey = line[IND_TYPENAME];
            if (key.equals(kkey)) {
                return Double.valueOf(res);
            }
        }
        return -1;
    }
    
    /**
     * Based on a datatype name, check whether or not this is a gridded dataset type.
     * @param key the name
     * @return true, if gridded or unknown.
     */
    public static boolean isGriddedDataType(String key) {
        for (String[] line : ALL_SOURCES) {
            String type = line[IND_DATATYPE];
            String kkey = line[IND_TYPENAME];
            if (key.equals(kkey)) {
                return type.equals(GGRID);
            }
        }
        return true;
    }
}
