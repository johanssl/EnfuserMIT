/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.meteorology.RoughnessLength.RL_FOREST;
import static org.fmi.aq.enfuser.meteorology.RoughnessLength.RL_URB;
import static org.fmi.aq.enfuser.meteorology.RoughnessLength.getFastRoughnessLength;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.ftools.RotationCell;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;

/**
 *
 * @author johanssl
 */
public class RoughPack {
    
    public GeoGrid griddedRL;
    final AreaNfo in;
    //logwind profiles
    private float[][] logWindProfiles_h_RLI;
    public final static float LOGW_STORESCALER = 100f;//must be a large number

    private final static float zeroPlaneD = 0f;
    public static final float RL_DEFAULT = 0.06f;
    
    private final static float MIN_H =2;
    public final static float MAX_ROUGHN = 3f;
    private final static int MAX_RLI_IND = 3000;
    public static final float RLI_INC = MAX_ROUGHN / MAX_RLI_IND;
    
    ArrayList<RotationCell> ray;
    boolean directed = true;
    GeoGrid[] directedRL;
    private final static int SECTOR_DEG = 10;
    
    public RoughPack(AreaNfo in, FusionOptions ops, MapPack mp) {
        
        float[][] rough = new float[in.H][in.W];//follows PuffPlatform resolution
        OsmLayer ol =null;
        this.in = in;
        if (mp!=null) ol = mp.getOsmLayer( in.midlat(), in.midlon());
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {

                double lat = in.getLat(h);
                double lon = in.getLon(w);
                float rl = getFastRoughnessLength(in.res_m, lat, lon, ol, ops);
                rough[h][w] = rl;

            }//for x
        }//for y 
        
