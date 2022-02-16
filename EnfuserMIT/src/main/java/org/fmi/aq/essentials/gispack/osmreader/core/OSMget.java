/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_CAR;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_HEAVY;
import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer.FRD;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.LU_N_A;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *
 * @author johanssl
 */
public class OSMget {

    
    public static boolean isCoastal(OsmLayer osm, double lat, double lon) {
        byte c = getSurfaceType(osm, lat, lon);

        if (isWater(c)) {
            //water, but is it coastal?

            //search the surrounding land uses. If one of them is not water then the we have a coastal land-use!
            for (int h = -1; h <= 1; h++) {
                for (int w = -1; w <= 1; w++) {

                    try {
                        byte cc = getSurfaceType(osm, lat + h * osm.dlat, lon + w * osm.dlon);

                        if (!isWater(cc) && cc != LU_N_A) {
                            return true;
                        }

                    } catch (ArrayIndexOutOfBoundsException ex) {

                    }
                }
            }

            return false;

        } else {
            return false;
        }
    }

    public static byte roadTier(byte osm) {
        return (byte) RoadPiece.tier(osm);
    }

    public static String roadPiecePrintout(RoadPiece rp) {
        String o = "RoadPiece printout: ";
        if (rp == null) {
            return o + " - null.";
        }

        o += rp.name + ", ref = " + rp.ref_number + "\n";
        o += "Tier = " + rp.tier() + ", v = " + rp.speedLimit + "km/h, lanes = " + rp.lanes + "\n";
        o += "Primary flow package = " + rp.flowSource() + "\n";
        float[] flows = rp.getDAVGS();
        o += "Vehicles/d=" + (int) flows[IND_CAR] + ", heavy = " + (int) flows[IND_HEAVY] + "\n";

        o += "Buslines[n] = " + rp.buslines + "\n";
        o += "Surface = " + RoadPiece.SURF_NAMES[rp.surface] + ", oneway = " + rp.oneway + "\n";

        int trafl_len = 0;
        if (rp.trafficLight_latlons != null) {
            trafl_len = rp.trafficLight_latlons.length;
        }

        o += "Gen. heading(deg) = " + (int) rp.getWayDirection_degs() + ", traffic lights[n] = " + trafl_len + "\n";

        return o;
    }

    public static float getElevation(OsmLayer ol, double lat, double lon) {
        if (ol == null) {
            return 0;
        }
        ByteGeoGrid bg = ol.getByteGeo(Materials.BGN_ELEVATION);
        if (bg == null) {
            return 0;
        }
        return (float) ByteGeoGrid.getValue_OOBzero(bg, lat, lon);
    }

    public static float getBGdata_perM2(String key, OsmLayer ol, double lat, double lon) {

        if (ol == null) {
            return 0;
        }
        ByteGeoGrid bg = ol.getByteGeo(key);
        if (bg == null) {
            return 0;
        }

        float dat = (float) ByteGeoGrid.getValue_OOBzero(bg, lat, lon);
        return (float) (dat / bg.cellA_m2);
    }

    public static float getBGdata(String key, OsmLayer ol, double lat, double lon) {
        if (ol == null) {
            return 0;
        }

        ByteGeoGrid bg = ol.getByteGeo(key);
        if (bg == null) {
            return 0;
        }

        return (float) ByteGeoGrid.getValue_OOBzero(bg, lat, lon);
    }

    public static Double getBGdata_hw(String key, OsmLayer ol, int h, int w) {
        if (ol == null) {
            return null;
        }
        ByteGeoGrid bg = ol.getByteGeo(key);
        if (bg == null) {
            return null;
        }

        double lat = ol.get_lat(h);
        double lon = ol.get_lon(w);

        return bg.getValue_excSafe(lat, lon);
    }


    public static boolean isWater(byte c) {
        return Tags.isWater(c);
    }

    public static byte getFunctionaltype_H0_UP(int H, int W, OsmLayer osm) {

        try {
            byte b = (osm.luFunc[H / FRD][W / FRD]);
            return b;
        } catch (ArrayIndexOutOfBoundsException e) {
            return LU_N_A;
        }

    }

