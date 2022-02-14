/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import org.fmi.aq.enfuser.core.EnfuserLaunch;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import static org.fmi.aq.enfuser.ftools.FuserTools.correctDir;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOpsCreator;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.interfacing.Guard;

/**
 * This static class defines Global Options that affect some of supporting
 * parameters for the model. This is loaded only ONCE when the model is
 * initiated (where as e.g., FusionOptions can be loaded multiple times through
 * enfuserGUI, and is loaded separately for different regions)
 *
 * One of the key use cases for this class is that it is loaded first and it
 * anchors the used root directory, that is to be used for data loading in all
 * other operations from then on. This means that all classes that require the
 * root directory use the getRootDir() method here, which is a static method in
 * a static class. In case this GlobOptions has not been setup yet, it will be
 * setup when the getRootDir() is used of the first time.
 *
 * In addition to root directory, several other properties that are not specific
 * to any region are defined here. That's why this is called as the "Global"
 * options. This class also holds the "EncryptedOptions" which is used to manage
 * API-keys and possible regional/temporal locks for the modelling system.
 *
 *
 * @author johanssl
 */
public class GlobOptions {
    
    //this can be set to false ONLY when a crypt instance is being created.
    private static GlobOptions gops;
    public final static String GLOB_OPS = "globOps.txt";
    public final static String I_PROXY_SETTINGS =  "PROXY_SETTINGS";//port,address

    public final static String I_ADMIN_EMAIL = "ADMIN_EMAIL";
    public final static String I_MAILER = "MAILER_SERVICE";
    public final static String I_DAEMON_MODE = "DAEMON_MODE";
    public final static String I_DEF_IO_ROOT = "DEF_IO_ROOT";
    public final static String I_SRTM_DIR = "SRTM_DIR";
    public final static String I_S2_DIR = "S2_GLOBAL_DIR";
    public final static String I_LOGGING_LEVEL = "LOGGING_LEVEL";
    public final static String I_DISABLE_BUSYLOGGER = "DISABLE_BUSYLOGGER";
    public final static String I_UNDEF_RUNMODE = "RUNMODE_WHEN_UNDEFINED";
    public final static String I_DISABLE_MINER_BEACON = "DISABLE_MINER_BEACON";
    public final static String I_GHS_SERVER_URL = "GHS_SERVER_URL";
    public final static String I_ES_URL ="ENFUSER_SERVICE_URL";
     
    public final static String I_REGDATA_FROM_EA= "REGIONAL_DATA_FROM_ARCHIVES";
    public final static String I_CUSTOM_MINER_DIR= "CUSTOM_MINERDIR";

    public final static String[] GLOPS_KEYS = {
        I_PROXY_SETTINGS,
        I_ADMIN_EMAIL,
        I_MAILER,
        I_DAEMON_MODE,
        I_DEF_IO_ROOT,
        I_SRTM_DIR,
        I_S2_DIR,
        I_LOGGING_LEVEL,
        I_DISABLE_BUSYLOGGER,
        I_UNDEF_RUNMODE,
        I_DISABLE_MINER_BEACON,
        I_GHS_SERVER_URL,
        I_REGDATA_FROM_EA,
        I_CUSTOM_MINER_DIR,
        I_ES_URL    
    };

    public final static String DEF_MINER_DIR = "EnfuserArchive";
    public final static String DEF_MASTER_OUT = "Enfuser_out_master";
   
    private final HashMap<String, String> args = new HashMap<>();
    private Level loglevel=Level.INFO;
    private boolean disableBusyLogger = false;
    
    final String ROOT;
    private final String LAUNCH_ROOT;
    
    public Regions allRegs;
    public Categories CATS;
    private volatile FusionOptions firstOps= null; 
    /**
     * If set true, then the regional configuration files will be read from
     * the EnfuserArchive instead of the model installation directory.
     * default: false.
     */
    boolean regionFromArchives = false;
    private Guard guard;
    public GlobOptions() {
        String[] actual_root = RootdirSwitch.getRootDirSwith();
        this.LAUNCH_ROOT = correctDir(actual_root[0]);
        this.ROOT = correctDir(actual_root[1]);
        EnfuserLogger.log(Level.FINER,this.getClass(),
                "Launched from "+ LAUNCH_ROOT +", switching to "+ROOT);
        this.guard = new Guard(ROOT);
        
        this.init();
        this.allRegs = new Regions(this.ROOT);
        LaunchTriggers.addLaunchTriggers(allRegs, ROOT);
        this.CATS = new Categories(ROOT);
        checkERCFboundLocs();
    }
    
    
   public static GlobOptions get() {
       if (gops==null) gops = new GlobOptions();
       return gops;
   }

