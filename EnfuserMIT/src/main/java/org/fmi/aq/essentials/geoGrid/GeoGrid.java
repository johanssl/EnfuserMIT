/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.geoGrid;

import org.fmi.aq.enfuser.kriging.KrigingElements;
import org.fmi.aq.enfuser.kriging.KrigingAlgorithm;
import java.io.Serializable;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.shg.RawDatapoint;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johanssl
 */
public class GeoGrid implements Serializable {

    private static final long serialVersionUID = 7525472294622776147L;//Important: change this IF changes are made that breaks serialization!



    public float[][] values;

    public final Boundaries gridBounds;// the grid
    public final int H, W;

    public final double dlat, dlon;
    public final double latRange, lonRange;
    final public Dtime dt;

    public String varName;

    public final double pi = Math.PI;
    private final static double degreeInM = 2 * Math.PI * 6371000.0 / 360.0;

    public final double dlatInMeters;
    public final double dlonInMeters;
    public final double lonScaler;
    public final double cellA_m;

    /**
     * A grid the presents gridded data values over certain geographic area and
     * time.
     *
     * @param name name of the grid,
     * @param vals values for the grid (HxW)
     * @param dt Time attribution (null should not be used: instead use
     * Dtime.getSystemDate()
     * @param bounds geoarea for data.
     */
    public GeoGrid(String name, float[][] vals, Dtime dt, Boundaries bounds) {
        this(vals, dt, bounds.clone(name));
    }

