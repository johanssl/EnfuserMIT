/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import static org.fmi.aq.essentials.plotterlib.Visualization.FileOps.deleteFile;
import static org.fmi.aq.essentials.plotterlib.Visualization.FileOps.lines_to_file;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.Parser;

/**
 *
 * @author johanssl
 */
public class KmlPins {

    public static void produceGEpins(String rootdir, String fname, ArrayList<String[]> name_latlon) {
        EnfuserLogger.log(Level.FINER,KmlPins.class,"Producing Google Earth pins for hotspots...");
        String n = System.getProperty("line.separator");
        Parser parser = new Parser(rootdir +fname);
        
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
            if (vals.length<3) {
                parser.logSplitLengthException(vals,3);
                continue;
            }
            
                String name = (vals[0]);
                Double lat = parser.parseDouble(vals[1], "LAT");//Double.valueOf(vals[1]);
                Double lon = parser.parseDouble(vals[2], "LON");//Double.valueOf(vals[2]);
                if (lat==null || lon==null) continue;
                
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

        }
        parser.getExceptionPrintout(KmlPins.class);
        
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

                EnfuserLogger.log(Level.FINER,KmlPins.class,"Adding " + sourceFiles[i]);
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
            EnfuserLogger.log(Level.FINER,KmlPins.class,"Zip file has been created!");
            deleteFile(kmlFile);
            
        } catch (IOException ioe) {
            EnfuserLogger.log(Level.WARNING,KmlPins.class,"IOException :" + ioe);
        }

    }

}
