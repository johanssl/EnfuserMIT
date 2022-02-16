/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.feeds;

import org.fmi.aq.enfuser.options.VarConversion;
import org.fmi.aq.interfacing.CloudStorage;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.mining.miner.RegionalMiner;
import static org.fmi.aq.enfuser.mining.miner.RegionalMiner.resetStreamerFiles;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.datapack.reader.GribReader;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.ftools.Streamer;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Logger;
import org.fmi.aq.enfuser.ftools.FileCleaner;
import org.fmi.aq.essentials.netCdf.NetcdfHandle;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.ERCFarguments;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.netCdf.MoleConversion;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_NONE;
import static org.fmi.aq.enfuser.mining.feeds.FeedConstructor.CREDENTALS_UP;
import org.fmi.aq.enfuser.datapack.reader.FileReader;
import org.fmi.aq.enfuser.ftools.FileOps;
import static org.fmi.aq.enfuser.mining.feeds.HourlyInterpolator.interpolateMissingLayers;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.Parser;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.interfacing.Feeder;

/**
 *
 * @author johanssl
 */
public abstract class Feed {

    //==========Feed class attributes
     /**searchRad_m if non-null, then a coordinate matching is done for
     * the existing sources. In case existing entry is found in a radius of less
     * than x meters then this new entry adopts the existing entry's name as an
     * alias.
    */
    public Integer searchRad_m=null;
    public String genID ="station";//base name for new information source
    public int genID_decimals=1000;//decimals used to round-up coordinates in name id
    //60.123 and 1000 => ST_60123_...
    public boolean addCoordsToKnownID=false;//applies only to readText generic (NM10)
    public final GlobOptions g;
    int[] cleanerWindow;
    
    public PrintStream SOUT = System.out;
    public boolean soutFlush = false;
    
    //basic properties
    public final FeedConstructor mainProps;
    
    public FusionOptions ops;
    public String primaryMinerDir;
    String dir_storeSout;
    public final String storeSout_fnam;

    public boolean rawDump = false;
    public boolean AWSDump = false;
    public ArrayList<String> addedFiles = new ArrayList<>();

    public String regname;
    public Boundaries b;
    public Boundaries[] bLocs;
    public ArrayList<Boundaries> customAreaBounds = null;
    public String secondaryDir = null;
    public int refreshModulo = 1;
    public int storeTicks = 0;
    public ArrayList<String> skipFiles = new ArrayList<>();
    
    public boolean readable = true;
    //the feed read operation is never initiated during model load phase.
    //There are certain feeds for which the load needs to be performed AFTER
    //preload (e.g., it needs additional data such as MapPack)

    public boolean stores = true;
    public boolean sourceNameByNcFile = false;
    public FileReader fileReader=null;
    
    public boolean legacyHttpCon = false;
    public ArrayList<String[]> reqestProperties_http = null;
    public final static String HTTP_REQUEST_PROPS = "HTTP_REQUEST_PROPS<";
    public boolean simpleFileDownload = true;

    protected String[] defaultVars = null;
    protected String[] defaultVars2 = null;
    public boolean interrupt = true;
    public boolean global = false;
    private Integer fileAmountCap = null;
    //simple tags (if present, something is turned on or off)
    public static final String CERT_BYPASS = "CERT_BYPASS";
    public final static String LEGACY_HTTP_CON = "LEGACY_HTTP_CONNECTION";
    public final static String DISABLE_SIMPLE_FILE_DOWNLOAD = "DISABLE_SIMPLE_FILE_DOWNLOAD";
    public final static String TAG_UNINTERRUPTABLE = "UNINTERRUPTABLE";
    public final static String PROXY_RETRY = "PROXY_RETRY";
    private final static String RAW_DATA_DUMP = "RAW_DATA_DUMP";
    public boolean rawDataOut = false;
    
    public final static String DISABLE_WRITE = "DISABLE_WRITE";
    public final static String DISABLE_READ = "DISABLE_READ";
    public final static String GLOBAL_MODE = "GLOBAL_MODE";
    
    private final static String DEF_VARS = "defaultVars";

    public boolean readAllVars_nc =false;
    public final static String READ_ALL_IN ="READ_ALL_IN";
    
    //readable properties (tag<value>)
    private final static String CUSTOM_MINER_TICKER = "CUSTOM_MINER_TICKER<";
    private final static String FILE_AMOUNT_CAP = "FILE_AMOUNT_CAP<";
    private final static String PRIORITY_DECREASER = "PRIORITY_DECREASER<";
    private final static String CUSTOM_PIXELOFFS = "PIXEL_OFFSETS_HW<";
    public final static String CLEANER_WINDOW_H ="CLEANER_WINDOW_H<";

    //this is for custom list of Boundaries (latmin,latmax,lonmin,lonmax& ...)
    public final static String TAG_CUSTOM_BOUNDS = "CUSTOM_BOUNDS<";
    public ArrayList<Boundaries> customB = new ArrayList<>();

    public final static String TAG_URL = "URL<";
    public String customUrl = null;
    public final static String TAG_VAR_LIST = "VAR_LIST<";
    protected String customVars = null;
    public final static String TAG_VAR_LIST2 = "VAR_LIST2<";
    protected String customVars2 = null;
    public static final String SEC_DIR = "SECONDARY_DIR<";
    public static final String ADDITIONAL_DIR = "ADDITIONAL_DIR<";
    
    public final static String TAG_READ_VARLIST = "VAR_LIST_READ<";
    protected String[] readVarList = null;

    //this is for legit modelled area names 
    public final static String TAG_CUSTOM_AREAS = "CUSTOM_AREAS<";
    private final static String AWS_DUMP = "AWS_DUMP<";//bucket,region,minute-of-hour-limit
    public String[] awsDump_bucketRegSparser = null;

    //this is for a list of names (use case can differ)
    public final static String CUSTOM_NAMES = "CUSTOM_NAMES<";
    protected ArrayList<String> customNames = null;

