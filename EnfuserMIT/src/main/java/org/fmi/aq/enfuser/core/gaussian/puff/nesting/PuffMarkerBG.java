/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff.nesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.fmi.aq.enfuser.core.gaussian.puff.Injector;
import static org.fmi.aq.enfuser.core.gaussian.puff.Injector.getBgQ;
import org.fmi.aq.enfuser.core.gaussian.puff.MarkerCarrier;
import org.fmi.aq.enfuser.core.gaussian.puff.PPFcontainer;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.meteorology.Met;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 * This implementation for regional nesting uses puff markers to handle
 * the long range transportation of pollutants. The markers are released
 * in a 'frame' around the modelling area, which makes it possible to
 * avoid double-counting concentrations in the modelling area itself.
 * @author johanssl
 */
public class PuffMarkerBG extends RegionalBG{
    
    private final static int BG_SPARSER = 2;
    final int CAT_BG;
    final VarAssociations VARA;
    ArrayList<float[]> bgFraming = new ArrayList<>();
    public float bgCellRes_m = 1500;
    private float bgTimer = 0f;

    private float[][][] bg_HWq = null;
    int W,H;
    boolean bgAddStep = false;
    
    public PuffMarkerBG(FusionOptions ops) {
        
        CAT_BG = Categories.CAT_BG;
        this.VARA = ops.VARA;
    }
    
    /**
     * Manage release frequencies during the modelling.
     * @param current
     * @param pf 
     */
    @Override
    public void updateTimer( Dtime current, PuffPlatform pf) {
        bgAddStep = this.updateBGtimer( current, pf, pf.timeTick_s);
    }
    
    /**
     * Set up the frame around the modelling area for marker releases.
     * @param pf 
     */
    @Override
    public void init(PuffPlatform pf) {
        this.W = pf.in.H;
        this.H =pf.in.W;
        this.initBgFraming(pf); 
    }
    
    /**
     * Make an Kriging interpolated dataset out from the markers currently
     * present in the modelling area.
     * @param pf
     * @param dt
     * @param consContainer 
     */
    @Override
    public void addRegionalBackground(PuffPlatform pf, Dtime dt,
            PPFcontainer consContainer) {
        
        //init the 3-dim grid
        long t = System.currentTimeMillis();
        bg_HWq = this.getBackgroundLayer(pf);//will be utilized in computeCons_mt
        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"Background fill took " + (t2 - t) + "ms");
        
