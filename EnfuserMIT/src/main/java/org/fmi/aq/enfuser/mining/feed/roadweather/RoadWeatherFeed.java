/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feed.roadweather;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.mining.feed.roadweather.RoadWeather;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.mining.feeds.FeedConstructor;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.FIN;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 *
 * @author johanssl, Annemari Kiviniemi
 */
public class RoadWeatherFeed extends Feed {
    
    public static final String RW_OBJECT ="roadWeather";
    
    public final static String RWM_URL = "https://tie.digitraffic.fi/api/v1/data/weather-data";
    public final static String RWFC_URL = "https://tie.digitraffic.fi/api/v1/data/road-conditions"; 
    private final static String ARG_TAG = "ROADCOND_ARGS";

    private final static String[] SPECS =  {"ROADCOND", "30", ARG_TAG ,"4"    ,".txt" , CREDENTALS_NONE};
    
        public static boolean isMe(String argTag) {
        if(argTag==null) return false;
        return ARG_TAG.equals(argTag);
    }
    
    public RoadWeatherFeed(FusionOptions ops) {
        super(FeedConstructor.create(SPECS,FIN,ops),ops);
    
        searchRad_m=null;
        genID ="RW";//base name for new information source
        genID_decimals=1000;//decimals used to round-up coordinates in name id
        //this feed will not be able to extract data from the url-links without
        //some additional request properties.
        if (this.reqestProperties_http==null || this.reqestProperties_http.isEmpty()) {
        this.reqestProperties_http = new ArrayList<>();
        this.reqestProperties_http.add(new String[]{"Accept-Encoding", "gzip"});
        
        }
    }
    
    @Override
    public boolean readsInitially() {
        return true;
    }
    
    @Override
    public boolean globalInitially() {
        return false;
    }
    
    @Override
    public boolean writesInitially() {
        return true;
    }
    
    @Override
    public String customArgumentInitially(ArrayList<String> areaNames) {
        return HTTP_REQUEST_PROPS + "Accept-Encoding,gzip>, CERT_BYPASS";
    }

     
    @Override
    public void read(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
       if (!this.readable) return;
        RoadWeather o = new RoadWeather(ops, DB,p, dp);
        dp.addCustom(RW_OBJECT, o);   
    }

    @Override
    public ArrayList<String> store() {
        storeTick();
        this.lastStoreCount = -1;
        this.addedFiles.clear();
        boolean errors=false;
        //forecasts
        try {

            this.SOUT.println("RC feed: getting forecast data from " + RWFC_URL);
            String resp = mineFromURL(RWFC_URL,null);
            ArrayList<String> arr = new ArrayList<>();
            arr.add(resp);
            String fileName = this.defaultHourlyFileName("_FORECAST", ".txt");
            FileOps.printOutALtoFile2(this.primaryMinerDir, arr, fileName, false);//rewrite forecast part (one per hour is sufficient)
            addedFiles.add(this.primaryMinerDir + fileName);
            
            //forecasts become unnecessary after a couple of days and they take space
           String fileNameOld = this.defaultHourlyFileName(-48,"_FORECAST", ".txt");
           File old48h = new File(this.primaryMinerDir + fileNameOld);
           if (old48h.exists()) {
               this.println("Deleting older forecast file: "+ old48h.getAbsolutePath());
               old48h.delete();
           }
            
            this.lastStoreCount += arr.size();

        } catch (Exception ex) {
            this.printStackTrace(ex);
            errors=true;
        }

        try {

            this.SOUT.println("RC feed: getting measurement data from " + RWM_URL);
            String resp = mineFromURL(RWM_URL,null);
            ArrayList<String> arr = parseRoadMeasurements(resp);
            this.SOUT.println("Parsed " + arr.size() + " lines from measurement line.");
            String fileName = this.defaultHourlyFileName("_OBS", ".csv");
            FileOps.printOutALtoFile2(this.primaryMinerDir, arr, fileName, false);//appends
            addedFiles.add(this.primaryMinerDir + fileName);
            this.lastStoreCount += arr.size();

        } catch (Exception ex) {
            this.printStackTrace(ex);
            errors = true;
        }
        if (errors) lastStoreCount =-1;//flag as not successful since errors encountered.
        return this.addedFiles;
    }