    public boolean containsCoordinates(double lat1, double lon1) {

        if (lat1 >= this.gridBounds.latmax || lat1 <= this.gridBounds.latmin 
                || lon1 >= this.gridBounds.lonmax || lon1 <= this.gridBounds.lonmin) {
            //   console.out("point 1 is not covered. (lat, lon) " +lat1 +", "+lon1);
            return false;

        } else {
            return true;
        }
    }
    
    
    public GeoGrid hardCopy() {
       float[][] dat = new float[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
               dat[h][w] = this.values[h][w]; 
            }
        }
        return new GeoGrid(dat,this.dt.clone(),this.gridBounds.clone());
    }
    
     public GeoGrid scaledHardCopy(float sc) {
       float[][] dat = new float[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
               dat[h][w] = this.values[h][w]*sc; 
            }
        }
        return new GeoGrid(dat,this.dt.clone(),this.gridBounds.clone());
    }


    public GeoGrid(float[][] vals, Dtime dt, Boundaries bounds) {

        this.gridBounds = bounds;

        H = vals.length;
        W = vals[0].length;
        this.values = vals;

        this.dt = dt;

        this.latRange = gridBounds.latmax - gridBounds.latmin;
        this.lonRange = gridBounds.lonmax - gridBounds.lonmin;

        this.dlat = (latRange) / (double) H;
        this.dlon = (lonRange) / (double) W;

        varName = bounds.name;
        double midlat = (gridBounds.latmax + gridBounds.latmin) / 2.0;
        lonScaler = Math.cos(midlat / 360 * 2 * pi);
        this.dlatInMeters = dlat * degreeInM;
        this.dlonInMeters = dlon * degreeInM * lonScaler;
        this.cellA_m = dlatInMeters * dlonInMeters;
    }

    public GeoGrid(ArrayList<RawDatapoint> rds, float gridRes_m, Boundaries b, String name, Dtime dt) {
        this.gridBounds = b.clone();
        EnfuserLogger.log(Level.FINER,this.getClass(),"Creating GeoGrid with boundaries: " + this.gridBounds.toText());

        double centlat = b.getMidLat();

        dlat = gridRes_m / (float) degreeInM;
        dlon = dlat / (float) Math.cos(centlat / 180.0 * pi);

        this.latRange = gridBounds.latmax - gridBounds.latmin;
        this.lonRange = gridBounds.lonmax - gridBounds.lonmin;

        H = (int) (latRange / dlat);
        W = (int) (lonRange / dlon);
        EnfuserLogger.log(Level.FINER,this.getClass(),"GG resolution: H/W/res_m => " + H + "/" + W + "/" + gridRes_m);

        this.values = new float[H][W];
        this.dt = dt;
        varName = name;

        //distribute values
        int h;
        int w;
        int counter = 0;
        for (RawDatapoint p : rds) {
            h = getH(this.gridBounds, p.lat, dlat);
            w = getW(this.gridBounds, p.lon, dlon);

            if (!HWok(h, w)) {
                continue;
            }

            this.values[h][w] = p.value;
            counter++;
        }

        EnfuserLogger.log(Level.FINER,this.getClass(),"Added " + counter + " values to grid.");
        double midlat = (gridBounds.latmax + gridBounds.latmin) / 2.0;
        lonScaler = Math.cos(midlat / 360 * 2 * pi);
        this.dlatInMeters = dlat * degreeInM;
        this.dlonInMeters = dlon * degreeInM * lonScaler;
        this.cellA_m = dlatInMeters * dlonInMeters;
    }

    /**
     * Fill all grid cells with Kriging interpolated values based on the given
     * raw values.
     *
     * @param rds the list of raw datapoints.
     * @param minDist_m minimum distance value for the algirthm (a larger value
     * can smoothen the outcome near the datapoint's locations).
     */
    public void fullKrigingFill(ArrayList<RawDatapoint> rds, float minDist_m) {
        KrigingElements ae = new KrigingElements();
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                double lat = this.getLatitude(h);
                double lon = this.getLongitude(w);

                float val = KrigingAlgorithm.calculateCombinationValue_IWD(
                        (float) lat, (float) lon, rds, ae, minDist_m);
                this.values[h][w] = val;
            }//for w
        }//for h

    }

    public GeoGrid(ArrayList<RawDatapoint> rds, float gridRes_m, String name) {

        double latmin = Double.MAX_VALUE;
        double latmax = -Double.MAX_VALUE;
        double lonmin = Double.MAX_VALUE;
        double lonmax = -Double.MAX_VALUE;

        for (RawDatapoint p : rds) {
            if (p.lat > latmax) {
                latmax = p.lat;
            }
            if (p.lat < latmin) {
                latmin = p.lat;
            }

            if (p.lon > lonmax) {
                lonmax = p.lon;
            }
            if (p.lon < lonmin) {
                lonmin = p.lon;
            }
        }

        this.gridBounds = new Boundaries(latmin, latmax, lonmin, lonmax);
        EnfuserLogger.log(Level.FINER,this.getClass(),"Creating GeoGrid with boundaries: " + this.gridBounds.toText());

        double centlat = 0.5 * latmin + 0.5 * latmax;

        dlat = gridRes_m / (float) degreeInM;
        dlon = dlat / (float) Math.cos(centlat / 180.0 * pi);

        this.latRange = gridBounds.latmax - gridBounds.latmin;
        this.lonRange = gridBounds.lonmax - gridBounds.lonmin;

        H = (int) (latRange / dlat);
        W = (int) (lonRange / dlon);
        EnfuserLogger.log(Level.FINER,this.getClass(),"GG resolution: H/W/res_m => " + H + "/" + W + "/" + gridRes_m);

        this.values = new float[H][W];
        this.dt = rds.get(0).dt;
        varName = name;

        //distribute values
        int h;
        int w;
        int counter = 0;
        for (RawDatapoint p : rds) {
            h = getH(this.gridBounds, p.lat, dlat);
            w = getW(this.gridBounds, p.lon, dlon);

            if (!HWok(h, w)) {
                continue;
            }

            this.values[h][w] = p.value;
            counter++;
        }

        EnfuserLogger.log(Level.FINER,this.getClass(),"Added " + counter + " values to grid.");
        double midlat = (gridBounds.latmax + gridBounds.latmin) / 2.0;
        lonScaler = Math.cos(midlat / 360 * 2 * pi);
        this.dlatInMeters = dlat * degreeInM;
        this.dlonInMeters = dlon * degreeInM * lonScaler;
        this.cellA_m = dlatInMeters * dlonInMeters;
    }
    
    /**
     * Set value in the grid.In case the given index would cause OutOfBounds
     * then nothing is done.
     * @param h h index
     * @param w w index
     * @param val the set value
     * @return true if value was set. False means OutOfBounds.
     */
    public boolean setValue_oobSafe(int h, int w, float val) {
        if (h<0 || h> H-1) return false;
        if (w<0 || w> W-1) return false;
        this.values[h][w]=val;
        return true;
    }
    
     /**
     * Adds value to the grid by summing to the existing value.
     * In case the given index would cause OutOfBounds
     * then nothing is done.
     * @param h h index
     * @param w w index
     * @param val the set value
     */
    public void addToValue_oobSafe(int h, int w, float val) {
        if (h<0 ||h> H-1) return;
        if (w<0 ||w> W-1) return;
        this.values[h][w]+=val;
    }

    public ArrayList<RawDatapoint> getRaws() {
        ArrayList<RawDatapoint> raws = new ArrayList<>();
        Boundaries b = this.gridBounds;
        float lat, lon;
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                lat = (float) (b.latmax - h * dlat);
                lon = (float) (b.lonmin + w * dlon);
                float value = this.values[h][w];

                RawDatapoint rd = new RawDatapoint(this.dt.clone(), lat, lon, value);
                raws.add(rd);
            }
        }

        return raws;
    }

    public double getValueAtIndex(int h, int w) {
        return this.values[h][w];
    }

    public double getValueAtIndex_oobSafe(int h, int w) {
        if (h < 0) {
            h = 0;
        }
        if (h >= H) {
            h = H - 1;
        }
        if (w < 0) {
            w = 0;
        }
        if (w >= W) {
            w = W - 1;
        }

        return this.values[h][w];
    }

    public double getValue(double lat, double lon) {

        int h = getH(gridBounds, lat, dlat);
        int w = getW(gridBounds, lon, dlon);

        return this.values[h][w];
    }

    public double getValue_closest(double lat, double lon) {

        int h = getH(gridBounds, lat, dlat);
        int w = getW(gridBounds, lon, dlon);

        if (h < 0) {
            h = 0;
        }
        if (h >= H) {
            h = H - 1;
        }
        if (w < 0) {
            w = 0;
        }
        if (w >= W) {
            w = W - 1;
        }

        return this.values[h][w];
    }

    public Float getValueAt_exSafe(double lat, double lon) {

        try {
            return (float) this.getValue(lat, lon);
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    public double getLatitude(int h) {
        return this.getLat(gridBounds, h, dlat);
    }

    public double getLongitude(int w) {
        return this.getLon(gridBounds, w, dlon);
    }

    public void saveToBinary(String fileName) {

        EnfuserLogger.log(Level.FINER,this.getClass(),"Saving gg to: " + fileName);
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fileName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(this);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                Logger.getLogger(GeoGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(GeoGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static GeoGrid load(String filename) {
        // Read from disk using FileInputStream
        FileInputStream f_in;
        try {
            f_in = new FileInputStream(filename);

            // Read object using ObjectInputStream
            ObjectInputStream obj_in;
            try {
                obj_in = new ObjectInputStream(f_in);
                try {
                    // Read an object
                    Object obj = obj_in.readObject();
                    if (obj instanceof GeoGrid) {
                        GeoGrid gg = (GeoGrid) obj;
                        return gg;
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(GeoGrid.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(GeoGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(GeoGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static boolean HWok(int h, int w, int H, int W) {
        if (h < 0 || h >= H) {
            return false;
        } else if (w < 0 || w >= W) {
            return false;
        }

        return true;
    }

    public boolean HWok(int h, int w) {
        return HWok(h, w, this.H, this.W);
    }

    public static int getH(Boundaries b, double lat, double dlat) {
        return (int) ((b.latmax - lat) / dlat);
    }

    public static int getW(Boundaries b, double lon, double dlon) {
        return (int) ((lon - b.lonmin) / dlon);
    }

    public static double getLat(Boundaries b, int h, double dlat) {
        return b.latmax - h * dlat;
    }

    public static double getLon(Boundaries b, int w, double dlon) {
        return b.lonmin + w * dlon;
    }

    public int getH(double lat) {
        return (int) ((this.gridBounds.latmax - lat) / dlat);
    }

    public int getW(double lon) {
        return (int) ((lon - this.gridBounds.lonmin) / dlon);
    }

    public boolean oobs_cc(double lat, double lon) {
      int h = getH(lat);
      int w = getW(lon);
      return oobs_hw(h,w);
    }
    
    public boolean oobs_hw(int h, int w) {
        if (h < 0 || h > H-1) {
            return true;
        } else if (w < 0 || w > W-1) {
            return true;
        }
        return false;
    }
    
    public static boolean gridOobs(float[][] dat, int h, int w) {
       if (h < 0 || h > dat.length-1) {
            return true;
        } else if (w < 0 || w > dat[0].length-1) {
            return true;
        }
        return false;
    }
    
    
    public static void gridSafeSetValue(float[][] dat, int h, int w, float f) {
       if (gridOobs(dat,h,w)) return;
       dat[h][w]=f;
    }
    
    public static void gridSafeAddValue(float[][] dat, int h, int w, float f) {
       if (gridOobs(dat,h,w)) return;
       dat[h][w]+=f;
    }

    public double[] getMinMaxAverage(int sparser) {
        double min = Double.MAX_VALUE;
        double max = Double.MAX_VALUE*-1;
        double ave = 0;
        int n =0;
         for (int h = 0; h < H; h+=sparser) {
            for (int w = 0; w < W; w+=sparser) {
                float val = this.values[h][w];
                if (val < min) min = val;
                if (val > max) max = val;
                ave+=val;
                n++;
            }
         }
         ave/=n;
         
         return new double[]{min,max,ave};
    }

    public ByteGeoGrid convert() {
        return new ByteGeoGrid(this.values,this.dt.clone(),this.gridBounds.clone());
    }

    public boolean SafeAddValue(float lat, float lon, float add) {
       int h = this.getH(lat);
       int w = this.getW(lon);
       if (!this.oobs_hw(h, w)) {
           this.values[h][w]+=add;
           return true;
       }
       return false;
    }

    public void flipDataVertically() {
       float[][] ndat = new float[this.H][this.W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                float val = this.values[h][w];
                ndat[H-h-1][w] =val;
            }
        }
        this.values = ndat;
    }
    
    public static GeoGrid flipY(GeoGrid g) {
        float[][] dat = new float[g.H][g.W];
        
        for (int h =0;h<g.H;h++) {
            for (int w =0;w<g.W;w++) {
                int hInv = g.H - h-1;
                dat[h][w] = g.values[hInv][w];
            }
        }
        return new GeoGrid(dat,g.dt.clone(),g.gridBounds.clone());
    }



}
