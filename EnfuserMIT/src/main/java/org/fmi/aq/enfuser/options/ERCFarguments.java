/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.io.File;
import org.fmi.aq.interfacing.CloudStorage;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;

/**
 * This class holds the regional arguments (settings) that affect the modelling
 * and data extraction to be done under this region.
 * 
 * This class also holds the static naming conventions used for ERCF files
 * and therefore is used in Setup to define default parameters when
 * an ERCF is created.
 * @author johanssl
 */
public class ERCFarguments {
//tags for modifications    

    private final static String PUT_TZ_HERE = "PUT_TZ_HERE";
    
    public final static String MINER_CLEANER = "MINER_CLEANER";
    public final static String AWS_S3_ARGS = "AWS_S3_ARGS";
    public final static String EMITTER_SYNCH = "AWS_EMITTER_SYNCH";
    public final static String TIMEZONE = "TIMEZONE";
    public final static String SUMMERTIME = "SUMMERTIME";
    public final static String NETCDF_BYTECOMPRESS = "NETCDF_BYTECOMPRESS";
    public final static String POL_BUILDING_PENETRATION = "POL_BUILDING_PENETRATION";
    
    public final static String WINDS_PRECOMP = "PRECOMPUTED_WINDS";
    public final static String ROUGHNESS_SCALER = "ROUGHNESS_SCALER";
    public final static String MIN_ABLH = "MIN_ABLH";
    public final static String MIN_WS = "MIN_WS";
    public final static String WINDFIELD_FUSION = "WINDFIELD_FUSION";
    
    public final static String BG_NESTING_MODE = "BG_NESTING_MODE";
    public final static String BLOCKER_STRENGTH ="BLOCKER_STRENGTH";
    public final static String DATAFUSION_MEMORY ="DATAFUSION_MEMORY";
    public final static String DF_STRENGTH ="DATAFUSION_STRENGTH";
    public final static String DATAFUSION_MEMORY_FACTORS ="DATAFUSION_MEMORY_FACTORS";
    public final static String DF_LOOV ="DATAFUSION_LEAVEONEOUT_STATISTICS";
    public final static String OUTLIER_CUT = "OUTLIER_CUT";

    public final static String CUSTOM_BOOLEAN_SETTINGS = "CUSTOM_BOOLEAN_SETTINGS";
    public final static String DUPLICANT_REGION = "DUPLICANT_REGION_OF";
    
    public final static String TAG_S3_BUCKET = "S3_BUCKET<";
    public final static String TAG_S3_REGION = "S3_REGION<";
    //default values for regions CN 0, FIN 1, EUR 2, GLOB 3. If array length is 2 then the region has no effect (same for all)
    
    //custom settings tags and capsules
    public final static String CUSTOM_ANIM_FULL_SIZE_ARCHS = "ANIM_FULL_SIZE_ARCHS";
    public final static String CUSTOM_PUFF_ANIMS ="PUFF_ANIMS";
    public final static String CUSTOM_NEURAL_OVERRIDES="NEURAL_OVERRIDES_LOAD";
    public final static String CUSTOM_EMISSION_DISTANCE_CHECKING ="EMISSION_DISTANCE_CHECKS";
    //encapsulated
    public final static String CUSTOM_DF_AUTOCOVARS = "DF_AUTOCOVARS_POW_W_MISSING<";
    public final static String CUSTOM_SPEED_MIXER= "SPEED_MIXER<";
    public final static String CUSTOM_CONTENT_CHECK ="CUSTOM_CONTENT_CHECK<";
    public final static String CUSTOM_NC_DIFFSTAMP ="NC_DIFF_STAMP";
    
