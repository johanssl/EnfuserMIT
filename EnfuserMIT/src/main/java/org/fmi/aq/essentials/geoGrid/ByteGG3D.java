/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.geoGrid;

import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class ByteGG3D {

    public byte[][][] values;
    public final float VAL_SCALER;
    public final float VAL_OFFSET;
    public final int H;
    public final int W;
    public final int T;

    /**
     * A compact representation of float data, using bytes. Floats are converted
     * into bytes using a 250 stepped-scaling (and an offset) The main use case
     * is for netCDF: the offset and scaler are compatible with netCDF. NOTE:
     * ByteGeoGrid is NOT netCDF compatible!
     *
     * @param thw values for grid.
     */
    public ByteGG3D(float[][][] thw) {
        EnfuserLogger.log(Level.FINER,this.getClass(),"Creating ByteGG3D");
        T = thw.length;
        EnfuserLogger.log(Level.FINER,this.getClass(),"T-dim = " + T);
        H = thw[0].length;
        EnfuserLogger.log(Level.FINER,this.getClass(),"H-dim = " + H);
        W = thw[0][0].length;
        EnfuserLogger.log(Level.FINER,this.getClass(),"W-dim = " + W);

        this.values = new byte[T][H][W];

        float maxVal = Float.MIN_VALUE;
        float minVal = Float.MAX_VALUE;
        for (float[][] fff : thw) {
            for (float[] ff : fff) {
                for (float f : ff) {
                    if (f > maxVal) {
                        maxVal = f;
                    }
                    if (f < minVal) {
                        minVal = f;
                    }
                }
            }
        }

        float range = maxVal - minVal;
        //scaler MUST NOT BE 0 (divByZero) and this happens with constant field
        if (range == 0) {//max,min are the same
            this.VAL_SCALER = minVal;
            this.VAL_OFFSET = 0;
            EnfuserLogger.log(Level.FINER,this.getClass(),"Constant value field: scaler is " + VAL_SCALER + ", offset is zero.");

        } else {

            this.VAL_OFFSET = minVal + 0.5f * range;
            this.VAL_SCALER = range / 250;
                EnfuserLogger.log(Level.FINER,this.getClass(),"Byte scaling is used! Scaler is " + VAL_SCALER);
                EnfuserLogger.log(Level.FINER,this.getClass(),"Range/flatAdd => " + range + "/" + this.VAL_OFFSET);

        }

        //setup values
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                for (int t = 0; t < T; t++) {
                    values[t][h][w] = convertToByte(thw[t][h][w]);
                    if (this.values[t][h][w] < -125) {
                        EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue " + thw[t][h][w] + "/" + this.values[t][h][w]);

                    }
                    if (this.values[t][h][w] > 125) {
                        EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue " + thw[t][h][w] + "/" + this.values[t][h][w]);
                    }
                }//for t
            }//for w
        }//for h

    }

    public float getConvertedFloatValue(int t, int h, int w) {
        byte b = this.values[t][h][w];
        return convertToFloat(b);
    }

    public byte getRawByte(int t, int h, int w) {
        return this.values[t][h][w];
    }

    private float convertToFloat(byte b) {
        //conversion
        //f = offset + b*scaler => b = (f - offset)/scaler
        float f = ((float) b * VAL_SCALER + VAL_OFFSET);
        return f;

    }

    private byte convertToByte(float f) {
        //conversion
        //f = offset + b*scaler => b = (f - offset)/scaler
        f -= this.VAL_OFFSET;
        f /= this.VAL_SCALER;
        return (byte) f;

    }

}
