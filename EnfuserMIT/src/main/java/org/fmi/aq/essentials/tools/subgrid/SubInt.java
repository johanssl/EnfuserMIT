/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.tools.subgrid;

import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class SubInt implements Serializable {

    private static final long serialVersionUID = 7526472293822721147L;//Important: change this IF changes are made that breaks serialization! 
    protected final int[] dat;
    protected final int oneVal;

    /**
     * Creates a subValue with a specified fill value, and a singlular grid
     * value
     *
     * @param oneVal
     * @param index
     * @param val
     */
    SubInt(int X, int fillVal, int index, int valAtIndex) {

        this.dat = new int[X * X];
        this.oneVal = fillVal;

        for (int i = 0; i < dat.length; i++) {
            dat[i] = fillVal;
            if (i == index) {
                dat[i] = valAtIndex;
            }
        }
    }

    public Integer canBeCompressed() {

        if (this.dat == null) {
            return null;//already compressed
        }
        int anyVal = dat[0];//the first value. If there is at least one value that is different than this, then the grid cannot be singular. 
        boolean allSame = true;
        for (int b : dat) {
            if (b != anyVal) {
                allSame = false;
                break;
            }

        }//for values

        if (allSame) {
            return anyVal;//the subValue can be represented with a single value (this one)
        }
        return null;

    }

    public SubInt(int oneVal, int[][] d) {
        if (d != null) {

            int X = d.length;
            this.dat = new int[X * X];

            for (int h = 0; h < X; h++) {
                for (int w = 0; w < X; w++) {
                    int index = X * h + w;
                    this.dat[index] = d[h][w];
                }
            }

        } else {
            this.dat = null;
        }
        this.oneVal = oneVal;
    }

    public int get(int h, int w, SubGrid sg) {
        if (dat == null) {
            return oneVal;
        } else {
            return dat[h * sg.X + w];
        }
    }
}
