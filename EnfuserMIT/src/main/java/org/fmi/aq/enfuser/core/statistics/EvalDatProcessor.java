/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import static org.fmi.aq.enfuser.core.output.OutputManager.trunkatedTime;
import org.fmi.aq.enfuser.core.receptor.PredictionPrep;

/**
 *
 * @author johanssl
 */
public class EvalDatProcessor {

    public final static String META_IDENTIFIER = "IDENTIFICATION";
    public final static String MAIN_STATS_ID = "MAIN_STATS";
    public final static String EXP_COMP_ID = "PREDICTION_COMPONENTS";
    public final static String STATS_LINE_ID = "STATS_LINE";
    public final static String MET_ID = "METEOROLOGY";

    public static ArrayList<String> toArchiveStrings(EvalDat dat) {

        ArrayList<String> arr = new ArrayList<>();
        //meta line
        String metaLine = META_IDENTIFIER
                + ";" + dat.type // [1]
                + ";" + dat.ID // [2]
                + ";" + (double) dat.lat // [3]
                + ";" + (double) dat.lon// [4]
                + ";" + dat.timestamp_utc // [5]
                + ";" + 0 + ";"; // [6] //used to be the awkward and dangerous forecastingPeriod.
        arr.add(metaLine);

        //main stats
        String main = MAIN_STATS_ID + ";";
        for (float f : dat.statsCrunchVector) {
            main += (double) f + ";";
        }
        arr.add(main);

        //exp components
        String exps = EXP_COMP_ID +";";
        for (float f : dat.expComponents) {
            exps += (double) f + ";";
        }
        arr.add(exps);

        //stat-lines
        arr.add(STATS_LINE_ID + ";" + dat.debugLine);
        // arr.add(LONGSTATS_LINE_ID+";" + dat.debugLine_neural);

        String mets = MET_ID + ";";
        mets += dat.met.metValsToStringWithExpl(";");
        arr.add(mets);
        arr.add("END;");
        return arr;
    }

    /**
     * For each of the given variable types and time span, fetch all hourly observations
     * and create EvalDat instances for them.
     * @param typeInts list of variables
     * @param ens the core
     * @param dts list of dates for which observations are searched for
     * @return all created EvalDat instances.
     */
    public static ArrayList<EvalDat> getEvaluationDatasets(ArrayList<Integer> typeInts,
            FusionCore ens,ArrayList<Dtime> dts) {

        ArrayList<EvalDat> dats = new ArrayList<>();

        for (int typeInt : typeInts) {
            VariableData vd = ens.datapack.variables[typeInt];
            if (vd == null) {
                continue;
            }


            for (AbstractSource s : vd.sources()) {
               
                if (s instanceof StationSource) {
                    StationSource st = (StationSource) s;

                    for (Dtime dt : dts) {
                        Observation o = st.getExactObservation(dt);

                        if (o != null) {
                            PredictionPrep.getExp(o,ens, typeInt);//will trigger Env and Met-creation, which is needed. 
                            EvalDat dat = new EvalDat(ens,ens.ops, o);
                            dats.add(dat);
                        }
                    }//for time
                }//if stationary source
            }//for sources 
        }//for type
        return dats;
    }
    
