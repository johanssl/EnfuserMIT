/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.colmap;

import org.fmi.aq.essentials.geoGrid.Boundaries;

import java.io.Serializable;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FastMath;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author johanssl
 */
public abstract class ColorMap implements Serializable {

    private static final long serialVersionUID = 7526472293822776146L;//Important: change this IF changes are made that breaks serialization! 
    public Boundaries b;
    public double dlat;
    public double dlon;
    public int W;
    public int H;

    public ColorMap(GeoImage gi) {
        this.b = gi.b;
        this.dlat = (b.latmax - b.latmin) / gi.H;
        this.dlon = (b.lonmax - b.lonmin) / gi.W;

        this.H = gi.H;
        this.W = gi.W;
    }

    public ColorMap() {

    }
    
    public double dlatInMeters() {
       return dlat*degreeInMeters; 
    }
    
    public double dlonInMeters() {
        return dlat*degreeInMeters*FastMath.FM.getFastLonScaler(b.getMidLat());
    }
    
    /**
     * Check whether these given indices are out of bounds.
     * @param h h index
     * @param w w index
     * @return true if out of bounds.
     */
    public boolean oobs(int h, int w) {
      if (h<0 || h> H-1) {
          return true;
      } else if (w<0 || w> W-1) {
          return true;
      }
      return false;
    }
    
        /**
     * Check whether these given coordinates are out of bounds.
     * @param lat latitude
     * @param lon longitude
     * @return true if out of bounds.
     */
    public boolean oobs_latlon(double lat, double lon) {
        int h = GeoGrid.getH(b, lat, this.dlat);
        int w = GeoGrid.getW(b, lon,this.dlon);
        return oobs(h,w);
    }
    
     public  int[] getRGB_latlon(double lat, double lon, int[] temp) {
        int h = GeoGrid.getH(b, lat, this.dlat);
        int w = GeoGrid.getW(b, lon,this.dlon);
        return getRGB(h, w, temp);
     }

    public float[] getHSB_latlon(double lat, double lon, float[] temp, Imager im) {
        int h = GeoGrid.getH(b, lat, this.dlat);
        int w = GeoGrid.getW(b, lon,this.dlon);
        return getHSB(h, w, temp, im);
    }

    public abstract ColorMap subset(Boundaries subb);

    public abstract int[] getRGB(int h, int w, int[] temp);

    public abstract float[] getHSB(int h, int w, float[] temp, Imager im);

    public static Object getScaledImage(ColorMap cm) {
        return getScaledImage(cm, cm.H, cm.W);
    }

    public static Object getScaledImage(ColorMap cm, int H, int W) {

        EnfuserLogger.log(Level.FINER,ColorMap.class,"Get Scaled image...");
        return new Imager().getScaledImage(cm,H,W);

    }

}
