/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.essentials.gispack.osmreader.colmap.ColorMap;
import org.fmi.aq.essentials.gispack.osmreader.core.AllTags;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.DATA_ROW;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.IND_COLOR;

/**
 *This class is to handle Image creation and reading (e.g., java.awt work)
 * @author johanssl
 */
public class Imager {
 
    //fileTypes
    public static final int IMG_FILE_PNG = 0;
    public static final int IMG_FILE_JPG = 1;
    public static final String[] fileTypes = {"PNG", "JPG"};

    private BufferedImage img;
    int imgW=-1;
    int imgH=-1;

    public Imager() {
        
    }



   /**
   * Read pixel rgb-values into integer array.
   * @param fname image file name
   * @return 
   */
    public int[] openPNG(String fname) {
        try {
            img = ImageIO.read(new File(fname));
            imgH=img.getHeight();
            imgW=img.getWidth();
            
            return new int[]{imgH,imgW};
        } catch (IOException ex) {
            Logger.getLogger(Imager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }


    public int[] getPixels() {
        int pixels[] = new int[imgW * imgH];
        img.getRGB(0, 0, imgW, imgH, pixels, 0, imgW);
        return pixels;
    }

    /**
 * Convert RGB in [0,255] space into HSB in [0,1] space.
 * @param r red component
 * @param g green component
 * @param b blue component
 * @param temp a temporary int[] to hold r,g,b. can be null.
 * @return float[3] HSB
 */
    public float[] RGBtoHSB(int r, int g, int b, float[] temp) {
       return Color.RGBtoHSB(r, g, b, temp);
    }

    public HashMap<Byte, Color> getOsmColorHash() {
        AllTags at = new AllTags();
        HashMap<Byte, Color> cols = new HashMap<>();

        for (Integer in : at.byType.keySet()) {
            String[][] elem = at.byType.get(in);
            String[] colLine = elem[DATA_ROW][IND_COLOR].split(",");

            int r = Integer.valueOf(colLine[0]);
            int g = Integer.valueOf(colLine[1]);
            int b = Integer.valueOf(colLine[2]);

            Color c = new Color(r, g, b);
            cols.put(in.byteValue(), c);
        }

        return cols;
    }

 /**
 * Convert H,S,B in [0,1] space into 32-bit RGB integer.
 * @param h hue
 * @param s saturation
 * @param b brightness
 * @return  32-bit RGB integer
 */
    public int HSBtoRGB(float h, float s, float b) {
       return Color.HSBtoRGB(h, s, b);
    }

    public int RGBtoPix(int[] rgb) {
    int Red = rgb[0];
    int Green = rgb[1];
    int Blue = rgb[2];
    Red = (Red << 16) & 0x00FF0000; //Shift red 16-bits and mask out other stuff
    Green = (Green << 8) & 0x0000FF00; //Shift Green 8-bits and mask out other stuff
    Blue = Blue & 0x000000FF; //Mask out anything not blue.

    return 0xFF000000 | Red | Green | Blue; //0xFF000000 for 100% Alpha. Bitwise OR everything together.

    }


    public Object pixelsToBufferedImage(int H, int W, int[] pixels) {
                
        BufferedImage pixelImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        pixelImage.setRGB(0, 0, W, H, pixels, 0, W);
        return pixelImage;
    }


    public void saveImage(String dir, Object img, String name, int IMG_FORMAT) {
                String fullName = dir + name + "." + fileTypes[IMG_FORMAT];

        try {
            // Save as new values_img
            ImageIO.write((BufferedImage)img, fileTypes[IMG_FORMAT], new File(fullName));
        } catch (IOException ex) {
            Logger.getLogger(Imager.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void dataToImageFrame(float[][] dat, Object bounds, Object en,
            String visOpsString, boolean exitOnClose) {
        
    }

    public void imageToFrame(BufferedImage buff, boolean exitOnClose) {
        JFrame frame = new JFrame();
            JLabel label = new JLabel(new ImageIcon(buff));
            frame.getContentPane().add(label, BorderLayout.CENTER);
            frame.pack();
            frame.setVisible(true);
            if (exitOnClose)frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public Object getScaledImage(ColorMap cm, int H, int W) {
                int[] pixels = new int[W * H];

        float h_frac = (float) cm.H / H;
        float w_frac = (float) cm.W / W;
        boolean noScaling = (H == cm.H && W == cm.W);

        int[] itemp = new int[3];

        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                int h_cm = (int) (h * h_frac);
                int w_cm = (int) (w * w_frac);
                if (noScaling) {
                    h_cm = h;
                    w_cm = w;
                }

                try {
                    int[] rgb = cm.getRGB(h_cm, w_cm, itemp);
                    Color col = new Color(rgb[0],rgb[1],rgb[2]);//cm.getColor(h_cm, w_cm, itemp);
                    pixels[(h) * W + (w)] = col.getRGB();
                } catch (Exception e) {

                }

            }//width
        }//height

        BufferedImage pixelImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        pixelImage.setRGB(0, 0, W, H, pixels, 0, W);

        return pixelImage;
    }

    public float[][] getRasterDataFromLoaded() {
        Raster raster = img.getData();
        float[][] dat = new float[imgH][imgW];
         
        for (int h = 0; h < imgH; h++) {
            for (int w = 0; w < imgW; w++) {
                float f = raster.getSampleFloat(w, h, 0);
                dat[h][w]=f;
            }
        }
        return dat;
    }

 /**
 * To be used after a model run, create a collection
 * of time series plots for measured vs. predicted. The output should be PNG
 * images at the operational out directory under /plots/. 
 * @param ops FusionOptions
 */
    public void createWeeklyPlots(Object ops) {
    }


     public static boolean canVisualizeEnfuserOutput() {
        return false;
    }

    public static void graphicEnfuserOutput(FusionCore ens, ArrayList<AreaFusion> afs,
            ArrayList<Integer> nonMet_typeInts) {

    }

      /**
     * Visualize the given raster data and store a PNG to the given directory.
     * @param dir the directory for PNG creation
     * @param g the data to be visualized
     * @param styleTag a tag that is used to fetch visualization instructions
     * from visualizationInstructions.csv
     * @param txt [fileNamePart, above color bar, header]
     * @param mp Maps for background rendering
     * @param toPane if true, then the visualization is shown in a pop-up JPanel.
     * @param kmz if true then a KMZ file is produced
     * @param customMinMax in non-null, then a custom min,max range is set.
     * @param curver if non-null then this gives the gradient progression curvature.
     * 1: linear, 2: emphasizes low values. 0.5: emphasize large values.
     */
    public static void visualize(String dir, GeoGrid g, String styleTag,
            String[] txt, MapPack mp, boolean toPane, boolean kmz,
            double[] customMinMax, Double curver) {

    }

    

}
