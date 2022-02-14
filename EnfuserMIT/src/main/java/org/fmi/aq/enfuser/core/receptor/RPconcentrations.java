/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import java.io.Serializable;
import org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment;
import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.options.Categories;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import static org.fmi.aq.enfuser.options.Categories.CAT_RESUSP;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.assimilation.PersistentAdjustments;

/**
 *
 * @author johanssl
 */
public class RPconcentrations implements Serializable{


    public float[][] dat_cq;
    public final byte[] bgType;
    public final static byte BG_MODEL = 0;
    public final static byte BG_DUMMY = 1;
    public final static byte BG_PPF = 2;
    final int sysH;
    final byte min;
    final Dtime[] allDates;
    
    //some specific NOx-O3 statistics for debugging
    public float no2PotentialReduct = 0;//this is [ugm-3] that NO2 was reduced due to insufficient O3
    public float noUnconverted = 0;
    public final float resusp_raw;
    
    public RPconcentrations(RP env, FusionCore ens, RPstack dc,
            Dtime dt, Dtime[] allDates, boolean regression) {
        this.sysH = env.sysH;
        this.min = env.min;
        this.allDates = allDates;
        bgType = new byte[ens.ops.VARA.Q_NAMES.length];
        dat_cq = new float[ens.ops.CATS.CATNAMES.length][ens.ops.VARA.Q_NAMES.length];
        float[][] fromPuffs_qc = ens.getPuffModelledAdditions_QS(env.lat, env.lon, dt);

        //pair the drivers with 
        for (int q : ens.ops.VARA.Q) {
            for (int c : ens.ops.CATS.C) {
                dat_cq[c][q] = dc.stack_cq[c][q];
                //If even this is not found, then use simple proxies.
                if (fromPuffs_qc != null) {
                    float red = dc.blocker_midRange.getReduction_CQ(c, q, ens.ops.VARA);
                    dat_cq[c][q] += fromPuffs_qc[q][c]*red;
                    if (c == CAT_BG) {
                        bgType[q] = BG_PPF;
                    }

                } else if (c == CAT_BG) {//ppf is null or fromPuffs is null => background has not been set.  
                    Float f = ens.getModelledBG(q, dt, env.lat, env.lon, ens.ops.orthogonalBg);
                    if (f != null) {
                        dat_cq[CAT_BG][q] = f;
                        bgType[q] = BG_MODEL;
                    } else {
                        dat_cq[CAT_BG][q] = 0;
                        bgType[q] = BG_DUMMY;
                    }
                }
            }//for cats
        }//for q

        this.resusp_raw = dc.stack_cq[CAT_RESUSP][ens.ops.VARA.VAR_COARSE_PM];
    }


    public float[] getExpectances_assimilation(HourlyAdjustment ol,
            int q, FusionCore ens, RP env) {
        if (ens.ops.VARA.isPrimary[q]) {
            return getExpectances_primary(ol, q, ens,0);
        } else {
           //as default, assimilation target only the 'primary' modelling variables. So, this should not happen. 
           return ens.prox.getProxyComponents(q,env, ens);
        }
    }

    public float[] getExpectances(int q, FusionCore ens, RP env) {
        if (ens.ops.VARA.isPrimary[q]) {
            return getExpectances_primary(null, q, ens,0);
        } else {
           return ens.prox.getProxyComponents(q,env, ens);
        }
    }
    
    public float[] getExpectances_forecastTest(int q, FusionCore ens, RP env) {
        if (ens.ops.VARA.isPrimary[q]) {
            return getExpectances_primary(null, q, ens,-24);
        } else {
           return ens.prox.getProxyComponents(q,env, ens);
        }
    }


    private HourlyAdjustment fetch(int q, FusionCore ens, int hourAdd) {
        AdjustmentBank oc = ens.getCurrentOverridesController();
        return oc.get(sysH+hourAdd, min, q, ens.ops);
    }
    
    private float[] getExpectances_primary(HourlyAdjustment ol,
            int q, FusionCore ens, int hourAdd) {

        if (ol==null) ol =fetch(q,ens,hourAdd);//this exists initially ONLY during assimilation. Otherwise, fetch.

        float[] arr = new float[ens.ops.CATS.CATNAMES.length];//build components
        for (int c : ens.ops.CATS.C) {
            float ex = dat_cq[c][q];
            //apply DATA FUSION mods, also longer term learning.
            arr[c] = PersistentAdjustments.operate(ol,c, q, allDates, ens, ex);
        }

        //balancing required?
            if (q == ens.ops.VARA.VAR_O3) {//non negativity, could be negative (and therefore needs scaling) due to NOx interractions.
                balanceO3(arr);
            } else if (q == ens.ops.VARA.VAR_NO2 || q == ens.ops.VARA.VAR_NO) {
                //Depends on O3 background. Must get o3 override as well
                //this is why O3 is assimilated BEFORE NOx.
                float o3bg = this.dat_cq[CAT_BG][ens.ops.VARA.VAR_O3];
                 //apply DATA FUSION mods, also longer term learning.
                HourlyAdjustment overO3 = fetch(ens.ops.VARA.VAR_O3,ens,hourAdd); 
                o3bg = PersistentAdjustments.operate(overO3,CAT_BG,ens.ops.VARA.VAR_O3,allDates,ens, o3bg);
                
                if (hourAdd==0) {//logging
                    if (q == ens.ops.VARA.VAR_NO2) {
                        no2PotentialReduct = photoChemNO2(arr, o3bg);
                    } else {//NO
                        noUnconverted = photoChemNO(arr, o3bg);
                    }
                }
            }//if nox

        return arr;
    }

