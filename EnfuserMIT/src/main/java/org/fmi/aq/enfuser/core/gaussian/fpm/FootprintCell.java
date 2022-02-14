/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.getInversion_b;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.getPlume_b;
import java.io.Serializable;
import org.fmi.aq.enfuser.options.FusionOptions;
import static java.lang.Math.abs;
import static org.fmi.aq.enfuser.core.gaussian.fpm.TabledGaussians.RC;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;

/**
 *
 * @author johanssl
 */
public class FootprintCell implements Serializable {

    private static final long serialVersionUID = 7526472393822776147L;//Important: change this IF changes are made that breaks serialization!   
    public final static double pi = Math.PI;

    public final float cellRes_m;
    public final float cellArea_m2;
    private float h_m;
    private float w_m;
    public float dist_m;

    public short[] trueSector;

    private float[][] r_cs;//on per each stabilityClass
    private float[] aw_y_blocks;

    short[] rotatedH_m;
    short[] rotatedW_m;
    float[] rotatedLatAdd_dir;
    float[] rotatedLonAdd_dir;

    float hm_rand = 0;
    float wm_rand = 0;

    float lat_rand = 0;
    float lon_rand_unscaled = 0;

    protected float[] lonScalers;

    public FootprintCell(float dlat_m, float h_m, float w_m) {

        this.h_m = h_m;
        this.w_m = w_m;
        this.cellRes_m = dlat_m;
        this.cellArea_m2 = dlat_m * dlat_m;
        this.dist_m = (float) Math.sqrt(h_m * h_m + w_m * w_m);

        float rand1 = (float) Math.random();
        float rand2 = (float) Math.random();

        if (this.cellRes_m > 6) {
            this.hm_rand = rand1 * (this.cellRes_m - 5);
            this.wm_rand = rand2 * (this.cellRes_m - 5);
            this.lat_rand = (float) (hm_rand / degreeInMeters);
            this.lon_rand_unscaled = (float) (wm_rand / degreeInMeters);
        }

        this.initYBlocks();
    }

    public float currentLat_fast(float lat, int wd) {
        return this.rotatedLatAdd_dir[wd] + lat;
    }

    public float currentLon_fast(float lon, int wd) {

        return this.rotatedLonAdd_dir[wd] + lon;
    }

    public float currentLat(float lat, int wd, FusionOptions ops) {
        return this.rotatedLatAdd_dir[wd] + lat + ops.fpmRand * this.lat_rand;
    }

    public float currentLon(float lat, float lon, int wd, FusionOptions ops) {
        int ablat = (int) abs(lat);
        return (this.rotatedLonAdd_dir[wd] + ops.fpmRand * this.lon_rand_unscaled) * lonScalers[ablat] + lon;
    }

    public float getFastOffset_hm(float windDir, FusionOptions ops) {
        if (ops == null) {
            return this.rotatedH_m[(int) windDir];
        }
        return this.rotatedH_m[(int) windDir] + ops.fpmRand * this.hm_rand;
    }

    public float getFastOffset_wm(float windDir, FusionOptions ops) {
        if (ops == null) {
            return this.rotatedW_m[(int) windDir];
        }
        return this.rotatedW_m[(int) windDir] + ops.fpmRand * this.wm_rand;
    }

    public int getTrueSector(float windDir, int sectorIncrement_degs) {
        return this.trueSector[(int) windDir] / sectorIncrement_degs;
    }

    public void setRotations() {
        this.rotatedH_m = new short[361];
        this.rotatedW_m = new short[361];
        this.rotatedLatAdd_dir = new float[361];
        this.lonScalers = new float[91];
        this.rotatedLonAdd_dir = new float[361];
        trueSector = new short[361];

        float[] temp = new float[4];
        float hadd = 0f;
        float wadd = 0f;

        for (int lat = 0; lat < 90; lat++) {
            float coslat = FM.getFastLonScaler(lat);
            this.lonScalers[lat] = 1f / coslat;
        }

        for (int wd = 0; wd < 361; wd++) {

            if (temp == null) {
                temp = new float[4];
            }

            //rotate the metric values
            temp = FM.rotateHW(this.h_m + wadd, this.w_m + hadd, wd, temp);
            if (wd == 0 && this instanceof SimpleAreaCell) {
                temp[0] = this.h_m;
                temp[1] = this.w_m;
            }
            // then convert to degrees
            rotatedH_m[wd] = (short) temp[0];
            rotatedW_m[wd] = (short) temp[1];

            //init true sector for this cell with this rotation
            float trueDist_m = (float) Math.sqrt(rotatedH_m[wd] * rotatedH_m[wd] + rotatedW_m[wd] * rotatedW_m[wd]);//randomization could have made conflicts with this.dist_m
            this.trueSector[wd] = FM.sect.getSector_fastest(rotatedH_m[wd], rotatedW_m[wd], 1, trueDist_m);

            this.rotatedLatAdd_dir[wd] = (float) (rotatedH_m[wd] / degreeInMeters);
            this.rotatedLonAdd_dir[wd] = (float) (rotatedW_m[wd] / degreeInMeters);//scale with 1/coslat afterwards.

        }
    }

