/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.combiners;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.core.receptor.RPstack;
import org.fmi.aq.enfuser.core.receptor.EnvArray;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;

/**
 *
 * @author johanssl
 */
public class PartialGrid {


    public ArrayList<RPstack>[][] dcs;

    AreaInfo in;
    private final float krig_minDist_m;
    public final int[] profilingRange;
    public final int sparser;
    final float lonp2Scaler;
    final float distAdd_deg;

    public PartialGrid(int minRange_m, int maxRange_m, AreaInfo inf, FusionCore ens) {
        profilingRange = new int[]{minRange_m, maxRange_m};

        int sp = this.getMicroMacro_factor(inf.res_m, minRange_m);
        if (sp > ens.ops.partialC_maxFactor) {
            sparser = ens.ops.partialC_maxFactor;
        } else {
            sparser = sp;
        }

        int H = inf.H / sparser;
        int W = inf.W / sparser;
        this.in = new AreaInfo(inf.bounds, H, W, inf.dt, inf.types);//make a sparsed copy of the original domain.
        krig_minDist_m = this.in.res_m * 0.25f;
        this.lonp2Scaler = in.coslat * in.coslat;
        distAdd_deg = (float) (0.25f * in.dlat);
        this.dcs = new ArrayList[H][W];

    }

    public void multiThread_init(FusionCore ens) {

        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();
        ens.setProgressBar(0);

        EnfuserLogger.log(Level.FINER,PartialGrid.class,"PartialGrid: starting multithread operation for \n" 
                + in.shortDesc() + "\n with a sparsing factor of " + this.sparser);
        for (int h = 0; h < in.H; h++) {

            PartialCallable cal = new PartialCallable(h, in, ens, profilingRange);
            //it will take some time before all Callables are added as futures (thread completed)
            FutureTask<ArrayList<RPstack>> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);

        }

