/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;


/**
 **
 * @author johanssl
 */
public class StabiClasses {

    public final static int STABILITY_A = 0;
    public final static int STABILITY_B = 1;
    public final static int STABILITY_C = 2;
    public final static int STABILITY_D = 3;
    public final static int STABILITY_EF = 4;

    public final static int STABILITY_CLASSES = 5;
    public final static String[] STABILITY_CLASS_NAMES = {"A", "B", "C", "D", "EF"};

    /**
     * Estimate the Pasquill-GIfford stability class as a function of 1/L_m.
     * Reference: Hall, D. & Spanton, A. & Dunkerley, Fay & Bennett, M. & 
     * Griffiths, R.. (2021). A Review of Dispersion Model Intercomparison 
     * Studies Using ISC, R91, AERMOD and ADMS. 
     * @param invMO the inverse Monin-Obukhov length.
     * @return stability classification.
     */
    public static byte basedOnInvMO(float invMO) {
        byte stabi;
        if (invMO < -0.056f) {
            stabi = STABILITY_A;

        } else if (invMO < -0.016f) {
            stabi = STABILITY_B;

        } else if (invMO < -0.004) {
            stabi = STABILITY_C;

        } else if (invMO < 0.004) {
            stabi = STABILITY_D;

        } else {
            stabi = STABILITY_D;//'EF' DISABLED FOR NOW
        }
        return stabi;
    }

    public static byte basedOnWindRad(double effRad, float ws) {
        byte stabi;
        //range 0 to 1000 (summertime clear sky at 90 degree angle);
        if (effRad <= 0) {//nighttime

            if (ws <= 3) {
                stabi = STABILITY_D;
            } else {
                stabi = STABILITY_D;//'EF' DISABLED FOR NOW
            }

        } else {//daytime

            if (effRad < 100) {//low
                if (ws <= 3) {
                    stabi = STABILITY_C;
                } else {
                    stabi = STABILITY_D;
                }
            } else if (effRad < 250) {//medium
                if (ws <= 3) {
                    stabi = STABILITY_B;
                } else {
                    stabi = STABILITY_C;
                }
            } else {//high
                if (ws <= 3) {
                    stabi = STABILITY_A;
                } else {
                    stabi = STABILITY_B;
                }
            }

        }//else if daytime  
        return stabi;
    }

}
