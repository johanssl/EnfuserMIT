/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.ftools.FastMath;
import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.XD_RATIO_MAX;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.XD_RATIO_MIN;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.getInversion_b;
import static org.fmi.aq.enfuser.core.gaussian.fpm.PlumeMath.getPlume_b;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.core.gaussian.fpm.TabledGaussians;
import static org.fmi.aq.enfuser.core.gaussian.fpm.TabledGaussians.RC;
import org.fmi.aq.enfuser.meteorology.WindState;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 * This class represents a parcel of emission masses that travel through the
 * modelling area according to the meteorological data. The carried emissions
 * also have source (category) or several of them and this class must keep
 * that information available.
 * 
 * Emitters can release these and when emitted they can contribute to concentration
 * fields.
 * 
 * The puffs need a 'platform' to handle the creation, releases and updates
 * for these puffs. This is handled by the PuffPlatform.
 * 
 * The puffs can update their emission masses, position, spread and meteorology
 * during their lifetime. They also have a specific domain where they are allowed
 * to remain. This class also has a number of methods for effective concentration
 * field computations. 
 * 
 * An instance of this is just single puff, but there can
 * be hundreds of thousands of them flying a round simultaneously.
 * 
 * @author johanssl
 */
public class PuffCarrier {

    //ID and category
    public String ID;//this can more specifically define the source of the puff (e.g., ship/power plant name).
    public final int sourcType;//the emission source category index
    public boolean mapPuff = false;//if true, then this was created by the RangedMapEmitter
    public byte[][] fractionalCategories_cq = null;//value 100 => 100% contribution done by category c for pol.type q. Used for mid range emitters that can have multiple categories in them.
    
    //the carried emission masses
    public float[] Q; 
    public final float origLat;
    public final float origLon;
    
    public WindState windState;
    //merging
    public boolean merged = false;//if true, this is a merged product of multiple puffs.
    public int mergedFrom =0;
    public boolean canMerge = true;//if true then this puff is allowed to merge with other puffs.
    static final String MERGED = "merged";
    public boolean usesMicroMeteorology =false;
    //position, trip- and time meters
    public float timer_total_s = 0;//how many seconds the puff has been alive
    public float immaterial_meters_left = -1;//how many travel meters is still required for the puff to be accounted in concentration computations.
    protected boolean toBeRemoved =false;
    public float activate_after_s = -1;//a freezing timer that will not let the puff to update position and spread. This can be used to create multiple puffs at a given time and freeze them for a couple of seconds => they evolve in correct sequence
    public float dist_total_m = 0;//the trip meter - how many meters have the center of the puff travelled so far.
    public final float[] HYX; //puff center point HEIGHT[m] , Y[m] and X[m].
    final int NSR_limit_m;//e.g., with 500, calculated concentration will not be affected from nearSourceReducer anymore. 
    Respec respec = new Respec();
    //rotations (for coordinate transformations) and randomizer (wind direction)
    public float cos_beta, sin_beta;
    public final float lonScaler;
    private final int RANDS = 20;
    RandSequencer rs;

    //puff removal (discard)
    public int discard_zone;//for power plants and other notable point sources
    //DZ = Discard Zone.
    public final static int DZ_VEXP = 0;//for significant point sources such as power plants - these are allowed to live and evolve in much larger area.
    public final static int DZ_EXP = 1;//for basically other puffs - a slightly expanded area around the modelling area definition
    public final static int DZ_NORMAL = 2;//the actual modelling area definition (not used for puffs at the moment)
    public boolean enteredNormalZone = false;//if true then the puff has at least one time entered the DZ_NORMAL.
    
    //temporary properties for faster concentration computations
    public float ybl_factor;// the y-block for the Gaussian concentration computations
    public float total_il;
    private float total_d;//for setting block (coarse particles)
    
