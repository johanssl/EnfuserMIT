/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import static org.fmi.aq.enfuser.ftools.FastMath.radIntoDeg;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class WindConverter {

    public static float[] dirSpeedFrom_toNE(float wDir, float v) {

        double beta;
        if (wDir >= 0 && wDir < 90) {// I
            float wd = wDir - 0; //angle between y-axis and the dir
            wd = 90 - wd;//e.g. 60 => angle diff = 60 => wd = 30;
            beta = (2 * Math.PI / 360.0) * (90 - wDir);

        } else if (wDir >= 90 && wDir < 180) { //IV
            float wd = wDir - 90; //angle between x-axis and the dir
            wd = 360 - wd;// e.g. wd = 120 => 330 in kartesian.
            beta = (2 * Math.PI / 360.0) * wd;

        } else if (wDir >= 180 && wDir < 270) {// III
            float wd = wDir - 180; //angle between y-axis and the dir
            wd = 270 - wd;//e.g. wd = 200 => y-angle = 20 => kartesian = 250
            beta = (2 * Math.PI / 360.0) * wd;

        } else if (wDir >= 270 && wDir <= 360) {//II

            float wd = wDir - 270; //angle between x-axis and the dir
            wd = 180 - wd;//e.g. wd = 200 => y-angle = 20 => kartesian = 250
            beta = (2 * Math.PI / 360.0) * wd;

        } else {
            EnfuserLogger.log(Level.WARNING,WindConverter.class,
            "WindDir sinCos split fail! wd = " + wDir);
            beta = 0;
        }

        float cos_beta = v * (float) Math.cos(beta);
        if (Math.abs(cos_beta) < 0.001) {
            cos_beta = 0;//for dirs multiple of 90 there are some rounding errors that are cleaned with this.
        }
        float sin_beta = v * (float) Math.sin(beta);
        if (Math.abs(sin_beta) < 0.001) {
            sin_beta = 0;
        }
        return new float[]{sin_beta * -1, cos_beta * -1}; //direction is 'from' but N,E components describe 'to' and therefore multiply by -1.

    }

    public static float[] NE_toDirSpeed_from(float sin, float cos, boolean brute) {
        if (brute) {
            return NE_toDirSpeedFrom_slow(sin, cos);
        }
        float v = (float) Math.sqrt(sin * sin + cos * cos);
        if (v < 0.1) {
            v = 0.1f;//otherwise NaN, not good. if the wind speed is zero the direction will not matter in any case!
        }
        sin /= v;
        cos /= v;
        float wd;
        if (sin >= 0 && cos >= 0) {//I
            double arad = FM.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 90 - angle;

        } else if (sin <= 0 && cos >= 0) {// IV
            sin *= -1;//now it's positive.
            double arad = FM.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 90 + angle;

        } else if (sin <= 0 && cos <= 0) {//III

            sin *= -1;//now it's positive.
            double arad = FM.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 270 - angle;

        } else if (sin >= 0 && cos <= 0) {

            double arad = FM.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 270 + angle;

        } else {
            EnfuserLogger.log(Level.WARNING,WindConverter.class,
                    "ERROR: WindDir re-transform fail! sin = " + sin + ", cos = " + cos);
            wd = 0;
        }

        //direction is 'from' but N,E components describe 'to' and therefore add degrees
        wd += 180;
        if (wd >= 360) {
            wd -= 360;
        }

        return new float[]{wd, v};

    }

    public static float[] NE_toDirSpeedFrom_slow(float sin, float cos) {
        float v = (float) Math.sqrt(sin * sin + cos * cos);
        if (v < 0.1) {
            v = 0.1f;//otherwise NaN, not good. if the wind speed is zero the direction will not matter in any case!
        }
        sin /= v;
        cos /= v;
        float wd;
        if (sin >= 0 && cos >= 0) {//I
            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 90 - angle;

        } else if (sin <= 0 && cos >= 0) {// IV
            sin *= -1;//now it's positive.
            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 90 + angle;

        } else if (sin <= 0 && cos <= 0) {//III

            sin *= -1;//now it's positive.
            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 270 - angle;

        } else if (sin >= 0 && cos <= 0) {

            double arad = Math.asin(sin);
            float angle = (float) radIntoDeg(arad);
            wd = 270 + angle;

        } else {
            EnfuserLogger.log(Level.FINER,WindConverter.class,
                    "ERROR: WindDir re-transform fail! sin = " + sin + ", cos = " + cos);
            wd = 0;
        }

        wd += 180;
        if (wd >= 360) {
            wd -= 360;
        }

        return new float[]{wd, v};

    }
}
