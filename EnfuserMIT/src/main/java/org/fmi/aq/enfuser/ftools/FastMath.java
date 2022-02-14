/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.ftools.Sector;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static java.lang.Math.PI;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.gaussian.puff.RandSequencer;

/**
 *
 * @author johanssl
 */
public class FastMath {

    public final static double degreeInMeters = 2 * Math.PI * 6371000 / 360;
    
    private final static int N = 100000;
    public static final float MILLION = 1000000.0F;
    private RandSequencer rands;
    private RandSequencer pseudo_rands;
    private RandSequencer[] lesRands;
    
    private final float[] below10;
    private final static float THRESH = 10;
    private final static float MAX = 1000;

    private final static float DELTA_N = THRESH / N;
    private final static float DN = MAX / N;

    private final float[] below1k;
    private final boolean on;

    private final static float NTHRESH = -10;
    private final static float NMAX = -1000;
    private final static float SMALL_SCALER = -N / THRESH;
    private final static float LARGE_SCALER = -N / MAX;

    //positive exp
    private final float[] pos100;
    private final static float MAX_POS = 100;
    private final static float POS_SCALER = N / MAX_POS;
    private final static float POS_DELTA_N = MAX_POS / N;

    //trigons 
    public float[][] sin_cos_360 = new float[361][2];
    public Sector sect = new Sector();
    private final HashMap<Integer, float[]> sinCos_beta = new HashMap<>();

    //ASIN ACOS 
    private final int ASIN_N = 2000;
    private final float[] asin_neg = new float[ASIN_N + 1];
    private final float[] asin = new float[ASIN_N + 1];

    private final float[] acos_neg = new float[ASIN_N + 1];
    private final float[] acos = new float[ASIN_N + 1];

    //sqrt
    private final float maxSQ_low = 10;
    private final float dSQ_low = 0.00001f;
    private final float[] SQ_low;

    private final float maxSQ_med = 2000;
    private final float dSQ_med = 0.1f;
    private final float[] SQ_med;

    private final float maxSQ_high = 200000;
    private final float dSQ_high = 10f;
    private final float[] SQ_high;

    private ArrayList<RotationCell> solarCheckArray;
    private final float[] latScaler;


    //instance to use
    public final static FastMath FM = new FastMath(true);

    public FastMath(boolean on) {
        
        this.pseudo_rands= new RandSequencer(51,true);
        this.rands = new RandSequencer(51,false);
        this.lesRands = new RandSequencer[50];
        for (int i =0;i<lesRands.length;i++) {
            this.lesRands[i] = new RandSequencer(101,false);
        }
        
        
        
        EnfuserLogger.log(Level.FINER,FastMath.class,"FastMath init...");
        this.on = on;
        this.below10 = new float[N + 1];
        this.below1k = new float[N + 1];
        this.pos100 = new float[N + 1];

        EnfuserLogger.log(Level.FINER,FastMath.class,"\t fastExp function...");
        for (int i = 0; i <= N; i++) {
            float mini = -i * DELTA_N;
            float large = -i * DN;
            float pos = i * POS_DELTA_N;

            below10[i] = (float) Math.exp(mini);
            below1k[i] = (float) Math.exp(large);
            this.pos100[i] = (float) Math.exp(pos);

        }

        EnfuserLogger.log(Level.FINER,FastMath.class,"Sin, cos...");
        for (int wd = 0; wd <= 360; wd++) {
            double beta = (2 * Math.PI / 360.0) * wd;
            double cos = Math.cos(beta);
            double sin = Math.sin(beta);
            this.sin_cos_360[wd][0] = (float) sin;
            this.sin_cos_360[wd][1] = (float) cos;
        }
        //fast sinCos (beta) comps
        for (double beta = -3 * PI; beta <= 3 * PI; beta += 0.01) {
            int key = (int) (beta * 100);
            float[] sinCos = {(float) Math.sin(beta), (float) Math.cos(beta)};
            this.sinCos_beta.put(key, sinCos);
        }

        EnfuserLogger.log(Level.FINER,FastMath.class,"Acos, asin...");
        this.initArTrigons();

        EnfuserLogger.log(Level.FINER,FastMath.class,"Fast SQRT...");
        //fast sqrt
        int SQ = 0;
        int n = (int) (maxSQ_low / dSQ_low);
        SQ_low = new float[n];
        for (int i = 0; i < n; i++) {
            SQ_low[i] = (float) Math.sqrt(i * dSQ_low);
            SQ++;
        }
        n = (int) (maxSQ_med / dSQ_med);
        SQ_med = new float[n];
        for (int i = 0; i < n; i++) {
            SQ_med[i] = (float) Math.sqrt(i * dSQ_med);
            SQ++;
        }

        n = (int) (maxSQ_high / dSQ_high);
        SQ_high = new float[n];
        for (int i = 0; i < n; i++) {
            SQ_high[i] = (float) Math.sqrt(i * dSQ_high);
            SQ++;
        }
        EnfuserLogger.log(Level.FINER,FastMath.class,"Computed " + SQ + " roots for fast access tables");

        //fast cos
        this.latScaler = new float[900];
        for (int i = 0; i < this.latScaler.length; i++) {
            double lat = i / 10.0;
            double rad = (2 * PI * lat / 360);
            this.latScaler[i] = (float) Math.cos(rad);
        }


        EnfuserLogger.log(Level.FINER,FastMath.class,"Done.");
    }

    
    public RandSequencer getRands(boolean determ) {
        if (determ) return this.pseudo_rands;
        return this.rands;
    }

