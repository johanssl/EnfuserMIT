/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.kriging;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import java.util.ArrayList;
import static org.fmi.aq.enfuser.ftools.FastMath.radIntoDeg;
import org.fmi.aq.essentials.shg.RawDatapoint;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;

/**
 *
 * @author Lasse
 */
public class KrigingAlgorithm {

    public static CombinedValue[] calculateCombinationValues_IWD(ArrayList<Integer> typeInts,
            DataCore ens, KrigingElements ae, Observation ob, boolean print) {

        CombinedValue[] cvs = new CombinedValue[ens.ops.VARLEN()];

        for (int typeInt : typeInts) {
            if (ens.datapack.variables[typeInt] == null) {
                continue;
            }

            cvs[typeInt] = calculateCombinationValue_IWD(typeInt, ens, ae, print, ob);

        }// for typeInt
        return cvs;
    }

    /**
     * This method produces fused values for the given location and time, with
     * the given dataset This method works also with vector data, such as
     * windDirection.
     *
     * @param typeInt defines the variable for which the fusion is done.
     * DataPack must contain data for this variable
     * @param ens the core
     * @param ae an instance to hold temporary values required for the
     * operation. This can simply be new AlgorithmElements() but for multiple
     * operations a single ae is adviced to be used repeatedly.
     * @param print if true then additional console printouts are provided.
     * @param target an instance of Observation that carries the time and
     * location for which this method is used for.
     * @return a CombVal instance that holds the "fused" value with other
     * supporting information.
     */
    public static CombinedValue calculateCombinationValue_IWD(int typeInt,
            DataCore ens, KrigingElements ae,
            boolean print, Observation target) {

        VariableData vd = ens.datapack.getVarData(typeInt);
        if (vd == null) {
            return null;
        }

        ae.clear();
        ae.maxI = vd.sources().size();// amount of sources in total 

        for (int i = 0; i < vd.sources().size(); i++) {

            //source, Observation from it and some skip conditions.    
            AbstractSource sourc_i = vd.sources().get(i);
            if (print) {
                EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                        "=#=#=#=#=#=#=#=#=#=#=#=#=#=#=#=#=#=#=#=\n FAlg: Checking " 
                        + sourc_i.ID + ", " + ens.ops.VAR_NAME(sourc_i.typeInt));
            }
            //zonetype analysis -both obs profile and the profile of current grid coordinates are needed
            if (sourc_i.banned) {
                if (print) {
                    EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                            "\t This source has been BANNED (global).");
                }
                continue;
            }

