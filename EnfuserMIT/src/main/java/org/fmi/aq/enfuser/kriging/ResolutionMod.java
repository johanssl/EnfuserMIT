/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.kriging;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLat;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLon;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;

/**
 *
 * @author johanssl
 */
public class ResolutionMod {

    public static void increaseRes(String key, float factor, OsmLayer ol, boolean moreKrig) {
        ByteGeoGrid bg = ol.getByteGeo(key);
        if (bg == null) {
            EnfuserLogger.log(Level.WARNING,ResolutionMod.class,
                  "Target grid is NULL, cannot do numeric modifications.");
            return;
        }
        GeoGrid g = bg.convert();

        EnfuserLogger.log(Level.FINER,ResolutionMod.class,"Enhancing resolution! factor = " + 2);
        EnfuserLogger.log(Level.FINER,ResolutionMod.class,"More kriging points = " + moreKrig);
        int rad = 1;
        if (moreKrig) {
            rad = 2;
        }
        GeoGrid g2 = increaseResolution_v2(rad, true, factor, g, true);
        EnfuserLogger.log(Level.FINER,ResolutionMod.class,"done!");
        ByteGeoGrid bg2 = new ByteGeoGrid(g2.values, Dtime.getSystemDate_utc(false, Dtime.ALL), bg.gridBounds);
        ol.put(key, bg2);
    }

    public static GeoGrid cropToSquareShapeCells(boolean print, GeoGrid gg,
            double threshRatio, boolean reduceRes) {

        //check aspect ratio
        double hRatio_tow = gg.dlatInMeters / gg.dlonInMeters;
        if (hRatio_tow > threshRatio || hRatio_tow < 1.0 / threshRatio) {
            if (print) {
                EnfuserLogger.log(Level.FINER,ResolutionMod.class,"GridEnhancer: "
                        + "ratio is not close to unity: " + hRatio_tow + " => crop to square cell shape.");
            }
        } else {
            return gg;
        }

        int nH, nW;

        if (reduceRes) {//REDUCE resolution - crop
            if (hRatio_tow > 1) {
                //dlon_m is SMALLER, and therefore W is over-represented.
                nH = gg.H;
                nW = (int) (gg.W / hRatio_tow);
                if (nW == 0) {
                    nW = 1;
                }
                if (print) {
                    EnfuserLogger.log(Level.FINER,ResolutionMod.class,
                            "\tW cropped from " + gg.W + " to " + nW);
                }
            } else {
                //dlon_m is LARGER, and therefore H is over-represented.
                nW = gg.W;
                nH = (int) (gg.H * hRatio_tow);
                if (nH == 0) {
                    nH = 0;
                }
                if (print) {
                    EnfuserLogger.log(Level.FINER,ResolutionMod.class,
                            "\tH cropped from " + gg.H + " to " + nH);
                }
            }

        } else {//INCREASE resolution

            if (hRatio_tow > 1) {
                //dlon_m is SMALLER, and therefore H can be enhanced.
                nW = gg.W;
                nH = (int) (gg.H * hRatio_tow);
                if (print) {
                    EnfuserLogger.log(Level.FINER,ResolutionMod.class,"\tH increased from " + gg.H + " to " + nH);
                }
            } else {
                //dlon_m is LARGER, and therefore W can be enhanced.
                nH = gg.H;
                nW = (int) (gg.W / hRatio_tow);
                if (print) {
                    EnfuserLogger.log(Level.FINER,ResolutionMod.class,"\tW increased from " + gg.W + " to " + nW);
                }
            }

        }
        if (nH < 1 || nW < 1) {
            String msg =("Enhancer: negative or zero grid size!" + nH + " x " + nW)
            +("\tH changed" + gg.H + " to " + nH)
            +("\tW changed " + gg.W + " to " + nW)
            +("\tGridEnhancer: ratio is not close to unity: " + hRatio_tow + " => crop to square cell shape.");
            EnfuserLogger.log(Level.WARNING,ResolutionMod.class,msg);
            return gg;

        }
        float[][] ndat = new float[nH][nW];
        Boundaries b = gg.gridBounds.clone();
        GeoGrid g = new GeoGrid(ndat, gg.dt.clone(), b);

        for (int h = 0; h < nH; h++) {
            for (int w = 0; w < nW; w++) {

                double lat = getLat(b, h, g.dlat);
                double lon = getLon(b, w, g.dlon);//this is the origin point for IWD-algo. 
                //take the value from previous grid
                float val = (float) gg.getValue_closest(lat, lon);
                g.values[h][w] = val;

            }//for w
        }//for h
        return g;
    }

    public final static float KRIG_FILLER_EMPTY = -200.12345f;

