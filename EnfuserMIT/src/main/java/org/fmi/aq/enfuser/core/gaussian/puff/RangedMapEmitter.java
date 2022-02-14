/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.options.Categories.CAT_MISC;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.tasker.HashTask;
import static org.fmi.aq.enfuser.core.gaussian.fpm.Footprint.getIterationIndex;
import org.fmi.aq.enfuser.core.gaussian.fpm.SimpleAreaCell;
import static org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier.DZ_EXP;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class handles emission information aggregation for puff modelling for
 * EMITTERS THAT DO NOT OTHERWISE contribute to puff modelling. This means
 * that every road, every house that burns biomass will be taken into account
 * here for 'Zone 3'. Emitters such as point sources, or ships will not be taken into account.
 * 
 * The modelling area (expanded) will be represented with a collection of MapEmitterCells.
 * Each of these are e.g. 500x500m wide and hold all the emission release rates
 * for that particular area. Naturally, this grid of cells needs to be updated
 * from time to time.
 * 
 * One of the tasks for this class is to reduce the amount of active emitter cells.
 * This can discard all the cells for which the contribution is very minor.
 * 
 * For puff creation and injection, the PuffPlatform will call this class'
 * getPuffsUpdate method. This will automatically update the emitter cells
 * if needed and return a collection of puffs to be simulated.
 * 
 * @author Lasse Johansson
 */


public class RangedMapEmitter {


    private MapEmitterCell[][] mecs;
    public float updateSecs = Float.MAX_VALUE / 10f;//a very large value here causes
    //an update to occur at the first method call for puffs.
    FusionOptions ops;
    int mapPuffTick = 0;
    public boolean emitterSparser = true;

    public final int evalRadius_m;
    public final float puffGenIncreases;
    AreaInfo in;
    final RandSequencer rand;