    public volatile Dtime lastSuccessStoreDt;
    public volatile String statusString = "notUsedYet";
    public volatile boolean previousWasSuccess = false;
    public volatile int lastStoreCount = -1;
    public volatile int lastSuccessStoreCount = -1;
    public boolean forkSOUT = false;

    //ublic final boolean auth;//use basic authentication with provided usr/pass
    public String[] creds_usr_pass = null;
    public final boolean certificates;//bypass SSH certificate check
    public int dataImprecisionRating = 1;
    protected boolean ncUnitConversions =false;
    public static final String ALLOW_MOLE_CONVERSIONS ="ALLOW_MOLE_CONVERSIONS";
    public static final String NC_UNIT_CHECKS ="NC_UNIT_CHECKS";
    
    /**
     * 
     * @param c basic parameters for the feed.
     * @param ops regional fusionOptions. Can be NULL (used for listing of the feeds). 
     * Also the feed type
     * that calls this must be prepared with null FusionOptions.
     */
    public Feed(FeedConstructor c, FusionOptions ops) {
        this.mainProps = c;
        this.g = GlobOptions.get();
        this.fileReader = FileReader.getDefault(c);
        
            if (c.argLine==null) {//still null, must return with disable read and write.
                this.readable =false;
                this.stores=false;
                creds_usr_pass = null;//no credentials
                certificates = true;//SSH security on 
                this.primaryMinerDir = null;
                this.dir_storeSout = null;
                this.storeSout_fnam =null;
                c.argLine ="";
                return;
            }

        this.ops = ops;
        regname = ops.getRegionName();
        b = ops.getRegionBounds().clone();
        bLocs = ops.reg.getAllModellingAreaBounds();
        String argLine = c.argLine;
        String argTag = c.argTag;
        if (argLine.contains(GLOBAL_MODE)) {
            this.global = true;
        }
        this.primaryMinerDir = ops.getArguments().getMinerDir(global) + c.feedDirName + "/";
        this.dir_storeSout = ops.getArguments().getMinerDir(global);
        this.storeSout_fnam = dir_storeSout + c.feedDirName + "_" + 0 + "_lastStore.txt";
        


            if (argLine.contains(DISABLE_READ)) {
                this.readable = false;
            }
            if (argLine.contains(DISABLE_WRITE)) {
                this.stores = false;
            }

            if (argLine.contains(DISABLE_SIMPLE_FILE_DOWNLOAD)) {
                this.simpleFileDownload = false;
            }
            
            if (argLine.contains(LEGACY_HTTP_CON)) {
                this.legacyHttpCon=true;
            }
            if (argLine.contains(TAG_UNINTERRUPTABLE)) {
                this.interrupt = false;
            }

            if (argLine.contains(SEC_DIR)) {//SEC_DIR
                    String value = getBetween(argLine, SEC_DIR, ">").replace(" ", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": Secondary read Directory set: " + value);
                    this.secondaryDir = value;
                    
            } else if (argLine.contains(ADDITIONAL_DIR)) {//SEC_DIR
                    String value = getBetween(argLine, ADDITIONAL_DIR, ">").replace(" ", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": Additional read Directory set: " + value);
                    this.secondaryDir = value;
            }

            if (argLine.contains(TAG_URL)) {//URL
                    String value = getBetween(argLine, TAG_URL, ">").replace(" ", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": custom URL set: " + value);
                    this.customUrl = value;
            }

            if (argLine.contains(TAG_VAR_LIST)) {//VAR_LIST
                    String value = getBetween(argLine, TAG_VAR_LIST, ">").replace(" ", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": custom VAR_LIST set: " + value);
                    this.customVars = value; 
            }
            

            if (argLine.contains(TAG_VAR_LIST2)) {//VAR_LIST2
                    String value = getBetween(argLine, TAG_VAR_LIST2, ">").replace(" ", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": custom VAR_LIST2 set: " + value);
                    this.customVars2 = value;
            }

            if (argLine.contains(TAG_READ_VARLIST)) {//VAR_LIST2
                    String value = getBetween(argLine, TAG_READ_VARLIST, ">").replace(" ", "");
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": custom READ VAR_LIST set: " + value);
                    this.readVarList = value.split(",");
            }
            
