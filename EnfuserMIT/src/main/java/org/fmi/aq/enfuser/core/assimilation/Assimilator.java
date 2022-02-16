/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import static org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment.getOptimizationRanges;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import static java.lang.Math.abs;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.core.receptor.PredictionPrep.getExps;

/**
 * This class provides the tool to automatically adjust emission source category
 * strengths based on the most recent observations. By doing so, the algorithms
 * used here also apply weight penalties to measurement data points that do not
 * align with the rest of the available information,
 *
 * @author johanssl
 */
public class Assimilator {

    int typeInt;
    ArrayList<Observation> obs;
    ArrayList<StationSource> sts;//all stations, but there can be two instances for each (previous measurements).
    ArrayList<StationSource> sts_unique;
    ArrayList<Boolean> isPrevObs = new ArrayList<>();
    float[][] expComponents;

    private PDescent best_sd;
    Dtime time;
    private Dtime time_prev;

    public final float HISTORY_W;//how much emphasis is being put to previous (-1h) data?

    Dtime[] allDates;
    FusionCore ens;
    int trueObsSize;
    protected final boolean secondary;
    protected final static Observation DUMMY_NULL = null;
    final VarAssociations VARA;
    final Categories CATS;

    public final float[] cons_cat_share100;
    /**
    * True Leave-one-out validation. 
    * if true, then a separate data fusion sweep will be done
    * for EACH measurement location while banning it. This will require
    * a lot more time to complete depending on the amount of measurement locations.
     */
    final boolean trueLOOV;
    StationSource currLOOV;
    
    public float minObs = Float.MAX_VALUE;
    public float minBG = Float.MAX_VALUE;
    public float maxBG = 0;
    public int overIters = 0;
    public int penaltyIters = 0;
    private WSSEbuilder builder;
    ArrayList<Float>[] catIters;

    /**
     * Constructor for Adapter.
     *
     * @param typeInt variable type for which the Override assessment is done
     * for.
     * @param bounds bounds to limit measurement data extraction.
     * @param time the time (hour) for which the assessment is done for.
     * @param ens FusionCore instance that holds data.
     */
    private Assimilator(int typeInt, Boundaries bounds, Dtime time,
            FusionCore ens) {
        
        VARA = ens.ops.VARA;
        CATS = GlobOptions.get().CATS;
        cons_cat_share100 = new float[CATS.CATNAMES.length];
        
        this.HISTORY_W = ens.ops.dfOps.df_history_w;//history_w a weighting factor to take into account previous hour's data [0,1].
        builder = new WSSEbuilder(ens.ops);
        
        this.typeInt = typeInt;
        this.trueLOOV = ens.ops.dfOps.df_trueLOOV;
        this.time = time.clone();
        this.time_prev = time.clone();
        this.time_prev.addSeconds(-3600);
        secondary = VARA.isSecondary[typeInt];
        allDates = ens.tzt.getDates(this.time);
        VariableData vd = ens.datapack.getVarData(typeInt);
        obs = new ArrayList<>();

        this.sts = new ArrayList<>();
        this.sts_unique = new ArrayList<>();
        this.ens = ens;
        this.trueObsSize = 0;
        AdjustmentBank oc = ens.getCurrentOverridesController();
        HourlyAdjustment existing = oc.get(this.time, typeInt, ens.ops);//Note: if this already exists this method does NOTHING.

        for (AbstractSource s : vd.sources()) {
            if (existing != null) {
                continue;//do nothing if there is an override already
            }
            if (s.banned || !s.dom.isStationary()) {
                continue; //leave one out -evaluation is to be done on stationary sources only.
            }                          //if the source has ALREADY been banned then this source has been left out from the datapool by the user and is thus skipped always
            if (!(s instanceof StationSource)) {
                continue;
            }
             
            StationSource st = (StationSource) s;
            if (ens.ops.dfOps.dataFusion_ignoreQualityLimit <= st.dbl.varScaler(typeInt) ) {
                continue;
            }

            if (bounds != null && !bounds.intersectsOrContains(st.lat(), st.lon())) {
                continue; //this stationary source is too far away.
            }
            sts.add(st);
            
            //fetch the observation that will be compared against fusion estimate
            Observation o = st.getExactObservation(time);
            this.isPrevObs.add(Boolean.FALSE);
            if (o == null || o.dt.systemHours() != this.time.systemHours()) {
                obs.add(DUMMY_NULL);
            } else {//true stat. observation found for this hour!
                trueObsSize++;
                obs.add(o);
                if (!sts_unique.contains(st))sts_unique.add(st);
                
                if (o.value < minObs) {
                    minObs = o.value;
                }

            }

            //HISTORICAL PREVIOUS MEASUREMENT FOR CONTINUITY==========
            if (HISTORY_W > 0) {
                sts.add(st);
                this.isPrevObs.add(Boolean.TRUE);
                //fetch the observation that will be compared against fusion estimate
                Observation op = st.getExactObservation(time_prev);
                if (op == null || op.dt.systemHours() != time_prev.systemHours()) {
                    obs.add(DUMMY_NULL);
                } else {//true stat. observation found for this hour!
                    trueObsSize++;
                    obs.add(op);
                    if (op.value < minObs) {
                        minObs = op.value;
                    }

                }
            }//if previous measurements are included

        }//for sourcs

        //this.weights = new float[obs.size()];
        this.expComponents = new float[obs.size()][];
        this.builder.initWeights(this, ens);
        float esum = 0;
        for (int i = 0; i < obs.size(); i++) {
            if (obs.get(i) == null) {
                continue;
            }

            float[] exps = getExps(obs.get(i),ens, typeInt);
            this.expComponents[i] = exps;

            float bg = exps[CAT_BG];
            if (bg > this.maxBG) {
                maxBG = bg;
            }
            if (bg < this.minBG) {
                minBG = bg;
            }
            for (int c : CATS.C) {
                this.cons_cat_share100[c] += abs(exps[c]);
                esum += abs(exps[c]);
            }

        }//for obs

        for (int c : CATS.C) {
            this.cons_cat_share100[c] = (100f * this.cons_cat_share100[c]) / (esum + 0.0001f);
            //relative contribution [%] of emission source category
            //for O3 use Math.abs() since contribution can be negative
        }

        if (this.trueObsSize > 0) {
            catIters = getOptimizationRanges(null, this);
        }

    }

