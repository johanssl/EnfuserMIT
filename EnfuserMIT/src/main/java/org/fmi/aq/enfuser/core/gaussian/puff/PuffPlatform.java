/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier.DISCARD_OUT;
import static org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier.DISCARD_TERMINATION;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import static org.fmi.aq.enfuser.core.gaussian.puff.PPFsupport.printoutPuffStackSpecs;
import org.fmi.aq.enfuser.core.gaussian.puff.nesting.PuffMarkerBG;
import org.fmi.aq.enfuser.core.gaussian.puff.nesting.RegionalBG;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.kriging.ResolutionMod;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.interfacing.InstanceLoader;
import org.fmi.aq.interfacing.MicroMet;

/**
 * Gaussian puff model, especially for shipping emission puffs. - Uses fast
 * gaussian computatations by FastPuffMath (compoments are pre-calculated, 1 x
 * 1m) - Meteorology structure from Enfuser.datapack - Visualization and
 * animation by Plotter and MapPack (backgrounds visualization) Logic: Time
 * steps (timeIncr) are being added one after another. Then all PuffInstances
 * are updated and new puffs (Injector, ShippingActivity, ShipData) are added to
 * the stack. - Gaussian Plume also supported as an override class to Puff.
 *
 * @author Lasse
 */
public class PuffPlatform {

    public ArrayList<PuffCarrier> instances;
    public PuffBins bins;
    public AreaNfo in;
    
    public Boundaries b_expanded;
    public final Boundaries b_largerBounds;
    public Boundaries b_veryExpanded;
    Dtime dt;
    HashMap<Long,ArrayList<String[]>> stats = new HashMap<>();
    
    public Dtime dt_orig;
    public Dtime end_orig;

    public int timeTick_s;
    public final int respec_s;
    
    public RangedMapEmitter mapPuffs;
    public FusionCore ens;

    public PPFcontainer consContainer;
    public final int shgTemporal_mins;
    float[] stepTransforms_q;
    float[] stepDecay_q;
    
    public RegionalBG regionalBg;
    //MicroMet: in some studies a high resolution 3D wind dataset, given by a LES-model is needed.
    public MicroMet mm;
    public float lesRander=1f;

    public RangedMapEmitter getRangedMapEmitter() {
        return this.mapPuffs;
    }
    
        
    public void disableMerging(boolean b) {
        this.bins.merger.merges=b;
        if (b){
            System.out.println("Puff merging enabled.");
        } else {
             System.out.println("Puff merging disabled.");
        }
    }

    public PuffPlatform(FusionCore ens, Dtime start, Dtime end,
            boolean mapSources, int shgTemporal_mins, int resm) {

        stepTransforms_q = new float[ens.ops.VARA.Q_NAMES.length];
        stepDecay_q = new float[ens.ops.VARA.Q_NAMES.length];
        this.shgTemporal_mins = shgTemporal_mins;
        EnfuserLogger.log(Level.FINER,PuffPlatform.class,"==PUFF PLATFORM==\n"
                +start.getStringDate() + " - " + end.getStringDate());

        instances = new ArrayList<>();
        this.b_expanded = ens.ops.boundsExpanded(true).clone();
        this.b_veryExpanded = ens.ops.boundsMAXexpanded().clone();
        EnfuserLogger.log(Level.FINE,PuffPlatform.class,"area for puff injections expanded to: "
         + this.b_expanded.toText());

        this.ens = ens;
        this.dt = start.clone();
        this.dt_orig = dt.clone();
        this.end_orig = end.clone();

        this.timeTick_s = ens.ops.perfOps.puffBaseTick_s();
        this.respec_s = ens.ops.perfOps.puffRespec_s();
        Boundaries b_output = ens.ops.boundsClone();
        this.b_largerBounds=b_output.clone();
        this.b_largerBounds.expand(0.1);
        setResolution((float)resm, b_output);
        
        if (mapSources) {
            this.mapPuffs = new RangedMapEmitter(b_expanded.clone(), ens, start.clone());
        }
        
        this.bins = new PuffBins(this);
        initBgNesting(ens);
        
        //load lesWind?
        this.mm = MicroMet.loadModule(ens.ops);
    }
    
