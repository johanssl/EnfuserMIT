/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.parametrization;

import org.fmi.aq.enfuser.options.VarAssociations;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 * This class provides a conversion tool between US-EPA AQI and Enfuser default
 * concentration units. The conversion is based on an polynomial approximation
 * that is set up for the region (AQI smaller than 150 and AQI is larger than
 * 150).
 *
 * @author johanssl
 */
public class USepa {

    private final HashMap<Integer, float[][]> polyCoeffs = new HashMap<>();
    private final HashMap<Integer, float[]> cons = new HashMap<>();
    private final HashMap<Integer, float[]> cons_minMax = new HashMap<>();
    private final static int N = 5000;
    private final static float THRESH = 150;
    private final static float MIN = 0;
    private final static float MAX = 500;

    private final static int A = 0;
    private final static int B = 1;
    private final static int C = 2;
    private final static int D = 3;

    public USepa(VarAssociations VARA) {

        float[][] O3c = {//to ugm-3
            {0.000306f, -0.0608f, 4.3904f, 0},
            {-1e-5f, 0.0089f, 0.8126f, 20.482f}
        };
        polyCoeffs.put(VARA.VAR_O3, O3c);

        float[][] COc = {//to ugm-3
            {-0.004f, 0.7424f, 75f, 0},
            {-0.0006f, 0.5816f, -27.09f, 6788f}
        };
        polyCoeffs.put(VARA.VAR_CO, COc);

        float[][] NO2c = {//to ugm-3
            {0.0006f, -0.086f, 4.9278f, 0},
            {0, -0.0089f, 15.004f, -1300.9f}
        };
        polyCoeffs.put(VARA.VAR_NO2, NO2c);

        float[][] pm25c = {//to ugm-3
            {0, 0.000874f, 0.2405f, 0},
            {1.3e-5f, -0.0131f, 5.0726f, -455.22f}
        };
        polyCoeffs.put(VARA.VAR_PM25, pm25c);

        float[][] pm10c = {//to ugm-3
            {-2.13e-5f, 0.0084f, 0.91333f, 0},
            {1.11e-5f, -0.0117f, 4.5839f, -200.9f}
        };
        polyCoeffs.put(VARA.VAR_PM10, pm10c);

        float[][] SO2c = {//to ugm-3
            {0.0002f, -0.0319f, 2.8817f, 0},
            {-2e-6f, -0.003f, 8.9406f, -795 - 7f}
        };
        polyCoeffs.put(VARA.VAR_SO2, SO2c);

        initReverse();
    }

    /**
     * Init pre-computed values that facilitate the reverse operation:
     * concentration into AQI.
     */
    private void initReverse() {
        for (Integer type : polyCoeffs.keySet()) {
            float[] minMax = new float[]{
                convertAQI_toCons(MIN, type), convertAQI_toCons(MAX, type)
            };
            this.cons_minMax.put(type, minMax);

            float range = minMax[1] - minMax[0];
            //float delta_c = range/N;
            float[] aqis = new float[N];
            aqis[N - 1] = minMax[1];

            for (int i = 0; i < N; i++) {
                float aq = MIN + (MAX - MIN) * (float) i / (float) N;
                float c = convertAQI_toCons(aq, type);

                //the index?
                int ind = (int) (N * c / range);
                aqis[ind] = aq;
            }
            this.cons.put(type, aqis);
        }//for type 
    }

    /**
     * Convert a concentration value (in default Enfuser units) into US-EPA AQI
     * value.
     *
     * @param c the concentration
     * @param typeInt variable type index
     * @return AQI value.
     */
    public float convertCons_toAQI(float c, int typeInt) {
        float[] minMax = this.cons_minMax.get(typeInt);
        if (minMax == null) {
            return 0;
        }

        if (c <= minMax[0]) {
            return MIN;
        }
        if (c >= minMax[1]) {
            return MAX;
        }

        float range = minMax[1] - minMax[0];
        int ind = (int) (N * c / range);

        float[] aqis = this.cons.get(typeInt);
        float aq = aqis[ind];
        while (aq == 0) {
            ind++;
            aq = aqis[ind];
        }
        //float delta_c = range/N;
        return aq;
    }

    /**
     * Convert and US-EPA AQI value into concentrations in Enfuser's default
     * units.
     *
     * @param aq AQI value
     * @param typeInt variable type index
     * @return concentration value
     */
    public float convertAQI_toCons(float aq, int typeInt) {
        float[][] coeffs = this.polyCoeffs.get(typeInt);
        if (coeffs == null) {
            return 0;
        }

        float[] p = coeffs[0];
        if (aq < MIN) {
            aq = MIN;
        }
        if (aq > MAX) {
            aq = MAX;
        }

        if (aq > THRESH) {
            p = coeffs[1];
        }

        float f = p[A] * aq * aq * aq + p[B] * aq * aq + p[C] * aq + p[D];
        return f;

    }

    /**
     * Form a new instance and printout converted concentrations as System
     * output.Also, a reverse computation back to AQI is provided to show that
     * the conversion is working as intended.
     * @param ops
     */
    public static void test(FusionOptions ops) {
        USepa aqi = new USepa(ops.VARA);
        VarAssociations VARA = ops.VARA;
        String header = "AQI;";
        int[] types = {VARA.VAR_NO2, VARA.VAR_SO2, VARA.VAR_CO, VARA.VAR_PM25, VARA.VAR_PM10, VARA.VAR_O3};

        for (int type : types) {
            String name = VARA.VAR_NAME(type);
            header += name + ";" + name + "_reverse;";
        }
        EnfuserLogger.log(Level.FINER,USepa.class,header);

        for (float a = MIN - 5; a < MAX + 5; a += 1) {
            String line = "" + (int) a + ";";
            for (int type : types) {
                float f = aqi.convertAQI_toCons(a, type);
                line += editPrecision(f, 2) + ";";
                float reverse = aqi.convertCons_toAQI(f, type);
                line += editPrecision(reverse, 1) + ";";
            }
            EnfuserLogger.log(Level.FINER,USepa.class,line);
        }

    }
    
}
