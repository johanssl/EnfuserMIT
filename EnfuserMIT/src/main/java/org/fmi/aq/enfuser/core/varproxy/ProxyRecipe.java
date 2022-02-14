/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.varproxy;

import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 * This class is for the formulation of a computation process for a specific
 * variable type based on other usable variable types.
 * It can be a customized class (e.g., for AQI) or a mathematical expression.
 * In either ways, it must be able to form predictions based on
 *  - AreaFusion content
 *  - a combination of meteorological and primary, secondary variable values
 * 
 * The ProxyRecipee can also take care of input data proxying. This means
 * that in case data is absent for the target variable type, then based
 * on the computation instructions these can be created virtually.
 * A concrete example of this is coarsePM for which measurements do not exist but
 * rather, they are virtually created based on PM10 (secondary) and PM2.5 (primary).
 * @author johanssl
 */
public abstract class ProxyRecipe {
    
    public int typeInt;
    protected final VarAssociations VARA;
    /**
     *If true, then this proxy participates in input proxying after data
     * has been loaded.
     */ 
    public boolean hasInputProcessing=false;

    public ProxyRecipe(VarAssociations VARA) {
        this.VARA = VARA;
    }
    /**
     * Estimate the proxy variable value for the given time, meteorology
     * and location. 
     * @param met the meteorology instance
     * @param af AreaFusion in which this operation is carried out
     * @param h h index for AreaFusion
     * @param w w index for AreaFusion
     * @param ens the core
     * @param lat latitude coordinate for h
     * @param lon longitude coordinate for w
     * @param temp
     * @return proxied prediction value
     */
    public abstract float proxy(Met met, AreaFusion af, int h, int w,
            FusionCore ens, double lat, double lon,HashMap<String,Double> temp);
    
    /**
     * Estimate the proxy variable value for the given time, meteorology
     * and location.
     * @param env RP instance to hold time, meteorology and local environment
     * characteristics
     * @param ens the core
     * @return proxied value
     */
    public abstract float proxy(RP env, FusionCore ens);
    
    public abstract ProxyRecipe hardClone();
    
    /**
     * Estimate the source component representation for this proxy variable type.
     * Often this cannot be done and in such cases the method should
     * return a float[] that holds the "proxy" as the first element.
     * @param env RP object for environmental information
     * @param ens the core
     * @return float[] source component (category) representation 
     */
    public abstract float[] proxyComponents(RP env, FusionCore ens);
   
    
    
    /**
     * This optional method takes care of preparations that are to be taken
     * before proxyGrid() is called. This can include some data sets to be
     * obtained/ processed.
     * @param af the area fusion instance
     * @param ens the core
     */
    public void prepareForComputations(AreaFusion af, FusionCore ens) {
        
    }
    
    /**
     * Some proxy variable may need some special proxy operations
     * on the loaded datapack. This process is to occur right after
     * the FusionCore creation phase.
     * @param ens The core
     */ 
    public void dataPackProcessing(FusionCore ens) {
        
    }

    /**
     * Add missing modelling variables to list in case the existing list
     * is missing some mandatory elements for this proxy variable.
     * @param nonMetTypes existing list
     * @return edited list
     */
    public abstract ArrayList<String> checkVariableListConsistency(
            ArrayList<String> nonMetTypes);
    
    
    /**
     *When the proxy variable recipee is loaded, then provide
     * some console feedback and details.
     */
    public abstract void printoutInfo();
    
}