    public float[] rotateHW(float h_m, float w_m, double windDir, float[] temp) {

        float newH;
        float newW;

        if (temp == null) {
            temp = new float[4];
        }

        float sin = this.sin_cos_360[(int) windDir][0];
        float cos = this.sin_cos_360[(int) windDir][1];

        newH = (h_m * cos - w_m * sin);
        newW = (w_m * cos + h_m * sin);

        temp[0] = (float) newH;
        temp[1] = (float) newW;

        return temp;
    }

    /**
     * get precomputed Math.asin() values
     *
     * @param sin must be between [-1,1] i.e., unit circle
     * @return asin-function value.
     */
    public float asin(float sin) {

        if (sin > 0) {
            return asin[(int) (sin * ASIN_N)];
        } else {
            return asin_neg[(int) (-sin * ASIN_N)];
        }

    }

    /**
     * get precomputed Math.acos() values
     *
     * @param cos must be between [-1,1] i.e., unit circle
     * @return acos function value
     */
    public float acos(float cos) {

        if (cos > 0) {
            return acos[(int) (cos * ASIN_N)];
        } else {
            return acos_neg[(int) (-cos * ASIN_N)];
        }

    }

    private void initArTrigons() {

        for (int i = 0; i <= ASIN_N; i++) {

            float f = (float) i / ASIN_N;//runs from 0 -> 1

            this.asin[i] = (float) Math.asin(f);
            this.asin_neg[i] = (float) Math.asin(-f);

            this.acos[i] = (float) Math.acos(f);
            this.acos_neg[i] = (float) Math.acos(-f);
        }

    }

    public float fexp(double val) {
        if (!on) {
            return (float) Math.exp(val);
        }

        if (val < 0) {

            if (val > NTHRESH) {//extra precision at 0-10 exponents
                return below10[(int) (val * SMALL_SCALER)];
            } else if (val > NMAX) {
                return below1k[(int) (val * LARGE_SCALER)];
            } else {//e^-1000 is practically zero.
                return 0;
            }

        } else {//postive

            if (val > MAX_POS) {
                val = MAX_POS;
            }
            return pos100[(int) (val * POS_SCALER)];
        }

    }

    public static void speedTest() {

        EnfuserLogger.log(Level.FINER,FastMath.class,"FastMath speed test:");
        long t = System.currentTimeMillis();
        float sum = 0;
        for (float i = 0; i < 1000; i += 0.0001f) {

            sum += Math.exp(-i);
        }

        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,FastMath.class,"many exp functions took " + (t2 - t) + "ms, sum = " + sum);

        t = System.currentTimeMillis();
        sum = 0;
        for (float i = 0; i < 1000; i += 0.0001f) {
            sum += FM.fexp(-i);
        }