    public static boolean initDone() {
      return gops!=null;
    }
   
  
   public FusionOptions getForFirstRegion() {
       
      if (firstOps!=null) return firstOps; 
      String reg = this.allRegs.mainNames()[0];
      String area = this.allRegs.getRegion(reg).getAllModellingAreaNames()[0];
      EnfuserLogger.log(Level.INFO, this.getClass(), 
              "Creating a FusionOptions instance based on region '"+reg+"'");
      firstOps = new FusionOptions(reg,area);
      return firstOps;
   }
    
    /**
     * The creation of some logging information can take a couple of nano-seconds
     * here and there, but for some methods the creation of logging actions
     * should be avoided. For Level.FINEST logs, this can be done by
     * using this method.
     * @return true if logging should be done.
     */
    public static boolean allowFinestLogging() {
       if (gops==null) return false;
       return gops.loglevel== Level.FINEST;
    }
   
    
    public ModellingArea getModellingArea(String loc) {
        return allRegs.getModellingArea(loc);
        
    }
    
    /**
     * Go over all the regions and modelling areas.
     * In case crypts.dat has a regional lock and one or more of the bounds
     * are not within the limit, the program terminates.
     */
    private void checkERCFboundLocs() {
        if (guard.hasRegionLock()) {
            for (ERCF e:allRegs.ercfs) {
                EnfuserLogger.log(Level.INFO,GlobOptions.class,
                "Checking bounds for "+ e.name+"...");
                guard.checkRegionLocking(e.mainBounds);
                for (ModellingArea a: e.areas) {
                    EnfuserLogger.log(Level.INFO,GlobOptions.class,
                    "\tChecking bounds for "+ a.name+"...");
                  
                    guard.checkRegionLocking(a.b);
                }//for areas
            }//for regs
        }//if locked
    }
    
        public ERCF getRegion(String reg) {
        return allRegs.getRegion(reg);
    } 
        
    public static void reload() {
        EnfuserLogger.log(Level.INFO,GlobOptions.class,
                "GlobOptions will be loaded again now.");
        gops = new GlobOptions();
    }    
    
    public void updateRegionChanges() {
        allRegs = new Regions(this.ROOT);
        LaunchTriggers.addLaunchTriggers(allRegs, ROOT);
        checkERCFboundLocs();
    }
    
    
    public String getRootDir() {
        return ROOT;
    }    

    public String enfuserArchiveDir(String regName) {
        
        String dir = this.args.get(I_CUSTOM_MINER_DIR);
        if (dir==null) {
            String io = defaultIOroot();
            dir = io + DEF_MINER_DIR +Z;
        }
        
        if (regName!=null) dir+=regName+Z;
        return dir;
    }
    
    public String dataDirCommon() {
        
        String dir = this.getRootDir() + "data" +Z;
        return dir;
    }

    
    public final static String EA_ADD = "EnfuserRegionalData";
    public String regionalDataDir(String regName) {
       
        if (regionFromArchives) {
            String dir = this.enfuserArchiveDir(regName);
            dir += EA_ADD +Z;
            //it is possible that this directory does not exist yet
            //File f = new File(dir);
            //if (!f.exists()) f.mkdirs();
            
            return dir;
        } else {
            String dir = this.getRootDir() +"data" +Z +"Regions"+ Z;
            if (regName!=null)dir+= regName+Z;
            //File f = new File(dir);
            //if (!f.exists()) f.mkdirs();
            
            return dir;
        }
        
    }
    
    public String masterRegionalOutDir(String regName) {
        String io = defaultIOroot();
        
        String dir = io + DEF_MASTER_OUT +Z;
        if (regName!=null) dir+=regName+Z;
        return dir;
    }
    
    
    /**
     * Get the setting value stored for a given key index. The value, that is
     * returned, is the one that has been defined in GlobOptions.txt for this
     * specific key.
     *
     * @param key the key, pointing to one of the elements of GLOPS_KEYS.
     * @return hashed value for the key index.
     */
    public String get(String key) {
        return args.get(key);
    }

