/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feed.roadweather;

import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.meteorology.WindConverter;
import static org.fmi.aq.enfuser.mining.feed.roadweather.RoadWeatherFeed.RMES_SPECS;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.VarConversion;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 *
 * @author johanssl
 */
public class RoadCond {

    public static String getDataString(RoadCond rc, String comma) {
        /*
         "roadStationId",//0
   "measuredTime",//1
         */
        String s;
        if (rc == null) {
            s = ";;";
        } else {
            s = rc.orig_id + ";" + rc.dt.getStringDate() + ";";
        }
        //then add data
        for (int i = 2; i < RoadWeatherFeed.RMES_SPECS.length; i++) {
            if (rc != null) {
                s += rc.vals[i];
            }
            s += ";";
        }

        return s;
    }

    public static void addHeaderStrings(ArrayList<String> arr) {

        for (int i = 0; i < RoadWeatherFeed.RMES_SPECS.length; i++) {
            arr.add(RoadWeatherFeed.RMES_SPECS[i]);
        }

    }

    public static void addDebugStrings(boolean forNeural, ArrayList<String> arr, RoadCond rc) {
        //first ref and time
        if (rc == null) {
            arr.add(FuserTools.EMPTY_STRING);
            arr.add(FuserTools.EMPTY_STRING);

        } else {

            if (forNeural) {//time and ref are not needed
                arr.add(FuserTools.EMPTY_STRING);
            } else {
                arr.add(rc.orig_id);
                arr.add(rc.dt.getStringDate());
            }
        }

        //then add data
        for (int i = 2; i < RoadWeatherFeed.RMES_SPECS.length; i++) {
            if (rc != null) {
                arr.add(rc.vals[i] + "");
            } else {
                arr.add(FuserTools.EMPTY_STRING);
            }

        }

    }

    public static String getHeaderString(String comma) {

        String s = "";
        for (int i = 0; i < RoadWeatherFeed.RMES_SPECS.length; i++) {
            s += RoadWeatherFeed.RMES_SPECS[i] + ";";
        }

        return s;
    }

    public String coordID;//for some locations there are multiple ID's with different measurement equipment. This is not good, so RoadConds take non-zero values from other competing entries
    private String orig_id;
    public Dtime dt;
    public double lat;
    public double lon;

    public float[] vals;
    public boolean[] available;
    boolean relevant = false;

    float[] windNE = null;

    public RoadCond(DataBase DB, String readline, Boundaries b) {

        String[] temp = readline.split(";");
        orig_id = temp[RoadWeatherFeed.I_ID];
        String date = temp[RoadWeatherFeed.I_TIME];
        //coordinates
        double[] latlon = DB.getCustomCoords(orig_id);
        if (latlon != null) {
            relevant = true;
            this.lat = latlon[0];
            this.lon = latlon[1];
            this.coordID = getCoordID(lat, lon);
            if (!b.intersectsOrContains(lat, lon)) {
                relevant = false;
            }

        }

        if (relevant) {
            dt = new Dtime(date);
            dt.addSeconds(-dt.min * 60 - dt.sec); //fullHour

            vals = new float[RoadWeatherFeed.RMES_SPECS.length];
            available = new boolean[RoadWeatherFeed.RMES_SPECS.length];

            int LEN = RoadWeatherFeed.RMES_SPECS.length;
            if (temp.length < LEN) {
                LEN = temp.length;
            }

            for (int i = 2; i < LEN; i++) {
                try {
                    if (temp[i].length() > 0) {
                        vals[i] = Double.valueOf(temp[i]).floatValue();
                        available[i] = true;
                    }
                } catch (NumberFormatException e) {
                }
            }//for parameters

            //process wind components
            if (available[RoadWeatherFeed.I_AVEWIND] && available[RoadWeatherFeed.I_WD]) {
                this.windNE = WindConverter.dirSpeedFrom_toNE(vals[RoadWeatherFeed.I_WD], vals[RoadWeatherFeed.I_AVEWIND]);
            }
        }//if relevant

    }

    public void inheritValues(RoadCond second) {

        for (int i = 0; i < vals.length; i++) {
            if (!this.available[i] && second.available[i]) {
                //take it from second and mark as available
                this.vals[i] = second.vals[i];
                this.available[i] = true;
            } else if (second.available[i] && this.vals[i] == 0) {//change to switch zero value to something else
                this.vals[i] = second.vals[i];
                this.available[i] = true;
            }
        }

    }

    private String getCoordID(double lat, double lon) {
        String s = (int) (lat * 1000) + "_" + (int) (lon * 1000);
        return s;
    }

    ArrayList<float[]> getMetObsValues(ArrayList<float[]> obsTemp,
            VarConversion[] vcs, FusionOptions ops) {
        obsTemp.clear();
        VarAssociations VARA = ops.VARA;

        for (int i = 0; i < RMES_SPECS.length; i++) {
            if (available[i]) {
                VarConversion vc = vcs[i];
                if (vc == null) {
                    continue;
                }
                float value = vc.applyScaleOffset(this.vals[i]);

                obsTemp.add(new float[]{value, vc.typeInt});
            }//if datapoint is available

        }//for all rc types

        if (this.windNE != null) {
            obsTemp.add(new float[]{windNE[0], VARA.VAR_WIND_N});
            obsTemp.add(new float[]{windNE[1], VARA.VAR_WIND_E});
        }

        return obsTemp;
    }

}
