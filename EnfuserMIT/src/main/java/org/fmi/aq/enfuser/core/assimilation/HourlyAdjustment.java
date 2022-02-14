/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import static org.fmi.aq.enfuser.options.Categories.OVER_DEFAULT;
import static org.fmi.aq.enfuser.options.Categories.OVER_INCR;
import static org.fmi.aq.enfuser.options.Categories.OVER_MAXF;
import static org.fmi.aq.enfuser.options.Categories.OVER_MAXSTEPS;
import static org.fmi.aq.enfuser.options.Categories.OVER_MINF;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.source.DBline;
import java.util.ArrayList;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.core.receptor.RPconcentrations;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 * This method is to define numeric scaling factors for contemporary emission
 * sources. Each hour can (and should) have an OverrideList for each pollutant
 * species. All OverrideLists will be held in OverrideController (at FusionCore)
 *
 * For background pollutant concentrations the numeric operator is additive
 * instead of multiplicative. This is a more robust way to adjust the background
 * concentrations when a scaling operation for forecasts could lead to
 * significant overestimation.
 *
 * The state of every OverrideList is to be assessed with Adapter's datafusion
 * algorithm using the most recent Observation data.
 *
 * To avoid discontinuity over the course of time, a sequence of OverrideLists
 * can be "smoothed". In such cases the attribute boolean smoothed is 'true'.
 * For forecasted period there is no measurement data to be used for Adapter:
 * therefore an estimate based on the previous sequence of OverrideLists will be
 * used to predict a 'future' OverrideList (boolean filled = true). A similar
 * fill-operation is used for the hours for which observation data was not
 * present.
 *
 * @author Lasse Johansson
 */
public class HourlyAdjustment {

    static HourlyAdjustment createDummy(Dtime dt, FusionOptions ops, int typeInt) {
        HourlyAdjustment ol = new HourlyAdjustment(dt, ops, typeInt);
        ol.filled = true;
        return ol;
    }
    
    private HourlyAdjustment(Dtime dt, FusionOptions ops, int typeInt) {
          
        Categories CATS = ops.CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        this.obs = new ArrayList<>();
        this.sts =  new ArrayList<>();
        this.isPrevObs = new ArrayList<>();
        this.typeInt = typeInt;
        this.overrides = new float[OVER_LEN];
        this.date = dt.getStringDate_noTS();
        
        //set default values
        for (int c = 0; c < OVER_LEN; c++) {
            this.overrides[c] = (float) CATS.numeric(c, OVER_DEFAULT);//OVERRIDE_RANGES[c][DriverCategories.OVER_DEFAULT];
        }

        this.dt =dt.clone();
    }

//public final static int CAT_EXP_TO_BG = CATNAMES.length;
    public final Dtime dt;
    protected float[] overrides;
    public String stringo = "";
    public String date = "";
    public String infoOneLine;
    public String infoHeaderLine = "";
    public String varPens = "";

    protected final ArrayList<Observation> obs;
    protected final ArrayList<StationSource> sts;
    protected final ArrayList<Boolean> isPrevObs;
    public final int typeInt;

    public boolean smoothed = false;
    public boolean filled = false;
    public boolean neural=false;

    public ArrayList<String> secondaryInfoLines = new ArrayList<>();

    public final static float PREV_W = 0.25f;
    public final static float CURR_W = 0.5f;
    public final static float NEXT_W = 0.25f;

    public final static float PREV_W2 = 0.3f;
    public final static float CURR_W2 = 0.7f;

    /**
     * Form a smoothed clone HourlyAdjustment using the a) previous hour Override,
 b) the current HourlyAdjustment and c) the next HourlyAdjustment.
     *
     * @param prev the previous
     * @param curr the current
     * @param next the next HourlyAdjustment
     * @return a smooth clone based on the 3 OverrideLists.
     */
    public static HourlyAdjustment smoothClone(HourlyAdjustment prev, HourlyAdjustment curr, HourlyAdjustment next) {

        HourlyAdjustment ol = new HourlyAdjustment(curr.date, curr.obs, curr.sts, curr.isPrevObs, curr.typeInt);
        ol.smoothed = true;
        for (int c = 0; c < ol.overrides.length; c++) {
            if (next != null) {
                ol.overrides[c] = PREV_W * prev.overrides[c] + CURR_W * curr.overrides[c] + NEXT_W * next.overrides[c];
            } else {
                ol.overrides[c] = PREV_W2 * prev.overrides[c] + CURR_W2 * curr.overrides[c];
            }
        }

        return ol;
    }

