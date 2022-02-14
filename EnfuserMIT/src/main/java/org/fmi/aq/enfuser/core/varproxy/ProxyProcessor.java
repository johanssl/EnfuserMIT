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
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.interfacing.InstanceLoader;

/**
 * This class holds all Proxy instructions for all variable types.
 * In case the instructions target input data (points and gridded) then these
 * are maintained in a separate list when the time comes to perform input data
 * proxy operations.
 * 
 * The main use-case for the class is in the Concentration Computations phase.
 * During this phase, for all modelling points in the area the lists of ProxyRecipees
 * are taken and based on the instructions secondary variable predictions are computed
 * one by one.
 * 
 * Before use the init-method must be invoked.
 * @author johanssl
 */
public class ProxyProcessor {
    
    private HashMap<Integer,ProxyRecipe> reps;
    private HashMap<Integer,ProxyRecipe> reps_primary_input;
    private final VarAssociations VARA;
    public ProxyProcessor(VarAssociations VARA) {
        this.VARA = VARA;
    }
    
    /**
     * Get a clean clone of of this instance that is safe to be utilized
     * in multi-threading operations.
     * @return clone
     */
    public ProxyProcessor cloneWithSecondaryVars() {
        ProxyProcessor pp = new ProxyProcessor(VARA);
        pp.reps = new HashMap<>();
        pp.reps_primary_input = new HashMap<>();
        for (int k:reps.keySet()) {
            if (VARA.isPrimary[k] || VARA.isMetVar[k]) continue;
            ProxyRecipe pr = reps.get(k);
            pp.reps.put(k, pr.hardClone());
        }
        
        return pp;
    }
    
    /**
     * For a specific cell in the AreaFusion, make secondary variable predictions
     * based on the instructions held by this instance.
     * @param af the AreaFusion that contains the already computed and available
     * primary predictions
     * @param h the h-index
     * @param w the w-index
     * @param ens the fusion core (for additional input if needed)
     * @param lat the latitude value of (h,w) in AreaFusion
     * @param lon the longitude value of (h,w) in AreaFusion
     * @param met Meteorological conditions at (h,w).
     * @param temp a temporary hash that may be needed for symbolic Expressions
     * which may be present in the ProxyRecipees.
     */
    public void processOne( AreaFusion af, int h, int w, FusionCore ens,
               double lat, double lon, Met met, HashMap<String,Double> temp) {
  
       if (af.oobs_hw(h,w)) return;
       for (ProxyRecipe pr:reps.values()) {
          float val= pr.proxy(met, af, h, w, ens, lat, lon,temp);  
          af.putValue(pr.typeInt,val,h,w);
       }   
     
   }
    
       /**
     * For a specific cell in the AreaFusion, make secondary variable predictions
     * based on the instructions held by this instance.
     * @param af the AreaFusion that contains the already computed and available
     * primary predictions
     * @param h the h-index
     * @param w the w-index
     * @param ens the fusion core (for additional input if needed)
     * @param temp a temporary hash that may be needed for symbolic Expressions
     * which may be present in the ProxyRecipees.
     */ 
   public void processOne( AreaFusion af, int h, int w,
           FusionCore ens, HashMap<String,Double> temp) {
        if (af.oobs_hw(h,w)) return;
        
        double lat = af.getLat(h);
        double lon = af.getLon(w);
        Met met = ens.getStoreMet (af.dt,lat,lon);
            for (ProxyRecipe pr:reps.values()) {
                float val =pr.proxy(met, af, h, w, ens, lat, lon,temp);
                af.putValue(pr.typeInt,val,h,w);
            }
       
   } 
    
    /**
     * Remove unnecessary proxy recipees for variables that are not
     * to be used. For input processing in paricular, these can be harmful.
     * @param nonMetTypes used types
     */
    private void deactiveMissing(ArrayList<String> nonMetTypes) {
        
        HashMap<Integer,ProxyRecipe> Nreps=new HashMap<>();
        HashMap<Integer,ProxyRecipe> Nreps_primary_input = new HashMap<>();
        for (int typeInt:reps.keySet()) {
            String var = VARA.VAR_NAME(typeInt);
            if (!nonMetTypes.contains(var)) {
                EnfuserLogger.log(Level.FINER,ProxyProcessor.class,
                        "REMOVING proxy recipee: "+ var);
            } else {
                Nreps.put(typeInt, reps.get(typeInt));
            }
        }
        
        for (int typeInt:reps_primary_input.keySet()) {
            String var = VARA.VAR_NAME(typeInt);
            if (!nonMetTypes.contains(var)) {
                EnfuserLogger.log(Level.FINER,ProxyProcessor.class,
                        "REMOVING proxy recipee: "+ var);
            } else {
                Nreps_primary_input.put(typeInt, reps_primary_input.get(typeInt));
            }
        }
        
        reps = Nreps;
        reps_primary_input = Nreps_primary_input;
    }  
    
