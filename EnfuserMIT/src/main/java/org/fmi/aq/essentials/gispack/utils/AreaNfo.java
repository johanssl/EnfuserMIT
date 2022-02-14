/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.utils;

import java.io.Serializable;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;

/**
 *
 * @author johanssl
 */
public class AreaNfo implements Serializable{

     private static final long serialVersionUID = 7226472294623476147L;//Important: change this IF changes are made that breaks serialization!
    public final Dtime dt;
    public final int H, W;
    public final Boundaries bounds;

    //derivitatives
    public final float res_m;
    public final float cellA_m2;
    public final double dlat, dlon;
    public final float coslat;
    public String name = "";

    public AreaNfo(Boundaries b, float res_m, Dtime dt) {
        this.bounds = b.clone();
        this.dt = dt.clone();
        //this.H =H;
        //this.W =W;
        //=========================
        int testRes = 1000;
        int[] HW = bounds.getMaxHWFromRes(testRes);
        double heightTotal_m = (bounds.latmax - bounds.latmin) * degreeInMeters;
        float cellRes_m = (float) (heightTotal_m / HW[0]);//with the tester resolution we get some pixel resolution. E.g., res_m is 500m and with the tester we get 200m

        float scaler = cellRes_m / res_m;//scaler is e.g., 200m/500m = 0.4.
        int newRes = (int) (testRes * scaler);//e.g., 1000*0.4 = 400

        HW = bounds.getMaxHWFromRes(newRes);
        H = HW[0];
        W = HW[1];

        //=========================
        double latrange = b.latmax - b.latmin;
        this.dlat = latrange / H;
        double lonrange = b.lonmax - b.lonmin;
        this.dlon = lonrange / W;

        this.cellA_m2 = b.getCellArea_km2(H, W) * 1000000;
        this.res_m = (float) (dlat * degreeInMeters);
        this.coslat = (float) Math.cos(bounds.getMidLat() * Math.PI / 180.0);
    }

    public AreaNfo(Boundaries b, int H, int W, Dtime dt) {
        this.bounds = b.clone();
        this.dt = dt.clone();
        this.H = H;
        this.W = W;

        double latrange = b.latmax - b.latmin;
        this.dlat = latrange / H;
        double lonrange = b.lonmax - b.lonmin;
        this.dlon = lonrange / W;

        this.cellA_m2 = b.getCellArea_km2(H, W) * 1000000;
        this.res_m = (float) (dlat * degreeInMeters);
        this.coslat = (float) Math.cos(bounds.getMidLat() * Math.PI / 180.0);
    }

    public AreaNfo(OsmLayer ol) {
      this(ol.b,ol.H,ol.W,Dtime.getSystemDate());
    }

    public AreaNfo(OsmLayer ol, int resDiv) {
       this(ol.b,ol.H/resDiv, ol.W/resDiv,
               Dtime.getSystemDate());
    }


    public double midlat() {
        return this.bounds.getMidLat();
    }

    public double midlon() {
        return this.bounds.getMidLon();
    }

    public double getLat(int h) {
        return GeoGrid.getLat(this.bounds, h, this.dlat);
    }

    public double getLon(int w) {
        return GeoGrid.getLon(this.bounds, w, this.dlon);
    }

    public String shortDesc() {
        String s = "{" + this.bounds.toText_fileFriendly(null) + ", " 
                + this.dt.getStringDate_YYYY_MM_DDTHH() + ", " + H + "x" 
                + W + "/" + this.res_m + "m}";
        return s;
    }

    public int getH(double lat) {
        return GeoGrid.getH(bounds, lat, dlat);
    }

    public int getW(double lon) {
        return GeoGrid.getW(bounds, lon, dlon);
    }

    public String areaCheckString(boolean addSeconds) {
        String s = bounds.toText_fileFriendly(null) + "_H" + H + "_W" + W;
        if (addSeconds) {
            s += "_s" + this.dt.systemSeconds();
        }
        return s;
    }

    public boolean oobs(int h, int w) {
      if (h<0 || h> H-1) {
          return true;
      } else if (w<0 || w> W-1) {
          return true;
      }
      return false;
    }

    public double latRange_m() {
        return (bounds.latmax - bounds.latmin) * degreeInMeters;
    }
    
     public double lonRange_m() {
        return (bounds.lonmax - bounds.lonmin) * degreeInMeters * coslat;
    }

}