    public void resetResolution(float resm) {
        this.setResolution(resm, in.bounds);
    }
    
    private void setResolution(float resm, Boundaries b_output) {
        this.in = new AreaNfo(b_output,(float)resm, Dtime.getSystemDate());
        float maxDim_actual = Math.max(in.H,in.W);
        float maxDim = ens.ops.perfOps.getPPF_dimensionLimit();
        if (maxDim_actual > maxDim) {
            float scaler = maxDim_actual/maxDim;
            int newResm = (int)(resm*scaler);
             EnfuserLogger.log(Level.INFO,PuffPlatform.class,
                     "PuffPlatform resolution will be limited "+resm +" => "+ newResm);
             resm = newResm;
             this.in = new AreaNfo(b_output,(float)resm, Dtime.getSystemDate());
        }
        this.consContainer=null;
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,"PuffPlatform dimensions: "+ in.H +" x "+ in.W);
    }
    
    private void initBgNesting(FusionCore ens) { 
        EnfuserLogger.log(Level.FINER,PuffPlatform.class,"Setting BG nesting with "
                + FusionOptions.NESTING_NAMES[ens.ops.regionalNestingMode]);
        switch (ens.ops.regionalNestingMode) {
            case FusionOptions.BGNEST_TRACER:
                this.regionalBg = new PuffMarkerBG(ens.ops);
                break;

            default:
                this.regionalBg=InstanceLoader.selectNestingStrategy(ens.ops);
                break;
        }
        
        if (this.regionalBg!=null) {
            this.regionalBg.init(this);
        }
    }
    
    /**
     * Takes multiple close-by entries from SHG based on the given lat,lon
     * coordinates, and produced a smooth interpolated product from them.
     *
     * @param rad evaluation radius in pixels, should be 1 or 2. Value 1 is
     * probably the best for most use cases.
     * @param lat latitude value
     * @param lon longitude value
     * @param dt
     * @param pixDistAdd
     * @return smoothed concentration array that contains all category-specific
     * pollutant concentrations. For indexing, see getSHG_index_QC().
     */
    public float[][] getKriged_SHGvalues(int rad, double lat, double lon,
            Dtime dt, double pixDistAdd) {
        if (consContainer == null) {
            return null;
        }

      QCd d = this.consContainer.getSmoothClone(dt, lat, lon, rad,pixDistAdd);
      if (d==null) return null;
      return d.intoArray(ens.ops);
        
    }

    /**
     * For the given time precomputed values are assessed based on the
     * Boundaries mid point. If data exists, then data is available for this
     * time and the method returns true.
     *
     * @param dt Time for the availability test.
     * @return true or false.
     */
    public boolean checkAvailabilityOfData(Dtime dt) {

        if (this.consContainer == null) {
            return false;
        }
        return consContainer.hasData(dt);
    }

    private RandSequencer rs = new RandSequencer(100);

    /**
     * Returns an array of concentrations for the selected location and time,
     * containing all pollutant types (Q) and source categories (C).
     *
     * @param krigSmooth if true, then a smooth Kriging interpolated data is
     * provided.
     * @param lat the latitude. In case of out-of-Bounds then the latitude is
     * snapped to the nearest border location.
     * @param lon the longitude. In case of out-of-Bounds then the latitude is
     * snapped to the nearest border location.
     * @param dt time
     * @return Q x S sized array of tabled concentrations. The first dimension
     * corresponds to primary types (Q, VariableAssociations), the second
     * dimension is for source categories (C, Categories).
     */
    public float[][] getAll_shg_QS(boolean krigSmooth, double lat, double lon, Dtime dt) {

        if (this.consContainer == null) {
            return null;
        }

        if (lat <= this.consContainer.in.bounds.latmin) {
            lat = this.consContainer.in.bounds.latmin + consContainer.in.dlat;
        } else if (lat >= this.consContainer.in.bounds.latmax) {
            lat = this.consContainer.in.bounds.latmax - consContainer.in.dlat;
        }

        if (lon <= this.consContainer.in.bounds.lonmin) {
            lon = this.consContainer.in.bounds.lonmin + consContainer.in.dlon;
        } else if (lon >= this.consContainer.in.bounds.lonmax) {
            lon = this.consContainer.in.bounds.lonmax - consContainer.in.dlon;
        }

        float[][] qc = null;
        if (krigSmooth) {
            
            qc = this.getKriged_SHGvalues(2, lat, lon, dt,0.1);
            
        } else {
            QCd d= this.consContainer.getValues(dt, lat, lon);
            if (d!=null) qc = d.intoArray(ens.ops);
        }

        if (qc == null) {
            errs++;
            if (errs < 100) {
                EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "ppf: getCellContent returned null for: " 
                        + dt.getStringDate() + ", " + lat + ", " + lon);
            }
            return null;
        }
        
        return qc;
    }
    int errs = 0;


    public GeoGrid currentSlice(int resIncreaser, int q, Integer c) {
        if (c!=null && c<0) c=null;
        if (resIncreaser<1) resIncreaser =1;
        int H = consContainer.in.H;
        int W = consContainer.in.W;
        
        float[][] dat = new float[H][W];
        Boundaries b = consContainer.in.bounds.clone();
        Dtime dtt = dt.clone();
        AreaNfo in = new AreaNfo(b,H,W,dtt);
        
        for (int h =0;h<H;h++) {
            for (int w =0;w<W;w++) {
                double lat = in.getLat(h);
                double lon = in.getLon(w);
                QCd d = consContainer.getValues(dt, lat, lon);

                if (c==null) {
                    dat[h][w]= d.categorySum(q, true,ens.ops);
                } else {
                    dat[h][w]= d.value(q, c,ens.ops);
                }
                
            }//for w
        }//for h
        
       GeoGrid g= new GeoGrid(dat,dtt,b);
        if (resIncreaser >1) {
            g = ResolutionMod.kriginSmooth_MT(2, false,resIncreaser, g);
        }
        return g;
    }

    public ArrayList<PuffCarrier> activeInstances() {
        return this.instances;
    }
    
    public void resetWithNesting(Dtime dt, FusionCore ens) {
        this.reset(dt);
        this.initBgNesting(ens);
    }

    public void reset(Dtime resetTime) {
        this.instances.clear();
        if (resetTime != null) {
            this.dt = resetTime.clone();
        } 
        if (this.mapPuffs != null) {
            this.mapPuffs.updateSecs = 1000000;
        }
        if (this.regionalBg!=null) {
            this.regionalBg.init(this);
        }
    }


    public void iterateTimeSpan_forPuffs(int spinH, float height_m, Dtime start, Dtime end,
            boolean resetSHG) {

        Dtime dt_skip = start.clone();
        dt_skip.addSeconds(-1 * spinH*3600);
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,
                "PuffPlatform: starting spin-up from " + dt_skip.getStringDate());

        this.reset(dt_skip);
       
        if (resetSHG) {
            this.consContainer = null;// will be created again in the next initSlice()
        }

        int stepsPerIter = this.shgTemporal_mins * 60 / this.timeTick_s;
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,
                "\t steps per iteration: " + stepsPerIter);


        while (this.dt.systemSeconds() <= end.systemSeconds()) {

            this.evolveCustom(stepsPerIter);
            
            if (this.dt.systemSeconds()<start.systemSeconds()) {
                EnfuserLogger.log(Level.FINER,PuffPlatform.class,
                    "ppf:initSlice skipped for spin-up " + dt.getStringDate());
            } else {
                
               EnfuserLogger.log(Level.FINER,PuffPlatform.class,
                    "ppf:initSlice at " + dt.getStringDate()); 
               this.initHorizontalSlice(height_m); 
               if (GlobOptions.get().getLoggingLevel().intValue()<=Level.FINE.intValue() 
                       || firstLog ) {
                   firstLog = false;
                   this.printLog(this.dt);
               }
            }
            
            ens.ops.logBusy();
        }//while time
 
    }
