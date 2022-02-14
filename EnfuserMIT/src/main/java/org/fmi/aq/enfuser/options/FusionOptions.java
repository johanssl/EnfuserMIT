/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.parametrization.MetFunction2;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.meteorology.RoughPack;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOpsCreator;

/**
 * This options instance holds all settings necessary for any operation
 * with Enfuser. 
 * 
 * For a given modelling region and area, this holds
 * all the necessary settings required to do any misc. task with the system
 * and launch a modelling task. In particular, this options instance holds
 * 
 * - Access to static settings such performance, AQIdef, variable- and category definitions.
 * - A separate instance of Visualization options.
 * - Region settings with arguments.
 * - an instance of RunParameters which govern the modelling task parameters.
 * 
 * @author johanssl
 */
public class FusionOptions {

//directories for data archives
    public static int DIR_DAT_COMMON = 1;
    public static int DIR_DATA_REGIONAL = 2;
    public static int DIR_MAPS_REGIONAL = 3;
    public static int DIR_EMITTERS_REGIONAL = 4;
    public static int DIR_TEMP = 5;

    public static float NULL_WINDSPEED = 0.3f;
    public static float VLOW_WINDSPEED = 0.6f;
    public static float LOW_WINDSPEED = 0.8f;
    
    public static int CCSTYLE_COMBINER=0;
    public static int CCSTYLE_NEWTEST=1;
//options that are read from configuration files one way or another===========
//===============================
     public int concentrationPhaseStyle =CCSTYLE_COMBINER;
    private float RL_scaler = 0.4f;
    public float MIN_WINDSPEED = 1f;
    public float MIN_ABLH = 100f;
    public boolean additionalABLH_limit_puffs = true;//a correction for elevated puff sources - ABLH for these can be tricky and lead to overpredictions easily. Solution: just artifically increase ABLH.
    
    public boolean netcdf_byteCompress = true;//instead of Float-type the largest cnc-netCDF files will be converted into Byte-type with offset and scaler values.
    public boolean reduceCons_atConstruct = true;

    public float WATER_PENETR = 0.9f;
    public float BUILDING_PENETRATION = 0.3f;
    RunParameters runparams;
    
    public final ERCF reg;
    public byte local_zone_winterTime = 2;
    public boolean switchSummerTime = true;

    public double blockerStrength =1.0f; //[0,1]
    public float elevationReducerThresh_m = 100;
    public float elevationReducer_min = 0.4f;

    public final PerformanceOptions perfOps;
    public boolean neuralLoadAndUse = false;
    
    public HashMap<Integer, Integer> specialDow = new HashMap<>();
    public ArrayList<String> mfLines;
    public HashMap<String, MetFunction2> allMfs = new HashMap<>();
    public int cores = Runtime.getRuntime().availableProcessors();
    public boolean loadWindProfileGrids = true;

    public VisualOptions visOps;
    public boolean SILAM_visualWindow = true;
//=============================

    public boolean orthogonalBg = false;
    public int regionalNestingMode =0;
    public final static int BGNEST_TRACER =0;//puff tracer markers
    public final static int BGNEST_DIRECT =1;//add with simplistic reductions
    public final static int BGNEST_SELECTIVE =2;//completely off for testing
    public final static int BGNEST_OFF =3;//completely off for testing
    public final static String[] NESTING_NAMES = {
     "PUFF_MARKERS",
     "DIRECT_ADDITION",
     "SELECTIVE_ADDITION",
     "NESTING_OFF"
    };
    
    public boolean variableResolution = true;
    public float vehicMixer = 0.08f;//add virtual distance [m] to vehicular sources per each kmh/h.
    //simulates instantanous turbulent mixing this way, since distance turns into more mixing.

    public final DfOptions dfOps;
    //==============================
    public final long creationMillis;
    private final Dtime creationDt;
    //then some other options that are either for GUI work or should not be changed often (or never)
    public boolean wDirFan = true;
    public boolean guiMode = false;
    public boolean applyElevation = true;

    public boolean solarEffects = false;
    public float BLOCK_ANALYSIS_M = 100;
    public ArrayList<String> filterReads = new ArrayList<>();

    public float D_PENETR = 0;
    public float WSET = 2e-2f;
    public float fpmRand = 1;
   
    public float plumeAssessmentDistanceCut = 0;
    public float minBlockDistance =10;
    //===========================

