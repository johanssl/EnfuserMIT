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
 * This class is a customized instruction on how to compute the secondary
 * variable type AQI based on the primary concentrations.
 * 
 * Note: this can also use secondary variable types (PM10) so AQI should
 * be indexed as the LAST secondary variable type.
 * @author johanssl
 */
public class AQIprox extends ProxyRecipe{

     public final static String ID ="AQI";

    public AQIprox(VarAssociations VARA) {
        super(VARA);
        this.typeInt = VARA.getTypeInt(ID, true, "AQIprox");
    }
    
    @Override
    public float proxy(Met met, AreaFusion af, int h, int w, FusionCore ens, 
            double lat, double lon,HashMap<String,Double> temp) {
        af.initGridsIfNull(ens.ops.VARA.VAR_AQI);
       
            Float[] aq_vars = new Float[ens.ops.VARLEN()];
            byte lu = ens.fastGridder.getAreaFusionLU(h,w);
            float val = evaluatePointAQI(af,h, w, ens, aq_vars, lu);
            return val;
    }

    @Override
    public float proxy(RP env, FusionCore ens) {
       return 0;
    }

    @Override
    public void printoutInfo() {
    }
        public static float evaluatePointAQI(AreaFusion af, int h, int w, 
                FusionCore ens, Float[] aq_vars, byte luType) {

        if (aq_vars == null) {
            aq_vars = new Float[ens.ops.VARLEN()];
        }

        for (int typeInt = 0; typeInt < ens.ops.VARLEN(); typeInt++) {
            if (af.hasConcentrationGrid(typeInt)
                    && typeInt != ens.ops.VARA.VAR_AQI) {
                aq_vars[typeInt] = af.getConsValue(typeInt, h, w);
            }
        }
        float fusionAQI = ens.aqi.getWeightedAQI(aq_vars, luType);
        return fusionAQI;
    }

    @Override
    public void dataPackProcessing(FusionCore ens) {

    }

    @Override
    public ArrayList<String> checkVariableListConsistency(
            ArrayList<String> nonMetTypes) {
        return nonMetTypes;
    }

    @Override
    public ProxyRecipe hardClone() {
        return new AQIprox(this.VARA);
    }

    @Override
    public float[] proxyComponents(RP env, FusionCore ens) {
        return null;
    }
}
