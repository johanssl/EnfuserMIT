/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;

/**
 * This class is to manage a schedule for modelling tasks via a configuration
 * file called 'taskTriggers.csv'.
 * This uses the ModellingArea definitions, especially the area names to
 * associate custom triggers (UTC-hours) for specific modelling areas.
 * When the model then launches in server-runmode, then these triggers
 * can cause a modelling task to launch.
 *
 * Note: the scheduled modelling tasks can hold some additional customization
 * parameters for modelling tasks. For example, a scheduled modelling task
 * can be defined in a way that it skips visualization parts and concentrates
 * on building up allPollutants archives.
 * 
 * This class does not hold the task trigger information. Instead, it
 * implements the run triggers directly to ModellingArea instances.
 * 
 * @author johanssl
 */
public class LaunchTriggers {
   
    public final static String TRIGGER_FILE ="taskTriggers.csv";
    public final static int AREA_IND =0;
    public final static int HOUR_IND =1;
    public final static int CUSTOM_IND =2;
    
   /**
    * Read taskTrigger file and and hourly run triggers to each specified
    * modelling area.
    * @param regs all ERCF's and modelling areas are here.
    * @param root root directory
    */ 
   public static void addLaunchTriggers(Regions regs, String root) {
       
        String datadir = root + "data"+Z;   
        File f = new File(datadir + TRIGGER_FILE);
        if (f.exists()) {
            ArrayList<String> arr = FileOps.readStringArrayFromFile(f.getAbsolutePath());
            for (String s : arr) {
                String[] temp = s.split(";");
                if (temp.length<2) continue;//cannot work as intended.
                String key = temp[AREA_IND];
                if (key.equals("AREA_NAME")) continue;//header.
                
                        ModellingArea m = regs.getModellingArea(key);
                        if (m==null) {
                            EnfuserLogger.log(Level.INFO,LaunchTriggers.class,
                                    "Note: Launch trigger set for UNKNOWN area: "+ key+ " at " + TRIGGER_FILE);
                            continue;
                        }
                        
                        //run modifiers, e.g., fixed time, span archiving mode.
                        String mod = "default";
                        if (temp.length > CUSTOM_IND)mod = temp[CUSTOM_IND];
                        if (mod.length()==0) mod = "default";
                        
                        //trigger hour
                         String trig = temp[HOUR_IND];
                        if (trig.contains("EACH")) {

                            int trigMod= Integer.valueOf(trig.replace("EACH", ""));

                            for (int i = 0; i < 24; i += trigMod) {
                                m.runTriggers.put(i, mod);
                            }

                        } else {
                            m.runTriggers.put(Integer.valueOf(trig), mod);
                        }
            }//for lines
        }//if custom triggers exist   
   }
   
}
