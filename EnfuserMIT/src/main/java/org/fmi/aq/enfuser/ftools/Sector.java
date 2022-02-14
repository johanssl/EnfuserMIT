/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class Sector {

    float[][] degs;
    static int max = 50;

    public Sector() {

        degs = new float[2 * max + 1][2 * max + 1];

        for (int h = -max; h <= max; h++) {
            for (int w = -max; w <= max; w++) {

                double angle_rad;
                if (h >= 0 && w >= 0) { // I

                    angle_rad = Math.atan((double) w / (double) h); //0-90

                } else if (h >= 0 && w < 0) { // IV

                    angle_rad = 2 * Math.PI - Math.atan((double) -w / (double) h); //270-360

                } else if (h < 0 && w < 0) { //III

                    angle_rad = Math.PI + Math.atan((double) -w / (double) -h); //180-270

                } else { // II
                    //h < 0, w>0
                    angle_rad = Math.PI - Math.atan((double) w / (double) -h); // 90-180

                }

                angle_rad = angle_rad / (2 * Math.PI) * 360.0; // now it's degrees

                degs[h + max][w + max] = (int) angle_rad;
            }
        }

    }

    public short getSector_fast(double h, double w, double degreeInterval) {

        double r = Math.sqrt(h * h + w * w);
        // double r = Math.pow(h*h+w*w, 0.5);
        h = 40 * (h / r);
        w = 40 * (w / r);

        return (short) (this.degs[(int) h + max][(int) w + max] / degreeInterval);

    }

    public short getSector_fastest(double h, double w, double degreeInterval, double r) {
        if (r <= 0) {
            return (byte) 0;
        }
        h = 40 * (h / r);
        w = 40 * (w / r);

        return (short) (this.degs[(int) h + max][(int) w + max] / degreeInterval);

    }

    public static byte getSector(int h, int w, double degreeInterval) {

        double angle_rad;
        if (h >= 0 && w >= 0) { // I

            angle_rad = Math.atan((double) w / (double) h); //0-90

        } else if (h >= 0 && w < 0) { // IV

            angle_rad = 2 * Math.PI - Math.atan((double) -w / (double) h); //270-360

        } else if (h < 0 && w < 0) { //III

            angle_rad = Math.PI + Math.atan((double) -w / (double) -h); //180-270

        } else { // II
            //h < 0, w>0

            angle_rad = Math.PI - Math.atan((double) w / (double) -h); // 90-180

        }

        angle_rad = angle_rad / (2 * Math.PI) * 360.0; // now it's degrees

        byte temp = (byte) ((float) angle_rad / degreeInterval);
        return temp;
    }

    public static void test() {

        EnfuserLogger.log(Level.FINER,Sector.class,"Sector test:");
        Sector s = new Sector();

        for (int h = 15; h > -16; h--) {
            for (int w = -15; w < 16; w++) {

                System.out.print(s.getSector_fast(h, w, 10) + "\t");

            }
            EnfuserLogger.log(Level.FINER,Sector.class,"");
        }

        EnfuserLogger.log(Level.FINER,Sector.class,"time test");
        long t = System.currentTimeMillis();
        for (int h = 1500; h > -1600; h--) {
            for (int w = -1500; w < 1600; w++) {
                getSector(h, w, 10);
                // System.out.print(getSector(h, w,10) + "\t");

            }
        }
        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,Sector.class,"took millis " + (t2 - t));

        EnfuserLogger.log(Level.FINER,Sector.class,"time test");
        t = System.currentTimeMillis();
        for (int h = 1500; h > -1600; h--) {
            for (int w = -1500; w < 1600; w++) {

                s.getSector_fast(h, w, 10);

            }
        }
        t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,Sector.class,"fast version took millis " + (t2 - t));

        for (int h = 1500; h > -1600; h--) {
            for (int w = -1500; w < 1600; w++) {

                double a = Math.sqrt(h * h + w * w);

            }
        }
        t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,Sector.class,"square test took millis " + (t2 - t));
    }

}