    public final static String[][] SETUP_DEFAULTS = {
        //almost all of the non-feed arguments below should have zone "ANY"
        {MINER_CLEANER                  ,"false"                            ,"One of [true,false] if true, then older (7 day) EnfuserArchive content will be automatically deleted."}, //19
        {AWS_S3_ARGS                    ,TAG_S3_BUCKET + "enfuser>, " + TAG_S3_REGION + "eu-central-1>, LOCAL_ONLY",     "allPollutant_x.zip data can be pushed to AWS S3 via these settings."},//24
        {EMITTER_SYNCH                  ,"bucketName,areaNameFilter"        ,"For some areas emission inventories for e.g., shipping can be updated via AWS S3"},//24
        {TIMEZONE                       ,PUT_TZ_HERE                        ,"In full hours, the local timezone."}, //26
        {SUMMERTIME                     ,"true"                             ,"Set as false in case the modelling region do not switch to summer time."},//27
        {NETCDF_BYTECOMPRESS            ,"true"                             ,"If true then gridded netCDF data will be stored as bytes instead of floats. The dataset size becomes 25% with respect to float data."}, //29
        {BG_NESTING_MODE                ,"0"                                ,"A placeholder for future setting managing the way regional AQ is being nested to the local scale modelling"},
        {BLOCKER_STRENGTH               ,"1.0"                              ,"A value in between [0,1] to define the strength of urban effects (buildings, vegetation, street canyons)."},
        {DATAFUSION_MEMORY              ,"true"                             ,"If true, then each modelling tasks reads, updates a and saves a 'state' for data fusion resulting in a longer term memory for data fusion."},
        {DATAFUSION_MEMORY_FACTORS      ,"0.7,2,0.005"                      ,"Three doubles: [min state, max state, learning rate]. Default: 0.7,2,0.005"},
        {DF_LOOV                        ,"true"                             ,"If true, then the data fusion algorithm is initiated once per each measurement location and true leave-one-out comparisons are provided. "},
        {DF_STRENGTH                    ,"medium"                           ,"One of [off,low,medium,high]. 'Off' disables data fusion algorithm. Default: medium. 'High' allows more weight penalties to measurements to be assigned."},
        
        {OUTLIER_CUT                    ,"0.999,2"                          ,"Two doubles [a,b]. In an attempt to reduce modelling outliers in gridded data the largest values exceeding 'x' are set to 'x'. Example: with [0.999,2] every 2nd cell is checked and outliers are cut for every value that exceeds 99.9% largest value in the grid."},
        
        {POL_BUILDING_PENETRATION       ,"0.3,0.9"                          ,"Two doubles [a,b] ranging from 0.1 to 1. a: indoor aq penetration factor (local emitters). b: visual reduction for water areas."}, //31"
        {ROUGHNESS_SCALER               ,"0.5"                              ,"Local wind profiles are estimated based on rougness length. Reduce this value [0,1] to reduce the effect of surface properties to wind."},//35
        {WINDS_PRECOMP                  ,"true"                             ,"if true, then precomputed wind datasets are read if such exist."},//39
        {MIN_ABLH                       ,"120"                              ,"Minimum allowed atmospheric boundary layer height. Any value lower than this will be set to equal this limit (in statistics the actual ABLH is still shown)"},//40
        {MIN_WS                         ,"1.0"                              ,"Minimum allowed local wind speed [m/s] for Gaussian Plume formulas. Default: 1m/s"},//41
        {WINDFIELD_FUSION               ,"false"                            ,"Under development. Use wind measurements to adjust NWP meteorology accordingly."},//41
        
        {CUSTOM_BOOLEAN_SETTINGS        ,""                                 ,"Insert custom tags to modify settings further (for developers)"},//41
        {DUPLICANT_REGION               ,""                                 ,"specify another region name here. This region will read input from that region and this cannot have an ective miner running."},//41
    };
    
    private final static String[] OPTIONAL_ARGS = {"none"};
    
