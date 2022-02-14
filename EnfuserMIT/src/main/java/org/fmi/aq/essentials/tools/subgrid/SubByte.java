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
public class SubByte implements Serializable {

    private static final long serialVersionUID = 7526472293822776227L;//Important: change this IF changes are made that breaks serialization! 
    protected final byte[] dat;
    protected final byte oneVal;

    /**
     * Creates a subValue with a specified fill value, and a singlular grid
     * value
     *
     * @param oneVal
     * @param index
     * @param val
     */
    SubByte(int X, byte fillVal, int index, byte valAtIndex) {

        this.dat = new byte[X * X];
        this.oneVal = fillVal;

        for (int i = 0; i < dat.length; i++) {
            dat[i] = fillVal;
            if (i == index) {
                dat[i] = valAtIndex;
            }
        }
    }

    public Byte canBeCompressed() {

        if (this.dat == null) {
            return null;//already compressed
        }
        byte anyVal = dat[0];//the first value. If there is at least one value that is different than this, then the grid cannot be singular. 
        boolean allSame = true;
        for (byte b : dat) {
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

    public SubByte(byte oneVal, byte[][] d) {
        if (d != null) {
            int X = d.length;
            this.dat = new byte[X * X];

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

    public byte get(int h, int w, SubGrid sg) {
        if (dat == null) {
            return oneVal;
        } else {
            return dat[h * sg.X + w];
        }
    }
}
