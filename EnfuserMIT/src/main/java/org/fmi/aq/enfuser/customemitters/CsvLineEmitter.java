/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.customemitters.AbstractEmitter.BILLION;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.essentials.shg.CoordinatedVector;
import org.fmi.aq.essentials.shg.SHGhandler;
import org.fmi.aq.essentials.shg.SparseGrid;
import org.fmi.aq.essentials.shg.SparseHashGrid;
import static org.fmi.aq.essentials.shg.SparseGrid.getT_key;
import java.io.File;
import java.util.HashMap;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;

/**
 * This class introduces the SparseHashGrid emitter (SHG). The name comes from
 * data format: in this emitter there is a sparse collection of emitter cells
 * that are held on a hashed structure. This format is ideal for emission
 * sources such as shipping emissions, for which "active" cells are infrequent
 * and the high-resolution data is 99% empty data.
 *
 * This emitter has a couple of unique features: - during initiation the emitter
 * data can be refactored (lower spatial and temporal resolution). The logic
 * here is that if PuffPlatform is not used, then a coarser resolution is
 * actually more beneficial For longer term time series production.
 *
 * - The emitter keeps track of the "active" emitter cells. This is done by
 * using the array of CoordinatedVector. and the method updateTemporals()
 *
 * Important: Units are assumed to be kg/cell/timestep, where the timestep is
 * defined in SHG in minutes.
 *
 * @author Lasse Johansson
 */
public class CsvLineEmitter extends AbstractEmitter {

    private String[] rawHeader;
    private ArrayList<String> rawLines;
    public SparseHashGrid shg;
    int temporal_mins;
    private long currTkey = -1;//keep track of current time layer and changes that can occur
    ArrayList<CoordinatedVector> cvs = new ArrayList<>();//keep track of elements that are non-empty for the current time layer
    public int[] datIndex_Q;

    public float res_m;
    final Boundaries maxBounds;

    private CsvLineEmitter lowResEmitter;
    private boolean isLowRes =false;
    /**
     * The constructor for SHGemitter.Based on the loaded SparseHashGrid data,
 the geographic and temporal domain is setup. Also the pollutant species
 indexing is setup but note that this process is simple and may not as of
 yet work for all pollutant species.
     *
     * @param file the emitter file
     * @param ops options used
     */
    public CsvLineEmitter(File file, FusionOptions ops) {
        //TEXT-SHG_steam_ship_50m_2016-04-01T00-00-00_2016-04-30T23-45-00_60.0_60.24_24.86_25.25_.shg  
        // //"SINGULAR2D_myname_cat_heightDef_tmin_tmax_latmin_latmax_lonmin_lonmax_.xxx"
        super(ops);
        res_m = 60;
        temporal_mins = SparseGrid.TIME_15MIN;
        if (ops.areaFusion_dt_mins() <= 30) {
            temporal_mins = SparseGrid.TIME_5MIN*2;
        }
            
        this.readAttributesFromFile(file);

            EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                    "Reading " + file.getAbsolutePath());
        

