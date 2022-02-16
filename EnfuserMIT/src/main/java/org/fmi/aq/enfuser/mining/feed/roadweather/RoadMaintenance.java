/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feed.roadweather;

import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.mining.feed.roadweather.Maintenance;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.HEL;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.logging.Parser;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.mining.feeds.FeedConstructor;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.enfuser.ftools.FileOps;

/**
 *
 * @author johanssl
 */
public class RoadMaintenance extends Feed {
    public static final String RM_OBJECT ="RoadMaintenance";
    public final static String MAINTENANCE_URL = "https://dev.hel.fi/aura/v1/snowplow/";
    private final static String ARG_TAG = "ROADMAINT_ARGS";

    private final static String[] SPECS =  {"ROADCOND", "2", ARG_TAG ,"1"  ,".csv" , CREDENTALS_NONE};
     
    public RoadMaintenance(FusionOptions ops) {
        super(FeedConstructor.create(SPECS,HEL,ops),ops);
    }


    @Override
    public boolean writesInitially() {
        return true;
    }
    @Override
    public boolean globalInitially() {
        return false;
    }
    @Override
    public String customArgumentInitially(ArrayList<String> areaNames) {
        return CERT_BYPASS;
    }
    @Override
    public boolean readsInitially() {
        return true;
    }
    
     
    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        
       if (!this.readable) return;
        Maintenance o = new Maintenance(ops, DB, p, mp);
        dp.addCustom(RM_OBJECT, o);
    }

    @Override
    public ArrayList<String> store() {
        storeTick();
        this.lastStoreCount = -1;
        this.addedFiles.clear();

        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);

        try {
            String resp;
            this.SOUT.println("RC feed: getting maintenance actions data from " + MAINTENANCE_URL);
            resp = mineFromURL(MAINTENANCE_URL,null);
            this.SOUT.println(resp);

            int count = this.parseAndStore(resp, dt);
            this.lastStoreCount += count;
        } catch (Exception ex) {
            this.printStackTrace(ex);
        }

        return this.addedFiles;
    }


    private final static char qmark = '"';

    private int parseAndStore(String results, Dtime dt) {

        ArrayList<String> procLines = new ArrayList<>();

        String qmarkString = "" + qmark;
        results = results.replace(qmarkString, "");
        results = results.replace(" ", "");
        results = results.replace("[", "");
        //split all the plows to an array
        String[] plowInfo = results.split("location_history");
        for (int i = 1; i < plowInfo.length; i++) {

            String[] events = plowInfo[i].split("events");

            try {
                if (events[1].contains("su")) {
                    procLines.add(processLine("salting", plowInfo[i], dt));
                    //storeSpecificActionToFile(lastObservations,"salting", plowInfo[i], dt, locationToSave);
                }
                if (events[1].contains("hi")) {
                    procLines.add(processLine("spreading_sand", plowInfo[i], dt));
                    // storeSpecificActionToFile(lastObservations,"spreading_sand", plowInfo[i], dt, locationToSave);
                }
                if (events[1].contains("pe")) {
                    procLines.add(processLine("street_washing", plowInfo[i], dt));
                    //storeSpecificActionToFile(lastObservations,"street_washing", plowInfo[i], dt, locationToSave);
                }
                if (events[1].contains("ps")) {
                    procLines.add(processLine("dust_binding", plowInfo[i], dt));
                    // storeSpecificActionToFile(lastObservations,"dust_binding", plowInfo[i], dt, locationToSave);
                }
                if (events[1].contains("hn")) {
                    procLines.add(processLine("sand_removal", plowInfo[i], dt));
                    //storeSpecificActionToFile(lastObservations,"sand_removal", plowInfo[i], dt, locationToSave);
                }
                if (events[1].contains("hj")) {
                    procLines.add(processLine("brushing", plowInfo[i], dt));
                    //storeSpecificActionToFile(lastObservations,"brushing", plowInfo[i], dt, locationToSave);
                }

            } catch (Exception e) {
                this.printStackTrace(e);
            }

        }//for plowinfo
        return storeNonDuplicates(procLines, this.primaryMinerDir);
    }

    private int storeNonDuplicates(ArrayList<String> arr, String dir) {

        String filename = this.defaultHourlyFileName("_MAINT", ".csv");
        String fullPath = dir + filename;
        int nlines = 0;
        //check existance of file
        File f = new File(fullPath);
        if (f.exists()) {//this file already exists, read data and cross check for duplicates

            //read data first to hash and check for duplicates
            ArrayList<String> existing = FileOps.readStringArrayFromFile(fullPath);
            HashMap<String, Boolean> hash = new HashMap<>();
            for (String s : existing) {
                hash.put(s, Boolean.TRUE);
            }
            //cross check
            ArrayList<String> newArr = new ArrayList<>();//this array will be written
            for (String s : arr) {
                if (hash.get(s) == null) {
                    newArr.add(s);//this line did not exist yet
                }
            }
            FileOps.printOutALtoFile2(dir, newArr, filename, true);
            addedFiles.add(dir + filename);
            nlines += newArr.size();

        } else {//new file, simple write 
            arr.add(0, "id;action;timestamp;savedAtUTC;lon;lat");
            FileOps.printOutALtoFile2(dir, arr, filename, false);
            addedFiles.add(dir + filename);
            nlines += arr.size();
        }
        this.SOUT.println("Data saved to " + fullPath + ", new lines = " + nlines);
        return nlines;
    }

    private String processLine(String actionType, String plowInfo, Dtime dt) {
        String[] id = plowInfo.split("id:");
        String[] timestamp = plowInfo.split("timestamp:");
        String[] coords = plowInfo.split("coords:");

        //only values now at 0
        String[] idAtZero = id[1].split(",");
        String[] timestampAtZero = timestamp[1].split(",");
        String[] latAtZero = coords[1].split(",");
        String[] lonAtZero = latAtZero[1].split("]");

        String line = idAtZero[0] + ";" + actionType + ";" + timestampAtZero[0] + ";"
                + dt.getStringDate_noTS() + ";" + latAtZero[0] + ";" + lonAtZero[0] + ";";
        return line;
    }

}