    public static void fillKriging(boolean skipNonEmpty, GeoGrid g, int rad, float emptyVal) {

        ArrayList<float[]> arr = new ArrayList<>();
        Boundaries b = g.gridBounds;
        float[][] ndat = new float[g.H][g.W];
        int fills = 0;
        for (int h = 0; h < g.H; h++) {
            for (int w = 0; w < g.W; w++) {

                if (skipNonEmpty && g.values[h][w] != emptyVal) {
                    continue;//do nothing
                }
                double lat = getLat(b, h, g.dlat);
                double lon = getLon(b, w, g.dlon);//this is the origin point for IWD-algo. 

                arr = getKrigingPoints(rad, lat, lon, g, arr, emptyVal, true);
                if (arr.isEmpty()) {
                    ndat[h][w] = KRIG_FILLER_EMPTY;
                    continue;
                }
                fills++;
                float val = kriging_v2(1.5, arr, (float) lat, (float) lon, g);
                ndat[h][w] = val;

            }//for w
        }//for h

        EnfuserLogger.log(Level.FINER,ResolutionMod.class,
                "Empty filler with Kriging: " + fills + " fills.");
        //add data
        for (int h = 0; h < g.H; h++) {
            for (int w = 0; w < g.W; w++) {

                if (skipNonEmpty && g.values[h][w] != emptyVal) {
                    continue;//do nothing
                }
                g.values[h][w] = ndat[h][w];

            }//for w
        }//for h

    }

