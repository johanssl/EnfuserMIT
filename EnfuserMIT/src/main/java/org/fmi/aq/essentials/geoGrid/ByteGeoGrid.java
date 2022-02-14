/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.geoGrid;

import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.date.Dtime;
import java.io.Serializable;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import org.fmi.aq.essentials.shg.RawDatapoint;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class ByteGeoGrid implements Serializable {

    private static final long serialVersionUID = 7526372294622776147L;//Important: change this IF changes are made that breaks serialization!
    public byte[][] values;
    public final float VAL_SCALER;
    public final float VAL_FLAT_ADD;

    public final Boundaries gridBounds;//the grid
    public final int H, W;

    public final double dlat, dlon;
    public final double latRange, lonRange;
    final public Dtime dt;

    public String varName;

    public final double pi = Math.PI;
    public final static double degreeInM = 2 * Math.PI * 6371000.0 / 360.0;

    public final double dlatInMeters;
    public final double dlonInMeters;
    public final double lonScaler;
    public final double cellA_m2;

    /**
     * A compact representation of geogridded data, using bytes.
     *
     * @param name Name of the grid
     * @param vals values for grid.
     * @param dt Time attribute
     * @param bounds Geobounds for the grid.
     */
    public ByteGeoGrid(String name, byte[][] vals, Dtime dt, Boundaries bounds) {
        this(vals, dt, bounds.clone(name));
    }

    public ByteGeoGrid(byte[][] vals, Dtime dt, Boundaries bounds) {

        this.VAL_SCALER = 1;
        this.VAL_FLAT_ADD = 0;
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
        this.cellA_m2 = dlatInMeters * dlonInMeters;
    }

    /**
     * A compact representation of geogridded data, using bytes. Floats are
     * converted into bytes using a 250 stepped-scaling (and an offset)
     *
     * @param floats values for grid.
     * @param dt Time attribute
     * @param bounds Geobounds for the grid.
     */
    public ByteGeoGrid(float[][] floats, Dtime dt, Boundaries bounds) {

        this.gridBounds = bounds;

        H = floats.length;
        W = floats[0].length;

        this.values = new byte[H][W];

        float maxVal = Float.MIN_VALUE;
        float minVal = Float.MAX_VALUE;
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                if (Float.isNaN(floats[h][w])) continue;
                
                if (floats[h][w] > maxVal) {
                    maxVal = floats[h][w];
                }
                if (floats[h][w] < minVal) {
                    minVal = floats[h][w];
                }
            }
        }

        this.dt = dt;

        float range = maxVal - minVal;
        this.VAL_FLAT_ADD = minVal;
        if (range == 0) {
            this.VAL_SCALER = 0;
        } else {
            this.VAL_SCALER = 250f / range;
        }

            EnfuserLogger.log(Level.FINER,this.getClass(),"Byte scaling is used! Scaler is " 
                    + VAL_SCALER + "\n" + "Range/flatAdd => " + range + "/" + this.VAL_FLAT_ADD);
        

        //setup values
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                if (Float.isNaN(floats[h][w])) {
                    values[h][w] = (byte)(-127);//mark as NaN
                    continue;
                }
                
                values[h][w] = (byte) ((floats[h][w] - this.VAL_FLAT_ADD) * VAL_SCALER - 125);
                if (this.values[h][w] < -125) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue " + floats[h][w] + "/" + this.values[h][w]);
                    // this.values[h][w] = (byte)127;
                } //TODO 256 different values could be USED!!!!
                if (this.values[h][w] > 125) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue " + floats[h][w] + "/" + this.values[h][w]);
                    //  this.values[h][w] = (byte)127;
                }
            }
        }

        this.latRange = gridBounds.latmax - gridBounds.latmin;
        this.lonRange = gridBounds.lonmax - gridBounds.lonmin;

        this.dlat = (latRange) / (double) H;
        this.dlon = (lonRange) / (double) W;

        varName = bounds.name;
        double midlat = (gridBounds.latmax + gridBounds.latmin) / 2.0;
        lonScaler = Math.cos(midlat / 360 * 2 * pi);
        this.dlatInMeters = dlat * degreeInM;
        this.dlonInMeters = dlon * degreeInM * lonScaler;
        this.cellA_m2 = dlatInMeters * dlonInMeters;
    }

    public ByteGeoGrid(ArrayList<RawDatapoint> rds, float gridRes_m,
            String name, boolean NO_SCALING, Float MIN_VALUE) {

        double latmin = Double.MAX_VALUE;
        double latmax = Double.MIN_VALUE;
        double lonmin = Double.MAX_VALUE;
        double lonmax = Double.MIN_VALUE;

        float maxVal = Float.MIN_VALUE;
        float minVal = Float.MAX_VALUE;

        if (MIN_VALUE != null) {
            minVal = MIN_VALUE;
        }

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

            if (p.value > maxVal) {
                maxVal = p.value;
            }
            if (p.value < minVal) {
                minVal = p.value;
            }
        }

        //   if (defScaler != null) {
        //   this.VAL_SCALER = defScaler;
        //       EnfuserLogger.log(Level.FINER,this.getClass(),"Byte scaling isfixed, Scaler is "+VAL_SCALER);
        //scaler evaluation  
        if (NO_SCALING) {
            this.VAL_FLAT_ADD = 0;
            this.VAL_SCALER = 1;
        } else {
            float range = maxVal - minVal;
            this.VAL_FLAT_ADD = minVal;
            this.VAL_SCALER = 250f / range;

            EnfuserLogger.log(Level.FINER,this.getClass(),"MinValue = " + minVal);
            EnfuserLogger.log(Level.FINER,this.getClass(),"maxValue = " + maxVal);
            EnfuserLogger.log(Level.FINER,this.getClass(),"Byte scaling is used! Scaler is " + VAL_SCALER);
            EnfuserLogger.log(Level.FINER,this.getClass(),"Range/flatAdd => " + range + "/" + this.VAL_FLAT_ADD);
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

        this.values = new byte[H][W];
        this.dt = rds.get(0).dt;
        varName = name;

        // init grid
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                values[h][w] = (byte) ((minVal - this.VAL_FLAT_ADD) * VAL_SCALER - 125);

            }
        }

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

            this.values[h][w] = (byte) ((p.value - this.VAL_FLAT_ADD) * VAL_SCALER - 125); // remove the baseline and scale
            if (this.values[h][w] < -125) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue " + p.value + "/" + this.values[h][w]);
                // this.values[h][w] = (byte)127;
            } //TODO 256 different values could be USED!!!!
            if (this.values[h][w] > 125) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue " + p.value + "/" + this.values[h][w]);
                //  this.values[h][w] = (byte)127;
            }
            counter++;
            // if (counter%100 ==0) EnfuserLogger.log(Level.FINER,this.getClass(),"trueValue/convertedValue "+p.value +"/"+this.values[h][w]);
        }

        double midlat = (gridBounds.latmax + gridBounds.latmin) / 2.0;
        lonScaler = Math.cos(midlat / 360 * 2 * pi);
        this.dlatInMeters = dlat * degreeInM;
        this.dlonInMeters = dlon * degreeInM * lonScaler;
        this.cellA_m2 = dlatInMeters * dlonInMeters;

        EnfuserLogger.log(Level.FINER,this.getClass(),"Added " + counter + " values to grid.");

    }

    public boolean HWok(int h, int w) {
        return GeoGrid.HWok(h, w, this.H, this.W);
    }

    public boolean containsCoordinates(double lat1, double lon1) {

        if (lat1 >= this.gridBounds.latmax || lat1 <= this.gridBounds.latmin || lon1 >= this.gridBounds.lonmax || lon1 <= this.gridBounds.lonmin) {
            //   console.out("point 1 is not covered. (lat, lon) " +lat1 +", "+lon1);
            return false;

        } else {
            return true;
        }
    }

    public void printaAtributes() {

        EnfuserLogger.log(Level.FINER,this.getClass(),"Map width: " + this.W + " height: " + this.H + " Pixels: " + this.W * this.H);
        EnfuserLogger.log(Level.FINER,this.getClass(),"Coordinate limits, latitude: " + this.gridBounds.latmin + " - " + this.gridBounds.latmax);
        EnfuserLogger.log(Level.FINER,this.getClass(),"Coordinate limits, longitude: " + this.gridBounds.lonmin + " - " + this.gridBounds.lonmax);
        EnfuserLogger.log(Level.FINER,this.getClass(),"dlat [m] / dlon [m]: " + (int) (this.dlatInMeters) + " / " + (int) (this.dlonInMeters));
        EnfuserLogger.log(Level.FINER,this.getClass(),"Cell area : " + this.cellA_m2 + " m2");
    }

    public GeoGrid convert() {

        float[][] nvals = new float[H][W];

        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                nvals[h][w] = (float) this.getValueAtIndex(h, w);

            }
        }
        EnfuserLogger.log(Level.FINER,this.getClass(),"Converting ByteGeoGrid to GeoGrid.");
        return new GeoGrid(nvals, this.dt.clone(), this.gridBounds.clone());

    }

    public double getValueAtIndex(int h, int w) {
        return scaleBack(h, w);
    }
    
    public boolean isNanAtIndex(int h, int w) {
        int temp = this.values[h][w] + 125; //reposition from -128 scale to 0 -256 scale;
        if (temp<-1) return true;
        return false;
    }

    public double nanValue=0;
    private double scaleBack(int h, int w) {
        if (VAL_SCALER == 0) {
            return this.VAL_FLAT_ADD;//singular grid
        }
        int temp = this.values[h][w] + 125; //reposition from -128 scale to 0 -256 scale;
        if (temp<-1) return nanValue;
        
        return (temp / VAL_SCALER + this.VAL_FLAT_ADD);
    }
    
    public int countNaNs() {
        int count =0;
        for (int h=0;h<H;h++) {
            for (int w =0;w<W;w++) {
                if(isNanAtIndex(h,w)) count++;
            }
        }
        return count;
    }

    public static double getValue_OOBzero(ByteGeoGrid g, double lat, double lon) {
        int h = getH(g.gridBounds, lat, g.dlat);
        int w = getW(g.gridBounds, lon, g.dlon);
        try {
            return g.scaleBack(h, w);
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

    public double getValue(double lat, double lon) {

        int h = getH(gridBounds, lat, dlat);
        int w = getW(gridBounds, lon, dlon);
        return scaleBack(h, w);
    }

    public Double getValue_excSafe(double lat, double lon) {

        int h = getH(gridBounds, lat, dlat);
        int w = getW(gridBounds, lon, dlon);
        try {
            return scaleBack(h, w);
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
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
                Logger.getLogger(ByteGeoGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(ByteGeoGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static ByteGeoGrid load(String filename) {
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
                    if (obj instanceof ByteGeoGrid) {
                        EnfuserLogger.log(Level.FINER,ByteGeoGrid.class,"GeoGrid loaded from .dat-file successfully.");
                        ByteGeoGrid gg = (ByteGeoGrid) obj;
                        return gg;
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(ByteGeoGrid.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(ByteGeoGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(ByteGeoGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

}
