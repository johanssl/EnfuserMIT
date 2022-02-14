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
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;

/**
 *This class is to represent an ERCF - Enfuser Run Control File.
 * These are the region_x.csv files under directory 'Regions'.
 * 
 * This object has basically three parts. First there is the main region
 * definition (name and area). Then there is a collection of ModellingAreas.
 * Finally, there is the collection of regional settings (arguments), which
 * for example define Feed parameters for the Region.
 * @author johanssl
 */
public class ERCF {
    
    final Boundaries mainBounds;
    final String name;
    final ERCFarguments args;
    public final ArrayList<ModellingArea> areas;
    final String absPath;
    final File ercf;
   
    /**
     * Create a new ERCF instance based on the given region_x.csv file.
     * @param reg the file that is used to set up ERCF.
     */
   public ERCF(File reg) {
       this.ercf = reg;
       this.absPath = reg.getAbsolutePath();
       String[] split = reg.getName().replace(".csv", "").split("_");
       name = split[1];

      ArrayList<String> arr = FileOps.readStringArrayFromFile(reg);
      String[] main=null;
      ArrayList<String> argLines = new ArrayList<>();
      ArrayList<String[]> areaLines=new ArrayList<>();
            for (String s : arr) {
                try {
                    String[] elems = s.split(";");
                    if (elems==null || elems.length==0) continue;
                    String def = elems[0];
                    if (def.contains("main")) {
                        main = elems;
                    } else if (def.contains("area")) {
                        areaLines.add(elems);
                    } else if (def.contains("argument")) {
                        argLines.add(s);
                    }
                } catch (Exception e) {
                    EnfuserLogger.log(e,Level.SEVERE,ERCF.class,
                            "RegionOptions: unreadable line:" + s + ", " + reg.getAbsolutePath());
                }
            }//for arr

            //                      0               1           2       3       4       5       6
            // setup main example: main;	Finland;	-;	59;	68;	16;	32;
            mainBounds = new Boundaries(main,3);//start parsind doubles from 3th
            //region options
            args = new ERCFarguments(argLines, name,reg);
            // iterate areas for this region
            areas = new ArrayList<>();
            for (String[] l:areaLines) {
                ModellingArea a = new ModellingArea(l,this.name);
                areas.add(a);
                
            }//for lines
            


    }

    /**
     * Get the bounds for all modelling areas under this region (ERCF)
     * @return a list of modelling area bounds
     */
    public Boundaries[] getAllModellingAreaBounds() {
       Boundaries[] bb = new Boundaries[this.areas.size()];
       for (int i =0;i<bb.length;i++) {
           bb[i] = areas.get(i).b.clone();
       }
       return bb;
    }

     /**
     * Get a list of modelling areas names this region (ERCF)
     * @return a list of modelling area names
     */
    public String[] getAllModellingAreaNames() {
       String[] bn = new String[this.areas.size()];
       for (int i =0;i<bn.length;i++) {
           bn[i] = areas.get(i).name;
       }
       return bn;
    }

    /**
     * Get the name of this region (ERCF).
     * @return name
     */
    public String getName() {
       return this.name;
    }

    public File ERCF_file() {
       return this.ercf;
    }
    
}
