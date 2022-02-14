/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment;
import org.fmi.aq.enfuser.core.receptor.RPconcentrations;
import org.fmi.aq.enfuser.core.receptor.PredictionPrep;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.options.Categories;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.COUNTERS;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.HEADERS_SOURCTYPE;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.MODBG;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.OBS;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.PRED;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.PRED_FORC;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.PRED_MAE;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.RAW_OBS;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;

/**
 *
 * @author johanssl
 */
public class EvalDat {
    
    /**
     * A special constructor for statistical analysis of an HourlyAdjustment.
     * @param ol
     * @param met
     * @param ops 
     */
    protected EvalDat(HourlyAdjustment ol, Met met, FusionOptions ops) {
        this.met =  met;
        this.typeInt = ol.typeInt;
        this.lat = met.lat;
        this.lon = met.lon;
        this.type = ops.VAR_NAME(typeInt);
        this.timestamp_utc = ol.date;
        this.expComponents = ol.getValues();
        this.ID ="dummy";
        this.debugLine = null;
        
        statsCrunchVector = new float[HEADERS_SOURCTYPE.length];
        statsCrunchVector[COUNTERS] = 1;
    }    
    
    //meta fields  
    public final int typeInt;
    public final String type;
    final String timestamp_utc;
    public final String ID;
    public final float lat;
    public final float lon;
    public int forecastPeriod =0;
    //main stats
    final float[] statsCrunchVector;
    public float[] expComponents;
    //full statistics lines
    final String debugLine;
    public boolean loovPoint =false;
    //meteorology
    Met met;
    float varRating =1f;
    String df_result=null;
    
    public float[] overrides=null;
    public EvalDat(FusionCore ens, FusionOptions ops, Observation o) {

        if (o.incorporatedEnv!=null)this.met = o.incorporatedEnv.met;
        this.typeInt = o.typeInt;
        this.type = ops.VARA.VAR_NAME(typeInt);
        
        float exp =0;
        if (ens!=null) {
            exp = PredictionPrep.getExp(o,ens, o.typeInt);
            this.expComponents = PredictionPrep.getExps(o,ens, o.typeInt);
        }
        //switch to true leave-one-out validations if available and allowed.
        if (ops.dfOps.df_trueLOOV) {
           float[] loovExps = o.getLOOVexps(typeInt);
           if (loovExps !=null) {
               this.expComponents = loovExps;
               exp = FuserTools.sum(loovExps);
               loovPoint=true;
           }
        }
        
        this.ID = o.ID;
        this.lat = o.lat;
        this.lon = o.lon;

        Float modBG =null;
        float fusedForc =0;
        
        if (ens!=null) {
            modBG = ens.getModelledBG(o.typeInt, o.dt, o.lat, o.lon, false);//false for non-orthogonal anthropogenic reduction
            fusedForc = PredictionPrep.getForecastTestExp(o,ens, o.typeInt);//o.getStoreFusionValue_forecasted_noMT(ens, ae,false, FORECAST_HOURS);
        }
        statsCrunchVector = new float[HEADERS_SOURCTYPE.length];
        statsCrunchVector[PRED] = exp;
        statsCrunchVector[PRED_FORC] = fusedForc;
        statsCrunchVector[OBS] = o.value;
        statsCrunchVector[COUNTERS] = 1;
        statsCrunchVector[PRED_MAE] = Math.abs(exp - o.value);
        statsCrunchVector[RAW_OBS] = o.origObsVal();

        if (modBG != null) {
            statsCrunchVector[MODBG] = modBG;
        } else {
            statsCrunchVector[MODBG] = 0;
        }

        boolean neural = false;
        if (ens!=null){
            debugLine = DebugLine.getFullDebugLine_nonHeader(";", o.typeInt, o, neural, ens, met);
        } else {
            debugLine=null;
        }

        this.timestamp_utc = o.dt.getStringDate_noTS();
        this.parseDfStatus();
    }
    
