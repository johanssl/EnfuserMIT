/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import static java.lang.Math.PI;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_A;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_B;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_C;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_D;

/**
 *
 * @author johanssl
 */
public class TabledGaussians {

    public final static int MIN_DIST_TO_ANYTHING = 5;

    public static final int IND_r = 0;
    public static final int IL_PLUME = 1;// 1/sqrt(PIr)  for plume with inversion layer
    public static final int IND_ry = 2;
    public static final int IND_rz = 3;

    protected final static int IND_sqrt_pir = 5;
    protected final static int IND_sqrt_r = 6;
    public static final int PLUME = 7; // 1/(4PIr)   for basic gaussPlume
    protected final static int IND_X = 8;
    public static final int PUFF_RAW = 9;// 1/(8(PIr)^3/2)   for raw gaussian puff
    protected final static int IND_PUFF_CORRECTION = 10;

    
    private final float[][][] r_cs_small;
    private final float[][][] r_cs_large;
    
    private final static int NX = 10000;
    private final static float X_THRESH = 10000;
        private final static float DELTA_X = X_THRESH / NX;
        private final static float X_SCALER = NX / X_THRESH;
    private final static float MAX_X = 500000;
        private final static float DELTA_XL = MAX_X / NX;
        private final static float XL_SCALER = NX / MAX_X;
    
        
    public final static TabledGaussians RC = new TabledGaussians();     
        
    private TabledGaussians() {
        
    this.r_cs_small = new float[StabiClasses.STABILITY_CLASSES][NX + 1][];
    this.r_cs_large = new float[StabiClasses.STABILITY_CLASSES][NX + 1][];
        for (int stab = 0; stab < StabiClasses.STABILITY_CLASS_NAMES.length; stab++) {
            for (int i = 0; i <= NX; i++) {
                double xmini = i * DELTA_X;
                this.r_cs_small[stab][i] = TabledGaussians.evaluateR_c_array(xmini, stab);
                float xlarge = i * DELTA_XL;
                this.r_cs_large[stab][i] = TabledGaussians.evaluateR_c_array(xlarge, stab);
            }

        }
        
    }
    
    
     public float[] getRC(double x_m, int stab) {
        if (x_m < 0) {
            x_m = MAX_X;//this must be set so for AreaSourceCell. For other uses this makes little or no difference.
        }
        if (x_m < X_THRESH) {
            return r_cs_small[stab][(int) (x_m * X_SCALER)];
        } else {
            if (x_m > MAX_X) {
                x_m = MAX_X;
            }
            return r_cs_large[stab][(int) (x_m * XL_SCALER)];
        }
    }
    
    public static float[] evaluateR_c_array(double x, int SIG_CLASS) {
        float[] arr = new float[11];

        x = Math.max(MIN_DIST_TO_ANYTHING, x);
        float sigmay;
        float sigmaz;

            switch (SIG_CLASS) {
                case STABILITY_A:
                    sigmay = (float) (0.32 * x * Math.pow(1 + 0.0004 * x, -0.5));
                    sigmaz = (float) (0.24 * x * Math.pow(1 + 0.001 * x, -0.5));
                    break;
                case STABILITY_B:
                    sigmay = (float) (0.28 * x * Math.pow(1 + 0.0004 * x, -0.5));
                    sigmaz = (float) (0.21 * x * Math.pow(1 + 0.0015 * x, -0.5));//N/A
                    break;

                case STABILITY_C://one interpolated class
                    sigmay = (float) (0.22 * x * Math.pow(1 + 0.0004 * x, -0.5));
                    sigmaz = (float) (0.18 * x * Math.pow(1 + 0.0025 * x, -0.5));//N/A
                    break;

                case STABILITY_D:
                    sigmay = (float) (0.16 * x * Math.pow(1 + 0.0004 * x, -0.5));
                    sigmaz = (float) (0.14 * x * Math.pow(1 + 0.003 * x, -0.5));
                    break;
                default:
                    //EF
                    sigmay = (float) (0.11 * x * Math.pow(1 + 0.0004 * x, -0.5));
                    sigmaz = (float) (0.08 * x * Math.pow(1 + 0.004 * x, -0.5));
                    break;
            }
            
            arr[IND_ry] = sigmay * sigmay / 2.0f;
            arr[IND_rz] = sigmaz * sigmaz / 2.0f;
            arr[IND_r] = (float)Math.sqrt(arr[IND_ry]*arr[IND_rz]);



        float r = arr[IND_r];

        arr[IL_PLUME] = 1.0f / (float) (Math.sqrt(PI * r)); // must be multiplied with Q/D
        arr[PLUME] = 1.0f / (float) ((4 * PI * r));
        arr[PUFF_RAW] = 1f / (8f * (float) Math.pow(PI * r, 1.5));//for raw puff base scaler

        
        arr[IND_sqrt_pir] = (float) Math.sqrt(PI * r);
        arr[IND_sqrt_r] = (float) Math.sqrt(r);
        arr[IND_X] = (float) x;
        arr[IND_PUFF_CORRECTION] = 4f*(float) Math.sqrt(PI * r); 
        return arr;
    }


}
