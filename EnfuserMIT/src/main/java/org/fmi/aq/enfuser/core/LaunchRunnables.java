/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

import org.fmi.aq.enfuser.core.EnfuserLaunch;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_FILEDIR;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_MINIMIZE_ON_CLOSE;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_NAME;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_OVERWRITE;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_REGIONDEF;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_RUNDATE;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_RUNMODE;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_VALUE;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.RUNM_AREA_TASK;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.RUNM_HLESS_MAPF;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.RUNM_HLESS_SETUP;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.date.Dtime;
import java.util.HashMap;

/**
 *
 * @author johanssl
 */
public class LaunchRunnables {

    public static void fullMapFuserRunnable(String areaName, String region, Boundaries b,
            boolean overwrite) {

        String runm = ARG_RUNMODE + "full_mapf";
        String area = ARG_NAME + areaName;
        String reg = ARG_REGIONDEF + region;
        String vals = ARG_VALUE + b.latmin + "," + b.latmax + "," + b.lonmin + "," + b.lonmax
                + ",5";
        if (overwrite) {
            vals += ",overwrite";
        }

        String[] args = {runm, area, reg, vals};
        Runnable runnable = new EnfuserLaunch.LaunchRunnable(args);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public static void areaTaskRunnable(String area, Dtime date, String hspan) {
        String runm = ARG_RUNMODE + RUNM_AREA_TASK;
        String name = ARG_NAME + area;
        String dateArg = "null";
        if (date != null) {
            dateArg = ARG_RUNDATE + date.getStringDate_noTS();
        }
        String hsArg = "null";
        if (hspan != null) {
            hsArg = EnfuserLaunch.ARG_HOURSPAN + hspan;
        }

        String[] args = {runm, name, dateArg, hsArg};
        Runnable runnable = new EnfuserLaunch.LaunchRunnable(args);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public static void headlessRegionSetupRunnable(boolean noRunnable, String osmDir,
            String regName, boolean overw) {

        if (noRunnable) {//for TestRegion (a runnable would requre some while(wait) loop to work).
            HashMap<String, String> hash = new HashMap<>();

            hash.put(ARG_FILEDIR, osmDir);
            if (regName != null) {
                hash.put(ARG_NAME, regName);
            }
            if (overw) {
                hash.put(ARG_OVERWRITE, "true");
            }
            EnfuserLaunch.headlessSetup(hash);
            return;
        }

        String runm = ARG_RUNMODE + RUNM_HLESS_SETUP;
        String fdir = ARG_FILEDIR + osmDir;
        String reg = null;
        if (regName != null) {
            reg = ARG_NAME + regName;//it's a bit counter-intuitive, but the region is given as "name".
        }
        String over = ARG_OVERWRITE + overw;
        String[] args = {runm, reg, fdir, over};

        Runnable runnable = new EnfuserLaunch.LaunchRunnable(args);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public static void launchSetupGUI() {

        String runm = ARG_RUNMODE + "setup";
        String min = ARG_MINIMIZE_ON_CLOSE + "true";

        String[] args = {runm, min};
        Runnable runnable = new EnfuserLaunch.LaunchRunnable(args);
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public static void headlessMapfRunnable(String dir) {

        String runm = ARG_RUNMODE + RUNM_HLESS_MAPF;
        String fdir = ARG_FILEDIR + dir;

        //String reg = null;
        //if (regName!=null)reg =arg(ARG_REGIONDEF)+regName;
        String[] args = {runm, fdir};
        Runnable runnable = new EnfuserLaunch.LaunchRunnable(args);
        Thread thread = new Thread(runnable);
        thread.start();

    }

}
