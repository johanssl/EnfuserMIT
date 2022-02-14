/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.netCdf;

import org.fmi.aq.essentials.geoGrid.GeoGrid;
import ucar.nc2.Attribute;
import ucar.nc2.Variable;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *This class automatically converts gridded values in case they are originally
 * as 'moles'. The converted unit is to be ugm-3.
 * @author johanssl
 */
public class MoleConversion {
    
    /**
     * Convert grid values from moles to ugm-3.This method does nothing
     * in case the unit String doesn't contain 'mole', or Attribute 'molar_mass'
     * cannot be found.
     * Also in case the unit has been specified to be kg/m3, g/m3 or mg/m3, 
     * this method can convert the values into ug/m3 as we need them to be.
     * 
     * @param nc the netCDFhandle from which the Geogrid g was read from
     * @param g the grid for value scaling
     * @param file
     */ 
    public static void convertToUgm3(NetcdfHandle nc, GeoGrid g, String file) {
        
        try {
            
           Variable var = nc.getLoadedVariable(); 
           if (var==null) return;
           String unit = var.getUnitsString();
           if (unit==null) return;//cannot convert
           if (unit.contains("ug/m3")) return;//no need for conversions
           
           if (unit.toLowerCase().contains("mole")) {
               EnfuserLogger.log(Level.FINER,MoleConversion.class,nc.loadedVar+": is in 'moles'.");
               Attribute atr = var.findAttributeIgnoreCase("molar_mass");
               if (atr!=null) {
                   double val;
                   String full = atr.getStringValue();
                    EnfuserLogger.log(Level.FINER,MoleConversion.class,"Molar mass found: "+ full);
                    val = Double.valueOf(full.split(" ")[0]);
                   
                  EnfuserLogger.log(Level.FINER,MoleConversion.class,"value="+val +", "+ atr.getFullName());
                  
                  float scaler = 1e9f;//assume kg/m3
                  if (full.contains("kg/")) {
                      scaler = 1e9f;
                  } else if(full.equals("g/mole")) {
                      scaler = 1e6f;
                  } else if(full.contains("mg/")) {
                      scaler = 1e3f;
                  } else if(full.contains("ug/")) {
                      scaler =1f;
                  }
                   scaler*=(float)val;
                 
                   float ave =0;
                   int n =0;
                   float max = Float.MAX_VALUE*-1;
                   float min = Float.MAX_VALUE;
                   for (int h =0;h<g.H;h++) {
                       for (int w=0;w<g.W;w++) {
                           g.values[h][w]*=scaler;
                           float f = g.values[h][w];
                           ave+=f;
                           n++;
                           
                           if (f>max)max=f;
                           if (f<min)min =f;
                           
                       }
                   }
                   
                      EnfuserLogger.log(Level.FINE,MoleConversion.class,"All values scaled with: "
                              +scaler +" to get ugm-3 for "+ var + ", "+ file);
                      EnfuserLogger.log(Level.FINE,MoleConversion.class,"Average: "+ ave/n +",min ="+min +",max="+max);
                  
                   
               }//if atr found   
           }//if mole
            
           else  {//check for other units that are not the target [ug/m3]
               float scaler = 1f;//assume kg/m3
               boolean scale = false;
                  if (unit.contains("kg/")) {
                      scaler = 1e9f;
                      scale = true;
                  } else if(unit.replace(" ", "").equals("g/m3")) {
                      scaler = 1e6f;
                      scale=true;
                  } else if(unit.contains("mg/")) {
                      scaler = 1e3f;
                      scale = true;
                  }
                  
               if (scale) {
                   for (int h =0;h<g.H;h++) {
                       for (int w=0;w<g.W;w++) {
                           g.values[h][w]*=scaler;
                       }
                   }
                      EnfuserLogger.log(Level.FINE,MoleConversion.class,"All values scaled with: "
                              +scaler +" to get ugm-3 for "+ var + ", "+ file +", unit="+unit);
               }//if scale   
           }//else (not mole)
           
           
        } catch (Exception e) {
           EnfuserLogger.log(e, Level.WARNING,MoleConversion.class,
                   "netCDF unit  conversion FAILED. Loaded variable=" + nc.loadedVar +", file= "+file);
        }
        
    }
    
}