    public static void balanceO3(float[] comps) {
        float pos = 0;//the background
        float neg = 0;//virtual negative O3 contributions due to NOx.
        for (float f : comps) {
            if (f > 0) {
                pos += f;
            } else {
                neg += f;
            }
        }//for cats

        float total = pos + neg;
        if (total < 0) {//action must be taken
            float scaler = -1 * pos / neg;//both of these are negative so this is positive
            //neg*-x = pos
            //example: pos = 50, neg = -80; total is -30 => the negative sum must be scaled to equal -pos. x*-80 = -50 => x = 0.625
            for (int i = 0; i < comps.length; i++) {
                if (comps[i] < 0) {
                    //scale down the negative contributions
                    comps[i] *= scaler;
                }
            }
        }
    }

    /**
     * Post-processing for NO2 modelled concentrations, crudely simulating the
     * photochemical reactions for NO2, NO and O3. The main assumption is that
     * all NO is initially ASSUMED to be converted to NO2 and this is later on
     * adjusted here. The limiting factor is the amount of ozone available.
     * Therefore, the amount of NO2 is to be adjusted based on ozone
     * availability and converted "back" to NO. In this very simplistic approach
     * the differences in molecular masses are ignored.
     *
     * @param compsNO2 component split (categories) of NO2 concentrations that
     * form the total modelled NO2 amount
     * @param O3bg O3 background value [µgm-3]
     * @return the reduction amount that was applied, while the components were
     * adjusted.
     */
    public float photoChemNO2(float[] compsNO2, float O3bg) {

        //NO2 has currently ALL assumed photochemical additions (65%)
        //compute the o3-converted NO2 from all components. In case this is larger than O3bg allows, then adjust
        float convertedNO2 = 0;
        float nonBGtotal = 0;

        for (int i = 0; i < compsNO2.length; i++) {
            if (i == CAT_BG) {
                continue;
            }
            nonBGtotal += compsNO2[i];
            convertedNO2 += compsNO2[i] * Categories.NO2_FRAC_PHOTOCH;///(Categories.NO2_FRAC_FLAT + Categories.NO2_FRAC_PHOTOCH);// 65/85 = 75% basically
            //before this process the assumption has been that this PHOTOCH fraction HAS BEEN converted to NO2, assuming there's enough O3.
        }//for cats

        if (nonBGtotal <= 0) {
            return 0;//nothing to do, and NaN would be the result if evaluation proceeds.
        }
        if (convertedNO2 <= O3bg) {
            return 0;//there's enough O3 for this amount of NO2
        }

        float reduction = convertedNO2 - O3bg;//if we get here this is always positive and shows how much NO2 must be reduced now.

        float scaler = (nonBGtotal - reduction) / nonBGtotal;//
        // nonBgTotal*x = nonBgTotal - reduction
        //=>  x = (nonbgt - red)/nonbgt
        for (int i = 0; i < compsNO2.length; i++) {
            if (i == CAT_BG) {
                continue;
            }
            compsNO2[i] *= scaler;

        }

        return reduction;
    }

    /**
     * Post-processing for NO2 modelled concentrations, crudely simulating the
     * photochemical reactions for NO2, NO and O3. The main assumption is that
     * all NO is initially ASSUMED to be converted to NO2 and this is later on
     * adjusted here. The limiting factor is the amount of ozone available.
     * Therefore, the amount of NO2 is to be adjusted based on ozone
     * availability and converted "back" to NO. In this very simplistic approach
     * the differences in molecular masses are ignored.
     *
     * @param compsNO component split (categories) of NO concentrations that
     * form the total modelled NO2 amount
     * @param O3bg O3 background value [µgm-3]
     * @return the reduction amount that was applied, while the components were
     * adjusted.
     */
    public float photoChemNO(float[] compsNO, float O3bg) {

        //compute the o3-converted NO2 from all components. In case this is larger than O3bg allows, then adjust
        float convertedNOtoNO2 = 0;
        float nonBGtotal = 0;

        //float conversionFrac = getRawConvertedNO2_fraction();
        for (int i = 0; i < compsNO.length; i++) {
            if (i == CAT_BG) {
                continue;
            }
            nonBGtotal += compsNO[i];
            convertedNOtoNO2 += compsNO[i] * Categories.NO2_FRAC_PHOTOCH / Categories.NO_FRAC_FLAT;//*(1f-FusionOptions.CATS.no2Frac(i) );
            //before this process the assumption has been that this PHOTOCH fraction HAS BEEN converted to NO2, assuming there's enough O3.
            //NO has only the NO_FRAC_FLAT at this stage, so it is possible to compute the converted NO2 from NO.

        }//for cats

        if (nonBGtotal <= 0) {
            return 0;//nothing to do
        }
        float unconverted = convertedNOtoNO2 - O3bg;// in case O3 bg is smaller, then we should have more NO than we have currently
        if (unconverted <= 0) {
            return 0;//there's more than enough O3 to convert all reserve NO to NO2, return.
        }
        //unconverted is larger than 0 => convertedNOtoNO2 is LARGER than O3bg => adjust (increase) NO as a post-process "cashback"
        float scaler = (unconverted + nonBGtotal) / nonBGtotal;//
        //nonBgTotal*x = unconverted + nonBgTotal
        //example: O3bg = 40, noSum = 30, convNO2 = 100;  => unconverted = 60.
        for (int i = 0; i < compsNO.length; i++) {
            if (i == CAT_BG) {
                continue;
            }
            compsNO[i] *= scaler;

        }
        return unconverted;

    }
}
