/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import java.util.ArrayList;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment;
import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.ArchiveContent;
import org.fmi.aq.enfuser.meteorology.HashedMet;
import static org.fmi.aq.enfuser.ftools.FuserTools.arrayConvert;

/**
 * This class takes care of storing an allPollutants file (compressed archive)
 * when computations have been done for a modelling time step. This process
 * can be done in a parallel thread, or in a single thread (e.g., for last modelling time step)
 * 
 * Before archiving this also calls for any post processing steps that needs
 * to be done before storing the results to file.
 * 
 * After archiving this class can also printout the stored content, file names
 * and sizes.
 * @author johanssl
 */
public class CompressedArchiver implements Runnable{

    private final FusionCore ens;
    private final AreaFusion af;
    private final AreaInfo in;
    private final Dtime time;
    public CompressedArchiver(FusionCore ens, AreaFusion af, AreaInfo in, Dtime time) {
        this.ens = ens;
        this.af = af;
        this.in = in;
        this.time=time;
    }
    
    
    @Override
    public void run() {
        Met met = ens.getStoreMet(in.dt, in.midlat(), in.midlon());
        af.metAtCenter = met;
        long t = System.currentTimeMillis();
        //used overrides information
        AdjustmentBank oc = ens.getCurrentOverridesController();
        for (Byte byt : in.types) {
            HourlyAdjustment over = oc.get(time.systemHours(), time.min, byt, ens.ops);
            if (over != null) {
                af.replaceTypeSpecificInfo(byt, over.stringo);
            }
        }

        
       EnfuserLogger.log(Level.FINER,CompressedArchiver.class,
               "Storing metGrids to AreaFusion.");

       af.metGrids = ens.hashMet.toGeoGrids(in.bounds, time, ens,HashedMet.WSM_RAW);
       EnfuserLogger.log(Level.FINER,CompressedArchiver.class,"Done storing");
       af.addStationaryObservations(ens);

        //prost process completed af, for OPTIONAL GUI-launched tasks
        af.postProcess( ens, arrayConvert(in.types));
        //store permanent output data for areaFusions that had measurements (not forecasted data). Rewrites are unnecessary
        ArrayList<String> content = Arch.permaStoreOutput(af, ens, arrayConvert(in.types));
        long t2 = System.currentTimeMillis();
        long secs = (t2-t)/1000;
        checkContent(content,secs,time,ens.ops.areaID());
    }
    
    
    
 
    /**
     * For a given list of contents, check that the files are non-zero in size.
     * If some are, then log an severe warning.
     * @param content
     * @param secs
     * @param time
     * @param name 
     */
    public static void checkContent(ArrayList<String> content, long secs, Dtime time, String name) {
       
         EnfuserLogger.log(Level.INFO,CompressedArchiver.class,
                "CompressedArchiver Done for "+ time.getStringDate_noTS()
                        +", took: "+ secs+"s.\nContent:");
         
         //get a list of keys that we should find here as .nc files.
         String[] checks = ArchiveContent.getTypesThatMustExist_nc();
         int okCount =0;
         
         for (String s:content) {
             EnfuserLogger.log(Level.INFO,CompressedArchiver.class,"\t"+s); 
             boolean zeroKb = s.contains(" 0"+Arch.KB);
             
             for (String check:checks) {
                 if (s.contains(".nc") && s.contains(check) && !zeroKb) {
                     okCount++;
                     break;
                 }
             }//for checks    
         }
         
        if (okCount < checks.length) {
             EnfuserLogger.log(Level.SEVERE, CompressedArchiver.class,
               time.getStringDate_noTS() +"-archive for " + name +" does not seem"
                       + " to contain all key datasets as non-zero "
                         + "sized netCDF files. Target count = "+checks.length
                         +", found ="+okCount);
        }
    }
    
    
       public static void archive(FusionCore ens, AreaFusion af,
            AreaInfo in, Dtime time, boolean lastAddition) {
         
        if (ens.ops.perfOps.multiThreadArchiving() && !lastAddition) {
            AreaInfo inf = new AreaInfo(in.bounds, in.H, in.W, in.dt.clone(), in.types);
            Runnable runnable = new CompressedArchiver(ens,af,inf,time.clone());
            Thread th = new Thread(runnable);
            th.start();
            
        } else {
            new CompressedArchiver(ens,af,in,time.clone()).run();
        }
        
    }    
    
    
    
    
}