   /**
    * Set up the content of this instance. This takes into account the list
    * of modelled variable types. In case a proxy recipee for a modelled variable
    * type requires another that is missing, then it is added to the list of
    * modelled variable types automatically. For example: if coarsePM has
    * been set as a modelling variable, PM2.5 and PM10 will be guaranteed to be
    * included in the list of variables.
    * 
    * For special cases of AQI and PM10 this 
    * method takes care that at least the basic proxy processes are available
    * for these in case no mathematical expressions have been given in
    * variableAssociations.
    * @param ops options
    * @param nonMetTypes modelled variable types (non-meteorological)
    * @return a modified (or original) list of modelling variables.
    */ 
   public ArrayList<String> init( FusionOptions ops, ArrayList<String> nonMetTypes) {

       reps = new HashMap<>();
       reps_primary_input = new HashMap<>();
       
        //RegionArguments regs = ops.getArguments();
        HashMap<String,String[]> proxies = ops.VARA.getPredictionInputRecipees();
            for (String var:proxies.keySet()) {
              
                String[] predInput = proxies.get(var);
                String func = predInput[0];
                String inp = predInput[1];
                int typeInt = ops.getTypeInt(var);
                ExpressionProxy sp = new ExpressionProxy(func,typeInt,inp,VARA);
                sp.printoutInfo();
                if (ops.VARA.isPrimary[typeInt]) {
                    EnfuserLogger.log(Level.FINER,ProxyProcessor.class,
                            "=> to primary input processing only.");
                    reps_primary_input.put(typeInt, sp);
                } else {
                    reps.put(typeInt, sp);
                }
  
            }//for ids

       //add defaults if not present
       if (!reps.containsKey(ops.VARA.VAR_AQI)) {
        EnfuserLogger.log(Level.FINER,ProxyProcessor.class,
                "ProxyProcessor: adding default AQI");
        AQIprox aq= new AQIprox(VARA);
        reps.put(aq.typeInt,aq);
        aq.printoutInfo();
       }
       
       //custom proxy variable instructions added as secondary modelling variables?
       InstanceLoader.addCustomInstructions(reps,VARA);
       this.deactiveMissing(nonMetTypes);
       return this.adjustVariableList(nonMetTypes);
   }
    
   
   /**
    * A check whether or not a proxy instruction is available for the given variable
    * type.
    * @param typeInt the variable type index.
    * @return true (if an instruction exists).
    */
   public boolean isProxyVariable(int typeInt) {
       return this.reps.containsKey(typeInt);
   }

    /**
     * Get an emission category -specific proxy result for the variable type.
     * The proxy instruction or the type may not support this and in that case
     * this returns null.
     * @param typeInt the variable type
     * @param env the RP object for the location
     * @param ens the core
     * @return float[] component representation of the secondary prediction.
     */
    public float[] getProxyComponents(int typeInt, RP env, FusionCore ens) {
        if (!isProxyVariable(typeInt))return ens.ops.CATS.zeroArray();
        float[] ex = this.reps.get(typeInt).proxyComponents(env, ens);
        if (ex==null) return ens.ops.CATS.zeroArray();
        return ex;
    }

    /**
     * Make sure that the modelling variable list is consistent and add variables
     * if needed.
     * @param nonMetTypes the initial list of modelling variables.
     * @return a modified (or original) list of modelling variables.
     */
    private ArrayList<String> adjustVariableList(ArrayList<String> nonMetTypes) {
      
       for (ProxyRecipe pr:reps.values()) {
           nonMetTypes = pr.checkVariableListConsistency(nonMetTypes);
       }
       for (ProxyRecipe pr:reps_primary_input.values()) {
           nonMetTypes = pr.checkVariableListConsistency(nonMetTypes);
       }
       
        return nonMetTypes;
    }

    /**
     * Launch input data proxy operations for all the types there are instructions
     * to do so.
     * @param ens the core 
     */
    public void dataPackPreparations(FusionCore ens) {
       
        for (ProxyRecipe pr:reps.values()) {
           if(pr.hasInputProcessing) pr.dataPackProcessing(ens);
        }
        
        for (ProxyRecipe pr:reps_primary_input.values()) {
           if(pr.hasInputProcessing) pr.dataPackProcessing(ens);
        }
    }

    
}