    public static byte getSurfaceType(OsmLayer ol, double lat, double lon) {
        if (ol == null) {
            return LU_N_A;
        }

        int h = ol.get_h(lat);
        int w = ol.get_w(lon);
        
        if (ol.oobs(h, w)) return LU_N_A;
            byte b = (ol.luSurf[h][w]);
            return b;
    }

    public static byte getFunctionaltype(OsmLayer osm, double lat, double lon) {
        if (osm == null) {
            return LU_N_A;
        }

        int h = osm.get_h(lat);
        int w = osm.get_w(lon);

        try {
            byte b = (osm.luFunc[h / FRD][w / FRD]);
            return b;
        } catch (ArrayIndexOutOfBoundsException e) {
            return LU_N_A;
        }

    }

    public static byte[] getOSMtypes(OsmLayer osm, double lat, double lon, byte[] temp) {
        if (temp == null) {
            temp = new byte[2];
        }
        temp[0] = LU_N_A;
        temp[1] = LU_N_A;
        if (osm == null) {
            return temp;
        }

        int h = osm.get_h(lat);
        int w = osm.get_w(lon);

        try {
            temp[0] = (osm.luSurf[h][w]);
            temp[1] = (osm.luFunc[h / FRD][w / FRD]);

        } catch (ArrayIndexOutOfBoundsException e) {
        }
        return temp;
    }

    public static ArrayList<Long> getHighwayRefIDs_original(OsmLayer ol) {

        ArrayList<Long> refs = new ArrayList<>();

        HashMap<Integer, RoadPiece> rps = ol.allRPs();
        for (Integer key : rps.keySet()) {
            RoadPiece rp = rps.get(key);
            long ref = rp.ref_number;

            refs.add(ref);
        }
        return refs;
    }

    public static ArrayList<Integer> getHighwayRefIDs_int(OsmLayer ol) {

        ArrayList<Integer> refs = new ArrayList<>();

        HashMap<Integer, RoadPiece> rps = ol.allRPs();
        for (Integer key : rps.keySet()) {
            refs.add(key);
        }
        return refs;
    }

    /**
     * Find the closest instance of certain surface type. Returns hw index and
     * also the found osm type.
     *
     * @param omin the minimum range of the types (see Tags) that evaluates as a
     * match.
     * @param omax the maximum range of the types that evaluates as a match.
     * @param ol the OsmLayer under processing.
     * @param lat latitude value for which the search is done.
     * @param lon longitude value for which the search is done.
     * @param rad_m radius in meters for which the search is done.
     * @return h,w index of the match and the osm-type as 3rd element.
     */
    public static int[] getSurfaceTypeIndex_atRadius(byte omin, byte omax, OsmLayer ol,
            double lat, double lon, double rad_m) {
        if (ol == null) {
            return null;
        }
        //if (temp == null) temp = new int[2];
        int h = ol.get_h(lat);
        int w = ol.get_w(lon);

        //byte osm = ol.getSurfaceType_hw(h, w);
        //if (osm >= omin && osm<=omax) return new int[]{h,w,osm};//found instantly
        //int maxRad = (int)rad_m/ol.resm +1;
        for (RadListElement e : ol.radList_5m_100m.elems) {

            if (e.dist_m > rad_m) {
                break;//sorted list, all others are beyond the range as well.
            }
            int nh = h + e.hAdd;
            int nw = w + e.wAdd;
            //check
            byte osm = ol.getSurfaceType_hw(nh, nw);
            if (osm >= omin && osm <= omax) {
                return new int[]{nh, nw, osm};//found instantly
            }
        }
        return null;
    }

    public static RoadPiece getRoad_atRadius(OsmLayer ol, double lat, double lon, double rad_m) {
        if (ol == null) {
            return null;
        }
        int h = ol.get_h(lat);
        int w = ol.get_w(lon);
        return getRoad_atRadius_hw(ol, h, w, rad_m);
    }

    /**
     * return the first road that is found inside the search radius. Step-size
     * is 5m and the maximum radius is 100m.
     *
     * @param ol the layer
     * @param h h index
     * @param w w index
     * @param rad_m search max radius_m
     * @return a RoadPiece. Returns null if no road is to be found.
     */
    public static RoadPiece getRoad_atRadius_hw(OsmLayer ol, int h, int w, double rad_m) {
        if (ol == null) {
            return null;
        }

        for (RadListElement e : ol.radList_5m_100m.elems) {

            if (e.dist_m > rad_m) {
                break;//sorted list, all others are beyond the range as well.
            }
            if (e.oobs_HW_hw(ol.H, ol.W, h, w)) continue;
            int nh = h + e.hAdd;
            int nw = w + e.wAdd;
            //check
                RoadPiece rp = ol.getRoadPiece(nh, nw);
                if (rp != null) {
                    return rp;
                }
            
        }
        return null;
    }