    public static GeoGrid increaseResolution_v2(int rad, boolean toSquares,
            float factor, GeoGrid g_prev, boolean print) {
        int H = (int) (g_prev.H * factor);
        int W = (int) (g_prev.W * factor);

        if (toSquares) {
            double hRatio_tow = g_prev.dlatInMeters / g_prev.dlonInMeters;
            if (hRatio_tow < 1.0 - SQUARE_THRESH) { //this means that W has LOWER resolution (more meters per cell) and should be increased
                W = (int) (W / hRatio_tow);//now it's larger
                if (print) {
                    EnfuserLogger.log(Level.FINER,ResolutionMod.class,"Enhancer.increaseRes_v2: W dim increased (squareing) "
                            + g_prev.W + " => " + (int) (g_prev.W * factor) + " => " + W);
                }
            } else if (hRatio_tow > 1.0 + SQUARE_THRESH) {//this means that H has LOWER resolution (more meters per cell) and should be increased
                H = (int) (H * hRatio_tow);//now it's larger 
                if (print) {
                    EnfuserLogger.log(Level.FINER,ResolutionMod.class,"Enhancer.increaseRes_v2: H dim increased (squareing) "
                            + g_prev.H + " => " + (int) (g_prev.H * factor) + " => " + H);
                }
            }
        }
        Boundaries b = g_prev.gridBounds.clone();
        float[][] ndat = new float[H][W];
        GeoGrid g = new GeoGrid(ndat, g_prev.dt.clone(), b);
        if (print) {
            EnfuserLogger.log(Level.FINER,ResolutionMod.class,"Enhancer.increaseRes_v2: kriging from  " + g_prev.H + "x "
                    + g_prev.W + " to " + H + " x " + W);
        }
        ArrayList<float[]> arr = new ArrayList<>();

        //ArrayList<double[]> valueWeights = new ArrayList<>();
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                double lat = getLat(b, h, g.dlat);
                double lon = getLon(b, w, g.dlon);//this is the origin point for IWD-algo. 

                arr = getKrigingPoints(rad, lat, lon, g_prev, arr, 0, false);
                //arr should never be empty! Can have up to 9 points in 3 x 3 form around the point

                float val = kriging_v2(0.6, arr, (float) lat, (float) lon, g_prev);
                g.values[h][w] = val;

            }//for w
        }//for h

        return g;
    }
    
    
        public static GeoGrid kriginSmooth_MT(int rad, boolean toSquares,
            float factor, GeoGrid g_prev) {
        int H = (int) (g_prev.H * factor);
        int W = (int) (g_prev.W * factor);

        if (toSquares) {
            double hRatio_tow = g_prev.dlatInMeters / g_prev.dlonInMeters;
            if (hRatio_tow < 1.0 - SQUARE_THRESH) { //this means that W has LOWER resolution (more meters per cell) and should be increased
                W = (int) (W / hRatio_tow);//now it's larger

            } else if (hRatio_tow > 1.0 + SQUARE_THRESH) {//this means that H has LOWER resolution (more meters per cell) and should be increased
                H = (int) (H * hRatio_tow);//now it's larger 
            }
        }
        Boundaries b = g_prev.gridBounds.clone();
        GeoGrid g = new GeoGrid(new float[H][W], g_prev.dt.clone(), b);
        ArrayList<Integer>[] buckets = new ArrayList[10];
        for (int i =0;i<buckets.length;i++) {
            buckets[i]=new ArrayList<>();
        }
        int buck =0;
        for (int h = 0; h < H; h++) {
            buckets[buck].add(h);
            buck++;
            if (buck>= buckets.length) buck =0;
        }//for h
        
        //create threads
        Threader.reviveExecutor();
        ArrayList<FutureTask> futs = new ArrayList<>();
        for ( ArrayList<Integer>hs:buckets) {
            Smoother cal = new Smoother(g,g_prev,hs,rad);
            FutureTask<Boolean> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);
        }
        // MANAGE callables           
        boolean notReady = true;
        while (notReady) {//

            notReady = false; // assume that ready 
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } 
            }
          EnfuserLogger.sleep(50,ResolutionMod.class);
        }// while not ready 
        Threader.executor.shutdown();
        
        return g;
    }
   public static class Smoother implements Callable<Boolean>{
        ArrayList<Integer> myH = new ArrayList<>();
        GeoGrid g;
        GeoGrid g_prev;
        final double distMin2;
        int rad;
        RadiusList rl;
        
        public Smoother(GeoGrid g, GeoGrid g_prev, ArrayList<Integer> myH, int rad) {
            this.g = g;
            this.g_prev = g_prev;
            this.myH = myH;
            rl = RadiusList.withFixedRads(rad, (float)g_prev.dlatInMeters, g.gridBounds.getMidLat());
            double distAdd = 0.25f*(float)g_prev.dlatInMeters;
            this.distMin2 = distAdd*distAdd;
        }
        @Override
        public Boolean call() throws Exception {
            
            for (int h:myH) {
                for (int w = 0; w < g.W; w++) {
                    //fine coordinates
                    double lat = g.getLatitude(h);
                    double lon = g.getLongitude(w);
                    
                    //crude set coordinates
                    int oh = g_prev.getH(lat);
                    int ow = g_prev.getW(lon);
                    double olat = g_prev.getLatitude(oh);
                    double olon = g_prev.getLongitude(ow);
                    
                    float totalW =0;
                    for (RadListElement e:rl.elems) {
                        int ch = oh+e.hAdd;
                        int cw = ow+e.wAdd;
                        if (g_prev.oobs_hw(ch, cw)) continue;
                        
                        double nowLat = olat - e.hAdd*g_prev.dlat;
                            double latdist_m = (lat-nowLat)*degreeInMeters;
                        double nowLon = olon + e.wAdd*g_prev.dlon;
                            double londist_m = (lon-nowLon)*degreeInMeters*rl.coslat;

                        double dist2 = latdist_m*latdist_m + londist_m*londist_m;
                        //if (dist2< distMin2) dist2 = distMin2;
                        dist2+=distMin2;
                        double weight = 1.0/dist2;
                        float orig = g_prev.values[ch][cw];
                        
                        g.values[h][w]+=orig*weight;
                        totalW+=weight;
                    }//for elements
                    //normalize
                    g.values[h][w]*=(1f/totalW);

            }//for w
            }//for h
            return true;
        }
       
   }     
        

    public static double SQUARE_THRESH = 0.15;

    public static GeoGrid increaseResolution(boolean toSquares, float factor,
            GeoGrid g_prev, boolean moreKrig) {
        int H = (int) (g_prev.H * factor);
        int W = (int) (g_prev.W * factor);

        if (toSquares) {
            double hRatio_tow = g_prev.dlatInMeters / g_prev.dlonInMeters;
            if (hRatio_tow < 1.0 - SQUARE_THRESH) { //this means that W has LOWER resolution (more meters per cell) and should be increased
                W = (int) (W / hRatio_tow);//now it's larger
            } else if (hRatio_tow > 1.0 + SQUARE_THRESH) {//this means that H has LOWER resolution (more meters per cell) and should be increased
                H = (int) (H * hRatio_tow);//now it's larger 
            }
        }
        Boundaries b = g_prev.gridBounds.clone();
        float[][] ndat = new float[H][W];
        GeoGrid g = new GeoGrid(ndat, g_prev.dt.clone(), b);

        //ArrayList<int[]> ij = new ArrayList<>();
        //   ij.add(new int[]{-1,-1}); ij.add(0); Is.add(1);
        int rad = 1;
        if (moreKrig) {
            rad = 2;
        }

        ArrayList<double[]> latlons = new ArrayList<>();
        //ArrayList<double[]> valueWeights = new ArrayList<>();
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                latlons.clear();
                double lat = getLat(b, h, g.dlat);
                double lon = getLon(b, w, g.dlon);//this is the origin point for IWD-algo. 

                for (int i = -rad; i <= rad; i++) {//take nine coordinate points
                    for (int j = -rad; j <= rad; j++) {

                        double clat = lat + i * g_prev.dlat;
                        double clon = lon + j * g_prev.dlon;
                        if (i == 0 && j == 0) {//we take the coordinates from the coarser grid!
                            clat = lat + 0.5 * g_prev.dlat;
                            clon = lon + 0.5 * g_prev.dlon;
                        }

                        latlons.add(new double[]{clat, clon});
                    }
                }

                float val = kriging(0.4, latlons, g_prev, lat, lon);
                g.values[h][w] = val;

            }//for w
        }//for h

        return g;
    }

    public static ArrayList<float[]> getKrigingPoints(int rad, double lat,
            double lon, GeoGrid g, ArrayList<float[]> arr, float emptyVal, boolean emptySkip) {
        arr.clear();
        //get hw index from original grid
        int h = getH(g.gridBounds, lat, g.dlat);
        int w = getW(g.gridBounds, lon, g.dlon);

        for (int i = -rad; i <= rad; i++) {
            for (int j = -rad; j <= rad; j++) {
                int ch = h + i;
                int cw = w + j;
                if (ch < 0 || ch > g.H-1) continue;
                if (cw < 0 || cw > g.W-1) continue;

                    float value = g.values[ch][cw];
                    if (emptySkip && value == emptyVal) {
                        continue;
                    }
                    double clat = getLat(g.gridBounds, ch, g.dlat);
                    clat += g.dlat * 0.5;//align with the center of this larger cell
                    double clon = getLon(g.gridBounds, cw, g.dlon);
                    clat += g.dlon * 0.5;//align with the center of this larger cell
                    float[] temp = {value, (float) clat, (float) clon, ch, cw};
                    arr.add(temp);

            }
        }

        return arr;
    }

    public static float kriging_v2(double distThresh, ArrayList<float[]> krigs, float lat, float lon, GeoGrid g) {
        if (distThresh < 0.0001) {
            distThresh = 0.0001;//must not be zero!
        }
        double dt2 = (distThresh * g.dlat) * (distThresh * g.dlat);// min distance squared
        float wSum = 0;
        float[][] weight_vals = new float[krigs.size()][2];

        for (int k = 0; k < krigs.size(); k++) {//iterate all krigs
            float[] kr = krigs.get(k);
            float value = kr[0];
            float clat = kr[1];
            float clon = kr[2];
            float y2 = (clat - lat) * (clat - lat);
            float x2 = (clon - lon) * (clon - lon) * (float) (g.lonScaler * g.lonScaler);

            float dist_degs2 = (float) (y2 + x2);// dist^2 at the moment
            if (dist_degs2 < dt2) {
                dist_degs2 = (float) dt2;//(float)(distThresh*g.dlat);//also, cannot be zero now
            }

            float w = 1f / dist_degs2;//IWD
            weight_vals[k][1] = value;
            weight_vals[k][0] = w;
            wSum += w;//cumulate the weight (for normalization)

        }//for k

        //ready to fill - go over the list again
        float finalValue = 0;
        for (int k = 0; k < krigs.size(); k++) {

            float w = weight_vals[k][0] / wSum;
            float val = weight_vals[k][1];
            finalValue += w * val;

        }//for krigs 
        return finalValue;
    }

    public static float kriging(double distPow, ArrayList<double[]> krigs, GeoGrid g_prev, double lat, double lon) {
        double lonScaler = g_prev.lonScaler;
        float wSum = 0;
        float[][] weight_vals = new float[krigs.size()][2];

        boolean added = false;

        for (int k = 0; k < krigs.size(); k++) {//iterate all krigs
            double[] kr = krigs.get(k);
            double clat = kr[0];
            double clon = kr[1];
            double y2 = 1000 * (clat - lat) * (clat - lat);
            double x2 = 1000 * (clon - lon) * (clon - lon) * lonScaler * lonScaler;

            float distSQ = (float) (y2 + x2) + 0.0001f;// dist^2 at the moment

            float w = 1f / (float) Math.pow(distSQ, distPow);//IWD

            Float val = g_prev.getValueAt_exSafe(clat, clon);
            if (val != null) {
                weight_vals[k][1] = val;
                added = true;
            } else {
                w = 0;
            }

            weight_vals[k][0] = w;
            wSum += w;//cumulate the weight (for normalization)

        }//for k

        if (!added) {
            return 0;//OOB!
        }
        //ready to fill - go over the list again
        float finalValue = 0;
        for (int k = 0; k < krigs.size(); k++) {

            float w = weight_vals[k][0] / wSum;
            float val = weight_vals[k][1];
            finalValue += w * val;

        }//for krigs 
        return finalValue;
    }

}
