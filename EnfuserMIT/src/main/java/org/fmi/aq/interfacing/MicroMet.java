/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;

/**
 *
 * @author johanssl
 */
public class MicroMet {

    public static boolean enabledByName(String myName) {
        return false;
    }

    
    public static MicroMet loadModule(FusionOptions ops) {
       return new MicroMet(ops,false);
    }
    
    public MicroMet(FusionOptions ops, boolean load) {
    }

    public boolean discardStep(PuffCarrier p, PuffPlatform pf, Dtime dt) {
      return false;
    }

    public boolean enabled() {
        return false;
    }

    public PuffCarrier createPuffInstance(FusionCore ens, Dtime last, int DZ_EXP, float[] hyx,
            float[] Qmini, float[] latlon, String myName, int cat, float res_m, int distRampUp) {
       return null;
    }
    
}
