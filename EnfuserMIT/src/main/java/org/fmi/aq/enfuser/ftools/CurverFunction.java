/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

/**
 *
 * @author johanssl
 */
public class CurverFunction {

    public final float MIN;
    public final float MAX;
    public final float CURVER;

    public final float valueSpace_min;
    public final float valueSpace_max;
    public final float valueRange;

    public CurverFunction(double min, double max, double curver, float[] valueSpace) {
        MIN = (float) min;
        MAX = (float) max;
        CURVER = (float) curver;
        this.valueSpace_min = valueSpace[0];
        this.valueSpace_max = valueSpace[1];
        this.valueRange = this.valueSpace_max - this.valueSpace_min;
    }

    public float getValue(float value) {

        if (value < valueSpace_min) {
            return MIN;
        }
        if (value > valueSpace_max) {
            return MAX;
        }

//interpolate curved value which is somewhere within the valueRange
        float maxWeight = (value - valueSpace_min) / valueRange;// if value is close to min, this is close to zero. This value ALWAYS e [0,1]
//curve
        float curvedW1 = 1f - (float) Math.pow(maxWeight, CURVER);

        float curvVal = MIN * curvedW1 + (1f - curvedW1) * MAX;
        return curvVal;
    }

}
