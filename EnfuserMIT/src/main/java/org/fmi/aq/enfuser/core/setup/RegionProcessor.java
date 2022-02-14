/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.setup;

import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import static org.fmi.aq.enfuser.core.setup.Setup.buildStoreRegFile;
import static org.fmi.aq.enfuser.core.setup.Setup.extractRegionFiles;
import org.fmi.aq.interfacing.Launcher;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;
import org.fmi.aq.enfuser.options.ERCF;

/**
 *
 * @author johanssl
 */
public class RegionProcessor {

    public static boolean OVERWRITES = false;
    
    /**
     * Find osmLayer file names that are present under the given directory, or
     * in one of it's sub-directories.
     * 
     * @param osmDir
     * @return 
     */
    public static ArrayList<File> findOsmLayerNames(String osmDir) {
        
        ArrayList<File> osms = new ArrayList<>();
        File odir = new File(osmDir);

        ArrayList<File> subs = new ArrayList<>();
        subs.add(odir);

        //find all subs
        File[] ff = odir.listFiles();
        for (File test : ff) {
            if (test.isDirectory()) {
                subs.add(test);
                EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                        "added subDir for search: " + test.getAbsolutePath());
            }

        }//for test

        for (File sub : subs) {
            ff = sub.listFiles();
            if (ff == null) {
                continue;
            }
            for (File test : ff) {

                String name = test.getName();
                String full = test.getAbsolutePath();
                if (name.contains("osmLayer.dat")) {
                    osms.add(test);
                    EnfuserLogger.log(Level.INFO,RegionProcessor.class,"\t added " + full);
                }
            }//for test
        }//for subs
        return osms;
    }
    
    
    /**
     * Create file structures and region files based on a collection of
     * osmLayers.
     *
     * @param ioRoot root for input/output data archiving (miner and model
     * tasks)
     * @param regName Region name, e.g., Finland or China.
     * @param osmDir path to folder that holds a collection of osmLayers. NOTE:
     * all sub-directories within this directory are also search for osmLayers.
     * a collection of osmLayers.
     * @param areaShrink value for modelling area shrinkage of osmLayer bounds.
     * default value 0.06 being 6%, which is used if null.
     */
    public static void processRegion(String ioRoot, String regName,
            String areaShrink, String osmDir) {
        
        ArrayList<File> osm_full_names = findOsmLayerNames(osmDir);
        EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                "Number of osmLayers found: " + osm_full_names.size());
        if (osm_full_names.isEmpty()) {
            EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                    "Nothing to process!");
            return;
        }
        double as;
        if (areaShrink == null) {
            as = 0.06;
            EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                    "Default area shrink = 6% will be used for boundaries.");
        } else {
            as = Double.valueOf(areaShrink);
        }

        processRegion(regName,osm_full_names,OVERWRITES,  as, osmDir);
    }

    /**
     * Create file structures and region files based on a collection of
     * osmLayers.
     *
     * @param regName Region name, e.g., Finland or China. Note: in case the
     * region is part of Finland or China, use this as the region name.
     * @param osmFiles an array of files pointing
     * to a collection of osmLayers.
     * @param overwrite if true, then existing installation for the Region can
     * be overwritten.
     * @param areaShrink value for modelling area shrinkage of osmLayer bounds.
     * default value 0.06 being 6%.
     * @param regionalSetupDir path to the regional setup directory
     */
    public static void processRegion(String regName,
            ArrayList<File> osmFiles,
            boolean overwrite, double areaShrink, String regionalSetupDir) {

        GlobOptions g = GlobOptions.get();
        String regDir = g.regionalDataDir(regName);


        if (regName == null || regName.length() == 0) {
            EnfuserLogger.log(Level.FINER,RegionProcessor.class,
                    "Empty or null region name!");
            return;
        }

        EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                "\t Creating region " +regName +" to "+ regDir);
        File rdf = new File(regDir);

        if (rdf.exists()) {
            EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                    "\t This region directory already exists.");

            if (overwrite) {
                EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                        "Overwrite is ON, deleting ALL existing previous content for this region.");
                rdf.delete();

            } else {
                EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                        "\t Region overwrite is set OFF - return.");
                return;
            }

        }// if exist

        //Create mapdir
        String mapDir = regDir + "maps/";
        File mapd = new File(mapDir);
        mapd.mkdirs();

        ArrayList<String> areaNames = new ArrayList<>();
        ArrayList<Boundaries> bounds = new ArrayList<>();

        //main area
        double lat_min = 360;
        double lat_max = -360;
        double lon_min = 360;
        double lon_max = -360;

        //copy osms here
        for (File opath : osmFiles) {
            try {
                String full = opath.getAbsolutePath();
                String fname = opath.getName();
                FuserTools.copyfile(full, mapDir + fname);

                EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                        "\t\t osmLayer added for Setup: " + fname);
                String[] temp = fname.split("_");
                String name = temp[0];
                areaNames.add(name);

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

                Boundaries b = new Boundaries(latmin, latmax, lonmin, lonmax);
                if (areaShrink > 0) areaShrink*=-1;//must be negative
                if (areaShrink!=0) b.expand(areaShrink);//modelling domain should be slightly smaller than OSM-data coverage.
                
                bounds.add(b);
            } catch (Exception e) {
                EnfuserLogger.log(e,Level.WARNING,RegionProcessor.class,
                  "ProcessRegion: file-name parse error encountered, skipping " + opath.getAbsolutePath());
            }
        }//for osmNames

        Boundaries greater = new Boundaries(lat_min, lat_max, lon_min, lon_max);
        greater.expand(0.1);//greater area should be somewhat larger than the maximum combined area.
        greater.latmax += 2;//for met. data and SILAM this will make sure a larger area is being covered.
        greater.latmin -= 2;
        greater.lonmax += 2;
        greater.lonmin -= 2;
        //check region lock
        boolean ok =g.checkRegionLock(greater);//can terminate if locking exists and this is area is not allowed.
        if (!ok) {
              EnfuserLogger.log(Level.INFO,
                "Region creation for this area is not allowed");
              return;
        }
        //copy other default reg files
        EnfuserLogger.log(Level.INFO,RegionProcessor.class,
                "Extracting necessary regional files to region directory: " + regDir);
        extractRegionFiles(g.getRootDir(), regDir);

        //build region file
        String regFile = buildStoreRegFile(bounds, areaNames, greater, regName);
        //time to load mapPack and options
        // boolean print = FusionOptions.VARA.print;
        GlobOptions.get().updateRegionChanges();

        FusionOptions ops = new FusionOptions(regName);
        FuserTools.mkMissingDirsFiles(ops);

        //Final step: possible feed postProcessing edits
        try {

            ArrayList<Feed> feeds = FeedFetch.getFeeds(ops ,null);
            for (Feed f : feeds) {
                if (f != null) {
                    f.makeRegionalOptionsEdits_setup(ops, regFile);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        
      try {
          //new Feature!
          //form a list of point emitters to /gridEmitters/ based on a setupFile that holds a list of powerplants.
          //also the necessary LTM-profiles will be copied.
          //PowerPlantProcessor.createForRegion(regName, greater,true, true);
          boolean areaTest =false;
         new Launcher().pointEmitterSetup(regName, greater, true, true,areaTest);
         
      } catch (Exception e) {
          e.printStackTrace();
      } 
        
      //emitter copy
      RegionPostProcessing.unzipPostSetup(ops, regionalSetupDir);
      
    }
    
    
  /**
   * Redo the regional ERCF file with default settings. Note: this will
   * replace the existing one!
   * @param regName region name (that must exist as an installed region)
   * @param areaShrink value for modelling area shrinkage of osmLayer bounds.
     * default value 0.06 being 6%, which is used if null.
   */  
  public static void recreateRegion(String regName, double areaShrink) {
      
        GlobOptions g = GlobOptions.get();
        String rootdir = g.getRootDir();
        
       String regDir = g.regionalDataDir(regName);
        if (regName == null || regName.length() == 0) {
            EnfuserLogger.log(Level.FINER,RegionProcessor.class,
                    "Empty or null region name!");
            return;
        }
        
        ERCF a = g.allRegs.getRegion(regName);
        if (a!=null) {
            EnfuserLogger.log(Level.FINER,RegionProcessor.class,
                    "Region "+ regName+" does seem to exist already. Recreating this region"
                            + " will replace the existing ERCG-file. Are you sure? (type y/n)");
           
            String resp = FuserTools.readSystemInput(10);
            if (resp!=null && resp.equals("y")) {
                
            }else {
                return;
            }
            
        }  
        File rdf = new File(regDir);

        if (!rdf.exists()) {
            EnfuserLogger.log(Level.FINER,RegionProcessor.class,
                    "\t This region directory does not exist yet.");
            return;
        }// if exist

        //Create mapdir
        String mapDir = regDir + "maps/";
        ArrayList<File> osmFiles =findOsmLayerNames(mapDir);
        ArrayList<String> areaNames = new ArrayList<>();
        ArrayList<Boundaries> bounds = new ArrayList<>();

        //main area
        double lat_min = 360;
        double lat_max = -360;
        double lon_min = 360;
        double lon_max = -360;

        //copy osms here
        for (File opath : osmFiles) {
            try {
                String fname = opath.getName();

                EnfuserLogger.log(Level.FINER,RegionProcessor.class,"\t\t" + fname);
                String[] temp = fname.split("_");
                String name = temp[0];
                areaNames.add(name);

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

                Boundaries b = new Boundaries(latmin, latmax, lonmin, lonmax);
                if (areaShrink > 0) areaShrink*=-1;//must be negative
                if (areaShrink!=0) b.expand(areaShrink);//modelling domain should be slightly smaller than OSM-data coverage.
                
                bounds.add(b);
            } catch (Exception e) {
                EnfuserLogger.log(e,Level.WARNING,RegionProcessor.class,
                  "ProcessRegion: file-name parse error encountered, skipping " + opath.getAbsolutePath());
            }
        }//for osmNames

        Boundaries greater = new Boundaries(lat_min, lat_max, lon_min, lon_max);
        greater.expand(0.1);//greater area should be somewhat larger than the maximum combined area.
        greater.latmax += 2;//for met. data and SILAM this will make sure a larger area is being covered.
        greater.latmin -= 2;
        greater.lonmax += 2;
        greater.lonmin -= 2;


        //copy other default reg files
        EnfuserLogger.log(Level.FINER,RegionProcessor.class,
                "extracting necessary regional files to region directory.");
        extractRegionFiles(rootdir, regDir);

        //build region file
        buildStoreRegFile(bounds, areaNames, greater, regName);
        //time to load mapPack and options
        // boolean print = FusionOptions.VARA.print;
        GlobOptions.get().updateRegionChanges();
      
  }  
}