    public int partialC_maxFactor = 6;
    public int tropomiCoverageReq = 60;
    public boolean metBasedOnSourceQuality = true;//For "meteorological data fusion". a value of true should be the best choice when gridded meteorological data
    //is available from multiple sources continously, and only the highest-quaity one is to be used.
    public boolean compressedArchForAnim = true;
    public boolean fasterHashedMet=true;

    public VarAssociations VARA;
    public Categories CATS;
    public boolean emissionDistanceChecking = false;
    public String regionalBG_name ="SILAM";
    public float vegeAmp=1f;
   

    public FusionOptions(String regname) {
        this(regname,null);
    }
            

    public FusionOptions(String regname, String areaName) {
        GlobOptions g = GlobOptions.get();
        this.CATS = g.CATS;
        
        
        if (areaName!=null) {
            this.runparams = RunParameters.fromModellingArea(areaName, null);
        }
        
        this.creationMillis = System.currentTimeMillis();
        this.creationDt = Dtime.getSystemDate();
        reg = g.getRegion(regname);
        ERCFarguments args = reg.args;
        this.dfOps = new DfOptions(args);
        this.local_zone_winterTime = (byte) args.getTimezone();//copy specified local time zone from region files 
        this.switchSummerTime = args.autoSummerTime();//copy specified local time zone from region files 
        visOps = new VisualOptions();
        visOps.setEnfuserDefaults();
        
        File varaf = RegionalFileSwitch.selectFile(regname,
                    RegionalFileSwitch.VARA_FILE);
        
        this.VARA = new VarAssociations(varaf);
        File perf =RegionalFileSwitch.selectFile(regname,RegionalFileSwitch.PERF_FILE);
        this.perfOps = new PerformanceOptions(perf);
        this.specialDow = parseSpecialDOWs(regname);
        //MET FUNCTIONS
        File mfuncs = RegionalFileSwitch.selectFile(regname, RegionalFileSwitch.MFUNC_FILE);
        mfLines = FileOps.readStringArrayFromFile(mfuncs);
        this.allMfs = MetFunction2.readFunctions(mfLines, VARA);
        
        this.loadWindProfileGrids = args.windGrids();

        double winds = args.getMinimumWindSpeedLimit();
        this.MIN_WINDSPEED = (float) winds;
        
        Double ablhLimit = args.minABLH();
        if (ablhLimit != null) {
            this.MIN_ABLH = ablhLimit.intValue();
        }

        Double rns = args.getRougnessScaler();
        if (rns != null) {
            this.RL_scaler = rns.floatValue();
        }

        String buildingMods = args.get(ERCFarguments.POL_BUILDING_PENETRATION);
        if (buildingMods != null) {
                String[] split = buildingMods.split(",");
                double d1 = Double.valueOf(split[0]);
                double d2 = Double.valueOf(split[1]);
                this.BUILDING_PENETRATION = (float) d1;
                this.WATER_PENETR = (float) d2;
        }

        String baVars = args.get(ERCFarguments.BLOCKER_STRENGTH);
        if (baVars != null) {
               this.blockerStrength = Double.valueOf(baVars);
               if (this.blockerStrength>1) this.blockerStrength =1;
               if (this.blockerStrength<0) this.blockerStrength=0;
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "BA mods taken from RegionArguments.");
        }

        //regional argument custom lines.
        this.compressedArchForAnim =  !args.hasCustomBooleanSetting(
                ERCFarguments.CUSTOM_ANIM_FULL_SIZE_ARCHS);//note the negation: default (missing) is false.
        
        this.neuralLoadAndUse = args.hasCustomBooleanSetting(
                ERCFarguments.CUSTOM_NEURAL_OVERRIDES);
        
       this.emissionDistanceChecking = args.hasCustomBooleanSetting(
                ERCFarguments.CUSTOM_EMISSION_DISTANCE_CHECKING);
       

        String speedMix =  args.getCustomContent(ERCFarguments.CUSTOM_SPEED_MIXER);
        if (speedMix!=null)vehicMixer =Double.valueOf(speedMix).floatValue();

