/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import static java.lang.Math.abs;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author Lasse
 */
public class SimpleAreaCell extends FootprintCell {

    public final float[] weights;

    public SimpleAreaCell(float dlat_m, float h_m, float w_m) {
        super(dlat_m, h_m, w_m);
        this.weights = new float[]{dlat_m * dlat_m, dlat_m * dlat_m};
    }

    /**
     * Fetch the gaussian weight for this cell. However, this extension simply
     * returns precomputed weights that reflect the cell physical area.
     * Therefore ALL the parameters are redundant. height above ground (H) and
     * wind speed (v).
     *
     * @param temp The AreaCell does not need this (different use-case), use
     * null
     * @param D, aka. ILH or ABLh, atmospheric boundary layer height. NOT USED
     * @param z height of observation, NOT USED
     * @param H height of source, NOT USED
     * @param U windSpeed, m/s, NOT_USED
     * @param siguClass stability class, NOT_USED
     * @param ops options, NOT_USED
     * @param addm additional distance [m], NOT_USED
     * @return calculated gaussian plume concentration
     */
    @Override
    public float[] setAreaWeightedCoeffs_U_zH_D_SIG(float[] temp, float U, float z,
            float H, float D, int siguClass, FusionOptions ops, int addm) {
        return this.weights;
    }

    @Override
    public float currentLat(float lat, int wd, FusionOptions ops) {
        return this.rotatedLatAdd_dir[wd] + lat;
    }

    @Override
    public float currentLon(float lat, float lon, int wd, FusionOptions ops) {
        int ablat = (int) abs(lat);
        return this.rotatedLonAdd_dir[wd] * lonScalers[ablat] + lon;
    }

    @Override
    public float getFastOffset_hm(float windDir, FusionOptions ops) {
        return this.rotatedH_m[0];
    }

    @Override
    public float getFastOffset_wm(float windDir, FusionOptions ops) {
        return this.rotatedW_m[0];
    }

    public int getSector(float windDir) {
        return this.trueSector[0];
    }

}
