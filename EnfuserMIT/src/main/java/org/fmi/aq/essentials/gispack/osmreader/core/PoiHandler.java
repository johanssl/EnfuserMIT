/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.essentials.gispack.utils.Tools.produceGEpins;

/**
 * This class describes a Point Of Interest (POI). This includes the coordinates
 * and OSM-tags given for this location.
 *
 * @author johanssl
 */
public class PoiHandler {

    /**
     * Get a list if Strings[] that include latitude, longitude and all tags for
     * the given list of POIs.
     *
     * @param pois the list
     * @return list of {lat,lon,tags}
     */
    public static ArrayList<String[]> getLatLonTexts(ArrayList<POI> pois) {

        ArrayList<String[]> latlons = new ArrayList<>();

        for (POI p : pois) {

            float[] latlon = getAverageCoords(p);
            float lat = latlon[0];
            float lon = latlon[1];

            latlons.add(new String[]{lat + "", lon + "", p.tags});
        }//for pois

        return latlons;
    }

    /**
     * Create a CSV and a Google Earth pin-file for the given list of POI's.
     *
     * @param pois the list
     * @param dir directory for files
     * @param index POI type index.
     */
    public static void printPointsToFiles(ArrayList<POI> pois, String dir, int index) {
        String nam = POI.getName(index);
        nam = nam.replace(":", "_");
        String filename = "pointsOfInterest_" + nam + ".csv";

        ArrayList<String[]> name_latlon = new ArrayList<>();
        ArrayList<String> arr = new ArrayList<>();

        for (POI p : pois) {

            float[] latlon = getAverageCoords(p);
            float lat = latlon[0];
            float lon = latlon[1];

            String s = POI.getName(p.poiType) + ";" + lat + ";" + lon + ";" + p.tags;//"\n";

            name_latlon.add(new String[]{p.tags, lat + "", lon + ""});

            arr.add(s);
            EnfuserLogger.log(Level.FINER,PoiHandler.class,s);
        }//for pois

        FileOps.printOutALtoFile2(dir, arr, filename, false);
        produceGEpins(dir, nam + "_pins", name_latlon);

    }

    public static void printOutPOI_list() {
        ArrayList<String> arr = new ArrayList<>();
        String header = ("========Points Of Interest (POI) ================");
        EnfuserLogger.log(Level.FINER,PoiHandler.class,header);
        arr.add(header);
        String[] temp = new String[POI.LEN];
        for (int i = 0; i < temp.length; i++) {
            temp[i] = POI.getName(i);
            if (i == POI.POI_PGENERATOR) {
                temp[i] = "generator:source";
            }
            EnfuserLogger.log(Level.FINER,PoiHandler.class,i + ";\t " + temp[i]);
            arr.add(i + ";\t " + temp[i]);
        }
        File f = new File("");
        String filename = f.getAbsolutePath() + "/POI_Info.csv";
        EnfuserLogger.log(Level.FINER,PoiHandler.class,"This information can be found from " + filename);
        FileOps.printOutALtoFile2(f.getAbsolutePath() + "/", arr, "POI_Info.csv", false);
    }

    public static float[] getAverageCoords(ArrayList<double[]> coords) {
        float lat = 0;
        float lon = 0;
        int n = 0;

        for (double[] cc : coords) {
            float clat = (float)cc[0];
            float clon = (float)cc[1];

            lat += clat;
            lon += clon;
            n++;
        }

        lat /= n;
        lon /= n;

        return new float[]{lat, lon};
    }
    
        public static float[] getAverageCoords(POI p) {
        //in newly created POIs we have this.    
        if (p.midCC!=null) return p.midCC;    
        float lat = 0;
        float lon = 0;
        int n = 0;
        
        //old style
        for (int[] cc : p.coords) {
            float clat = (float)cc[0]/10000000;
            float clon = (float)cc[1]/10000000;

            lat += clat;
            lon += clon;
            n++;
        }

        lat /= n;
        lon /= n;

        return new float[]{lat, lon};
    }
    

}
