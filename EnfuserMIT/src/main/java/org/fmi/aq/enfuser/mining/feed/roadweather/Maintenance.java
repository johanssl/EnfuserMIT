/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feed.roadweather;

import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.gispack.Masks.MapPack;


/**
 *
 * @author johanssl
 */
public class Maintenance {


    public final static int MAINT_SANDING = 0;
    public final static int MAINT_SALTING = 1;
    public final static int MAINT_DBIND = 2;
    public final static int MAINT_WASH = 3;
    public final static int MAINT_BRUSHING = 4;
    public final static int MAINT_DREM = 5;

    public final static String[] MAINT_NAMES = {
        "spreading_sand",
        "salting",
        "binding",
        "washing",
        "brushing",
        "sand_removal"
    };

    HashMap<Integer, ArrayList<float[]>> actions = new HashMap<>();//use sysH for key
    //Road piece -time -mapping of maintenance actions
    HashMap<Integer, HashMap<Long, RoadPiece>>[] rpActionHash = new HashMap[MAINT_NAMES.length];
    HashMap<Integer, float[]> actionhash_daily = new HashMap<>();

    int obsSize = 0;
    int actionSize = 0;
    int actionSize_roads = 0;
    private ArrayList<File> actionFiles = new ArrayList<>();
    private RoadMaintenance feed;
    
    public Maintenance(FusionOptions ops, DataBase DB, RunParameters p, MapPack mp) {

        feed = new RoadMaintenance(ops);
        Dtime start = p.start();
        Dtime end = p.end();
        
        for (int i = 0; i < MAINT_NAMES.length; i++) {
            rpActionHash[i] = new HashMap<>();
        }

            File f = new File(feed.primaryMinerDir);
            File[] ff = f.listFiles();
            if (ff != null) {
                for (File file : ff) {
                    String name = file.getName();
                    String[] namsplit = name.split("_");
                    String date = namsplit[1].replace("-00-00Z", ":00:00");

                    Dtime dt;
                    try {
                        dt = new Dtime(date);
                    } catch (NullPointerException e) {
                        EnfuserLogger.log(Level.WARNING,this.getClass(),
                        "RoadConditions: could not parse file time stamp: " + date 
                        + ", from " + file.getAbsolutePath());
                        continue;
                    }
                    //read if timewise necessary
                    if (start.systemHours() > dt.systemHours() || end.systemHours() < dt.systemHours()) {
                        continue;//not applicable
                    }
                  this.actionFiles.add(file);//to be read later on.
                }//for files
            }
     this.afterInit(mp);
    }
    
    
    public static String getMaintHeaderString(String comma) {
        String s = "";
        for (int i = 0; i < MAINT_NAMES.length; i++) {
            s += MAINT_NAMES[i] + " [roads/h];";
        }
        return s;
    }

    public static void addMaintHeaderString(ArrayList<String> arr) {

        for (int i = 0; i < MAINT_NAMES.length; i++) {
            arr.add(MAINT_NAMES[i] + " [roads/h]");
        }
    }


boolean mpDone = false;
    private void afterInit(MapPack mp) {
        if (mpDone|| mp==null) return;
        mpDone =true;
        
        if (feed.readable) {
            ArrayList<String> arr = null;
            for (File file : this.actionFiles) {
                arr = FileOps.readStringArrayFromFile(file.getAbsolutePath());
                for (String line : arr) {
                    this.putAction(line, mp);

                }
            }
            //evaluate action sizes
            for (int actI = 0; actI < this.rpActionHash.length; actI++) {
                HashMap<Integer, HashMap<Long, RoadPiece>> ob = this.rpActionHash[actI];
                int maintTypeLen = 0;
                for (Integer sysH : ob.keySet()) {
                    int sysSecs = sysH * 3600;
                    Dtime hourDt = Dtime.UTC_fromSystemSecs(sysSecs);
                    HashMap<Long, RoadPiece> hourHash = ob.get(sysH);
                    EnfuserLogger.log(Level.FINER,RoadWeather.class,"\t\t\t" + hourDt.getStringDate()
                            + ", " + MAINT_NAMES[actI] + ", roadPieces = " + hourHash.size());
                    maintTypeLen += hourHash.size();
                }
                
                EnfuserLogger.log(Level.FINER,RoadWeather.class,"\t\t action " + MAINT_NAMES[actI]
                        + " has " + maintTypeLen + "activity points.");
                this.actionSize += maintTypeLen;
            }
        }//if readable feed 
    }
    
