/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.colmap;

import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.ftools.FileOps;
import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * The purpose of this class is to provide an alternative read option for image
 * data that commonly uses GE kmz-files. As an option, the information can be
 * given as PNG and a CSV meta-data as well.
 *
 * @author johanssl
 */
public class GeoImageReader {

    public final static String KMZ = ".kmz";
    public final static String PNG = ".PNG";
    public final static String META = ".txt";

    /**
     * Read GeoImage instance using a file that is searched from the given
     * directory. This file can be either a .kmz-file or a combination of .PNG
     * and .txt. In case the data is being read from PNG-txt -pair, then the
     * given String identifier needs to be present in both input files.
     *
     * @param nameIdentifier identifier String that must be contained within the
     * local file name (without parents). NOTE: DO NOT use file extensions such
     * as .kmz or .PNG here!
     * @param folder directory that should contain the searched datasets.
     * @param allowPNG if true, then an optional PNG-txt read method is used in
     * case a .kmz-file was not found.
     * @return GeoImage instance. This is an intermediary data format for image
     * data with added meta-data such as the bounding box, and can be turned
     * into ColorMap.
     */
    public static GeoImage findReadFromFile(String nameIdentifier, String folder,
            boolean allowPNG) {

        GeoImage gi = null;//this is the one we return
        //first, try to find the necessary data on KMZ-format
        String file = getFileNameContainingBoth(folder, nameIdentifier, KMZ);
        EnfuserLogger.log(Level.FINER,GeoImageReader.class,
                "GeoImageReader: attempting kmz-file fetch for " + nameIdentifier);
        if (file == null) {//did not exist (kmz)
            EnfuserLogger.log(Level.FINER,GeoImageReader.class,
                    "GeoImage file (that contains: " + nameIdentifier + ", " + KMZ + ") does NOT exist at " + folder);
            if (allowPNG) {//allow ONG-csv-combination
                EnfuserLogger.log(Level.FINER,GeoImageReader.class,
                        "\t attempting a PNG-TXT read option...");
                String imfile = getFileNameContainingBoth(folder, nameIdentifier, PNG);
                String metafile = getFileNameContainingBoth(folder, nameIdentifier, META);
                if (imfile != null && metafile != null) {
                    EnfuserLogger.log(Level.FINER,GeoImageReader.class,
                            "Found a PNG and CSV-meta-file for GeoImage creation.");
                    //BufferedImage img = null;
                    try {
                        
                        //img = ImageIO.read(new File(folder + imfile));
                        //then the boundaries, that should
                        //have a simple structure at first line: latmin;latmax;lonmin;lonmax;
                        ArrayList<String> arr = FileOps.readStringArrayFromFile(folder + metafile);
                        String[] bstring = arr.get(0).split(";");
                        Boundaries b = new Boundaries(bstring, 0);
                        EnfuserLogger.log(Level.FINER,GeoImageReader.class,"Bounds read successfully.");
                        gi = new GeoImage(new File(folder + imfile), b);
                        EnfuserLogger.log(Level.FINER,GeoImageReader.class,"Image read from kmz-file.");
                        
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    //time to create the GeoImage
                    //gi = new GeoImage(img, b, folder, imfile);

                }//if ONG and CSV both exist
                else {
                    EnfuserLogger.log(Level.INFO,GeoImageReader.class,
                            "a PNG-TXT read option was not successful. one or both the required files was missing at "
                            + folder + ", or Boundaries parse operation failed for the txt-file. NameIdentifier="+nameIdentifier);
                }
            }//if allow PNG-creation route

        } else {//kmz-file exists
            gi = GeoImage.loadFromKMZ(file, folder);
        }
        return gi;
    }

    /**
     * Find a file name under "dir" that contains the test-String and ends with
     * the given extension.
     *
     * @param dir the directory
     * @param test test String that must be contained
     * @param extension the file extension in which the file must end with. When
     * matching the extension this and the file name are converted into
     * lower-case, which means that e.g., both ".png" and ".PNG" will be
     * accepted.
     * @return local file name. Returns null if not found.
     */
    public static String getFileNameContainingBoth(String dir, String test, String extension) {

        File f = new File(dir);
        File[] ff = f.listFiles();
        String ext = extension.toLowerCase();

        for (File ft : ff) {
            if (ft.getName().contains(test)
                    && ft.getName().toLowerCase().endsWith(ext)) {
                return ft.getName();
            }
        }
        return null;
    }

}
