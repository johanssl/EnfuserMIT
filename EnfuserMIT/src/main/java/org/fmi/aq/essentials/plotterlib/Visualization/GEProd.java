/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class GEProd {

    public static void produceKML(String rootdir, String type2, String dateID,
            double lonmin, double lonmax, double latmin, double latmax, String nameString) {
        EnfuserLogger.log(Level.FINER,GEProd.class,"Producing a kml-file based on boundary coordinates.");
        String n = System.getProperty("line.separator");

        String fileTypeDot = "." + FigureData.fileTypes[FigureData.IMG_FILE_PNG];

        String output = rootdir + "kml_" + type2 + "_" + dateID + ".kml";

        String kmlOut = "<?xml version='10' encoding='UTF-8'?>";
        FileOps.lines_to_file(output, kmlOut, false);
        kmlOut = "<kml xmlns='http://www.opengis.net/kml/2.2' xmlns:gx='"
                + "http://www.google.com/kml/ext/2.2' xmlns:kml="
                + "'http://www.opengis.net/kml/2.2' xmlns:atom="
                + "'http://www.w3.org/2005/Atom'>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "<GroundOverlay>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	<name>" + nameString + "</name>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	<Icon>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<href>" + type2 + fileTypeDot + "</href>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<viewBoundScale>075</viewBoundScale>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	</Icon>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	<LatLonBox>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<north>" + latmax + "</north>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<south>" + latmin + "</south>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<east>" + lonmax + "</east>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<west>" + lonmin + "</west>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	</LatLonBox>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "</GroundOverlay>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "</kml>";
        FileOps.lines_to_file(output, kmlOut, true);

    }

    /**
     * Writes XML data to a KMZ file.
     *
     * @param data Data to write to the KMZ file.
     * @param rootdir Directory in which the file will be created.
     * @param fname File name without extension.
     */
    public static void printToFile(String data, String rootdir, String fname) {
        String kmlFile = fname + ".kml";
        FileOps.lines_to_file(kmlFile, data, false);
        try {
            String zipFile = rootdir + fname + ".kmz";
            String[] sourceFiles = {kmlFile};
            //create byte buffer
            byte[] buffer = new byte[1024];
            FileOutputStream fout = new FileOutputStream(zipFile);
            //create object of ZipOutputStream from FileOutputStream
            ZipOutputStream zout = new ZipOutputStream(fout);
            for (int i = 0; i < sourceFiles.length; i++) {
                EnfuserLogger.log(Level.FINER,GEProd.class,"Adding " + sourceFiles[i]);
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
            EnfuserLogger.log(Level.FINER,GEProd.class,"Zip file has been created!");
        } catch (IOException ioe) {
            EnfuserLogger.log(Level.FINER,GEProd.class,"IOException :" + ioe);
        }
        try {
            FileOps.deleteFile(kmlFile);
        } catch (Exception e) {
            EnfuserLogger.log(Level.WARNING,GEProd.class,
                    "Could not delete: " + kmlFile);
        }
    }

    /**
     * Creates a KMZ file with rectangular polygons including descriptions. Use
     * files to visualize and select areas in Google Earth.
     *
     * @param rootdir Directory into which the KMZ file should be created.
     * @param fname KMZ file base name.
     * @param coordinates ArrayList with String[] containing coordinates of the
     * rectangle corners and an optional description to add to each. Example:
     * {10.1, 11.1, 20.1, 22.3, "Foo"}
     */
    public static void produceGECoordinateRectangles(String rootdir, String fname, ArrayList<String[]> coordinates) {
        EnfuserLogger.log(Level.FINER,GEProd.class,"Creating polygon for Google Earth.");

        String n = System.getProperty("line.separator");
        String o = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + n
                + "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" xmlns:kml=\"http://www.opengis.net/kml/2.2\" xmlns:atom=\"http://www.w3.org/2005/Atom\">" + n
                + "<Document>" + n
                + "	<name>S2 Dataset Polygons</name>" + n
                + "	<StyleMap id=\"m_ylw-pushpin\">" + n
                + "		<Pair>" + n
                + "			<key>normal</key>" + n
                + "			<styleUrl>#s_ylw-pushpin</styleUrl>" + n
                + "		</Pair>" + n
                + "		<Pair>" + n
                + "			<key>highlight</key>" + n
                + "			<styleUrl>#s_ylw-pushpin_hl</styleUrl>" + n
                + "		</Pair>" + n
                + "	</StyleMap>" + n
                + "	<Style id=\"s_ylw-pushpin_hl\">" + n
                + "		<IconStyle>" + n
                + "			<scale>1.3</scale>" + n
                + "			<Icon>" + n
                + "				<href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>" + n
                + "			</Icon>" + n
                + "			<hotSpot x=\"20\" y=\"2\" xunits=\"pixels\" yunits=\"pixels\"/>" + n
                + "		</IconStyle>" + n
                + "	</Style>" + n
                + "	<Style id=\"s_ylw-pushpin\">" + n
                + "		<IconStyle>" + n
                + "			<scale>1.1</scale>" + n
                + "			<Icon>" + n
                + "				<href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>" + n
                + "			</Icon>" + n
                + "			<hotSpot x=\"20\" y=\"2\" xunits=\"pixels\" yunits=\"pixels\"/>" + n
                + "		</IconStyle>" + n
                + "		<PolyStyle>" + n
                + "               <colorMode>random</colorMode>" + n
                + "           </PolyStyle>" + n
                + "	</Style>";
        int polyCount = 1;
        for (String[] coords : coordinates) {
            o += "	<Placemark>" + n
                    + "		<name>Polygon " + polyCount++ + "</name>";
            if (coords.length == 5) { // any description to add?
                o += "<description><![CDATA[" + coords[4] + "]]></description>";
            }
            o += "		<styleUrl>#m_ylw-pushpin</styleUrl>" + n
                    + "		<Polygon>" + n
                    + "			<tessellate>1</tessellate>" + n
                    + "			<outerBoundaryIs>" + n
                    + "				<LinearRing>" + n
                    + "					<coordinates>";
            o += coords[2] + "," + coords[0] + ",0 "
                    + coords[2] + "," + coords[1] + ",0 "
                    + coords[3] + "," + coords[1] + ",0 "
                    + coords[3] + "," + coords[0] + ",0 ";
            o += "					</coordinates>" + n
                    + "				</LinearRing>" + n
                    + "			</outerBoundaryIs>" + n
                    + "		</Polygon>" + n
                    + "	</Placemark>";
        }
        o += "</Document>" + n
                + "</kml>";
        FileOps.lines_to_file(rootdir + fname + ".kml", o, false);
    }

    public static void produceGEpins(String rootdir, String fname, ArrayList<String[]> hotspots) {
        EnfuserLogger.log(Level.FINER,GEProd.class,"Producing Google Earth pins for hotspots...");
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

        for (int i = 0; i < hotspots.size(); i++) {
            String[] vals = hotspots.get(i);

            try {
                int rank = Integer.valueOf(vals[0]);
                double lat = Double.valueOf(vals[2]);
                double lon = Double.valueOf(vals[3]);
                double amnt = (Double.valueOf(vals[1]));

                String pinName = "Rank_" + rank + "_(" + (int) (amnt / 1000) + "t)";
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

        FileOps.lines_to_file(rootdir + fname + ".kml", o, false);

        printToFile(o, rootdir, fname);
    }

    public static void produceKMZ(String name, String resultsDir, boolean preserveImage) {

        String fileTypeDot = "." + FigureData.fileTypes[FigureData.IMG_FILE_PNG];
        String[] sourceFiles = {"kml_" + name + "_testi.kml", name + fileTypeDot};
        try {
            String zipFile = name + ".kmz";

            //create byte buffer
            byte[] buffer = new byte[1024];

            /*
                         * To create a zip file, use
                         *
                         * ZipOutputStream(OutputStream out)
                         * constructor of ZipOutputStream class.
             */
            //create object of FileOutputStream
            FileOutputStream fout = new FileOutputStream(resultsDir + zipFile);

            //create object of ZipOutputStream from FileOutputStream
            ZipOutputStream zout = new ZipOutputStream(fout);

            for (int i = 0; i < sourceFiles.length; i++) {

                EnfuserLogger.log(Level.FINER,GEProd.class,"Adding " + sourceFiles[i]);
                //create object of FileInputStream for source file
                FileInputStream fin = new FileInputStream(resultsDir + sourceFiles[i]);

                /*
                                 * To begin writing ZipEntry in the zip file, use
                                 *
                                 * void putNextEntry(ZipEntry entry)
                                 * method of ZipOutputStream class.
                                 *
                                 * This method begins writing a new Zip entry to
                                 * the zip file and positions the stream to the start
                                 * of the entry data.
                 */
                zout.putNextEntry(new ZipEntry(sourceFiles[i]));

                /*
                                 * After creating entry in the zip file, actually
                                 * write the file.
                 */
                int length;

                while ((length = fin.read(buffer)) > 0) {
                    zout.write(buffer, 0, length);
                }

                /*
                                 * After writing the file to ZipOutputStream, use
                                 *
                                 * void closeEntry() method of ZipOutputStream class to
                                 * close the current entry and position the stream to
                                 * write the next entry.
                 */
                zout.closeEntry();

                //close the InputStream
                fin.close();

            }

            //close the ZipOutputStream
            zout.close();

            EnfuserLogger.log(Level.FINER,GEProd.class,"Zip file has been created!");

        } catch (IOException ioe) {
            EnfuserLogger.log(Level.FINER,GEProd.class,"IOException :" + ioe);
        }

        for (String deleteFile : sourceFiles) {

            if (preserveImage) {
                if (deleteFile.contains(fileTypeDot)) {
                    continue;
                }
                //file is not deleted after kmz-creaton if it contains ".PNG"
                //  => preserves the image.
            }

            try {
                FileOps.deleteFile(resultsDir + deleteFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public static void allImagesToKMZ(String dir) {

        File fdir = new File(dir);
        File[] files = fdir.listFiles();

        for (File f : files) {

            if (f.getName().contains(".png") || f.getName().contains(".PNG")) {
                EnfuserLogger.log(Level.FINER,GEProd.class,
                        "Producing a KMZ file from a PNG image: " + f.getName());
            } else {
                continue;
            }

            String trueName = f.getName().replace(".PNG", "");
            trueName = trueName.replace(".png", "");

            try {
                String[] nameSplit = trueName.split("_");
                double latmin = Double.valueOf(nameSplit[0]);
                double latmax = Double.valueOf(nameSplit[1]);
                double lonmin = Double.valueOf(nameSplit[2]);
                double lonmax = Double.valueOf(nameSplit[3]);

                String name = trueName;

                imageToKMZ(dir, name, lonmin, lonmax, latmin, latmax);
            } catch (Exception e) {
                EnfuserLogger.log(Level.WARNING,GEProd.class,
                        "Could not produce KMZ from image: " + f.getName());
            }
        }

    }

    public static void imageToKMZ(BufferedImage img, String dir, Boundaries b, String name, boolean preserveImg) {

        FigureData.saveImage(dir, img, name, FigureData.IMG_FILE_PNG);
        produceKML(dir, name, "testi", b.lonmin, b.lonmax, b.latmin, b.latmax, name);
        produceKMZ(name, dir, preserveImg);

    }

    public static void imageToKMZ(String rootdir, String name, double lonmin, double lonmax, double latmin, double latmax) {
        EnfuserLogger.log(Level.FINER,GEProd.class,"Producing a kml-file based on bounding coordinates coordinates.");
        String n = System.getProperty("line.separator");

        String fileTypeDot = "." + FigureData.fileTypes[FigureData.IMG_FILE_PNG];

        String output = rootdir + name + ".kml";

        String kmlOut = "<?xml version='10' encoding='UTF-8'?>";
        FileOps.lines_to_file(output, kmlOut, false);
        kmlOut = "<kml xmlns='http://www.opengis.net/kml/2.2' xmlns:gx='http://www.google.com/kml/ext/2.2' xmlns:kml='http://www.opengis.net/kml/2.2' xmlns:atom='http://www.w3.org/2005/Atom'>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "<GroundOverlay>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	<name>" + name + "</name>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	<Icon>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<href>" + name + fileTypeDot + "</href>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<viewBoundScale>075</viewBoundScale>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	</Icon>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	<LatLonBox>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<north>" + latmax + "</north>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<south>" + latmin + "</south>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<east>" + lonmax + "</east>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "		<west>" + lonmin + "</west>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "	</LatLonBox>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "</GroundOverlay>";
        FileOps.lines_to_file(output, kmlOut, true);
        kmlOut = "</kml>";
        FileOps.lines_to_file(output, kmlOut, true);

        finalizeKMZ(name, rootdir);

    }

    private static void finalizeKMZ(String name, String resultsDir) {
        String deleteFile = name + ".kml";

        String fileTypeDot = "." + FigureData.fileTypes[FigureData.IMG_FILE_PNG];

        try {
            String zipFile = name + ".kmz";
            String[] sourceFiles = {name + ".kml", name + fileTypeDot};

            //create byte buffer
            byte[] buffer = new byte[1024];

            /*
                         * To create a zip file, use
                         *
                         * ZipOutputStream(OutputStream out)
                         * constructor of ZipOutputStream class.
             */
            //create object of FileOutputStream
            FileOutputStream fout = new FileOutputStream(resultsDir + zipFile);

            //create object of ZipOutputStream from FileOutputStream
            ZipOutputStream zout = new ZipOutputStream(fout);

            for (int i = 0; i < sourceFiles.length; i++) {

                EnfuserLogger.log(Level.FINER,GEProd.class,"Adding " + sourceFiles[i]);
                //create object of FileInputStream for source file
                FileInputStream fin = new FileInputStream(resultsDir + sourceFiles[i]);

                /*
                                 * To begin writing ZipEntry in the zip file, use
                                 *
                                 * void putNextEntry(ZipEntry entry)
                                 * method of ZipOutputStream class.
                                 *
                                 * This method begins writing a new Zip entry to
                                 * the zip file and positions the stream to the start
                                 * of the entry data.
                 */
                zout.putNextEntry(new ZipEntry(sourceFiles[i]));

                /*
                                 * After creating entry in the zip file, actually
                                 * write the file.
                 */
                int length;

                while ((length = fin.read(buffer)) > 0) {
                    zout.write(buffer, 0, length);
                }

                /*
                                 * After writing the file to ZipOutputStream, use
                                 *
                                 * void closeEntry() method of ZipOutputStream class to
                                 * close the current entry and position the stream to
                                 * write the next entry.
                 */
                zout.closeEntry();

                //close the InputStream
                fin.close();

            }

            //close the ZipOutputStream
            zout.close();

            EnfuserLogger.log(Level.FINER,GEProd.class,"Zip file has been created!");

        } catch (IOException ioe) {
            EnfuserLogger.log(Level.WARNING,GEProd.class,"GE-production IOException :" 
                    + name +", "+resultsDir);
        }

        try {
            FileOps.deleteFile(resultsDir + deleteFile);
        } catch (Exception e) {
            EnfuserLogger.log(Level.WARNING,GEProd.class,
                    "Could not delete: " + resultsDir + deleteFile);
        }

    }

}
