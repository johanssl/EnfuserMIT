/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.colmap;

import static org.fmi.aq.enfuser.ftools.FileOps.readStringArrayFromFile;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.Zipper.UnZip;
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author johanssl
 */
public class GeoImage {

    public Boundaries b;
    public String absolutePath;
    public String name;

    public int[][] colInts;
    public int W;
    public int H;
    public final Imager im;
    public GeoImage(File imFile, Boundaries b) {
        this.b = b;
        this.absolutePath = imFile.getAbsolutePath();
        this.name = imFile.getName();
        
        im = new Imager();
        int[] hw = im.openPNG(imFile.getAbsolutePath());
        this.H = hw[0];//img.getHeight();
        this.W = hw[1];//img.getWidth();
        
        this.colInts = new int[H][W];
        int pixels[] = im.getPixels();//new int[W * H];
        //img.getRGB(0, 0, W, H, pixels, 0, W);

        int h = 0;
        int w = -1;
        for (int k = 0; k < pixels.length; k++) {
            // update h-w index
            w++;
            if (w >= W) {
                w = 0;
                h++;
            }

            colInts[h][w] = pixels[k];
        }//for pix  
    }

    public int[] getRGB(int h, int w) {
        int pix = this.colInts[h][w];
        int red = ((pix >> 16) & 0xff);
        int green = ((pix >> 8) & 0xff);
        int blue = (pix & 0xff);
        int[] temp = {red, green, blue};
        return temp;
    }

    public static float[] RGBtoHSB(int[] rgb, Imager im) {
        float[] hsb = im.RGBtoHSB(rgb[0], rgb[1], rgb[2], null);
        return hsb;
    }

    public static GeoImage loadWithIdentifier(String id, String dir) {
        return GeoImageReader.findReadFromFile(id, dir,
                true);
    }

    public static GeoImage loadFromKMZ(String kmzFile, String dir) {

        Boundaries b = null;
        GeoImage gi=null;
        //open zip file
        EnfuserLogger.log(Level.FINER,GeoImage.class,"Unzip: " + dir + kmzFile + " => " + dir);
        ArrayList<String> arr = UnZip(dir + kmzFile, dir);
        for (String unzipped : arr) {

            EnfuserLogger.log(Level.FINER,GeoImage.class,unzipped);

            if (unzipped.contains(".kml")) {

                //get bounds
                ArrayList<String> docLines = readStringArrayFromFile(dir + unzipped);
                String fullDoc = "";
                for (String s : docLines) {
                    fullDoc += s;
                }

                double latmax = Double.valueOf(getBetween(fullDoc, "<north>", "</north>"));
                double latmin = Double.valueOf(getBetween(fullDoc, "<south>", "</south>"));
                double lonmax = Double.valueOf(getBetween(fullDoc, "<east>", "</east>"));
                double lonmin = Double.valueOf(getBetween(fullDoc, "<west>", "</west>"));
                b = new Boundaries(latmin, latmax, lonmin, lonmax);

                EnfuserLogger.log(Level.FINER,GeoImage.class,"MaskBounds read from kmz-file.");
            } else if (unzipped.contains(".PNG") || unzipped.contains(".png")) {

                
                    File filen = new File(dir+unzipped);
                    gi = new GeoImage(filen, b);
                    //img = ImageIO.read(new File(dir + unzipped));

                    EnfuserLogger.log(Level.FINER,GeoImage.class,"Image read from kmz-file.");
               
            }

            //clean-up
            File f = new File(dir + unzipped);
            f.delete();

        }

        return gi;//new GeoImage(img, b, dir + kmzFile, kmzFile);
    }


}
