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
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author johanssl
 */
public class ByteColorMap extends ColorMap implements Serializable {

    private static final long serialVersionUID = 7526472293822776142L;//Important: change this IF changes are made that breaks serialization! 
    byte[][] dat_hw;

    public ByteColorMap(GeoImage gi) {
        super(gi);

        this.dat_hw = new byte[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                int[] rgb = gi.getRGB(h, w);
                float[] hsb = GeoImage.RGBtoHSB(rgb,gi.im);

                this.dat_hw[h][w] = transform(hsb);

                if (h % 100 == 0 && w % 100 == 0) {
                    int[] conv = getRGB(h, w, null);
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t RGB = " + rgb[0] + " " + rgb[1] + " " 
                            + rgb[2] + " => " + "" + " => " + conv[0] + " " + conv[1] + " " + conv[2]);
                }

            }//for w
        }//fow h

    }

    @Override
    public int[] getRGB(int h, int w, int[] temp) {

        float[] hsb = this.getHSB(h, w, null,null);
        int red = (int)(hsb[2]*254);//((pix >> 16) & 0xff);
        int green = (int)(hsb[2]*254);//((pix >> 8) & 0xff);
        int blue = (int)(hsb[2]*254);//(pix & 0xff);
        int[] c = {red, green, blue};

        return c;

    }

    @Override
    public float[] getHSB(int h, int w, float[] temp, Imager im) {

        byte b = this.dat_hw[h][w];
        //EnfuserLogger.log(Level.FINER,this.getClass(),"raw byte = " + b);
        int br_level = ((b + 100) / 50);//0-4

        float br = br_level / 4f;
        //EnfuserLogger.log(Level.FINER,this.getClass(),"\t br_level is " + br_level +", which turns to " + br);

        b = (byte) Math.abs(b);

        int satb = ((b % 50) / 10);// eg. -133 => 33 =>3
        float sat = satb / 5f;
        // EnfuserLogger.log(Level.FINER,this.getClass(),"\t sat level is " + (b%50)/10 +", which turns to " + sat);

        float hue = (b % 10) / 10f;
        // EnfuserLogger.log(Level.FINER,this.getClass(),"\t hue is " + hue);

        if (temp == null) {
            temp = new float[3];
        }
        temp[0] = hue;
        temp[1] = sat;
        temp[2] = br;
        return temp;
    }

    /**
     * 10 different hue values 5 different saturation values 4 brightness levels
     *
     * @param hsb
     * @return
     */
    private byte transform(float[] hsb) {
        float br = hsb[2];
        int base = -100 + (int) (br * 4) * 50;//max 100

        int hueb = (int) (hsb[0] * 10);
        if (hueb > 9) {
            hueb = 9;
        }

        int satb = (int) (hsb[1] * 50);//0-50
        satb = (int) (satb / 10) * 10;//0,10,20,30,40,50
        if (satb > 40) {
            satb = 40;
        }

        byte finalb = (byte) (base + hueb + satb);
        return finalb;

    }

    @Override
    public ColorMap subset(Boundaries subb) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