    public final static String[] RMES_SPECS = {
        "roadStationId",//0
        "measuredTime",//1
        "KOSTEUDEN_MAARA_1",//2
        "TIENPINNAN_TILA3",//3
        "KITKA3",//4
        "NAKYVYYS_METRIA",//5
        "AURINKOUP",//6
        "SADE_INTENSITEETTI",//7
        "SADE",//8
        "MAKSIMITUULI",//9
        "TUULENSUUNTA",//10
        "SADE_TILA",//11
        "ILMA",//12
        "LUMEN_MAARA3",//13
        "SATEEN_OLOMUOTO_PWDXX",//14

        "VEDEN_MAARA3",//15
        "TIE_1",//16
        "SADESUMMA",//17
        "ILMAN_KOSTEUS",//18
        "KELI_1",//19
        "KESKITUULI",//20
        "JAAN_MAARA3",//21
        "LUMEN_SYVYYS",//22
        "KASTEPISTE",//23
        "KASTEPISTE_ERO_TIE",//24
    };

    public final static int I_ID = 0;
    public final static int I_TIME = 1;
    public final static int I_MOIST = 2;//KOSTEUDEN_MAARA
    public final static int I_SURFC = 3;
    public final static int I_FRIC = 4;
    public final static int I_VIS = 5;
    public final static int I_SUN = 6;
    public final static int I_RAININ = 7;
    public final static int I_RAIN = 8;
    public final static int I_MAXWIND = 9;
    public final static int I_WD = 10;//tuulensuunta
    public final static int I_RAINSTATE = 11;
    public final static int I_TEMP = 12;
    public final static int I_SNOW = 13;
    public final static int I_RAINC = 14;

    public final static int I_WATER = 15;
    public final static int I_SURFTEMP = 16;
    public final static int I_RAINSUM = 17;
    public final static int I_HUMID = 18;
    public final static int I_WEATH = 19;
    public final static int I_AVEWIND = 20;//keskituuli
    public final static int I_ICE = 21;
    public final static int I_SNOWDEPTH = 22;
    public final static int I_DEWP = 23;
    public final static int I_DEWP_DIFF = 24;

    private static int getMeasurementIndex(String name) {

        for (int j = 2; j < RMES_SPECS.length; j++) {
            if (RMES_SPECS[j].equals(name)) {
                return j;
            }
        }
        return -1;
    }

    private ArrayList<String> parseRoadMeasurements(String resp) {
        ArrayList<String> arr = new ArrayList<>();
        HashMap<String, String[]> hash = new HashMap<>();

        String[] units = new String[RMES_SPECS.length];

        resp = resp.replace(" ", "");
        resp = resp.replace("\"", "");
        String[] split = resp.split("roadStationId:");

        for (String s : split) {
            SOUT.println("================\n");
            try {
                String[] sec = s.split(",");
                String stationID = sec[0];//always the first element
                int idval = 0;

                try {
                    idval = Integer.valueOf(stationID);
                } catch (NumberFormatException e) {
                    continue;
                }

                SOUT.println("ID numeric = " + idval);
                String time = getBetween(s, "measuredTime:", "Z");
                String fieldname = getBetween(s, "name:", ",");
                String fieldvalue = getBetween(s, "sensorValue:", ",");

                String unit = getBetween(s, "sensorUnit:", ",");

                SOUT.println("time = " + time + ", name=" + fieldname + ", value=" + fieldvalue);
                if (fieldvalue.length() > 50) {
                    continue;
                }
                int ind = getMeasurementIndex(fieldname);
                //check new entry in hash
                String[] dats = hash.get(stationID);
                if (dats == null) {
                    dats = new String[RMES_SPECS.length];
                    for (int z = 0; z < dats.length; z++) {
                        dats[z] = "";
                    }
                    dats[I_ID] = stationID;
                    dats[I_TIME] = time + "+0:00";
                    hash.put(stationID, dats);
                }

                if (ind >= 0) {
                    dats[ind] = fieldvalue;
                    if (units[ind] == null) {
                        units[ind] = unit;
                    }
                    hash.put(stationID, dats);

                }//store

            } catch (Exception e) {
                this.printStackTrace(e);
            }

        }//for elements of stationID

        //add header and unit line
        String heads = "";
        String uns = "";
        for (int i = 0; i < RMES_SPECS.length; i++) {
            heads += RMES_SPECS[i] + ";";
            if (units[i] != null) {
                uns += units[i] + ";";
            } else {
                uns += "unit;";
            }
        }
        arr.add(heads);
        arr.add(uns);
        //add lines to array from hash
        for (String[] vals : hash.values()) {
            String line = "";
            for (String s : vals) {
                line += s + ";";
            }
            arr.add(line);
        }

        return arr;
    }

    private final static char qmark = '"';


}
