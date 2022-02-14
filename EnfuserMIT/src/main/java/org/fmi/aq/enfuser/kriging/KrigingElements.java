/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.kriging;

import static org.fmi.aq.enfuser.kriging.KrigingAlgorithm.combineWdir;
import java.util.ArrayList;

/**
 *
 * @author johanssl
 */
public class KrigingElements {

    private final ArrayList<float[]> var_weight_val_i;
    public int maxI = 1;

    public float backupValue = 0;
    public float weightsum = 0;
    public float finalVar = 0;
    public float finalValue = 0;

    public static final int VAR = 0;
    public static final int WEIGHT = 1;
    public static final int VALUE = 2;
    public static final int SOURC_I = 3;

    public KrigingElements() {
        var_weight_val_i = new ArrayList<>();
    }

    public void add(float[] dat) {
        // this.obs.add(o);
        this.var_weight_val_i.add(dat);
        this.weightsum += dat[WEIGHT];
    }

    public void clear() {
        this.var_weight_val_i.clear();
        weightsum = 0;
        finalVar = 0;
        finalValue = 0;
        this.backupValue = 0;
    }

    float[] getSourceWeights_percent() {
        float[] sw = new float[maxI];
        for (float[] ff : this.var_weight_val_i) {
            int index = (int) ff[SOURC_I];// source index as it is in VariableData
            sw[index] = 100.0f * ff[WEIGHT] / this.weightsum;
        }
        return sw;
    }

    public float computeVar_locAnalysis(float[] addDat) {

        float var = 0;
        float wsum = this.weightsum;
        if (addDat != null) {
            wsum += addDat[WEIGHT];

            float w = addDat[WEIGHT] / wsum;//normalize
            var += addDat[VAR] * w * w; // this sums every d2^2*VAR(obs)

        }

        for (float[] dat : var_weight_val_i) {

            float w = dat[WEIGHT] / wsum;//normalize
            var += dat[VAR] * w * w; // this sums every d2^2*VAR(obs)
        }

        return var;
    }

    public void finalize(boolean wdir) {
        ArrayList<float[]> arr = null;
        if (wdir) {
            arr = new ArrayList<>();
        }

        finalValue = 0;
        finalVar = 0;
        if (this.var_weight_val_i.isEmpty()) {
            finalValue = this.backupValue;
        }

        for (float[] dat : var_weight_val_i) {

            float w = dat[WEIGHT] / weightsum;//normalize
            finalValue += w * dat[VALUE];	// this sums every d2*OBS
            finalVar += dat[VAR] * w * w; // this sums every d2^2*VAR(obs)
            if (wdir) {
                arr.add(new float[]{dat[VALUE], w});
            }
        }

        if (wdir) {
            finalValue = combineWdir(arr);//vector, must be dealt differently   
        }
    }
}
