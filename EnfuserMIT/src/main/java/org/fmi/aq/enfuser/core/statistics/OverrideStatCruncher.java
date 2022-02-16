/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import java.io.File;
import org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment;
import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.core.FusionCore.DF_OVERRIDE;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.Y_NAME;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 *
 * @author johanssl
 */

public class OverrideStatCruncher {

    ArrayList<HourlyAdjustment> dats = new ArrayList<>();
    String type;
    int typeInt;
    AdjustmentBank oc;
    FusionOptions ops;
    HashMap<Integer,Met> hourlyMets;
    TZT tzt;
    
    public OverrideStatCruncher(String type, AdjustmentBank oc, FusionOptions ops,
            HashMap<Integer,Met> hash, TZT tzt, Dtime start, Dtime end) {
        
        this.type = type;
        this.typeInt = ops.VARA.getTypeInt(type);
        this.oc=oc;
        this.ops = ops;
        this.hourlyMets = hash;
        this.tzt = tzt;
        catalogOverrides(start, end);
    }
    
    public OverrideStatCruncher(String type, FusionCore ens, Dtime start, Dtime end) {
        this.ops = ens.ops;
        this.type = type;
        this.typeInt = ops.VARA.getTypeInt(type);
        this.oc=ens.getCurrentOverridesController();
        this.hourlyMets = new HashMap<>();
        this.tzt = ens.tzt;
        
        double midlat = ops.getRunParameters().bounds().getMidLat();
        double midlon = ops.getRunParameters().bounds().getMidLon();
        catalogOverrides(start, end);
        for (HourlyAdjustment ol:dats) {
            int hour = ol.dt.systemHours();
            Met met = ens.getStoreMet(ol.dt,midlat,midlon);
            hourlyMets.put(hour, met);
        }
    }
    
    private void catalogOverrides(Dtime start, Dtime end) {
        Dtime current = start.clone();
        current.addSeconds(-3600);
       dats.clear();
        while (current.systemHours() <= end.systemHours()) {
            current.addSeconds(3600);
            HourlyAdjustment over = oc.get(current, typeInt, ops);
            if (over != null && !over.filled) {
                this.dats.add(over);
            }
        }
    }

    public void crunchToFile() {
        ArrayList<EvalDat> eds = new ArrayList<>();
        for (HourlyAdjustment ol:dats) {
            int hour = ol.dt.systemHours();
            Met met = this.hourlyMets.get(hour);
            if (met!=null) {
                EvalDat ed = new EvalDat(ol,met,ops);
                eds.add(ed);
            }
        }
        ArrayList<String> ySkips = new ArrayList<>();
        ySkips.add(Y_NAME(StatsCruncher.PRED));
        ySkips.add(Y_NAME(StatsCruncher.OBS));
        ySkips.add(Y_NAME(StatsCruncher.MODBG));
        ySkips.add(Y_NAME(StatsCruncher.PRED_FORC));
        ySkips.add(Y_NAME(StatsCruncher.PRED_MAE));
        ySkips.add(Y_NAME(StatsCruncher.RAW_OBS));
        //counters and components remain
        
        ArrayList<String> ss = ConditionalAverager.process(ySkips, ops, tzt, true, eds);
        String name = this.type +  "_overrideCrunch.csv";
        FileOps.printOutALtoFile2(ops.statsCrunchDir(this.typeInt), ss, name, false);

    }
    
    
    
    public static void simplePrintoutToFile(FusionCore ens, Dtime start, Dtime end,
            int typeInt, String source) {
        
       String type = ens.ops.VAR_NAME(typeInt);
     ArrayList<String> arr = new ArrayList<>();
        EnfuserLogger.log(Level.INFO,OverrideStatCruncher.class,
                "Attempting overrideList prinout with "+ source +", " + type
        +", " +start.getStringDate() +" - "+ end.getStringDate());
     VariableData vd  =ens.datapack.getVarData(typeInt);
     AbstractSource s = vd.getSource(source);
     if ( s==null || !(s instanceof StationSource) ) {
        EnfuserLogger.log(Level.INFO,OverrideStatCruncher.class,
                "Source does not exist or is not a station source.");
        return;
    }

     StationSource st =(StationSource)s;
     Dtime current = start.clone();
     while (current.systemHours()<end.systemHours()) {
         
         String line ="";
         current.addSeconds(3600);
         
             boolean ava = ens.sufficientDataForTime(ens, current);
             if (!ava) continue;
         
         Observation o = st.getExactObservation(current);
         if (o==null) continue;
         boolean longstring = false;
         HourlyAdjustment ol = ens.getOverrideForObservation(o, DF_OVERRIDE);
            if (ol!=null) {
               String first = ol.getString(longstring, ens.ops);
                line+=first;
            }
            line+=";";
         HourlyAdjustment ol2 = ens.getOverrideForObservation(o, FusionCore.NEURAL_OVERRIDE);
            if (ol2!=null) {
                String sec = ol2.getString(longstring,ens.ops);
                line+=sec;
            }
        arr.add(line); 
     }    
        
     String dir = ens.ops.defaultOutDir();
     File f = new File(dir +"overridesPrintout_"+type +"_"+start.getStringDate_fileFriendly()+".csv");
     EnfuserLogger.log(Level.INFO,OverrideStatCruncher.class,"Printing to "+ f.getAbsolutePath() +", lines = "+arr.size());
     FileOps.printOutALtoFile2(f, arr, false);
    }

}