    public float x_m() {
        return this.h_m;
    }

    public float y_m() {
        return this.w_m;
    }

    public boolean isMidline() {
        return (Math.abs(w_m) < this.cellRes_m);
    }

    public final static int SECTOR_ANGLE = 10;

    public float[] getOffsets_degrees_m(float[] temp, Float windDir, float cos_midlat) {

        if (temp == null) {
            temp = new float[4];
        }

        //rotate the metric values
        temp = FM.rotateHW(this.h_m, this.w_m, windDir, temp);
        // then convert to degrees
        temp[2] = temp[0];//switch and save, these are the metric values
        temp[3] = temp[1];

        temp[0] = (float) (temp[0] / degreeInMeters);
        temp[1] = (float) (temp[1] / degreeInMeters / cos_midlat);

        // }
        return temp;
    }

    /**
     * Get hight and width of this cell in terms of degrees (not radians).
     *
     * @param cos_midlat cosine value for the mid latitude point
     * @return cell resolution on terms of degrees: {dlat,dlon}
     */
    public float[] getCellResolution_degrees(float cos_midlat) {
        return new float[]{
            this.cellRes_m / (float) degreeInMeters,
            this.cellRes_m / (float) degreeInMeters / cos_midlat};
    }

    public final static int IND_GAS = 0;
    public final static int IND_CPM = 1;

    public final static int MAX_RATIO = 5;
    public final static int MIN_RATIO = 3;

    public float[] setAreaWeightedCoeffs_U_zH_D_SIG(float[] temp, float U, float z,
            float H, float D, int siguClass, FusionOptions ops, int addMixing_m) {
        float yblock;
        float[] r_c;
        if (addMixing_m == 0) {//default case

            yblock = this.aw_y_blocks[siguClass];
            r_c = this.r_cs[siguClass];

        } else {//e.g., vehicle induced turbulence nearby => additional  ry,rz used worth of a couple of meters
            float effectiveHm = (float)Math.max(this.h_m,addMixing_m);
            r_c = RC.getRC(effectiveHm, siguClass);
            yblock = PlumeMath.getYblock(r_c, this.w_m) * this.cellArea_m2;
        }

        //first the gaseous
        float w = 0;
        float b = getPlume_b(r_c, z, H);//this is needed for dust eventually.
        if (D < ops.MIN_ABLH) {
            D = ops.MIN_ABLH;
        }

        if (z < D && H < D) {//return 0 if not the case
            float ratio = this.h_m / D;

            if (ratio > MAX_RATIO) {
                w = r_c[TabledGaussians.IL_PLUME] * yblock * getInversion_b(3, z, H, D, r_c, false);

            } else if (ratio > MIN_RATIO) {//between 3 and 2 - the in between zone

                float frac2 = (ratio - MIN_RATIO) / (float) (MAX_RATIO - MIN_RATIO);//this is in between [1,0]
                float w2 = r_c[TabledGaussians.IL_PLUME] * yblock * getInversion_b(3, z, H, D, r_c, false);
                float w1 = r_c[TabledGaussians.PLUME] * yblock * b;

                w = (frac2 * w2 + (1f - frac2) * w1);//linear combine

            } else {//D can be ignored
                w = r_c[TabledGaussians.PLUME] * yblock * b;
            }

        }//if z,H applicable
        temp[IND_GAS] = w / U;
        //then coarse particle
        float setComp = PlumeMath.getSettlingComp(r_c, z, H, ops.WSET);
        temp[IND_CPM] = r_c[TabledGaussians.PLUME] * yblock * b * setComp / U;
        return temp;
    }

    private void initYBlocks() {
        this.aw_y_blocks = new float[StabiClasses.STABILITY_CLASS_NAMES.length];
        this.r_cs = new float[StabiClasses.STABILITY_CLASS_NAMES.length][];
        for (int s = 0; s < this.aw_y_blocks.length; s++) {
            float[] r_c = RC.getRC(h_m, s);
            this.r_cs[s] = r_c;
            this.aw_y_blocks[s] = PlumeMath.getYblock(r_c, w_m) * this.cellArea_m2;//area weighted already, ready to be used.
        }
    }

}
