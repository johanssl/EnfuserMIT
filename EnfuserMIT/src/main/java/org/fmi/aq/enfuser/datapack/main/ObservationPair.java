/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.main;

import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;

/**
 *
 * @author johanssl
 */
public class ObservationPair {

    public Observation o1;
    public Observation o2;

    public ObservationPair(Observation o1, Observation o2) {
        this.o1 = o1;
        this.o2 = o2;
    }

    public static ObservationPair getPreviousNextPair(StationSource st, Dtime dt, boolean previous) {
        Observation o1 = st.getExactObservation(dt);
        if (o1 == null) {
            return null;
        }

        Observation o2;
        if (previous) {
            o2 = st.getExactObservation(dt, -1);
        } else {
            o2 = st.getExactObservation(dt, 1);
        }

        if (o2 == null) {
            return null;
        }
        return new ObservationPair(o1, o2);

    }

    public static ArrayList<ObservationPair> stackAllPairs_forCloseByHours(StationSource st, int hour) {
        ArrayList<ObservationPair> obs = new ArrayList<>();

        for (Observation o : st.getLayerObs()) {
            if (o.dt.hour != hour) {
                continue;
            }

            ObservationPair op = getPreviousNextPair(st, o.dt, false);
            if (op != null) {
                obs.add(op);
            }
            ObservationPair op2 = getPreviousNextPair(st, o.dt, true);
            if (op != null) {
                obs.add(op2);
            }
        }

        return obs;
    }

    public static ArrayList<ObservationPair>[] getStacks_diurnal(StationSource st) {

        ArrayList<ObservationPair>[] arr = new ArrayList[24];
        for (int i = 0; i < 24; i++) {
            arr[i] = new ArrayList<>();
        }

        for (Observation o : st.getLayerObs()) {
            int h = o.dt.hour;

            ObservationPair op = getPreviousNextPair(st, o.dt, false);
            if (op != null) {
                arr[h].add(op);
            }
            ObservationPair op2 = getPreviousNextPair(st, o.dt, true);
            if (op2 != null) {
                arr[h].add(op2);
            }
        }//for obs

        return arr;
    }
    
    public static Float getDiurnalAutoCovar_1h(StationSource st) {
       
        ArrayList<ObservationPair>[] arr = getStacks_diurnal(st);
        int n=0;
        float sum=0;
        for (int h = 0; h < 24; h++) {
            ArrayList<ObservationPair> obs = arr[h];
            if (obs.isEmpty()) {
                continue;
            }
            for (ObservationPair op : obs) {
                float diff = op.getAbsoluteDifference();
                sum+= diff*diff;
                n++;
            }//for pairs

        }//for h
        if (n==0) return 0f;
        return sum/n;
        
    }

    public static float[] getDiurnalAutoCovars_1h(StationSource st) {

        float[] autocs = new float[24];
        ArrayList<ObservationPair>[] arr = getStacks_diurnal(st);

        for (int h = 0; h < 24; h++) {
            ArrayList<ObservationPair> obs = arr[h];
            if (obs.isEmpty()) {
                continue;
            }

            float cubeSum_neg = 0;
            int nk = 0;
            float cubeSum_pos = 0;
            int pk = 0;
            for (ObservationPair op : obs) {
                float diff = op.getAbsoluteDifference();
                if (op.positiveTimeDiff()) {
                    cubeSum_pos += diff * diff;
                    pk++;
                } else {
                    cubeSum_neg += diff * diff;
                    nk++;
                }
            }//for pairs

            //conclusion, take the smaller one. For traffic sites there can be
            //pretty high values when traffic ramps up from zero to large numbers,
            //and this does not reflect the use case for station autocovar plot.
            cubeSum_neg /= (nk + 0.0001f);//shield from divByZero, just in case
            cubeSum_pos /= (pk + 0.0001f);

            if (nk == 0 || cubeSum_pos < cubeSum_neg) {//note: if nk==0 then it must be so that np>0
                autocs[h] = cubeSum_pos;
            } else {
                autocs[h] = cubeSum_neg;
            }

        }//for h
        return autocs;
    }

    private float getAbsoluteDifference() {
        return Math.abs(o1.value - o2.value);
    }

    private boolean positiveTimeDiff() {
        return o2.dt.systemHours() > o1.dt.systemHours();
    }

}