        String vegeA = args.getCustomContent("VEGE_AMP<");
        if (vegeA!=null) {
            this.vegeAmp = Double.valueOf(vegeA).floatValue();
        }
        String nestingInt = args.getValue(ERCFarguments.BG_NESTING_MODE);
        if (nestingInt!=null)this.regionalNestingMode =Integer.valueOf(nestingInt);
       
    }
    
    private HashMap<Integer, Integer> parseSpecialDOWs(String regname) {
        HashMap<Integer, Integer> hash = new HashMap<>();
            File tester = RegionalFileSwitch.selectFile(regname,
                    RegionalFileSwitch.SPECIAL_D);
            
            if (tester.exists()) {
            ArrayList<String> arr = FileOps.readStringArrayFromFile(tester);
            int k = -1;
            for (String line : arr) {
                    k++;
                    if (k == 0) {
                        continue;
                    }
                    String[] split = line.split(";");
                    String yr = split[0];
                    String mon12 = split[1];
                    String day = split[2];

                    Integer dow = Integer.valueOf(split[3]);

                    int mon011 = Integer.valueOf(mon12).shortValue() - 1;
                    int d = Integer.valueOf(day);
                    int key;
                    if (yr.length() == 4) {
                        key = this.getSpecialDOW_key(Integer.valueOf(yr).shortValue(), (byte) mon011, (byte) d);
                    } else {
                        key = this.getSpecialDOW_key(null, (byte) mon011, (byte) d);
                    }

                    this.specialDow.put(key, dow);
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t added a new"
                            + " custom DOW for " + yr + "-" + mon12 + "-" + day);
                }
            }
            return hash;
    }
    
    public float scaleRL(float rough) {
         rough = rough * RL_scaler + (1f - RL_scaler) * RoughPack.RL_DEFAULT;
         return rough;
    }

    public String runParameterPrintout() {
        RunParameters p = this.runparams;
        String line = this.getRegionName()+"," + this.areaID()+", " +
                p.start().getStringDate_YYYY_MM_DDTHH() + " - " + p.end().getStringDate_YYYY_MM_DDTHH()
                +", "+p.res_m()+"m, " + p.areaFusion_dt_mins +"min, " + p.bounds().toText_fileFriendly(k);
        
        for (String var:p.nonMetTypes()) {
            line+= var+",";
        }
        
        for (ModellingArea in:p.innerAreas) {
            line+= in.name+",";
        }
        return line;
    }
    
    public boolean IS_METVAR(int type) {
        return VARA.isMetVar[type];
    }
    
    public int VARLEN() {
        return VARA.VAR_LEN();
    }

    public String VAR_NAME(int typeInt) {
        return VARA.VAR_NAME(typeInt);
    }

