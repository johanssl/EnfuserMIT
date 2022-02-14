/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.colmap;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class BWkmzGrid {

    public static ByteGeoGrid clipFromKMZ(String dir, Boundaries bt, String id) {
        //BWGEO_0_100_...kmz

        GeoImage gi2 = GeoImage.loadWithIdentifier(id, dir);//GeoImage.fromKMZ(nam, dir);
        if (gi2 == null) {
            return null;
        }

        MonoColorMap mcm = new MonoColorMap(gi2);
        EnfuserLogger.log(Level.FINER,BWkmzGrid.class,"Black & White -colormap (BW) loaded successfully.");
        AreaNfo in = new AreaNfo(mcm.b, mcm.H, mcm.W, Dtime.getSystemDate());
        float res_m = in.res_m;//now we have the original data's resolution

        String[] temp = gi2.name.split("_");//nam.split("_");
        double scaler = 1;
        double offset = 0;
        try {
            scaler = Double.valueOf(temp[3]);
            offset = Double.valueOf(temp[2]);
        } catch (Exception e) {

        }
        AreaNfo inTarget = new AreaNfo(bt, res_m, Dtime.getSystemDate());
        int H = inTarget.H;
        int W = inTarget.W;//no we have the dimensions for the clipped data.
        float[][] dat = new float[H][W];
        GeoGrid g = new GeoGrid(dat, in.dt, bt);//using the target bounds

        //iterating over Target area
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                double lat = inTarget.getLat(h);
                double lon = inTarget.getLon(w);
                //get brightness
                int val = 0;
                try {
                    int hh = in.getH(lat);
                    int ww = in.getW(lon); //this should be the cell in our BW image
                    val = mcm.getBrightness(hh, ww);

                } catch (IndexOutOfBoundsException e) {
                }

                dat[h][w] = (float) (val * scaler + offset);
            }
        }//for h

        ByteGeoGrid bg = new ByteGeoGrid(dat, g.dt, g.gridBounds);
        return bg;
    }

    public static ByteGeoGrid findAndclipFromKMZ(String dir, String mustContain, Boundaries bt) {
        return clipFromKMZ(dir, bt, mustContain);
    }

}
