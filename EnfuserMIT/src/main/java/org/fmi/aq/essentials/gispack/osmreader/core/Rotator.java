/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class Rotator implements Serializable {

    private static final long serialVersionUID = 7526472293822776267L;//Important: change this IF changes are made that breaks serialization!     
    private float[][] sin_cos_360;
    private Sector sect;
    public float[] cos_lat;

    public Rotator() {
        initFastTrigons();
    }

    public double getDirection(double dlat_m, double dlon_m, double dist_m, int INTERV) {
        if (sect == null) {
            initFastTrigons();
        }
        return INTERV * sect.getSector_fastest(dlat_m, dlon_m, INTERV, dist_m);
    }

    public void initFastTrigons() {
        sin_cos_360 = new float[361][2];
        sect = new Sector();
        //precalc rotation
        for (int wd = 0; wd <= 360; wd++) {
            double beta = (2 * Math.PI / 360.0) * wd;
            double cos = Math.cos(beta);
            double sin = Math.sin(beta);
            this.sin_cos_360[wd][0] = (float) sin;
            this.sin_cos_360[wd][1] = (float) cos;
        }

        this.cos_lat = new float[90];
        for (float lat = 0; lat < 90; lat++) {
            int index = (int) lat;
            this.cos_lat[index] = (float) Math.cos(lat / 360 * (2 * Math.PI));
        }

    }

    public float[] rotateDir(float h_m, float w_m, double windDir, float[] temp) {

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

}
