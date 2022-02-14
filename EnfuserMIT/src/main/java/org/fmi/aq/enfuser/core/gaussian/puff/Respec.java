/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.date.Dtime;

/**
 * This class manages when a puff respecs its meteorological state and 
 * decay functions.
 * 
 * The principle is that this cumulates time and distance that has passed
 * since the last update. If the timers suggest that a refresh is needed,
 * then action is taken and the counters are reset. Such a reset occurs
 * when e.g., the puff has travelled far enough so that the current meteorological
 * state is no longer representative. For mass decay actions fewer updates
 * are being done.
 * 
 * 
 * 
 * @author johanssl
 */
public class Respec {
    
    float timer_s=0;
    float distance_m=0;
    float decayTimer_s =0;
    public Respec() {
        
    }
    /**
     * Update travel time and distance and possibly update the state for the
     * puff.
     * @param time time addition to the timers
     * @param dist_m distance addition to the distance counter
     * @param pf the platform (needed for updates)
     * @param p the puff 
     * @param dt current time (for meteorology fetch)
     */
    public void update(float time, float dist_m, PuffPlatform pf,
            PuffCarrier p, Dtime dt) {
        this.timer_s+=time;
        decayTimer_s+=time;
        this.distance_m+=dist_m;
        this.respecCheck(pf, p, dt);
    }

    /**
     * Check whether or not an update is needed and carry this update out.
     * @param pf the platform (needed for updates)
     * @param p the puff
     * @param dt current time (for meteorology fetch)
     */
    private void respecCheck(PuffPlatform pf, PuffCarrier p, Dtime dt) {
        if(pf==null) return;
        
        boolean update = false;//assume no update initially.
        if(pf.ens.hashMet.res_m<this.distance_m)update= true;
        if(pf.ens.hashMet.res_min*60<this.timer_s)update= true;
        if (pf.respec_s < this.timer_s) update= true;
        
        if (update) {//yes, carry on.
            respec(pf, p, dt);//updates wind state and meteorology
            this.timer_s =0;//reset timer
            this.distance_m=0;//reset distance counter
        }
        
        //chem transformations and deposition/decay
        if (!(p instanceof MarkerCarrier) && (this.decayTimer_s> pf.respec_s)) {
            chemDepoStep(pf,p, this.decayTimer_s);
            this.decayTimer_s =0;
        }
        
    }

     /**
     * Sets the current coordinates as original and resets the tripTimer. This
     * will allow variable meteorology to be used as a function of location and
     * time. Cool!
     *
     * @param pf the puffPlatform managing puff modelling
     * @param dt time current time
     * @param respecTick_s time since last respec. This can be used for chemical
     * transformations
     */
    private void respec(PuffPlatform pf, PuffCarrier p, Dtime dt) {

        float lat = p.currentLat(pf);
        float lon = p.currentLon(pf);
        p.updateWindState(dt, pf.ens, lat, lon);
        p.initTrigons();  
    }

    /**
     * Apply decay functions to the carried emission masses, as set in
     * variableAssociations. Main purpose is to roughly simulate
     * dry and wet depositions.
     * TODO: this could be extended to cover some basic chemical
     * interactions, but in general this is difficult to do with a Gaussian model
     * @param pf
     * @param respecTime_s
     */
    private void chemDepoStep(PuffPlatform pf, PuffCarrier p, float respecTime_s) {
        //do this process only once every respec time tick (e.g., 5 min intervals).
        float hours = respecTime_s / 3600f;

        //depo-decay step
        VarAssociations VARA = pf.ens.ops.VARA;
        for (int q : VARA.Q) {
            
            float redFact = VARA.getPuffReductionFactor(q, p.windState.met, hours);//Step reduction. Does nothing if this is 0.
            pf.stepDecay_q[q] += redFact * p.Q[q];
            p.Q[q] *= (1f - redFact);

        }

    }
    
}