    //Sandbox-testing, static variables 
    //(NOT to be meddeled with in operational modelling)
    public static float PLUME_RISE_M = 0;//a placeholder for plume rise effects (just adds to H)
    //status messages (for puff removal from stack)
    public final static int DISCARD_TERMINATION = 100;
    public final static int DISCARD_OUT = 1;
    public final static int OK = 2;
    
    
    /**
     * The main constructor for puff creation in operational use.
     * @param ens the core (for meteorological data fetch)
     * @param dt creation time
     * @param discardZone applicable zone for the puff (if outside this an be discarded)
     * @param hyx initial Height Y[m] and X[m] in the PuffPlatforms coordinate system.
     * @param Q emission masses [ug]
     * @param latlon initial release coordinates
     * @param ID source ID (can be null)
     * @param sourcType source category index
     * @param immaterial_m amount of meters that the puff must travel before
     * it can contribute to concentrations
     * @param NSR_limit_m amount of meters before which the concentrations
     * caused by the puff can be reduced artificially.
     */
    public PuffCarrier(FusionCore ens, Dtime dt,
            int discardZone,  float[] hyx, float[] Q,
            float[] latlon, String ID, int sourcType, float immaterial_m, int NSR_limit_m) {
        this.HYX = copyArr(hyx, 1);
        this.NSR_limit_m = NSR_limit_m;
        this.discard_zone = discardZone;
        this.sourcType = sourcType;
        this.Q = Q;
        if (immaterial_m > 0) {
            this.immaterial_meters_left = immaterial_m;
        }
        
        this.rs = new RandSequencer(RANDS);
        this.ID = ID;
        this.origLat = latlon[0];
        this.origLon = latlon[1];
        this.windState = new WindState();
        this.updateWindState(dt, ens, latlon[0], latlon[1]);
        initTrigons();
        this.lonScaler = FastMath.FM.getFastLonScaler(latlon[0]);

    }
    
    /**
     * For flattening the high concentration values at the early stages of the
     * puff's life time near the center, a smoothening factor can be used.
     * This method will give this reducing scaler in between [0,1].
     * Such a distance based smoother can be introduced e.g, in the LTM
     * profiles for power plants.
     * 
     * @return a scaling value in between [0,1] and most often just 1.
     */
    protected float cons_distLimiter() {

        if (this.dist_total_m > this.NSR_limit_m || this.NSR_limit_m <= 0) {
            return 1f;
        } else {
            float f = this.dist_total_m / this.NSR_limit_m;
            return f;
        }

    }

    /**
     * Returns true if the puff is inactive. Inactivity means that the puff
     * does not contribute to concentration computations, does not merge
     * with other puffs. An inactive puff still updates.
     * 
     * Note that this is most often a temporary status
     * and an inactive puff can retain activity after it has traveled
     * a bit farther.
     * @return 
     */
    public boolean isInactive() {
        return (this.activate_after_s > 0
                || this.dist_total_m == 0
                || this.immaterial_meters_left > 0
                );
    }

    /**
     * Updates the temporal variables and travel distance/ location metrics. The
     * propagation of the center of the puff is being monitored.
     *
     * @param tick Time in seconds to be evolved.
     * @param dt current time
     * @param pf the puffPlatform
     */
    public void evolve(float tick, PuffPlatform pf, Dtime dt) {
        //check inactivity counters
        if (activate_after_s > 0) {
            this.activate_after_s -= tick;
            
           if (this.activate_after_s>=0) {//still not evolving
               return;
               
           } else {//Went over. Do a mini update with smaller tick
               tick+=this.activate_after_s;//negative or zero
               if (tick<=0) return;
           }
        }//if not active yet
        this.timer_total_s += tick;

        //within this tick the puff travelled tick*U meters in total. This distance is distributed to y-x axis.
        float trip = windState.U * tick;
        if (this.immaterial_meters_left > 0) {//reduce until material (<0)
            this.immaterial_meters_left -= trip;
        }

        this.dist_total_m += Math.max(1, windState.U) * tick;
        //dist_total is used for the 'spread' of the puff (eddy diffusitivites)
        //this max() clause makes it so that even with low speeds the puffs
        //continue to diffuse slightly.
        this.HYX[1] += trip * this.sin_beta;
        this.HYX[2] += trip * this.cos_beta;
        //update meteorology, wind state, decays
        this.respec.update(tick, trip, pf, this,dt);
    }