    /**
     * Load a feed-dependent API-key from EncryptedOptions. The Feed's argument
     * tag, that is, the one used in RegionalOptions (e.g.,SILAM_ARGS) is used
     * to fetch the associated value.
     *
     * @param f the feed
     * @return Feed API-key as a String. Returns null if no such mapping has
     * been set for this Feed.
     */
    public String getFeedApik(Feed f) {
        return guard.getEncryptedProperty(f.mainProps.argTag);
    }

    /**
     * Build a key that is used for EncryptedOptions to store Feed-related
     * "user" token for online data mining
     *
     * @param s the Feed argument name, e.g., SILAM_ARGS
     * @return the key used in EncryptedOptions
     */
    public String feedUserKey(String s) {
        return s + "_USER";
    }

    /**
     * Build a key that is used for EncryptedOptions to store Feed-related
     * "password" token for online data mining
     *
     * @param s the Feed argument name, e.g., SILAM_ARGS
     * @return the key used in EncryptedOptions
     */
    public static String feedPasswordKey(String s) {
        return s + "_PWORD";
    }

    public String feedPasswordKey(Feed f) {
        return feedPasswordKey(f.mainProps.argTag);
    }
    public String feedUserKey(Feed f) {
        return feedUserKey(f.mainProps.argTag);
    }
    /**
     * Return user and password information for a feed, as defined and stored in
     * EncryptedOptions. Returns null if either of the two is missing in
     * EncryptedOptions
     *
     * @param f the feed
     * @return String[]{user,password}
     */
    public String[] getFeedUserPasswd(Feed f) {
        String usr = guard.getEncryptedProperty(feedUserKey(f.mainProps.argTag));
        String pwd = guard.getEncryptedProperty(feedPasswordKey(f.mainProps.argTag));
        if (usr == null || pwd == null) {
            return null;
        }
        return new String[]{usr, pwd};
    }

    /**
     * Fetch a custom property from EncryptedOptions
     *
     * @param key key value that is used for the fetch operation from
     * EncryptedOptions's hash.
     * @return stored value, can be null.
     */
    public String getEncryptedProperty(String key) {
        return guard.getEncryptedProperty(key);
    }

    public boolean serverDaemon() {

        String daemon = get(I_DAEMON_MODE);
        if (daemon == null) {
            return false;//base assumption
        }
        String test = daemon.toLowerCase();
        if (test.contains("true")) {
            return true;
        } else {
            return false;
        }

    }

    public boolean minerBeaconDisabled() {

        String beaconDisabled = get(I_DISABLE_MINER_BEACON);
        if (beaconDisabled == null) {
            return false;//base assumption: it's on.
        }
        String test = beaconDisabled.toLowerCase();
        if (test.contains("true")) {
            return true;
        } else {
            return false;
        }

    }
    
     public String ghsServerUrl() {
        return get(I_GHS_SERVER_URL);
    }   

    public String runmodeWhenUndefined() {

        String runm = get(I_UNDEF_RUNMODE);
        if (runm == null) {
            return EnfuserLaunch.RUNM_MANUAL;//base assumption
        }
        return runm;
    }

    public String[] adminEmails() {
        String admin = get(I_ADMIN_EMAIL);
        if (admin == null) {
            return new String[]{"noname@gmail.com"};
        } else {
            admin = admin.replace(" ", "");
            admin = admin.replace(",", ";");
            if(admin.contains(";")){
                return admin.split(";");
            } else {
                return new String[]{admin};
            }
        }
    }

    public void setHttpProxies() {
        String[] portAddr = proxyPortAddr();
        if (portAddr == null) {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");

        } else {

            String proxy = portAddr[1];//split[0];
            String port = portAddr[0];//split[1];

            EnfuserLogger.log(Level.FINER,this.getClass(),"Proxy: " + proxy + ", port = " + port);
            System.setProperty("http.proxyHost", proxy);
            System.setProperty("http.proxyPort", port);
            System.setProperty("https.proxyHost", proxy);
            System.setProperty("https.proxyPort", port);

        }
    }

