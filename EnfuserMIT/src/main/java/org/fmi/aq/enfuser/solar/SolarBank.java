/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.solar;

import org.fmi.aq.essentials.date.Dtime;
import static java.lang.Math.sin;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.SimpleTimeZone;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;


/**
 *
 * @author johanssl
 */
public class SolarBank {

    private final HashMap<Integer, float[]> solars;

    public static final int AZ = 0;
    public static final int ZEN = 1;
    public static final int POWER = 2;

    public final static float MAX_POWER_WM2 = 1000; //The Sun's rays are attenuated as they pass through the atmosphere, leaving maximum normal surface irradiance at approximately 1000 W /m2

    public static float[] getSolarSpecs(double lat, double lon, double elev_m, Dtime dt) {
        GregorianCalendar cal = new GregorianCalendar(new SimpleTimeZone(dt.timeZone * 60 * 60 * 1000, "UTC"));
        cal.set(dt.year, dt.month_011, dt.day, dt.hour, dt.min, 0); // 17 October 2003, 12:30:30 LST-07:00

        double[] az_zen = SPA.calculateSolarPosition(
                cal,
                lat, // latitude (degrees)
                lon, // longitude (degrees)
                elev_m, // elevation (m)
                DeltaT.estimate(cal), // delta T (s)
                1010, // avg. air pressure (hPa)
                11); // avg. air temperature (°C)
        //EnfuserLogger.log(Level.FINER,SolarBank.class,"SPA: " + position +" hour = "+h);
        double zenith = az_zen[1];
        zenith = 90 - zenith;
        double az = az_zen[0];
        double rad = zenith / 360.0 * 2 * Math.PI;
        double pow = MAX_POWER_WM2 * sin(rad);

        if (zenith <= 0) {
            pow = 0;
            az = -1;
            zenith = -1;
        }
        return new float[]{(float) az, (float) zenith, (float) pow};
    }

    public static void testSolarValues() {
        Dtime dt = new Dtime("2010-03-01T00:00:00");
        double lat = 60.0;
        double lon = 25.0;
        double elev_m = 0;
        double totalPow = 0;

        for (int h = 0; h < 24; h++) {
            dt.addSeconds(3600);
            float[] solar = getSolarSpecs(lat, lon, elev_m, dt);
            double zenith = solar[ZEN];
            double az = solar[AZ];
            double pow = solar[POWER];
            if (true) {
                EnfuserLogger.log(Level.FINER,SolarBank.class,"azimuth: " + (int) az + ",zenith: " + (int) zenith
                        + " hour = " + h + " => power = " + (int) pow);
                totalPow += pow;
            }

        }//for h
        EnfuserLogger.log(Level.FINER,SolarBank.class,"Total power: " + (int) totalPow);
    }

    public SolarBank(double lat, double lon, Dtime dt) {
        this.solars = new HashMap<>();
        Dtime curr_dt = dt.clone();
        curr_dt.addSeconds(-dt.hour * 3600 - dt.min * 60 - dt.sec);//start of the day

        EnfuserLogger.log(Level.FINER,SolarBank.class,"SolarBank init, using " + curr_dt.getStringDate());

        int secs_d = 24 * 3600;
        int secs_15min = 15 * 60;

        for (int s = 0; s < secs_d; s += secs_15min) {

            for (double h = 0; h <= 100; h += 5) {

                int key = this.getKey(h, curr_dt);

                float[] solar = getSolarSpecs(lat, lon, h, curr_dt);
                this.solars.put(key, solar);
            }//for h   
            curr_dt.addSeconds(secs_15min);
        }//for time

        EnfuserLogger.log(Level.FINER,SolarBank.class,"SolarBank complete. Entires = " + this.solars.size());
    }

    public int getKey(double height_m, Dtime dt) {

        int h_discrete = (int) (height_m / 5);
        h_discrete *= 5;
        if (h_discrete > 100) {
            h_discrete = 100;
        }
        if (h_discrete < 5) {
            h_discrete = 5;
        }

        return (int) (h_discrete) * 100000 + (dt.hour * 60 + dt.min) / 15;
    }

    public double[] get(Dtime dt, int h) {
        int key = this.getKey(h, dt);
        float[] dat = this.solars.get(key);

        double[] copy = new double[dat.length];
        for (int i = 0; i < dat.length; i++) {
            copy[i] = dat[i];
        }
        return copy;
    }

}
