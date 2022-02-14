/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import static org.fmi.aq.enfuser.datapack.main.TZT.LOCAL;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.DataCore;

/**
 * This class is for searching and filtering Observations and evaluation points
 * for StatsCruncher (EvalDat).
 *
 * The filtering criteria include: month, day-of-week (DoW) hour of day (local)
 * and a collection of meteorological filters. For meteorological filters, any
 * number can be given, with a) a lower limit value, b) upper limit value or c)
 * both.
 *
 * @author johanssl
 */
public class ObsSearch {

    public int[] monthReq = null;
    public int[] dowReq = null;//day of week
    public int[] hourReq = null;

    public final static String MONTHS_KEY = "M<";
    public final static String HOUR_KEY = "H<";
    public final static String DOW_KEY = "DOW<";
    public final static String MET_KEY = "MET<";

    private HashMap<Integer, Float[]> met_minMax = null;

    public String customHeader;


    public ObsSearch() {
    }


    /**
     * Add a meteorological filter
     *
     * @param typeInt variable type index (of "MET")
     * @param min minimum value for acceptance (if null, will be ignored)
     * @param max maximum value for acceptance (if null, will be ignored)
     */
    public void addMetFilter(int typeInt, Float min, Float max) {
        if (this.met_minMax == null) {
            this.met_minMax = new HashMap<>();
        }
        this.met_minMax.put(typeInt, new Float[]{min, max});
    }

    /**
     * check if the local time satisfies the filter for local hour.
     *
     * @param local the time
     * @return boolean accepted (true), false for filtered out.
     */
    public boolean hourOK(Dtime local) {

        if (this.hourReq == null) {
            return true; //not defined: accept all 
        }
        //check acceptance
        boolean h_ok = false;
        for (int hh : hourReq) {
            if (local.hour == hh) {
                h_ok = true;
            }
        }

        return h_ok;
    }

    /**
     * check if the local time satisfies the filter for month.
     *
     * @param local the time
     * @return boolean accepted (true), false for filtered out.
     */
    public boolean monthOK(Dtime local) {

        if (this.monthReq == null) {
            return true; //not defined: accept all 
        }
        //check acceptance
        boolean m_ok = false;
        for (int d : monthReq) {
            if (d == local.month_011) {
                m_ok = true;
            }
        }

        return m_ok;
    }

    /**
     * check if the local time satisfies the filter for day-of-week.
     *
     * @param local the time
     * @return boolean accepted (true), false for filtered out.
     */
    public boolean dowOK(Dtime local) {

        if (this.dowReq == null) {
            return true; //not defined: accept all 
        }
        //check acceptance
        boolean d_ok = false;
        for (int d : dowReq) {
            if (d == local.dayOfWeek) {
                d_ok = true;
            }
        }

        return d_ok;
    }

    /**
     * Check whether or not the given Observation is to be accepted with subject
     * to the search criteria.
     *
     * @param ob the observation
     * @param ens the core
     * @return boolean accepted (true), false for filtered out.
     */
    public boolean isAcceptable(Observation ob, DataCore ens) {

        //then check rules to omit
        Dtime[] dates = ens.tzt.getDates(ob.dt);
        Dtime local = dates[LOCAL];

        if (!hourOK(local)) {
            return false;
        }
        if (!monthOK(local)) {
            return false;
        }
        if (!dowOK(local)) {
            return false;
        }

        boolean metok = metsOK(ob.getStoreMet(ens));
        if (!metok) {
            return false;
        }

        return true;

    }

    /**
     * Check whether or not the given Meteorology instance can be accepted with
     * subject to the met. filter rules set for this ObsSearch instance.
     *
     * If there are no rules for Meteorology, then this method returns true. If
     * there are rules but the Met-instance is null, OR the carried
     * met.variables are null for the checked types, then this returns false.
     *
     * @param met Meteorology instance to be checked.
     * @return boolean accepted (true), false for filtered out.
     */
    public boolean metsOK(Met met) {

        if (this.met_minMax == null) {
            return true;//accept all      
        }
        if (met == null) {
            return false; // there are rules but the ob.Met is not there => cannot be accepted
        }
        for (int typeInt : this.met_minMax.keySet()) {

            Float testVal = met.get(typeInt);
            if (testVal == null) {
                return false;//does not exist but a filter for this type exists => do not accept.
            }
            Float[] minmax = this.met_minMax.get(typeInt);

            Float f_lower = minmax[0];
            if (f_lower != null && testVal < f_lower) {
                return false;
            }

            Float f_upper = minmax[1];//this should not be null
            if (f_upper != null && testVal > f_upper) {
                return false;
            }

        }

        return true;
    }

}
