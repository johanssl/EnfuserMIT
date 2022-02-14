/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.geoGrid.Boundaries;

/**
 *This class is used to represent a single modelling area, that in essence
 * is a single line in the upper part of an ERCF (regions_x.csv).
 * 
 * The information this setting class holds include: name, bounds,
 * modelling variables, temporal span (with two hour integers), resolution,
 * run triggers for runmode=server, inner area list and model run customization
 * options.
 * @author johanssl
 */
public class ModellingArea {
    
    public final String regionName;
    public final Boundaries b;
    public final String name;
    public final int res_m;
    public final int res_min;

    public final ArrayList<String> vars;
    public final int backW, forwH;
    
    /**
     * Holds UTC-hour run triggers for this modelling area.
     * Note: runTriggers.csv may add to this hash in case the modelling
     * area name is present in the file.
     */
    public HashMap<Integer,String> runTriggers=new HashMap<>();
    
    /**the inner areas are names for other ModellingAreas that physically
     * are fully contained within this ModellingArea.
     */
    public ArrayList<String> innerAreas = new ArrayList<>();
    
    final String mods;
    
    public final static int I_NAME =1;
    public final static int I_RES =2;
    public final static int I_BOUNDS_START =3;//latmin, latmax 4, lonmin 6, lonmax 6
    public final static int I_VARS =7;
    public final static int I_TSPAN =8;
    public final static int I_HTRIGGERS =9;
    public final static int I_INNERS =10;
    public final static int I_MODS =11;//custom string tags
    
    /**
     * Create a new ModellingArea instance using a split line from ERCF.
     * @param l the definition String[] for the area, as given in ERCF.
     * The length of this should be at least 11, and the order of properties
     * should follow the static indices defined in this class.
     * @param region Region name under which this area belongs to.
     */
    ModellingArea(String[] l, String region) {
        //bounds and name
        this.regionName = region;
        String bName = l[I_NAME];
        this.b = new Boundaries(l,I_BOUNDS_START);
        b.name = bName;
        this.name =bName;
        
        //resolution settings
        String resol = l[I_RES]; //e.g., 15,60 = > 15m and 60 min.
        res_m = Integer.valueOf(resol.split(",")[0]);
        res_min = Integer.valueOf(resol.split(",")[1]);
        
        //modelling varibles for area run
        this.vars = new ArrayList<>();
        String modvars = l[I_VARS];
        if (modvars!=null && modvars.length()>1) {
            String[] varsS =modvars.replace("-", "").split(",");
            for (String v:varsS) {
               vars.add(v); 
            }
        } 
                
        //tspan
        String[] tsp = l[I_TSPAN].split(",");
        if (tsp.length==3) {
             EnfuserLogger.log(Level.FINE, this.getClass(), "Modelling area parameters"
                    + " have been defined with 3 integers (a,b,c) for "
                    +this.regionName+","+ this.name +", Consider switching to "
                            + "using 'b,c' since the spinoff-hour (a) need not to be defined anymore.");
             
           backW = Math.abs(Integer.valueOf(tsp[1]));
           forwH = Math.abs(Integer.valueOf(tsp[2]));
             
        } else if (tsp.length ==2) {
            
            backW = Math.abs(Integer.valueOf(tsp[0]));
            forwH = Math.abs(Integer.valueOf(tsp[1]));
        } else {
            EnfuserLogger.log(Level.SEVERE, this.getClass(), "Modelling area parameters"
                    + " have been incorrectly set for modelling time span! "
                    +this.regionName+","+ this.name +", There should be two integers 'a,b'.");
            backW=2;
            forwH=2;
        }
        
        //triggers
        String trigs = l[I_HTRIGGERS];
        if (trigs.length()>0 && !trigs.contains("-")) {
            
            
        if (trigs.contains("EACH")) {//modulo style

            int trigMod= Integer.valueOf(trigs.replace("EACH", ""));
            for (int i = 0; i < 24; i += trigMod) {
                runTriggers.put(i, "default");
            }

        } else {//list of UTC hours
                          
            String[] temporalTriggers = trigs.split(",");
            for (String tt: temporalTriggers) {
                Integer hour = Integer.valueOf(tt);
                this.runTriggers.put(hour,"default");
            }  
        } 
        
        }//if triggers given 
        
        
        //inner areas
        String innerLoc = l[I_INNERS];
        if (innerLoc.length() > 1) {
            String[] ins = innerLoc.split(",");
            for (String in:ins) {
                this.innerAreas.add(in);
            }
        }

        
        mods = l[I_MODS];

    }

    public String regionName() {
       return this.regionName;
    }
    
    /**
     * Build a String showing the inner modelling domains associated to this
     * area. If there are none then this returns an empty String.
     * Note: the inner areas are names for other ModellingAreas that physically
     * are fully contained within this ModellingArea.
     * @return a String that has the inner areas listed, separated with ','
     */
    public String innerAreaString() {
      String s ="";
      int k =0;
      for (String in:this.innerAreas) {
          s+=in;
          if (k<this.innerAreas.size()-1)s +=",";
          k++;
      }
      return s;
    }

    /**
     * Represent the hourly UTC run triggers for this modelling area.
     * This is used for logging.
     * @return a String representation of the run triggers of this modelling area.
     */
    public String runTiggerString() {
        if (this.runTriggers.size()<1) return "";
        int k =0;
        String s =" (";
      for (Integer h:this.runTriggers.keySet()) {
          s+="h"+h;
          if (k<this.runTriggers.size()-1)s +=",";
          k++;
      }
        return s+")";
    }
    
}