        t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,FastMath.class,"equally many fast exp functions took " + (t2 - t) + "ms, sum = " + sum);

        t = System.currentTimeMillis();
        sum = 0;
        for (float i = 0; i < 1000; i += 0.0001f) {

            sum += Math.exp(-i);
        }

        t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,FastMath.class,"many exp functions took " + (t2 - t) + "ms, sum = " + sum);
    }

    public float[] getFastSinCos(double beta) {
        int key = (int) (beta * 100);
        return this.sinCos_beta.get(key);
    }

    /**
     * Gives an array of cells (North), 5m interval for the next 500m. The array
     * can be used e.g., for sun ray block evaluation
     *
     * @return a list of FpmCells that can be used for the assessment of solar
     * shading for a given location once the cells are rotated towards the sun's
     * direction.
     */
    public ArrayList<RotationCell> getSolarCheckCells() {
        if (this.solarCheckArray == null) {
            this.solarCheckArray = new ArrayList<>();
            for (int i = 0; i < 100; i++) {//500m rad

                if (i > 40 && i % 4 != 0) {
                    continue;
                } else if (i > 25 && i % 3 != 0) {//sparse 15m
                    continue;
                } else if (i > 10 && i % 2 != 0) {//sparse 10m
                    continue;
                }

                this.solarCheckArray.add(new RotationCell(5f, i * 5f, 0));// a line of cells to the north, 5m interval, resolution is 5m though it does not matter in this case. 
            }
        }

        return this.solarCheckArray;
    }

    public ArrayList<RotationCell> getCustomRay(float res_m, float maxRad_m, int sparser) {

        ArrayList<RotationCell> arr = new ArrayList<>();
        float R = maxRad_m / res_m;
        for (int i = 0; i < R; i += sparser) {//500m rad

            arr.add(new RotationCell(res_m, i * res_m, 0));// a line of cells to the north, 5m interval, resolution is 5m though it does not matter in this case. 
        }

        return arr;
    }

    private ArrayList<RotationCell> windOrthoCheckArray = null;

    public ArrayList<RotationCell> getOrthoWindCells(Boundaries b) {

        if (windOrthoCheckArray == null) {
            EnfuserLogger.log(Level.FINER,FastMath.class,"Creating orthoWind Fpm list..");
            windOrthoCheckArray = new ArrayList<>();
            double full_Hm = b.latRange() * degreeInMeters * 0.6;
            double full_Wm = b.lonRange() * degreeInMeters * 0.6 * getFastLonScaler(b.getMidLat());
            double dist_m = Math.max(full_Hm, full_Wm);

            EnfuserLogger.log(Level.FINER,FastMath.class,"distance = " + (int) dist_m + "m");
            //two rays that are orthogonal to wind's direction and together they cover 30% of the area length.
            //however, 10km rays should be enough for the effect.
            this.windOrthoCheckArray.add(new RotationCell(5f, 0f, (float) dist_m));//h_m is zero, use w_m
            this.windOrthoCheckArray.add(new RotationCell(5f, 0f, (float) -dist_m));//and then -w_m.
            this.windOrthoCheckArray.add(new RotationCell(5f, 0f, (float) dist_m * 0.5f));//h_m is zero, use w_m
            this.windOrthoCheckArray.add(new RotationCell(5f, 0f, (float) -dist_m * 0.5f));//and then -w_m.
        }

        EnfuserLogger.log(Level.FINER,FastMath.class,"Done.");
        return windOrthoCheckArray;
    }

    public float fastSqrt_old(float f) {

        if (f < maxSQ_low) {
            int index = (int) (f / dSQ_low);
            return this.SQ_low[index];

        } else if (f < maxSQ_med) {

            int index = (int) (f / dSQ_med);
            return this.SQ_med[index];

        } else if (f < maxSQ_high) {

            int index = (int) (f / dSQ_high);
            return this.SQ_high[index];

        } else {
            return (float) Math.sqrt(f);
        }

    }

    public float getFastLonScaler(double lat) {
        return this.latScaler[(int) Math.abs(lat * 10)];
    }
    
    
     /**
     * Compute the complementary error function.
     *
     * @param x variable value, as is defined for erfc.
     * @return error function value of the given x
     */
    public static double erfc(double x) {
        if (x >= 0.) {
            return erfccheb(x);
        } else {
            return 2.0 - erfccheb(-x);
        }
    }

    private static final double[] COEFF = {
        -1.3026537197817094,
        6.4196979235649026e-1,
        1.9476473204185836e-2,
        -9.561514786808631e-3,
        -9.46595344482036e-4,
        3.66839497852761e-4,
        4.2523324806907e-5,
        -2.0278578112534e-5,
        -1.624290004647e-6,
        1.303655835580e-6,
        1.5626441722e-8,
        -8.5238095915e-8,
        6.529054439e-9,
        5.059343495e-9,
        -9.91364156e-10,
        -2.27365122e-10,
        9.6467911e-11,
        2.394038e-12,
        -6.886027e-12,
        8.94487e-13,
        3.13092e-13,
        -1.12708e-13,
        3.81e-16,
        7.106e-15,
        -1.523e-15,
        -9.4e-17,
        1.21e-16,
        -2.8e-17
    };

    private static double erfccheb(double x) {
        double t, ty, tmp, d = 0., dd = 0.;

        t = 2.0 / (2.0 + x);
        ty = 4. * t - 2.;

        for (int j = COEFF.length - 1; j > 0; j--) {
            tmp = d;
            d = ty * d - dd + COEFF[j];
            dd = tmp;
        }

        return t * Math.exp(-x * x + 0.5 * (COEFF[0] + ty * d) - dd);
    }

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }
    
    
    public static double degIntoRad(double d) {
        return (d / 360.0 * 2 * PI);
    }

    public static double radIntoDeg(double r) {
        return (r / 2 / PI * 360);
    }



    public float randomizeWind(float wdir, int scale, boolean determ) {
        
       try { 
       float rand;
       if (determ) {
           rand = this.pseudo_rands.nextRand();
       } else {
           rand = this.rands.nextRand();
       }
       
       float dir = wdir +rand*scale;
       if (dir > 360) dir-=360;
       if (dir<0) dir+=360;
       return dir;
       
       } catch (ArrayIndexOutOfBoundsException e) {
          return wdir; //has been encountered in some Java builds. A possible concurrency
          //problem, but only in some newer java versions.
       }
    }

    
    
    
    public RandSequencer getLesRands() {
       int ind = (int)(Math.random()*this.lesRands.length-1);
       return this.lesRands[ind];
    }


}