    public WSSEbuilder getDataFusionParams() {
        return this.builder;
    }

    /**
     * Launch the full optimization sequence for discrete gradient descend.
     *
     * @param ens FusionCore to hold data this iteration needs.
     * @return an PDescent object that holds the result of this algorithm,
 Including an HourlyAdjustment.
     *
     */
    private PDescent optimize(FusionCore ens) {
        //check if there is insufficient amount of measurement data to complete the task.
        if (this.trueObsSize == 0) {
            return null;
        } 

        if (this.trueLOOV) {//launch full iteration where
            //each measurement point get's its own run for data fusion.
            //outcome will be stored to observation with flagLoov().
            optimizeWithLOOV();
        }
        
        ADescent od = new ADescent(this);
        od.descendToMinimum();
        this.best_sd = od.best;
        if (this.best_sd == null) {
            return null;
        }

        this.best_sd.flagObsPenalties();

        if (ens.g.getLoggingLevel().intValue()<= Level.FINER.intValue()) {
            EnfuserLogger.log(Level.FINER,Assimilator.class,"OverIters = " + this.overIters + ", penaltyIters = "
                    + this.penaltyIters + ", minErr = " + (int) (10000 * od.currentMin)
                    + ", maxErr = " + (int) (10000 * od.worst));

            for (int i = 0; i < catIters.length; i++) {
                String nam = HourlyAdjustment.modName(i);
                EnfuserLogger.log(Level.FINER,Assimilator.class,"Override length: " + catIters[i].size() + ", " + nam);
            }

        }

        return best_sd;
    }
    
    /**
     * Run the data fusion separately for each measurement location
     * while omitting it. The outcome will be stored to the measurement
     * currently being iterated with flagLoov().
     */ 
    private void optimizeWithLOOV() {
        
        if (sts_unique.size() <= 1) {//insufficient data.
            return;
        }
        
        ADescent od;
        for (StationSource st:this.sts_unique) {
            this.best_sd = null;
            this.currLOOV = st;
            od = new ADescent(this);
            od.descendToMinimum();
            this.best_sd = od.best;
            
            if (this.best_sd != null) {
                this.best_sd.flagLOOV();
            }
    
        }//for stations
        
        //null the temporary states so that a default data fusion algorthm run
        //can be invoked afterwards.
         best_sd=null;
         this.currLOOV =null;
    }