    public RangedMapEmitter(Boundaries bounds, FusionCore ens, Dtime dt) {

        this.ops = ens.ops;
        //grid resolution is determined by RegionOptions
        float res_m = (int) ens.ops.perfOps.ppfMapPuffRes_m();
        in = new AreaInfo(bounds.clone(), res_m, dt, new ArrayList<>());
        
        evalRadius_m = (int) ens.ops.perfOps.puffPlumeSwitchThreshold_m();
        puffGenIncreases = 0.5f*ens.ops.perfOps.ppfMapPuff_genIncr();
        this.mecs= new MapEmitterCell[in.H][in.W];

        EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,
                "=====================================================================");
        EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,
                "FixedSourceMap for puff: H / W / res_m => " + in.H + " / " + in.W 
                        + "/ " + (int) in.res_m + "m");
        EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,
                "=====================================================================");
        ens.customCellArray = getCellArray((float) in.res_m);
        this.rand = new RandSequencer(37);
    }

    
    public String getStatusLogLine() {
        int n =(in.H*in.W);
        
        double pmRemoval_percent = 1000*this.pmRemovedRate/(this.pmRemainingRate + this.pmRemovedRate);
        String s = "RangedMapEmitter has "+ n +" cells. During update "
                + removed +" were omitted. (" + (100*removed/n) +"% removal). For PM2.5 the removal %% is "+(int)pmRemoval_percent+".";
        int total =0;
        int elevated =0;
        
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                if (mecs[h][w]!=null) {
                    total++;
                    if (mecs[h][w].hasElevated()) {
                        elevated++;
                    }
                }
            }//for w   
        }//for h
        s+= " Active cells: "+total +", of which "+elevated +" has elevated emission content.";
        return s;
    }
    
    //stats for removal process
    int removed =0;
    float pmRemovedRate =0;
    float pmRemainingRate =0;
    /**
     * Update the MapEmitterCells for the given time. Afterwards, remove
     * the least notable emitter cells to reduce the amount of active emitter
     * cells. This will be done based on the average emission rate: 
     * cell with significantly lower emission release rates than the average
     * will be omitted.
     * @param dt_utc the time
     * @param ens the core
     */
    public void updateMapSources(Dtime dt_utc, FusionCore ens) {

        removed =0;
        pmRemovedRate =0;
        pmRemainingRate =0;
        
        float[] rateAverages = new float[ens.ops.VARA.Q_NAMES.length];
        float aveNorm = 1f/(in.H * in.W);
        int NaNwarns =0;
        AreaInfo currInf = new AreaInfo(in.bounds, in.H, in.W, dt_utc, in.types);//this will carry the correct time to the evaluation (important)
            ens.ppfForcedAvailable(true);
        HashMap<String, Object> hash_hwMet = HashTask.multiThreadHash_EMPUFF(ens, currInf);
            ens.ppfForcedAvailable(false);
        for (String key : hash_hwMet.keySet()) {
            MapEmitterCell mec = (MapEmitterCell) hash_hwMet.get(key);//Object casted to Env.

            String[] split = key.split("_");
            int h = Integer.valueOf(split[0]);
            int w = Integer.valueOf(split[1]);
            if (mec.nanWarning!=null && NaNwarns < 10) {
                EnfuserLogger.log(Level.WARNING, this.getClass(), "NaN rangedMapEmitter cell at " 
                        + dt_utc.getStringDate_noTS() +", " + mec.nanWarning 
                        +", "+ in.getLat(h) +","+in.getLon(w));
                NaNwarns++;
            }
            
            if (!mec.hasContent()) {
                removed++;
                continue;
            }
            mec.setLocation(h,w,in);
            this.mecs[h][w]=mec;
           
            for (int q : ops.VARA.Q) {
               rateAverages[q]+= mec.typeRates_allElevations(q)*aveNorm;
            }
            
        }//for keys

        //removal step. Make a list of all, then remove the least useful ones.
        ArrayList<MapEmitterCell> arr = new ArrayList<>();
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                MapEmitterCell mec = this.mecs[h][w];
                if (mec == null) continue;
                mec.initContributionPercentages(rateAverages);
                arr.add(mec);
            }//for w
        }//for h
        
       
        Collections.sort(arr,new MECsorter());//smallest contributions are first
        int maxRems = (int)(0.8*(in.H * in.W));//there will be at least 20% cells remaining. However, all cells with zero rates are omitted.
        
        for (MapEmitterCell mec:arr) {
            if (mec.removableContribution() && removed < maxRems) {
                int h = mec.h;
                int w = mec.w;
                this.mecs[h][w]=null;
                removed++;
                pmRemovedRate+= mec.typeRates_allElevations(ens.ops.VARA.VAR_PM25);
            } else {
                pmRemainingRate+= mec.typeRates_allElevations(ens.ops.VARA.VAR_PM25);
            }
        }
        
         String msg = "average rates (nox,pm25,true_pm10, µg/s) = " 
                    + rateAverages[ops.VARA.VAR_NO2] + " / " + rateAverages[ops.VARA.VAR_PM25] 
                 + " / " + rateAverages[ops.VARA.VAR_COARSE_PM];
        EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,msg
                + "\nFixedSourceMap: non-zero "
                    + "sources AFTER minimal source removal: " + (in.H * in.W - removed) 
                + ", removed = " + removed );

    }

    /**
     * Setup the evaluation array for Env-objects, that covers a rectangular
     * area of selected grid cell. Choice of resolution: 10m which should be
     * sufficient since even the smallest emitter objects should be of this
     * size.
     *
     * @param res_m resolution of the RangedMapEmitter cell resolution (e.g.,
     * 600m) This defines how many AreaSourceCells are needed to cover that
     * area.
     * @return a list of AreaSourceCells.
     */
    public static ArrayList<SimpleAreaCell> getCellArray(float res_m) {
        ArrayList<SimpleAreaCell> cells = new ArrayList<>();

        ArrayList<int[]> HW;
        int r = 0;
        float dh_m = 10;

        while (r * dh_m <= res_m / 2) { //res_m is equal to the x- and y-length of one grid cell. Meanwhile, r*dh_m is approx. equal to half of that measure

            HW = getIterationIndex(r);
            for (int[] hw : HW) {

                int h = hw[0];
                int w = hw[1];

                float h_m = dh_m * h; // meters in the wind's direction, 'up'. NOTE: h and w and NOT the rotated currH and currW.
                float w_m = dh_m * w; //  meters in the direction perpendicular to the wind's direction, 'left or right'

                SimpleAreaCell sac = new SimpleAreaCell(dh_m, h_m, w_m);
                sac.setRotations();
                cells.add(sac);
            }//for hw
            r++;
        }//for ring
        EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,
                "Cell array contains " + cells.size() + " items.");
        return cells;
    }

    /**
     * Get new puff instances for this time tick. If needed, this can also
     * update the MapEmitterCells if they've gotten outdated.
     * @param pf the platform
     * @param last time
     * @return an array of newly created puffs.
     */
    public ArrayList<PuffCarrier> getPuffsUpdate(PuffPlatform pf, Dtime last) {
        
        ArrayList<PuffCarrier> mps = new ArrayList<>();
        GlobOptions g = GlobOptions.get();
        mapPuffTick++;
        float tick = pf.timeTick_s;

        //emission factor update triggers
        pf.mapPuffs.updateSecs += pf.timeTick_s;
        if (this.updateSecs >= 30 * 60) {//15min update rate.
            
            EnfuserLogger.log(Level.FINER,RangedMapEmitter.class,"ppf: map sources update.");
            
            long t = System.currentTimeMillis();
            this.updateMapSources(last, pf.ens);//updates source term coefficients as a function of time.
            long t2 = System.currentTimeMillis();
            EnfuserLogger.log(Level.FINER,RangedMapEmitter.class,"FixedSourceMap update took " + (t2 - t) + "ms");
            this.updateSecs = 0;
        }


        int actives =0;
        int tot =0;
        int outers =0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {

                if (mecs[h][w]!=null) {
                    double lat = in.getLat(h);
                    double lon = in.getLon(w);
                   
                    actives++;
                    int adds=addPuffs(mps, h, w, pf, last, tick);
                    tot+=adds;
                    if (!pf.in.bounds.intersectsOrContains(lat, lon)) outers+=adds;
                }//if non-null

            }//for w
        }// for h
        
        if (actives>0) {
            int ave = tot/actives;
            EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,
                    "RangedMapEmitter: active cells="+actives+", injecting " 
                            + ave +" puffs on average per step. total="+tot +", of which are outers: "+outers);
            
            EnfuserLogger.log(Level.FINE,RangedMapEmitter.class,"Injector: Added " + mps.size() + " map puffs for a duration of " 
                    + tick / 60 + " minutes");
            float no2sum = 0;
            float pm25sum = 0;
            for (PuffCarrier p : mps) {
                no2sum += p.Q[ops.VARA.VAR_NO2];
                pm25sum += p.Q[ops.VARA.VAR_PM25];
            }
            EnfuserLogger.log(Level.FINER,RangedMapEmitter.class,"\t added mapPuffs contain " + no2sum / FastMath.MILLION 
                    + "g of NO2 and  " + pm25sum / FastMath.MILLION + "g of PM25 ");
        }
        else {
          EnfuserLogger.log(Level.INFO,RangedMapEmitter.class,
                    "There seems to be no active mapEmitter cells for "+last.getStringDate_noTS());
        } 
            
        return mps;

    }

    private boolean rateWarning = true;
    int warns =0;
    
    /**
     * Create and add new puffs based on the given MapEmitterCell index.
     * The created puffs will be added to the given arrayList.
     * @param arr the arrayList where puffs are added to
     * @param h h index
     * @param w w index
     * @param pf the platform
     * @param dt time
     * @param timeTick_s time tick interval (to scale rates => emission masses)
     * @return amount of added puffs.
     */
    private int addPuffs(ArrayList<PuffCarrier> arr, int h, int w,
            PuffPlatform pf, Dtime dt, float timeTick_s) {
        rateWarning = true;
         int adds =0;
        MapEmitterCell mec = mecs[h][w];
        for (int hm:mec.getHeightsWithData()) {
        float[] q = mec.getEmissionMasses(hm, timeTick_s);
       
        if (q==null) {
            continue;
        }
 
        byte[][] contribs_cq = mec.getContributions(hm);
        int singleCat = mec.getSingleCategory(hm);//perhaps this complex contribution array is not needed?
        if (singleCat!=-1) {//yep, just one category
            contribs_cq =null;
        } else {//multiple ones, assign MISC as a placeholder.
            singleCat = CAT_MISC;
        }
        float lat = (float) in.getLat(h);
        float lon = (float) in.getLon(w);
        float immaterial_until_m = this.evalRadius_m;//becomes active after, e.g. 1600m  

        //Met met = pf.ens.getStoreMet(dt, lat, lon);
        float[] YX = Transforms.latlon_toMeters(lat, lon, pf.in.bounds);

        //automatic puff release count management
        int pcs = pf.getPuffReleaseRateIncreaser(hm,lat,lon, dt,this.puffGenIncreases);

        float miniTick = (float) timeTick_s / pcs;
        float Q_scaler = 1f / pcs;//*skipIncreaser;
            for (int i = 0; i < pcs; i++) {
                //the "puff" comes from a very large land mass tile. Thus we can randomize the point-like source a bit.
                float[] HYX = {hm, YX[0] + (float) (rand.nextRand() * in.res_m*0.5f),
                    YX[1] + (float) (rand.nextRand() * in.res_m*0.5f)};
                float[] Qmini = PuffCarrier.copyArr(q, Q_scaler);

                PuffCarrier p2 = new PuffCarrier(pf.ens, dt, DZ_EXP, HYX, Qmini,
                        new float[]{lat, lon}, "", singleCat, immaterial_until_m, 0);
                p2.fractionalCategories_cq = contribs_cq;//store source info. This will override the main category "MISC".

                if (i > 0) {
                    p2.evolve(i * miniTick, pf, dt);
                }
                p2.mapPuff = true;//flag this as mapPuff, which makes it possible to optimize concentration field computations.
                arr.add(p2);
                adds++;
            }
        }//for heights
        return adds;
    }

    /**
     * Get a grid representation of emission release rates for visualizations.
     * @param dt time
     * @param ens core
     * @param q emission type index
     * @return total emission rates for the type for the given time.
     */
    public GeoGrid getEmissionGrid(Dtime dt, FusionCore ens, int q) {
        if (!ens.ops.VARA.isPrimary[q]) {
            EnfuserLogger.log(Level.INFO,RangedMapEmitter.class,"RangedMapEmitter does not support"
                    + "the selected non-primary variable type.");
            return null;
        }
        this.updateMapSources(dt, ens);
        float[][] dat = new float[this.in.H][this.in.W];
        int actives =0;
        int tot =0;
        int outerActives =0;
        Boundaries main = ens.ops.getRunParameters().bounds();
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                
                tot++;
                
                if (mecs[h][w]!=null) {
                    actives++;
                    
                    dat[h][w] = mecs[h][w].typeRates_allElevations(q); 
                    double lat = in.getLat(h);
                    double lon = in.getLon(w);
                    boolean outer = !main.intersectsOrContains(lat, lon);
                    if (outer)outerActives++;
                }//if not offline
            }//for w
        }// for h
        EnfuserLogger.log(Level.INFO,RangedMapEmitter.class,"Total cells = "+tot
                +", active = "+ actives +", of which outer: "+outerActives);
        GeoGrid g = new GeoGrid(dat, dt.clone(), in.bounds.clone());
        return g;
    }

}