    /**
     * Get the current position of the puff center as in WGS84 latitude.
     * @param pf
     * @return latitude value
     */
    public float currentLat(PuffPlatform pf) {
        return pf.latitudeFromY(HYX[1]);
    }

    /**
     * Get the current position of the puff center as in WGS84 longitude.
     * @param pf
     * @return longitude value
     */
    public float currentLon(PuffPlatform pf) {
        return pf.longitudeFromX(HYX[2]);
    }

    
    /**
     * Based on the current centerpoint location and time, fetch Met objects
     * for raw wind data. Then, based on height and surface properties assess
     * the prevailing wind conditions for the puff. While doing so, 
     * some randomization (direction, speed and even height) may be applied.
     * 
     * @param dt time
     * @param ens the core
     * @param lat latitude
     * @param lon longitude
     */
    public void updateWindState(Dtime dt, FusionCore ens,float lat, float lon) {

        float height_m = HYX[0];
        windState.setTimeLoc(lat, lon, dt, ens,null);

        if (!(this instanceof MarkerCarrier)) {
            if (WindState.ADDS_RANDOM) {
               double uadd = rs.nextRand() * ens.ops.perfOps.puffRespecRandomness() / 30f;
               double wadd = rs.nextRand() * ens.ops.perfOps.puffRespecRandomness();//randomization +-degs
               windState.addRandom_uDir(uadd,wadd,ens.ops);
               //some elevation randomization
               
                double hadd = rs.nextRand() * ens.ops.perfOps.puffRespecRandomness() / 10f;
                this.HYX[0]+=hadd;
                if (HYX[0]<0) HYX[0]=0;
            }
        }
        
        windState.profileForHeight(height_m, ens);
    }


    /**
     * Based on wind direction, set trigonometric values to facilitate puff's
     * positional updates int the H-Y-X coordinate system.
     */
    protected void initTrigons() {
        //0 => blows from left to right. This is ACTUALLY 270 wdir.
        float rotated_wd = windState.wdir + 90;
        if (rotated_wd > 360) {
            rotated_wd -= 360;
        }

        double beta = -(2 * Math.PI / 360.0) * rotated_wd;
        float[] sinCos = FM.getFastSinCos(beta);
        this.sin_beta = sinCos[0];
        this.cos_beta = sinCos[1];

    }

    /**
     * Evaluates whether this particular puff emission has traveled far enough
     * and has become irrelevant. An irrelevant puff can be removed from the
     * list of puffs thereby increasing performance.
     *
     * @param pf the puffPlatform
     * @return integer classification to define the current meaningfulness of
     * the puff. This can be one of: -DISCARD_OUT (the puff can be removed (out
     * of bounds) from the stack, has no relevance) -DISCARD_TERMINATION (the
     * puff is to be removed from stack (used for ApparentPlume) -OK the puff is
     * to remain on the stack until it becomes irrelevant
     */
    public int isSubstantial(PuffPlatform pf) {
        
        if (this.toBeRemoved) return DISCARD_TERMINATION;//not allowed to live
        
        float lat = this.currentLat(pf);
        float lon = this.currentLon(pf);
        //first check termination through time
        boolean inMainDom = pf.in.bounds.intersectsOrContains(lat, lon);
        boolean inExpDom = pf.b_expanded.intersectsOrContains(lat, lon);
        //boolean inMainIshDom = pf.b_largerBounds.intersectsOrContains(lat, lon);
        
        if (inMainDom) {
            this.enteredNormalZone = true;//this by default false and is set to true only bu this way.
        }

        if (!pf.b_veryExpanded.intersectsOrContains(lat, lon)) {
            //no puff is allowed to live outside the MAXIMUM area
            return DISCARD_OUT;

        } else if (this.discard_zone != DZ_VEXP && !inExpDom) {
            //these puffs are not allowed to live outside the EXPANDED area
            return DISCARD_OUT;
        }
        
        //This puff will not be discarded or terminated. Check main domain
        return OK;

    }