boolean firstLog = true;

public void printLog(Dtime dt) {
    
    long mins = dt.systemSeconds()/60;
    ArrayList<String[]> arr = this.stats.get(mins);
    if (arr==null) return;
    
    String s ="";
    for (String[] temp:arr) {
        s+= temp[1]+"\n";
    }
    EnfuserLogger.log(Level.INFO, this.getClass(), s);
    
}

private ArrayList<PuffCarrier> lastInjects;
    /**
     * Evolves the state of the puffPlatform. All puffs are then updated
     * accordingly. Also, new puffs are being added via Injector.
     *
     * @param steps the amount of steps that is evolved. Each step evolves time
     * in seconds given by 'timeTick_s'.
     */
    public void evolveCustom(int steps) {
        //add new puffs
        Dtime current = this.dt.clone();

        ArrayList<Dtime> dts = new ArrayList<>();
        ArrayList<Dtime[]> allDts = new ArrayList<>();

        long t0;
        long t1;
        long t2;
        int injects =0;
        t0 = System.currentTimeMillis();
        for (int i = 0; i < steps; i++) {

            //manage times (stack in array for later use)   
            dts.add(current.clone());
            if (this.regionalBg!=null) {
              this.regionalBg.updateTimer( current,this);
            }
            
            Dtime next = current.clone();
            next.addSeconds(timeTick_s);
                ens.ppfForcedAvailable(true);
            lastInjects = Injector.getInjects(current, next, this);
                ens.ppfForcedAvailable(false);
                
            EnfuserLogger.log(Level.FINE,PuffPlatform.class,
                    "adding "+ lastInjects.size() +" puffs to stack at "+ dt.getStringDate_YYYY_MM_DDTHH());
            injects+=lastInjects.size();
            //add freeze timing since most of these are in essence future puffs
            for (PuffCarrier p : lastInjects) {
                p.activate_after_s += (i+1) * this.timeTick_s;
            }
             this.instances.addAll(lastInjects);
            
            current.addSeconds(this.timeTick_s);//update current
            this.dt = current.clone();
        }//for steps
        t1 = System.currentTimeMillis();

        //MAIN maintenance ======================
        ArrayList<PuffCarrier> removables = new ArrayList<>();
        ArrayList<PuffCarrier> puffsLeft = new ArrayList<>();

        //clear chemTransforms and decay arrays
        for (int q : ens.ops.VARA.Q) {
            this.stepTransforms_q[q] = 0;
            this.stepDecay_q[q] = 0;
        }
        //multithreading
        boolean mt = true;
        if (mt) {
            evolve_multiThread(dts, allDts);

        } else {

            for (PuffCarrier p : instances) {
                for (int k = 0; k < dts.size(); k++) {
                    Dtime dtt = dts.get(k);
                    p.evolve(this.timeTick_s, this, dtt);
                }
            }

        }//not multithreading puff evolution

        //check what is left
        int oobs = 0;
        int terminated = 0;
        boolean first =true;
        for (PuffCarrier p : instances) {

            
            int ok = p.isSubstantial(this);
            if (ok == DISCARD_OUT) {
                removables.add(p);
                oobs++;
            } else if (ok == DISCARD_TERMINATION) {
                removables.add(p);
                terminated++;
                            
            } else {
                //call the external module for customized modelling (LES-puffs)
                boolean discard = this.mm.discardStep(p,this,dt);
                if (discard) {
                   removables.add(p);
                   terminated++; 
                } else {
                  puffsLeft.add(p);  
                } 
            }

        }//for puffs       
        this.instances = puffsLeft;
        t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO, "Added "+ injects +", removed "+removables.size() +" puffs.");
           ArrayList<String[]> lines= printoutPuffStackSpecs(this,t0, t1, t2, removables,
                   oobs, terminated, steps,injects);
           
         if(lines!=null) this.stats.put(dt.systemSeconds()/60, lines);
        

    }
    
    public void printoutLog() {
        if (this.stats==null || this.stats.isEmpty()) return;
        String fname = "puffModellingLog.txt";
        String dir = this.ens.ops.operationalOutDir();
        
        ArrayList<String> arr = new ArrayList<>();
        try {
            
           
            ArrayList<Long> sorted = new ArrayList<>();
            for (Long key:stats.keySet()) {
                sorted.add(key);
            }
            Collections.sort(sorted);
        for (Long key:sorted) {
            ArrayList<String[]> cont = stats.get(key);
            for (String[] temp:cont) {
                arr.add(temp[0] + ";\t"+temp[1]);
            }
        }
        
          EnfuserLogger.log(Level.INFO,PuffPlatform.class,
                    "Creating puff modelling log: "+ dir +fname);
        FileOps.printOutALtoFile2(dir, arr, fname, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Evaluates a concentration map for a given z(m) and resolution.
     * @param height_m observation height in meters.
     */
    public void initHorizontalSlice(float height_m) {

        this.bins.distribute(this, height_m);
        if (this.consContainer == null) {// first Slice init ever.
            consContainer = new PPFcontainer(in, this.shgTemporal_mins,ens.ops);
        }
        computeCons_multiThread(height_m);
    }

    private void evolve_multiThread(ArrayList<Dtime> dts, ArrayList<Dtime[]> allDts) {

        ArrayList<FutureTask> futs = new ArrayList<>();
        int cores = ens.ops.cores;
        Threader.reviveExecutor();
        EnfuserLogger.log(Level.FINER,PuffPlatform.class,"Evolve MULTI-THREAD with " + instances.size() + " puffs.");
        ArrayList<PuffCarrier>[] pfs = new ArrayList[cores]; //create arrays for separate threads
        int N = 0;
        for (int i = 0; i < pfs.length; i++) {
            pfs[i] = new ArrayList<>();
        }
        int mod = 0;
        for (PuffCarrier p : instances) {//distribute puffs to threads, evenly
            int index = mod % cores;

            pfs[index].add(p);
            N++;
            mod++;
        }
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,
                "\t PuffPlatform concentration computation with " + N + " puffs in " + cores + " threads to be executed.");

        for (ArrayList<PuffCarrier> thp : pfs) {

            EvolveCallable cal = new EvolveCallable(thp, dts, allDts, this);
            FutureTask<ArrayList<PuffCarrier>> fut = new FutureTask<>(cal);
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
          EnfuserLogger.sleep(10,PuffPlatform.class);
        }// while not ready 

        Threader.executor.shutdown();
        EnfuserLogger.log(Level.FINER,PuffPlatform.class,"\t\t -> DONE.");
    }

    /**
     * Multithreaded method for the evaluation of a concentration map for a
     * given z(m) and resolution. Each raster point (or a horizontal row) can be
     * computed individually and this fact is exploited here.
     *
     * @param z_m
     * @param res
     * @param q_index
     */
    private void computeCons_multiThread(float height_m) {
        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();

        ArrayList<int[]>[] indices = PuffCalcCallable2.sortComputations(this);//create list of indices for multiple threads.
        //the order is randomized and some h,w indices can be missing (sparse resolution)
        
        long t = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,
                "res_m = " + (int)in.res_m + ", runStep="+dt.getStringDate_noTS());
        EnfuserLogger.log(Level.FINER,PuffPlatform.class,"*****H = " + height_m + "m"); 
        EnfuserLogger.log(Level.FINER,PuffPlatform.class,"Slice dimensions: H= " + in.H + " / W = " + in.W);

        for ( ArrayList<int[]>hw:indices) {
            PuffCalcCallable2 cal = new PuffCalcCallable2(this,hw);
            FutureTask<ArrayList<QCd>> fut = new FutureTask<>(cal);
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
          EnfuserLogger.sleep(100,PuffPlatform.class);
        }// while not ready 
        
        this.consContainer.removeTimeLayer(dt);//remove, if same time layer than previous. This is especially important for fillMissing().
        ArrayList<QCd> items;
        for (FutureTask fut:futs) {
            try {
                items = (ArrayList<QCd>) (fut.get());
                for (QCd dat:items) {
                  this.consContainer.addQCData(dat,  dat.y,  dat.x, dt);
                }

            } catch (InterruptedException | ExecutionException ex) {
                EnfuserLogger.log(ex,Level.SEVERE,PuffPlatform.class,
                        "MultiThreading computation result compilation failed!");
            }

        }// for futures
        //fill missing
        float aveFill = this.consContainer.fillMissing(dt);
        Threader.executor.shutdown();
        //background nesting to modelled puff concentrations
        if (this.regionalBg!=null) {
            this.regionalBg.addRegionalBackground(this, dt, consContainer);
        }
        
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,"Average fillRate = " + (int) aveFill + "%");
        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO,PuffPlatform.class,"Puff area computation took " + (t2 - t) + " ms");
    }
    

    public Dtime getDt() {
        return this.dt;
    }

    /**
     * Must not be used for PM10, which is the sum of PM2.5 and coarsePM
     *
     * @param q
     * @param c
     * @param start
     * @param end
     * @param nonZ
     * @return
     */
    public float[][][] Extract3D(int q, Integer c, Dtime start, Dtime end, boolean nonZ) {
        boolean dated = start != null && end != null;

        if (dated) {
            return consContainer.ExtractSub3D(0, c, nonZ, start, end);
        } else {
            return consContainer.Extract3D(q, c, nonZ);
        }
    }

    public FusionCore ens() {
        return this.ens;
    }

    public Boundaries inBounds() {
        return this.in.bounds.clone();
    }

    public void prepareComputationsForHeight(float z, FusionOptions ops) {
       for (PuffCarrier p:this.instances) {
           p.initForConcentrationCalcs(z, ops);
       }
    }


    float getMetricY(int y) {
      return (in.H - y) * in.res_m;                
    }

    float getMetricX(int x) {
       return  x * in.res_m;
    }

    public ArrayList<PuffCarrier> getLastInjections() {
       return this.lastInjects;
    }

    
    public int getPuffReleaseRateIncreaser(float hm, float lat, float lon, Dtime dt,
            float increaser) {
       //wind speed at release height. If high, more frequent puffs are needed. 
       float U = ens.macroLogWind(lat, lon, hm, dt);
       if (U< 1) U = 1;
       float rate = U;
       rate*=((float)this.timeTick_s/300f);//for larger time steps, 
       int pcs = Math.max(1, (int)rate);
       pcs*=increaser;
       return Math.max(1, pcs);
    }


    public class EvolveCallable implements Callable<ArrayList<PuffCarrier>> {

        ArrayList<PuffCarrier> puffs;
        ArrayList<Dtime> dts;
        ArrayList<Dtime[]> allDts;
        PuffPlatform pf;

        public EvolveCallable(ArrayList<PuffCarrier> puffs, ArrayList<Dtime> dts,
                ArrayList<Dtime[]> allDts, PuffPlatform pf) {
            this.puffs = puffs;
            this.dts = dts;
            this.allDts = allDts;
            this.pf = pf;
        }

        @Override
        public ArrayList<PuffCarrier> call() throws Exception {

            for (PuffCarrier p : puffs) {
                for (int i = 0; i < dts.size(); i++) {
                    p.evolve(pf.timeTick_s, pf, dts.get(i));
                }

            }//for puffs

            return puffs;
        }
    }


      /**
     * Get the current position of the puff center as in WGS84 latitude.
     * @param y the y-dimension value as in meters.
     * @return latitude value
     */
    public float latitudeFromY(float y) {
        double dlat = y / degreeInMeters;
        return (float) (dlat + in.bounds.latmin);
    }

    /**
     * Get the current position of the puff center as in WGS84 longitude.
     * @param x the x-dimension value as in meters.
     * @return longitude value
     */
    public float longitudeFromX(float x) {
        double dlon = x / degreeInMeters / in.coslat;
        return (float) (dlon + in.bounds.lonmin);
    }  
    

}
