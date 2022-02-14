/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import java.util.ArrayList;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;

/**
 * The purpose of this class is to create a meta.csv file to output directory,
 * describing the key parameters for the modelling tasks.
 * @author johanssl
 */
public class Meta {
    public final static String METAFILE ="meta.csv";
  
    public static void createMetaFile(DataCore ens) {
        String dir = ens.ops.operationalOutDir();
        FusionOptions ops = ens.ops;
        RunParameters p = ops.getRunParameters();
        
        
        ArrayList<String> arr = new ArrayList<>();
        //basic info
        arr.add("ERCF;resolution[m];"+Math.round(p.res_m()));
        arr.add("ERCF;timestep[min];"+Math.round(p.areaFusion_dt_mins));
        Boundaries b = p.bounds();
        arr.add("ERCF;bounds(latmin,latmax,lonmin,lonmax;"+b.latmin +";"+b.latmax+";"+b.lonmin+";"+b.lonmax);
        String vars =csv(p.nonMetTypes(),",");
        arr.add("ERCF;modellingVariables;"+vars);
        String start = p.start().getStringDate();
        String end = p.end().getStringDate();
        arr.add("TIMESPAN;"+start+";"+end);
        
        String cats =csv(ops.CATS.CATNAMES,",");
        arr.add("SOURCE_CATEGORIES;"+cats);
        
        //variable definitions
        VarAssociations v = ops.VARA;
        for (int i =0;i<ops.VARA.ALL_VAR_NAMES().length;i++) {
            String var = v.VAR_NAME(i);
            String unit = v.UNIT(i);
            String desc = v.LONG_DESC(i);
            String role = "PRIMARY";
            String ncunit = v.VARNAME_CONV(i);
            if (v.IS_METVAR(i)) role ="METEOROLOGICAL";
            if (v.isSecondary[i]) role ="SECONDARY";
            arr.add("VARDEF;"
                    +var+";"
                    +unit+";"
                    +desc+";"
                    +role +";"
                    +ncunit);
        }
        DataPack dp = ens.datapack;
        addDataPackPrintout(dp, ops, arr);        
        FileOps.printOutALtoFile2(dir, arr, METAFILE, false);
    }
    
    public static void append(String toMeta, FusionOptions ops) {
       ArrayList<String> arr =new ArrayList<>();
       arr.add(toMeta);
       String dir = ops.operationalOutDir();
       FileOps.printOutALtoFile2(dir, arr, METAFILE, true);
    }
   
    
    public static void addDataPackPrintout(DataPack dp, FusionOptions ops,
            ArrayList<String> arr) {
 
      VarAssociations VARA = ops.VARA;
      ArrayList<String> vars = ops.getRunParameters().loadVars(ops);
      
        for (String var:vars) {
            int typeInt = VARA.getTypeInt(var);
            VariableData vd = dp.variables[typeInt];
            
            if (vd == null || vd.size() == 0) continue;

                for (AbstractSource s : vd.sources()) {
                    if (s==null) continue;
                    try {
                    String stype;
                    if (s instanceof GridSource) {
                        stype ="GRIDDED";
                    } else {
                       stype ="STATION";
                       if (s.dbl!=null && s.dbl.varScaler(typeInt)>9) stype ="SENSOR";
                    }
                    String base ="DATA;"+var+";"+stype+";"+ s.ID+";"+s.dbl.alias_ID+";";
                    
                    String line =  base+s.oneLinePrinout(ops);
                    arr.add(line);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }//for sources
  
        }//for variables

    }
    
    public static String csv(String[] arr, String sep) {
        String s ="";
        int k =0;
        for (String ss:arr) {
            k++;
            s+=ss;
            if (k<arr.length)s+=sep;
            
        }
        return s;
    }
    
        public static String csv(ArrayList<String> arr, String sep) {
        String s ="";
        int k =0;
        for (String ss:arr) {
            k++;
            s+=ss;
            if (k<arr.size())s+=sep;
            
        }
        return s;
    }

 
}
