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
public class IntColorMap extends ColorMap implements Serializable {

    private static final long serialVersionUID = 3526472293822776147L;//Important: change this IF changes are made that breaks serialization! 
    byte[][] dat_hw_rgb;//rgb dimension is resolved by having H-dim increased with a factor of 3. Red is first, then green, then blue.

    static final int HMOD = 3;
    static final int[] HMODS = {0, 1, 2};

    public IntColorMap() {

    }
    
    public static IntColorMap readFromKmz(String dir, String name) {
        GeoImage gi= GeoImage.loadFromKMZ(name, dir);
        return new IntColorMap(gi);
    }

    public IntColorMap(GeoImage gi) {
        super(gi);

        this.dat_hw_rgb = new byte[H * HMOD][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                int[] rgb = gi.getRGB(h, w);

                //short col = transformShort(rgb);
                byte[] rgbs = transform(rgb);
                for (int hmods : HMODS) {
                    this.dat_hw_rgb[h + H * hmods][w] = rgbs[hmods];
                }

            }//for w
        }//fow h
    }

    private byte[] transform(int[] rgb) {
        byte[] bb = new byte[3];
        for (int i = 0; i < bb.length; i++) {
            bb[i] = (byte) (rgb[i] - 128);
            if (bb[i] < Byte.MIN_VALUE) {
                bb[i] = Byte.MIN_VALUE;
            }
        }
        return bb;
    }

    @Override
    public int[] getRGB(int h, int w, int[] temp) {
 
        if (temp == null) {
            temp = new int[3];
        }

        temp[0] = this.dat_hw_rgb[h][w] + 128;
        temp[1] = this.dat_hw_rgb[h + H][w] + 128;
        temp[2] = this.dat_hw_rgb[h + 2 * H][w] + 128;

        return temp;

    }

    public byte[] getRaw(int h, int w) {
        byte[] raw = new byte[3];
        for (int hmod : HMODS) {
            raw[hmod] = this.dat_hw_rgb[h + H * hmod][w];
        }
        return raw;
    }

    @Override
    public float[] getHSB(int h, int w, float[] temp, Imager im) {

        int r = this.dat_hw_rgb[h][w] + 128;
        int g = this.dat_hw_rgb[h + H][w] + 128;
        int b = this.dat_hw_rgb[h + 2 * H][w] + 128;

        float[] hsb = im.RGBtoHSB(r, g, b, temp);
        return hsb;
    }

    @Override
    public ColorMap subset(Boundaries subb) {

        int minH = getH(this.b, subb.latmax, this.dlat);
        int maxH = getH(this.b, subb.latmin, this.dlat);

        int minW = getW(this.b, subb.lonmin, this.dlon);
        int maxW = getW(this.b, subb.lonmax, this.dlon);

        int newH = (maxH - minH);
        int newW = (maxW - minW);

        IntColorMap ico = new IntColorMap();
        ico.b = subb.clone();
        ico.dlat = this.dlat;
        ico.dlon = this.dlon;
        ico.H = newH;
        ico.W = newW;
        ico.dat_hw_rgb = new byte[newH * HMOD][newW];

        for (int hmod : HMODS) {//0,1,2 for r,g,b  
            for (int h = 0; h < newH; h++) {
                for (int w = 0; w < newW; w++) {

                    try {//this is a bit nasty, but the term hmod*H takes care of swithing through r,g,b
                        ico.dat_hw_rgb[h + hmod * ico.H][w] = this.dat_hw_rgb[h + this.H * hmod + minH][w + minW];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        ico.dat_hw_rgb[h][w] = this.dat_hw_rgb[0][0];//fill something at least   
                    }
                }

            }
        }//for hmods

        return ico;
    }

}