    /**
     * return all unique roads that are found inside the search radius.
     * Step-size is 5m and the maximum radius is 100m.
     *
     * @param temp a temp list. This is cleared, added with and returned.
     * @param ol the layer
     * @param h h index
     * @param w w index
     * @param rad_m search max radius_m
     * @return temp list with added elements. Key = road's ref-number (int)
     */
    public static HashMap<Integer, RoadPiece> getAllRoads_atRadius_hw(
            HashMap<Integer, RoadPiece> temp, OsmLayer ol, int h, int w, double rad_m) {

        temp.clear();

        if (ol == null) {
            return temp;
        }

        for (RadListElement e : ol.radList_5m_100m.elems) {

            if (e.dist_m > rad_m) {
                break;//sorted list, all others are beyond the range as well.
            }
            int nh = h + e.hAdd;
            int nw = w + e.wAdd;
            //check
            try {
                RoadPiece rp = ol.getRoadPiece(nh, nw);
                if (rp != null) {
                    temp.put(rp.ref_number_int, rp);
                }
            } catch (IndexOutOfBoundsException ex) {
            }
        }
        return temp;
    }
    
        public static ArrayList<RoadPiece> findRoads(int[] speedRange, int sizeMax, MapPack mp,
                double lat, double lon, double rad_m) {

        ArrayList<RoadPiece> rps = new ArrayList<>();
        OsmLayer ol = mp.getOsmLayer(lat,lon);
        if (ol == null) {
            return rps;
        }
        return findRoads(speedRange, sizeMax, ol,
           lat, lon, rad_m);
       
    }
        
    public static ArrayList<RoadPiece> findRoads(int[] speedRange, int sizeMax, OsmLayer ol,
            double lat, double lon, double rad_m) {
        ArrayList<RoadPiece> rps = new ArrayList<>();
        
        int h = ol.get_h(lat);
        int w = ol.get_w(lon);
        
        for (RadListElement e : ol.radList_5m_100m.elems) {

            if (e.dist_m > rad_m) {
                break;//sorted list, all others are beyond the range as well.
            }
            int nh = h + e.hAdd;
            int nw = w + e.wAdd;
            //check
            try {
                RoadPiece rp = ol.getRoadPiece(nh, nw);
                if (rp != null && !rps.contains(rp)) {
                    if (speedRange==null || (rp.speedLimit >= speedRange[0] && rp.speedLimit <= speedRange[1])){
                        rps.add(rp);
                        if (rps.size()>=sizeMax) return rps;
                    }
                }
            } catch (IndexOutOfBoundsException ex) {
            }
        }
        return rps;
    }    

    public static ArrayList<RoadPiece> getPieces(OsmLayer ol) {

        ArrayList<RoadPiece> refs = new ArrayList<>();

        HashMap<Integer, RoadPiece> rps = ol.allRPs();
        for (Integer key : rps.keySet()) {
            RoadPiece rp = rps.get(key);
            refs.add(rp);
        }
        return refs;
    }

    public final static float TRS_DIST_NA = -100;

    public static float getDistTo_trafficSignal_m(RoadPiece rp, int h, int w, OsmLayer ol) {
        boolean changed = false;
        float dist = Float.MAX_VALUE;
        if (rp.trafficLight_latlons == null) {
            return TRS_DIST_NA;
        }

        for (float[] latlon : rp.trafficLight_latlons) {
            //decode
            float lat = latlon[0];
            float lon = latlon[1];

            int h2 = getH(ol.b, lat, ol.dlat);
            int w2 = getW(ol.b, lon, ol.dlon);
            // int h2 = key/100000;
            // int w2 = key - h2*100000;
            float dist_proxy = Math.abs(h - h2) + Math.abs(w - w2);
            if (dist_proxy < dist) {
                dist = dist_proxy;
                changed = true;
            }
        }

        if (!changed) {
            return TRS_DIST_NA;
        } else {
            float f = (float) (dist * ol.resm);
            f += 10;//should not be zero
            return f;
        }

    }

