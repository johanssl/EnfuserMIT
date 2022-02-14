/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.geoGrid;

import java.io.Serializable;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johanssl
 */
public class IntGrid implements Serializable {

    private static final long serialVersionUID = 7525471294622776147L;//Important: change this IF changes are made that breaks serialization!
    public int[][] values;

    public final Boundaries gridBounds;// the grid
    public final int H, W;

    public final double dlat, dlon;
    public final double latRange, lonRange;
    public Dtime dt;
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
    public IntGrid(String name, int[][] vals, Dtime dt, Boundaries bounds) {
        this(vals, dt, bounds.clone(name));
    }

    public boolean containsCoordinates(double lat1, double lon1) {

        if (lat1 >= this.gridBounds.latmax || lat1 <= this.gridBounds.latmin
                || lon1 >= this.gridBounds.lonmax || lon1 <= this.gridBounds.lonmin) {

            return false;

        } else {
            return true;
        }
    }

    public IntGrid(int[][] vals, Dtime dt, Boundaries bounds) {

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

    public int getValueAtIndex(int h, int w) {
        return this.values[h][w];
    }

    public int getValue(double lat, double lon) {

        int h = getH(gridBounds, lat, dlat);
        int w = getW(gridBounds, lon, dlon);

        return this.values[h][w];
    }

    public void saveToBinary(String fileName) {

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

    public static IntGrid load(String filename) {
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
                    if (obj instanceof IntGrid) {
                        IntGrid gg = (IntGrid) obj;
                        return gg;
                    }
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
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

}
