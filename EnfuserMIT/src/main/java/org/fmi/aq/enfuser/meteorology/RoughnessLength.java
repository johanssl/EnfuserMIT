/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.meteorology.RoughPack.RL_DEFAULT;
import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.Building;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.essentials.gispack.osmreader.core.Tags;
import org.fmi.aq.essentials.gispack.osmreader.core.Materials;

/**
 *
 * @author johanssl
 */
public class RoughnessLength {

    public final static int N_ROUGHS = 50;
    public final static float MIN_RL = 0.001f;
    public final static float MAX_RL = 2f;
    public final static float RL_RANGE = MAX_RL - MIN_RL;
    public static float[] ROUGHS = setupRoughs();

    public final static float[] ROUGHNESS = {
        0.001f,//smooth & water //0
        0.006f, //snow
        0.010f, //lawn grass //2
        0.018f, //rough grass
        0.03f, //rough grass
        0.06f, //crops //4
        0.25f, //few trees
        0.4f, //many tree, few builds //6
        0.5f, //many tree, few builds //6
        0.6f, //forest
        0.8f, //forest
        0.8f, //suburbs //8
        0.1f, //suburbs //8
        1.2f, //suburbs //8
        2f, //miniUrb //9
        3f,//urban center //10
    };

    public final static int RL_SMOOTH = 0;
    public final static int RL_SNOW = 1;
    public final static int RL_GRASS = 2;
    public final static int RL_ROUGHGRASS = 3;
    public final static int RL_ROUGHGRASS2 = 4;
    public final static int RL_CROPS = 5;
    public final static int RL_TREE = 6;
    public final static int RL_TREE1 = 7;
    public final static int RL_TREES_HOUSE = 8;
    public final static int RL_FOREST = 9;
    public final static int RL_FOREST1 = 10;

    public final static int RL_SUB0 = 11;
    public final static int RL_SUB1 = 12;
    public final static int RL_SUB = 13;
    public final static int RL_MURB = 14;
    public final static int RL_URB = 15;

    public final static int WG_SECTOR = 30;
    public final static int NSECTS = 360 / WG_SECTOR;

    private static float[] setupRoughs() {
        ROUGHS = new float[N_ROUGHS];
        for (int i = 0; i < N_ROUGHS; i++) {
            float rl = MIN_RL + i * (float) i / (float) (N_ROUGHS * N_ROUGHS) * RL_RANGE;
            ROUGHS[i] = rl;
        }
        return ROUGHS;
    }

    public static float rlFromIndex(int n) {
        if (n >= N_ROUGHS) {
            return ROUGHS[N_ROUGHS - 1];
        }
        return ROUGHS[n];
    }

    public static int getRLindex(float rl) {

        for (int i = 0; i < N_ROUGHS; i++) {
            if (rl < ROUGHS[i]) {
                return i;
            }
        }
        return N_ROUGHS - 1;
    }

    public static float assessRoughness_simple(RoadPiece rp, Building b, byte osm,
            float vegeIndex, float dist_m,  OsmLayer ol) {
        
        float rough = ROUGHNESS[RL_CROPS];//default

        if (b != null) {//use building data if exist
            float height = b.getHeight_m(ol);
            if (height > 20 && b.area_m2 > 600) {
                rough = ROUGHNESS[RL_URB];

            } else if (height > 15 && b.area_m2 > 400) {
                rough = ROUGHNESS[RL_MURB];

            } else if (height > 7 && b.area_m2 > 300) {
                rough = ROUGHNESS[RL_SUB];
            } else {
                rough = ROUGHNESS[RL_SUB0];
            }

        } else if (rp != null) { //use rp data if exist
            rough = ROUGHNESS[RL_GRASS];//propably comparable?

        } else if (Tags.isPedestrianWay(osm)) { //use rp data if exist
            rough = ROUGHNESS[RL_GRASS];//propably comparable?    

        } else if (vegeIndex > 0.01) { //use vegeIndex if exist
            rough = (float)Math.max(rough, vegeIndex*0.4);

        } else if (OSMget.isWater(osm)) {
            rough = ROUGHNESS[RL_SMOOTH];
        }

        //reduce the effect of far away urban objects
        if (dist_m >= 0) {
            rough = Math.min(rough, rough * 20f / (dist_m + 1));//gets smaller after 40m or so
        }

        return rough;
    }

