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
import org.fmi.aq.enfuser.datapack.source.DBline;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import java.util.Collection;
import static java.lang.Math.abs;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.options.VarConversion;
import org.fmi.aq.enfuser.options.RunParameters;

/**
 *
 * @author johanssl
 */
public class RoadWeather {
    
    HashMap<String, HashMap<Integer, RoadCond>> condHash_id = new HashMap<>();//use roadW stationID as primary key and sysH for secondary
    HashMap<Integer, HashMap<String, RoadCond>> condHash_time = new HashMap<>();//use sysHour as primary key and stationID for secondary - this gives access to all, but small amount of stations, each specific hour

    ConcurrentHashMap<Long, String> rp_to_measurementID = new ConcurrentHashMap<>();
    int obsSize = 0;

    HashMap<String, float[]> measlatLons = new HashMap<>();
    private float coslat;

    final HashMap<String, Observation>[] metObs;//Observation instances for road surface moisture, which is also a Met-variable.
    //public boolean dataSparsing = true;

    private VarConversion[] rcConverts = new VarConversion[RoadWeatherFeed.RMES_SPECS.length];
    private RoadWeatherFeed feed;

    public RoadWeather(FusionOptions ops, DataBase DB, RunParameters p, DataPack dp) {

        //init varConversions
        initRCconverts(ops);
         metObs = new HashMap[ops.VARA.VAR_LEN()];
        for (int i : ops.VARA.metVars) {
            metObs[i] = new HashMap<>();
        }
        Dtime start = p.start();
        Dtime end = p.end();
        feed = new RoadWeatherFeed(ops);

        if (feed.readable) {//Read Road Conditions ONLY if set to do so.
            ArrayList<String> arr;
            File f = new File(feed.primaryMinerDir);
            File[] ff = f.listFiles();
            if (ff != null) {
                for (File file : ff) {
                    String name = file.getName();
                    String[] namsplit = name.split("_");
                    String date = namsplit[1].replace("-00-00Z", ":00:00");

                    Dtime dt = null;
                    try {
                        dt = new Dtime(date);
                    } catch (NullPointerException e) {
                        EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                        "RoadConditions: could not parse file time stamp: " + date 
                                + ", from " + file.getAbsolutePath());
                        continue;
                    }

                    //read if timewise necessary
                    if (start.systemHours() > dt.systemHours() || end.systemHours() < dt.systemHours()) {
                        continue;//not applicable
                    }

                    if (name.contains("_OBS")) {

                        arr = FileOps.readStringArrayFromFile(file.getAbsolutePath(), false);
                        boolean dataSparsing = arr.size() > 10000;
                        int n = 0;
                        for (String line : arr) {
                            n++;
                            if (dataSparsing && n % 4 != 0) {
                                continue;//every fourth line will be read. There can be lots of 5 min data which is mostly unnecessary.
                            }
                            RoadCond rc = new RoadCond(DB, line, p.bounds());
                            this.putCond(rc, DB, ops);
                        }

                    } 
                }//for files
            }

            //evaluate coslat
            float avelat = 0;
            for (float[] latlon : this.measlatLons.values()) {
                avelat += latlon[0];
            }

            avelat /= this.measlatLons.size();
            this.coslat = (float) Math.cos(avelat * Math.PI / 180.0);

            EnfuserLogger.log(Level.FINER,RoadWeather.class,"ROAD CONDITIONS========");
            EnfuserLogger.log(Level.FINER,RoadWeather.class,
                    "\t unique road weather stations: " + this.measlatLons.size() + ", midlat = " + avelat);

            for (HashMap<Integer, RoadCond> hash : this.condHash_id.values()) {
                this.obsSize += hash.size();
            }
            EnfuserLogger.log(Level.FINER,RoadWeather.class,
                    "\t Road weather observations in total: " + obsSize);
            int j = 0;
            for (HashMap<String, Observation> obs : this.metObs) {
                if (obs != null && !obs.isEmpty()) {
                    EnfuserLogger.log(Level.FINER,RoadWeather.class,
                            "Adding roadWater amount as Met.observations, size = " 
                            + obs.size() + " for type " + ops.VAR_NAME(j));
                }
                j++;
            }

        }

        //finally, put all RoadConds to hourhash
        for (String mref : condHash_id.keySet()) {
            HashMap<Integer, RoadCond> hash = condHash_id.get(mref);
            for (Integer sysH : hash.keySet()) {
                //get or create time hash
                RoadCond rc = hash.get(sysH);
                HashMap<String, RoadCond> timeHash = this.condHash_time.get(sysH);
                if (timeHash == null) {
                    timeHash = new HashMap<>();
                    this.condHash_time.put(sysH, timeHash);//now it's there
                }

                //add this RoadCond
                timeHash.put(mref, rc);
            }//for time
        }//for coordIDs
        