    /**
     * Define the discrete space for which the emission source scaling factors
     * can be adjusted within (for Adapter). These are mainly defined in
     * Categories.java but the previous state is also taken into account to
     * avoid discontinuity. Also: for emission sources that have very low
     * relative share the discrete space for adjustments is strongly limited -
     * this will reduce the computational effort significantly. (E.g., consider
     * NO2 concentrations for which households do not contribute to)
     *
     * @param previous the previous hour HourlyAdjustment
     * @param a Assimilator instance
     * @return an ArrayList of test ranges (min, max), one element per each
     * defined emission category.
     */
    public static ArrayList<Float>[] getOptimizationRanges(HourlyAdjustment previous, Assimilator a) {
        float[] cons_cat_share100 = a.cons_cat_share100;
        int OVER_LEN = a.CATS.CATNAMES.length;
        ArrayList<Float>[] arr = new ArrayList[OVER_LEN];

        for (int c = 0; c < OVER_LEN; c++) {
            arr[c] = new ArrayList<>();
            float hardMin = (float)a.CATS.numeric(c, OVER_MINF);
            float hardMax = (float)a.CATS.numeric(c, OVER_MAXF);
            double inc = a.CATS.numeric(c, OVER_INCR);
            float def = (float)a.CATS.numeric(c, OVER_DEFAULT);
            if (inc<=0) {//skip this category, no stepSize
                arr[c].add(def);
                continue;
            }

            if (a.secondary) {//e.g., for PM10 (secondary) the overrides have been done for PM2.5 and coarsePM (both primaries) that together make PM10.
                hardMin = def;
                hardMax = def;
            }

            if (previous != null) {//limit the variation since the current Override should be at least somewhat similar than the previous one.

                double steps = a.CATS.numeric(c, OVER_MAXSTEPS);
                if (steps < 2) {
                    steps = 2;
                }
                double minFromPrev = previous.overrides[c] - inc * steps;
                double maxFromPrev = previous.overrides[c] + inc * steps;
                def = previous.overrides[c];
                if (minFromPrev > hardMin) {
                    hardMin = (float)minFromPrev;//.e.g prev - 3inc = 0.8 => 0.8
                }
                if (maxFromPrev < hardMax) {
                    hardMax = (float)maxFromPrev;//.e.g prev + 3inc = 1.1 => 1.1
                }

            }

            //background adjustment, handled separately (a bit more complicated)
            //the value range is to be ABSOLUTE, and the hardMin, hardMax
            //will act as scalers. SO, setting the min.max to 0 the
            //bakcground adjustment will always be 0.
            if (c == CAT_BG) {

                    if (a.secondary) {//add 2 or 3 dummy override choices for the algorithm.
                        //three backround adjustments to choose from: 0,0 or how about 0?
                        //The big idea here is to allow some "degree of freedom" for the algorithm
                        //so that it resolves properly but ultimately does nothing.
                        arr[c].add(0f);
                        arr[c].add(0f);
                        arr[c].add(0f);
                        continue;
                    }

                    float negmin;
                    float posmax;
                    int negSteps = 4;
                    int posSteps = 4;

                    float diff = Math.abs(a.maxBG - a.minObs);
                    if (a.maxBG > a.minObs) {//model BG could be overestimated, but is propably not underpredicted

                        negmin = -1.5f*diff - 8;
                        negSteps = 8;
                        posmax = 0.5f*diff + 8;

                    } else {//minimum observed is larger than model BG max, which should indicate that bg might be underestimated, and is propably not an overestimation

                        negmin = -0.5f * diff - 8;
                        posmax = 2f * diff + 0.3f*a.minObs;
                        posSteps = 10;
                    }

                    //
                    float negLimit = -1 * a.minBG;
                    if (negmin < negLimit) {
                        negmin = negLimit;//e.g., the minimum bg value is 10µgm-3 so (-10) should be the limit. Otherwise we can end up with a zero field for background (which could be mathematically the best solution but will look artificial and buggy even)
                    }
                    for (int i = 1; i < negSteps; i++) {//interpolate 5 steps between min,max
                        float f = (float) i / negSteps;
                        float val = negmin * (1f - f);
                        arr[c].add(val*hardMin);
                    }
                    arr[c].add(0f);

                    for (int i = 1; i < posSteps; i++) {//interpolate 5 steps between min,max
                        float f = (float) i / posSteps;
                        float val = posmax * f;
                        arr[c].add(val*hardMax);
                    }
                    //for Category background this is as fas as we go.
                    continue;
                }//if BG

            float contrPercent = cons_cat_share100[c];
                EnfuserLogger.log(Level.FINER,HourlyAdjustment.class,
                        "Optimization ranges: emCategory contribution for " 
                                + a.CATS.CATNAMES[c] + " is " + contrPercent + "%");
            
            if (contrPercent < 2) {
                arr[c].add((float) def);
                continue;
            }

            //add elements to array
            for (float f = (float) hardMin; f <= hardMax; f += inc) {
                arr[c].add(f);
                    EnfuserLogger.log(Level.FINER,HourlyAdjustment.class,
                            "\t\t Added a possible override " + f + " for " + a.CATS.CATNAMES[c]);
                
            }

            if (arr[c].isEmpty()) {
                arr[c].add((float) def);//MUST have at least one value! Otherwise the multiple inner for-loops in Assimilator will not work.
            }
        }//for categories
        return arr;

    }
    