    /**
     * Roughness length (m) estimator for PuffPlatform (no direction needed)
     *
     * @param range_m evaluation range in meters
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     * @param mp osmLayer
     * @param ops options
     * @return roughness length estimate
     */
    public static float getFastRoughnessLength(float range_m, double lat, double lon,
            OsmLayer mp, FusionOptions ops) {
        if (mp == null) {
            return RL_DEFAULT;//default
        }
        float roughnessLength = 0;
        float rlCounter = 0;

        int N = 10; //5m data for OSM, N cells evaluated per "ray"
        float[] rotated_c = new float[2];
        for (int wd = 0; wd < 360; wd += 60) {

            for (int d = 0; d < N; d++) {// up to 125m away, straight to the north
                if (d == 0 && wd != 0) {
                    continue;//zero distance needs to be evaluated just once when wd =0.
                }
                // then we ,rotate the indexes so that they are directed towards the wind
                rotated_c = FM.rotateHW(d, 0, wd, rotated_c);
                double currLat = lat + rotated_c[0] * mp.dlat;
                double currLon = lon + rotated_c[1] * mp.dlon;

                int h = mp.get_h(currLat);
                int w = mp.get_w(currLon);
                if (mp.oobs(h, w))  continue;// no osm-data, so return something with low roughness (water/plains). 
                byte osm = mp.luSurf[h][w];

                Building b = null;
                if (OSMget.isBuilding(osm)) {
                    b = mp.getBuilding(h, w);
                }
                RoadPiece rp = null;
                if (OSMget.isHighway(osm)) {
                    rp = mp.getRoadPiece(h, w);
                }

                float veg = 0;
                ByteGeoGrid vegbg = mp.getByteGeo(Materials.BGN_VEGE);
                if (vegbg != null) {
                    veg = (float) vegbg.getValue(lat, lon);//the value must be between 0 and 1 originally   
                }

                //update roughness
                float rl = assessRoughness_simple(rp, b, osm, veg, 0, mp);
                roughnessLength += rl;//sum, average later on.
                rlCounter++;
            }//for eval cells
        }//for wd
        
        if (rlCounter==0) return RL_DEFAULT;
        float rl = roughnessLength / rlCounter;
        if (rl > MAX_RL) {
            rl = MAX_RL;
        }
        return rl;
    }
    
        public static float getSingleRoughnessLength(double lat, double lon,FusionCore ens) {
        OsmLayer mp = ens.mapPack.getOsmLayer(lat, lon);
        if (mp == null) {
            return RL_DEFAULT;//default
        }
            int h = mp.get_h(lat);
            int w = mp.get_w(lon);
            if (mp.oobs(h, w))   return RL_DEFAULT;//default
            byte osm = mp.luSurf[h][w];

            Building b = null;
            if (OSMget.isBuilding(osm)) {
                b = mp.getBuilding(h, w);
            }
            
            RoadPiece rp = null;
            if (OSMget.isHighway(osm)) {
                rp = mp.getRoadPiece(h, w);
            }
            
            float veg = (float) mp.getFastVegeDepth(lat, lon);
            return assessRoughness_simple(rp, b, osm, veg, 0, mp);       
    }

    public static byte getRL_type(float val) {

        for (int i = 0; i < ROUGHNESS.length; i++) {
            if (val <= ROUGHNESS[i]) {
                return (byte) i;
            }
        }
        return (byte) (ROUGHNESS.length - 1);
    }

}
