/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import static org.fmi.aq.enfuser.datapack.main.TZT.LOCAL;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import org.fmi.aq.enfuser.core.FusionCore;
import java.util.ArrayList;
import org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_BUS;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_CAR;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_HEAVY;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.options.VarAssociations;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;

/**
 *
 * @author johanssl
 */
public class DebugLine {

    public final static int I_TIME = 0;
    public final static int I_PRED = 1;
    public final static int I_OBS = 2;
    public final static int I_CREDPEN = 3;
    public final static int I_ERR = 4;
    public final static int I_AERR = 5;

    public final static int I_RAWBG = 6;
    public final static int I_ADJBG = 7;
    public final static int I_MONTH = 8;
    public final static int I_LOCH = 9;
    public final static int I_WEEKD = 10;
    public final static int I_WEEKD_S = 11;

    public final static int I_DOM_ROADREF = 12;
    public final static int I_VEHICS = 13;
    public final static int I_BUS = 14;
    public final static int I_HEAVY = 15;
    public final static int I_CONG = 16;
    public final static int I_RFLOW = 17;
    public final static int I_PATS = 18;

    public final static int I_HARD_BLOCK = 19;
    public final static int I_NBLOCK = 20;
    public final static int I_MIRRORING = 21;
    public final static int I_ELEBLOCK = 22;

    public final static int I_NO2L = 23;
    public final static int I_RAWRESP = 24;
    public final static int I_STUD_FRAC = 25;
    
    public final static int I_SANDSALT = 26;
    public final static int I_BINDING = 27;
    public final static int I_BRUSHING = 28;

    public final static int I_LOGW = 29;
    public final static int I_AVETEMP = 30;
    public final static int I_AVECLOUD = 31;
    public final static int I_AVEWS = 32;
    public final static int I_AVEHUM= 33;
    public final static int I_FORCMODE_PRED = 34;
    

    public final static int IND_NAME = 0;
    public final static String[][] DEBUGS = {
        {"TIME"                 ,"skip_neural"},//0
        {"PREDICTION"           ,""},
        {"OBSERVED"             ,""},
        {"OBSERVATION_RELIABILITY"      ,"skip_neural"},
        {"OBSERVATION_ERROR"              ,""},//the value to predict for Neural Net //RF
        {"OBSERVATION_ABS_ERR"          ,"skip_neural"},//5

        {"RAW_BACKGROUND"       ,""},//6 
        {"DATAFUSED_BG"          , ""},
        {"MONTH"                , ""},
        {"HOUR_LOCAL"           , ""},
        {"WEEKDAY"              , ""},
        {"WEEKDAY_SIMPLE"       , ""}, //11

        {"CLOSEST_ROAD_REF", "skip_neural"}, //12
        {"CARS_PER_H"           , ""},
        {"BUS_PER_H"            , ""},
        {"HEAVY_PER_H"          , ""},
        {"CONGESTION_INDEX"     , ""},
        {"TELECOM_FLOW_DATA"    , ""},//17 
        {"PERFECT_FIT_TRAFFIC_SCALER", "skip_neural"},//18 

        {"URBAN_OBSTACLE_INDEX" , ""}, //19
        {"NATURAL_OBSTACLE_INDEX", ""},
        {"STREET_CANYON_BENDING", ""},
        {"RELATIVE_ELEVATION_REDUCTION"  , ""},//22

        {"NOX_O3_GAP"           , ""}, //23
        {"RAW_RESUSPENSION"     , "dust"},
        {"STUDTYRE_FRAC"        , "dust"}, //25
        
        {"DUST_SANDING_SALTING", "dust"},//26
        {"DUST_BINDING"        ,"dust"}, //27
        {"DUST_BRSUHING"       ,"dust"},//28


        {"LOGWIND"              , ""},//29
        {"DAY_TEMPERATURE"      , ""},//30
        {"DAY_CLOUDINESS"       , ""},//31
        {"DAY_WSPEED"           , ""},//32
        {"DAY_HUMIDITY"         , ""},//33
        
        {"FORECASTED_PREDICTION","skip_neural"}//34
    };