    private static float[][] sin_cos_360;
    private static Sector sect;

    private static void initFastTrigons() {
        sin_cos_360 = new float[361][2];
        sect = new Sector();
        //precalc rotation
        for (int wd = 0; wd <= 360; wd++) {
            double beta = (2 * Math.PI / 360.0) * wd;
            double cos = Math.cos(beta);
            double sin = Math.sin(beta);
            sin_cos_360[wd][0] = (float) sin;
            sin_cos_360[wd][1] = (float) cos;
        }

    }

    public static float[] rotateDir(float h_m, float w_m, double windDir, float[] temp) {

        if (sin_cos_360 == null) {
            initFastTrigons();
        }
        float newH;
        float newW;

        if (temp == null) {
            temp = new float[2];
        }

        float sin = sin_cos_360[(int) windDir][0];
        float cos = sin_cos_360[(int) windDir][1];

        newH = (h_m * cos - w_m * sin);
        newW = (w_m * cos + h_m * sin);

        temp[0] = (float) newH;
        temp[1] = (float) newW;

        return temp;
    }

    public static final int INCL_STEP = 1;
    private final static float d225 = 22.5f;

    public static double getRoadInclination_percent_new(RoadPiece rp, double lat, double lon, OsmLayer ol) {

        ByteGeoGrid elevation = ol.getByteGeo(Materials.BGN_ELEVATION);

        if (!rp.hasDirection() || elevation == null) {
            if (!rp.hasDirection()) {
                EnfuserLogger.log(Level.FINER,OSMget.class,"RoadPiece has no direction!");
            }
            return 0;
        }

        int h = getH(elevation.gridBounds, lat, elevation.dlat);
        int w = getW(elevation.gridBounds, lon, elevation.dlon);

        float dir = rp.getWayDirection_degs();

        double inclination = 0;
        int max = 3;
        for (int i = 0; i < max; i++) {

            if (i == 1) {
                dir += 10;
                if (dir > 360) {
                    dir -= 360;
                }
            } else if (i == 2) {
                dir -= 20;
                if (dir < 0) {
                    dir += 360;
                }
            }

            float[] hw = rotateDir(i + 1.5f, 0, dir, null);
            int dh = Math.round(hw[0]);
            dh *= -1;//h-indexing is REVERSED: geog.up is actually 'down' in terms of indexing
            int dw = Math.round(hw[1]);

            double eleDiff = 0;
            double dist = Math.sqrt(dh * dh + dw * dw) * elevation.dlatInMeters * 2;

            try {

                double f1 = elevation.getValueAtIndex(h - dh, w - dw);
                double f2 = elevation.getValueAtIndex(h + dh, w + dw);
                if (f1 < 1 || f2 < 1) {
                    return 0;//cannot be trusted near coastline
                }
                eleDiff = f1 - f2;

            } catch (IndexOutOfBoundsException e) {

            }

            inclination += 100.0 / max * (eleDiff) / (dist);//add averaged
        }

        if (inclination > 40 || inclination < -40) {
            inclination = 0;//this cannot be real
        }
        if (inclination > 20) {
            inclination = 20;
        }
        if (inclination < -20) {
            inclination = -20;
        }
        //if (!rp.oneway) inclination = Math.abs(inclination);
        rp.inclinationAngle = (byte) inclination;
        return inclination;
    }

    public static boolean isBuilding(byte b) {
        // return b == OsmLayer.DESC_BUILDING;
        return Building.isBuilding(b);
    }

    public static boolean isHighway(byte b) {
        //return b >= OsmLayer.DESC_TIER1;
        return Tags.isHighway(b);
    }

    public static ArrayList<Integer> getWayKeys(ArrayList<Integer> arr, OsmLayer ol, int IDint) {

        arr.clear();

        for (RoadPiece rp : ol.all_rps().values()) {
            if (rp.ref_number_int == IDint) {
                Integer ref = rp.ref_number_int;

                for (int h = 0; h < ol.H; h++) {
                    for (int w = 0; w < ol.W; w++) {

                        RoadPiece rp2 = ol.getRoadPiece(h, w);
                        if (rp2 != null && rp2.ref_number_int == ref) {
                            int key = OsmLayer.getSpatialHashKey(h, w);
                            arr.add(key);
                        }

                    }
                }

            }//if name match

        }
        return arr;
    }

