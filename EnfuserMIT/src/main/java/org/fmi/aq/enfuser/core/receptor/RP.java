/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 *
 * @author johanssl
 */
public class RP implements Serializable {

    public RPconcentrations ccat;
    public Met met;
    public final int sysH;//hour epoch
    public final byte min;//minutes
    public final float lat, lon;
    public Dtime dt;
    public Dtime[] allDates;
    public final boolean indoorReduction_applicable;
    public final byte groundZero_surf;// static ground-zero variable
    public final byte groundZero_func;// static ground-zero variable
    public final byte bh_g0;
    public final float vegeDeph_m_g0;

    public final short absoluteGroundElevation;//includes absolute ground elevation.
    public final short relativeGroundElevation;//relative elevation, based on 8 points with roughly 1km distances, includes obsH but no buildings
    public final short observationElevation;

    //SECONDARY===============
    public final float logWind;
    public final float mirroringSum; // blockAnalysis - streetcanyon strength factor
    public byte closestB_dist_m = 100;
    public byte closestRoad_dist_m = 100;
    public final short shortestEmDist_m;

    //==============================
    public final boolean ppfWasAvailable;
    public HashMap<String,Double> temp = null;//this has a special use-case in an addon-module, that estimates a proxy variable based on a symbolic expression.
    
    public RP(EnvArray enva, double lat, double lon, FusionCore ens, Dtime dt, Dtime[] allDates) {
        this.ppfWasAvailable = ens.puffModellingAvailable(dt);
        this.sysH = dt.systemHours;
        this.min = (byte) dt.min;
        //make sure that Dtime[] can be fetched from ens.TZT using this sysH epoch
        ens.tzt.getDates(dt);//now using getDate_notNullSafe(sysH) works.
        this.dt = dt;
        this.allDates = allDates;
        this.met = enva.met;

        this.lat = (float) lat;
        this.lon = (float) lon;

        this.logWind = enva.logWind;
        this.mirroringSum = enva.mirrorStrenSum;

        if (enva.ba != null) {
            absoluteGroundElevation = enva.absoluteGroundElevation;
            relativeGroundElevation = enva.relativeGroundElevation;
        } else {
            absoluteGroundElevation = 0;
            relativeGroundElevation = 0;
        }
        this.observationElevation = enva.obsHeight_aboveG;
        //basic land use
        this.groundZero_surf = enva.groundZero_surf;
        this.groundZero_func = enva.groundZero_func;

        this.bh_g0 = enva.bh_g0;
        this.indoorReduction_applicable = ens.ops.reduceCons_atConstruct && this.atBuilding();
        this.ccat = new RPconcentrations(this, ens, enva.dcat, dt, allDates, false);

        this.closestB_dist_m = (byte) Math.min(enva.ba.closestBuilding_m, 125);
        this.closestRoad_dist_m = (byte) Math.min(enva.ba.closestRoad_m, 125);
        this.shortestEmDist_m = enva.shortestEmDist_m;
        this.vegeDeph_m_g0 = (float) Math.min(1.0, enva.ba.vegeDepth_g0);
    }
    
    public boolean needsReEvaluation(FusionCore ens) {
        
        if (this.ppfWasAvailable) {
            return false;  
        } else return ens.puffModellingAvailable(dt);
        
    }

    public boolean atBuilding() {
        return (this.bh_g0 > this.observationElevation && OSMget.isBuilding(this.groundZero_surf));
    }

    public Float getWindDir(FusionOptions ops) {
        if (met == null) {
            return null;
        }
        return met.getWdir();
    }

    public boolean directed(FusionOptions ops) {
        return this.getWindDir(ops) != null;
    }

    public Dtime[] getDt_Clones(FusionCore ens) {

        Dtime dt = ens.tzt.getDates_notNullSafe(sysH)[TZT.UTC].clone();
        dt.addSeconds(min * 60);

        Dtime dtl = ens.tzt.getDates_notNullSafe(sysH)[TZT.LOCAL].clone();
        dtl.addSeconds(min * 60);

        return new Dtime[]{dt, dtl};
    }

    
    public float getExpectance(int typeInt, FusionCore ens) {
        return FuserTools.sum(this.ccat.getExpectances(typeInt, ens, this));
    }
    

    public float[] getExpectances(int typeInt, FusionCore ens) {
        return this.ccat.getExpectances(typeInt, ens, this);
    }

    public float cq(int c, int q) {
        return this.ccat.dat_cq[c][q];
    }



    public int variableRes(float res_m, FusionOptions ops) {
        if (!ops.variableResolution) {
            return 0;
        }

        if (this.closestB_dist_m / res_m > 12
                && this.closestRoad_dist_m / res_m > 12
                && this.shortestEmDist_m / res_m > 12) {
            return 2;
            
        } else if (this.closestB_dist_m / res_m > 6
                && this.closestRoad_dist_m / res_m > 6
                && this.shortestEmDist_m / res_m > 6) {
            return 1;
            
        }
        
        return 0;
    }


    public float[][] getPrimaryExps(ArrayList<Integer> types, FusionCore ens) {
        float[][] dat = new float[types.size()][];
        int k = -1;
        for (int typeInt : types) {
            k++;
            if (!ens.ops.VARA.isPrimary[typeInt]) {
                continue;
            }
            dat[k] = this.getExpectances(typeInt, ens);

        }//for types
        return dat;
    }
    
    
        public static byte[] expsToContributionPercents(float[] cats_q) {
        GlobOptions g= GlobOptions.get();
        byte[] contribs = new byte[cats_q.length];
        float sum = 0;
        for (float f : cats_q) {
            sum += f;
        }

        if (sum == 0) {
            return contribs;//no contribution from any category. Avoid NaN.
        }
        for (int c : g.CATS.C) {
            byte b = (byte) (100f * cats_q[c] / sum);//cannot exceed 100.
            contribs[c] = b;
        }
        return contribs;
    }

    public static byte[][] expsToContributionPercents(float[][] cq, FusionOptions ops) {
        GlobOptions g= GlobOptions.get();
        byte[][] contribs_cq = new byte[g.CATS.CATNAMES.length][ops.VARA.Q_NAMES.length];
        for (int q : ops.VARA.Q) {
            float sum = 0;

            for (int c : g.CATS.C) {
                sum += cq[c][q];
            }

            if (sum == 0) {
                continue;//no contribution from any category. Avoid NaN.
            }
            for (int c : g.CATS.C) {
                byte b = (byte) (100f * cq[c][q] / sum);//cannot exceed 100.
                contribs_cq[c][q] = b;
            }

        }//for q
        return contribs_cq;
    }



}
