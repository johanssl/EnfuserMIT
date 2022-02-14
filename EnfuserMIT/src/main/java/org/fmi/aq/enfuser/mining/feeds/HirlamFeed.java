/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.essentials.date.Dtime;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.mining.miner.RegionalMiner;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.EU;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *
 * @author Mjohanssl
 */
public class HirlamFeed extends Feed {
    
    private final static String ARG_TAG = "HIRLAM_ARGS";
    private final static String[] SPECS =  {"HIRLAM", "180", ARG_TAG    ,"23"   ,".nc" , CREDENTALS_NONE}; //2 
    private final static String URL_BODY = "https://opendata.fmi.fi/download?producer=hirlam&param=";
    private final static String FEATURE_URL = "https://opendata.fmi.fi/wfs?request="
            + "GetFeature&storedquery_id=fmi::forecast::hirlam::surface::finland::grid";
        
    public HirlamFeed(FusionOptions ops) {
        super(FeedConstructor.create(SPECS,EU,ops),ops);
        
        this.defaultVars = new String[]{"Temperature", "WindUMS", "WindVMS", "TotalCloudCover",
       "RadiationLW", "RadiationGlobal", "Precipitation1h"};   
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
        return "customize feed behaviour here.";
    }
    @Override
    public boolean readsInitially() {
        return true;
    }    
        