            if (target.ID.equals(sourc_i.ID)) {
                if (print) {
                    EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                            "\t This source has been BANNED (local).");
                }
                continue;
            }

            AbstractSource s = vd.sources().get(i);
            Observation best = s.getSmoothObservation(target.dt, target.lat, target.lon);//getClosest(target.dt,target.lat,target.lon, ens,target.dates(ens),24);//may not fetch obs from certain sources (SILAM) 

            //best can be null if no suiteable observation is found, then substitute 
            if (best == null) {
                if (print) {
                    EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                            "\t bestObs was null for this one.");
                }
                continue;
            }

            if (print) {
                EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                        "\t" + best.dt.getStringDate() + ", value = " + best.value);
            }

            float weight;
            float var;
            float value;

            //temporal and spatial separation
            float dist_km = (float) best.getDistance_m(target.lat, target.lon) * 1000;
            float lag_tau_h = (float) Math.abs(best.dt.systemHours() - target.dt.systemHours());

            var = ens.DB.getTotalVar(sourc_i, lag_tau_h, best.dataMaturity_h(), dist_km, ens.ops) + 0.001f;
            weight = 1 / var;
            value = best.value;

            if (print) {
                EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                        "\t " + sourc_i.ID + ": " + "weight = " 
                        + weight + ", var = " + var + ", ESTIMATE FOR TARGET = " + value);
            }

            // weightsum+=weight;
            ae.add(new float[]{var, weight, value, i});

        }//for sources
        ae.finalize(false);

        if (!ens.ops.IS_METVAR(typeInt) && ae.finalValue < 0) {
            ae.finalValue = 0;//enforce non-negativity for pollutants.
        }
        float STD = (float) Math.sqrt(ae.finalVar);// + 0.025f*val; 
        CombinedValue cv = new CombinedValue(typeInt, ae.finalValue, STD);

        return cv;
    }

    public static float calculateCombinationValue_IWD(int typeInt,
            DataCore ens, Observation target) {
        KrigingElements ae = new KrigingElements();
        try {
            CombinedValue cv = calculateCombinationValue_IWD(typeInt, ens, ae, false, target);
            return cv.value;
        } catch (NullPointerException e) {
            return 0;
        }
    }


    /**
     * Simple Kriging data fusion from RawDatapoints assuming equal weight for
     * all data points.
     *
     * @param lat latitude for the point of interest.
     * @param lon longitude for the point of interest.
     * @param rds array of elements (data for the fusion)
     * @param ae temporary instance.
     * @param minDist_m minimum allowed distance (a higher value can smoothen
     * the outcome nearby the datapoints).
     * @return combined, fused value.
     */
    public static float calculateCombinationValue_IWD(float lat, float lon,
            ArrayList<RawDatapoint> rds, KrigingElements ae, float minDist_m) {

        ae.clear();
        ae.maxI = rds.size();

        for (int i = 0; i < rds.size(); i++) {

            float weight;
            float value;
            RawDatapoint rd = rds.get(i);

            float dist_m = (float) Observation.getDistance_m(lat, lon, rd.lat, rd.lon);
            if (dist_m < minDist_m) {
                dist_m = minDist_m;
            }

            weight = 1f / (dist_m + 0.001f);
            value = rd.value;

            ae.add(new float[]{1, weight, value, i});

        }//for rds
        ae.finalize(false);

        return ae.finalValue;
    }

    public static float combineWdir(ArrayList<float[]> arr) {

        float sin = 0;
        float cos = 0;

        for (float[] vals : arr) {
            float val = vals[0]; // scalar windDirection (0,360)
            float w = vals[1];

            float[] sinCos = WD_intoSinCos_kartesian(val);
            sin += w * sinCos[0];
            cos += w * sinCos[1];

        }
        return transform_kartesianToWdir(sin, cos);

    }

    public static float[] WD_intoSinCos_kartesian(float wDir) {
        //0 => blows from left to right. This is ACTUALLY 270 wdir.
        double beta;
        if (wDir >= 0 && wDir < 90) {// I
            //float wd = wDir-0; //angle between y-axis and the dir
            // wd = 90-wd;//e.g. 60 => angle diff = 60 => wd = 30;
            beta = (2 * Math.PI / 360.0) * (90 - wDir);

        } else if (wDir >= 90 && wDir < 180) { //IV
            float wd = wDir - 90; //angle between x-axis and the dir
            wd = 360 - wd;// e.g. wd = 120 => 330 in kartesian.
            beta = (2 * Math.PI / 360.0) * wd;

        } else if (wDir >= 180 && wDir < 270) {// III
            float wd = wDir - 180; //angle between y-axis and the dir
            wd = 270 - wd;//e.g. wd = 200 => y-angle = 20 => kartesian = 250
            beta = (2 * Math.PI / 360.0) * wd;

        } else if (wDir >= 270 && wDir <= 360) {//II

            float wd = wDir - 270; //angle between x-axis and the dir
            wd = 180 - wd;//e.g. wd = 200 => y-angle = 20 => kartesian = 250
            beta = (2 * Math.PI / 360.0) * wd;

        } else {
            EnfuserLogger.log(Level.WARNING,KrigingAlgorithm.class,
                  "WindDir sinCos split fail! wd = " + wDir);
            beta = 0;
        }
        float[] sinCos = FM.getFastSinCos(beta);
        return sinCos;

    }

    private static float transform_kartesianToWdir(float sin, float cos) {

        if (sin >= 0 && cos >= 0) {//I
            double arad = Math.asin(sin / 1.0f);
            float angle = (float) radIntoDeg(arad);
            return 90 - angle;

        } else if (sin <= 0 && cos >= 0) {// IV
            sin *= -1;//now it's positive.
            double arad = Math.asin(sin / 1.0f);
            float angle = (float) radIntoDeg(arad);
            return 90 + angle;
        } else if (sin <= 0 && cos <= 0) {//III

            sin *= -1;//now it's positive.
            double arad = Math.asin(sin / 1.0f);
            float angle = (float) radIntoDeg(arad);
            return 270 - angle;

        } else if (sin >= 0 && cos <= 0) {

            double arad = Math.asin(sin / 1.0f);
            float angle = (float) radIntoDeg(arad);
            return 270 + angle;

        } else {
            EnfuserLogger.log(Level.FINER,KrigingAlgorithm.class,
                    "ERROR: WindDir re-transform fail! sin = " + sin + ", cos = " + cos);
            return 0;
        }

    }

}
