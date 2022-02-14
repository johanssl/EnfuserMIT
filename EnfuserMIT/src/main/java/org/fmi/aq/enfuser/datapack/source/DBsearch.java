/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import java.util.HashMap;
import org.fmi.aq.enfuser.datapack.main.Observation;

/**
 * This small utility class is for faster search for DBlines based on coordinates.
 * Idea: for an unique pair of (lat,lon) the search need to be done only once.
 * @author johanssl
 */
public class DBsearch {
    
  private final HashMap<String,DBline> cachedFind= new HashMap<>();
  private final HashMap<String,Boolean> cachedFind_checks= new HashMap<>();
  
  public DBsearch() {
      
  }
        public DBline findClosest_cached(DataBase DB, double rad_m, double lat, double lon) {
            
        //first time?
        String key = lat+"_"+lon;
        Boolean b = this.cachedFind_checks.get(key);
        if (b!=null) {//already processed
            return this.cachedFind.get(key);
        }
          
        //mark as checked
        cachedFind_checks.put(key,Boolean.TRUE);
        
        DBline dbl = null;
        double rad = Double.MAX_VALUE;
        
        for (String ida : DB.keySet()) {
            DBline test = DB.get(ida);
           
            if (test.hasCoordinates) {
                
                double dist = Observation.getDistance_m(test.lat, test.lon, lat, lon);
                if (dist < rad_m) {
                   if (dist<rad ) {
                       rad = dist;
                       dbl=test;
                   }
                }
            }
        }
        
        if (dbl!=null) this.cachedFind.put(key, dbl);
        return dbl;
    } 
    
}