        this.griddedRL = new GeoGrid("roughness", rough, Dtime.getSystemDate(), in.bounds);
        initLogWinds();
        ray= FastMath.FM.getCustomRay(in.res_m, 1000f + in.res_m, 1);
        this.predirs();
          
    }
    
    private int dirIndex(int wdir) {
        return wdir/SECTOR_DEG;
    }
    
    private void predirs() {
        long t = System.currentTimeMillis();
        if (this.directed) {
            int H = in.H;
            int W = in.W;
            this.directedRL = new GeoGrid[360/SECTOR_DEG];
            for (int dir = 0;dir < 360;dir+=SECTOR_DEG) {
                int ind = this.dirIndex(dir);
                this.directedRL[ind] = new GeoGrid(new float[H][W],Dtime.getSystemDate(),this.griddedRL.gridBounds);
                for (int h = 0; h < H; h++) {
                    for (int w = 0; w < W; w++) {

                        double lat = in.getLat(h);
                        double lon = in.getLon(w);
                        float rl = this.computeDirectedRL((float)lat, (float)lon, dir);
                         this.directedRL[ind].values[h][w]=rl;
                    }//for w
                }//for h 
            }//for deg
            
        }//if pre-dir
        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO, "RoughPack directed RL computations took "+ (t2-t) +"ms");
    }
    
    
        public final static int MAXH = 200;

    private void initLogWinds() {
        EnfuserLogger.log(Level.FINER,FastMath.class,"Evaluating default logWinds for h1=10m...");
        logWindProfiles_h_RLI = new float[MAXH][MAX_RLI_IND];
        for (int h2 = 0; h2 < MAXH; h2++) {
            //if (h2%10 ==0) EnfuserLogger.log(Level.FINER,FastMath.class,"h2 = "+h2);
            for (int rl = 0; rl < MAX_RLI_IND; rl++) {
                float roughn = rl * RLI_INC;//min 0.001, max 3.001.

                float u2 = logWind(true, LOGW_STORESCALER, roughn, h2);
                logWindProfiles_h_RLI[h2][rl] = u2;
            }
        }
        EnfuserLogger.log(Level.FINER,FastMath.class,"logWinds completed.");
    }

    private float getPrecompLogWindProfile(float h2, float rough) {

        int rInd = (int) (rough / RLI_INC);
        if (rInd > MAX_RLI_IND - 1) {
            rInd = MAX_RLI_IND - 1;
        }
        int hInd = (int) Math.min(MAXH - 1, h2);
        return logWindProfiles_h_RLI[hInd][rInd] / LOGW_STORESCALER;

    }

    /**
     * This method estimates the wind profile with variable roughness length and
     * height, based on modelled coarse wind data (10m elevation is assumed).
     * Minimum windspeed (e.g. 1m/s) and height (2m) is limited to avoid the
     * Gaussian modelling from breaking.
     *
     * @param init if FALSE, then a precomputed value is taken from FastMath
     * class. For operational use: false.
     * @param u1 wind speed at default heigth [10m]
     * @param roughness roughness length
     * @param height_m2 profiling height [m]. Minimum value applies is 1m.
     * @return log-wind profiled value.
     */
    private float logWind(boolean init, float u1, float roughness, float height_m2) {

        float H1 = 10f;

        if (!init) {
            float u2 = getPrecompLogWindProfile(height_m2, roughness) * u1;
            return u2;
        }

        if (height_m2 < MIN_H) {
            height_m2 = MIN_H;
        }
        if (roughness < RLI_INC) {
            roughness = RLI_INC;
        }
        if (roughness > MAX_ROUGHN) {
            roughness = MAX_ROUGHN;
        }
        float log1 = (float) Math.log((H1 - zeroPlaneD) / RL_DEFAULT);
        float log2 = (float) Math.log((height_m2 - zeroPlaneD) / roughness);

        float u2 = u1 * log2 / log1;
        return u2;
    }
    
    /**
     * This is like the logWind -method but allows custom reference height
     * to be set and the given wind speed value is at this custom height.
     * Based on these inputs this then returns the profiled wind speed
     * at another height.
     * @param u_atCustom wind speed at H_custom
     * @param H_custom the custom reference height [m]
     * @param roughness roughness length used for both assessment heights
     * @param height_m2 the height [m] for the returned wind speed.
     * @return wind speed at height_m2.
     */
    public float customLogWind(float u_atCustom, float H_custom, float roughness, float height_m2) {

        if (height_m2 < MIN_H) {
            height_m2 = MIN_H;
        }
        if (H_custom < MIN_H) {
            H_custom = MIN_H;
        }
        
        if (roughness < RLI_INC) {
            roughness = RLI_INC;
        }
        if (roughness > MAX_ROUGHN) {
            roughness = MAX_ROUGHN;
        }
        float log1 = (float) Math.log((H_custom - zeroPlaneD) / roughness);
        float log2 = (float) Math.log((height_m2 - zeroPlaneD) / roughness);

        float u2 = u_atCustom * log2 / log1;
        return u2;
    }

    
    public float logWind_limited(float MIN_WINDSPEED, float u1,
            float roughness, float height_m2) {

        if (height_m2 < MIN_H) {
            height_m2 = MIN_H;
        }
        
        double maxRL = RL_URB - height_m2*0.05;//at height 20m this is 2. At height 40m this 1.
        if (maxRL <RL_FOREST) maxRL =RL_FOREST;
        if (roughness > maxRL) roughness = (float)maxRL;
        
        float u = logWind(false, u1, roughness, height_m2);
        if (u < MIN_WINDSPEED) {
            u = MIN_WINDSPEED;
        }

        return u;
    }
    
    public float getRoughness(float lat, float lon,FusionCore ens, int wdir) {
        float rl = RL_DEFAULT;
        GeoGrid g = this.griddedRL;
        
        if (this.directed) {
            int ind = this.dirIndex(wdir);
            g = this.directedRL[ind];
        }
        
        if (g.containsCoordinates(lat, lon)) {
             rl = (float) g.getValue_closest(lat, lon);
        }
        
        return rl;
    }
    
    private float computeDirectedRL(float lat, float lon, int wdir) {
        
    float rl=0;
    int n=0;
    float wsum =0;
    float N = this.ray.size();
    
        for (RotationCell e:this.ray) {
            int dir = directionShift(n,wdir);
            
            float weight = 1f - (float)n/N;//from 1.0 to zero as a function of distance.
            n++;
            float clat = e.currentLat_fast(lat,dir);
            float clon = e.currentLon_fast(lon, dir);
            if (this.griddedRL.containsCoordinates(clat, clon)) {
                rl += (float) this.griddedRL.getValue_closest(clat, clon)*weight;
                wsum+=weight;
           }
        }
        if (wsum==0) {
           rl = RL_DEFAULT;
        } else {
           rl/=wsum;
        }

        return rl;
    }
    
    private int directionShift(int n, int wdir) {
        int dir =wdir;
        if (n%3==0) {
            dir = wdir+3;
            
        } else if (n%4==0) {
            dir = wdir-3;
        }
        
        if (dir< 0) dir+=360;
        if (dir > 360) dir-=360;
        return dir;
    }
    
    public float logWind_limited(float lat, float lon,FusionCore ens, int wdir,
                float height_m2, float u1) {
       
        float rl = this.getRoughness(lat,lon,ens,wdir);
        return logWind_limited(ens.ops.MIN_WINDSPEED, u1, rl,  height_m2);
    }
    
    
    public float getProfiledWind(float hm, float lat, float lon, Dtime dt, FusionCore ens) {
       Met met = ens.getStoreMet(dt, lat, lon);
       float rl = this.getRoughness(lat,lon,ens,met.getWdir().intValue());
       return logWind_limited(ens.ops.MIN_WINDSPEED, met.getWindSpeed_raw(), rl, hm);
    }
    
    
    
    public static void metLogWindTest() {
        AreaNfo in = new AreaNfo(Boundaries.getDefault(),10,10,Dtime.getSystemDate());
        RoughPack rp = new RoughPack(in,null,null);
        float u1 = 6;
        float roughness = 0.14f;

        for (float h2 = 2; h2 < 30; h2++) {
            float u2 = rp.logWind(true, u1, roughness, h2);
            EnfuserLogger.log(Level.INFO,RoughPack.class,"h = " + h2 + ", v = " + u2);
        }

        //speed test
        int maxi = 10000000;
        float u2;

        long t = System.currentTimeMillis();
        float sum = 0;
        for (int i = 0; i < maxi; i++) {
            u2 = rp.logWind(true, u1, roughness, (float) i / maxi * 10);
            sum += u2;
        }

        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO,RoughPack.class,"Time taken: " + (t2 - t) + "ms, testave=" + (sum / maxi));

        //  if (false) {
        t = System.currentTimeMillis();
        sum = 0;
        for (int i = 0; i < maxi; i++) {
            u2 = rp.logWind(false, u1, roughness, (float) i / maxi * 10);
            sum += u2;
        }

        t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO,RoughPack.class,"Time taken with FPM: " + (t2 - t) + "ms, testave=" + (sum / maxi));
        // }

        for (float h2 = 2; h2 < 30; h2++) {
            u2 = rp.logWind(false, u1, roughness, h2);
            EnfuserLogger.log(Level.INFO,RoughPack.class,"h = " + h2 + ", v = " + u2);
        }

    }


    
}
