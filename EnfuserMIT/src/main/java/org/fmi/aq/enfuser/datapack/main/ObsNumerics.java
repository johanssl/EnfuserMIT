/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.main;

import org.fmi.aq.enfuser.datapack.source.Layer;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import java.util.ArrayList;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;

/**
 *A simple utility class that provides simple numerical/statistical properties
 * such as sums and
 * averages for values and observations.
 * @author johanssl
 */
public class ObsNumerics {

    public static double getAverage(ArrayList<Double> arr) {

        double ave = 0;

        for (int i = 0; i < arr.size(); i++) {
            ave += arr.get(i);
        }

        ave = ave / arr.size();
        return ave;

    }

    public static double getVar(ArrayList<Double> arr) {

        double ave = ObsNumerics.getAverage(arr);
        double sum = 0;
        for (int i = 0; i < arr.size(); i++) {
            sum = sum + (arr.get(i) - ave) * (arr.get(i) - ave);

        }
        sum = sum / arr.size();
        return sum;
    }
    
    public static float getVar_obs(ArrayList<Observation> obs) {

        if (obs.size()<2) return 0;

        double ave = 0;
        for (Observation o:obs) {
            ave+=o.value;
        }
        ave/=obs.size();

        float sum = 0;
        for (Observation o:obs) {
            sum += (o.value - ave) * (o.value - ave);
        }

    sum /= obs.size();
    return sum;
}

    public static double getSTD(ArrayList<Double> arr) {

        double var = ObsNumerics.getVar(arr);
        double std = Math.sqrt(var);

        return std;

    }

    /**
     * Compute the auto-covariance of measurements for each hour of day. In this
     * case the autocovar computation is done with a one hour difference: the
     * value corresponds to to average variability of two consecutive hourly
     * measurements.
     *
     * @param st the station
     * @return 24-element array of computed 1h autocovariances.
     */
    public static float[] getOneHourAutoCovars(StationSource st) {
        float[] dat = ObservationPair.getDiurnalAutoCovars_1h(st);
        return dat;
    }
    
    public static Float getOneHourAutoCovar_single(StationSource st) {
        return  ObservationPair.getDiurnalAutoCovar_1h(st);
    }

    public static double[] getAutocoVar(StationSource ss, int t0, int tMax) {

        double[] covars = new double[tMax - t0 + 1];

        Dtime[] startEnd = ss.getStartAndEnd();
        long startH = startEnd[0].systemHours();
        long endH = startEnd[1].systemHours();
        int length = (int) (endH - startH + 1);


        Observation[] obsArr = new Observation[length];
        for (Layer l : ss.layerHash.values()) {
            Observation o = l.getStationaryObs();
            int index = (int) (o.dt.systemHours() - startH);
            obsArr[index] = o;
        }

        for (int t = t0; t <= tMax; t++) {

            ArrayList<Double> errs = new ArrayList<>();

            for (int i = 0; i < obsArr.length - t; i++) {

                if (obsArr[i] == null || obsArr[i + t] == null) {
                    continue; // we need both values to evaluate difference!
                }
                double diff = obsArr[i].value - obsArr[i + t].value;
                errs.add(diff * diff); // assumption: µ_e = 0;

            }

            double autoc = ObsNumerics.getAverage(errs);
            covars[t - t0] = editPrecision(autoc, 2);

        }

        return covars;
    }

    /**
     * Get the average measurement value for the given array of Observations.
     *
     * @param obs The list for which to average
     * @return average value
     */
    public static float getAverageObservedValue(ArrayList<Observation> obs) {
        if (obs == null || obs.isEmpty()) {
            return 0;//better than NaN
        }
        float sum = 0;
        for (Observation o : obs) {
            sum += o.value;
        }
        return sum / obs.size();
    }



}// class end
