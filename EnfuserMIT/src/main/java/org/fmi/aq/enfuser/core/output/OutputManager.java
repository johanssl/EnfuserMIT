/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.enfuser.core.statistics.EvalDatProcessor;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.netCdf.NcInfo;
import org.fmi.aq.essentials.netCdf.NetCDFout2;
import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.essentials.plotterlib.animation.Anim;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import ucar.ma2.InvalidRangeException;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FuserTools.copyTo;
import org.fmi.aq.enfuser.ftools.Zipper;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.plotterlib.Visualization.GridDot;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.essentials.plotterlib.animation.AnimEncoder;
import org.fmi.aq.interfacing.ArchStatProcessor;
import org.fmi.aq.interfacing.Feeder;
import org.fmi.aq.interfacing.Imager;
import org.fmi.aq.interfacing.TropomiProcessor;

/**
 * This static class takes care of output data storing that is not the hourly
 * archive files. Rather, this output will be created based on the archived
 * datsets.
 *
 * This includes: - output copy to secondary output routes - "runCompleted"
 * dataset summaries. - animations & figures - QC figures and text data - netCDF
 * files
 *
 * @author johanssl
 */
public class OutputManager {

    /**
     * Create post-processed output for the modelled area.The data to do this
     * will be given by the list of AreaFusions. In case this is empty or null,
     * the AreaFusion instances will be loaded and converted from archives.
     *
     * @param ens the core
     */
    public static void outputAfterAreaRun(FusionCore ens) {
        
        ens.clearMemoryForOuputPostprocessing();//some of the processes that follows
        RunParameters p = ens.ops.getRunParameters();
        
        //may require significant amount of memory.
        FusionOptions ops = ens.ops;
        String dir = ops.operationalOutDir();
        EnfuserLogger.log(Level.INFO,OutputManager.class,
                "//////>>> Entered Output processing phase. Directory = "+ dir);
        
        EnfuserLogger.log(Level.FINER,OutputManager.class,
                "Compressed Archives used for Animation = "+ ens.ops.compressedArchForAnim);
        
        ens.ops.logBusy(true);
        FuserTools.clearDirContent(dir);
         
        ArrayList<Integer> nonMet_typeInts = p.nonMetTypeInts(ens.ops);
        Dtime areaStart = p.start();
        Dtime end = p.end();
        Boundaries bounds = p.bounds();
        String permaDir = ops.statsOutDir(areaStart);
        AdjustmentBank oc = ens.getCurrentOverridesController();
        //new output statistics 
        
        if (p.statsOutput()) {
            try {
                EvalDatProcessor.storeEvaluationDataToFile(dir, nonMet_typeInts, ens,
                        areaStart, end, false);
                //store to addtional directory that will not be emptied for the next model run
                EvalDatProcessor.storeEvaluationDataToFile(permaDir, nonMet_typeInts, ens,
                        areaStart, end,false);
                
                if (ens.ops.dfOps.df_trueLOOV) {
                    EvalDatProcessor.storeEvaluationDataToFile(permaDir, nonMet_typeInts, ens,
                            areaStart, end,true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                oc = ens.getCurrentOverridesController();
                boolean hidable = false;
                ArrayList<String> overs_v2 = oc.getObservationStatistics(areaStart, end, ens, nonMet_typeInts,hidable);
                if (overs_v2 != null) {
                    String overnam = "observationStatistics_" + trunkatedTime(areaStart)
                            + "_" + trunkatedTime(areaStart) + ".csv";
                    FileOps.printOutALtoFile2(dir, overs_v2, overnam, false);
                    FileOps.printOutALtoFile2(permaDir, overs_v2, overnam, false);
                }
                //forecast points
                ArrayList<String> forcs = oc.getObservationStatistics_forecast(areaStart, end, ens, nonMet_typeInts);
                if (forcs != null) {
                    String forcDir = permaDir +"forecasted"+Z;
                    File f = new File(forcDir);
                    if (!f.exists()) f.mkdirs();
                    String overnam = "forecastStatistics_" + trunkatedTime(areaStart)
                            + "_" + trunkatedTime(areaStart) + ".csv";
                    FileOps.printOutALtoFile2(dir, forcs, overnam, false);
                    FileOps.printOutALtoFile2(forcDir, forcs, overnam, false);
                }

                //store the current, learned scaling parameters to file
                if (ops.dfOps.CQL_restore) {
                    if (ens.CQL!=null) {
                        ens.CQL.printoutMasters(ops," (to be written to file) ");
                        ens.CQL.saveToBinary(ops,areaStart);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            
            
            //plots! One week's worth of visualizing based on stored observationStatistics.
            new Imager().createWeeklyPlots(ops);
            //rolling statsCruncher feature has been enabled?
            if (p.operativeStatsCruncher()) {
                try {
                    new ArchStatProcessor().launchRollingStatisticsCreation(
                            ops.getRegionName(), ops.areaID(), 7);
                    
                } catch (Exception e) {
                    EnfuserLogger.log(e, Level.WARNING, OutputManager.class,
                            "auto statCruncher failed!");
                }
            }
            
            if (p.operativeGridAverages()) {//NOTE: if this special feature has been enabled, it can take a lot of time, even 30 min.
                int hourSparser = p.gridAveragerSparser(); 
                new ArchStatProcessor().launchGridAverager(
                            ops.getRegionName(), ops.areaID(), hourSparser,true, p);
            }
            EnfuserLogger.log(Level.INFO,OutputManager.class,"/////OutputManager:tasks done.\n");
            // a new feature: operational TROPOMI analysis, but only for areas that actually support this, and only once per week.
            if (p.tropomiAnalysis()) {
            new TropomiProcessor().launchOperationalAnalysis(ops);
            }
            flagRunCompletedFile(dir, ens);
            Meta.createMetaFile(ens);
        }//if stats out
        PuffPlatform ppf = ens.getPPF();
        if (ppf!=null) ppf.printoutLog();//to results dir.

        //==============================================================================================================
        if (!p.videoOutput() //misc not assessed though...
                && !p.imagesOutput()
                && !p.gridsOutput()
                ) {
            EnfuserLogger.log(Level.INFO,OutputManager.class,
                    "Output post processing has been set OFF. This means that"
                    + " no aggregate netCDF, animations, figures or statistics will be generated.");
            return;
        }
        //NOTHING ELSE IS PROCESSED, just archive model data and continue to next time span.
        //==============================================================================================================

        Dtime lastObsTime = null;
        try {
            lastObsTime = ens.datapack.getMaxObservedDt();
        } catch (Exception e) {

        }

//it possible that the AreaFusion arrayList is null or empty (memory saving mode)
//in that case we load it now from the archives that have just been made
      
           EnfuserLogger.log(Level.INFO,OutputManager.class,
                   "Loading modelling data from archives...");
           ArrayList<AreaFusion> afs = ACtoAF.getAFs_withRunParams(ops);
        

  EnfuserLogger.log(Level.INFO,OutputManager.class,"====>>> Archives have been read.");
  ens.ops.logBusy(true); 
  
//for private statistics (stats/) some hidden measurements needs to be revealed.
        if (ops.visOps.vid_interpolationFactor > 1) {
            EnfuserLogger.log(Level.FINER,OutputManager.class,
                    "Slice interpolation has been set ON, fusion arraySize=" + afs.size());
        }

//save overrides if exists (it should)
        if (oc != null) {
            EnfuserLogger.log(Level.FINER,OutputManager.class,"Printing out used overrides.");
            oc.timeSeriesToFile(ops.areaID(), ops, nonMet_typeInts, dir, false);
        }


        Dtime firstDt = afs.get(0).dt.clone();
        boolean storeMet = true;
        int typeInt_prox = 0;
        int metGrid_interpFactor = ops.visOps.vid_interpolationFactor;//store for comparison when typeInt is changed
        GeoGrid[][] metgrids = null;
        try {
            metGridsForType(ops, afs, storeMet, typeInt_prox, dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        storeMet = false;

        Anim a;
        float[][][] emsData;
        // iterate over different pollutants
        
        int lastTypeForAnim =-1;//in case video encoding uses a parallel thread, the last one must not.
        for (int typeInt : nonMet_typeInts) {
             boolean anim = ops.doAnimation(typeInt);
             if (anim) lastTypeForAnim = typeInt;
        }
        
        ArrayList<AnimEncoder> encs = new ArrayList<>();
        
        
        for (int typeInt : nonMet_typeInts) {
          String type = ens.ops.VAR_NAME(typeInt);
           boolean anim = ops.doAnimation(typeInt);
            try {
                //get visual options for this type
                ops.setTypeVisOps(typeInt);
                ops.logBusy();
                //byte typeInt = FusionOptions.getTypeInt(type);
                if (ens.ops.IS_METVAR(typeInt)) {
                    continue; // no met data in this iteration
                }
                GeoGrid[] SILAM_grids = null;
                //Dtime[] dts = AreaFusion.getDates_interpolated(afs,  ops);
                if (ops.SILAM_visualWindow ) {
                    try {
                        SILAM_grids = RegionalGridProcessor.extractRegionalBGgeos(typeInt, afs, ens);//ens.datapack.extractSilamGeos(typeInt,dts, ens);
                    } catch (Exception e) {
                        EnfuserLogger.log(Level.INFO,OutputManager.class,
                                "OutputManager: SILAM visuals are not available for "+ type);
                    }
                }

                //get local measurements in String form for this time span and typeInt    
                ArrayList<GridDot>[] coordinatedStrings = new ArrayList[afs.size()];
                for (int z = 0; z < afs.size(); z++) {
                    coordinatedStrings[z] = afs.get(z).getGridDots(typeInt,ops,ens.DB);
                }

                //the next grids needs not to be interpolated yet 
                emsData = AreaFusion.getGrids_nonInterp(afs, typeInt);

                if (p.gridsOutput()) {
                    String vari = ens.ops.VAR_NAME(typeInt) + "";
                    EnfuserLogger.log(Level.INFO,OutputManager.class,
                            "Producing dynamic netCDF for " + vari + " to " + dir);
                    vari = vari.replace(".", "");
                    try {

                        //netCDF-out
                        NcInfo inf = new NcInfo(
                                ens.ops.VARNAME_CONV(typeInt),
                                ens.ops.UNIT(typeInt),
                                ens.ops.LONG_DESC(typeInt),
                                NcInfo.MINS,
                                firstDt,
                                ops.areaFusion_dt_mins()
                        );
                        inf.setDifferentTimeZoneInfo(ens.ops);
                        
                        String ncfile = dir + ops.areaID() + "_" + vari + "_" 
                                + firstDt.systemSeconds() + "_post_downloads.nc";
                        NetCDFout2.writeDynamicNetCDF(ops.netCDF_byteCompress(),
                                inf,
                                emsData,
                                bounds,
                                FuserTools.getNetCDF_attributes(lastObsTime),
                                ncfile);
                        EnfuserLogger.log(Level.FINER,OutputManager.class,"DONE producing dynamic netCDF for " + vari);
                        //zip it
                        Zipper.zipLocally(ncfile, false);
                      
                        
                    } catch (IOException | InvalidRangeException ex) {
                        ex.printStackTrace();
                    }
                }

                double[] mm = ens.ops.visOps.minMax = AreaFusion.assessConcentrationRange(afs, typeInt);
                
                if (typeInt == ens.ops.VARA.VAR_AQI) {
                    ops.visOps.minMax = AreaFusion.getMinMax(afs, typeInt);
                } else  {
                    mm = ens.ops.VARA.applyVisualizationLimits(typeInt, mm);
                    ops.visOps.minMax = mm;
                }

                //animation
                ens.ops.logBusy();
                if (typeInt == ens.ops.VARA.VAR_AQI && ops.visOps.colorScheme == FigureData.COLOR_PASTEL_GR) {
                //e.g. define scheme pastelGR (8) for AQI in varAssociations.csv
                    ops.visOps.customPaletteRecipee = ens.aqi.getCustomNaturalPalette(ops.visOps.minMax);
                //intelligent color palette scaling for this AQI interval
                } else {
                    ops.visOps.customPaletteRecipee = null;
                }

                int secsBetween = 60 * ops.areaFusion_dt_mins();
                ops.visOps.waterValueScaler_anim = ops.WATER_PENETR;
                
                a = new Anim(dir, emsData, bounds, ops.visOps, firstDt, secsBetween,
                        ens.mapPack,type);
                a.local_ts = ops.getArguments().getTimezone();
                
                a.animOut = anim;
                if (!p.imagesOutput()) {
                    a.figsOut=false;
                }
                if (!a.animOut && !a.figsOut) continue;
                
                a.regID = ops.areaID() + "_";
                a.imgPostID = "_post";
                a.coordinatedStrings = coordinatedStrings;

                a.aboveColb = ens.ops.VARA.LONG_DESC(typeInt, true);
                a.varName = ens.ops.VAR_NAME(typeInt);
                a.fullAviName = ops.areaID() + "_" + a.varName + "_" + firstDt.systemSeconds() + "_post_downloads.avi";

                //check if metData needs to be interpolated (e.g. frame interpolation factor is different)
                if (ops.visOps.vid_interpolationFactor != metGrid_interpFactor || metgrids ==null) {
                    metGrid_interpFactor = ops.visOps.vid_interpolationFactor;//store for comparison when typeInt is changed
                    metgrids = metGridsForType(ops, afs, storeMet, typeInt, dir);
                }

                a.addMetGrids(metgrids, false);//no interpolation needed
                a.displayUTC_Z = true; // adds "Z" right next to the timestamp since everything is in UTC time.

                if (SILAM_grids != null && ops.visOps.placeMiniWindow) {
                    try {
                    EnfuserLogger.log(Level.FINER,OutputManager.class,"Interpolating Silam miniWindow...");
                    a.miniWindow_interpolated = a.interpolateGeo(SILAM_grids);
                    } catch (NullPointerException w) {
                        EnfuserLogger.log(Level.FINER,OutputManager.class,
                                "SILAM miniwindow grid interpolation error "
                                        + "for : " + ens.ops.VAR_NAME(typeInt));
                    }
                }

                System.gc();
                try {
                    ops.logBusy();
                    a.createFrames();// also produces figures
                    AnimEncoder enc = a.get();
                    if (enc!=null) encs.add(enc);
                        
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // a.produceAnimation_multiThread();// also produces figures
                System.gc();

            } catch (Exception e) {
                e.printStackTrace();
            }
            
          //restore settings for next type 
        }// for types z

        //do the encoding, possibly in multithreading
        AnimEncoder.encodeAll(encs,ops);
        
        //phase two, while the priority for output gets lower and lower.
        if (p.miscOutput())Feeder.secondaryOptionalOutput(ens,dir, areaStart, end,nonMet_typeInts,afs.get(0));
        copyOutputToSecondaryChannels(ens, areaStart, end);
    }
    

    /**
     * Copy and rename selected content from the "Enfuser_out_master" to
     * "Enfuser_out_reduced" and "Enfuser_out_all".
     *
     * @param ens the core. If FusionOptions instance at the core has
     * "archiveRunOnlyMode" then this method does nothing. Also, if regional
     * arguments do not specify any secondary output directories then file copy
     * may not occur.
     * @param af_start start time for modelling work.
     * @param end end time for modelling work.
     */
    public static void copyOutputToSecondaryChannels(FusionCore ens, Dtime af_start, Dtime end) {


        //store netCDF data to CZ and SmartMet out (if set on). Note: these are NOT 
        //allowed for GUImode testing (only operational runs use these)
        //herokuApp post
        FusionOptions ops = ens.ops;
        String outdir_master = ens.ops.operationalOutDir();//arg.stringArgs[RegionArguments.OUT_MASTER_STORAGE] + thisloc +"/";
        EnfuserLogger.log(Level.FINER,OutputManager.class,"Operational master directory for output: " + outdir_master);
        String[] ignoreNames = ops.getForkIgnoreNames();
        ops.logBusy();
                String reducedOut = ops.reducedAreaOutDir();//can be null if this output channel has been disabled
        if (reducedOut != null) {
            EnfuserLogger.log(Level.INFO,OutputManager.class,"OneLiners: Post reduced data to " + reducedOut);
            //naming changes will be done.
            
            try {
                /*
                String intervalString = af_start.getStringDate_YYYY_MM_DDTHH() + "_"
                + end.getStringDate_YYYY_MM_DDTHH();
                String[] replaceWithDates_remove = {"post_downloads",
                ens.ops.areaID() + "_"}; //e.g. post_downloads => temporalInterval, HKI-METRO(hourlyUpdate) => empty
                */
                post(outdir_master, reducedOut, null, null, ".nc", ignoreNames);
                flagRunCompletedFile(reducedOut, ens);

            } catch (Exception e) {
                e.printStackTrace();
            }

        }//only netCDF will be copied
        
        ops.logBusy();
        String areaHeroDir = ops.herokuAreaOutDir();
        if (areaHeroDir != null) {
            EnfuserLogger.log(Level.INFO,OutputManager.class,"OneLiners: Post all data to " + areaHeroDir);
            try {
                post(outdir_master, areaHeroDir,  null, null, null,ignoreNames);//no name changes applied

                flagRunCompletedFile(areaHeroDir, ens);
            } catch (Exception e) {
                e.printStackTrace();
            }
            EnfuserLogger.log(Level.INFO,OutputManager.class,"OneLiners: done Posting data to " + areaHeroDir);
        }

      


    }

    /**
     * Create a flag file showing the loaded input datasets and to signal that
     * no more files will be put to the directory.
     *
     * @param targetDir the output data directory
     * @param ens the core
     */
    public static void flagRunCompletedFile(String targetDir, FusionCore ens) {
        String line = "Enfuser model run fully completed.";
        line += "\n" + ens.datapack.getFileContributionList();
        ArrayList<String> arr = new ArrayList<>();
        arr.add(line);
        FileOps.printOutALtoFile2(targetDir, arr, "runCompleted.txt", false);
    }


    /**
     * Create an array of GeoGrids that contain INTERPOLATED meteorological data
     * to be used for animations. The first dimension is for variable types. The
     * second one is temporal. The number of temporal layers depend on a) the
     * overall difference of end-time to start-time, b), ops.areaFusion_dt_mins
     * and c) ops.visOps.vid_interpolationFactor.
     *
     * As an additional feature, this method is also able to store this data as
     * netCDF files, given that "ops ops.netCDF_out" and "store" are set to
     * "true".
     *
     * @param ops the options
     * @param afs the list of AreaFusion instances that carries the "raw"
     * meteorological data.
     * @param store if true, then netCDF dump is allowed.
     * @param typeInt the modelling variable type for which the metGrids are
     * generated for. NOTE: for different variable types DIFFERENT visualization
     * options can be set via VarAssociations. This includes different amount of
     * frames per animation and therefore there is a variable type dependency
     * here.
     * @param dir the directory for optional netCDF data dump.
     * @return an array of meteorological GeoGrids.
     */
    public static GeoGrid[][] metGridsForType(FusionOptions ops, ArrayList<AreaFusion> afs,
            boolean store, int typeInt, String dir) {
        //metGrids and netCDF out
        int VARLEN = ops.VARLEN();
        GeoGrid[][] metgrids = new GeoGrid[VARLEN][];
        ArrayList<NcInfo> infos = new ArrayList<>();
        ops.setTypeVisOps((byte) typeInt);//each type can have a different imterpolation factor, and this updates the value!
        boolean hasContent = false;

        int interp_minutes = ops.areaFusion_dt_mins() / ops.visOps.vid_interpolationFactor;
        Dtime firstDt = afs.get(0).dt.clone();

        for (int i = 0; i < VARLEN; i++) {
            if (!ops.IS_METVAR(i)) {
                continue;// ONLY met data for this iteration
            }

            GeoGrid[] gg = AreaFusion.getMetGrids_interpolated(afs, i, ops);
            if (gg != null) {
                metgrids[i] = gg;
                NcInfo inf = new NcInfo(
                        ops.VARNAME_CONV(i),
                        ops.UNIT(i),
                        ops.LONG_DESC(i),
                        NcInfo.MINS,
                        firstDt,
                        interp_minutes
                );
                infos.add(inf);
            }

        }//for vars

        if (store && ops.getRunParameters().gridsOutput() && hasContent) {

            try {
                String filename = ops.areaID() + "_met_" + firstDt.systemSeconds() + ".nc";
                NetCDFout2.writeMultiDynamicNetCDF(false, infos,
                        metgrids, FuserTools.getNetCDF_attributes(null), dir + filename);

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

        return metgrids;
    }
    
    
    
     /**
     * This quite OLD method manages (copies and renames files) files in several
     * output folders, namely for the secondary output directories
     * that have less importance nowadays.
     *
     * @param resultDir the general resultDir, commonly points to PREGEN dir.
     * @param heroAreaDir directory for HerokuApp cloud directory in
     * Dropbox/Apps/. This can also be another Directory such as in Cityzer db.
     * @param dateString Not used at the moment (idea was to preserve some QC
     * data based on it)
     * @param replaceWithDate_remove if not null, then naming conventions can be
     * changed (type or file type NOT affected)
     * @param mustContain a tag for CITYZER file management, e.g., ".nc", then
     * only netCDF files will be stored
     */
    private static void post(String resultDir, String heroAreaDir, String dateString,
            String[] replaceWithDate_remove, String mustContain, String[] ignores) {

        //keep track of files to be sent to heroku portal.
        ArrayList<File> heros = new ArrayList<>();
        File f = new File(resultDir);
        File[] ff = f.listFiles();
        if (ff != null) {
            for (File test : ff) {
                
                boolean ignored = false;
                if (ignores!=null && ignores.length>0) {
                    for (String ig : ignores) {
                        if (test.getName().contains(ig)) {
                            ignored =true;
                            break;
                        }
                    }
                }//if ignore rules 
                
                if (ignored) continue;
                
                if (test.getName().contains("_post")) {
                    if (mustContain != null && !test.getName().contains(mustContain)) {
                        continue; //e.g. the file name does not contain ".nc" and it should => skip
                    }
                    heros.add(test);
                }
            }
        }


        if (heroAreaDir != null && heroAreaDir.length() > 4) {

            //cleanup 
            File ftest = new File(heroAreaDir);
            if (!ftest.exists()) {
                ftest.mkdir();
                ftest = new File(heroAreaDir);
            }

            File[] existing = ftest.listFiles();
            if (existing != null) {

                for (File e : existing) {
                    String name = e.getName();
                    boolean del = true;//otherwise delete

                    if (del) {
                        EnfuserLogger.log(Level.FINE,OutputManager.class,"cleaning " + name);
                        e.delete();
                    }
                }//for existing files
            }//if existing

            //add herokufiles to the herokuGate
            for (File hero : heros) {
                try {
                    //this is the place for name mods
                    String newName = hero.getName() + "";
                        EnfuserLogger.log(Level.FINER,OutputManager.class,
                                "\t checking " + hero.getName());
                    

                    if (replaceWithDate_remove != null) {
                        newName = newName.replace(replaceWithDate_remove[0], dateString);//replace with a properDateString
                        newName = newName.replace(replaceWithDate_remove[1], "");//remove
                    }

                    File copy = new File(heroAreaDir + newName);
                    // posted.add(copy.getName());
                    if (newName.contains(".nc") ) {
                        EnfuserLogger.log(Level.INFO,OutputManager.class,
                                "=> File copy: " + copy.getAbsolutePath());
                    } else {
                         EnfuserLogger.log(Level.FINER,OutputManager.class,
                                "=> File copy: " + copy.getAbsolutePath()); 
                    }
                    copyTo(hero, copy);

                } catch (Exception ex) {
                     EnfuserLogger.log(ex,Level.WARNING,OutputManager.class,
                    "FileCopy failure: "+ hero.getAbsolutePath());
                }
            }//for heros (files)

                EnfuserLogger.sleep(5000,OutputManager.class);
                ArrayList<String> beacon = new ArrayList<>();
                beacon.add("all files have been uploaded.");
                FileOps.printOutALtoFile2(heroAreaDir, beacon, "uploadComplete.txt", false);

        }//if herokuGate != null => post

    }   

    public static String trunkatedTime(Dtime areaStart) {
        Dtime dt = areaStart.clone();
        int mod = dt.hour%8;
        dt.addSeconds(-mod*3600);
        return dt.getStringDate_fileFriendly();
    }

}