        public double[] getOutlierCut() {

        try {
        String cut = get(OUTLIER_CUT);
        if (cut==null || !cut.contains(",")) return null;
        String[] split = cut.split(",");
        
        return new double[]{
            Double.valueOf(split[0]),
            Double.valueOf(split[1])  
        };
        
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Build an array lines that form a part for an ERCF that is created
     * during Setup. This adds default values for all RegionArgument types.
     * This also includes for Feeds that are available to be used.
     * @param areas list of modelling area names
     * @param areab modelling area boundaries. These are important for
     * setting up Feed parameters that check wheter or not the feeds are
     * relevant for the region.
     * @param tz timezone for the region.
     * @return A list of lines to be added to an ERCF when being created.
     */
    public static ArrayList<String> addSetupDefaults(ArrayList<String> areas,
            ArrayList<Boundaries> areab,  int tz) {

        //collect all regional options String lines.
        //Possible add-on Feeds are to be included as well.
        ArrayList<String> lines = new ArrayList<>();
         lines.add("\nPart2: regional settings-----------------------");
        for (String[] defLine : SETUP_DEFAULTS) {
            String line = "argument;" + defLine[0] +";" +defLine[1] + ";;// "+defLine[2] +";;;;";
             if (line.contains(PUT_TZ_HERE)) {
                line = line.replace(PUT_TZ_HERE, (tz + ""));
            }
            lines.add(line);
        }
         lines.add("\nPart3: feed settings---------------------------");
        ArrayList<Feed> feeds = FeedFetch.getFeeds(null, null);
        for (Feed f:feeds) {
            String line = "argument;" + f.mainProps.argTag +";" 
                    +f.getDefaultArgumentLine(areab,
                            areas,
                            f.globalInitially(),
                            f.writesInitially(),
                            f.customArgumentInitially(areas),
                            f.readsInitially()) 
                    +";;;;;;";
            lines.add(line);
        }
        
        return lines;
    }

    private HashMap<String, String> tagValues = new HashMap<>();
    private HashMap<String, String> tagValues_extended = new HashMap<>();
    public final String regName;
    public String duplicantRegionName = null;
    private final File file;
    /**
     * Create an ERCF argument instance.
     * @param arguments a list of lines in the form of 'argument;key;value;'
     * @param regname the region (ERCF) name.
     * @param file the file from which the content was read.
     */
    public ERCFarguments(ArrayList<String> arguments,
            String regname, File file) {
        this.regName = regname;
        this.file = file;
        //read all arguments into hashMap. Structure: argumentText=value;
        for (String arg : arguments) {

            if (!arg.contains("argument")) {
                continue;
            }

            String[] split = arg.split(";");
            if (split.length < 3) {
                continue;//cannot hold a valid argument: "argument;key;value;
            }
            String key = split[1].replace(" ", "");
            String resp = split[2].replace(" ", "");

            tagValues.put(key, resp);
            //extended (for feeds). These can have DISABLE_READ;DISABLE_WRITE;GLOBAL_MODE;...and then the other stuff;
            String ext = resp+"";
            if (split.length > 3) {
                for(int k =3;k<split.length;k++) {
                   
                    String s = split[k].replace(" ", "");
                    ext+=","+ s;
                }
            }
            this.tagValues_extended.put(key, ext);
        }//for arguments
        String duplicant = this.getDuplicantRegion();
        if (duplicant!=null) this.duplicantRegionName = duplicant;
        printoutTagValueState(true);
    }
    
    private String getDuplicantRegion() {
        String s= this.tagValues.get(DUPLICANT_REGION);
        if (s==null) return s;
        if (s.length()==0) return null;
        s = s.replace(" ", "");
        if (s.equals("null")) return null;
        return s;
    }
    public boolean isDuplicantRegion() {
        return this.duplicantRegionName!=null;
    }
    
    /**
     *List all missing known tags in the ERFarguments. Also, list the arguments
     * that exist but are unknown. Possible deviations listed here will be
     * logged as warnings.
     */
    private void printoutTagValueState(boolean addMissing) {
        String unks ="";
        for (String existing: this.tagValues.keySet()) {
            //check for known tags
            boolean known =false;
            for (String[] specs:SETUP_DEFAULTS) {
                String arg  =specs[0];
               
                if (arg.equals(existing)) {
                    known = true;
                    break;
                }
            }//for knwon
            if (!known) {
                if (existing.contains("_ARGS")) continue;//this is a feed.
                unks+=existing+",";
    
            }
        }//for actual
        
      if (unks.length()>1) {
          EnfuserLogger.log(Level.WARNING, this.getClass(), "Unknown argument"
                        + " types set in ERCF for "+this.regName+": "+unks
                        +". These will have no effect with this model version.");
          
      }  
        
      //then the missing
      for (String[] specs:SETUP_DEFAULTS) {
                String arg  =specs[0];
                //is this optional?
                boolean optional=false;
                for (String s:OPTIONAL_ARGS) {
                   if( s.equals(arg)) optional=true;
                }
                if (optional) continue;
                
                String value = this.tagValues.get(arg);
                if (value==null) {//this is missing, warn
                    EnfuserLogger.log(Level.WARNING, this.getClass(), "Missing argument"
                        + " type in ERCF for "+this.regName+": "+arg
                        +""); 
                    
                    if (addMissing) {
                       
                      String def = specs[1];
                      String comment = specs[2];
                      String line = "argument;"+arg+";" +def+";;//"+comment +";;;;";
                      ArrayList<String> arr = new ArrayList<>();
                      arr.add(line);
                      boolean append = true;
                       EnfuserLogger.log(Level.WARNING, this.getClass(), "\t adding "+line
                        + " to "+file.getAbsolutePath());
                      FileOps.printOutALtoFile2(file, arr, append);
                            
                    }
                }//if missing
      }
        
    }

    public int getTimezone() {
        String ts = this.tagValues.get(TIMEZONE);
        if (ts != null) {
            return Integer.valueOf(ts);
        }

        EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "RegionArguments: TIME ZONE NOT FOUND! " + this.regName);
        return 0;
    }

    /**
     * Using the custom argument line channel, check whether or not the key is
     * present. This can be be used for misc. false/true settings in the model.
     * A missing key will be dealt as "false" as default.
     *
     * if it is, then this returns true. Otherwise false is returned.
     *
     * @param customArg the searched property key.
     * @return false/true
     */
    public boolean hasCustomBooleanSetting(String customArg) {
        String custom = this.tagValues.get(CUSTOM_BOOLEAN_SETTINGS);
        if (custom == null) {
            return false;//as default, a missing line means a missing key, which equals FALSE.
        }
        if (custom.contains(customArg)) {
            return true;//the key is present, so true
        } else {
            return false;//not there, so false
        }
    }
    
    /**
     * Return true if the region has a summer time switch. Some countries
     * do not use summer time at all.
     * @return 
     */
    public boolean autoSummerTime() {

        String ts = this.tagValues.get(SUMMERTIME);
        if (ts != null) { //open weather api key
            if (ts.toLowerCase().equals("true") || ts.toLowerCase().equals("yes")) {
                return true;
            } else {
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "RegionArguments: summerTime switch DISABLED for this region.");
                return false;
            }
        }

        EnfuserLogger.log(Level.FINER,this.getClass(),
                "RegionArguments: summerTime switch enabled as default.");
        
        return true;
    }
    
