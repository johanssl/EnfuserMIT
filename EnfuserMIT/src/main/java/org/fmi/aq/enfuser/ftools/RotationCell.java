/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import org.fmi.aq.enfuser.options.FusionOptions;
import static java.lang.Math.abs;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;

/**
 *
 * @author johanssl
 */
public class RotationCell  {

    public final float cellRes_m;
    public final float cellArea_m2;
    private final float h_m;
    private final float w_m;
    public float dist_m;

    public short[] trueSector;

    short[] rotatedH_m;
    short[] rotatedW_m;
    float[] rotatedLatAdd_dir;
    float[] rotatedLonAdd_dir;

    protected float[] lonScalers;

    public RotationCell(float dlat_m, float h_m, float w_m) {

        this.h_m = h_m;
        this.w_m = w_m;
        this.cellRes_m = dlat_m;
        this.cellArea_m2 = dlat_m * dlat_m;
        this.dist_m = (float) Math.sqrt(h_m * h_m + w_m * w_m);

        setRotations();
    }

    public float currentLat_fast(float lat, int wd) {
        return this.rotatedLatAdd_dir[wd] + lat;
    }

    public float currentLon_fast(float lon, int wd) {

        return this.rotatedLonAdd_dir[wd] + lon;
    }

    public float currentLat(float lat, int wd, FusionOptions ops) {
        return this.rotatedLatAdd_dir[wd] + lat;
    }

    public float currentLon(float lat, float lon, int wd, FusionOptions ops) {
        int ablat = (int) abs(lat);
        return (this.rotatedLonAdd_dir[wd])* lonScalers[ablat] + lon;
    }

    public float getFastOffset_hm(float windDir, FusionOptions ops) {
        if (ops == null) {
            return this.rotatedH_m[(int) windDir];
        }
        return this.rotatedH_m[(int) windDir];
    }

    public float getFastOffset_wm(float windDir, FusionOptions ops) {
        if (ops == null) {
            return this.rotatedW_m[(int) windDir];
        }
        return this.rotatedW_m[(int) windDir];
    }

    public int getTrueSector(float windDir, int sectorIncrement_degs) {
        return this.trueSector[(int) windDir] / sectorIncrement_degs;
    }

    private void setRotations() {
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

  
    public final static int MAX_RATIO = 5;
    public final static int MIN_RATIO = 3;

   
}