 public void setValue(int catI, float value) {
     this.overrides[catI] =value;
 }   
    
    private float aveErr = 0;
    private float avePen = 0;
    private float avePen_count = 0;
    private int aveErrC = 0;//a counter: how many observations was involved.

    /**
     * for logging and assessing the state of OverrideList, setup a series of
     * text output. These can be stored to file by OverrideController as
     * standard output of the system.
     *
     * @param ens the FusionCore
     */
    public void setupPrintouts(FusionCore ens) {

        //best_sd.flagPenaltiesAndReStat();
        stringo = "===" + ens.ops.VAR_NAME(typeInt) + "===\nStationID;value;exp\n";
        infoOneLine = "";
        infoHeaderLine = "";
        varPens = "";

        this.secondaryInfoLines.clear();
        //createAverageMet
        double midlat = 0;
        double midlon = 0;
        int n = 0;
        for (int k = 0; k < obs.size(); k++) {
            if (this.isPrevObs.get(k)) {
                continue;
            }
            Observation o = obs.get(k);
            if (o == null) {
                continue;
            }
            n++;
            midlat += o.lat;
            midlon += o.lon;
        }

        midlat /= n;
        midlon /= n;
        Met met = ens.getStoreMet(dt, midlat, midlon);
        String metHeader = "MET;" + Met.getMetValsHeader(";",ens.ops) + "POINTS;";
        infoHeaderLine += metHeader;
        String metvals = "MET;" + met.metValsToString(";") + "POINTS;";
        infoOneLine += metvals;

        //go over points
        for (int k = 0; k < obs.size(); k++) {
            if (this.isPrevObs.get(k)) {
                continue;//history-previous is not printed out.
            }
            Observation o = this.obs.get(k);
            StationSource s = sts.get(k);
            infoHeaderLine += s.lat() + "," + s.lon() + "," + s.dbl.height_m + ";OBS_VAL;PREDICTION;BG;RAW_BG;VAR_PEN;ORIG_OBSVAL;";
            if (o == null || filled) {
                //was not available, empty fields;
                infoOneLine += ens.DB.getSecondaryID(s.ID) + ";;;;;;;";

                continue;
            }

            float[] exps = o.incorporatedEnv.ccat.getExpectances_assimilation(this,typeInt, ens, o.incorporatedEnv);
            float[] exps_raw = o.incorporatedEnv.ccat.getExpectances_assimilation(null,typeInt, ens, o.incorporatedEnv);

            float exp = FuserTools.sum(exps);//o.getExp(true,ens, typeInt, false);//true for postReinit., which is NEEDED in this case where an HourlyAdjustment has just been stored to database.
            //NOTE that this particular HourlyAdjustment MUST be stored in ens.overrides!
            DBline dbl = ens.DB.get(s.ID);
            boolean hide = false;
            if (dbl!=null) {
                if (dbl.hideFromOutput) {
                    hide = true;
                }
            } else {
                hide =true;
                EnfuserLogger.log(Level.WARNING,HourlyAdjustment.class,
                        "OverrideList: source does not seem to have a DataBase line: " + s.ID);
            }
            
            String sval = editPrecision(o.value, 1) + "";
            String sraw = editPrecision(o.origObsVal(), 1) + "";
            if (hide) {
                sval = "";
                sraw = "";
            }

            String thisString = ens.DB.getSecondaryID(s.ID) + ";"
                    + sval + ";"
                    + editPrecision(exp, 1) + ";"
                    + editPrecision(exps[CAT_BG], 1) + ";"
                    + editPrecision(exps_raw[CAT_BG], 1) + ";"
                    + o.reliabilityString() + ";"//editPrecision(o.varPenalty_new,1) + ";"
                    + sraw + ";";

            stringo += thisString + "\n";
            infoOneLine += thisString;
            //infoHeaderLine += s.dom.maxlat+","+s.dom.maxlon+","+s.height_m +";OBS_VAL;PREDICTION;BG;RAW_BG;VAR_PEN;";

            aveErr += Math.abs(o.value - exp);
            double penaltyProx = o.dataFusion_qualityIndex();
            avePen += penaltyProx * penaltyProx;//the actual penalty effect is ^2 as a function of index.
            if (o.dataFusion_qualityIndex() > 0) {
                avePen_count++;
            }
            aveErrC++;
        }//for obs

        for (int i = 0; i < obs.size(); i++) {
            Observation o = obs.get(i);
            if (o != null) {
                //o.varPenalty_new = 1f/penalties[i];
                if (o.reliabilityAssessed() && o.dataFusion_qualityIndex() > 0) {
                    varPens += this.sts.get(i).getShortID(ens.DB)
                            + "=" + o.reliabilityString() + "  ";
                }
            }

        }

    }
    