    public static ArrayList<Integer> getWayKeys(ArrayList<Integer> arr, OsmLayer ol, String name, boolean all) {

        arr.clear();

        for (RoadPiece rp : ol.all_rps().values()) {
            if (rp.name.equals(name)) {
                Integer ref = rp.ref_number_int;

                for (int h = 0; h < ol.H; h++) {
                    for (int w = 0; w < ol.W; w++) {

                        RoadPiece rp2 = ol.getRoadPiece(h, w);
                        if (rp2 != null && rp2.ref_number_int == ref) {
                            int key = OsmLayer.getSpatialHashKey(h, w);
                            arr.add(key);
                        }

                    }
                }
                if (!all) {
                    break;
                }
            }//if name match

        }
        return arr;
    }

    public static void printoutRoads(OsmLayer ol) {
        int k = 0;

        HashMap<Long, Boolean> printed = new HashMap<>();
        HashMap<Integer, RoadPiece> rps = ol.allRPs();

        for (Integer key : rps.keySet()) {

            RoadPiece rp = rps.get(key);

            if (printed.get(rp.ref_number) != null) {
                continue;
            }
            printed.put(rp.ref_number, Boolean.TRUE);
            k++;

            EnfuserLogger.log(Level.FINER,OSMget.class,"Highway " + rp.ref_number + " (" + k + ")");
            EnfuserLogger.log(Level.FINER,OSMget.class,"\t speed = " + rp.speedLimit + ", tier = " + rp.tier());
            EnfuserLogger.log(Level.FINER,OSMget.class,"\t\t " + rp.name);
            EnfuserLogger.log(Level.FINER,OSMget.class,"\t\t\t buslines = " + rp.buslines);

        }

    }

    public static ArrayList<float[]> getTrafficLights(OsmLayer ol) {
        ArrayList<float[]> arr = new ArrayList<>();
        HashMap<Integer, RoadPiece> rps = ol.allRPs();
        for (RoadPiece rp : rps.values()) {
            if (rp.trafficLight_latlons != null) {
                for (float[] latlon : rp.trafficLight_latlons) {
                    arr.add(latlon);
                }
            }
        }
        return arr;
    }




    public void setPresetSpeedhash(String filename) {
        ArrayList<String> arr = FileOps.readStringArrayFromFile(filename);
        int adds = 0;
        for (String s : arr) {
            String[] split = s.split(";");
            try {
                String name = split[0];
                Integer sp = Integer.valueOf(split[1]);
                RoadPiece.GIVEN_SPEEDS.put(name, sp);
                adds++;
            } catch (Exception e) {

            }
        }
        EnfuserLogger.log(Level.FINER,OSMget.class,"Added " + adds 
                + " custom speed definitions from " + filename);
    }

    /**
     * Return a complete mapping of spatial RoadPiece elements, for each road
     * separately.
     *
     * @param ol OsmLayer under processing.
     * @return a hash of all spatial keys that point to RoadPieces. These keys
     * can be converted into (h,w) for the OsmLayer with OsmLayer.decode...
     *
     */
    public static HashMap<Integer, ArrayList<Integer>> allRoadKeys_int(OsmLayer ol) {
        EnfuserLogger.log(Level.FINER,OSMget.class,"OSMget: get all RoadPiece keys...");
        HashMap<Integer, ArrayList<Integer>> hash = new HashMap<>();

        for (int h = 0; h < ol.H; h++) {
            if (h % 100 == 0) {
                EnfuserLogger.log(Level.FINER,OSMget.class,"h = " + h);
            }
            for (int w = 0; w < ol.W; w++) {

                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp != null) {
                    Integer hwk = OsmLayer.getSpatialHashKey(h, w);

                    ArrayList<Integer> arr = hash.get(rp.ref_number_int);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        arr.add(hwk);
                        hash.put(rp.ref_number_int, arr);
                    } else {
                        arr.add(hwk);
                    }

                }//if rp
            }//for w
        }//for h

        return hash;
    }

}
