/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.utils;

import org.fmi.aq.essentials.date.Dtime;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import static org.fmi.aq.essentials.plotterlib.Visualization.FileOps.deleteFile;
import static org.fmi.aq.essentials.plotterlib.Visualization.FileOps.lines_to_file;

/**
 *
 * @author johanssl
 */
public class Tools {

    //This method prints recieved messages hourly counters to message.csv file
    public static void StringsToFile(String fileName, ArrayList<String> lines, boolean append) {
        String n = System.getProperty("line.separator");
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(fileName, append));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                out.write(line + n);
            }

            out.close();
        } catch (IOException e) {
        }

    }

    public static Dtime sysTime() {
        return Dtime.getSystemDate_utc(false, Dtime.ALL);
    }

//this tool method edits the number of decimals of d2 double
//int precision is the number of decimals wanted
    public static double editPrecision(double value, int precision) {
        
        
        int prec = (int) Math.pow(10, precision);
        int temp = (int) (prec * value);
        double result = (double) temp / prec;
        return result;
    }

    /**
     * Fetch String that lies between identifiers.
     *
     * @param full Text that content is searched from
     * @param before MUST BE UNIQUE! a subString that is known to occur before
     * target
     * @param after a subString that is known to occur after the target
     * @return target String
     */
    public static String getBetween(String full, String before, String after) {
        //  "<img class=\"leaflet-tile leaflet-tile-loaded\" src=\"./OpenStreetMap_files/4738(7).png\" style=\"height: 256px; width: 256px; left: 339px; top: 257px;\">\n"; 
        //before = "width:
        //after = px;

        try {
            String[] temp = full.split(before); // temp[1] = : 256px; left: -1197px; top: 2561px;">
            String[] temp2 = temp[1].split(after); //temp2[0] =  256;
            return temp2[0];
        } catch (ArrayIndexOutOfBoundsException w) {
            return null;
        }
    }

    public static int cropLongToInteger(long l) {
        if (l < 0) {

            l *= -1;

            if (l < Integer.MAX_VALUE) {//simplest case
                return (int) l * -1;
            } else {//the number exceeds int max value so there is at least 10 digits
                //take the 9 last digits so there are 999 999 999 unique numbers to be had
                String s = l + "";
                int start = s.length() - 9;
                String sub = s.substring(start, s.length());
                return Integer.valueOf(sub) * -1;
            }

        } else {// l GE 0

            if (l < Integer.MAX_VALUE) {//simplest case
                return (int) l;
            } else {//the number exceeds int max value so there is at least 10 digits
                //take the 9 last digits so there are 999 999 999 unique numbers to be had
                String s = l + "";
                int start = s.length() - 9;
                String sub = s.substring(start, s.length());
                return Integer.valueOf(sub);
            }

        }

    }
    
        /**
     * Get an array of H-W indices for a cross-shape that is of the size 2r x 2r.
     * The content specified h,w with respect to the center point of the square.
     * At the center h=w =0.
     * Main use case is to iterate over a grid and check content that is at
     * most R cells away from a given origin.
     * @param r the radius of the square for which indices are provided.
     * @param arr the array where indices are added. If null, then a new instance
     * is created. If the given array has content it will be cleared.
     * @return an ArrayList of indices [h,w].
     */
    public static ArrayList<int[]> getCrossShapeIndices(int r, ArrayList<int[]> arr) {

        if (arr == null) {
            arr = new ArrayList<>();
        } else {
            arr.clear();
        }

        if (r == 0) {
            arr.add(new int[]{0, 0});
            return arr;
        }

        // e.g. r = 1
        for (int w = -r; w <= r; w++) { // -1 to 1
            arr.add(new int[]{r, w}); // up --, adds (1,-1), (1,0), (1,1)
            arr.add(new int[]{-r, w}); // down --, adds (-1,-1), (-1,0), (-1,-1)

        }

        for (int h = -r + 1; h <= r - 1; h++) { // -1 to 1
            arr.add(new int[]{h, r}); // right |, adds (0,r)
            arr.add(new int[]{h, -r}); // left | , adds (0,-r)

        }

        return arr;
    }
    
    
    public static void produceGEpins(String rootdir, String fname, ArrayList<String[]> name_latlon) {
        EnfuserLogger.log(Level.FINER,Tools.class,"Producing Google Earth pins for hotspots...");
        String n = System.getProperty("line.separator");

        String o = "<?xml version='1.0' encoding='UTF-8'?>" + n
                + "<kml xmlns='http://www.opengis.net/kml/2.2' xmlns:gx='http://www.google.com/kml/ext/2.2' xmlns:kml='http://www.opengis.net/kml/2.2' xmlns:atom='http://www.w3.org/2005/Atom'>" + n
                + "<Document>" + n
                + "	<name>" + fname + ".kmz" + "</name>" + n
                + "	<Style id='s_ylw-pushpin'>" + n
                + "		<IconStyle>" + n
                + "			<scale>1.1</scale>" + n
                + "			<Icon>" + n
                + "				<href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>" + n
                + "			</Icon>" + n
                + "			<hotSpot x='20' y='2' xunits='pixels' yunits='pixels'/>" + n
                + "		</IconStyle>" + n
                + "	</Style>" + n
                + "	<Style id='s_ylw-pushpin_hl'>" + n
                + "		<IconStyle>" + n
                + "			<scale>1.3</scale>" + n
                + "			<Icon>" + n
                + "				<href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>" + n
                + "			</Icon>" + n
                + "			<hotSpot x='20' y='2' xunits='pixels' yunits='pixels'/>" + n
                + "		</IconStyle>" + n
                + "	</Style>" + n
                + "	<StyleMap id='m_ylw-pushpin'>" + n
                + "		<Pair>" + n
                + "			<key>normal</key>" + n
                + "			<styleUrl>#s_ylw-pushpin</styleUrl>" + n
                + "		</Pair>" + n
                + "		<Pair>" + n
                + "			<key>highlight</key>" + n
                + "			<styleUrl>#s_ylw-pushpin_hl</styleUrl>" + n
                + "		</Pair>" + n
                + "	</StyleMap>" + n;

        for (int i = 0; i < name_latlon.size(); i++) {
            String[] vals = name_latlon.get(i);

            try {
                String name = (vals[0]);
                double lat = Double.valueOf(vals[1]);
                double lon = Double.valueOf(vals[2]);

                String pinName = name;
                String temp
                        = "         <Placemark>" + n
                        + "		<name>" + pinName + "</name>" + n
                        + "		<LookAt>" + n
                        + "			<longitude>" + lon + "</longitude>" + n
                        + "			<latitude>" + lat + "</latitude>" + n
                        + "			<altitude>0</altitude>" + n
                        + "			<heading>-4.464842434738702</heading>" + n
                        + "			<tilt>0</tilt>" + n
                        + "			<range>519210.2902027154</range>" + n
                        + "			<gx:altitudeMode>relativeToSeaFloor</gx:altitudeMode>" + n
                        + "		</LookAt>" + n
                        + "		<styleUrl>#m_ylw-pushpin</styleUrl>" + n
                        + "		<Point>" + n
                        + "			<coordinates>" + lon + "," + lat + "," + "0</coordinates>" + n
                        + "		</Point>" + n
                        + "	</Placemark>" + n;

                o = o + temp;

            } catch (NumberFormatException ex) {

            } catch (ArrayIndexOutOfBoundsException exx) {

            }
        }
        o = o + "</Document>" + n
                + "</kml>" + n;

        // print to file
        String kmlFile = rootdir + fname + ".kml";
        lines_to_file(kmlFile, o, false);

        // kmz zip file=================
        try {
            String zipFile = rootdir + fname + ".kmz";
            String[] sourceFiles = {kmlFile};

            //create byte buffer
            byte[] buffer = new byte[1024];
            FileOutputStream fout = new FileOutputStream(zipFile);

            //create object of ZipOutputStream from FileOutputStream
            ZipOutputStream zout = new ZipOutputStream(fout);

            for (int i = 0; i < sourceFiles.length; i++) {

                EnfuserLogger.log(Level.FINER,Tools.class,"Adding " + sourceFiles[i]);
                //create object of FileInputStream for source file
                FileInputStream fin = new FileInputStream(sourceFiles[i]);
                zout.putNextEntry(new ZipEntry(sourceFiles[i]));

                int length;

                while ((length = fin.read(buffer)) > 0) {
                    zout.write(buffer, 0, length);
                }
                zout.closeEntry();
                fin.close();

            }

            //close the ZipOutputStream
            zout.close();
            EnfuserLogger.log(Level.FINER,Tools.class,"Zip file has been created!");

        } catch (IOException ioe) {
            EnfuserLogger.log(Level.SEVERE,Tools.class,"IOException :" + ioe);
        }

        try {
            deleteFile(kmlFile);
        } catch (Exception e) {
            EnfuserLogger.log(Level.WARNING,Tools.class,
                  "Could not delete: " + kmlFile);
        }

    }

}
