/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.kriging;

/**
 *
 * @author johanssl
 */

/*This is a basic wrapper class to hold fused value, source weights and STD
 * 
 */
public class CombinedValue {

    public final int typeInt;
    public float value;
    public final float STD;

    public float[] weights_percent;
    public float offSetSum;


    public CombinedValue(int typeInt, float value, float std) {
        this.typeInt = typeInt;
        this.value = value;
        this.STD = std;
    }

    public void addWeights_percent(KrigingElements ae) {
        weights_percent = ae.getSourceWeights_percent();
    }

}