     private void parseDfStatus() {
        try {
            String[] debSplit = this.debugLine.split(";");
            this.df_result = debSplit[3];
            String temp = df_result.replace("(", "START");
            String var = getBetween(temp,"START", "_");
            this.varRating = Double.valueOf(var).floatValue();
            //System.out.println("DF PARSE: "+ this.df_result+", var="+this.varRating);
        }catch (Exception e) {
          
        }
    }

    public float value() {
        return statsCrunchVector[OBS];
    }
    
    public static boolean CAT_CAPS =true;
    /**
     * in VarAssociations some category-specific max limits may have been specified.
     * This method checks the components and adjusts them to fit these limitations.
     * Also the prediction itself and MAE may be updated accordingly.
     * @param ops options (carrying VARA and category definitions).
     */
    public void applyCategoryCaps(FusionOptions ops) {
        boolean changed = applyCategoryCaps(typeInt,ops, this.expComponents);
        if(changed) {
            float nsum = 0;
            for (int c:ops.CATS.C) {
                nsum+= this.expComponents[c];
            }
            this.statsCrunchVector[PRED] = nsum;
            statsCrunchVector[PRED_MAE] = Math.abs(nsum - this.statsCrunchVector[OBS]);
        }
    }
    
     /**
     * in VarAssociations some category-specific max limits may have been specified.
     * This method checks the components and adjusts them to fit these limitations.
     * Also the prediction itself and MAE may be updated accordingly.
     * @param ops options (carrying VARA and category definitions).
     */
     public static boolean applyCategoryCaps(int typeInt,FusionOptions ops, float[] comps) {
        if (!CAT_CAPS) return false;
        double[] limits = ops.VARA.categoryCaps(typeInt, ops.CATS);
        //if no limits have been specified, these are all Double.MAX_VALUE.
        boolean changed = false;
        for (int c:ops.CATS.C) {
            double lim = limits[c];
            if (comps[c] > lim) {
                comps[c] = (float)lim;
                changed = true;
            }  
        }
        return changed;
    }

    public float modelledValue() {
        return statsCrunchVector[PRED];
    }

   private Dtime dt;

    public Dtime dt() {
        if (dt != null) {
            return dt;
        }
        dt = new Dtime(this.timestamp_utc);
        return dt;
    }

    public Met getMet() {
        return this.met;
    }

    public float getAdjustedBG() {
        return this.expComponents[Categories.CAT_BG];
    }

    public EvalDat(int typeInt,int forecastP,String timestamp_utc, String ID, double lat, double lon,
            float[] temp, float[] expComponents, String debugLine, Met met) {

        this.typeInt = typeInt;
        this.type = met.VARA.VAR_NAME(typeInt);
        this.timestamp_utc = timestamp_utc;
        this.forecastPeriod = forecastP;
        this.ID = ID;
        this.lat = (float) lat;
        this.lon = (float) lon;
        this.statsCrunchVector = temp;
        this.expComponents = expComponents;
        this.debugLine = debugLine;

        this.met = met;
        this.varRating = (float)varRating;
        this.parseDfStatus();
    }

    public String debugLine() {
        return this.debugLine;
    }
    
    /**
     * Previously, some virtual forecasting points were allowed to enter statistics
     * files, in which the forecasting hour was larger than zero and 'measured'
     * value was a copy of the model prediction. This checks whether this
     * is one of those dangerous virtual points.
     * @return true if this should not be allowed to enter StatsCruncher.
     */
    public boolean isForecastDummy() {
        if (this.forecastPeriod>0) {
            int ob = (int)(this.statsCrunchVector[StatsCruncher.OBS]*1000);
            int pred  =(int)(this.statsCrunchVector[StatsCruncher.PRED]*1000);
            return ob == pred;
        }
        return false;
    }

    float variabilityRating() {
       return this.varRating;
    }

    double rawRegional() {
        return statsCrunchVector[StatsCruncher.MODBG];
    }

    double forecastEstimate() {
       return this.statsCrunchVector[StatsCruncher.PRED_FORC];
    }

}
