/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.colmap;

import static org.fmi.aq.essentials.gispack.osmreader.colmap.GeoImage.loadFromKMZ;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.interfacing.Imager;

/**
 * This class is to extract Hue, Saturation, Brightness, Red, Green and blue
 * components out from a ColorMap.
 * The output may be an image or a gridded dataset (e.g., GeoGrid)
 * This class also reads a KMZ to an IntColorMap.
 * @author johanssl
 */
public class ColmapProcess {

    public final static int HUE=0;
    public final static int SATUR=1;
    public final static int BRIGHT=2;
    
    public final static int RED=3;
    public final static int GREEN=4;
    public final static int BLUE=5;
    
    public final static String[] COMP_NAMES = {
        "Hue",
        "Saturation",
        "Brightness",
        "Red",
        "Green",
        "Blue"
    };
    
    /**
     * Read the specified component into an array of pixels for image creation
     * and visualization.
     * @param cm the colorMap
     * @param type the component type index, one of COMP_NAMES.
     * @return int[] pixels as an Object.
     */
    public static Object getHSB_component(ColorMap cm, int type) {
        EnfuserLogger.log(Level.FINER,ColmapProcess.class,
                "Image component creation - "+ COMP_NAMES[type]);

        int[] pixels = new int[cm.W * cm.H];
        Imager im = new Imager();
        //PIXEL COLOR evaluation
        float[] ftemp = new float[3];
        int[] rgb = new int[3];
        for (int h = 0; h < cm.H; h++) {
            for (int w = 0; w < cm.W; w++) {
                int pix =0;
                boolean fromHSB =true;
                float[] hsb = cm.getHSB(h, w, ftemp,im);
                
                if (type ==BRIGHT) {
                    hsb[0] = 0;//eliminate hue
                    hsb[1] = 0;//eliminate saturation
                } else if (type == SATUR) {
                    hsb[0] = 0;//eliminate hue
                    hsb[2] = 0;//eliminate br
                } else if (type== BRIGHT) {//hue map
                     hsb[1] = 0;//eliminate satur
                     hsb[2] = 0;//eliminate br
                } else {
                    fromHSB =false;
                    rgb = cm.getRGB(h, w, rgb);
                    if (type==RED) {
                        rgb[1]=0;//disable green
                        rgb[2]=0;//disable blue
                        
                    } else if (type ==GREEN) {
                        rgb[0]=0;//disable red
                        rgb[2]=0;//disable blue
                        
                    } else {//blue
                        rgb[0]=0;//disable red
                        rgb[1]=0;//disable green
                    }
                  pix = im.RGBtoPix(rgb);  
                }
                
               if (fromHSB) pix = im.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                //colIndex has been evaluated at this point
                pixels[(h) * cm.W + (w)] = pix;

            }//width
        }//height

        Object pixelImage = im.pixelsToBufferedImage(cm.H,cm.W,pixels);
        return pixelImage;
    }
    
    /**
     * Read the specified component into a ByteGeoGrid. The component values
     * are represented in range [0,1].
     * and visualization.
     * @param cm the colorMap
     * @param type the component type index, one of COMP_NAMES.
     * @return a ByteGeoGrid
     */
   public static ByteGeoGrid getComponent_values_bgg(ColorMap cm, int type) {
        EnfuserLogger.log(Level.FINER,ColmapProcess.class,
                "Image component creation, byteGeo - "+ COMP_NAMES[type]);
        
        float[][] dat = new float[cm.H][cm.W];
        Imager im = new Imager();
        //PIXEL COLOR evaluation
        float[] ftemp = new float[3];
        int[] rgb = new int[3];
        for (int h = 0; h < cm.H; h++) {
            for (int w = 0; w < cm.W; w++) {

                if (type <RED) {//HSB based
                float[] hsb = cm.getHSB(h, w, ftemp,im);
                dat[h][w]=hsb[type];
                
                } else {//RGB based
                    rgb = cm.getRGB(h, w, rgb);
                    int rgb_type = type-3;
                    dat[h][w]=(float)rgb[rgb_type]/255f;//scale to [0,1]
                    
                }
            }//width
        }//height

        return new ByteGeoGrid(dat,Dtime.getSystemDate(),cm.b.clone());
    }

     /**
     * Read the specified component into a GeoGrid. The component values
     * are represented in range [0,1].
     * and visualization.
     * @param cm the colorMap
     * @param type the component type index, one of COMP_NAMES.
     * @return a GeoGrid
     */
   public static GeoGrid getComponent_values_gg(ColorMap cm, int type) {
        EnfuserLogger.log(Level.INFO,ColmapProcess.class,
                "Image component creation, geoGrid - "+ COMP_NAMES[type]);
        
        float[][] dat = new float[cm.H][cm.W];
        Imager im = new Imager();
        //PIXEL COLOR evaluation
        float[] ftemp = new float[3];
        int[] rgb = new int[3];
        for (int h = 0; h < cm.H; h++) {
            for (int w = 0; w < cm.W; w++) {

                if (type <RED) {//HSB based
                float[] hsb = cm.getHSB(h, w, ftemp,im);
                dat[h][w]=hsb[type];
                
                } else {//RGB based
                    rgb = cm.getRGB(h, w, rgb);
                    int rgb_type = type-3;
                    dat[h][w]=(float)rgb[rgb_type]/255f;//scale to [0,1]
                    
                }
            }//width
        }//height

        return new GeoGrid(dat,Dtime.getSystemDate(),cm.b.clone());
    }
    
   /**
    * Create H,S,B sub images out from the given ColorMap.
    * @param dir directory where component images are produced.
    * @param cm the colorMap
    */
    public static void HSBlayersToFile(String dir, ColorMap cm) {
        Imager im =new Imager();
        Object img = getHSB_component(cm, HUE);//cm.getHueImage();
        im.saveImage(dir, img, "hue", Imager.IMG_FILE_PNG);

        img = getHSB_component(cm, SATUR);//cm.getSaturationImage();
        im.saveImage(dir, img, "satur", Imager.IMG_FILE_PNG);

        img = getHSB_component(cm, BRIGHT);//getBRimage(cm);
        im.saveImage(dir, img, "br", Imager.IMG_FILE_PNG);

    }

    /**
     * Read a KMZ-file and cast it as an IntColorMap.
     * @param dir the directory where the kmz is.
     * @param kmzFile the local filename.
     * @return an IntColorMap.
     */
    public static IntColorMap fromKmz(String dir, String kmzFile) {
        GeoImage gi = loadFromKMZ(kmzFile, dir);
        return new IntColorMap(gi);
    }

}
