/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.interfacing.CloudStorage;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.essentials.geoGrid.ByteGeoNC;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.ERCFarguments;
import ucar.ma2.InvalidRangeException;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.netCdf.NcInfo;
import org.fmi.aq.essentials.netCdf.NetCDFout2;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.meteorology.HashedMet;
import org.fmi.aq.enfuser.ftools.Zipper;
import org.fmi.aq.enfuser.logging.GridQC;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;

/**
 * The purpose of this class is handle the creation and archiving of hourly
 * compressed datasets, using the "permaStoreOutput(). Additionally, this class
 * handles the cloud storage dump for the archive files and can launch an
 * animation task using pre-existing set of archive files.
 *
 * @author johanssl
 */
public class Arch {

    public final static String KB =" KB";
    
    public static String getStamp(FusionOptions ops, Dtime dt) {
        
        if (ops.getRunParameters().areaFusion_dt_mins%60 ==0 || dt.min==0) {
            return dt.getStringDate_fileFriendly();
            
        } else {
            String body = dt.getStringDate_fileFriendly();
            if (dt.min>=10) {
                body+="-"+dt.min;
            } else {
                body+="-0"+dt.min;
            }
            return body;
        }
    }
    /**
     * Store output in files that cover a single modelled time slice.Files that
 already exist will not be replaced or processed again. IMPORTANT: This
 method only saves slices that had measurement data (no forecasts) The
 main purpose is to build historical archive for e.g., annual averages.

 In case AWS S3 arguments has been specified this method also stores data
 to AWS S3 cloud storage.
     *
     * @param af AreaFusion instance that holds all modelled data to be stored.
     * @param ens the core
     * @param nonMetTypes a list on non-meteorological modelling variable types
     * to be stored (as defined in VariableAssociations)
     * @return content description
     */
    public static ArrayList<String> permaStoreOutput(AreaFusion af, FusionCore ens,
            ArrayList<Integer> nonMetTypes) {
        ERCFarguments args = ens.ops.getArguments();
        ArrayList<String> content = new ArrayList<>();
        
        CloudStorage io = null;
        if (!ens.ops.guiMode) {
            io = args.getAWS_S3(false);
            //check if AWS S3 has been disabled via ERCF area line:
            boolean enabled = ens.ops.getRunParameters().outputToS3();
            if (!enabled) {
                EnfuserLogger.log(Level.FINER,Arch.class,"AWS S3 connection NOT allowed due to ERCF instructions.");
                io=null;
            }
            
        } else {
            EnfuserLogger.log(Level.FINER,Arch.class,"AWS S3 connection NOT allowed in GUI-mode.");
        }

        if (io != null) {
            EnfuserLogger.log(Level.FINER,Arch.class,"Opening AWS S3 connection...");
            GlobOptions.get().setHttpProxies();
            try {
                io.establishConnection();
                EnfuserLogger.log(Level.FINER,Arch.class,"Connected.");
            } catch (Exception e) {
                e.printStackTrace();
                io = null;
            }
        }

        //directory for stored data
        String dir = ens.ops.archOutDir(af.dt);
        EnfuserLogger.log(Level.FINER,Arch.class,"Hourly archive: storing data to "
                + dir + ", afDt=" + af.dt.getStringDate());
        
        File f = new File(dir);
        if (!f.exists()) {
            f.mkdirs();
        }

        String datasetName = "allPollutants_" + getStamp(ens.ops,af.dt) + ".nc";

        f = new File(dir + datasetName);
        if (f.exists()) {
                EnfuserLogger.log(Level.FINER,
                        Arch.class,f.getAbsolutePath() + " exists already, replace.");
            
            f.delete();
        }//exists already
        
        Dtime last = af.dt;
        //create netCDF and zip it. 
        ArrayList<ByteGeoNC> bgs = new ArrayList<>();
        ArrayList<NcInfo> nfos = new ArrayList<>();

        //metData
        ArrayList<GeoGrid> metgs = new ArrayList<>();
        ArrayList<GeoGrid> metgs_exp = new ArrayList<>();
        ArrayList<NcInfo> met_nfos = new ArrayList<>();

        //try to make metGrids for an expanded area
        Boundaries bExp = ens.ops.boundsExpanded(true);
        GeoGrid[] expMets = null;
        try {
            expMets = ens.hashMet.toGeoGrids(bExp, af.dt, ens, HashedMet.WSM_RAW);

        } catch (Exception e) {
           EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered an  exception when extracting meteorological datasets"+ af.dt.getStringDate_noTS());
        }
        int len = af.typeLength();
        for (int i = 0; i < len; i++) {
            if (!ens.ops.IS_METVAR(i) && af.hasConcentrationGrid(i)){//a high res pollutant grid exists => take it.
                    
                bgs.add(new ByteGeoNC(af.consAsFloatArray(i)
                        , af.dt, af.bounds));

                NcInfo pollutantNfo = new NcInfo(
                        ens.ops.VARNAME_CONV(i),
                        ens.ops.UNIT(i),
                        ens.ops.LONG_DESC(i),
                        NcInfo.MINS,
                        af.dt,
                        ens.ops.areaFusion_dt_mins()
                );

                nfos.add(pollutantNfo);

            } else if (ens.ops.IS_METVAR(i) && af.metGrids[i] != null) {
                if (expMets != null) {//use these if existing
                    metgs_exp.add(expMets[i]);
                }
                metgs.add(af.metGrids[i]);

                NcInfo pollutantNfo = new NcInfo(
                        ens.ops.VARNAME_CONV(i),
                        ens.ops.UNIT(i),
                        ens.ops.LONG_DESC(i),
                        NcInfo.MINS,
                        af.dt,
                        ens.ops.areaFusion_dt_mins()
                );

                met_nfos.add(pollutantNfo);

            }
        }

        ArrayList<String> zipFiles = new ArrayList<>();

        // 1: ==============SILAM grids
        try {
            ArchivePack ap = RegionalGridProcessor.extractSilamBGs(af.dt, ens, af);
            if (ap!=null) {
            ArrayList<GeoGrid> reggs = ap.geos;
            //make sure that each GeoGrid of equal size and bounds
            ArrayList<NcInfo> reg_nfos = ap.nfos;
            reggs = GridQC.verifyEqualDimensions(true,reggs,
                    "SILAM regional archiving "+ af.dt.getStringDate(),reg_nfos);
            
            String regNam = "regional_" + getStamp(ens.ops,af.dt) + ".nc";

            NetCDFout2.writeNetCDF_statc(true, reg_nfos,
                    reggs, FuserTools.getNetCDF_attributes(last), dir + regNam);

            zipFiles.add(regNam);
            
            } else {
                EnfuserLogger.log(Level.WARNING,Arch.class,"regional BG-grid"
                        + " extraction failed from AreaFusion instance. "
                        + "Data missing for "+ af.dt.getStringDate());
            }
        } catch (Exception e) {
           EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a netCDF creation exception for regionalAQ data for"+ af.dt.getStringDate_noTS());
        }

        //2: ================selected components (type-source)
        try {
            ArchivePack ap = af.getComponentPack(ens.ops);
            ArrayList<ByteGeoNC> compBgs = ap.bgeos;
            ArrayList<NcInfo> comp_nfos = ap.nfos;
            String compNam = "components_" + getStamp(ens.ops,af.dt) + ".nc";

            NetCDFout2.writeStaticByteNetCDF(comp_nfos, compBgs,// af.bounds, 60,
                    FuserTools.getNetCDF_attributes(last), dir + compNam);

            zipFiles.add(compNam);

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a netCDF creation exception for components-AQ data for"+ af.dt.getStringDate_noTS());
        }

        //3: ==================overrides printout & statistics
        try {
            AdjustmentBank oc = ens.getCurrentOverridesController();
            ArrayList<String> overs = oc.getAllOverridesInText(af.dt, ens.ops, nonMetTypes);
            if (overs != null) {
                String overnam = "eval_" + getStamp(ens.ops,af.dt) + ".csv";
                FileOps.printOutALtoFile2(dir, overs, overnam, false);
                zipFiles.add(overnam);
            }

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a netCDF creation exception for overrides (eval_) data for"+ af.dt.getStringDate_noTS());
        }

        //this older format is very clunky - V2
        try {
            AdjustmentBank oc = ens.getCurrentOverridesController();
            boolean hidable = true;//this statistics can be delivered to users. So we hide selected things if we must
            ArrayList<String> overs_v2 = oc.getObservationStatistics(af.dt, af.dt, ens, nonMetTypes,hidable);
            if (overs_v2 != null) {
                String overnam = "observationStatistics_" + getStamp(ens.ops,af.dt) + ".csv";
                FileOps.printOutALtoFile2(dir, overs_v2, overnam, false);
                zipFiles.add(overnam);
            }

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a netCDF creation exception for observationStatistics for"+ af.dt.getStringDate_noTS());
        }
 
        //4: ====================  non-met data
        try {
            NetCDFout2.writeStaticByteNetCDF(nfos, bgs,// af.bounds, 60, 
                    FuserTools.getNetCDF_attributes(last), dir + datasetName);
            zipFiles.add(datasetName);

            //5:  ===================== metData
            String metNam = "met_" + getStamp(ens.ops,af.dt) + ".nc";
            //String metNam = metNamBase+"_netcdf.nc";//nc write method does this by itself, unfortunately.
            try {
                NetCDFout2.writeNetCDF_statc(false, met_nfos,//"hours since "+af.dt.getStringDate_netCDF_zoned(),
                        metgs, FuserTools.getNetCDF_attributes(last), dir + metNam);
                zipFiles.add(metNam);
            } catch (Exception e) {
                EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a netCDF creation exception for meteorological data for"+ af.dt.getStringDate_noTS());
            }

            //6: ======================= metData expanded
            String metNamEx = "metExpanded_" + getStamp(ens.ops,af.dt) + ".nc";
            //String metNam = metNamBase+"_netcdf.nc";//nc write method does this by itself, unfortunately.
            try {
                NetCDFout2.writeNetCDF_statc(true, met_nfos,//"hours since "+af.dt.getStringDate_netCDF_zoned(),
                        metgs_exp, FuserTools.getNetCDF_attributes(last), dir + metNamEx);
                zipFiles.add(metNamEx);

            } catch (Exception e) {
                EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a netCDF creation exception for Expanded meteorological data for"+ af.dt.getStringDate_noTS());
            }

            //8:===========================custom feed content
            ArrayList<String> customZips = ens.feeder.zipCustomFeedContentForArchive(ens, af,dir);
            for (String s:customZips) {
                zipFiles.add(s);
            }

            //=============================================================//
            //=============================================================//
            //zip and destroy
            String zipName = datasetName.replace(".nc", ".zip");
            //build content String
            for (String fnam:zipFiles) {
                File test = new File(dir+fnam);
                if (test.exists()) {
                    long size_kb = test.length()/1000;
                    content.add(fnam +" "+size_kb+KB);
                }
            }
            
            Zipper.zipAndDestroyList(true, dir, zipFiles, zipName);

            //amazon aws
            if (io != null) {
                String zipFullName = dir + zipName;
                String key = args.regName + Z + ens.ops.areaID() + Z + zipName; //e.g., Finland/HKI-METRO/allPollutants_2018-10-01T00-00.zip
                EnfuserLogger.log(Level.INFO,Arch.class,"AWS3 Put: " + zipFullName + "," + key);

                String privacy = "public";
                //s3t.put(zipFullName,key,acl);
                io.put(zipFullName, key, privacy, null);
            }

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.SEVERE, Arch.class, "allPollutants.zip creation"
                    + " encountered a severe exception for "+ af.dt.getStringDate_noTS());
        }

