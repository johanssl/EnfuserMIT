/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class RadiusList implements Serializable {

    private static final long serialVersionUID = -4762704446788419180L;//Important: change this IF changes are made that breaks serialization! 
    public ArrayList<RadListElement> elems = new ArrayList<>();
    public final double coslat;
    public final float res_m;
    final int rad;
    public final float maxDist_m;
    final int sparser;

    public RadiusList(GeoGrid g, int n) {
        this((float) g.dlatInMeters, (float) g.dlatInMeters * n,
                g.gridBounds.getMidLat(), 1);
    }

    public RadiusList(float res_m, float maxDist_m, double midlat) {
        this(res_m, maxDist_m, midlat, 1);
    }

    public RadiusList(float res_m, float maxDist_m, double midlat, int sparser) {

        coslat = Math.cos(midlat / 180 * Math.PI);
        this.res_m = res_m;
        this.maxDist_m = maxDist_m;
        this.sparser = sparser;

        this.rad = (int) (maxDist_m / res_m) + 1;

        for (int i = -rad; i <= rad; i++) {
            for (int j = -rad; j <= rad; j++) {

                if (sparser > 1) {
                    if (i % sparser != 0 || j % sparser != 0) {
                        continue;
                    }
                }

                double dist = res_m * Math.sqrt(i * i + j * j);
                if (dist > maxDist_m) {
                    continue;
                }
                double latAdd = i * res_m / degreeInMeters;
                double lonAdd = j * res_m / degreeInMeters / coslat;
                RadListElement re = new RadListElement(i, j, dist, latAdd, lonAdd);
                elems.add(re);
            }
        }

        Collections.sort(elems, new RadListComparator());

    }

    public static RadiusList withFixedRads(int N, float res_m, double midlat) {
        return new RadiusList(N, res_m, midlat, true);
    }
        public static RadiusList withFixedRads_SQUARE(int N, float res_m, double midlat) {
        return new RadiusList(N, res_m, midlat, false);
    }

    private RadiusList(int MAX_RADS, float res_m, double midlat, boolean distCheck) {

        coslat = Math.cos(midlat / 180 * Math.PI);
        this.res_m = res_m;
        this.maxDist_m = res_m * MAX_RADS;
        this.sparser = 1;

        this.rad = MAX_RADS;

        for (int i = -rad; i <= rad; i++) {
            for (int j = -rad; j <= rad; j++) {

                if (sparser > 1) {
                    if (i % sparser != 0 || j % sparser != 0) {
                        continue;
                    }
                }

                double dist = res_m * Math.sqrt(i * i + j * j);
                if (distCheck && dist > maxDist_m) {
                    continue;
                }
                double latAdd = i * res_m / degreeInMeters;
                double lonAdd = j * res_m / degreeInMeters / coslat;
                RadListElement re = new RadListElement(i, j, dist, latAdd, lonAdd);
                elems.add(re);
            }
        }

        Collections.sort(elems, new RadListComparator());

    }

    public static void test() {
        float maxDist = 50;
        float resm = 5;
        double midlat = 60;
        RadiusList rl = new RadiusList(resm, maxDist, midlat);

        EnfuserLogger.log(Level.FINER,RadiusList.class,
                "res=" + resm + ", maxD=" + maxDist + ", mlat = " + midlat + ", size= " + rl.elems.size());
        for (RadListElement ele : rl.elems) {
            EnfuserLogger.log(Level.FINER,RadiusList.class,
                    "\t" + ele.hAdd + ", " + ele.wAdd + ", dist_m = " + ele.dist_m
                    + ", cc_offsets = " + ele.latAdd + ", " + ele.lonAdd);
        }

        EnfuserLogger.log(Level.FINER,RadiusList.class,"==============");

        maxDist = 50;
        resm = 10;
        midlat = 0;
        rl = new RadiusList(resm, maxDist, midlat);

        EnfuserLogger.log(Level.FINER,RadiusList.class,
                "res=" + resm + ", maxD=" + maxDist + ", mlat = " + midlat + ", size = " + rl.elems.size());
        for (RadListElement ele : rl.elems) {
            EnfuserLogger.log(Level.FINER,RadiusList.class,
                    "\t" + ele.hAdd + ", " + ele.wAdd + ", dist_m = " + ele.dist_m
                    + ", cc_offsets = " + ele.latAdd + ", " + ele.lonAdd);
        }

        EnfuserLogger.log(Level.FINER,RadiusList.class,"==============");
    }
}