    public static String buildString(int index,
            ObservationSupport os, FusionOptions ops) {

        //skips 
        /*
        if (forNeural && DEBUGS[index][IND_EXCLUDES].contains("neural")) {
            return FuserTools.EMPTY_STRING;
        }

        if (!dust && DEBUGS[index][IND_EXCLUDES].contains("dust")) {
            return FuserTools.EMPTY_STRING;
        }
        */
        
        //case by case
        switch (index) {
            case I_TIME:
                return os.o.dt.getStringDate();
            case I_PRED:
                return (int) (os.exp * 100) / 100f + "";
            case I_OBS:
                return (int) (os.o.value * 100) / 100f + "";
            case I_CREDPEN:
                return os.o.reliabilityString();
            case I_ERR:
                return (os.o.value - os.exp) + "";
            case I_AERR:
                return Math.abs(os.o.value - os.exp) + "";

            case I_RAWBG:
                return os.modBG + "";
            case I_ADJBG:
                return os.exps[CAT_BG] + "";

            case I_MONTH:
                return os.o.dt.month_011 + "";
            case I_LOCH:
                return os.dts[LOCAL].hour + "";
            case I_WEEKD:
                return StatsCruncher.weekDayString(os.dts[LOCAL], false);
            case I_WEEKD_S:
                return StatsCruncher.weekDayString(os.dts[LOCAL], true);

            case I_DOM_ROADREF:
                return os.dominantRoadRef();
            case I_VEHICS:
                return (int)os.vehics[IND_CAR] + "";
            case I_BUS:
                return (int)os.vehics[IND_BUS] + "";
            case I_HEAVY:
                return (int)os.vehics[IND_HEAVY] + "";
            case I_CONG:
                return os.congestion + "";
            case I_RFLOW:
                return os.flowData + "";
            case I_PATS:
                return getPATS(os.o, os.exps) + "";

            case I_HARD_BLOCK:
                return os.hardBlock();
     
            case I_NBLOCK:
                return os.naturalBlock();
                
            case I_MIRRORING:
                return os.mirroring();
                
            case I_ELEBLOCK:
                return os.eleBlock();   

            case I_NO2L:
                return os.o.incorporatedEnv.ccat.no2PotentialReduct + "";
            case I_RAWRESP:
                return (int)(100*os.o.incorporatedEnv.ccat.resusp_raw) + "";
            case I_STUD_FRAC:
                return os.studdedFrac();
                
            case I_SANDSALT:
                return os.dustSanding();
                
            case I_BINDING:
                return os.dustBinding();
                
           case I_BRUSHING:
                return os.dustBrushing();
                    
            case I_LOGW:
                return editPrecision(os.o.incorporatedEnv.logWind,2) + "";
            case I_AVETEMP:
                return os.getMetAverage(ops.VARA.VAR_TEMP);
            case I_AVECLOUD:
                return os.getMetAverage(ops.VARA.VAR_SKYCOND);
            case I_AVEWS:
                return os.getMetAverage(ops.VARA.VAR_WINDSPEED);
            case I_AVEHUM:
                return os.getMetAverage(ops.VARA.VAR_RELHUMID);
                
            case I_FORCMODE_PRED:
                return (int) (os.exp_forc * 100) / 100f + "";

            default:
                EnfuserLogger.log(Level.FINER,DebugLine.class,
                        "DebugLine: method not supported for " + DEBUGS[index][IND_NAME]);
                return "NULL";
        }

    }

    public static String getFullDebugLine_HEADER(String sep, FusionOptions ops) {
       String line = "";
        ArrayList<String> arr = getFullDebugLine_HEADER(ops);
        for (String s : arr) {
            line += s.replace(",", "_") + sep;
        }
        return line;
    }

    public static String getFullDebugLine_nonHeader(String sep, int typeInt, Observation o,
            boolean forNeural, FusionCore ens, Met met) {

        String line = "";
        ArrayList<String> arr = getFullDebugLine_nonHeader(typeInt, o, forNeural, ens, met);
        for (String s : arr) {
            if(s==null) {
                line+=FuserTools.EMPTY_STRING+sep;
                continue;
            }
            line += s.replace(",", "_") + sep;
        }
        return line;
    }