            if (argLine.contains(ALLOW_MOLE_CONVERSIONS)|| argLine.contains(NC_UNIT_CHECKS)  ) {//VAR_LIST2
                this.ncUnitConversions = true;
            }
            
                    
            if (argLine.contains(READ_ALL_IN)) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"SILAM feed: reading all datasets in reagrdless"
                        + " of the choices modelling variables.");
                this.readAllVars_nc=true;
            }

            //EXCLUDE_SECONDARY_OUT
            if (argLine.contains(RAW_DATA_DUMP)) {
                this.rawDataOut = true;
            }
            //auth and certs
            if (argLine.contains(CERT_BYPASS)) {
                certificates = false;
            } else {
                certificates = true;
            }

            this.creds_usr_pass = g.getFeedUserPasswd(this);
            if (this.creds_usr_pass != null) {
                EnfuserLogger.log(Level.FINE,this.getClass(),
                        argTag + ": user and password taken from encrypted GlobalOptions.");
            }
            //custom data miner ticker (min)?
            if (argLine.contains(CUSTOM_MINER_TICKER)) {//new feature: check the latest version automatically and build an URL body.

                    String value = getBetween(argLine, CUSTOM_MINER_TICKER, ">").replace(" ", "");
                    int ival = Integer.valueOf(value);
                    this.mainProps.ticker_min = ival;
            }//if custom ticker

            //file amount cap
            if (argLine.contains(FILE_AMOUNT_CAP)) {//new feature: check the latest version automatically and build an URL body.

                String value = getBetween(argLine, FILE_AMOUNT_CAP, ">").replace(" ", "");
                int ival = Integer.valueOf(value);
                this.fileAmountCap = ival;

            }//if custom ticker

            if (argLine.contains(PRIORITY_DECREASER)) {
                String value = getBetween(argLine, PRIORITY_DECREASER, ">").replace(" ", "");
                EnfuserLogger.log(Level.INFO,this.getClass(),
                        argTag + ": file priority reduced for files that contain '"+value+"'");
                this.fileReader.addFilePriorityReducer(value,1); 
            }
            
            //custom bounds
            if (argLine.contains(TAG_CUSTOM_AREAS)) {//new feature: check the latest version automatically and build an URL body.

                    String[] values = getBetween(argLine, TAG_CUSTOM_AREAS, ">").replace(" ", "").split(",");
                    this.customAreaBounds = new ArrayList<>();
                    for (String area : values) {
                        //find the bounds
                        Boundaries b = g.findAreaBoundsByName(area);
                        if (b != null) {
                            Boundaries bb = b.clone();
                            this.customAreaBounds.add(bb);
                            EnfuserLogger.log(Level.FINER,this.getClass(),
                                    argTag + ": \tFeed custom area defined: " + bb.toText());  
                        }
                    }

            }//if custom areas

            if (argLine.contains(CUSTOM_NAMES)) {//custom name list

                    String[] values = getBetween(argLine, CUSTOM_NAMES, ">").replace(" ", "").split(",");
                    this.customNames = new ArrayList<>();
                    for (String name : values) {
                        this.customNames.add(name);
                        EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": \tFeed custom name: " + name);
                        
                    }

            }//if custom name list

            if (argLine.contains(TAG_CUSTOM_BOUNDS)) {//custom bounds can be used in some feeds such as OpenSky

                    String[] values = getBetween(argLine, TAG_CUSTOM_BOUNDS, ">").replace(" ", "").split("&");//e.g., <60.1,60.2,24.5,24.6& 60.1,60.2,24.5,24.6& ...>
                    for (String coords : values) {
                        //set the bounds
                        String[] cc = coords.split(",");
                        if (cc==null || cc.length<4) continue;
                        Boundaries bb = new Boundaries(
                                Double.valueOf(cc[0]),
                                Double.valueOf(cc[1]),
                                Double.valueOf(cc[2]),
                                Double.valueOf(cc[3])
                        );

                        this.customB.add(bb);
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                argTag + ": \tFeed custom Bounds defined: " + bb.toText());
                        

                    }//for & 

            }//if custom bounds

            if (argLine.contains(AWS_DUMP)) {//feed store() files are stored to AWS s3

                    String[] values = getBetween(argLine, AWS_DUMP, ">").replace(" ", "").split(",");
                    int minOfHour = Integer.valueOf(values[2]);//must be an integer.
                    this.awsDump_bucketRegSparser = new String[]{values[0], values[1], minOfHour + ""};
                    EnfuserLogger.log(Level.FINER,this.getClass(),argTag + ": \tFeed AWSdump params defined: bucket=" 
                            + values[0] + ", region=" + values[1] + ", minute-of-hour must exceed " + minOfHour);
            }//if AWS



        if (filteredByName(ops)) {
            this.readable = false;
        }
        
        if (argLine.contains(CLEANER_WINDOW_H)) {
                String clean = getBetween(argLine, CLEANER_WINDOW_H,">");
                if (clean!=null) {
                    String[] split = clean.split(",");
                    cleanerWindow = new int[]{Integer.valueOf(split[0]),Integer.valueOf(split[1])};
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "Old data will be cleared from the feed directory "+ this.primaryMinerDir);
                }
       }
        
        if (argLine.contains(Feed.HTTP_REQUEST_PROPS)) {
            try {
            String prop = getBetween(argLine, HTTP_REQUEST_PROPS,">");
            this.reqestProperties_http = new ArrayList<>();
            String [] all = prop.split("&");
            for (String a:all) {
                this.reqestProperties_http.add(a.split(","));
            }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    
    
    public String getStoreDirectory(Dtime dt) {
        return this.primaryMinerDir;
    }
    
    
     /**
     * Get the amount of mined files in the primary miner directory.
     * @return amount of files.
     */
    private int currentAmountOfFiles() {
        File f = new File(this.primaryMinerDir);
        File[] ff = f.listFiles();
        if (ff == null) {
            return 0;
        }
        return ff.length;
    }

    public boolean fileAmountCapped() {
        if (this.fileAmountCap == null) {
            return false;
        }

        int n = this.currentAmountOfFiles();
        if (n > this.fileAmountCap) {
            return true;
        } else {
            return false;
        }
    }
    
     
    public String coordinateAdditionToID(double lat, double lon) {
       int decimals = genID_decimals;
        String ID = (int) (lat * decimals) + "_" + (int) (lon * decimals);
        return ID;
    }
    
        /**
     * Check whether or not the feed's store-operation
     * cannot be performed due to missing credentials/API-key.
     * @return true if credentials are missing.
     * In case the feed does not require credentials this returns false.
     */
    public boolean cannotStore_missingCreds() {
        
        String req =  this.mainProps.apikRequirement;
        if (req.equals(CREDENTALS_NONE)) {
            return false;
        } else if (req.equals(CREDENTALS_UP)) {
            if (this.creds_usr_pass ==null) {
                return true;//requires user-password but none is available
            } else {
                return false;
            }
        } else {//apik
            
            String apik = g.getFeedApik(this);
            if (apik==null) {//needs this but none is available
                return true;
            } else {
                return false;
            }  
        }  
    }

    /**
     * Return a list of variables to be read. This can return null and if so,
     * then all encountered types are read by the feed.
     *
     * @return a list of variable types (integers, as defined in
     * variableAssociations).
     */
    public ArrayList<Integer> getReadVariableList() {
        if (this.readVarList == null) {
            return null;
        }
        ArrayList<Integer> arr = new ArrayList<>();
        for (String s : this.readVarList) {
            int typeInt = ops.VARA.getTypeInt(s);
            if (typeInt >= 0) {
                arr.add(typeInt);
            }
        }
        if (arr.isEmpty()) {
            return null;
        }
        return arr;
    }
    
    public FusionOptions ops() {
        return this.ops;
    }

    public String mineFromURL(String address,String hideAPIk) throws Exception {
        
        if (this.legacyHttpCon) {
           return RegionalMiner.mineFromURL_legacy(!this.certificates, address,
                this.SOUT, this.creds_usr_pass);
        }
        
        return RegionalMiner.mineFromURL_static(!this.certificates, address,
                this.SOUT, this.creds_usr_pass,hideAPIk, reqestProperties_http);
    }

    /**
     * Download a file from URL
     * @param localFileName local filename to be used for download
     * @param simple if true, then a simpler connection approach is used. (My be used if 'false' fails)
     * @param address the URL address
     * @param refresh if true, then the download proceeds even if a non-zero file with
     * the name already exists
     * @return true if a successful download occurs, OR the file already is present.
     * @throws Exception 
     */
    public boolean downloadFromURL(String localFileName, boolean simple,
            String address, boolean refresh) throws Exception {

        return RegionalMiner.downloadFromURL_static(localFileName, simple,
                !this.certificates, address, this.SOUT, this.creds_usr_pass, null, null, refresh);
    }

    /**
     * Push the files specified in the array to AWS S3 cloud. This method does
     * nothing in case no AWS bucket and region has been specified for this
     * Feed. This method also does nothing in case storeTicks%sparser !=0 where
     * 'sparser' is the third argument for AWSDump. For feeds with high update
     * rate (1-5min) it can be beneficial to set the sparser to a paired value
     * e.g., in between [4,10]
     *
     * @param files the list of files to be stored.
     */
    public void AWSdump(ArrayList<String> files) {
        if (this.awsDump_bucketRegSparser == null) {
            return;
        }
        if (files == null || files.isEmpty()) {
            return;
        }

        try {

            int minOfHour = Integer.valueOf(this.awsDump_bucketRegSparser[2]);
            Dtime current = Dtime.getSystemDate();
            if (current.min < minOfHour) return;

            ERCFarguments args = ops.getArguments();

            String replacement = args.getMinerDir(); //e.g., D:/EnfuserArchive/
            CloudStorage io = ops.getArguments().getAWS_S3(true);
            if (io == null) {
                return;
            }

            String bucket = this.awsDump_bucketRegSparser[0];
            String region = this.awsDump_bucketRegSparser[1];
            ArrayList<String[]> argA = new ArrayList<>();
            argA.add(new String[]{"bucket", bucket});
            argA.add(new String[]{"region", region});
            io.setParameters(argA);

            EnfuserLogger.log(Level.FINER,this.getClass(),"Opening AWS S3 connection...");
            GlobOptions.get().setHttpProxies();
            try {
                io.establishConnection();
                EnfuserLogger.log(Level.FINER,this.getClass(),"Connected.");
            } catch (Exception e) {
                this.printStackTrace(e);
                return;
            }

            for (String filename : files) {
                String key = filename.replace(replacement, "");
                this.SOUT.println("\t" + filename);
                this.SOUT.println("\t\t put: " + key + " to " + region + "," + bucket);
                //s3t.put(filename, key, null);
                io.put(filename, key, null, null);
            }

        } catch (Exception e) {
            this.printStackTrace(e);
        }

    }

    public String defaultHourlyFileName(String add, String format) {
        return defaultHourlyFileName(0,add, format);
    }
    
    public String defaultHourlyFileName(int hourAdd,String add, String format) {
    Dtime dt = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);
    if (hourAdd!=0) {
        dt.addSeconds(3600*hourAdd);
    }
    String dt_str = dt.getStringDate(Dtime.STAMP_NOZONE) + "Z";
    String localFileName = this.mainProps.feedDirName + "_" + dt_str.replace(":", "-") + add + format;
    return localFileName;
    }

    /**
     * To be used after Setup (RegionOptions file processing). For some of the
     * feeds, post-processing of the regional arguments may be necessary in case
     * the settings are too complicated to set-up properly during setup. As
     * default, a clear majority if not all Feeds need not to Override this
     * method. However, the feeds that need post processing for their settings
     * should override this method.
     *
     * @param ops the options for the region
     * @param regFileName The full file name (with path) pointing towards the
     * regional setting file, e.g., Region_Finland.csv.
     */
    public void makeRegionalOptionsEdits_setup(FusionOptions ops, String regFileName) {
        //default: do nothing.
        //If something needs to be done: Override.
    }

    public void releaseStorageSpace(String storageDir) {

    }

    private boolean filteredByName(FusionOptions ops) {
        if (ops.filterReads.contains(this.mainProps.feedDirName)) {
            return true;
        }
        return false;
    }

    public String[] vars() {
        String s = this.customVars;

        if (s == null || s.contains(DEF_VARS)) {
            return this.defaultVars;
        } else {
            return s.split(",");
        } 
    }

    protected String[] sec_vars() {
        String s = this.customVars2;

        if (s == null || s.contains(DEF_VARS)) {
            return this.defaultVars2;
        } else {
            return s.split(",");
        }
    }

    public void printout() {
        EnfuserLogger.log(Level.FINER,this.getClass(),"Feed " + this.mainProps.feedDirName + " ======================");
        EnfuserLogger.log(Level.FINER,this.getClass(),"readable = " + this.readable + ", stores = " + this.stores);

        EnfuserLogger.log(Level.FINER,this.getClass(),"dir = " + this.primaryMinerDir);
        if (this.secondaryDir != null) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"has secondary dir: " + this.secondaryDir);
        }

        if (this.customUrl != null) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"has custom URL: " + this.customUrl);
        }

    }
    
    protected void storeTick() {
        storeTicks++;
        cleanPrimaryDir();
    }
    
    protected void cleanPrimaryDir() {
        if (storeTicks%10==0 && cleanerWindow!=null) {
            this.SOUT.println("Primary directory cleanup initiated.");
            int TOO_FRESH_TO_DELETE = cleanerWindow[0];
            int TOO_OLD_TO_DELETE = cleanerWindow[1];

            FileCleaner.cleanSingleDir(this.SOUT,this.primaryMinerDir,
                    TOO_FRESH_TO_DELETE, TOO_OLD_TO_DELETE);
        }
    }

    public static void renameFiles(String dir, String target, String replacement) {
        int k = 0;
        EnfuserLogger.log(Level.FINER,Feed.class,
                "Renaming files under " + dir + ", " + target + " => " + replacement);
        File file = new File(dir);
        File[] ff = file.listFiles();
        for (File test : ff) {
            String nam = test.getName();//helsinkiMetData_2018-01-01-t0000.grib
            if (!nam.contains(target)) {
                continue;
            }
            String newNam = nam.replace(target, replacement);
            test.renameTo(new File(dir + newNam));
            k++;
        }

        EnfuserLogger.log(Level.FINER,Feed.class,"Files renamed: " + k);
    }

    public void setSout_forStore() {
        if (this.SOUT != System.out) {
            SOUT.close();
        }
        //delete previous file
        File f = new File(this.storeSout_fnam);
        if (f.exists()) {
            f.delete();
        }
        try {
            SOUT = new PrintStream(new BufferedOutputStream(new FileOutputStream(this.storeSout_fnam)));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Feed.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void setSout_forStore(String rootdir, boolean on, Streamer o, Streamer er) {

        if (on) {
            resetStreamerFiles(o, er, rootdir, this.storeSout_fnam,
                    this.storeSout_fnam.replace(".txt", "_err.txt"), true);
        } else {
            resetStreamerFiles(o, er, rootdir, null, null, false);
        }
    }

    /**
     * Reads data from archives and adds Observations to DataPack or FusionCore
     * directly.Return a value that corresponds to the amount of data read.
     *
     * @param dp The DataPack instance where the read information is stored.
     * This is passed on to FusionCore later on.
     * @param p run parameters define the time span, area and variables for load.
     * @param DB data base for ID's
     * @param mp some custom feeds may need map information for input processing
     * In most cases this can be null (but not for Here-feed)
     */
    public abstract void read(DataPack dp,RunParameters p, DataBase DB,MapPack mp);

    /**
     * Extract online data and archive it. Returns true for successful
     * operation.
     *
     * @return a list of files that was stored.
     */
    public abstract ArrayList<String> store();

    /**
     * Form an argument line for setup suitable for this feed, taking into account
     * the list of modelling bounds (areas).For areas for which the feed is
     * irrelevant, the line should contain DISABLE_READ, DISABLE_WRITE tags.
     * @param bounds
     * @param areaNames
     * @param globalAsDefault
     * @param write
     * @param custom
     * @param read
     * @return 
     */
    public String getDefaultArgumentLine(ArrayList<Boundaries> bounds,
            ArrayList<String> areaNames, boolean globalAsDefault,
            boolean write, String custom, boolean read) {
        
        String reads = DISABLE_READ+"";
        String writes = DISABLE_WRITE+"";
        String glob = "";
            if (globalAsDefault) glob = GLOBAL_MODE+"";
        String other =custom+"";
        
        
        boolean hits =false;
        Boundaries relev = this.mainProps.relevanceBounds;
        for (Boundaries b:bounds) {
            if (relev.intersectsOrContains(b)) hits = true;
        }
        
        if (hits) {//plausable
            reads ="";
            writes ="";
        } 
        //final rules
        if (!write) writes = DISABLE_WRITE+"";
        if (!read) reads = DISABLE_READ+"";
        
        return reads +";"
                +writes +";" 
                +glob +";" 
                +other+";"; 
    }
    
    public String getFullFileName_generic(Dtime dt) {
        String body = this.primaryMinerDir + this.mainProps.feedDirName + "_";
        String ftype = this.mainProps.formats[0];
        String date = dt.getStringDate_fileFriendly() + "-00-00";
        if (!date.contains("Z")) {
            date += "Z";
        }
        return body + date + ftype;
    }

    private ArrayList<File> getReadDirs(Dtime start, Dtime end) {
        ArrayList<File> arr = new ArrayList<>();
        
        ArrayList<String> mains = new ArrayList<>();
        mains.add(this.primaryMinerDir);
        if (secondaryDir != null) {
             mains.add(this.secondaryDir);
         }
        int[] yrs = {start.year};
        if (start.year != end.year) yrs = new int[]{start.year,end.year};
        
        for (String dir:mains) {
            File f = new File(dir);
            arr.add(f);
            for (int y:yrs) {
                String yrdir = dir + y +FileOps.Z;
                File yf = new File(yrdir);
                if (yf.exists()) arr.add(yf);
               
            }
        }
        return arr;
    }
    
    /**
     * Form a list of relevant input data files in the primary (or as well in the secondary) 
     * miner directory. The files that meet the temporal and bounding box limits
     * will be placed on a hash, for which time is used as the hashcode.
     * @param b bounding box limit for data load. In case the input files
     * define data bounding box, then this can be used to filter
     * unnecessary data to be loaded.
     * @return hash of relevant input data files.
     */
    public HashMap<Long, File> getFilesToRead(
            RunParameters p, Boundaries b) {
        
        HashMap<Long, File> hash = new HashMap<>();
        Parser parser = new Parser(this.primaryMinerDir);
        ArrayList<File> tempDirs = getReadDirs(p.loadStart(),p.end()); 
           
      for(File f:tempDirs) {   
        File[] files = f.listFiles();
        if (files != null) {
            EnfuserLogger.log(Level.FINER,this.getClass(),
                "Checking files from "+f.getAbsolutePath() +" ("+files.length+")");
            for (File add : files) {
                //delete zero files, don't read directories
                boolean deleted = this.deleteZeroFile(add);
                if (deleted) {
                    continue;
                }
                if (add.isDirectory()) continue;
                
                EnfuserLogger.log(Level.FINER,this.getClass(),"Checking feed input "+ add.getAbsolutePath());
                if (!this.fileReader.fileBoundsAcceptable(parser,add,b)) continue;
                Long secs = this.fileReader.fileDatesAcceptable(parser,add,p,mainProps.checkSpanH);
                if (secs==null) continue;

                File existing = hash.get(secs);
                if (existing!=null 
                        && !existing.getName().equals(add.getName())) {//exists for same time but the name is different
                    int k =0;
                    while (existing!=null && k < 3600) {//change time slightly then
                        secs++;//increase time
                        k++;
                        if (k==3600) {
                            EnfuserLogger.log(Level.WARNING, this.getClass(), "while loop "
                                    + "maxed for file listing: "+ add.getAbsolutePath());
                        }
                        existing = hash.get(secs);
                    }//for 60 seconds
                }//if reserved
                
                hash.put(secs, add);    
            }//for files in list
        }//if non-null
      }//for dirs


        EnfuserLogger.log(Level.FINER,this.getClass(),
                "Preliminary list of files include "+ hash.size() +" for " + this.primaryMinerDir);
         
        parser.getExceptionPrintout(this.getClass());
        if (GlobOptions.get().isLogged(Level.FINER)) {
            for (Long key:hash.keySet()) {
                 EnfuserLogger.log(Level.FINER,this.getClass(),
                "\t"+ key +"\t\t" +hash.get(key));
            }
        }
        return hash;
    }

    public ArrayList<GeoGrid> getSpecificNetCDF_generic(String filename,
            int typeInt, Dtime start, Dtime end) {

        NetcdfHandle ncHandle;
        VarConversion vc = null;
        String ncV = null;

        //cross-check variables
        try {
            ncHandle = new NetcdfHandle(filename);
            for (String ncVar : ncHandle.varNames) {
                VarConversion vctest = ops.VARA.allVC_byName.get(ncVar);
                if (vctest != null && vctest.typeInt == typeInt) {
                    vc = vctest;
                    ncV = ncVar;
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        int heightIndex = 0;

        try {
            ncHandle = new NetcdfHandle(filename);
            ncHandle.loadVariable(ncV);
            if (ncHandle.loadedVar == null || vc == null
                    || !ncHandle.loadedVar.equals(vc.specialTypeName)) {
                return null;//load was not successful, the variable is not listed in the file.
            }

            ArrayList<GeoGrid> arr = null;
            try {
                arr = ncHandle.getSubsetOnGrid(heightIndex, start, end, b, vc.scaler_offset, null);
            } catch (Exception e) {
                
                ncReadErrs++;
                if (ncReadErrs < 10) {
                        String msg = "ncHandle.getSubsetOnGrid Exception with " + filename + ", " + vc.specialTypeName;
                        EnfuserLogger.log(e,Level.WARNING,this.getClass(),msg);   
                }
            }

            ArrayList<GeoGrid> procs = new ArrayList<>();
            for (GeoGrid g : arr) {

                //check longitude definition and adjust
                if (b.lonmin > 180 || b.lonmax > 180) {
                    Boundaries b2 = b.clone();
                    if (b2.lonmin > 180) {
                        b2.lonmin -= 360;
                    }
                    if (b2.lonmax > 180) {
                        b2.lonmax -= 360;
                    }
                    g = new GeoGrid(g.values, g.dt.clone(), b2);//gridBounds is 'final' so must create a new GeoGrid.
                    procs.add(g);
                } else {
                    procs.add(g);
                }
            }
            ncHandle.close();
            return procs;

        } catch (Exception eee) {
            eee.printStackTrace();
        }

        return null;
    }

    private int ncReadErrs = 0;

    private ArrayList<String> addNetCDF(HashMap<Long, File> fileHash, DataPack dp,
            RunParameters p, DataBase DB, ArrayList<String> types, Boundaries b) {

        ArrayList<String> readFiles = new ArrayList<>();
        String name = mainProps.feedDirName;
        DBline dbl = DB.get(name);
        Dtime start = p.loadStart();
        Dtime end = p.end();
        NetcdfHandle ncHandle;
        ArrayList<VarConversion> varsToRead = new ArrayList<>();
        //get timewise sorted keys for files
        ArrayList<Long> keys = new ArrayList<>();
        for (long k : fileHash.keySet()) {
            keys.add(k);
        }
        Collections.sort(keys);//from smallest to largest. This is VERY important! Iterating the data this way older data gets automatically replaced by newer model data.
        for (File f:fileHash.values()) {
            
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "NEW READ prelist: " + f.getAbsolutePath() + "\n==================================================");
            
        }

        HashMap<String, VarConversion> feedVars_converted_all = ops.VARA.allVC_byName;//this.getVarConversions(types);
        //filter some of the varConversions
        HashMap<String, VarConversion> feedVars_converted = new HashMap<>();
        for (String key : feedVars_converted_all.keySet()) {
            VarConversion vc = feedVars_converted_all.get(key);
            if (types.contains(vc.baseType)) {
                feedVars_converted.put(key, vc);
            }
        }

        for (long key : keys) {
            File f = fileHash.get(key);
            String filename = f.getAbsolutePath();
            int priorityDecreaser = this.fileReader.getFilePriorityReducer(f.getName());
            varsToRead.clear();
            //cross-check variables
            try {
                ncHandle = new NetcdfHandle(filename);
                for (String ncVar : ncHandle.varNames) {
                    VarConversion tester = feedVars_converted.get(ncVar);
                    
                    if (tester != null) {
                        varsToRead.add(tester);//the file contains this variable!
                    }
                }
            } catch (Exception e) {
                EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                        "netCDF file failed to open: "+ filename);  
            }

            boolean added = false;
            for (VarConversion vc : varsToRead) {

                int heightIndex = 0;

                try {
                    ncHandle = new NetcdfHandle(filename);
                    ncHandle.loadVariable(vc.specialTypeName);
                    if (ncHandle.loadedVar == null || !ncHandle.loadedVar.equals(vc.specialTypeName)) {
                        continue;//load was not successful, the variable is not listed in the file.
                    }

                    ArrayList<GeoGrid> arr = null;
                    try {
                        arr = ncHandle.getSubsetOnGrid(heightIndex, start, end, b, vc.scaler_offset, null);

                    } catch (Exception e) {
                        ncReadErrs++;
                        if (ncReadErrs < 10) {   
                            String msg = "ncHandle.getSubsetOnGrid Exception with " + filename + ", " + vc.specialTypeName;
                            EnfuserLogger.log(e,Level.WARNING,this.getClass(),msg);       
                        }
                    }

                    for (GeoGrid g : arr) {
                        //check longitude definition and adjust
                        if (b.lonmin > 180 || b.lonmax > 180) {
                            Boundaries b2 = b.clone();
                            if (b2.lonmin > 180) {
                                b2.lonmin -= 360;
                            }
                            if (b2.lonmax > 180) {
                                b2.lonmax -= 360;
                            }
                            g = new GeoGrid(g.values, g.dt.clone(), b2);//gridBounds is 'final' so must create a new GeoGrid.
                        }
                           
                        if (this.ncUnitConversions) {
                            MoleConversion.convertToUgm3(ncHandle, g, filename);
                        }
                        if (priorityDecreaser>0) g.dt.freeByte+=(byte)priorityDecreaser;//larger value=> this dataset
                        //could be replaced with another one with LOWER value
                        
                        if (this.sourceNameByNcFile) {
                            try {
                                String nameTest = this.fileReader.getFileID(f);
                                dbl = DB.get(nameTest);
                                if (dbl==null) {
                                    dbl = DB.storeGridSource(nameTest);
                                }

                            }catch (Exception e) {
                               EnfuserLogger.log(Level.WARNING,this.getClass(),
                                "sourceNameByNcFile: Cannot parse source name from "+ filename);
                            }
                        }//if custom name
                        
                        dp.addGrid(dbl, (byte) vc.typeInt, g, filename, DB,ops);
                        if (!added) {
                            readFiles.add(filename);//data was read from this file!
                        }
                        added = true;
                    }

                    ncHandle.close();

                } catch (Exception eee) {
                    eee.printStackTrace();
                }
            }// for types 
        }// for filenames
        return readFiles;
    }

    public ArrayList<String> addGrib_generic(HashMap<Long, File> fileHash, DataPack dp,
            RunParameters p, DataBase DB, ArrayList<String> types, Boundaries b) {

        ArrayList<String> readFiles = new ArrayList<>();
        DBline dbl = DB.get(this.mainProps.feedDirName);
        Dtime start = p.loadStart();
        Dtime end = p.end();
        //get timewise sorted keys for files
        ArrayList<Long> keys = new ArrayList<>();
        for (long k : fileHash.keySet()) {
            keys.add(k);
        }
        Collections.sort(keys);//from smallest to largest. This is VERY important! Iterating the data this way older data gets automatically replaced by newer model data.
        for (long key : keys) {
            String filename = fileHash.get(key).getAbsolutePath();
              EnfuserLogger.log(Level.FINE,this.getClass(),
                      "NEW READ prelist: " + filename + "\n==================================================");
            
        }

        HashMap<String, VarConversion> feedVars_converted = ops.VARA.allVC_byName;
        HashMap<String, Byte> th = new HashMap<>();
        HashMap<String, float[]> scalers = new HashMap<>();
        for (String key : feedVars_converted.keySet()) {
            VarConversion vc = feedVars_converted.get(key);

            th.put(vc.specialTypeName, (byte) vc.typeInt);
            scalers.put(vc.specialTypeName, vc.scaler_offset);
                EnfuserLogger.log(Level.FINE,this.getClass(),"ADDING " + vc.specialTypeName);
            
        }

        for (long key : keys) {

            String filename = fileHash.get(key).getAbsolutePath();

            try {
                HashMap<String, GeoGrid> hgs = GribReader.getSubsetsOnGrid(false, filename,
                        th, start, end, b, scalers);

                for (String hkey : hgs.keySet()) {
                    GeoGrid g = hgs.get(hkey);
                    int typeInt = Integer.valueOf(hkey.split("_")[0]);
                    //check longitude definition and adjust
                    if (b.lonmin > 180 || b.lonmax > 180) {
                        Boundaries b2 = b.clone();
                        if (b2.lonmin > 180) {
                            b2.lonmin -= 360;
                        }
                        if (b2.lonmax > 180) {
                            b2.lonmax -= 360;
                        }
                        g = new GeoGrid(g.values, g.dt.clone(), b2);//gridBounds is 'final' so must create a new GeoGrid.
                    }
                    // if (krigingForZero) Enhancer.fillKriging(false, g, 2, 0);
                    if (dp != null) {
                        dp.addGrid(dbl, (byte) typeInt, g, filename, DB,ops);
                    }
                    readFiles.add(filename);//data was read from this file!
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "added " + hgs.size() + " geoGrids from " + filename);
                }

            } catch (Exception eee) {
                EnfuserLogger.log(eee, Level.WARNING, this.getClass(),
                        "Data addition from GRIB failed: "+ filename);
            }

        }// for filenames

        return readFiles;
    }

    public final static double[] DEFAULT_EXPANSION = {50,0};//50km addition to read area (grids)
    public final static boolean NETCDF =true;
    public final static boolean GRIB=false;
    public final static boolean HOUR_FILL =true;
    public final static boolean NO_FILL=false;
    
    /**
     * Read all relevant netCDF data from archives based on the dates and area set in
     * RunParameters. 
     * @param NETCDF if true, then as NETCDF, false = grib
     * @param dp read gridded datasets will be added to this instance.
     * @param DB database for ID's
     * @param p run parameters (dates, area, variables)
     * @param km_fracInc [area_increase_flat_km, areaIncreaseFactor]. Most
     * of the datasets should cover the vicinity of the modelling area. Default:
     * [50,0] which will add 50km to all directions.
     * @param fillMissing if true, then after dataset insertion a gap-filler
     * algorithm is used. This is NEEDED for data that is in 3h, 6h interval (GHS, ECMWF).
     * At the end there should be hourly data inserted.
     */
    public void readGridData(boolean NETCDF, DataPack dp, DataBase DB, RunParameters p,
            double[] km_fracInc, boolean fillMissing) {
        
        Boundaries readBounds;
        if (km_fracInc!=null) {
            readBounds = p.boundsCustomExpanded(km_fracInc[0],km_fracInc[1]);
        } else {
            readBounds= p.bounds().clone();
        }
        
        //there are some (e.g., GFS) custom feeds that do not use the intended coordinate/Boundaries system.
        Feeder.modifyGridBoundariesForReading(readBounds,this);
        
        ArrayList<String> vars = p.loadVars(ops);
        VarAssociations VARA = ops.VARA;
        ArrayList<String> readVars = vars;
        if (this.readAllVars_nc) {
            readVars = new ArrayList<>();
            for (Integer typeI: VARA.allVars()) {
                readVars.add(VARA.VAR_NAME(typeI));
            }
        }
        
        HashMap<Long, File> hash = getFilesToRead(p,p.bounds());
        if (NETCDF) {
             this.addNetCDF(hash, dp, p, DB, readVars, readBounds);
        } else {
             this.addGrib_generic(hash, dp, p, DB, readVars, readBounds);
        }
       
        if (fillMissing)interpolateMissingLayers(this, dp, vars);
    }
    
    /**
     * For measurement data parsing done by FileParser.This can be overridden
     * and used for finishing touches for added new Observation. Use-case:
     * AQICN feed that needs AQI-conversions to happen.
     * @param o the observation
     * @param vc
     */
    public void reformatParsedObservation(Observation o, VarConversion vc) {
        if (vc!=null) {
           vc.applyScaleOffset(o);
        }
    }

    public boolean specialTextParser(String[] splitline, Parser safeParser,
            DBline dbl, Dtime dtc, DataPack dp, DataBase DB, MapPack mp) {
        return false;
    }
    
    public void readTextData(DataPack dp, RunParameters p, DataBase DB, MapPack mp) {
        ArrayList<String> vars = p.loadVars(ops);
        //get list of files
        HashMap<Long, File> hash = getFilesToRead(p,p.bounds());
        //read files.
        HashMap<Integer,String> varhash = new HashMap<>();
        for (String var:vars) {
            int typeInt = ops.VARA.getTypeInt(var);
            if (typeInt>=0) varhash.put(typeInt,var);
        }
        readText_generic(hash,dp, varhash, DB,ops.boundsClone(),mp);
    }
    
    private void readText_generic(HashMap<Long, File> fileHash,
            DataPack dp, HashMap<Integer,String> vars, DataBase DB,  Boundaries b, MapPack mp) {

        EnfuserLogger.log(Level.FINER,this.getClass(),"#¤%#%¤#%¤#%¤ readText files #¤%#%¤#¤%#%¤#%¤ - " + this.primaryMinerDir);

        ArrayList<String> content;
        
        HashMap<String, Integer> varHash = new HashMap<>();
        for (byte i = 0; i < ops.VARA.VAR_LEN(); i++) {
            String s = ops.VARA.VAR_NAME(i);
            varHash.put(s, ops.VARA.getTypeInt(s));
        }

        Parser safeParser=null;
        for (Long key : fileHash.keySet()) {
            String filen = fileHash.get(key).getAbsolutePath();
            File file = new File(filen);
            if (safeParser==null){
                safeParser =new Parser(filen);
            } else {
                safeParser.switchFile(filen);
            }
           
            content = safeParser.readStringFile(file); //read raw lines
            if (content.isEmpty()) continue;
            String header = content.get(0);
            this.fileReader.setFileForParsing(safeParser,file, header, this,vars);
                boolean first = true;
                for (String line : content) {//for lines in file
                    if (first) {
                        first =false;
                        if (this.fileReader.skipFirstLine)continue;
                    }
                        this.fileReader.parseTextObservations(dp, this,line,
                                vars, b, safeParser,DB,mp);
                }//for content
                
            EnfuserLogger.log(Level.FINER,"Added " +fileReader.recentAdditions() +" obs from " + filen);    
        }//for files
        if (safeParser!=null) safeParser.getExceptionPrintout(this.getClass());
        EnfuserLogger.log(Level.FINER,this.getClass(),"#¤%#%¤#%¤#%¤END OF  "
                + "readText files #¤%#%¤#¤%#%¤#%¤");
    }

    public void println(String string) {
        //EnfuserLogger.log(Level.FINER,this.getClass(),string);
        SOUT.println(string);
        if (this.soutFlush) SOUT.flush();
        if (this.forkSOUT) {
            EnfuserLogger.log(Level.FINER,this.getClass(),string);
        }
    }

    public void printStackTrace(Exception e) {
        e.printStackTrace(SOUT);
        if (this.forkSOUT) {
            e.printStackTrace();
        }
    }

    protected boolean deleteZeroFile(File add) {

        if (add.length() > 0) {
            return false;
        } else {
            //add.delete();//TODO test that it is safe to do this.
            return true;
        }

    }
    
    public abstract boolean readsInitially();
    public abstract boolean globalInitially();
    public abstract boolean writesInitially();
    public abstract String customArgumentInitially(ArrayList<String> areaNames);

}