    /**
     * A simple check to assess whether an HourlyAdjustment should be created.
     *
     * @param ens the core
     * @param time time
     * @param typeInt modelling variable type index (only primaries are allowed)
     * @param print true for more console printouts
     * @return boolean value if an HourlyAdjustment should be created for the this
 time.
     */
    private static boolean shouldBeProcessed(FusionCore ens, Dtime time, int typeInt, boolean print) {

        if (ens.ops.VARA.isMetVar[typeInt]) {
            EnfuserLogger.log(Level.FINER,Assimilator.class,"Skip optimize overrides for " + time.getStringDate()
                    + ": is Meteorological variable " + ens.ops.VAR_NAME(typeInt));
            return false;
        }

        AdjustmentBank oc = ens.getCurrentOverridesController();
        HourlyAdjustment existing = oc.get(time.systemHours(), 0, typeInt, ens.ops);
        if (existing != null) {
            if (print) {
                EnfuserLogger.log(Level.FINER,Assimilator.class,"Existing override found for " + time.getStringDate()
                        + ", type = " + ens.ops.VAR_NAME(typeInt));
            }
            return false;
        }
        
        //check type
        if (ens.ops.VARA.isSecondary[typeInt]) {
            return ens.ops.dataFusionAllowedForSecondary(typeInt);
        }

        return true;

    }

    /**
     * This is a wrapper method for the creation of an Assimilator and then for the
 use of Assimilator.optimize().
     * @param ens FusionCore for data availability
     * @param time tme (hour) for which the assessment is done
     * @param typeInt the type (index) of data the assessment is done. The
 Assimilator can only be used for primary types and therefore an incorrect
 choice of typeInt may result in a warning and a 'null' return.
     * @param bounds area for measurement extraction and use.
     * @return an HourlyAdjustment which contains the results of the algorithm.
     */
    public static HourlyAdjustment evaluateOverrideConditions_new(FusionCore ens, Dtime time,
            int typeInt, Boundaries bounds) {

        boolean ok = shouldBeProcessed(ens, time, typeInt, true);
        if (!ok) {
            return null;
        }
        
        if (ens.ops.dfOps.dfDisabled()) {
            return ens.getCurrentOverridesController().getStoreDummy(ens.ops,typeInt,time);
        }
        
        
        Assimilator a = new Assimilator(typeInt, ens.ops.bounds(), time, ens);
        if (a.trueObsSize == 0) {
            EnfuserLogger.log(Level.FINER,Assimilator.class,"Cannot optimize overrides for " + time.getStringDate() 
                    + ": no observations present for " + ens.ops.VAR_NAME(typeInt) 
                    + " (this warning can be ignored for forecasted hours)");
            return null;
        }
        
            EnfuserLogger.log(Level.FINER,Assimilator.class,"Overrides for: " + time.getStringDate());
        

        try {
            PDescent pd = a.optimize(ens);//true for automatic addition for HourlyAdjustment to OverrideController
            if (pd == null) {
                return null;
            } 
            if (ens.g.getLoggingLevel().intValue()<= Level.FINER.intValue()) {
                EnfuserLogger.log(Level.FINER,Assimilator.class,pd.ol.stringo);
                pd.ol.printout();
            }
            //automatic learning
            if (PersistentAdjustments.IS_LEARNING) {
                    EnfuserLogger.log(Level.FINER,Assimilator.class,"Adapter: automatic learning initiated for " 
                            + time.getStringDate() + " - " + ens.ops.VAR_NAME(typeInt));
                
                PersistentAdjustments.updateValue(pd.ol, ens.tzt.getDates(time),ens);
            }
            return pd.ol;

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.WARNING,Assimilator.class,"DataFusion failure for "
                    + time.getStringDate() +", "+ ens.ops.VAR_NAME(typeInt));
            return null;
        }

    }

}