    private String[] getAvailableVars(String cont) {
        this.println("\nHIRLAM variable list:");
        HashMap<String, Boolean> vars = new HashMap<>();
        try {
            String[] temp = cont.split("swe:field name=\"");
            for (String test : temp) {
                String var = test.split("\" xlink:href")[0];
                if (var.contains("xml") || var.contains("<")) {
                    continue;
                }
                vars.put(var, Boolean.TRUE);
            }

            String[] list = new String[vars.size()];
            int j = 0;
            for (String s : vars.keySet()) {
                this.println("HIRLAM var found: " + s);
                list[j] = s;
                j++;
            }
            return list;

        } catch (Exception e) {
            printStackTrace(e);
            return null;
        }
    }

    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        this.readGridData(NETCDF,dp, DB, p, DEFAULT_EXPANSION,NO_FILL);
    }


    @Override
    public ArrayList<String> store() {
        storeTick();
        this.addedFiles.clear();
        println("Extracting HIRLAM-data...");
        
        String latestDate;
        Dtime start;
        Dtime end;
        int dayDiff;
        this.lastStoreCount=0;
        int fails =0;
        
        try {
            latestDate = getLatestHirlamDate();
            start = new Dtime(latestDate);
            while(start.hour!=0) {
                start.addSeconds(3600);
            }
            end = new Dtime(latestDate);
            end.addSeconds((RegionalMiner.FORECASTING_HOURS) * 3600);
            while(end.hour!=0) {
                end.addSeconds(3600);
            }
            dayDiff = (end.systemHours() - start.systemHours())/24;
            println("hirlam origin date = " + latestDate 
                    +", start="+start.getStringDate_YYYY_MM_DDTHH() 
                    +", end = "+end.getStringDate_YYYY_MM_DDTHH()
                    +", daySpan="+dayDiff
            );
            
        } catch (Exception ex) {
            printStackTrace(ex);
            lastStoreCount =-1;
            return addedFiles;
        }
        
        //url body
        String addr = URL_BODY + "";
        //add variables
        int j = 0;
        String[] vars = this.vars();
        for (String var : vars) {
            addr += var;
            if (j < vars().length - 1) {
                addr += ",";
            }
            j++;
        }
        //add other static parameters.
        addr+="&format=netcdf"
                +"&projection=epsg:4326"
                +"&bbox=" + b.lonmin + "," + b.latmin + "," + b.lonmax + "," + b.latmax
                + "&origintime=" + latestDate +"Z";
        
        Dtime curr_start = start.clone();
        Dtime curr_end = start.clone();
        curr_end.addSeconds(3600*24);
        //daily iteration
        for (int d =0;d<dayDiff;d++) {
            String startTime = curr_start.getStringDate(Dtime.STAMP_NOZONE);
            String endTime = curr_end.getStringDate(Dtime.STAMP_NOZONE);
            //add download span to url.
            String address = addr + "&starttime=" + startTime + "Z" + "&endtime=" + endTime + "Z";
            println("Extracting HIRLAM with\n" + address);
            String localFileName = primaryMinerDir + "HIRLAM_" + startTime.replace(":", "-") + "Z.nc";

           try {
               boolean refresh = true;//true => refresh (will download even if a file exists)
               boolean succ = downloadFromURL(localFileName, simpleFileDownload, address, refresh);
               if (succ) {
                   addedFiles.add(localFileName);
                   lastStoreCount++;
               }  else {
                   fails++;
               }
           } catch (Exception e) {
               printStackTrace(e);
               fails++;
           }

            //update start,end
            curr_start.addSeconds(24*3600);
            curr_end.addSeconds(24*3600);
        }//for days
        
        if (fails>0) {
            lastStoreCount=-1;//flag as unsuccesful.
        }
        return addedFiles;
        
    }


    private String getLatestHirlamDate() throws Exception {
        String date;
        String address = FEATURE_URL;
        /*
            "http://data.fmi.fi/fmi-apikey/"+
            apiKey +"/wfs?request=GetFeature&storedquery_id=fmi::forecast::hirlam::surface::finland::grid";
         */
        SOUT.println("========Hirlam dataset properties:  " + address);
        String currContent = mineFromURL(address,null);
        SOUT.println(currContent);
        SOUT.println("=================================================\n");
        date = getBeginDate(currContent);
        this.getAvailableVars(currContent);//for logging only.
        return date;
    }

    public static String getBeginDate(String content) {

        String start = "<gml:beginPosition>";
        String end = "</gml:beginPosition>";

        String[] temp = content.split(start);
        String[] temp2 = temp[1].split(end);
        return temp2[0].replace("Z", "");

    }

    @Override
    public void releaseStorageSpace(String storageDir) {

        String dir = this.primaryMinerDir;
        File f = new File(dir);
        File[] ff = f.listFiles();

        for (File file : ff) {

            String name = file.getName();
            if (!name.contains(".nc")) {
                continue;
            }
            String[] spl = name.split("_");
            String date = spl[1].replace(".nc", "");
            if (!date.contains("T00-")) {
                continue;//check only these
            }
            date = date.replace("-00-00Z", ":00:00");

            try {

                Dtime dt = new Dtime(date);
                Dtime dt_next = dt.clone();
                dt_next.addSeconds(3600 * 24);
                //check if there is a file for this data
                String testName = "HIRLAM_" + dt_next.getStringDate_YYYY_MM_DD() + "T00-00-00Z.nc";
                File test = new File(dir + testName);
                if (test.exists()) {//delete most files between these two timestamps. 06 and 18 are unnecessary!
                    File delFile = new File(dir + testName.replace("T00-00-00Z", "T06-00-00Z"));
                    if (delFile.exists()) {
                        delFile.delete();
                        EnfuserLogger.log(Level.FINER,this.getClass(),"Deleting " + delFile.getAbsolutePath());
                    }

                    delFile = new File(dir + testName.replace("T00-00-00Z", "T18-00-00Z"));
                    if (delFile.exists()) {
                        delFile.delete();
                        EnfuserLogger.log(Level.FINER,this.getClass(),"Deleting " + delFile.getAbsolutePath());
                    }
                }

            } catch (Exception e) {

            }

        }

    }

    public static String fileNameToDate(String file) {
        String timestamp = file.replace("HIRLAM_", "");

        timestamp = timestamp.replace("-00-00Z.nc", ":00:00");
        timestamp = timestamp.replace("_00_00Z.nc", ":00:00");
        return timestamp;
    }
}