    public String[] proxyPortAddr() {

        String s = args.get(I_PROXY_SETTINGS);
        if (s == null || s.toLowerCase().contains("disabled")) {
            return null;
        } else if (s.contains(",")) {
            return s.split(",");
        } else {
            return null;
        }
    }
    
    
    public String getCustomArg(String key) {
        return args.get(key);
    }

    private void init() {
        
        File pointerFile = new File(ROOT + GLOB_OPS);
        if (pointerFile.exists()) {
            boolean notify = true;
            ArrayList<String> arr = FileOps.readStringArrayFromFile(
                    pointerFile.getAbsolutePath(), notify);
            for (String s : arr) {
                try {
                    String[] split = s.split("=");
                    String key = split[0];
                    String value = split[1];
                    
                    //does the value contain "="?
                    if (split.length>2) {
                        for (int k =2;k<split.length;k++) {
                            value+="="+split[k];
                        }
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "GlobOptions: value with deliminator given: "+ value);
                    }
                    
                    EnfuserLogger.log(Level.FINER,this.getClass(),"key = " + key 
                            + ", value = " + value);
                    //check if this appends an existing line
                    if (args.get(key)!=null) {
                        String extValue = args.get(key);
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "Appending to an existing key-value pair: "+ extValue);
                        value = extValue +" "+ value;
                    }
                    args.put(key, value);

                } catch (Exception e) {

                }
            }

            String level = args.get(I_LOGGING_LEVEL);
            Level le = Level.INFO;
            if (level != null) {
                le = parseLogLevel(level);
            } else {
               EnfuserLogger.log(Level.INFO,this.getClass(),
                       "Logging level has not been defined, using INFO");
            }
            this.loglevel =le;
            
