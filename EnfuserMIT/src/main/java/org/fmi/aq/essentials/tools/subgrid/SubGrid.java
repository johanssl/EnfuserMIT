/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.tools.subgrid;

import java.io.Serializable;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.essentials.geoGrid.ByteGeoGrid.degreeInM;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import static java.lang.Math.PI;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A compact grid structure - grids within a grid. For data that has similar
 * values being clustered (variability is sparse and infrequent), this can save
 * a lot of memory.
 *
 * This abstract class defines the common fields and methods.
 *
 * @author johanssl
 */
public abstract class SubGrid implements Serializable {

    private static final long serialVersionUID = 5526472293822776147L;//Important: change this IF changes are made that breaks serialization! 
    public int OOBS = -1234567;
    public int DONE = -1234566;
    public int NOT_DONE = -1234555;

    public final Boundaries b;// the grid

    public final int X;//internal res (X x X)

    final double latRange, lonRange;
    final public Dtime dt;

    public String varName;

    //sparse res
    public final int H_s, W_s;
    public final double dlat_s, dlon_s;
    public final double dlatInMeters_s;
    public final double dlonInMeters_s;
    //fine res
    public final int H_fine, W_fine;
    public final double dlat_fine, dlon_fine;
    public final double dlatInMeters_fine;
    public final double dlonInMeters_fine;

    public final double lonScaler;
    public final double cellA_m;

    /**
     * Setup the common features of this object.
     *
     * @param name name of the grid.
     * @param H h-dimension (fine/max resolution)
     * @param W w-dimension (fine/max resolution)
     * @param dt time attribution
     * @param bounds geo area of the data.
     * @param X sparsing seed for sub-grids (which are X times X)
     */
    public SubGrid(String name, int H, int W, Dtime dt, Boundaries bounds, int X) {
        this(H, W, dt, bounds.clone(name), X);
    }

    public boolean containsCoordinates(double lat1, double lon1) {

        if (lat1 >= this.b.latmax || lat1 <= this.b.latmin || lon1 >= this.b.lonmax || lon1 <= this.b.lonmin) {
            //   console.out("point 1 is not covered. (lat, lon) " +lat1 +", "+lon1);
            return false;

        } else {
            return true;
        }
    }

    public SubGrid(int H, int W, Dtime dt, Boundaries bounds, int X) {

        this.b = bounds;
        this.X = X;

        H_fine = H;
        W_fine = W;

        H_s = H / X;
        W_s = W / X;

        this.dt = dt;

        this.latRange = b.latmax - b.latmin;
        this.lonRange = b.lonmax - b.lonmin;

        this.dlat_s = (latRange) / (double) H_s;
        this.dlon_s = (lonRange) / (double) W_s;
        this.dlat_fine = (latRange) / (double) H_fine;
        this.dlon_fine = (lonRange) / (double) W_fine;

        varName = bounds.name;
        double midlat = (b.latmax + b.latmin) / 2.0;
        lonScaler = Math.cos(midlat / 360 * 2 * PI);
        this.dlatInMeters_fine = dlat_fine * degreeInM;
        this.dlonInMeters_fine = dlon_fine * degreeInM * lonScaler;
        this.cellA_m = dlatInMeters_fine * dlonInMeters_fine;

        this.dlatInMeters_s = dlat_s * degreeInM;
        this.dlonInMeters_s = dlon_s * degreeInM * lonScaler;

    }

    public abstract int values(int h, int w);

    public int getValueAtIndex(int h, int w) {
        return this.values(h, w);
    }

    public int getValueAtIndex_oobSafe(int h, int w) {
        if (h < 0) {
            return OOBS;
        }
        if (w < 0) {
            return OOBS;
        }
        return this.values(h, w);
    }

    public abstract int changeValueAt(int h, int w, int val);

    /**
     * Go over the sub grid and compress all cells than can be compressed.
     */
    public abstract void checkCompress();

    public int getValue(double lat, double lon) {

        int h = getH(lat);
        int w = getW(lon);

        return this.values(h, w);
    }

    public int getValue_closest(double lat, double lon) {

        int h = getH(lat);
        int w = getW(lon);

        if (h < 0) {
            h = 0;
        }
        if (h >= H_fine) {
            h = H_fine - 1;
        }
        if (w < 0) {
            w = 0;
        }
        if (w >= W_fine) {
            w = W_fine - 1;
        }

        return this.values(h, w);
    }

    public Integer getValueAt_exSafe(double lat, double lon) {

        try {
            return this.getValue(lat, lon);
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
                Logger.getLogger(SubIntGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SubIntGrid.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static SubGrid load(String filename) {
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
                    if (obj instanceof SubIntGrid) {
                        EnfuserLogger.log(Level.FINER,SubGrid.class,"SubIntGrid loaded from .dat-file successfully.");
                        SubIntGrid gg = (SubIntGrid) obj;
                        return gg;
                    } else if (obj instanceof SubByteGrid) {
                        EnfuserLogger.log(Level.FINER,SubGrid.class,"SubByteGrid loaded from .dat-file successfully.");
                        SubByteGrid gg = (SubByteGrid) obj;
                        return gg;
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(SubIntGrid.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(SubIntGrid.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SubIntGrid.class.getName()).log(Level.SEVERE, null, ex);
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
        return HWok(h, w, this.H_fine, this.W_fine);
    }

    public int getH(double lat) {
        return (int) ((b.latmax - lat) / this.dlat_fine);
    }

    public int getW(double lon) {
        return (int) ((lon - b.lonmin) / dlon_fine);
    }

    public double getLat(int h) {
        return b.latmax - h * dlat_fine;
    }

    public double getLon(int w) {
        return b.lonmin + w * dlon_fine;
    }

}