        rawLines = FileOps.readStringArrayFromFile(file);
        EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"Done reading.");
        

        this.setupDataIndexing();
        this.ltm = LightTempoMet.readWithName(ops, this.myName);
        maxBounds = ops.boundsMAXexpanded().clone();

            EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                    "max Boundaries for emitter: " + maxBounds.toText());
        

        this.initEmitter(res_m, temporal_mins);
        //create a low-resolution copy
        this.lowResEmitter = new CsvLineEmitter(file,  ops,true);

    }
    
     private CsvLineEmitter(File file, FusionOptions ops, boolean lowres) {
        super(ops);
        res_m = 60;
        temporal_mins = SparseGrid.TIME_15MIN;
        if (ops.areaFusion_dt_mins() <= 30) {
            temporal_mins = SparseGrid.TIME_5MIN*2;
        }
        if (lowres) {
            res_m=100;
            temporal_mins = SparseGrid.TIME_HOUR;
            this.isLowRes=true;
        }
            
        this.readAttributesFromFile(file);
        EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                    "Reading " + file.getAbsolutePath());
        
        rawLines = FileOps.readStringArrayFromFile(file);
        EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"Done reading.");

        this.setupDataIndexing();
        this.ltm = LightTempoMet.readWithName(ops, this.myName);
        maxBounds = ops.boundsMAXexpanded().clone();
        EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                    "max Boundaries for emitter: " + maxBounds.toText());
        

        this.initEmitter(res_m, temporal_mins);
        if (this.ltm.hasNormalizingSum()) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    myName +": LTM has a normalizing sum defined but this feature"
                            + " has been reserved only for annual-totals -type of gridded emitters!");
        }
    }   
    


    @Override
    protected boolean getStoreTemporalZero(Dtime dt, Dtime[] allDates) {

        if (dt.systemSeconds() < this.sysSecRanges[0] 
                || dt.systemSeconds() > this.sysSecRanges[1]) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Setup the emission type indexing. Variables in shg are evaluated and
     * proper emission type indices are associated to those variables that match
     * the naming conventions..
     */
    private void setupDataIndexing() {
        rawHeader = rawLines.get(0).split(";");
        this.datIndex_Q = new int[VARA.Q_NAMES.length];

        //init
        for (int q : VARA.Q) {
            this.datIndex_Q[q] = -1;// N/A
        }

        for (int j = 0; j < this.rawHeader.length; j++) {

            String name = rawHeader[j];
            name = name.replace(".", "");
            name = name.replace("[g/s]", "");
            name = name.replace("(all)", "");
            name = name.replace(" ", "");
            
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"Checking header name: " + name);
            
            if (name.contains("NOx")) {
                name = name.replace("NOx", "NO2");
            }
            if (name.contains("SOx")) {
                name = name.replace("SOx", "SO2");
            }

            int index = VARA.getTypeInt(name, false, this.origFile.getAbsolutePath());
            if (index >= 0) {
               
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"\tFound it.");
                this.datIndex_Q[index] = j;
            }

        }//for raw header

    }
    int warnings = 0;

    /**
     * Update list of non-empty elements and the current timeKey. Returns true
     * if the timespan is not covered
     *
     * @param last the exact time
     * @return a boolean value to signal that NO relevant data exists for this
     * time step.
     */
    private boolean updateTemporals(Dtime last) {
        long sysS = last.systemSeconds();
        if (sysS < sysSecRanges[0] || sysS > sysSecRanges[1]) {
            return true;
        }//no data for this time

        if (shg == null) {
            warnings++;
            if (warnings == 1) {
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                        "SHGemitter: null SHG-dataset encountered! " + last.getStringDate() 
                                + ", " + this.origFile.getAbsolutePath());
            }
            return true;
        }

        Long tKey = shg.getClosestTimeKey(last, true);
        if (tKey == null) {
            return true;
        }

        if (tKey != this.currTkey) {
            this.currTkey = tKey;
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                        "Updating cvs for " + this.origFile.getAbsolutePath());
            
            this.cvs = shg.getCoordinatedDataVectors(last);//update the list of cells that output emissions
            EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                        "\t now there are " + cvs.size() + " shg emitter points.");
            
        }//if new key
        return false;
    }

    @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        //this is a task for the low-res emitter. The high-res emitter is for puff modelling.
        if(!this.isLowRes) {
            //must do h,w conversion
            if (this.lowResEmitter.in.H != in.H) {
                float ratioh = (float)this.lowResEmitter.in.H/(float)in.H;
                float ratiow = (float)this.lowResEmitter.in.W/(float)in.W;
                h = (int)(ratioh*h);
                w = (int)(ratiow*w);
                
                if (h<0 || h>= this.lowResEmitter.in.H) return null;
                if (w<0 || w>= this.lowResEmitter.in.W) return null;
            }
            //recursively call this again, for the lowResEmitter.
            //it cannot do this same recursion since it has 'isLowRes=true';
            return this.lowResEmitter.getRawQ_µgPsm2(dt, h, w);
        }
      
        long key = getT_key(dt.systemSeconds(), shg);
        float[] dat = this.shg.getCellContent_tkHW(key, (short) h, (short) w);
        if (dat == null) {
            return null;
        }

        float shgSecs = shg.timeInterval_min * 60f;

        float[] qtemp = new float[VARA.Q_NAMES.length];
        for (int q : VARA.Q) {
            qtemp[q] = dat[q] * (1f / shgSecs) / in.cellA_m2;
            //base unit: kg/cell/time unit. To convert this into area source µg/s/m2 - the SHG-timestep duration,
            //unit, and grid cell area are factored out.
            //if index exists for this q_type, then the unit is kg/cell/shgTimeUnit.
            //now it's µg
        }
        return qtemp;
    }

    @Override
    public float getEmHeight(OsmLocTime loc) {
        return (float) this.height_def;
    }

    @Override
    public ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs, PuffPlatform pf,
            Dtime[] allDates, FusionCore ens) {
        ArrayList<PrePuff> arr = new ArrayList<>();

        boolean cont = updateTemporals(last);
        if (cont) {
            return null;//timespan not valid, no puffs
        }

        float genIncr = this.ltm.puffGenIncreaseF;
        if (genIncr == 0) {
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,
                        "SHG Puff generation increase factor is ZERO!");
            
            return arr;
        }

        //add puffs  
        for (CoordinatedVector cv : this.cvs) {

            float[] Q = new float[VARA.Q_NAMES.length];
            float shgSecs = shg.timeInterval_min * 60f;

            Met met = null;
            if (ltm.hasMetFunctions()) {
                met = pf.ens.getStoreMet(last, cv.lat, cv.lon);
            }

            for (int q : VARA.Q) {
                Q[q] = cv.dat[q] * (secs / shgSecs) * ltm.getScaler(last, allDates, met, q);
            }
            //check that NO is taken care of
            PrePuff pr = new PrePuff(Q,cv.lat,cv.lon,(float)this.height_def);
            arr.add(pr);

        }//for cvs

        return arr;
    }

    public static void main(String[] args) {
        testDateParse();
    }
    private static void testDateParse() {
        Dtime start = new Dtime("2020-01-01T00:00:00");
        
        String[][] test = {
        {"","","","2019-02-03T04:05:59"},    
        {"","","","02-03T04:05:59"},
        {"","","","03T04:05:59"},
        {"","","","04:05:59"},
        {"","","","05:59"},
            
        };
        
       for (String[] sp:test) {
           Dtime dt = parseLineTime(sp,start);
           EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,dt.getStringDate_noTS());
       } 
        
    }
    
    /**
     * Parse the line time stamp, taking into account that it can be presented
     * in various compact forms. For all the compact forms, the not-available
     * date parts should be taken from the the file "start-time"
     * @param split splitted line element.
     * @param nameStart The start date for file (for data in the file) 
     * @return parsed date.
     */
    public static Dtime parseLineTime(String[] split, Dtime nameStart) {
        String raw = split[IND_DT];
        String stamp;
        //2020-01-01T00:00:00 length is 
        if (raw.length() > 17) {//full stamp
           stamp = raw;
        
        }  else if (raw.length() > 13) {//year missing
           stamp = nameStart.year + "-" + raw;   
        
        }  else if (raw.length() > 10) {//year, month missing
           stamp = nameStart.getStringDate_YYYY_MM() + "-" + raw;   
        
         }  else if (raw.length() > 7) {//year, month, day missing
           stamp = nameStart.getStringDate_YYYY_MM_DD()+ "T" + raw;      
           
        } else {//year, month, day, hour missing
           stamp = nameStart.getStringDate_YYYY_MM_DDTHH() + ":" + raw;

        }

        return new Dtime(stamp);
    }
    
    boolean init = false;

    private final static int IND_ID=0;
    private final static int IND_LAT=1;
    private final static int IND_LON=2;
    private final static int IND_DT=3;
    private final static int IND_STACKH=4;
    
    private void initEmitter(float res_m, int temporalMins) {
        init = true;
        this.res_m = res_m;
        this.temporal_mins = temporalMins;
        Boundaries b = this.nameBounds.clone();//take the file boundaries as it is
        //but crop the boundaries in case the area is too large.

        if (b.latmax > this.maxBounds.latmax) {
            b.latmax = this.maxBounds.latmax;
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"latmax cropped to " + b.latmax);
            
        }
        if (b.lonmax > this.maxBounds.lonmax) {
            b.lonmax = this.maxBounds.lonmax;
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"lonmax cropped to " + b.lonmax);
            
        }

        if (b.latmin < this.maxBounds.latmin) {
            b.latmin = this.maxBounds.latmin;
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"latmin cropped to " + b.latmin);
            
        }
        if (b.lonmin < this.maxBounds.lonmin) {
            b.lonmin = this.maxBounds.lonmin;
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"lonmin cropped to " + b.lonmin);
            
        }

        in = new AreaInfo(b, res_m, Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
        //init shg
        shg = new SparseHashGrid((short) in.H, (short) in.W, this.nameBounds, VARA.Q_NAMES, this.temporal_mins);
        this.isZeroForAll = new boolean[in.H][in.W];

        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                this.isZeroForAll[h][w] = true;
            }
        }

        double[] testSums_kg = new double[VARA.Q_NAMES.length];
        float[] testSums_kg_oob = new float[VARA.Q_NAMES.length];
        //read data
        String[] sp;
        String[] sp2;
        ArrayList<String> temp = new ArrayList<>();
        Dtime dt;
        Dtime dt2;

        int bskips = 0;
        int errs = 0;
        int zeroskips = 0;
        int unfeasibles = 0;
        int shipChanges = 0;
        int longSteps = 0;
        for (int k = 1; k < rawLines.size() - 1; k++) {

            try {

                sp = rawLines.get(k).split(";");
                sp2 = rawLines.get(k + 1).split(";");

                double lat = Double.valueOf(sp[IND_LAT]);
                double lon = Double.valueOf(sp[IND_LON]);

                double lat2 = Double.valueOf(sp2[IND_LAT]);
                double lon2 = Double.valueOf(sp2[IND_LON]);

                if (this.nameBounds.intersectsOrContains(lat, lon) || this.nameBounds.intersectsOrContains(lat2, lon2)) {
                    temp.add(rawLines.get(k));
                } else {
                    bskips++;
                }

                double dist_m = Observation.getDistance_m(lat, lon, lat2, lon2);
                //double dur_s = Double.valueOf(sp[4]);
                //if (dur_s<0) continue;//a marker for that a skip should happen now (no interpolation to the next one)

                //check height
                if (sp[IND_STACKH].contains("-")) {

                    unfeasibles++;
                    continue;
                } //THE ENCODED SKIP CONDITION

                if (sp2[IND_ID].length() > 7
                        || (k > 1 && sp[IND_ID].length() > 7)) {//the next line (or the previous one) has a MMSI! The vessels are different!
                    shipChanges++;
                    continue;
                }

                dt = parseLineTime(sp, nameStart);
                dt2 = parseLineTime(sp2, nameStart);
                
                
                double dur_s = dt2.systemSeconds() - dt.systemSeconds();
                if (dur_s <= 0) {
                    unfeasibles++;
                    continue;
                }

                if (dur_s > 2 * 3600) {
                    longSteps++;
                    continue;
                }
                //create emRates
                double[] emrate = new double[VARA.Q.length];
                double[] emrate2 = new double[VARA.Q.length];
                double ts = 0;// testsum

                //testSums
                for (int q : VARA.Q) {// over Q
                    int col = this.datIndex_Q[q];
                    if (col < 0) {
                        continue;
                    }
                    double qs1 = Double.valueOf(sp[col]);
                    emrate[q] = qs1;
                    double qs2 = Double.valueOf(sp2[col]);
                    emrate2[q] = qs2;
                    ts += qs1 + qs2;

                    testSums_kg[q] += (0.5 * qs1 + 0.5 * qs2) * dur_s / 1000; //g/s => kg        
                }

                if (ts <= 0) {
                    zeroskips++;
                    continue;
                }

                //compute the amount of steps and interpolate
                int steps = (int) (1.2 * dist_m / res_m + 1);
                int stepsT = (int) (dur_s / this.temporal_mins / 60);
                if (stepsT > steps) {
                    steps = stepsT;
                }

                float secsAdd = 0;
                for (int i = 0; i <= steps; i++) {
                    float frac2 = (float) i / steps;
                    float frac1 = 1f - frac2;

                    float stepLength_s = (float) dur_s / (steps + 1);//this many seconds worth of emissions needs to be distributed
                    secsAdd += stepLength_s;
                    //get interpolated cc
                    double clat = lat * frac1 + lat2 * frac2;
                    double clon = lon * frac1 + lon2 * frac2;

                    float[] incr = new float[VARA.Q_NAMES.length];
                    for (int q : VARA.Q) {// over Q

                        double qs1 = emrate[q];
                        double qs2 = emrate2[q];

                        double qs = frac1 * qs1 + frac2 * qs2;
                        incr[q] = (float) (qs * stepLength_s) * FastMath.MILLION; //now it's µg.

                    }
                    long secs = dt.systemSeconds() + (int) secsAdd;
                    //empty content check: no emissions => no reason to add anything! 

                    try {

                        if (this.nameBounds.intersectsOrContains(clat, clon)) {
                            shg.sumOrCreateContent_fastLatLon(incr, secs, clat, clon, false);

                            int hh = in.getH(clat);
                            int ww = in.getW(clon);
                            this.isZeroForAll[hh][ww] = false;

                        } else {//sum to oobs

                            for (int q : VARA.Q) {
                                testSums_kg_oob[q] += incr[q] / BILLION;//µg to kg
                            }

                        }

                    } catch (Exception e) {

                    }
                }//for steps

            } catch (Exception e) {
                errs++;
            }
        }//for lines
        this.rawLines = temp;
            if(bskips>1) EnfuserLogger.log(Level.INFO,CsvLineEmitter.class,this.myName +" csvEmitter: Reduced line count by: " + bskips + " (outOfBounds)");
            if (errs>0)EnfuserLogger.log(Level.WARNING,CsvLineEmitter.class,"Errors: " + errs);
            EnfuserLogger.log(Level.FINE,CsvLineEmitter.class,"ZeroEmission lines = " + zeroskips);
            EnfuserLogger.log(Level.FINE,CsvLineEmitter.class,"Unffeasible lines: " + unfeasibles);
            EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"MMSI changes: " + shipChanges);
            EnfuserLogger.log(Level.FINE,CsvLineEmitter.class,"Long steps (>2h) skipped: " + longSteps);
            Level logl = GlobOptions.get().getLoggingLevel();
            if (logl.intValue()<=Level.FINER.intValue()) {
                EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"Read complete. Testsums:");
                for (int q = 0; q < VARA.Q_NAMES.length; q++) {
                    String varname = VARA.VAR_NAME(q);
                    EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,varname + ": " + (int) (testSums_kg[q]) + "kg");

                    float shg_sum = SHGhandler.getsum_total(shg, varname);
                    EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"\t from shg: " + (int) (shg_sum / (BILLION)) + "kg");
                    EnfuserLogger.log(Level.FINER,CsvLineEmitter.class,"\t\t not writted to SHG (OOB): " + (int) (testSums_kg_oob[q]) + "kg");
                }
            }
    }


    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
        float[] Q = new float[VARA.Q.length];

        for (int h = 0; h < this.in.H; h++) {
            for (int w = 0; w < this.in.W; w++) {
                if (this.isZeroForAll != null && this.isZeroForAll[h][w]) {
                    continue;
                }
                float[] inc = getRawQ_µgPsm2(dt, h, w);
                if (inc == null) {
                    continue;
                }

                for (int q : VARA.Q) {
                    Q[q] += inc[q]  * in.cellA_m2 *UGPS_TO_KGPH;
                }

            }//for w
        }//for h

        return Q;
    }

}