            String busylog = args.get(I_DISABLE_BUSYLOGGER);
            if (busylog != null) {
                if (busylog.toLowerCase().contains("true")) {
                    disableBusyLogger = true;
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "FusionTask BUSYLOGGER is set OFF (parallel task launch are allowed).");
                }
            }
            
            String regEA = args.get(I_REGDATA_FROM_EA);
            if (regEA != null) {
                if (regEA.toLowerCase().contains("true")) {
                    this.regionFromArchives= true;
                    EnfuserLogger.log(Level.FINE,this.getClass(),
                            "Regional static configuration data will be read from EnfuserArchives.");
                }
            }
               
        }//if exists
        else {
            String msg = ROOT + GLOB_OPS + " IS MISSING!\n The program cannot launch without it, terminating in 2 seconds.";
            EnfuserLogger.log(Level.WARNING,this.getClass(),msg);
            EnfuserLogger.sleep(5000,GlobOptions.class);
            System.exit(100);
        }//FILE NOT FOUND (this will not do.)
    }



    public void addKeyValueToCrypts(String key, String value) {
        guard.addKeyValueToCrypts(key, value);
    }

    

    public String defaultIOroot() {

        String ioRoot = args.get(I_DEF_IO_ROOT);
        
        if (ioRoot == null ) {
            EnfuserLogger.log(Level.INFO,this.getClass(),
                    "GlobOptions: WARNING! Input/Output root requested but this"
                            + " has not been defined on GlobOptions.txt!");
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "To define a default IO-root (that is used for at at least"
                            + " the headless-setuo, define "
                    + I_DEF_IO_ROOT + " =x");

            //lets build the most propable I/O-root
            //String root = ROOT;
            //File test = new File(root);
            ioRoot = ROOT;//test.getParent();
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "Assuming IO-root to be: " + ioRoot);

        }//was null 
        else if ( ioRoot.equals("*")) {
            return ROOT;
        }
        else {
            ioRoot = FuserTools.correctDir(ioRoot);
        }

        return ioRoot;
    }

    
   public String getCommonS2Dir() {
        String test = args.get(I_S2_DIR);

        if (test!=null && test.contains("*")) {
            return defaultIOroot() +"overpassOSM"+Z +"S2_archive" + Z;
        }
        
        if (test != null) {
            test = FuserTools.correctDir(test);
            return test;
        }
        return null;
   }
   
    public String overpassOSMdir(boolean trueRoot) {
        String test = defaultIOroot() +"overpassOSM"+Z;
        if (trueRoot) test = getRootDir() +"overpassOSM"+Z;
        File f = new File(test);
        if (!f.exists()) f.mkdirs();
        return test;
    }

    
    /**
     * Return the directory for SRTM elevation data as defined in the
     * GlobOptions.txt There are two options for this directory and the one that
     * exists (file.exists() will be returned.
     *
     * This method cannot return null even though the argument lines are
     * missing. In this case the root directory is used, added with
     * /SRTM_archive/. Also, the method creates the directory it is returning in
     * case it does not already exist.
     *
     * The logic here for the dual directories is the same as with root
     * directories, one GLobOptions can be used in different development
     * environments more easily.
     *
     * @return directory for SRTM data
     */
    public String getDefault_SRTMdir() {

        //check SRTM1, if exists return
        String test = args.get(I_SRTM_DIR);
        if (test==null ) {
            test = args.get(I_SRTM_DIR+"1");//in previous versions it was defined like this.
        }
        
        if (test!=null && test.contains("*")) {
            return defaultIOroot() +"overpassOSM"+Z +"SRTM_archive" + Z;
        }
        
        if (test != null) {
            test = FuserTools.correctDir(test);
            File f = new File(test);
            if (f.exists()) {
                return test;
            }
        }

        //none of the directories actually existed.
        //final thing to do: create SRTM1 if argument has been defined
        //otherwise give a warning
        if (test != null) {
            File f = new File(test);
            f.mkdirs();
            EnfuserLogger.log(Level.FINER,this.getClass(),"CREATING SRTM directory that did not exist previously: " + test);
            return test;

        } else {
            EnfuserLogger.log(Level.FINER,this.getClass(),"WARNING! SRTM elevation archive NOT defined in GlobOptions!");
            EnfuserLogger.log(Level.FINER,this.getClass(),"Define this by adding a line: " + args.get(I_SRTM_DIR) + "='value' ");

            EnfuserLogger.log(Level.FINER,this.getClass(),"Creating a directory at root (SRTM_archive)");
            String dir = defaultIOroot() +"overpassOSM"+Z +"SRTM_archive" + Z;
            EnfuserLogger.log(Level.FINER,this.getClass(),dir);
            File fdir = new File(dir);
            fdir.mkdirs();
            return dir;
        }

    }

    /**
     * A check for 'Is fusion task busy-logger disabled'. If so, then any number
     * of parallel task launch is allowed. However, this can be dangerous int
     * terms of system performance and memory availability!
     *
     * @return boolean true if disabled => allow parallel tasks.
     */
    public boolean busyLoggerDisabled() {
        return disableBusyLogger;
    }


    public VisualOptions getScriptedVisualizationOps(String s, OsmLayer ol) {
        try {
            String recipee = getVisualizationInstruction(s);
            if (recipee==null) return null;
            return VisualOpsCreator.fromString(recipee,ol);
            
        } catch (Exception e) {
             EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                  "Visualization options script error! " +s);
        }
        return null;
    }
    
    
    private final static String VOPS_FILE ="visualizationInstructions.csv";
    /**
     * Read pre-defined visualization instructions from a file, using a key
     * for ID matching. Main use-case: mapFUSER visualizations.
     * @param key the key for ID matching
     * @return String recipe for VisualiOpsCreator. Return null if key was not
     * matched.
     */
    public String getVisualizationInstruction(String key) {
      String fname = getRootDir() +"data"+Z + VOPS_FILE;
      File f = new File(fname);
      if (f.exists()) {
          try {
              ArrayList<String> arr = FileOps.readStringArrayFromFile(f);
              EnfuserLogger.log(Level.FINER,this.getClass(),
                      "Attempting visualization recipee search for "+key);
              for (String s:arr) {
                  String[] split = s.split(";");
                  String test = split[0];
                  String val = split[1];
                  if (test.equals(key)) {
                      EnfuserLogger.log(Level.FINER,this.getClass(),
                              "visualInstructions: found "+key +" -> "+ val);
                      return val;
                  }
              }
              
              
              //did not find with match, try contains
              for (String s:arr) {
                  String[] split = s.split(";");
                  String test = split[0];
                  String val = split[1];
                  if (test.contains(key) || key.contains(test)) {
                      EnfuserLogger.log(Level.FINER,this.getClass(),
                              "visualInstructions: found "+key +" -> "+ val);
                      return val;
                  }
              }
              
          }catch (Exception e) {
              
          }
   
      } else {
          EnfuserLogger.log(Level.FINER,this.getClass(),
                  "File does not exist? "+ f.getAbsolutePath());
      }
       return null; 
    }


    public Level getLoggingLevel() {
        return this.loglevel;
    }
    
    public void setLoggingLevel(String level) {
        this.loglevel = parseLogLevel(level);
    }
    
    private static Level parseLogLevel(String level) {
        Level le = Level.INFO;
        String lvl = level.toLowerCase();
                
                if (lvl.contains("severe")) {
                    le = Level.SEVERE;
                } else if (lvl.contains("warn")) {
                    le = Level.WARNING;
                } else if (lvl.contains("info")) {
                    le = Level.INFO;
                } else if (lvl.contains("all")) {
                    le = Level.ALL;
                } else if (lvl.contains("finest")) {
                    le = Level.FINEST;
                } else if (lvl.contains("finer")) {
                    le = Level.FINER;
                }else if (lvl.contains("fine")) {
                    le = Level.FINE;
                } 
                
               EnfuserLogger.log(Level.INFO,GlobOptions.class,
                       "Logging level set to "+le.toString() ); 
               return le;
    }

    public FusionOptions[] allRegionalOptions() {
        FusionOptions[] ops = new FusionOptions[this.allRegs.ercfs.size()];
        for (int i =0;i<ops.length;i++) {
            ops[i] = new FusionOptions(this.allRegs.ercfs.get(i).name);
        }
        return ops;
    }

    public Boundaries findAreaBoundsByName(String area) {
        return this.allRegs.findAreaBoundsByName(area);
    }
    
    public FusionOptions findForArea(String name) {
       ModellingArea m = getModellingArea(name);
        if (m==null) {
            EnfuserLogger.log(Level.INFO,this.getClass(),"FusionOptions search based"
                    + " on area name was unsuccessful. Unknown area="+name);
            return null;
        }
        
        String region = m.regionName();
        EnfuserLogger.log(Level.INFO,this.getClass(),"Region = " + region);
        FusionOptions ops = new FusionOptions(region,name);
        return ops;
    }

    public Regions getAllRegions() {
       return this.allRegs;
    }

    public void setLoggingLevel(Level log) {
      this.loglevel =log;
    }
    
    private boolean deterministic = false;
    public boolean deterministic() {
       return this.deterministic;
    }

    public void setDeterministic(boolean b) {
        this.deterministic = b;
    }

    /**
     * Create a list of options for all modelling area-region pairs that
     * have scheduled run trigger hours. This includes the effects on
     * taskTriggers.csv.
     * @return a list of options with triggers.
     */
    public ArrayList<FusionOptions> getOptionsWithRunTriggers() {
        ArrayList<FusionOptions> opList= new ArrayList<>();
        for (ERCF e: this.allRegs.ercfs) {
            
            for (ModellingArea a: e.areas) {
                String area = a.name;
                String reg = a.regionName();
                if (!a.runTriggers.isEmpty()) {
                    opList.add(new FusionOptions(reg,area));
                }
            }
        }//for regs
        return opList;
    }
    
    /**
     * Display all keys held by crypts.dat on system out.
     * @param compare if true then the keys matched against
     * known set of keys.
     * @param secret
     */
    public void printCryptedKeys(boolean compare,String secret) {
     if (this.guard!=null) this.guard.printKeys(compare,secret);
    }

    /**
     * Check whether of not a complementary mailer service
     * has been enabled. The mailer itself may use an addon library
     * and require additional credentials to be in place.
     * @return true if enabled.
     */
    public boolean mailerEnabled() {
        String test = args.get(I_MAILER);
        if (test!=null ) {
           return (test.toLowerCase().contains("enabled") 
                   || test.toLowerCase().contains("true" ));
        }
        return false;
    }

    public boolean isLogged(Level l) {
       return this.loglevel.intValue() <= l.intValue();
    }

    public boolean checkRegionLock(Boundaries b) {
        return guard.regionAllowed(b);
    }



}
