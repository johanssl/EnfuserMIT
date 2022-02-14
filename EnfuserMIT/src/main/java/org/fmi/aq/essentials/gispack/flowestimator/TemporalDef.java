/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import java.util.Calendar;
import org.fmi.aq.essentials.date.Dtime;

/**
 *
 * @author johanssl
 */
public class TemporalDef {

    public static final String[] WKDS_NAMES = {"monfri", "sat", "sun"};
    public static final int WKDS = 3;
    public static final int DIURNALS = 24;

    public static int getWKDindex(Dtime dt) {
        int wkdi = 0;
        if (dt.dayOfWeek == Calendar.SATURDAY) {
            wkdi = 1;
        } else if (dt.dayOfWeek == Calendar.SUNDAY) {
            wkdi = 2;
        }
        return wkdi;
    }
    
        public static int getWKDhour(Dtime dt) {
        int wkdi = 0;
        if (dt.dayOfWeek == Calendar.SATURDAY) {
            wkdi = 1;
        } else if (dt.dayOfWeek == Calendar.SUNDAY) {
            wkdi = 2;
        }
        return wkdi*24+dt.hour;
    }
    
 public static int getWKDindex(String name) {
        
        String test = name.toLowerCase().replace("-", "");
        for (int i =0;i<WKDS_NAMES.length;i++) {
            if (test.equals(WKDS_NAMES[i]))return i;
        }
        return -1;
    }
 
 public static String wkdName(int ind) {
     return WKDS_NAMES[ind];
 }

    static int getExtendedIndex72(Dtime dt, Integer switchH) {
       int wkdi = getWKDindex(dt);
       int base = wkdi*24;
       if (switchH!=null) {
           return base +switchH;
       } else {
           return base + dt.hour;
       }
    }

    
    
}
