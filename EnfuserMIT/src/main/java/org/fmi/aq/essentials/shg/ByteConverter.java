/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import java.io.Serializable;

//import shg.sparse.SparseMap;
/**
 *
 * @author Lasse
 */
public class ByteConverter implements Serializable {

    private static final long serialVersionUID = 7526472293822836147L;//Important: change this IF changes are made that breaks serialization! 
    float[] maxVals;
    float[] minVals;

    float[] FLAT_ADD;
    float[] VAL_SCALER;

    public boolean wasConverted = false;

    int n;

    public ByteConverter(int n) {
        this.n = n;
        maxVals = new float[n];
        minVals = new float[n];

        FLAT_ADD = new float[n];
        VAL_SCALER = new float[n];
    }

    public byte[] convertToBytes(float[] values) {
        byte[] bvals = new byte[n];
        for (int i = 0; i < n; i++) {
            bvals[i] = (byte) ((values[i] - this.FLAT_ADD[i]) * VAL_SCALER[i] - 125);
        }
        return bvals;
    }

    public float[] convertBytesToFloats(byte[] bvals) {
        float[] fvals = new float[n];

        for (int i = 0; i < n; i++) {
            int temp = bvals[i] + 125; //reposition from -128 scale to 0 -256 scale;    
            fvals[i] = (temp / VAL_SCALER[i] + this.FLAT_ADD[i]);
        }
        return fvals;

    }

}
