/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.enfuser.ftools.FastMath;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.enfuser.ftools.FastMath.degIntoRad;
import static org.fmi.aq.enfuser.ftools.FastMath.radIntoDeg;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author Lasse
 */
public class Transforms {


    public static float[] latlon_toMeters(float lat, float lon, Boundaries b) {

        double scaler = FastMath.FM.getFastLonScaler(lat);

        double dlon = (lon - b.lonmin);
        float dlon_m = (float) (dlon * degreeInMeters * scaler);

        double dlat = (lat - b.latmin);
        float dlat_m = (float) (dlat * degreeInMeters);

        return new float[]{dlat_m, dlon_m};
    }
    
    public static float latToPuffY(float lat, PuffPlatform pf) {
        double dlat = (lat - pf.in.bounds.latmin);
        return (float) (dlat * degreeInMeters);
    }
    
    public static float lonToPuffX(float lat,float lon, PuffPlatform pf) {
        double scaler = FastMath.FM.getFastLonScaler(lat);
        double dlon = (lon - pf.in.bounds.lonmin);
        return (float) (dlon * degreeInMeters * scaler);
    }

    public static float[] meters_toLatLon_SLOW(float y, float x, Boundaries b) {

        double scaler = FastMath.FM.getFastLonScaler(b.getMidLat());

        double dlon = x / degreeInMeters / scaler;
        float lon = (float) (dlon + b.lonmin);

        double dlat = y / degreeInMeters;
        float lat = (float) (dlat + b.latmin);

        return new float[]{lat, lon};
    }

    public static float meters_toLat(float y, float x, Boundaries b, float scaler, float[] temp) {

        double dlat = y / degreeInMeters;
        float lat = (float) (dlat + b.latmin);
        return lat;

    }

    public static float[] meters_toLatLon_FAST(float y, float x, Boundaries b, float scaler, float[] temp) {

        double dlon = x / degreeInMeters / scaler;
        float lon = (float) (dlon + b.lonmin);

        double dlat = y / degreeInMeters;
        float lat = (float) (dlat + b.latmin);

        if (temp == null) {
            temp = new float[]{lat, lon};
        } else {
            temp[0] = lat;
            temp[1] = lon;
        }

        return temp;

    }

    public static float headingIntoDeg(float heading) {

        float f;

        if (heading >= 0 && heading <= 90) {// north-east, from 90 to 0 in kartesian (I)
            f = 90 - heading;   // e.g. heading is 30 => 60 in kartesian
        } else if (heading >= 90 && heading <= 180) { // south-east, from 270 to 360 in kartesian (IV)
            f = 360 + 90 - heading;// e.g. heading is 170 => 280 in kartesian

        } else if (heading >= 180 && heading <= 270) { // south_west, from 180 to 270 in kartesian (III)
            f = 270 + 180 - heading; // e.g. heading is 270 => 180 in kartesian
        } else {//north-west, from 90 to 180 in kartesian (II)
            f = 360 + 90 - heading; // e.g. heading is 330 => 90+30 in kartesian
        }

        return f;

    }

    private static float degIntoHeading(float deg) {

        // float rad = (float)(deg/2/PI*360); // into deg
        float f;
        if (deg >= 0 && deg <= 90) {// north-east, from 90 to 0 in kartesian (I)
            f = 90 - deg;   // e.g. rad is 30 => 60 in heading
        } else if (deg >= 90 && deg <= 180) { // north_west,  (II)
            f = 360 + 90 - deg; // e.g. rad is 120 => 330 in heading

        } else if (deg >= 180 && deg <= 270) { // south_west (III)
            f = 270 + 180 - deg; // e.g. rad is 270 => 180 in heading

        } else {//north-west, from 90 to 180 in kartesian (IV)
            f = 360 + 90 - deg;// e.g. rad is 330 => 120 in heading
        }

        return f;

    }


    public static float[] UintoApparentWind(boolean print, float windSpeed,
            float windDir, float objSpeed, float objDir) {
        //degs into radians
        if (objSpeed < 0.1) {
            return new float[]{windSpeed, windDir};
        }
        if (print) {
            EnfuserLogger.log(Level.FINER,Transforms.class,"ws / wd / obS / obH: " + windSpeed + "m/s  "
                    + windDir + " / " + objSpeed + "m/s  " + objDir);
        }

        float wdeg;// = (float)(windDir/360.0*2*PI); 
        wdeg = headingIntoDeg(windDir);
        float objDeg;// = (float)(objHeading/360.0*2*PI);
        objDeg = headingIntoDeg(objDir);
        //calculate v - v_obj, in vector form
        // note: wind is 'from' vector and obj is 'to' vector! So this corresponds to -(-v - v_obj)

        double objRad = degIntoRad(objDeg);
        double windRad = degIntoRad(wdeg);

        double y_o = objSpeed * Math.sin(objRad);
        double x_o = objSpeed * Math.cos(objRad);

        double y_w = windSpeed * Math.sin(windRad);
        double x_w = windSpeed * Math.cos(windRad);

        double y_ap = y_w + y_o;
        double x_ap = x_w + x_o;

        double apparent_u = Math.sqrt(x_ap * x_ap + y_ap * y_ap);

        double appaRad = Math.acos(x_ap / apparent_u);
        double appaDeg = radIntoDeg(appaRad);

        // EnfuserLogger.log(Level.FINER,this.class,"Apparent u/rad" + apparent_u +" / "+appaRad);
        float appaDir = degIntoHeading((float) appaDeg);
        if (print) {
            EnfuserLogger.log(Level.FINER,Transforms.class,"Apparent u " + apparent_u + "m/s, Apparent dir = " + appaDir);
        }

        float[] ff = {(float) apparent_u, appaDir};
        return ff;
    }

}
