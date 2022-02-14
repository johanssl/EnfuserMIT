/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

import org.fmi.aq.enfuser.parametrization.LightTempoMet;

/**
 *
 * @author johanssl
 */
public class EmitterCellSparser {

    public static void editSparserBasedOnContent(String ID, float nonz_frac_01, LightTempoMet ltm) {

        EnfuserLogger.log(Level.INFO,EmitterCellSparser.class,
                "EmitterCellSparser: " + ID + ", amount of ACTIVE, non-zero emitter cells = "
                + 100f * nonz_frac_01 + "%");
        int orig = ltm.emissionCellSparser;

        if (nonz_frac_01 > 0.6) {
            ltm.emissionCellSparser = 5;
        } else if (nonz_frac_01 > 0.45) {
            ltm.emissionCellSparser = 5;

        } else if (nonz_frac_01 > 0.3) {
            ltm.emissionCellSparser = 4;

        } else if (nonz_frac_01 > 0.22) {
            ltm.emissionCellSparser = 3;

        } else if (nonz_frac_01 > 0.1) {
            ltm.emissionCellSparser = 2;
        } else {
            ltm.emissionCellSparser = 1;
        }
        int cube = ltm.emissionCellSparser * ltm.emissionCellSparser;
        EnfuserLogger.log(Level.INFO,EmitterCellSparser.class,
                "Emission cell sparser set to " + ltm.emissionCellSparser
                + " (every " + cube
                + "th cell will emit " + cube + "x emissions.)");

        if (orig > ltm.emissionCellSparser) {
            EnfuserLogger.log(Level.INFO,EmitterCellSparser.class,
                    "However, the original value for the sparser was set to "
                    + orig + ". It will be used instead."
            );
            ltm.emissionCellSparser = orig;
        }

        float distRamp = (1 - ltm.emissionCellSparser) * 20;//in meters, to avoid "spikyness" nearby the sparse, strong emitters.
        if (ltm.distanceRamp_m < distRamp) {
            EnfuserLogger.log(Level.INFO,EmitterCellSparser.class,
                    "\t Distance ramp-up also added: " + distRamp);
            ltm.distanceRamp_m = distRamp;
        }

    }

}