        obsToDataPack(dp,ops,DB);
    }


    public Collection<RoadCond> getAllHourlyRC(Dtime dt) {
        int sysH = dt.systemHours();
        HashMap<String, RoadCond> hash = this.condHash_time.get(sysH);
        if (hash != null) {
            return hash.values();
        }
        return null;
    }

    public RoadCond getClosestHourlyRC(Dtime dt, double lat, double lon) {
        int sysH = dt.systemHours();
        HashMap<String, RoadCond> hash = this.condHash_time.get(sysH);
        if (hash != null) {

            double minDist = Double.MAX_VALUE;
            RoadCond closest = null;
            for (RoadCond rc : hash.values()) {
                double dist = Observation.getDistance_m(lat, lon, rc.lat, rc.lon);
                if (dist < minDist) {
                    minDist = dist;
                    closest = rc;
                }
            }//for rcs
            return closest;
        }
        return null;
    }


    private final static float LAND = -0.5f;
    private final static float WATER = -1f;

    public float[][] getGridRepresentation(Dtime dt, int rcType, double lat,
            double lon, OsmLayer ol, int resDiv, float valScaler) {

        //OsmLayer ol = ens.mapPack.getOsmLayer(ens.mapPack, lat, lon);
        if (ol == null) {
            return null;
        }

        float[][] dat = new float[ol.H / resDiv][ol.W / resDiv];
        for (int h = 0; h < dat.length; h++) {
            for (int w = 0; w < dat[0].length; w++) {
                int ch = h * resDiv;
                int cw = w * resDiv;

                try {
                    byte osm = ol.luSurf[ch][cw];

                    if (OSMget.isHighway(osm)) {

                        RoadPiece rp = ol.getRoadPiece(ch, cw);
                        RoadCond cond = this.getRoadCond(rp, dt, false);
                        float val = cond.vals[rcType] * valScaler;
                        dat[h][w] = val;
                    } else if (OSMget.isWater(osm)) {//visualize water
                        dat[h][w] = WATER;
                    } else {
                        dat[h][w] = LAND;//visualize land
                    }

                } catch (Exception e) {

                }
            }
        }

        int rad = 3;
        //then actual measurements to grid
        for (String ref : this.measlatLons.keySet()) {

            float[] latlon = this.measlatLons.get(ref);
            RoadCond rc = this.getRoadCond(ref, dt.systemHours());
            if (rc != null) {
                float val = rc.vals[rcType] * valScaler;
                boolean wasAvailable = rc.available[rcType];
                int h = ol.get_h(latlon[0]);//index at OsmLayer.
                int w = ol.get_w(latlon[1]);

                try {

                    int dh = h / resDiv;//index at produced grid
                    int dw = w / resDiv;
                    for (int i = -rad; i <= rad; i++) {
                        for (int j = -rad; j <= rad; j++) {
                            if (!wasAvailable && ((int) abs(i) == rad || (int) abs(j) == rad)) {//box the measurement to indicate N/A
                                dat[dh + i][dw + j] = WATER;
                            } else {
                                dat[dh + i][dw + j] = val;
                            }
                        }
                    }

                } catch (Exception e) {

                }

            }

        }

        return dat;
    }

    /**
     * Fetch the closest roadCond instance for this particular road and time
     *
     * @param rp RoadPiece element for which the conditions are sought for.
     * @param dt time
     * @param print if true, then additional printouts are given.
     * @return a RoadCond instance matched with the given roadPiece
     */
    public RoadCond getRoadCond(RoadPiece rp, Dtime dt, boolean print) {
        if (this.measlatLons.isEmpty()) {
            return null;//nothing will be available
        }
        String mref = this.rp_to_measurementID.get(rp.ref_number);
        if (mref == null || print) {//link does not exist yet, create
            if (print) {
                EnfuserLogger.log(Level.FINER,RoadWeather.class,
                        "roadWeather IDref does not exist for road " + rp.ref_number + " (" + rp.name + ")");
            }
            float midlat = rp.getMidLat();

            float midlon = rp.getMidLon();

            if (print) {
                EnfuserLogger.log(Level.FINER,RoadWeather.class,
                        "\t midPoint = " + midlat + ", " + midlon);
            }
            //check measurement locs and find the closest
            double minDist = Double.MAX_VALUE;

            for (String ref : this.measlatLons.keySet()) {
                float[] latlon = this.measlatLons.get(ref);
                float lat = latlon[0];
                float lon = latlon[1];
                float dist = (lat - midlat) * (lat - midlat) + (lon - midlon) * (lon - midlon) * coslat * coslat;
                if (dist < minDist) {
                    minDist = dist;
                    mref = ref + "";
                }//update
            }//for keys

            //store link
            if (print) {
                double dist_m = Math.sqrt(minDist) * degreeInMeters;
                EnfuserLogger.log(Level.FINER,RoadWeather.class,
                        "Closest roadWeather station found: " + mref 
                                + " at a distance of " + (int) dist_m + " m");
            }
            this.rp_to_measurementID.put(rp.ref_number, mref);

        }//if not found

        //find data for this measurement id.
        RoadCond rc = getRoadCond(mref, dt.systemHours());
        if (print) {
            if (rc == null) {
                EnfuserLogger.log(Level.FINER,RoadWeather.class
                        ,"roadWeather was NULL for this time: " + dt.getStringDate());
            } else {
                EnfuserLogger.log(Level.FINER,RoadWeather.class,
                        "roadWeather existed for this time: " + dt.getStringDate());
            }
        }
        return rc;
    }

    public RoadCond getRoadCond(String mref, int sysH) {
        HashMap<Integer, RoadCond> hash = this.condHash_id.get(mref);
        if (hash == null) {
            return null;
        }
        return hash.get(sysH);
    }

    private ArrayList<float[]> obsTemp = new ArrayList<>();

    private void putCond(RoadCond rd, DataBase DB, FusionOptions ops) {
        if (!rd.relevant) {
            return;
        }
        HashMap<Integer, RoadCond> hash = this.condHash_id.get(rd.coordID);
        if (hash == null) {
            hash = new HashMap<>();
            this.condHash_id.put(rd.coordID, hash);
        }
        //first check if an roadCond exist for this loc
        RoadCond existing = hash.get(rd.dt.systemHours());
        if (existing == null) {//simple case, did not exist
            hash.put(rd.dt.systemHours(), rd);
            this.measlatLons.put(rd.coordID, new float[]{(float) rd.lat, (float) rd.lon});

        } else {
            existing.inheritValues(rd);//just inherit values
        }

        //add observation
        DBline dbl = DB.getStoreMeasurementSource(feed,null,
                null, (byte) 0, rd.lat,
                rd.lon, 2, 3);

        String obsKey = rd.dt.systemHours() + "_" + rd.coordID;//unique entry for each hour and location.
        obsTemp = rd.getMetObsValues(obsTemp, this.rcConverts,ops);//list all value-type pairs that can be converted into MetVars and stored to DataPack.

        for (float[] val_index : obsTemp) {//iterate over value-types 
            float val = val_index[0];
            int typeInd = (int) val_index[1];
            Observation o = new Observation((byte) typeInd, dbl.ID, (float) rd.lat, (float) rd.lon,
                    rd.dt, val, 3, null);
            this.metObs[typeInd].put(obsKey, o);
        }

    }

    private void obsToDataPack(DataPack dp, FusionOptions ops, DataBase DB) {
        for (HashMap<String, Observation> obs : this.metObs) {
            if (obs == null) {
                continue;
            }
            for (Observation o : obs.values()) {
                dp.addObservation(o, ops, DB);
            }
        }
        
        //road moisture is important for road dust modelling. However, the previous
        //values also matter, so it makes sense to turn it into a rolling average.
        VariableData vd = dp.getVarData(ops.VARA.VAR_ROADWATER);
        if (vd!=null) {
            int adjusts =0;

            ArrayList<StationSource> sts = vd.stationSources();
            for (StationSource st:sts) {
                for (Observation o:st.getLayerObs()) {
                    float p1 = o.origObsVal();

                    Observation prev2 = st.getExactObservation(o.dt,-2);
                    Observation prev4 = st.getExactObservation(o.dt,-4);
                    
                    if (prev2!=null && prev4!=null) {
                        float p2 = prev2.origObsVal();
                        float p4 = prev4.origObsVal();
                        float rolling = (p1 + p2 + p4)/3;
                        o.replaceValue(rolling);
                        adjusts++;
                    }
                    
                }//for obs
            }//for stations
            EnfuserLogger.log(Level.INFO,RoadWeather.class,"RoadConditions: road water averaging done for " 
                        + adjusts +" measurements.");
        }//if VariableData exists
        
    }


    private void initRCconverts(FusionOptions ops) {

        for (int i = 0; i < this.rcConverts.length; i++) {

            String name = RoadWeatherFeed.RMES_SPECS[i];
            VarConversion vc = ops.VARA.allVC_byName.get(name);
            if (vc != null) {
                this.rcConverts[i] = vc;
                EnfuserLogger.log(Level.FINER,RoadWeather.class,"RoadConditions: adding a varConversion for " 
                        + ops.VAR_NAME(vc.typeInt) + " => " + name);
            }

        }

    }

    public String getClosestStation(float midlat, float midlon) {
        float minDist = Float.MAX_VALUE;
        String mref = null;
        for (String ref : this.measlatLons.keySet()) {
            float[] latlon = this.measlatLons.get(ref);
            float lat = latlon[0];
            float lon = latlon[1];
            float dist = (lat - midlat) * (lat - midlat) + (lon - midlon) * (lon - midlon) * coslat * coslat;
            if (dist < minDist) {
                minDist = dist;
                mref = ref + "";
            }//update
        }//for keys 

        return mref;
    }




}