    /**
     * Return true if the region has been instructed to use precomputed
     * local wind fields. 
     * @return 
     */
    public boolean windGrids() {

        String wg = this.tagValues.get(WINDS_PRECOMP);
        if (wg != null) { //open weather api key
            if (wg.toLowerCase().equals("true") || wg.toLowerCase().equals("yes")) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
    
    /**
     * for a given setting property, find encapsulated content with another
     * parameter key. This is intended for argument lines that can have
     * multiple encapsulated settings within.
     * @param argKey the key to find the argument line, e.g.,
     * CUSTOM_BOOLEAN_SETTINGS
     * @param paramTag the secondary key, e.g, CUSTOM_NESTING_MODE
     * @return encapsulated setting value. If not found then returns null.
     */
    private String getEncapsulatedArgument(String argKey, String paramTag) {
        String val = this.tagValues.get(argKey);
        if (val == null) {
            return null;
        }
        return getBetween(val, paramTag, ">");
    }

    public final static String GLOBAL_DIRNAME = "Global";

    /**
     * Get the DataMiner (EnfuserArchive) directory for this region.
     * @param global if true, then the global directory is used
     * instead of the regional one.
     * @return path to mined dynamic input data.
     */
    public String getMinerDir(boolean global) {
        String s = GlobOptions.get().enfuserArchiveDir(null);
        String reg = this.regName;
        if (this.isDuplicantRegion()) {
            reg = this.duplicantRegionName;
        }
        if (global) {
            return s + GLOBAL_DIRNAME + "/";
        } else {
            return s + reg + "/";
        }

    }
    
     /**
     * Get the DataMiner (EnfuserArchive) directory for this region.
     * @return path to mined dynamic input data.
     */
    public String getMinerDir() {
        return getMinerDir(false);
    }

    /**
     * Evaluate the value of a boolean type argument. In case argument is not
     * defined, returns default.
     *
     * @param type
     * @return
     */
    private boolean valueTrue(String key, boolean deflt) {
        String value = this.tagValues.get(key);
        if (value == null) {
            return deflt;
        } else if (value.equals("no") || value.equals("false")
                || value.equals("FALSE")) {
            return false;
        }

        return true;
    }

    public boolean netCDF_byteCompress() {
        return this.valueTrue(NETCDF_BYTECOMPRESS, true);
    }

    public boolean AWS_localOnly() {
        String arg = (this.tagValues.get(AWS_S3_ARGS));
        if (arg == null) {
            return true;
        }

        if (arg.contains("LOCAL_ONLY")) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Get an instance of S3tools that has predefined region,bucket and secret
     * API-keys set. This method can return null if RegionArguments specify that
     * archives are to be stored as LOCAL_ONLY. The method also returns null if
     * no AWS_ARGS has been specified.
     *
     * @param bypassLocalSetting the LOCAL_ONLY setting can be bypassed by
     * setting this 'true' Use case (true): for Feed input AWS dump. Otherwise
     * this should be 'false'.
     * @return the instance.
     */
    public CloudStorage getAWS_S3(boolean bypassLocalSetting) {

        try {
            if (!bypassLocalSetting && AWS_localOnly()) {
                return null;
            }
            CloudStorage io = new CloudStorage();
            String bucket = this.getEncapsulatedArgument(AWS_S3_ARGS, TAG_S3_BUCKET);
            String region = this.getEncapsulatedArgument(AWS_S3_ARGS, TAG_S3_REGION);
            EnfuserLogger.log(Level.FINER,this.getClass(),"bucket=" + bucket + ", region=" + region);
            String cred1 = GlobOptions.get().getEncryptedProperty("AWS_S1");//GlobOptions.get(GlobOptions.I_APIK_S31);
            if (cred1 != null) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"AWS S3 secret1 found.");
            }
            String cred2 = GlobOptions.get().getEncryptedProperty("AWS_S2");//GlobOptions.get(GlobOptions.I_APIK_S32);
            if (cred2 != null) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"AWS S3 secret2 found.");
            }

            ArrayList<String[]> args = new ArrayList<>();
            args.add(new String[]{"bucket", bucket});
            args.add(new String[]{"region", region});
            args.add(new String[]{"creds1", cred1});
            args.add(new String[]{"creds2", cred2});
            io.setParameters(args);
            return io;

        } catch (Exception e) {
            return null;
        }

    }
    /**
     * Get the minimum value allowed for boundary layer height.
     * @return minimum value used for dispersion formulas
     */
    Double minABLH() {
        String temp = this.tagValues.get(MIN_ABLH);
        if (temp == null) {
             EnfuserLogger.log(Level.WARNING, this.getClass(),
                     "Warning: minimum ABLH [m] limits has not been defined for "+this.regName);
            return null;
           
        }
        return Double.valueOf(temp);
    }

     /**
     * Get the minimum value allowed for wind speed.
     * @return minimum value used for dispersion formulas
     */
    public double getMinimumWindSpeedLimit() {
        String temp = this.tagValues.get(MIN_WS);
        if (temp == null ) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                     "Warning: minimumwind speed [m/s] limits has not been defined for "+this.regName);
            return 1;
        }
        if (temp.contains(",")) temp = temp.split(",")[0];//in some older files there can be two numbers splitted with ",".
        return Double.valueOf(temp);
    }

    /**
     * Get an adjustment factor that is applied to any roughness length estimation
     * A lower value of 1, e.g. 0.5 can be used to smoothen the effects
     * of urban landscape to local wind profiling. A value of 0
     * disables the local wind profiling altogether.
     * @return 
     */
    Double getRougnessScaler() {

        String temp = this.tagValues.get(ROUGHNESS_SCALER);
        if (temp == null) {
            return null;
        }
        return Double.valueOf(temp);
    }

    public String get(String key) {
        return this.tagValues.get(key);
    }
        public String getExtended(String key) {
        return this.tagValues_extended.get(key);
    }

    /**
     * Get String instructions for AWS S3 connection for extracting recent
     * emission datasets.
     * @param areaName Specific modelling area name. This is used
     * in case there are specific instructions on which tag is to be used
     * for specific modelling areas.
     * @return String[bucket,region,tag]. The emitter synch feature can
     * only donwload files that has the tag in its filename.
     */
    public String[] getEmitterSynchString(String areaName) {
        String temp = this.tagValues.get(EMITTER_SYNCH);
        if (temp == null) {
            return null;
        }
        if (temp.contains("bucketName")) {
            return null;//default value (proper setting does not exist.
        }
        String[] bucket_reg_tag= temp.split(",");
        String tag = bucket_reg_tag[2];
        if (tag.contains("&")) {//has specific area name instructions e.g., 'pks=AH_HELSINKI&tku=AH_TURKU'
            EnfuserLogger.log(Level.FINER,this.getClass(),"EmitterSynch: searching identification tag for '" +areaName + "' from "+ tag);
            boolean found = false;
            String[] sp = tag.split("&");
            for (String test:sp) {
                EnfuserLogger.log(Level.FINEST,this.getClass(),"\t testing:"+test);
                if (test.contains(areaName+"=")) {
                    bucket_reg_tag[2]= test.replace(areaName+"=", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t got it: " + bucket_reg_tag[2]);
                    found=true;
                    break;
                }
            }
            if (!found) {
                EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "EmitterSynch: syntax incorrect for area name? "
                        + areaName +", "+ tag);
            }
        }//if area specific format
        return bucket_reg_tag;
    }
    
    public String getValue(String key) {
        return this.tagValues.get(key);
    }

   public String getCustomContent(String key) {
      String custom = this.tagValues.get(CUSTOM_BOOLEAN_SETTINGS);
        if (custom != null && custom.contains(key)) {
            return getBetween(custom,key,">");
        } else {
            return null;
        }
    }

    void put(String key, String value) {
        this.tagValues.put(key, value);
    }

    public boolean booleanSetting(String key, boolean missing) {
       String value = this.tagValues.get(key);
       if (value==null) return missing;
       
       String lower = value.toLowerCase();
       if (lower.contains("true")) return true;
       return false;
       
    }

}