    /**
     * The main method for concentration computation originating from this puff,
     * with the given coordinate location.
     *
     * @param cq_add CQ matrix (float[][], C for Categories, Q for primary
     * types) in which the concentration contribution of this puff is to be
     * stored. Can be null, in which case the contribution is stored Q instead.
     * @param z observation height
     * @param y Gaussian dimension y (parallel distance to x-axis)
     * @param x Gaussian dimension x (distance along the x-axis)
     * @param ops options
     */
    public void stackConcentrations(float[][] cq_add, float z,
            float y, float x, FusionOptions ops) {
        if (this.isInactive()) {//return 
            return;
        }

        //1: transform coordinates
        float y_diff = y - this.HYX[1];// get dy, from the puff source's point of view
        float x_diff = x - this.HYX[2];// get dx
        float d2 = y_diff*y_diff + x_diff*x_diff;

        float[] r_c = RC.getRC(this.dist_total_m, windState.initialStabiClass());
        float total;

        float D = windState.maxABLH();
        float H = HYX[0];
        float pre = r_c[TabledGaussians.PUFF_RAW];
        float b = getPlume_b(r_c, z, H);
        float ypuf = d2 / (-4 * r_c[TabledGaussians.IND_ry]);
        ypuf = FM.fexp(ypuf);

        float puffBase = pre * ypuf * cons_distLimiter();//this is a common feature for all puff solutions

//FOUR CASES TO CHECK=====================
        if (z > D && H < D) {//observation above D while the source is below (+-)
            total = puffBase * b * PlumeMath.BLH_PENETR;
        } else if (z > D && H > D) {//both the observation and source are above D (++)
            H -= D;
            z -= D;
            total = puffBase * b;

        } else if (z < D && H > D) {//source above, observation below (-+)
            total = puffBase * b * PlumeMath.BLH_PENETR;
        } else {  //z and H are both below D - the usual case (--)
            float ratio = this.dist_total_m / D;

            if (ratio > XD_RATIO_MAX) {
                float b_il = getInversion_b(1, z, H, D, r_c, true);
                total = puffBase * b_il;
            } else if (ratio > XD_RATIO_MIN) {//between 3 and 2
                float b_il = getInversion_b(1, z, H, D, r_c, true);
                float w2 = ratio - XD_RATIO_MIN;
                total = (w2 * b_il + (1f - w2) * b) * puffBase;
            } else {//D can be ignored
                total = puffBase * b;
            }

        }

        float wsetComp = PlumeMath.getSettlingComp(r_c, z, H, ops.WSET);
        float total_dust = puffBase * wsetComp * b;
        this.CQstack(total, total_dust, cq_add);
    }

    /**
     * For a given elevation (z) the puff can be prepared for fast concentration
     * computations, to be used with stackConcentrations_fast()
     * @param z observation height in meters.
     * @param ops options
     */
    public void initForConcentrationCalcs(float z, FusionOptions ops) {
        float[] r_c = RC.getRC(this.dist_total_m, windState.initialStabiClass());

        float D = windState.maxABLH();
        float H = HYX[0];
        float pre = r_c[TabledGaussians.PUFF_RAW];
        float b = getPlume_b(r_c, z, H);
        ybl_factor = 1f / (-4 * r_c[TabledGaussians.IND_ry]);
        float puffBase = pre * cons_distLimiter();//this is a common feature for all puff solutions

//FOUR CASES TO CHECK=====================
        if (z > D && H < D) {//observation above D while the source is below (+-)
            total_il = puffBase * b * PlumeMath.BLH_PENETR;
        } else if (z > D && H > D) {//both the observation and source are above D (++)
            H -= D;
            z -= D;
            total_il = puffBase * b;

        } else if (z < D && H > D) {//source above, observation below (-+)
            total_il = puffBase * b * PlumeMath.BLH_PENETR;
        } else {  //z and H are both below D - the usual case (--)
            float ratio = this.dist_total_m / D;

            if (ratio > XD_RATIO_MAX) {
                float b_il = getInversion_b(3, z, H, D, r_c, true);
                total_il = puffBase * b_il;
            } else if (ratio > XD_RATIO_MIN) {//between 3 and 2
                float b_il = getInversion_b(3, z, H, D, r_c, true);
                float w2 = ratio - XD_RATIO_MIN;
                total_il = (w2 * b_il + (1f - w2) * b) * puffBase;
            } else {//D can be ignored
                total_il = puffBase * b;
            }
        }

        float wsetComp = PlumeMath.getSettlingComp(r_c, z, H, ops.WSET);
        total_d = puffBase * wsetComp * b;

    }

    
    /**
     * Stack the concentrations caused by this puff (into either Q or cq_add)
     * based on the given location (z,y,x) and the location and state of this
     * puff.
     * 
     * This faster than the stackConcentrations method, since this uses
     * precomputed blocks set by 'initForConcentrationCalcs'.
     * 
     * @param y the y-coordinate (in the y,x coordinate system in meters)
     * @param x the x-coordinate (in the y,x coordinate system in meters)
     * @param ops options
     */
    public void stackConcentrations_fast(QCd qcd,
            float y, float x, FusionOptions ops) {
        if (this.isInactive()) {//return 
            return;
        }

        //1: transform coordinates
        float y_diff = y - this.HYX[1];// get dy, from the puff source's point of view
        float x_diff = x - this.HYX[2];// get dx
        float d2 = y_diff*y_diff + x_diff*x_diff;
        //wdir rotation

        float ybl = d2 * this.ybl_factor;
        ybl = FM.fexp(ybl);

        float total = ybl * this.total_il;
        float total_dust = ybl * this.total_d;

        if (total < 0) {
            total = 0;
        }
        if (total_dust < 0) {
            total_dust = 0;
        }

        this.CQstack(total, total_dust, qcd,ops);
    }

