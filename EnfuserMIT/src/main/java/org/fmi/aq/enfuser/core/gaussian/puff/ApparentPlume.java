/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.getInversion_b;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.getPlume_b;
import org.fmi.aq.enfuser.core.gaussian.fpm.TabledGaussians;
import static org.fmi.aq.enfuser.core.gaussian.fpm.TabledGaussians.RC;
import static org.fmi.aq.enfuser.core.gaussian.puff.Transforms.UintoApparentWind;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author Lasse
 */
public class ApparentPlume extends PuffCarrier {

    public float x_scaler;
    public final float[] latlon_orig;

    public ApparentPlume(FusionCore ens, Dtime dt, float[] hyx, float[] QperS,
            float obj_heading, float objSpeed, float[] latlon,
            String ID, int sourcType, int NSR_limit_m) {

        super(ens, dt,DZ_EXP,hyx, QperS, latlon, ID, sourcType, 1, NSR_limit_m);

        this.latlon_orig = latlon;
        float U = this.windState.U;
        float wDir = this.windState.wdir;
        float[] u_dir = UintoApparentWind(false, U, wDir, objSpeed, obj_heading);//apparent wind mod.
        this.windState.manuallySetUdir(u_dir);
        initTrigons();
        this.canMerge = false;
        //sigma correction
        float u_corr = Math.max(0.1f, U);
        float ur_corr = Math.max(0.1f, U);//apparent wind strength;

        this.x_scaler = u_corr / ur_corr;

    }

    @Override
    public void evolve(float tick, PuffPlatform pf, Dtime dt) {
        //do nothing since plume doesn't evolve at all
    }

    @Override
    public boolean isInactive() {
        return false;//is always on
    }

    @Override
    public int isSubstantial(PuffPlatform pf) {
        return PuffCarrier.DISCARD_TERMINATION; //this plume must die. Immediately. Always.
    }

    /**
     * The main method for concentration computation originating from this plume
     * shape, with the given coordinate location.
     *
     * @param cq CQ float[][] data to which the contributions of this plumes is
     * added to. C dimension stands for Categories, Q for primary pollutant
     * types. cq CAN be null, in which case the contribution is stacked to Q
     * @param z obervation height
     * @param y y-dimension for Gaussian computation (parallel distance from mid
     * axis)
     * @param x x-dimension for Gaussian computation (distance along mid axis)
     */
    @Override
    public void stackConcentrations(float[][] cq, float z, float y, float x, FusionOptions ops) {
        if (this.isInactive()) {//return
            return;
        }
        
        //1: transform coordinates
        float yy = y - this.HYX[1];// get dy, from the puff source's point of view
        float xx = x - this.HYX[2];// get dx
        //wdir rotation
        y = (yy * cos_beta - xx * sin_beta);
        x = (xx * cos_beta + yy * sin_beta);
        x *= this.x_scaler;//the apparent plume special

        //PROCEED TO CONCENTRATION COMPUTATION==================  
        if (x < 0) {
            return;
        }

        float[] r_c = RC.getRC(x, this.windState.initialStabiClass());
        float total = 0;

        float D = this.windState.maxABLH();
        float H = HYX[0];

        float b = getPlume_b(r_c, z, H);
        float ybl = PlumeMath.getYblock(r_c, y);

        float plumeBase = ybl * cons_distLimiter();//this is a common feature for all puff solutions

        if (x / D > 2) {
            total = r_c[TabledGaussians.IL_PLUME] * plumeBase * getInversion_b(3, z, H, D, r_c, false);
        } else if (x / H > 3) {
            total = r_c[TabledGaussians.IL_PLUME] * plumeBase * getInversion_b(1, z, H, D, r_c, false);
        } else {//close range modelling, cannot use low N b_IL.

            if (z > D || H > D) {
                total = 0;//r_c[IND_inv_4PIr]*getYblock(r_c[IND_ry], y)*getPlume_b(r_c,z,H)*D_PENETR;
            }
            total = r_c[TabledGaussians.PLUME] * plumeBase * b;
            if (H / D > 0.7) {//single plume

                float Hmirror = D + (D - H);
                float b2 = getPlume_b(r_c, z, Hmirror);
                total += r_c[TabledGaussians.PLUME] * plumeBase * b2;
            }

        }

        float wsetComp = PlumeMath.getSettlingComp(r_c, z, H, ops.WSET);
        float total_dust = r_c[TabledGaussians.PLUME] * plumeBase * wsetComp * b;
        this.CQstack(total, total_dust, cq);
        
    }

}