    public static ArrayList<String> getFullDebugLine_nonHeader(int typeInt, Observation o,
            boolean forNeural, FusionCore ens, Met met) {

        ArrayList<String> arr = new ArrayList<>();
        //boolean dust = (typeInt == ens.ops.VARA.VAR_PM10 || typeInt == ens.ops.VARA.VAR_COARSE_PM);
        ObservationSupport os = new ObservationSupport(o, ens, met, null);
        
        for (int i = 0; i < DEBUGS.length; i++) {
                String s = buildString(i, os, ens.ops);
                arr.add(s);
        }

        //components
        for (int c : ens.ops.CATS.C) {
                float f = os.exps[c];
                String s = (int) (f * 100) / 100f + "";
                arr.add(s);
        }

        //mets
            for (Float metf : os.met.allMetVars()) {
                if (metf == null) {
                    arr.add(FuserTools.EMPTY_STRING);
                } else {
                    
                    int prec =1;
                    if (metf < 1) {
                        prec = 4;
                    } else if (metf < 100) {
                        prec =2;
                    } 
                    
                    arr.add(editPrecision(metf,prec) + "");
                }
            }//for metvars

            addRegionalBGs_csvString(o, os.dts, ens, arr,ens.ops.VARA);
       
             HourlyAdjustment ol=ens.getOverrideForObservation(os.o,FusionCore.DF_OVERRIDE);
             for (int c:ens.ops.CATS.C) {
                 if (ol!=null ) {
                     arr.add(editPrecision(ol.getvalue(c),2)+"");
                 } else {
                        arr.add(FuserTools.EMPTY_STRING);  
                 }
             }

         FusionCore.addCustomDebugs(ens,arr,o,false);
        return arr;
    }
    
    public static ArrayList<String> getFullDebugLine_HEADER(FusionOptions ops) {
      
        ArrayList<String> arr = new ArrayList<>();
        for (int i = 0; i < DEBUGS.length; i++) {
                arr.add(DEBUGS[i][IND_NAME]); 
        }
        //components
        for (int c : ops.CATS.C) {
            arr.add(ops.CATS.CATNAMES[c]);
        }
        //mets
        Met.addMetValsHeaders(arr,ops);
        addRegionalBGs_csvString(null, null, null, arr,ops.VARA);
        
             for (int c:ops.CATS.C) {
                  arr.add("override_"+ops.CATS.CATNAMES[c]);
             }

        FusionCore.addCustomDebugs(null,arr,null,true);
        return arr;
    }
    
        public static void addRegionalBGs_csvString(Observation o, Dtime[] dts,
            FusionCore ens, ArrayList<String> arr, VarAssociations VARA) {
      
        for (int i = 0; i <VARA.isPrimary.length; i++) {
          
                if (VARA.isMetVar[i]) {
                    continue;
                }

                if (o == null) {//header
                    arr.add("BG_" + VARA.VAR_NAME(i));
                    continue;
                }

                Float modBG = ens.getModelledBG(i, o.dt, o.lat, o.lon, false);;//false for non-orthogonal anthropogenic reduction
                if (modBG != null) {
                    arr.add(editPrecision(modBG, 2) + "");
                } else {
                    arr.add(FuserTools.EMPTY_STRING);
                }

        }//for all types that are not Met
    }
        
    private final static double[] PATS_ACCEPT_RANGE = {0.1, 5};

    /**
     * Perfect Agreement with Traffic contribution Scaling.Assuming that the
     * difference between modelled and observed concentration is due to
     * imperfect traffic emission modelling, this statistics can give
     * information on the local traffic through statistical analysis.
     *
     * @param o the observation
     * @param expComps category-specific component representation of the model
     * prediction
     * @return String for the PATS value.
     */
    protected static String getPATS(Observation o, float[] expComps) {

        //get the traffic contribution
        float exp = 0;

        for (float f : expComps) {
            exp += f;
        }
        float trComp = expComps[Categories.CAT_TRAF];
        float exp_nonTr = exp - trComp;
        if (trComp / exp < 0.05) {
            return "-"; //there must be a substantial traffic contribution for this to mean something.
        }
        //obs - exp = diff
        //obs - (exp_nonTr + a*exp_tr) = 0 => the difference is zero with the modification
        // => (obs - exp_nonTr)/exp_tr = a
        float scaler = (o.value - exp_nonTr) / trComp;
        if (scaler > PATS_ACCEPT_RANGE[0] && scaler < PATS_ACCEPT_RANGE[1]) {
            return editPrecision(scaler, 2) + "";
        } else {//negative scaling to traffic means that the prediction error cannot be due to traffic emission modelling.
            return "-";
        }
    }    

}