    public void printoutCheck(MapPack mp) {
        try {
        afterInit(mp);
         EnfuserLogger.log(Level.INFO,RoadWeather.class,""
                 + "roadMaintenance has "+ this.actionhash_daily.size() +" 3-day entries,"
         +this.actionFiles.size()+ " files were read, " + this.actions.size() +" roads with data points.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public float[] getMaintActsCounts_hourly(Dtime dt, int h_backwards) {

        float[] dat = new float[MAINT_NAMES.length];
        for (int i = 0; i < dat.length; i++) {
            for (int h = 0; h < h_backwards; h++) {
                HashMap<Integer, HashMap<Long, RoadPiece>> ob = this.rpActionHash[i];
                HashMap<Long, RoadPiece> thash = ob.get(dt.systemHours() - h);
                if (thash != null) {
                    dat[i] += thash.size();
                }
            }
        }//for datType
        return dat;
    }


    private void putAction(String line, MapPack mp) {
        String[] temp = line.split(";");
        //"id;action;timestamp;savedAtUTC;lon;lat"
        double id = 0;
        try {
            id = Double.valueOf(temp[0]);
        } catch (NumberFormatException e) {
            return;
        }

        String action = temp[1];
        int act = -1;//MAINT_SANDING;
        for (int i = 0; i < MAINT_NAMES.length; i++) {
            if (action.contains(MAINT_NAMES[i])) {
                act = i;
                break;
            }
        }
        if (act == -1) {
            EnfuserLogger.log(Level.FINER,RoadWeather.class,
                    "Unknown maintenance action: " + action);
            return;
        }

        Dtime dt2 = new Dtime(temp[2]);
        double lat = Double.valueOf(temp[5]);
        double lon = Double.valueOf(temp[4]);

        //getRoadPiece
        OsmLayer ol = mp.getOsmLayer(lat, lon);
        if (ol != null) {
            RoadPiece rp = OSMget.getRoad_atRadius(ol, lat, lon, 50);
            if (rp != null) {
                //flag action to this road during this particular hour
                HashMap<Integer, HashMap<Long, RoadPiece>> acthash = rpActionHash[act];
                //take temporal
                HashMap<Long, RoadPiece> thash = acthash.get(dt2.systemHours());
                if (thash == null) {
                    thash = new HashMap<>();
                    acthash.put(dt2.systemHours(), thash);
                }

                thash.put(rp.ref_number, rp);

            }
        }

        ArrayList<float[]> arr = this.actions.get(dt2.systemHours());
        if (arr == null) {
            arr = new ArrayList<>();
            this.actions.put(dt2.systemHours(), arr);
        }
        float[] floats = {(float) id, (float) act, (float) lat, (float) lon};
        arr.add(floats);
        
        //aggregated data
        int key = dt2.systemHours()/72;
        float[] agg = this.actionhash_daily.get(key);
        if (agg ==null) {
            agg = new float[MAINT_NAMES.length];
            this.actionhash_daily.put(key, agg);
        }
        agg[act]++;
        
    }

    

    public String getMaintActsCountsString_hourlySum(Dtime local, String comma, int hp) {
        float[] dat = this.getMaintActsCounts_hourly(local, hp);
        String s = "";
        for (float f : dat) {
            s += f + ";";
        }
        return s;
    }
    
    public float[] getDailyTotals(Dtime dt) {
       int key = dt.systemHours()/72;
       float[] agg = this.actionhash_daily.get(key); 
       if (agg ==null) agg = new float[MAINT_NAMES.length];
       return agg;
        
    }

    public void getMaintActsCountsString_hourlySum(Observation o, ArrayList<String> arr, int hp) {
        float[] dat = this.getMaintActsCounts_hourly(o.dt, hp);

        for (float f : dat) {
            arr.add(f + "");
        }
    }


}
