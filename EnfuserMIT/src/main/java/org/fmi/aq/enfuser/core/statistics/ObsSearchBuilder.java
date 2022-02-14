/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import org.fmi.aq.enfuser.ftools.ObsSearch;
import static org.fmi.aq.enfuser.ftools.ObsSearch.DOW_KEY;
import static org.fmi.aq.enfuser.ftools.ObsSearch.HOUR_KEY;
import static org.fmi.aq.enfuser.ftools.ObsSearch.MET_KEY;
import static org.fmi.aq.enfuser.ftools.ObsSearch.MONTHS_KEY;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import java.util.ArrayList;
import java.util.Calendar;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class ObsSearchBuilder {
  
     /**
     * Parse and return an ObsSearch instance from a line instruction String.
     *
     * @param line the line instruction. This can include: - month filters
     * "M(a,b,c,...)" Note: replace () with diamond operators. - DOW filters
     * "DOW(sat,sun,..)" Note: replace () with diamond operators. - local hour
     * filters "H(8,9,10,...)" Note: replace () with diamond operators. -
     * Meteorological filters, e.g. "MET(rain, 0.1, null& windDirection,
     * 30,60)", again with diamond operators.
     * @return parsed ObsSearch instance.
     */
    public static ObsSearch fromString(String line, FusionOptions ops) {
        EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"ObsSearch from String:");
        ObsSearch os = new ObsSearch();
        if (line == null) {
            return os;
        }

        //parse months
        if (line.contains(MONTHS_KEY)) {
            String[] mon = getBetween(line, MONTHS_KEY, ">").split(",");
            os.monthReq = new int[mon.length];
            for (int i = 0; i < mon.length; i++) {

                os.monthReq[i] = Integer.valueOf(mon[i]) - 1;
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,
                        "Adding month filter [1-12]: " + (os.monthReq[i] + 1));
            }
        }//if contains MONTHS

        //parse hour
        if (line.contains(HOUR_KEY)) {
            String[] h = getBetween(line, HOUR_KEY, ">").split(",");
            os.hourReq = new int[h.length];
            for (int i = 0; i < h.length; i++) {
                os.hourReq[i] = Integer.valueOf(h[i]);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,
                        "Adding local hour filter: " + os.hourReq[i]);
            }
        }//if contains HOUR

        //dow 
        if (line.contains(DOW_KEY)) {

            String dows = getBetween(line, DOW_KEY, ">").toLowerCase();
            ArrayList<Integer> dowi = new ArrayList<>();

            if (dows.contains("mon")) {
                dowi.add(Calendar.MONDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Monday.");
            }
            if (dows.contains("tue")) {
                dowi.add(Calendar.TUESDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Tuesday.");
            }
            if (dows.contains("wed")) {
                dowi.add(Calendar.WEDNESDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Wednesday.");
            }
            if (dows.contains("thu")) {
                dowi.add(Calendar.THURSDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Thursday.");
            }
            if (dows.contains("fri")) {
                dowi.add(Calendar.FRIDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Friday.");
            }
            if (dows.contains("sat")) {
                dowi.add(Calendar.SATURDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Saturday.");
            }
            if (dows.contains("sun")) {
                dowi.add(Calendar.SUNDAY);
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Sunday.");
            }

            if (dows.contains("work")) {
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Adding day-of-week filter: Monday to Friday.");
                dowi.add(Calendar.MONDAY);
                dowi.add(Calendar.TUESDAY);
                dowi.add(Calendar.WEDNESDAY);
                dowi.add(Calendar.THURSDAY);
                dowi.add(Calendar.FRIDAY);
            }

            os.dowReq = new int[dowi.size()];
            for (int i = 0; i < dowi.size(); i++) {
                os.dowReq[i] = dowi.get(i);
            }

        }//if contains DOW

        //met filters
        if (line.contains(MET_KEY)) {
            String mets = getBetween(line, MET_KEY, ">").replace(" ", "");
            String[] split = mets.split("&");
            for (String ele : split) {
                String[] var_min_max = ele.split(",");
                Float min = null;
                Float max = null;
                String var = var_min_max[0];
                int typeInt = ops.VARA.getTypeInt(var, true, "ObsSearch");

                try {
                    min = Double.valueOf(var_min_max[1]).floatValue();
                } catch (NumberFormatException e) {
                }

                try {
                    max = Double.valueOf(var_min_max[2]).floatValue();
                } catch (NumberFormatException e) {
                }
                EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,
                        "Adding met filter: " + var + "min= " + min + "max= " + max);
                os.addMetFilter(typeInt, min, max);

            }//for elements
        }
        EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"Done.");
        return os;
    }
    
    
     /**
     * A simple test method for parsing ObsSearch instances from text.
     */
    public static void test(FusionOptions ops) {
        ops.VARA.getTypeInt("NO2", false, null);// will trigger
        //settings files to be loaded at this stage ( and not in between the system.out that is to occur below)

        EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"TEST ONE:==========");
        String line = "M<1,2>, H<22,23>, DOW<SAT,sun>";
        ObsSearch os = fromString(line,ops);

        EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"TEST TWO:==========");
        line = "M<1,2>, H<22,23>, DOW<work>, MET<rain, 0.1, null& windDirection, 30,60>";
        os = fromString(line,ops);

        EnfuserLogger.log(Level.FINER,ObsSearchBuilder.class,"TEST THREE:==========");
        line = "";
        os = fromString(line,ops);

    }
    
}