        for (int h = 0; h < pf.in.H; h++) {
            for (int w = 0; w < pf.in.W; w++) {
               float[] bg_dat = this.getBGarray(h, w);//has Q dimension
               consContainer.setQvalues(CAT_BG,VARA, bg_dat,h, w, dt);
            }
        }                   
    }
    
        private float[] getBGarray(int true_h, int w) {
        //check background availability and take datapoints from there
        if (this.bg_HWq != null) {
            int bg_h = true_h / BG_SPARSER;
            if (bg_h > this.bg_HWq.length - 1) {
                bg_h = this.bg_HWq.length - 1;//OOB always possible
            }
            int bg_w = w / BG_SPARSER;
            if (bg_w > this.bg_HWq[0].length - 1) {
                bg_w = this.bg_HWq[0].length - 1; //OOB always possible
            }
            return this.bg_HWq[bg_h][bg_w];
        }
        return null;
    }

        private void initBgFraming(PuffPlatform athis) {
            FusionCore ens = athis.ens;
           
        if (ens.ops.orthogonalBg) {
            return;//BalloonCarrier BG not used (instead, a post-processed grid data IS used)
        }        //check bg dat availability first
        float[] latlon = {(float) athis.in.bounds.getMidLat(), (float) athis.in.bounds.getMidLon()};
        Dtime dt= athis.getDt();
        MarkerCarrier loon = Injector.getBgQ(athis, ens, dt, latlon, true);
        if (loon == null) {
            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"Background puffs not available! init time = " + dt.getStringDate());
        }

        float dlat_bg = (float) (this.bgCellRes_m / degreeInMeters);
        float dlon_bg = (float) (this.bgCellRes_m / degreeInMeters / athis.in.coslat);

        //boundaries with some safezone padding
        float latmin = (float) athis.b_expanded.latmin;
        float latmax = (float) athis.b_expanded.latmax;
        float lonmin = (float) athis.b_expanded.lonmin;
        float lonmax = (float) athis.b_expanded.lonmax;

        for (float lat = latmin; lat <= latmax; lat += dlat_bg) {

            this.bgFraming.add(new float[]{lat, lonmin}); //left frame
            this.bgFraming.add(new float[]{lat, lonmax}); //right frame

        }

        for (float lon = lonmin + dlon_bg; lon <= lonmax - dlon_bg; lon += dlon_bg) {

            this.bgFraming.add(new float[]{latmin, lon}); //lower frame (without the corner)
            this.bgFraming.add(new float[]{latmax, lon}); //upper frame (without the corner)

        }

        EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"There are " + this.bgFraming.size() + " bg framing points in total.");
    }
        
    /**
     * Get marker puffs currently present in the stack.
     * @param athis
     * @return 
     */
    private ArrayList<MarkerCarrier> getSortedMarkers(PuffPlatform athis) {

        ArrayList<MarkerCarrier> bgps = new ArrayList<>();
        for (PuffCarrier p : athis.instances) {
            if (p instanceof MarkerCarrier) {
                MarkerCarrier pi = (MarkerCarrier) p;
                bgps.add(pi);
            }
        }

        Collections.sort(bgps, new BgiComparator());
        return bgps;
    }
    
    /**
     * Form the interpolated background layer based on the markers.
     * @param athis
     * @return 
     */
    private float[][][] getBackgroundLayer(PuffPlatform athis) {
        FusionCore ens = athis.ens;
        float res_m = athis.in.res_m;
        Dtime dt = athis.getDt();
        if (!this.bgFraming.isEmpty()) {

            ArrayList<MarkerCarrier> bgps = getSortedMarkers(athis);
            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"Got " + bgps.size() + "sorted bg instances");
            if (!bgps.isEmpty()) {
                EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"first element (sysSec): " + bgps.get(0).init.systemSeconds());
                EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"last element (sysSec): " + bgps.get(bgps.size() - 1).init.systemSeconds());
            }
            float[][][] dat_YXq = new float[H / BG_SPARSER][W / BG_SPARSER][ens.ops.VARA.Q_NAMES.length];
            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"BG grid res = " + dat_YXq.length + " x " + dat_YXq[0].length);

            boolean[][] filled = new boolean[H / BG_SPARSER / 2][W / BG_SPARSER / 2];//false by default, note that this grid is even coarser! (less krigs)
            ArrayList<int[]> krigs = new ArrayList<>();

            for (int k = 0; k < bgps.size(); k++) {
                MarkerCarrier pi = bgps.get(k);
                int y_pi = H - (int) (pi.HYX[1] / res_m);
                int x_pi = (int) (pi.HYX[2] / res_m);

                y_pi /= BG_SPARSER;
                x_pi /= BG_SPARSER;

                //index for filled[][]
                int y_f = y_pi / 2;
                int x_f = x_pi / 2;
                //oob check
                if (y_f< 0 || y_f > filled.length-1) continue;
                if (x_f< 0 || x_f > filled[0].length-1) continue;

                    if (filled[y_f][x_f]) {
                        continue;//already filled and the next one is OLDER anyways. Since the arrayList was sorted by time.
                    }                        //new 
                    filled[y_f][x_f] = true;
                    krigs.add(new int[]{y_pi, x_pi, k});

            }//for balloons        

            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"Kriging fill points: " + krigs.size());

            //fill found values
            float[] weights = new float[krigs.size()];
            int currentHours = dt.systemHours;
            for (int y = 0; y < H / BG_SPARSER; y++) {
                for (int x = 0; x < W / BG_SPARSER; x++) {

                    float wSum = 0;//cumulate this ocer all krigs
                    boolean brk = false;
                    for (int k = 0; k < krigs.size(); k++) {//iterate all krigs
                        int[] kr = krigs.get(k);
                        int y2 = kr[0];
                        int x2 = kr[1];

                        float dist = (float) Math.sqrt((y - y2) * (y - y2) + (x - x2) * (x - x2));
                        if (dist < 2) {
                            dist = 2f;//will smoothen the outcome.
                        }

                        //add some temporal distance
                        MarkerCarrier ip = bgps.get(kr[2]);
                        int tDist = currentHours - ip.init.systemHours - 5;
                        if (tDist > 0) {
                            dist += tDist;//one hour equals one cell length.
                        }
                        float w = 1f / (dist * dist);//IWD
                        weights[k] = w;
                        wSum += w;//cumulate the weight (for normalization)

                    }//for k
                    if (brk) {
                        continue;// only with testing output this can occur
                    }
                    //ready to fill - go over the list again
                    for (int k = 0; k < krigs.size(); k++) {
                        int[] kr = krigs.get(k);
                        MarkerCarrier ip = bgps.get(kr[2]);

                        float w = weights[k] / wSum;
                        for (int i = 0; i < ip.Q.length; i++) {
                            dat_YXq[y][x][i] += ip.Q[i] * w;
                        }//for q
                    }//for krigs

                }//for x
            }//for y

            return dat_YXq;

        }//if bg exists  
        return null;

    }      
      
    /**
     * Manage release rates and on/off switch for creating markers during
     * injection phase.
     * @param current
     * @param athis
     * @param timeTick_s
     * @return 
     */
     public boolean updateBGtimer( Dtime current,
             PuffPlatform athis, float timeTick_s) {
        //check effective travel distance at center:
        FusionCore ens = athis.ens;
        Met met = ens.hashMet.getStoreMet(current, athis.in.bounds.getMidLat(),
                athis.in.bounds.getMidLon(), ens);
        float u = met.getWindSpeed_raw();
        if (u < ens.ops.MIN_WINDSPEED) {
            u = ens.ops.MIN_WINDSPEED;
        }
        
        float travelTime_s = this.bgCellRes_m / u;

        this.bgTimer += timeTick_s;
        boolean bgAdd = false;
        if (bgTimer > travelTime_s) {
            bgAdd = true;//now it's the time to launch new group of background puff balloons!
            bgTimer = 0;
            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"===/=/=/bgTimer reset!");
            
        }

            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"Effective step time for bg is " 
                    + (int) travelTime_s + "s, speed = " + u);
            EnfuserLogger.log(Level.FINER,PuffMarkerBG.class,"\t current bgAdd timer = " + this.bgTimer);
        
        return bgAdd;
    }   
     
    /**
     * Add markers to array during injection phase.
     * @param pf
     * @param last
     * @param arr
     * @return 
     */ 
    @Override
    public int addMarkersToArray(PuffPlatform pf, Dtime last, ArrayList<PuffCarrier> arr) {
        int adds =0;
        if (!bgFraming.isEmpty() && this.bgAddStep) {
            for (float[] latlon : bgFraming) {
                MarkerCarrier bgp = getBgQ(pf, pf.ens, last, latlon, false);
                if (bgp != null) {
                    arr.add(bgp);
                    adds++;
                }
            }//for frame points

        }//if adding bg  
        return adds;
    }

 public class BgiComparator implements Comparator<MarkerCarrier> {

    @Override
    public int compare(MarkerCarrier e1, MarkerCarrier e2) {
        return (int) (e2.init.systemSeconds() - e1.init.systemSeconds());
        //smallest sysSec in this case will be the LAST element
        // => the newest instance (has largest sysSec) will be the FIRST element

        //usage example:    
        //Collections.sort(messages, new MessageTimeComparator());
    }

}   
    
    
}