        return content;
    }

    /**
     * Store local archives to AWS S3 cloud storage for the given temporal
     * range. Note:this method requires the respective plugin for AWS S3 to be
     * available. The region and area in question (and therefore the physical
     * location of data) is carried by FusionOptions.
     *
     * @param ops options to carry Regional arguments and thereby the AWS bucket
     * and region settings.
     * @param start start time for data dump
     * @param end end time for data dump
     * @param allowReplace if true, then files already found in AWS S3 are
     * replaced.
     */
    public static void cloudDump(FusionOptions ops, Dtime start, Dtime end, boolean allowReplace) {

        //first check AWS conn
        ERCFarguments args = ops.getArguments();
        CloudStorage io = args.getAWS_S3(false);
        if (io != null) {
            EnfuserLogger.log(Level.FINER,Arch.class,"Opening AWS S3 connection...");
            GlobOptions.get().setHttpProxies();
            try {
                //s3t.open();
                io.establishConnection();
                EnfuserLogger.log(Level.FINER,Arch.class,"Connected.");
            } catch (Exception e) {
                e.printStackTrace();
                io = null;
            }
        }

        if (io == null) {
            EnfuserLogger.log(Level.FINER,Arch.class,"Cannot establish connection to AWS S3!");
            return;
        }

        int adds = 0;
        HashMap<String, Boolean> keys = new HashMap<>();
        for (String key
                : //s3t.browseKeys()
                io.browse(null, null)) {
            keys.put(key, Boolean.TRUE);//fast search is possible
        }
        EnfuserLogger.log(Level.FINER,Arch.class,"There are " + keys.size() + " entries in AWS S3.");

        Dtime current = start.clone();
        while (current.systemHours() <= end.systemHours()) {
            EnfuserLogger.log(Level.FINER,Arch.class,"\n =============================================\n");
            String dir = ops.archOutDir(current);//ops.areaID must be set properly 
            String name = "allPollutants_" + current.getStringDate_YYYY_MM_DDTHH() + ".zip";
            if (current.min != 0) {
                name = name.replace(".zip", "-" + current.min + ".zip");
            }
            //TODO 30 min data? The filename does not consider minutes.
            EnfuserLogger.log(Level.FINER,Arch.class,"Searching data for " + dir + name);

            String zipFullName = dir + name;
            File f = new File(zipFullName);
            if (f.exists()) {//do work
                System.out.print("\t  ---> it exists.");

                //does it exist in AWS S3?
                String key = args.regName + Z + ops.areaID() + Z + name; //e.g., Finland/HKI-METRO/arch_2018-10-01T00-00.zip
                boolean exists = keys.get(key) != null;
                EnfuserLogger.log(Level.FINER,Arch.class,"\t the key is " + key + ", exists in AWS? = " + exists);
                if (!exists || allowReplace) {
                    if (exists) {
                        EnfuserLogger.log(Level.FINER,Arch.class,"\t\t the file already exists in AWS, but it will be replaced.");
                    }
                    EnfuserLogger.log(Level.FINER,Arch.class,"\t\t AWS3 Put: " + zipFullName + " ===> " + key);
                    String privacy = "public";
                    //s3t.put(zipFullName,key,acl);
                    io.put(zipFullName, key, privacy, null);
                    adds++;
                } else {//it exists and replace is not allowed (false, false)
                    EnfuserLogger.log(Level.FINER,Arch.class,"\t\t the file already exists in AWS, and will NOT be replaced.");
                }

            }//if archive exists

            current.addSeconds(3600 / 2);
        }//for time

        EnfuserLogger.log(Level.FINER,Arch.class,"Added " + adds + " files to AWS S3.");
    }

   

    public static void createNetCDF(ArrayList<Integer> nonMet_typeInts, FusionOptions ops,
            Dtime start, Dtime end, int minStep) {

        boolean animSupport = false;
        ArrayList<AreaFusion> afs = ACtoAF.getPollutantAFs(animSupport, nonMet_typeInts,
                start, end, minStep, ops.getDir(FusionOptions.DIR_TEMP), ops,false);

    createNetCDF(afs, nonMet_typeInts, ops,start, end, minStep);

    }
    
    
      public static void createNetCDF(ArrayList<AreaFusion> afs, 
              ArrayList<Integer> nonMet_typeInts, FusionOptions ops,
            Dtime start, Dtime end, int minStep) {

        float[][][] emsData;
        // iterate over different pollutants

        for (int typeInt : nonMet_typeInts) {
            if (ops.IS_METVAR(typeInt)) {
                continue; // no met data in this iteration
            }

//there is no point in this method if the array size is one. Let's assume that there are at least two elements in the array.
            long secDiff = afs.get(1).dt.systemSeconds() - afs.get(0).dt.systemSeconds();
            ops.setTemporalResolution_min((int) (secDiff / 60));
            EnfuserLogger.log(Level.FINER,Arch.class,"Arch.createNetCDF: temporal difference (min) for AFs is " + ops.areaFusion_dt_mins());

            emsData = AreaFusion.getGrids_nonInterp(afs, typeInt);
            String dir = ops.operationalOutDir();
            boolean dirCreated = (new File(dir).mkdirs());
            EnfuserLogger.log(Level.FINER,Arch.class,"Arch.createNetCDF: output dir is going to be " + dir);

            String vari = ops.VAR_NAME(typeInt) + "";
            EnfuserLogger.log(Level.FINER,Arch.class,"Producing dynamic netCDF for " + vari + " to " + dir);
            vari = vari.replace(".", "");
            try {

                //netCDF-out
                NcInfo pollutantNfo = new NcInfo(
                        ops.VARNAME_CONV(typeInt),
                        ops.UNIT(typeInt),
                        ops.LONG_DESC(typeInt),
                        NcInfo.MINS,
                        afs.get(0).dt,
                        minStep
                );

                NetCDFout2.writeDynamicNetCDF(ops.netCDF_byteCompress(),
                        pollutantNfo,
                        emsData, afs.get(0).bounds,// ops.areaFusion_dt_mins,
                        FuserTools.getNetCDF_attributes(null),
                        dir + ops.areaID() + "_" + vari + "_" + afs.get(0).dt.systemSeconds() + "_post_downloads.nc");
                EnfuserLogger.log(Level.FINER,Arch.class,"DONE producing dynamic netCDF for " + vari);

            } catch (IOException | InvalidRangeException ex) {
                ex.printStackTrace();
            }

        }

    }  
    

}
