/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.colmap;

import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.io.Serializable;
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author johanssl
 */
public class MonoColorMap extends ColorMap implements Serializable {

    private static final long serialVersionUID = 7196472293822776147L;//Important: change this IF changes are made that breaks serialization!  
    public byte[][] dat_hw_rgb;

    public MonoColorMap() {

    }
    
    public static MonoColorMap readFromKmz(String dir, String name) {
        GeoImage gi= GeoImage.loadFromKMZ(name, dir);
        return new MonoColorMap(gi);
    }

    public MonoColorMap(GeoImage gi) {
        super(gi);

        this.dat_hw_rgb = new byte[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                int[] rgb = gi.getRGB(h, w);
                this.dat_hw_rgb[h][w] = (byte) (rgb[0] - 128);

                if (h % 100 == 0 && w % 100 == 0) {
                    int[] conv = getRGB(h, w, null);
                }

            }//for w
        }//fow h
    }

    @Override
    public int[] getRGB(int h, int w, int[] temp) {
        byte bb = this.dat_hw_rgb[h][w];
        if (temp == null) {
            temp = new int[3];
        }

        temp[0] = bb + 128;
        temp[1] = bb + 128;
        temp[2] = bb + 128;

        return temp;

    }

    @Override
    public float[] getHSB(int h, int w, float[] temp, Imager im) {
        byte bb = this.dat_hw_rgb[h][w];
        float[] hsb = im.RGBtoHSB(0, 0, bb + 128, temp);

        return hsb;
    }

    @Override
    public MonoColorMap subset(Boundaries subb) {

        int minH = getH(this.b, subb.latmax, this.dlat);
        int maxH = getH(this.b, subb.latmin, this.dlat);

        int minW = getW(this.b, subb.lonmin, this.dlon);
        int maxW = getW(this.b, subb.lonmax, this.dlon);

        int newH = (maxH - minH);
        int newW = (maxW - minW);

        MonoColorMap ico = new MonoColorMap();
        ico.b = subb.clone();
        ico.dlat = this.dlat;
        ico.dlon = this.dlon;
        ico.H = newH;
        ico.W = newW;
        ico.dat_hw_rgb = new byte[newH][newW];

        for (int h = 0; h < newH; h++) {
            for (int w = 0; w < newW; w++) {
                try {
                    ico.dat_hw_rgb[h][w] = this.dat_hw_rgb[h + minH][w + minW];
                } catch (ArrayIndexOutOfBoundsException e) {
                    ico.dat_hw_rgb[h][w] = this.dat_hw_rgb[0][0];//fill something at least   
                }
            }
        }

        return ico;
    }

    public int getBrightness(int h, int w) {
        try {
            return this.dat_hw_rgb[h][w] + 128;
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    public int getBrightness(double lat, double lon) {
        int h = getH(this.b, lat, this.dlat);
        int w = getW(this.b, lon, this.dlon);
        return getBrightness(h, w);
    }

}
