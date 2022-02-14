/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.source.Layer;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;

/**
 *This static class can be used to compute Model predictions for Observations
 based on RP profiles and also overriding hourly scaling factors held by
 the FusionCore.
 * @author johanssl
 */
public class PredictionPrep {
   
    
       public static void initEnv(Observation o,FusionCore ens) {
        o.incorporatedEnv = EnvProfiler.getFullGaussianEnv(
                ens, o, null);
    }

    /**
     * Computes the expected value at the observation location, time and height.
     * For this same location/time the meteorological conditions are set up.
     *
     * @param ens the core
     * @param typeInt This is also defined in the observation it self, however,
     * in fusion algorithm the type can be variable.
     * @return a model prediction value for the requested variable type
     */
    public static float getExp(Observation o,FusionCore ens, int typeInt) {
        if (o.incorporatedEnv == null) {
            initEnv(o,ens);
        } else if (o.incorporatedEnv.needsReEvaluation(ens)) {
             initEnv(o,ens);
        }

        return o.incorporatedEnv.getExpectance(typeInt, ens);
    }

    /**
     * Manually force the Exp computation to use OLDER Overrides from the past
     * as if this was based on forecasting.
     *
     * @param o
     * @param ens the core
     * @param typeInt variable type index, as defined in VariableAssociations
     * @return a model prediction that is a simulated case for a longer term
     * forecast (uses older data fusion Overrides in the assessment)
     */
    public static float getForecastTestExp(Observation o,FusionCore ens, int typeInt) {
        if (o.incorporatedEnv == null) {
            initEnv(o,ens);
        }else if (o.incorporatedEnv.needsReEvaluation(ens)) {
             initEnv(o,ens);
        }
       float[] ex = o.incorporatedEnv.ccat.getExpectances_forecastTest(typeInt, ens, o.incorporatedEnv);
       return FuserTools.sum(ex); 
       
    }

    /**
     * Get expected concentration values separately for each source category.
     *
     * @param o
     * @param ens the core
     * @param typeInt variable type index as defined in VariableAssociation.
     * This should not be one of the meteorological types, however
     * @return model prediction, represented as a combination of
     * category-specific components (e.g., traffic, background...) as defined in
     * Categories.
     */
    public static float[] getExps(Observation o,FusionCore ens, int typeInt) {
        if (o.incorporatedEnv == null) {
            initEnv(o,ens);
        }else if (o.incorporatedEnv.needsReEvaluation(ens)) {
             initEnv(o,ens);
        }

        return o.incorporatedEnv.getExpectances((byte) typeInt, ens);
    }   
    
    
    
      public static void resetEnvBank(StationSource s) {

        EnfuserLogger.log(Level.FINER,s.getClass(),"\t" + s.ID + "," + s.VARA.VAR_NAME(s.typeInt) 
                + ", clearing observation Envs.");
        for (Layer l : s.getSortedLayers()) {
            Observation o = l.getStationaryObs();
            o.incorporatedEnv = null;
        }

    }

    /**
     * For FusionCore.EvaluateOverrideConditions(), will save computational
     * time.
     *
     * @param s
     * @param dt time which is used to find the closest matching Observations,
     * for which the stored RP-object is to be cleared.
     * @param ens the core (although NOT NEEDED)
     */
    public static void resetSpecificEnvs(StationSource s, Dtime dt, DataCore ens) {
        if (!s.dom.isStationary()) {
            EnfuserLogger.log(Level.FINER,s.getClass(),"WARNING! reseting Env instances on "
                    + "NON-STATIONARY source!");
            return;
        }

        Observation o = s.getExactObservation(dt, 0);
        if (o != null) {
            o.incorporatedEnv = null;
        }
    }  
    
}