    public int amountOfObservations() {
        int n =0;
    
       for (int k = 0; k < obs.size(); k++) {
            if (this.isPrevObs.get(k)) {
                continue;//history-previous is not printed out.
            }
            n++;
        }
       return n;
    }

    /**
     * get the Average error for model predictions after Adapter's datafusion.
     * Note: setupPrintouts() needs to be invoked for this to function properly.
     *
     * @return float value for average prediction error (µgm-3)
     */
    public float aveErr() {
        return this.aveErr / (this.aveErrC + 0.0001f);
    }

    /**
     * get the Average penalty factor for measurements after Adapter's
     * datafusion. Note: setupPrintouts() needs to be invoked for this to
     * function properly.
     *
     * @return float value.
     */
    public float avePen() {
        return this.avePen / (this.aveErrC + 0.00001f);
    }

    public float averagePenalitesGiven() {
        return (float) this.avePen_count / (this.aveErrC + 0.00001f);
    }

    /**
     * Create a filler HourlyAdjustment based on a previous HourlyAdjustment, which
 stats are copied.
     *
     * @param prev previous Override element to be used when the filler is
     * formed.
     * @param addH hours after the previous this filler is to be stored.
     * @return filled HourlyAdjustment element.
     */
    public static HourlyAdjustment filler(HourlyAdjustment prev, int addH) {
        Categories CATS = GlobOptions.get().CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        ArrayList<Observation> obs = new ArrayList<>();
        for (Observation o : prev.obs) {
            obs.add(Assimilator.DUMMY_NULL);//for this HourlyAdjustment there are no real observations available
        }
        HourlyAdjustment ol = new HourlyAdjustment(prev.date, obs, prev.sts, prev.isPrevObs, prev.typeInt);
        ol.filled = true;
        ol.dt.addSeconds(addH * 3600);
        ol.date = ol.dt.getStringDate_noTS();
        //set default values
        for (int c = 0; c < OVER_LEN; c++) {
            ol.overrides[c] = prev.overrides[c];
        }
        return ol;
    }

    /**
     * Initiate an OverrideList with default settings.
     *
     * @param date the date in String form.
     * @param obs Observation data
     * @param sts a list of StationSources with identical indexing as with 'obs'
     * @param isPrevObs a list of Booleans: true in case the Observation
     * instance if for previous hour data.
     * @param typeInt pollutant species type index.
     */
    public HourlyAdjustment(String date, ArrayList<Observation> obs, ArrayList<StationSource> sts,
            ArrayList<Boolean> isPrevObs, int typeInt) {
        Categories CATS = GlobOptions.get().CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        this.obs = obs;
        this.sts = sts;
        this.isPrevObs = isPrevObs;
        this.typeInt = typeInt;
        this.overrides = new float[OVER_LEN];
        this.date = date;

        //set default values
        for (int c = 0; c < OVER_LEN; c++) {
            this.overrides[c] = (float) CATS.numeric(c, OVER_DEFAULT);//OVERRIDE_RANGES[c][DriverCategories.OVER_DEFAULT];
        }

        this.dt = new Dtime(date);
    }

