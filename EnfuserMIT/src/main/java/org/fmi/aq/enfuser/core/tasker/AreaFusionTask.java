/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.tasker;

import org.fmi.aq.enfuser.core.output.OutputManager;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.interfacing.GraphicsMod;

/**
 *
 * @author Lasse
 */
public class AreaFusionTask implements Runnable {
    FusionCore ens;
      
    public AreaFusionTask(FusionCore ens) {
        this.ens =ens;
    }
    
    
    public AreaFusionTask(boolean boundsName, boolean puffs, boolean df,
            FusionCore ens, Boundaries b, Float resm,
            Dtime start, Dtime end, ArrayList<String> nonMetTypes) {
        this.ens = ens;
        ens.ops.getRunParameters().customize(boundsName, b,resm, null, start,end,nonMetTypes);
        ens.ops.getRunParameters().puffs = puffs;
        ens.ops.getRunParameters().overs =df;
    }

    @Override
    public void run() {
            FusionOptions ops = ens.ops;
            ens.prepareTimeSpanForFusion();
            //do fusion
            ens.processAreaFusionSpan();
            OutputManager.outputAfterAreaRun( ens);

            //results displayer
            if (ops.guiMode) {
                new GraphicsMod().LaunchResultsDisplayer(ens,null);//launch viewer from addons.
            } 
    }

}
