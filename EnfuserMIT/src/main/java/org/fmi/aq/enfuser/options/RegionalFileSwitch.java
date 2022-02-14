/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.io.File;

/**
 * There are several configuration files at data/ that can have a regional customized
 * copy. If such a customized copy exists then this loads the regional copy
 * instead of the default configuration file at data/.
 * @author johanssl
 */
public class RegionalFileSwitch {
   public final static String SPECIAL_D = "specialDays.csv";
   public final static String VARA_FILE = "variableAssociations.csv";
   public final static String AQI_FILE = "AQIdef.csv";
   public final static String DB_FILE = "db.csv";
   public final static String RESP_FILE = "RESP.csv";
   public final static String PERF_FILE = "performanceOptions_v2.csv";
   public final static String MFUNC_FILE = "metFunctions2.csv";
   
    public static File selectFile(String regName, String fname) {
        GlobOptions g = GlobOptions.get();
        String regionalDir = g.regionalDataDir(regName);
        String commonDir = g.dataDirCommon();
        
        String f = regionalDir + fname;
            File tester = new File(f);
            if (!tester.exists()) {//switch
                f = commonDir + fname;
            }
            return new File(f);
    }

    public static File selectFile(FusionOptions ops, String fname) {
       return selectFile(ops.getRegionName(), fname);
    }
    
}