    /**
     * Clone the numeric adjustment factors based on another HourlyAdjustment's
 content.
     *
     * @param prev the base for cloning.
     */
    void cloneData(HourlyAdjustment prev) {
        Categories CATS = GlobOptions.get().CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        this.overrides = new float[OVER_LEN];
        for (int c = 0; c < OVER_LEN; c++) {
            this.overrides[c] = prev.overrides[c];
        }
    }

    /**
     * Printout the raw content of this HourlyAdjustment's numeric adjustments.
     */
    public void printout() {
        Categories CATS = GlobOptions.get().CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        for (int c = 0; c < OVER_LEN; c++) {
            EnfuserLogger.log(Level.FINER,HourlyAdjustment.class,"\t" + modName(c) + " => " + editPrecision(this.overrides[c], 1));
        }

    }

    /**
     * Return the name of emission source category.
     *
     * @param c category index
     * @return a String source name.
     */
    public static String modName(int c) {
        Categories CATS = GlobOptions.get().CATS;
        return CATS.CATNAMES[c];
    }

    static String getHeader(int typeInt) {
        Categories CATS = GlobOptions.get().CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        String s = "DATE;TYPE;INFO;";
        for (int c = 0; c < OVER_LEN; c++) {
            s += modName(c) + ";";
        }
        return s;
    }

    /**
     * Return a compact line that can be stored to a CSV-file (data separated
     * with ";") The time, emission type and numeric adujustment factors are
     * included.
     *
     * @param longs
     * @param ops
     * @return a compact String line.
     */
    public String getString(boolean longs, FusionOptions ops) {
        Categories CATS = GlobOptions.get().CATS;
        int OVER_LEN = CATS.CATNAMES.length;
        String s = date + ";" + ops.VARA.VAR_NAME(this.typeInt) + ";" + this.modsString() + ";";
        for (int c = 0; c < OVER_LEN; c++) {

            String temp = editPrecision(this.overrides[c], 1) + ";";
            s += temp;
        }//for modTypes
        if(longs) s += infoOneLine;
        return s;
    }

    /**
     * Manually set an additive value for background component.
     *
     * @param aveBias the value to be set.
     */
    void setBgOffset(float aveBias) {
        this.overrides[CAT_BG] = aveBias;
    }

    /**
     * Operate a value using the OverrideList's numeric adjustment. The use of
     * this method adds safety since some of the numeric adjustments are
     * additive and other are multiplicative.
     *
     * @param cat the emission source category.
     * @param val the value to be operated on.
     * @return the adjusted value.
     */
    private float operate(int cat, float val) {
        if (cat == CAT_BG) {
            return val + this.overrides[CAT_BG]; 
        } else {
            return val * this.overrides[cat];
        }
    }

    /**
     * Get the numeric adjustment value for emission category.
     *
     * @param c the emission category.
     * @return the numeric adjustment value.
     */
    public float getvalue(int c) {
        return this.overrides[c];
    }

    /**
     * For addition information logging, return a descriptive text that shows
 whether the HourlyAdjustment has been subject to "fill" or "smoothing"
 operation.
     *
     * @return String text e.g., "", "F" (filled) or "S" (smoothed).
     */
    String modsString() {
        String mod = "";
        if (this.filled) {
            mod += "F,";
        }
        if (this.smoothed) {
            mod += "S";
        }
        if (this.neural) {
            mod += "N";
        }
        return mod;
    }

    /**
     * Update measurement data source credibility rating (which progress through
 time) based on the observation penalties that were adjustment by Assimilator.
     */
    void logPenaltyWeights() {
        for (int i = 0; i < this.obs.size(); i++) {
            Observation o = this.obs.get(i);
            if (o == null) {
                continue;
            }
            if (this.isPrevObs.get(i) == true) {
                continue;
            }
            StationSource st = this.sts.get(i);
            st.storeDataFusionQualityRating(o);

        }
    }

    
   public double getCQLfactoredValue(int c, FusionCore ens) {
        //get longer term memory.
         float cql = PersistentAdjustments.getLearnedScaler(
                 c, this.typeInt, ens.tzt.getDates(dt),ens);
         //combine this with the recent
         float combined =operate(c, cql);
         return combined;  
    }
   
   protected float operate(int c, FusionCore ens, float val) {
       float comp = (float)this.getCQLfactoredValue(c, ens);
       if (c == CAT_BG) {
            return Math.max(0, val + comp); 
        } else {
            return val * comp;
        }
   }
   


    public float[] getValues() {
        return this.overrides;
    }

}
