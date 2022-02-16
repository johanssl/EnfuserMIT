/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.setup;

import org.fmi.aq.enfuser.ftools.FileOps;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.Zipper;
import org.fmi.aq.enfuser.options.ERCF;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 *
 * @author johanssl
 */
public class Setup {

    
    protected static void extractRegionFiles(String root, String regRoot) {
        String name = "regCommons.zip";
        String zipDir = root + "setupFiles/";
        //unzip content to root.
        ArrayList<String> arr = Zipper.UnZip(zipDir + name, regRoot);
        for (String unzipped : arr) {
            EnfuserLogger.log(Level.FINER,Setup.class,"\t adding " + unzipped);
        }
    }

    /**
     * Build and create a region file based on multiple regional- osmLayer
     * related properties.
     *
     * @param bounds a list of modelling area boundaries
     * @param bNames a list of modelling area names (length should match)
     * @param main Main region bounds
     * @param regName region name
     * @return full file path to the created region file
     */
    protected static String buildStoreRegFile(ArrayList<Boundaries> bounds, ArrayList<String> bNames,
            Boundaries main, String regName) {

        //build region file
        ArrayList<String> lines = new ArrayList<>();
        //TODO COMPLETE THIS
        lines.add(
                "lineType;Region;resolution[meters, minutes];latmin;latmax;lonmin;lonmax;Variables (server mode);"
                + "temporalRange: pastGreater_h,past_h, forecast_h;triggerHours_UTC;innerLoc;outputCustomizations;"
        );

        //estimate timezone
        int tz = (int) Math.round(main.getMidLon() / 15);
        EnfuserLogger.log(Level.FINER,Setup.class,"Estimated timezone for region: " + tz + " (edit manually this later if incorrect).");
        //add main region
        lines.add(
                "main;" + regName + ";-;" + editPrecision(main.latmin, 3) + ";" + editPrecision(main.latmax, 3)
                + ";" + editPrecision(main.lonmin, 3) + ";" + editPrecision(main.lonmax, 3) + ";-;-;-;-;-;"
        );
        //add areas

        for (int i = 0; i < bounds.size(); i++) {
            lines.add(getAreaLine(bNames.get(i), bounds.get(i)));
        }
        
        ArrayList<String> args = ERCFarguments.addSetupDefaults(bNames,  bounds, tz)  ;//addDefaultArgs(areaLine, myRootDrive, regName, regFile, tz);
        for (String ar:args) {
            lines.add(ar);
        }
        
        String dataDir = GlobOptions.get().dataDirCommon() +"Regions"+ FileOps.Z;
        File test = new File(dataDir);
        if (!test.exists()) test.mkdirs();
       
        String filename = "region_" + regName + "_0.csv";
        EnfuserLogger.log(Level.INFO,Setup.class,"ERC creation: "+ dataDir + filename);
        FileOps.printOutALtoFile2(dataDir, lines, filename, false);
        return dataDir + filename;
    }
    
    public static String DEF_TRIGGERS ="-";
    public static String DEF_SPAN ="12,12";
    public static String DEF_TYPES ="NO2,PM25,PM10,O3,coarsePM,AQI";
    
    private static String getAreaLine(String nam, Boundaries b) {

        String typesTimeTrigs = 
                 DEF_TYPES+";" 
                +DEF_SPAN+";"
                +DEF_TRIGGERS+";-;insert tags here;";

        //test resolution
        int res_m = (int) (b.getDlat(2000, 2000) * degreeInMeters);
        if (res_m < 10) {
            res_m = 10;
        }

        String line
                = "area;" + nam + ";" + res_m + ",60;" + editPrecision(b.latmin, 3) + ";" + editPrecision(b.latmax, 3) + ";"
                + editPrecision(b.lonmin, 3) + ";" + editPrecision(b.lonmax, 3) + ";" + typesTimeTrigs;
        return line;
    }

    public static void checkNewAreas(FusionOptions ops) {
        if (ops.guiMode) {

            ArrayList<String> mapNames = new ArrayList<>();//these are the names based on OsmLayer, which SHOULD all be defined in RegionOptions
            ArrayList<Boundaries> bounds = new ArrayList<>();
            String dir = ops.getDir(FusionOptions.DIR_MAPS_REGIONAL);
            File f = new File(dir);
            try {

                File[] osms = f.listFiles();

                for (File osm : osms) {
                    if (!osm.getName().contains("Layer.dat")) {
                        continue;
                    }
                    String[] temp = osm.getName().split("_");
                    String name = temp[0];
                    mapNames.add(name);

                    //bounds
                    double latmin = Double.valueOf(temp[1]);
                    double latmax = Double.valueOf(temp[2]);
                    double lonmin = Double.valueOf(temp[3]);
                    double lonmax = Double.valueOf(temp[4]);

                    Boundaries b = new Boundaries(latmin, latmax, lonmin, lonmax);
                    b.expand(-0.05);//modelling domain should be slightly smaller than OSM-data coverage.
                    bounds.add(b);

                }//for osmLAyers

            } catch (Exception e) {
                e.printStackTrace();
            }
            ERCF r = ops.reg;
            String[] areaNames = r.getAllModellingAreaNames();
            int j = -1;
            for (String candidate : mapNames) {
                j++;
                boolean found = false;

                for (String aname:areaNames) {
                    if (aname.equals(candidate)) {
                        found = true;
                        EnfuserLogger.log(Level.FINER,Setup.class,"Wizard.checkNewAreas: cross-checked a "
                                + "mapname with RegionOptions' areas - " + candidate);
                    }
                }

                if (!found) {
                    EnfuserLogger.log(Level.FINER,Setup.class,"\"Wizard.checkNewAreas: there is an OsmLayer map "
                            + "area that is NOT listed in RegionOptions (" + candidate + ")"
                                    + "\n This will be now added automatically \n"
                                    + "(Model restart is necessary for these changes to take effect).");
                    String newLine = getAreaLine(candidate, bounds.get(j));

                    File erfc = r.ERCF_file();
                    ArrayList<String> arr = FileOps.readStringArrayFromFile(erfc.getAbsolutePath());
                    arr.add(2, newLine);

                    FileOps.printOutALtoFile2("", arr, erfc.getAbsolutePath(), false);
                    EnfuserLogger.log(Level.FINER,Setup.class,"\t added " + newLine + " to " + erfc.getAbsolutePath());
                }

            }

        }
    }

    static Boundaries superBoundsFromFileNames(ArrayList<String[]> fnames) {
        //main area
        double lat_min = 360;
        double lat_max = -360;
        double lon_min = 360;
        double lon_max = -360;

        for (String[] ss : fnames) {

            String[] temp = ss[1].split("_");
            //bounds
            double latmin = Double.valueOf(temp[1]);
            double latmax = Double.valueOf(temp[2]);
            double lonmin = Double.valueOf(temp[3]);
            double lonmax = Double.valueOf(temp[4]);

            //update main reg bounds
            if (latmin < lat_min) {
                lat_min = latmin;
            }
            if (latmax > lat_max) {
                lat_max = latmax;
            }
            if (lonmin < lon_min) {
                lon_min = lonmin;
            }
            if (lonmax > lon_max) {
                lon_max = lonmax;
            }

        }//for osmLAyers
        Boundaries b = new Boundaries(lat_min, lat_max, lon_min, lon_max);
        EnfuserLogger.log(Level.FINER,Setup.class,"OsmLayers form an area: " + b.toText());
        return b;
    }

}
