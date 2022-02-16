/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.essentials.geoGrid.Boundaries;

/**
 *This class is used to gather all available ERCF files and objects.
 * All ERCF files under /data/Regions/ will be read to form a list of 
 * modelling regions. However, in case the ERCF has a tag 'DISABLED' in its
 * file name, then it will not be read as a special rule.
 * 
 * The list of ERCFs in the form of this class is mainted in GlobOptions.
 * This list can be updated and edited during runtime with GlobOptions's update
 * method.
 * 
 * @author johanssl
 */
public class Regions {

    public ArrayList<ERCF> ercfs;
    
    /**
     * Create a new instance by reading all ERCF's under the regional configuration
     * file directory. 
     */
    public Regions(String ROOT) {

        //File f = new File("");
        String dir = ROOT + "data" + FileOps.Z + "Regions" + FileOps.Z;
        EnfuserLogger.log(Level.INFO,Regions.class,"region options: " + dir);
        this.ercfs = new ArrayList<>();
        File f = new File(dir);
        if (f.exists()) {

            ArrayList<File> regArray = getAvailableERCFs(dir);
            //add regions, one per file
                 for (File regf : regArray) {
                     ERCF reg = new ERCF(regf);
                     ercfs.add(reg);
                 }
                
        } else {
                EnfuserLogger.log(Level.INFO,Regions.class,
                        "RegionOptions cannot be created! Required file structures do not exist yet!");   
        }
    }
    
    /**
     * List the ERCF files available to be used for Region definitions. 
     * @param dir directory for listing.
     * @return 
     */
    public static ArrayList<File> getAvailableERCFs(String dir) {
        File f = new File(dir);
        if (!f.exists()) return new ArrayList<>();
        File[] ff = f.listFiles();
        ArrayList<File> regArray = new ArrayList<>();
        for (File file : ff) {
            if (file.getName().startsWith("region_") && !file.getName().contains("DISABLED")) {
                //skip clause
                regArray.add(file);
            }
        }//for files
        return regArray;
    }

    ModellingArea getModellingArea(String loc) {
       for (ERCF r:this.ercfs) {
           for (ModellingArea m:r.areas) {
               if (m.name.equals(loc)) return m;
           }
       }
       return null;
    }

    public ERCF getRegion(String reg) {
       for (ERCF r:this.ercfs) {
           if (r.name.equals(reg)) return r;
       }
       return null;
    }

    Boundaries findAreaBoundsByName(String area) {
       ModellingArea m = getModellingArea(area);
       if (m!=null) {
           return m.b.clone();
       }
       return null;
    }

    public int size() {
       return this.ercfs.size();
    }

    public String[] mainNames() {
       String[] nams = new String[this.ercfs.size()];
       for (int i =0;i<this.ercfs.size();i++) {
           nams[i]= this.ercfs.get(i).name;
       }
       return nams;
    }

    
    

   

}
