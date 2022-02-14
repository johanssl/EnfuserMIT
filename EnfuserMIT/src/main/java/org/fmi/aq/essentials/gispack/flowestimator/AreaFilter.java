/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.PolygonC;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;

/**
 *
 * @author johanssl
 */
public class AreaFilter {
    public final static String CUSTOM_AFILTER = "AREA_FILTER<";
    PolygonC p;
    float[] radPoint = null;
    private Boundaries maxBounds;
    private HashMap<Long, Boolean> roughHash = null;
    private double hash_resolution_degrees;

    public final static String KEY_RADF = "radius_filter";
    public final static String KEY_POLYF = "polygon_filter";
    public final static String[] AKEYS = {
        KEY_RADF,
        KEY_POLYF,};

    public AreaFilter() {

    }

    public String toMapTaskerString() {
        if (!this.hasRules()) {
            return "";
        }
        String s = CUSTOM_AFILTER + "";

        if (radPoint != null) {
            s += KEY_RADF + "=" + radPoint[0] + "," + radPoint[1] + "," + radPoint[2] + ">";

        } else if (p != null) {
            s += KEY_POLYF + "=";
            for (int i = 0; i < p.npoints; i++) {
                //int x = p.xpoints[i];
                //int y = p.ypoints[i];
                double lat = p.lats[i];//(double) y / TO_INTEGER;
                double lon = p.lons[i];//(double) x / TO_INTEGER;
                s += lat + "," + lon;
                if (i < p.npoints - 1) {
                    s += CCSEP;
                }
            }
            s += ">";
        }
        return s;
    }

    public void setAsRadiusFilter(float lat, float lon, float rad_m) {

        this.radPoint = new float[]{lat, lon, rad_m};
        this.p = null;
    }

    public void setAsPolygonFilter(ArrayList<double[]> arr) {
        this.radPoint = null;
        
        arr.add(arr.get(0));//add the first also to last to close the polygon.
        this.p = new PolygonC(arr);
        this.maxBounds = getMaxBounds(arr);
    }

    public static Boundaries getMaxBounds(ArrayList<double[]> latlon) {
        if (latlon == null || latlon.size() < 2) {
            return null;
        }
        double latmin = Double.MAX_VALUE;
        double latmax = -Double.MAX_VALUE;
        double lonmin = Double.MAX_VALUE;
        double lonmax = -Double.MAX_VALUE;

        for (double[] cc : latlon) {
            double lat = cc[0];
            double lon = cc[1];
            if (lat > latmax) {
                latmax = lat;
            }
            if (lat < latmin) {
                latmin = lat;
            }

            if (lon > lonmax) {
                lonmax = lon;
            }
            if (lon < lonmin) {
                lonmin = lon;
            }
        }

        return new Boundaries(latmin, latmax, lonmin, lonmax);

    }

    public boolean isIncluded(double lat, double lon) {
        //polygon
        if (p != null) {

            if (!p.contains(lat, lon) ) {//to avoid errors also
                //check the "erroneus" inverted polygon. False positives should 
                //not occur unless lat is approximately the same as lon.
                return false;
            }
        }
        //radius point
        if (radPoint != null) {
            float rlat = radPoint[0];
            float rlon = radPoint[1];
            float rdist_m = radPoint[2];

            double dist_m = Observation.getDistance_m(rlat, rlon, lat, lon);
            if (dist_m > rdist_m) {
                return false;
            }
        }
        return true;
    }
    
    ArrayList<RoadPiece> getCloseBy(OsmLayer ol) {
      if (this.radPoint!=null  ) {
          float rlat = radPoint[0];
          float rlon = radPoint[1];
          float rdist_m = radPoint[2];
          if (rdist_m <=10) {//return the first encountered roadPiece.
            return OSMget.findRoads(null, 1, ol, rlat, rlon, rdist_m);
          }
      }
      return null;
    }

    public final static String CCSEP = "  ";