        // MANAGE callables           
        boolean notReady = true;
        while (notReady) {//

            notReady = false; // assume that ready 
            double ready_N = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } else {
                    ready_N++;
                }
            }
            int prog = (int) (100 * ready_N / futs.size());
            EnfuserLogger.log(Level.FINER,PartialGrid.class,prog + "% ready for Partial profiles (" 
                    + profilingRange[0] + "-" + profilingRange[1] + "m).");
             ens.setProgressBar(prog);
            
             EnfuserLogger.sleep(1000,PartialGrid.class);
        }// while not ready 

        //init the arrayList grid    
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                this.dcs[h][w] = new ArrayList<>();
            }
        }

        int totalDCs = 0;
        int adds = 0;
        int RAD = 1;

        for (FutureTask fut : futs) {
            try {
                // EnfuserLogger.log(Level.FINER,PartialGrid.class,"future "+i +" updating the grid..." );
                ArrayList<RPstack> arr = (ArrayList<RPstack>) (fut.get());
                for (RPstack dc : arr) {
                    totalDCs++;
                    int h = dc.ph;
                    int w = dc.pw;

                        for (int i = -RAD; i <= RAD; i++) {
                            for (int j = -RAD; j <= RAD; j++) {
   
                                adds+=addDC(dc,h+i,w+j);
                            }
                        }
                        //add a couple more
                        addDC(dc, h + 2,w);
                        addDC(dc, h - 2,w);
                        addDC(dc, h,w+2);
                        addDC(dc, h,w-2);
                }

            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();

            }
        }
        EnfuserLogger.log(Level.FINER,PartialGrid.class,"Partial Grid complete total cells = " + (in.H * in.W) 
                + ", processed DriverCategories = " + totalDCs + ", added to arrays = " + adds);
        //center debug
        int h = in.H / 2;
        int w = in.W / 2;
        ArrayList<RPstack> dct = this.dcs[h][w];
        
            EnfuserLogger.log(Level.FINER,PartialGrid.class,"Checking center array content: " + h + "/" 
                    + w + "___" + in.getLat(h) + ", " + in.getLon(w));
            for (RPstack test : dct) {
                EnfuserLogger.log(Level.FINER,PartialGrid.class,"\t " + test.ph + "/" + test.pw + "___" 
                        + test.p_lat + ", " + test.p_lon);
            }
        
        Threader.executor.shutdown();

    }
    
    private int addDC(RPstack dc, int h, int w) {
        if (h<0 || h>dcs.length-1) return 0;
        if (w<0 || w>dcs[0].length-1) return 0;
        
        dcs[h][w].add(dc);
        return 1;
    }

    public final int rad = 1;

    public void stack(RPstack dc, float lat, float lon) {

        //get hw index from original grid, do some OOB checking
        int h = in.getH(lat);
        if (h < 0) {
            h = 0;
        }
        if (h >= in.H) {
            h = in.H - 1;
        }
        int w = in.getW(lon);
        if (w < 0) {
            w = 0;
        }
        if (w >= in.W) {
            w = in.W - 1;
        }

        float sum = 0;
        ArrayList<RPstack> arr = this.dcs[h][w];
        float[] ws = new float[arr.size()];

        int j = -1;
        for (RPstack dct : arr) {
            j++;
            float dist_m = (float) Observation.getDistance_m(lat, lon, dct.p_lat, dct.p_lon);
            if (dist_m < this.krig_minDist_m) {
                dist_m = krig_minDist_m;
            }
            float weight = 1f / dist_m;
            ws[j] = weight;
            sum += weight;
        }

        j = -1;
        for (RPstack dct : arr) {
            j++;
            float weight = ws[j] / sum;
            dc.stackDrivers(dct, weight);
        }

    }

    /**
     * In AreaFusion this is the key defining parameter to ease the
     * computational burden by calculating LESS full profiles and MORE partial
     * small-scale ones. If the returned Integer is 5, then only once per 25
     * pixels a full Profile is being calculated.
     *
     * They key principle is that the higher the resolution, the less these full
     * profiles are needed.
     *
     * @param res_m size of one pixel (length) in meters.
     * @return
     */
    private int getMicroMacro_factor(double res_m, int minRad) {
        if (minRad == 0) {
            return 1;//level 1 grid, no sparse smoothing
        }
        double factor = 0.33;

        int test = (int) (factor * minRad / res_m); // e.g. 0.33*150/15m per pixel => 4.

        if (test < 2) {
            test = 2;// a value of 2 will result in a significant performance gain, but 1 does not benefit at all.
        }
        return test;
    }

    public class PartialCallable implements Callable<ArrayList<RPstack>> {

        final Dtime[] allDates;
        FusionCore ens;

        final int h;
        final double lat;

        final int[] range;
        AreaInfo in;

        public PartialCallable(int h, AreaInfo in, FusionCore ens, int[] range) {

            this.in = in;
            //this.time = time.clone();   
            this.allDates = ens.tzt.getDates(in.dt);
            this.ens = ens;
            this.h = h;
            this.range = range;

            lat = in.getLat(h);
        }

        @Override
        public ArrayList<RPstack> call() throws Exception {
            ArrayList<RPstack> arr = new ArrayList<>();

            for (int w = 0; w < in.W; w++) {
                double lon = in.getLon(w);
                Observation fob = Observation.AreaFusionPoint(lat, lon, in.dt, FusionCore.FUSION_HEIGHT_M,ens.ops);
                BlockAnalysis ba = ens.getBlockAnalysis(null, fob);
                EnvArray enva = PartialProfiler.profile_fastArea(range,  allDates, ens, fob, null,ba);

                //store loc info
                enva.dcat.ph = h;
                enva.dcat.pw = w;
                enva.dcat.p_lat = (float) lat;
                enva.dcat.p_lon = (float) lon;

                arr.add(enva.dcat);

            }//for w

            return arr;
        }

    }

}
