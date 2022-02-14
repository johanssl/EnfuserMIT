/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.utils;

import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class Seasonals implements Serializable {

    private static final long serialVersionUID = 7526472293813776147L;//Important: change this IF changes are made that breaks serialization! 

    public float[] tireType_default_weekly = new float[53];
    public float[] tireType_studded_weekly = new float[53];
    public float[] tireType_friction_weekly = new float[53];

    public float[] monthlyTemperatures = new float[12];
    public float[] monthlyVegetationIndex;
    public float[] monthlyRainFall_mm = new float[12];

    public Seasonals(double lat) {

        //ok, lets make some very generic assumptions and later on refine these values as post process.
        this.monthlyVegetationIndex = GENERIC_VEGEIND;

        for (int k = 0; k < tireType_default_weekly.length; k++) {
            float studRaw = STUDDED_WEEKS_FIN[k];
            if (lat < 62) {
                double diff = 62 - lat;
                studRaw -= diff * 0.06f;
                if (studRaw < 0) {
                    studRaw = 0;
                }
            }

            float frictions = 0.4f * studRaw;
            float studs = 0.6f * studRaw;
            float def = 1f - studs - frictions;
            tireType_default_weekly[k] = def;
            tireType_studded_weekly[k] = studs;
            tireType_friction_weekly[k] = frictions;

        }//for weekly
    }

    private final static float[] GENERIC_VEGEIND = {
        0.3f,
        0.3f,
        0.4f,
        0.6f,
        0.9f,
        1,
        1,
        1,
        0.9f,
        0.7f,
        0.5f,
        0.4f
    };

    private static float[] STUDDED_WEEKS_FIN = {
        0.8f, 0.8f, 0.8f, 0.8f,//1-4
        0.9f, 0.9f, 1, 1,
        1, 1, 1, 1,//9-12
        1f, 1f, 1f, 0.8f,//13-16
        0.7f, 0.6f, 0.5f, 0.35f,//17-20
        0.25f, 0.15f, 0.1f, 0.1f,//21-24
        0.1f, 0.1f, 0.1f, 0,//25-28
        0, 0, 0, 0,//29-32
        0, 0, 0, 0,//33-36
        0, 0, 0, 0,//37-40
        0.1f, 0.2f, 0.3f, 0.4f,//41-44
        0.5f, 0.6f, 0.7f, 0.7f,//45-48
        0.8f, 0.8f, 0.8f, 0.8f,//49-52
        0.8f, 0.8f, 0.8f, 0.8f,};

    public static final String[] MONTH_NAMES = {
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",};

}
