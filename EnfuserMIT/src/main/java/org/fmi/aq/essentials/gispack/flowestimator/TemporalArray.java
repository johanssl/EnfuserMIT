/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import org.fmi.aq.essentials.date.Dtime;
import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public abstract class TemporalArray implements Serializable {

    private static final long serialVersionUID = 7526472294622776447L;//Important: change this IF changes are made that breaks serialization!
    protected float[] values;//this is a 3-dimensional dataset presented as 1dim array.
    protected int classLen = -1;
    public String source;
    protected float[] secondaryScalers;
    protected float[] amps;//this allows easy type-specific amplifiers to be introduced
//NOTE: this has two factors in it: e.g., daily mean and then user adjusted factor. 
    protected float[] primaryScalers;
//min value =-10, max = 10 with 0.1 increments when scaled as below.
    public byte valuesSet_percentage;

    /**
     * get the product of two amplifiers.
     *
     * @param classInd class index, that should be in between [0,classLen[
     * @return amplifier factor stored for the class
     */
    protected float getAmp(int classInd) {
        if (this.amps == null) {
            initAmps();
        }
        return this.amps[classInd] * this.primaryScalers[classInd];
    }

    protected void initAmps() {
        this.amps = new float[this.classLen];
        this.primaryScalers = new float[this.classLen];
        for (int i = 0; i < this.amps.length; i++) {
            this.amps[i] = 1f;
            this.primaryScalers[i] = 1f;
        }
    }

    /**
     * Fetch the raw index for datapoint that relates with the given class and
     * time.
     *
     * @param classI class index
     * @param dt time
     * @return get index for the one dimensional array.
     */
    public abstract int get1Dim_index(int classI, Dtime dt);

    public abstract int get1Dim_index(int classI, Dtime dt, int overrideH);

    /**
     * Get the index for secondary scaler value for the given class and time.
     * Apply, e.g., monthly-type specific modifier (index) that can be dealt as
     * post-process.
     *
     * @param classI class index
     * @param dt time
     * @return index where scaler is located.
     */
    protected abstract int getSecondaryScalerIndex(int classI, Dtime dt);

    public float getSecondaryScaler(int classI, Dtime dt) {
        if (this.secondaryScalers == null) {
            return 1f;
        }
        int ind = getSecondaryScalerIndex(classI, dt);
        return secondaryScalers[ind];
    }

    public abstract void setSecondaryScaler(int classI, Dtime dt, float amp);

    public float[] getDailyAverages(Dtime dt) {
        float[] vals = new float[classLen];

        for (int h = 0; h < 24; h++) {
            for (int c = 0; c < classLen; c++) {
                vals[c] += this.getValue(c, dt, h);
            }//for classes
        }//for hours
        return vals;
    }

    /**
     * Fetch a single value in the compactArray that relates to the given
     * classIndex and time.
     *
     * @param classI the classIndex
     * @param dt time
     * @return stored vehicle flow amount
     */
    public float getValue(int classI, Dtime dt) {
        int ind = get1Dim_index(classI, dt);
        float scaled = this.values[ind];
        scaled *= getAmp(classI);
        return scaled * getSecondaryScaler(classI, dt);
    }

    public float getValue(int classI, Dtime dt, int overrideH) {
        int ind = get1Dim_index(classI, dt, overrideH);
        float scaled = this.values[ind];
        scaled *= getAmp(classI);
        return scaled * getSecondaryScaler(classI, dt);
    }

    protected float getUnscaledValue(int classI, Dtime dt, int overrideH) {
        int ind = get1Dim_index(classI, dt, overrideH);
        return this.values[ind];
    }


    /**
     * Set a post-processing amplifier targeting the given class (index). Note:
     * this is for simpler user-defined scalers that are applied separately from
     * DailyAverageFlows (DAVGs).
     *
     * @param classI the class index
     * @param amp value set for amplification. A value of 1 has no effect.
     * @param method edit method
     */
    public void editClassAmplifier_user(int classI, float amp, int method) {

        if (method == FlowEstimator.EDIT_MULTI) {
            this.amps[classI] *= amp;
        } else if (method == FlowEstimator.EDIT_SET) {//set
            this.amps[classI] = amp;
        } else {//additive, this more difficult, since the actual amp needs to be computed 
            //the logic is, that we don't want to edit the DAVG value directly.
            //However, the effect must be equal to the case where DAVG is added with 'amp'.

            float davg = this.primaryScalers[classI];
            if (davg <= 0) {
                this.primaryScalers[classI] += amp;//this is unusual (should not happen!), perhaps system.out these cases?
                return;
            }

            //davg*x = davg + amp   => x = (davg + x)/davg
            float namp = (davg + amp) / davg;
            this.amps[classI] *= namp;
        }
    }

    public float getUserClassModifier(int c) {
        return this.amps[c];
    }

    /**
     * Reset all user-customizable scalers for all vehicle categories. (Sets a
     * value of 1.0).
     */
    public void resetUserClassAmplifiers() {
        for (int c : FlowEstimator.VC_INDS) {
            editClassAmplifier_user(c, 1, FlowEstimator.EDIT_SET);
        }
    }

    /**
     * Set a post-processing amplifier targeting the given class (index). Note:
     * this for Daily Average Flows (DAVGs) and an additive sum operation can be
     * used
     *
     * @param classI the class index
     * @param amp value set for amplification. A value of 1 has no effect.
     * @param method (EDIT_ADD) can also be used here.
     */
    public void editClassAmplifier_davg(int classI, float amp, int method) {

        if (this.primaryScalers == null) {
            this.initAmps();
        }

        if (method == FlowEstimator.EDIT_ADD) {
            this.primaryScalers[classI] += amp;
        } else if (method == FlowEstimator.EDIT_MULTI) {
            this.primaryScalers[classI] *= amp;
        } else {//set
            this.primaryScalers[classI] = amp;
        }
    }

    /**
     * Set a post-processing amplifier targeting the given class (index)
     *
     * @param classI the class index
     * @param dt time
     * @param mod multiplicative modifier
     */
    public void modify(int classI, Dtime dt, float mod) {
        int index = this.get1Dim_index(classI, dt);
        this.values[index] *= mod;
    }

    public float[] getValues_allClasses(float[] temp, Dtime dt) {
        if (temp == null) {
            temp = new float[classLen];
        }
        for (int i = 0; i < classLen; i++) {
            temp[i] = getValue(i, dt);
        }
        return temp;
    }

    public float[] getValues_allClasses_smooth(float amp, float[] temp, Dtime dt, Dtime next, int mins) {

        if (mins == 0 || next == null) {
            return getValues_allClasses(temp, dt);//it is smooth already
        }
        float secScaler = (float) mins / 60f;//e.g. if 59 then "sec" will get all the emphasis.
        float firstScaler = 1f - secScaler;//must sum up to 1 these two.

        if (temp == null) {
            temp = new float[classLen];
        }

        for (int i = 0; i < classLen; i++) {
            float first = getValue(i, dt);
            float sec = getValue(i, next);
            temp[i] = first * firstScaler + sec * secScaler;//smooth combination
            temp[i] *= amp;
        }//for classes
        return temp;
    }

}
