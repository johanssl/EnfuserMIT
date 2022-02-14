/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.netCdf;

import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.enfuser.options.FusionOptions;
import ucar.nc2.Attribute;

/**
 *
 * @author johanssl
 */
public class NcInfo {

    public final String varname;
    public final String units;
    public final String long_desc;
    public final String temporalDef;
    public final String temporalStart;
    public final int timeUnitStep;

    private ArrayList<Attribute> customAttrs = null;

    public final static int SECS = 0;
    public final static int MINS = 1;
    public final static int HOURS = 2;
    public final static int DAYS = 3;

    public final static String[] TDEFS = {
        "seconds",
        "minutes",
        "hours",
        "days"
    };

    private final static int DAYS_SECS = 24 * 3600;
    private final static int H_SECS = 3600;

    public NcInfo(String varname, String units, String longd, Dtime dt) {
        this(varname, units, longd, HOURS, dt, 1);
    }

    public void addCustomAttributes(ArrayList<Attribute> atr) {
        this.customAttrs = atr;
    }

    public NcInfo(String varname, String units, String longd, int tdef, Dtime dt, int timeStep) {
        this.varname = varname + "";
        this.units = units + "";
        this.long_desc = longd + "";

        //minute check
        if (tdef == MINS && timeStep % 60 == 0) {
            tdef = HOURS;
            timeStep /= 60;
        }
        this.temporalDef = TDEFS[tdef] + " since ";
        this.temporalStart = dt.getStringDate_noTS().replace("T", " ") + ".000 UTC";
        this.timeUnitStep = timeStep;
    }

    public NcInfo(String varname, String units, String longd) {
        this.varname = varname + "";
        this.units = units + "";
        this.long_desc = longd + "";

        //irrelevant temporal data (this constructor is for data for which the time does not count
        this.temporalDef = TDEFS[HOURS] + " since ";
        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);
        this.temporalStart = dt.getStringDate_noTS().replace("T", " ") + ".000 UTC";
        this.timeUnitStep = 1;
    }

    public NcInfo(String varname, String units, String longd, Dtime dt1, Dtime dt2) {
        this.varname = varname + "";
        this.units = units + "";
        this.long_desc = longd + "";

        int tdef;
        int tu;
        this.temporalStart = dt1.getStringDate_noTS().replace("T", " ") + ".000 UTC";
        if (dt2 == null) {
            tdef = HOURS;
            tu = 1;
        } else {//dt2 exists
            int secs = (int) (dt2.systemSeconds() - dt1.systemSeconds());

            //days  
            if (secs >= DAYS_SECS && secs % DAYS_SECS == 0) {
                tdef = DAYS;
                tu = secs / DAYS_SECS;
            } else if (secs >= H_SECS && secs % H_SECS == 0) {
                tdef = HOURS;
                tu = secs / H_SECS;
            } else if (secs >= 60 && secs % 60 == 0) {
                tdef = MINS;
                tu = secs / 60;
            } else {
                tdef = SECS;
                tu = secs;
            }

        }

        this.temporalDef = TDEFS[tdef] + " since ";
        this.timeUnitStep = tu;

    }

    public static ArrayList<Attribute> getVariableAttributes_byte(Float ADD_OFFSET, Float VAL_SCALER) {
        ArrayList<Attribute> atr = new ArrayList<>();

        atr.add(new Attribute("add_offset", ADD_OFFSET));
        atr.add(new Attribute("scale_factor", VAL_SCALER));

        return atr;
    }

    public ArrayList<Attribute> getDefVariableAttributes() {
        ArrayList<Attribute> atr = new ArrayList<>();
        atr.add(new Attribute("units", this.units));
        atr.add(new Attribute("long_name", this.long_desc));

        if (this.customAttrs != null) {
            atr.addAll(this.customAttrs);
        }
        return atr;
    }

    public Attribute getTimeAttribute() {
        
        String s = temporalDef + temporalStart;
        if (differentTS) {
            s = s.replace(".000 UTC", "+0:00");
        }
        return new Attribute("units", s);
    }

    boolean differentTS =false;
    public void setDifferentTimeZoneInfo(FusionOptions ops) {
       differentTS = ops.getArguments().hasCustomBooleanSetting(
               ERCFarguments.CUSTOM_NC_DIFFSTAMP);
       
    }

}