    public void stackConcentrations_latlon(float[][] cq,  float z,
            float lat, float lon, FusionCore ens, Boundaries b) {
        //1: transform coordinates
        float[] yx = Transforms.latlon_toMeters(lat, lon, b);
        stackConcentrations(cq, z, yx[0], yx[1], ens.ops);

    }

    public static float[] copyArr(float[] ar, float scaler) {
        float[] narr = new float[ar.length];
        for (int i = 0; i < ar.length; i++) {
            narr[i] = ar[i] * scaler;
        }
        return narr;
    }

    /**
     * Used in combination with stackConcentrations()
     *
     * @param total Gaussian weight (gaseous) that is to be combined with actual
     * emission amounts.
     * @param total_dust Gaussian weight (coarse particle matter) that is to be
     * combined with actual emission amounts.
     * @param qcd
     * @param ops
     */
    protected void CQstack(float total, float total_dust, QCd qcd, FusionOptions ops) {
        GlobOptions g = GlobOptions.get();
        for (int q : windState.met.VARA.Q) {
            float cons;
            if (q == windState.met.VARA.VAR_COARSE_PM) {
                cons = this.Q[q] * total_dust;
            } else {
                cons = this.Q[q] * total;
            }
            if (this.fractionalCategories_cq == null) {
                int c = this.sourcType;
                qcd.add(q,c,cons,ops);

            } else {//a bit more complex sum operation. The puff has multiple origin categories (mapPuff)

                for (int c : g.CATS.C) {
                    float frac = (float) this.fractionalCategories_cq[c][q] / 100f;
                    qcd.add(q,c,cons*frac,ops);
                }

            }
        }
    }
    
        protected void CQstack(float total, float total_dust, float[][] CQ) {
        GlobOptions g = GlobOptions.get();
        for (int q : windState.met.VARA.Q) {
            float cons;
            if (q == windState.met.VARA.VAR_COARSE_PM) {
                cons = this.Q[q] * total_dust;
            } else {
                cons = this.Q[q] * total;
            }
            if (this.fractionalCategories_cq == null) {
                int c = this.sourcType;
                CQ[c][q]+=cons;

            } else {//a bit more complex sum operation. The puff has multiple origin categories (mapPuff)

                for (int c : g.CATS.C) {
                    float frac = (float) this.fractionalCategories_cq[c][q] / 100f;
                    CQ[c][q]+=cons*frac;
                }
            }
        }
    }

    public boolean isImmaterial() {
       return this.immaterial_meters_left>0;
    }

    public float distSquared_m(float y, float x) {
       y =(y-this.HYX[1]);
       x = (x-HYX[2]);

       return x*x + y*y;
    }


}