//============================
    public double convertPPM_table(int typeInt, double value) {
        return VARA.convertPPM_table(typeInt, value);
    }
    
    public int[] getIndexArrayFromHeader(String[] splitline) {
        return VARA.getIndexArrayFromHeader(splitline);
    }

    public String[] ALL_VAR_NAMES() {
      return VARA.ALL_VAR_NAMES();
    }

    public ArrayList<String> allSpeciesList() {
      return VARA.allSpeciesList();
    }

    public String LONG_DESC(int typeInt) {
        return VARA.LONG_DESC(typeInt, true);
    }

    public String VARNAME_CONV(int typeInt) {
        return VARA.VARNAME_CONV(typeInt);
    }

    public String UNIT(int typeInt) {
        return VARA.UNIT(typeInt);

    }

    public int getTypeInt(String type) {
        return VARA.getTypeInt(type);
    }

    public boolean IS_PRIMARY(byte typeI) {
        return VARA.isPrimary[typeI];
    }

    public ArrayList<Integer> getTypeInts(ArrayList<String> vars) {
        return getTypeInts(vars, false, null);
    }

    int k = 0;
    public final static String busyFileName = "busyLogger.txt";

    /**
     * Write a marker to byseLogger.txt to signal that a processing task is
     * currently being performed and no parallel task is allowed to be launched
     * by the system in parallel. Since this method is invoked in several key
     * break-point moments, this method also printouts system state to log-files
     * when called. That is, in case additional debug information has been
     * enabled via GlobOptions.
     *
     * To disable this function one can define DISABLE_BUSYLOGGER=true in
     * globOptions.txt.
     */
    public void logBusy() {
        logBusy(false);
    }
        public void logBusy(boolean forceStatus) {
        k++;
        if ( k % 5 == 0 ||forceStatus) {
            EnfuserLogger.log(Level.INFO,this.getClass(),this.getCurrentSystemState());
        }
        if (GlobOptions.get().busyLoggerDisabled()) {
            return;//do nothing, which means that
        }        //the file is not updated => all tasks are allowed to run in parallel.

        String name = GlobOptions.get().getRootDir() + busyFileName;
        try {
            Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
            FileOps.lines_to_file(name, dt.getStringDate(), false);
        } catch (Exception e) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    "busy-logger was unable to update the beacon file - "+ name);
        }
    }
    
       
    /**
     * Get a String representation of current System state in terms of
     * processing cores, memory and modelling task duration.
     *
     * @return String representation.
     */
    public String getCurrentSystemState() {

        int core = Runtime.getRuntime().availableProcessors();
        /* Total amount of free memory available to the JVM */
        long freeMem_megs = Runtime.getRuntime().freeMemory() / 1000000;
        /* This will return Long.MAX_VALUE if there is no preset limit */
        long maxMemory_megs = Runtime.getRuntime().maxMemory() / 1000000;
        long totalMem_megs = Runtime.getRuntime().totalMemory() / 1000000;

        long durationMillis = System.currentTimeMillis() - this.creationMillis;
        long secs = durationMillis / 1000;

        return "==========System state===\n\t cores=" + core + ", free Memory (Mb)=" + freeMem_megs
                + ", maxMemory=" + maxMemory_megs + ",totalMemory=" 
                + totalMem_megs + "\n Duration[s]=" + secs + "\n==========";

    }


    /**
     * In AreaFusion this is the key defining parameter to ease the
     * computational burden by calculating LESS full profiles and MORE partial
     * small-scale ones. If the returned Integer is 5, then only once per 25
     * pixels a full Profile is being calculated.
     *
     * They key principle is that the higher the resolution, the less these full
     * profiles are needed.
     *
     * @param dlat_m size of one pixel (length) in meters.
     * @return and integer value that describes for how many adjacent cells the
     * mid-range emission profiling can be copied. For example, a value of 4
     * means that in a box of 4x4 cells the mid-term profile can be applied to
     * each cell. The higher the value, the faster the overall computations get.
     */
    public int getMicroMacro_factor(double dlat_m) {

        double factor = 0.45;

        int test = (int) (factor * this.plumeZone1_m() / dlat_m); // e.g. 0.55*280/20m per pixel => 4.

        if (test <= 1) {
            return 1;
        }
        if (test < 3) {
            test = 3;
        }

        if (test % 3 == 2) {//e.g. 5
            test++;
        }
        return test;
    }

    public ERCFarguments getArguments() {
        return reg.args;
    }

    public boolean netCDF_byteCompress() {
        return getArguments().netCDF_byteCompress();
    }

    public String getRegionName() {
        return reg.name;
    }

    
    public ArrayList<Integer> getTypeInts(ArrayList<String> types,
            boolean warning, String id) {

        ArrayList<Integer> arr = new ArrayList<>();
        for (String type : types) {
            int typeI =VARA.getTypeInt(type, warning, id);
            if (typeI>=0) arr.add(typeI);
        }

        return arr;

    }

    public double plumeZone1_m() {
        return this.perfOps.PlumeZone1_m();
    }

    /**
     * Using the acceptance range in RegionalArguments for types, check whether
     * or not the parsed Observation is acceptable with respect to the limits.
     * If no limits have been set for the type, this returns true
     *
     * @param fob the parsed observation.
     * @return boolean true, false for acceptance.
     */
    public boolean isAcceptable(Observation fob) {

        //first a simple check
        if (!IS_METVAR(fob.typeInt) && fob.value < 0) {
            return false;
        }

        //check region options
        double[] minmax = VARA.getAcceptRange(fob.typeInt);    
        if (minmax != null) {
            if (fob.value < minmax[0] || fob.value > minmax[1]) {
                
                    EnfuserLogger.log(Level.INFO,this.getClass(),fob.ID + ", " + fob.dt.getStringDate()
                            + ", not accepted, value = " + fob.value + ", type = " + VAR_NAME(fob.typeInt));
                
                return false;
            }
        }

        return true;
    }

    public Integer getSpecialDOW_key(Short year, byte month_011, byte day) {
        int key = month_011 * 100 + day;
        if (year != null) {
            key += year * 1000;
        }
        return key;
    }

    /**
     * Retrieve Day-Of-Week override value for special, listed holidays. E.g.,
     * an independence day in Tuesday can be set to use temporal profile fit for
     * Sunday instead. Returns values 0, 1 or 2 which correspond to Mon-Fri, Sat
     * and Sun.
     *
     * @param year year as short value
     * @param month_011 month defined in [0,11] range as byte
     * @param day of month as byte [1,31]
     * @return possibly altered day-of-week, if such has been specified for the
     * given parameters
     */
    public Integer getSpecialDOW(short year, byte month_011, byte day) {

        //check special days for each year
        int key = getSpecialDOW_key(null, month_011, day);
        Integer dow = this.specialDow.get(key);
        if (dow != null) {
            return dow;
        }

        //check special days that vary each year
        key = getSpecialDOW_key(year, month_011, day);
        dow = this.specialDow.get(key);
        return dow;

    }

    /**
     * Read a type-specific instruction for image and animation creation.
     * This is based on 'visual_style_id' column of variableAssociations.csv.
     * Note that one can specify a instruction here, as well as an identifier key.
     * The instruction for a given style is read from visualizationInstructions.csv.
     * The visualOptions instance will be then created based on the instruction
     * associated to the given key.
     * 
     * In case the style is not given or a match is not found, then a default style
     * will be used.
     * @param typeInt modelling variable type index.
     */
    public void setTypeVisOps(int typeInt) {
        String id = VARA.getVisualizationStyleID(typeInt) +"";
        if (id.contains("<") && id.contains(">")) {//this is actually the instruction itself. 
            this.visOps = VisualOpsCreator.fromString(id, null);
            return;
        }
        VisualOptions vop = GlobOptions.get().getScriptedVisualizationOps(id, null);
        if (vop ==null) {
            //try again with defaults
            vop = GlobOptions.get().getScriptedVisualizationOps("default_style", null);
        }
        if (vop!=null)this.visOps = vop;
    }
    
    private String guiAdd() {
        if (this.guiMode) return "gui"+Z;
        return "";
    }

    public String defaultOutDir() {
        String dir= GlobOptions.get().getRootDir()  + "Results" + Z;
        File f = new File(dir);
        if (!f.exists())f.mkdirs();
        return dir;
    }

    public String getEmitterDir() {
        String dir = this.getDir(FusionOptions.DIR_DATA_REGIONAL) + "";
        dir += "emitters" + Z + "gridEmitters" + Z;
        return dir;
    }
    
    public String getDir(int dirType) {
        return getDir(dirType, this.getRegionName(), GlobOptions.get());
    }
    
    
        public static String getDir(int dirType, String regName, GlobOptions g) {
        if (dirType == DIR_DAT_COMMON) {
            return g.dataDirCommon();
            
        } else if (dirType == DIR_DATA_REGIONAL) {
            return g.regionalDataDir(regName);
            
        } else if (dirType == DIR_MAPS_REGIONAL) {
            return g.regionalDataDir(regName) 
                    + "maps"+ Z;
            
        } else if (dirType == DIR_EMITTERS_REGIONAL) {
            return g.regionalDataDir(regName) 
                    +  "emitters" + Z + "gridEmitters" + Z;
        } else if (dirType == DIR_TEMP) {
            return g.getRootDir() + "Results" + Z + "COMMON_TEMP" + Z;
        } else {
            EnfuserLogger.log(Level.SEVERE, FusionOptions.class,
                    "Directory cannot be formed for index "+dirType);
            return null;
        }
    }

    /**
     * This returns the base for an Enfuser_master_out (EMO) directory.
     * Example: /data/Enfuser_master_out/Finland/Turku/.
     * @return 
     */
    private String getBaseEMOdir(boolean mkdirs, String add) {
        ERCFarguments arg = this.getArguments();
        String masterStorage =  GlobOptions.get().masterRegionalOutDir(null);
        
        String outdir = masterStorage +arg.regName + Z 
                + this.runparams.areaID() + Z +guiAdd() + add;
        if (mkdirs) {
           File f = new File(outdir);
           if (!f.exists()) f.mkdirs(); 
        }
        return outdir;
    } 
    
    public String getPlotsDir() {
        return this.getBaseEMOdir(true,"plots"+Z);
    }
        public String getStationLocImageDir() {
        return this.getBaseEMOdir(true,"locs"+Z);
    }
  
     /**
     * This returns the base for an Enfuser_master_out (EMO) directory
     * and a sub directory 'run' within it. This helds the temporary output
     * for this particular area.
     * Example: /data/Enfuser_master_out/Finland/Turku/run/.
     * @return 
     */
    public String operationalOutDir() {
        return this.getBaseEMOdir(true,"run"+Z);
    }

    public String statsOutDir(Dtime dt) {
        if (dt==null) {
            return this.getBaseEMOdir(true,"stats"+Z);
        }
        return this.getBaseEMOdir(true,"stats"+Z + dt.year + Z);
    }
    
    public String archOutDir(Dtime dt) {
        if (dt==null) {
            return this.getBaseEMOdir(true,"arch"+Z);
        }
        return this.getBaseEMOdir(true,"arch"+Z + dt.year + Z);
    }

    public String herokuAreaOutDir() {
        if (this.guiMode) {
            return null;
        }
        
        String allOutForkDir = this.runparams.allOutForkDir();
        if (allOutForkDir==null) return null;
        
        String dir = allOutForkDir + this.runparams.areaID() + Z;
        //create dirs if this does not exist yet
        File f = new File(dir);
        f.mkdirs();
        return dir;
    }

    public String getS5pResultDir(Dtime dt, String varname) {
        if (varname==null) return this.getBaseEMOdir(true,"S5p"+Z +dt.year+Z);
        return this.getBaseEMOdir(true,"S5p"+Z +dt.year+Z + varname+Z);
    }

    /**
     * Get directory for storing gridded output data, namely for Cityzer's
     * netCDF files. This directory can have a sub-dir for previous and the next
     * runs: /0/ and /1/. Example dir: //DIR_IN_ARGS/1/HKI-METRO/ or
     * //DIR_IN_ARGS/0/HKI-METRO/
     *
     * @return String directory path
     */
    public String reducedAreaOutDir() {
        if (this.guiMode) {
            return null;
        }
        String s = this.runparams.reducedOutForkDir();
        if (s==null) return null;
        
        s += this.runparams.areaID() + Z;

        //create dirs if this does not exist yet
        File f = new File(s);
        f.mkdirs();

        return s;
    }

    private String statCruchDir = "statCrunch";
    /**
     * create and return a directory for statCrunch related output.This is not
     * part of operational use but rather, generated by enfuserGUI.The creation
     * date is taken into account (day) so, recently tasked output should find
     * it's way to the same directory.
     *
     * @param typeInt variable type index
     * Otherwise there will be 'statCrunch'
     * @return String path to directory, that is also created if missing
     */
    public String statsCrunchDir(Integer typeInt) {
       
        int yr = this.runparams.end().year;
        String add =this.statCruchDir+Z
                + yr +Z;
        if (this.sc_dir_add!=null && statCruchDir.contains("stat")) {//point run task, there can be many of these for testing purposes for the same year.
            add+=this.sc_dir_add+Z;
        }
        if(typeInt!=null) add+= VARA.VAR_NAME(typeInt) + Z;

        return this.getBaseEMOdir(true,add);
    }
    
    
    public String griddedAveragesDir(boolean temp, boolean components, Integer forcedMonth) {
        int yr = this.runparams.end().year;
        int month = this.runparams.end().month_011+1;
        if (forcedMonth!=null) month = forcedMonth;
        String add = "monthlyGrids" + Z + yr +Z  
                +month +Z;
        if (components) add +="components"+Z;
        if (temp) add+="temp"+Z;
        return this.getBaseEMOdir(true,add);
    }
        
    public String getRegionalNeuralDir(String usecase, String var, String cat) {
       String dir = this.getDir(FusionOptions.DIR_DATA_REGIONAL) 
             + "neural"+Z +usecase+Z;
       
            if (var!=null) dir  +=var +Z;
            if (cat!=null)dir+=cat +Z;
       File f = new File(dir);
       if (!f.exists()) f.mkdirs();
       return dir;
    }
    public String sc_dir_add=null;
    public void switchStatCrunchDir(boolean b) {
       if (b) {
           this.statCruchDir = "archCrunch";
       } else {
           this.statCruchDir = "statCrunch";
           if (sc_dir_add==null){
               sc_dir_add = (int)(Math.random()*10000) +"";
           }
       }
    }
    
    
    /**
     * Cross-check file name with a list of String keys that signal that the
     * file need not to be read. Note: keys with length smaller than 5 are
     * IGNORED for safety.
     *
     * @param nam file name (without path)
     * @return true if read methods are disabled for the file with the given
     * name. otherwise returns false.
     */
    public boolean readDisabled(String nam) {
        for (String s : this.filterReads) {
            if (s == null || s.length() < 5) {
                continue;
            }
            if (nam.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public Dtime creationTime() {
        return this.creationDt;
    }

    public boolean metFromHighestQualitySource() {
        return metBasedOnSourceQuality;
    }
/**
 * Get a copy of the main region bounding area for this option instance.
 * Note: This is NOT directly related to the set modelling area bounds.
 * @return boundaries.
 */
    public Boundaries getRegionBounds() {
      return reg.mainBounds;

    }

    public double[] getDF_autocovarFactors() {
       return this.dfOps.df_autocovar_coeffs;
    }

    public boolean dataFusionAllowedForSecondary(int typeInt) {
        String content = VARA.getVariablePropertyString(typeInt, VARA.HEADER_DF_SECONDARY);
        if (content==null) return false;
        
        if (content.toLowerCase().contains("true")) {
            return true;
        } else {
            return false;
        }
    }

    public boolean puffAnims() {
        return this.getArguments().hasCustomBooleanSetting(
                ERCFarguments.CUSTOM_PUFF_ANIMS);
    }

    public Boundaries boundsClone() {
       return this.runparams.bounds().clone();
    }

    public Boundaries bounds() {
      return this.runparams.bounds();
    }

    public int areaFusion_dt_mins() {
      return this.runparams.areaFusion_dt_mins;
    }

    public String areaID() {
       return this.runparams.areaID();
    }

    public Boundaries boundsExpanded(boolean puff) {
      boolean expandedPuff = this.perfOps.expandedPuffArea();
      if (!expandedPuff) puff =false;
      return this.runparams.boundsExpanded(puff);
    }

    public Boundaries boundsMAXexpanded() {
        return this.runparams.boundsMAXexpanded();
    }

    public int getModellingAreaResolution_m() {
       return (int)this.runparams.res_m();
    }
    
    public int getPuffModellingResolution_m() {
        //actual modelling area resolution
        int resm = getModellingAreaResolution_m();
        return (int)this.perfOps.ppf_resolution(resm);
    }

    public void setTemporalResolution_min(int min) {
        this.runparams.areaFusion_dt_mins = min;
    }

    public void setModellingBounds(Boundaries clone) {
        this.runparams.setModellingBounds(clone);
    }

    public RunParameters getRunParameters() {
       return this.runparams;
    }


    public RunParameters setCustomRunParameters(String name, Boundaries b, Dtime start, Dtime end,
            Float resm, Integer mins, ArrayList<String> nonMetVars) {
      this.runparams = new RunParameters(b,name,null);
      this.runparams.customize(false,b, resm, mins, start, end, nonMetVars);
      return this.runparams;
    }

    /**
     * Check runParameters and then variableAssociation skip conditions.
     * @param typeInt type index.
     * @return true if enabled via runParameters and not skipped via varAssociations.
     */
    public boolean doAnimation(int typeInt) {
        boolean varaSkipped = VARA.outputContentSkipped(typeInt, "anim");
        if (varaSkipped) return false;
        boolean anim = this.runparams.videoOutput();
        return anim;
    }
    

    public float getFlowAdjustmentFactor(Dtime dt) {
        //TODO implement feed for real-time traffic counts
        return 1f;
    }

    
    private ArrayList<Dtime[]> year;
    public ArrayList<Dtime[]> getFullYear() {
        if (year ==null) {
            ArrayList<Dtime[]> arr = new ArrayList<>();
            int yr = this.getRunParameters().start().year;
            Dtime dt = new Dtime(yr +"-01-01T00:00:00");
            TZT tzt = new TZT(this);
            for (int i =0;i<8760+25;i++) {
                arr.add(tzt.getDates(dt));
                dt.addSeconds(3600);
                if (dt.year!=yr) break;//year has changed (this takes care of leap year issues).
            }
            year =arr;
        } 
        return year;  
    }

    public String[] getForkIgnoreNames() {
        return this.runparams.forkIgnore();
    }

    public float RL_scaler() {
        return this.RL_scaler;
    }

    public void setRL_scaler(float floatValue) {
        this.RL_scaler = floatValue;
    }
}