    public static AreaFilter fromStrings(String argline) {
       
        EnfuserLogger.log(Level.FINER,AreaFilter.class,
                "==========AreaFilter: parsing from strings==============");
        
        if (argline == null) {
            return null;
        }

        AreaFilter afilt = new AreaFilter();
        if (argline.contains(KEY_RADF)) {
            String val = argline.replace(KEY_RADF + "=", "");

            String[] split = val.split(",");
            afilt.setAsRadiusFilter(
                    Double.valueOf(split[0]).floatValue(),
                    Double.valueOf(split[1]).floatValue(),
                    Double.valueOf(split[2]).floatValue()
            );
            
                EnfuserLogger.log(Level.FINER,AreaFilter.class,
                        "AreaFilter: radius point added: " + afilt.radPoint[0] + ", "
                        + afilt.radPoint[1] + " radius[m] = " + (int) afilt.radPoint[2]);
            

        } else if (argline.contains(KEY_POLYF)) {
            String val = argline.replace(KEY_POLYF + "=", "");
            String[] split = val.split(CCSEP);//lat1,lon1  lat2,lon2  ...
            ArrayList<double[]> arr = new ArrayList<>();
            for (String s : split) {
                String[] ss = s.split(",");

                arr.add(new double[]{
                    Double.valueOf(ss[0]),
                    Double.valueOf(ss[1]),});
            }
            afilt.setAsPolygonFilter(arr);
            EnfuserLogger.log(Level.FINER,AreaFilter.class,
                        "AreaFilter: polygon added with " + arr.size() + " points.");
            

        }

        if (!afilt.hasRules()) {
            return null;
        }
        return afilt;
    }

    public boolean hasRules() {
        if (this.p != null || this.radPoint != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Create a pre-computed hash table for coordinates that are assessed to be
     * included in the area limit polygon.
     *
     * @param d defines the hash table resolution in degrees. Setting the value
     * to 0.01 the resolution is in the order of 1km (The value is not allowed
     * to be smaller than 0.001). This method should be used for areas outside
     * of polar regions ( abs(lat) smaller than 80). This is to be used only for
     * Polygon area limits, not the radius.
     */
    public void setupRoughHash(double d) {
        this.hash_resolution_degrees = Math.max(Math.abs(d), 0.001);
        this.roughHash = new HashMap<>();
        int adds = 0;
        int iters = 0;
        for (double lat = this.maxBounds.latmin; lat <= this.maxBounds.latmax; lat += this.hash_resolution_degrees) {
            for (double lon = this.maxBounds.lonmin; lon <= this.maxBounds.lonmax; lon += this.hash_resolution_degrees) {
                iters++;
                long key = this.getKey(lat, lon);

                boolean in = this.isIncluded(lat, lon);
                if (in) {
                    adds++;
                    this.roughHash.put(key, Boolean.TRUE);
                }

            }//for lon
        }//for lat
        
        EnfuserLogger.log(Level.FINER,AreaFilter.class,
                    "Rough Hash created for AreaFilter. Total iterations = " 
                            + iters + ", in-count = " + adds);
        
    }

    /**
     * get the hash key as a function of coordinates
     *
     * @param lat the latitude
     * @param lon the longitude
     * @return unique long key for the coordinate pair.
     */
    private long getKey(double lat, double lon) {
        //max value is 90/0.001 = 90 000. This times 100k is smaller than 10^5*10^5 = 10^10.
        //a Long value can easily handle this
        long k = (long) (lat / this.hash_resolution_degrees) * 10000 + (long) (lon / this.hash_resolution_degrees);
        return k;

    }

    /**
     * Estimate whether or not this location is inside the area filter based on
     * pre-computed hashed values. Note: setupRoughHash() must have been called
     * before using this method.
     *
     * @param lat the latitude
     * @param lon the longitude
     * @return boolean value, true if 'in'.
     */
    public boolean isIncluded_roughHash(float lat, float lon) {
        Boolean in = this.roughHash.get(getKey(lat, lon));
        if (in == null) {
            return false;
        } else {
            return true;
        }
    }


}