     /**
     * For each of the given variable types and time span, create dummy Observations
     * for future hours for which there are now measurement data,
     * and create EvalDat instances for them. This can be used
     * for create forcasting points for later performance evaluation.
     * @param typeInts list of variables
     * @param ens the core
     * @param dts list of dates for which observations are searched for
     * @return all created EvalDat instances.
     */
    public static ArrayList<EvalDat> getEvaluationDatasets_forecast(ArrayList<Integer> typeInts,
            FusionCore ens,ArrayList<Dtime> dts) {

        ArrayList<EvalDat> dats = new ArrayList<>();
        Dtime now = Dtime.getSystemDate_FH();
        for (int typeInt : typeInts) {
            VariableData vd = ens.datapack.variables[typeInt];
            if (vd == null) {
                continue;
            }

            for (AbstractSource s : vd.sources()) {
                if (s instanceof StationSource) {
                    StationSource st = (StationSource) s;

                    for (Dtime dt : dts) {
                        if (dt.systemHours()< now.systemHours()) continue;
                        int diff = dt.systemHours()- now.systemHours();
                        //by definition cannot be a forecast point
                        Observation o = st.getExactObservation(dt);
                        if (o != null) continue;
                        
                        //create dummy observation
                        o = new Observation(st.typeInt, st.dbl, dt.clone(), -1, st.ID);
                            PredictionPrep.getExp(o,ens, typeInt);//will trigger Env and Met-creation, which is needed. 
                            EvalDat dat = new EvalDat(ens,ens.ops, o);
                            dat.forecastPeriod = diff;
                            dats.add(dat);  
                    }//for time
                }//if stationary source
            }//for sources 
        }//for type
        return dats;
    }

    public final static String STATC_BASENAME = "statCrunchData_";

    public static File storeEvaluationDataToFile(String dir, ArrayList<Integer> typeInts, FusionCore ens,
            Dtime start, Dtime end, boolean allowLOOV) {
        ArrayList<String> dirs = new ArrayList<>();
        dirs.add(dir);
        return storeEvaluationDataToFile(dirs, typeInts, ens,
                start, end,allowLOOV).get(0);
    }

    private static ArrayList<File> storeEvaluationDataToFile(ArrayList<String> dirs,
            ArrayList<Integer> typeInts, FusionCore ens,
            Dtime start, Dtime end, boolean allowLOOV) {

        ArrayList<File> outs = new ArrayList<>();
        ArrayList<Dtime> dts = new ArrayList<>();

        int span = end.systemHours() - start.systemHours();
        for (int h = 0; h <= span; h++) {
            Dtime dt = start.clone();
            dt.addSeconds(h * 3600 - dt.min * 60 - dt.sec);
            dts.add(dt);
        }

        ArrayList<EvalDat> dats = getEvaluationDatasets(typeInts, ens, dts);

        ArrayList<String> lines = new ArrayList<>();
        lines.addAll(getGeneralInfo(ens));

        for (EvalDat dat : dats) {
            lines.addAll( toArchiveStrings(dat) );
        }

        String endPart;
        if (span == 0) {
            endPart = trunkatedTime(start) + ".csv";
        } else {
            endPart = trunkatedTime(start) + "_" + trunkatedTime(start) + ".csv";
        }

        String filename = STATC_BASENAME + endPart;
        if (allowLOOV) filename = "loovCrunchData_" + endPart;
        for (String dir : dirs) {
            FileOps.printOutALtoFile2(dir, lines, filename, false);
            outs.add(new File(dir + filename));
        }

        return outs;
    }
    
    
    
    
    public final static String INFO_CATEGORIES = "INFO_CATEGORIES";
    public final static String INFO_VARIABLES = "INFO_VARIABLES";
    public final static String INFO_DEBUGS = "INFO_DEBUG_HEADER";

    private static ArrayList<String> getGeneralInfo(FusionCore ens) {

        ArrayList<String> arr = new ArrayList<>();
        String line = INFO_CATEGORIES + ";";
        for (String cat : ens.ops.CATS.CATNAMES) {
            line += cat + ";";
        }
        arr.add(line);

        arr.add("INFO_LAUNCH_TIME;" + ens.ops.creationTime().getStringDate() + ";");
        arr.add("INFO_AREA;" + ens.ops.areaID() + ";" + ens.ops.bounds().toText_fileFriendly(4) + ";");
        arr.add(INFO_DEBUGS + ";" + DebugLine.getFullDebugLine_HEADER(";",ens.ops));

        String varline = INFO_VARIABLES + ";";
        for (int i = 0; i < ens.ops.VARLEN(); i++) {
            varline += ens.ops.VARA.VAR_NAME(i) + ";";
        }
        arr.add(varline);

        return arr;
    }

}
