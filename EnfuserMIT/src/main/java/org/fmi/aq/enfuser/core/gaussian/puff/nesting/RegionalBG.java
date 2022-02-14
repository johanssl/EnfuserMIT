/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff.nesting;

import java.util.ArrayList;
import org.fmi.aq.enfuser.core.gaussian.puff.PPFcontainer;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.essentials.date.Dtime;

/**
 *This class takes care of 'nesting' gaussian puff modelling into regional
 * background. For a puff model this can be quite challenging and for most
 * implementations of this method, the issue of double-counting of emission
 * contribution plays a key role.
 * @author johanssl
 */
public abstract class RegionalBG {
   
    
        /**
     * Initialization step for the class for preparation.
     * @param pf the platform. This will be called when the puffPlatform
     * instance is created.
     */
    public abstract void init(PuffPlatform pf);
    
    /**
     * For the given modelling time step for puff model, compute and 
     * add the background layer to the given data container.
     * @param pf The PuffPlatform
     * @param dt current modelling time
     * @param consContainer the data container the data is to be added to.
     */
    public abstract void addRegionalBackground(PuffPlatform pf, Dtime dt,
            PPFcontainer consContainer);

    
    /**
     * This method is invoked during the evolution step. In the the class
     * requires to do dynamic processing steps then this is the time to do those.
     * @param current current modelling time
     * @param pf the  platform
     */
    public abstract void updateTimer(Dtime current, PuffPlatform pf);

    /**
     * Add PuffCarrier instances to the given array during injection phase,
     * if applicable (should not be necessary in most implementations). 
     * @param pf the plaform
     * @param last last time for injection step
     * @param arr array of puffs (also marker puffs) in which new ones can be added to.
     * @return a count of added puffs to array.
     */
    public abstract int addMarkersToArray(PuffPlatform pf, Dtime last,
            ArrayList<PuffCarrier> arr);


    
}
