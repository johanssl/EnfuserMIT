/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author Lasse
 */
public class MarkerCarrier extends PuffCarrier {

//int res_m;
    public Dtime init;
    public final static boolean MET_UPDATES = false;

    public MarkerCarrier(FusionCore ens, Dtime dt, float[] hyx,
            float[] Q, float[] latlon, String ID) {

        super(ens, dt,DZ_EXP, hyx, Q, latlon, ID, CAT_BG, -1, 0);
        //this.res_m = (int)pf.bgCellRes_m;
        this.init = dt.clone();
        canMerge = false;
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
    @Override
    public int isSubstantial(PuffPlatform pf) {
        //todo, for now, let's have a timer limit

        float lat = this.currentLat(pf);
        float lon = this.currentLon(pf);

        if (this.timer_total_s > 7200 && !pf.b_expanded.intersectsOrContains(lat, lon)) {
            //some time has passed and the center of the puff is not within the boundaries => remove
            // EnfuserLogger.log(Level.FINER,this.class,"removing a puff from list (out of boundaries)");
            return DISCARD_OUT;

        } else {
            return OK;
        }
    }

    @Override
    public void stackConcentrations(float[][] cq,  float z,
            float y, float x, FusionOptions ops) {
    }
    
    @Override
     public void stackConcentrations_fast(QCd qcd,
            float y, float x, FusionOptions ops) {
         
     }

}
