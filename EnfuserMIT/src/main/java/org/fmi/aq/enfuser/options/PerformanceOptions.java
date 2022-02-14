/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;


/**
 * This class represent the configuration file performanceOptions.csv
 * This holds options for performance related settings, especially for
 * the Gaussian Puff and Plume -related topics.
 * 
 * 
 * @author johanssl
 */
public class PerformanceOptions {

    public final static String PUFF_TICK_S="PUFF_BASETICK_S";//how often puffs are being released
    public final static String PUFF_RESPEC_S="PUFF_RESPEC_TICK_S";//how often their state is updated (met)
    public final static String PUFF_RESPEC_RANDOMDIR="PUFF_RESPEC_RANDOMDIR";

    public final static String PUFF_SCALED_RESOLUTION = "PUFF_RESOLUTION_SCALER";//if defined, then the resolution will be based on the modelling resolution times this.
    public final static String PUFF_RESOLUTION_LIMITER= "PUFF_RESOLUTION_LIMITER";
    public final static String PUFF_SELECTION_RADIUS_M ="PUFF_SELECTION_RADIUS_M";//define the radius for the selection of puffs that can contribute to concentrations. Too far => skip.
    public final static String PUFF_MERGING_RATE ="PUFF_MERGING_RATE";//with a value of [0,1] how often do nearby puffs merge together.
    public final static String RME_RESOLUTION_M ="RME_RESOLUTION_M";//RME is for Ranged Map Emitter. Define emitter resolution in meters for the averaged ranged map emitters.
    public final static String RME_RELEASE_RATE_FACTOR ="RME_RELEASE_RATE_FACTOR";
    public final static String RME_ELEVATED ="RME_ELEVATED_SOURCES";
    
    public final static String MET_RESM ="MET_RESOLUTION_M";//define the resolution in meters in which the meteorology is represented and being stored as Met objects.
    public final static String PLUME_ZONE1_RADM="PLUME_ZONE1_RADM";//in meters, the max radius for Zone 1 computations (Gaussian plume)
    public final static String PLUME_ZONE2_RADM="PLUME_ZONE2_RADM";//in meters, the max radius for Zone 2 computations (Gaussian plume)
    public final static String PLUMEFORM_PERFORMANCE ="PLUMEFORM_PERFORMANCE";
    public final static String USE_EXTENDED_PUFF_AREA ="USE_EXTENDED_PUFF_AREA";// if true, then the extended are (for puff modellling is even more extended. This can be used in case the modelling area is a piece of a larger urban area (has emitters all around)
    public final static String MULTI_THREAD_ARCHIVING= "MULTI_THREAD_ARCHIVING";
    public final static String PARALLEL_VIDEO_ENCODING= "PARALLEL_VIDEO_ENCODING";
    /**
     * Key names, default values and then some description for the elements.
     */
    public final static String[][]SPECS = {//key, default value, input type
        
        {PUFF_TICK_S,               "300",  "Integer", "high",
            "This affects how often (once every x seconds) puffs are being released"
            + " from emitters. Default value: 300s which equals one puff every 5 minutes."
            + " Lower values means more puffs which in turn means more computations."},
        
        {PUFF_RESPEC_S,             "600",  "Integer", "low",
            "Define the rate (once every x seconds) at which "
            + "existing puffs will be updated in terms of " 
            + "meteorology and pollutant mass. This does not consider the positional"
            + " updates for puffs, however."},
        
        {PUFF_RESPEC_RANDOMDIR,     "5",    "Integer", "low",
            "Define a small randomization amount"
            + " in degrees that can be applied to the wind direction for puffs during"
            + " the puff respec operation. Example: with a value of 5, a uniform "
            + "random value in between [-5,5] degrees will be applied."},
        
        {PUFF_SCALED_RESOLUTION,    "0.17", "Double [0.05,0.3]", "highest",
            "This value is used to determine the resolution in which the Puff concentration"
            + " computations are assessed. Example: if the modelling resolution "
            + "is 20m and a value of 0.1 is used then puff modelling resultion is 200m."
            + " A smaller value here means a coarser resolution for computations"},
        
        {PUFF_RESOLUTION_LIMITER,    "250", "Integer [50,500]", "high",
            "This value is used to limit the dimension of the grids in which puff"
            + " computations is being performed. For example, if the computations"
            + " are done in a 300x300 grid then it can take up to 9 times more"
            + " time than it would in a 100x100 grid. However, this just sets a maximum"
            + " limit and the grid size is also affected by PUFF_SCALED_RESOLUTION"
            + " and the physical modelling area."},
        
        {PUFF_SELECTION_RADIUS_M,   "2500", "Integer","high",
            "Define the maximum distance [m] for puffs in which it can contribute to "
            + "computed concentrations. A smaller value van significantly reduce"
            + " the computational cost at the slight expense of accuracy."},
        
        {PUFF_MERGING_RATE,         "0.5",  "Double [0,1]","moderate",
            "Define a factor that requlated how often nearby puffs should merge together"
            + " and thereby reduce the amount of puffs being modelled. A larger value"
            + " (maximum of 1) makes the puffs merge more often. A value of 0 "
            + "(minimum) disables the merging method."},
        
        {RME_RESOLUTION_M,          "700",  "Integer","high",
            "Define the resolution of a single emitter cell [m] (for the 'RangedMapEmitter')"
            + " that bundles and aggregates the high-res emitters in the modelling area into larger cells."
            + " A larger value means less emitters in total which in turn means less puff to model."},
        
        {RME_RELEASE_RATE_FACTOR,   "1",    "Integer, [1,5]","moderate",
            "A factor that is applied to RangedmapEmitter puff release rates. "
            + "A large value causes more frequent puffs to be released which in turn"
            + " means more computations. Default:1."},
        
        {RME_ELEVATED,   "false",    "String, one of [true,false]","moderate",
            "If set as true, then release height differences will be taken into account better. "
            + "This can increase the amount of puffs significantly and is only"
            + " needed with e.g., industrial netCDF emitters. Default:false."},
        
        
        {MET_RESM,                  "1000", "Integer","low",
            "Define the resolution [m] which is used for the representation of "
            + "meteorology internally in the model. Note: there may me meteorological "
            + "properties (such as wind) that uses a much"
            + " finer resolution regardless of this parameter."},
        
        {PLUME_ZONE1_RADM,          "280",  "Integer [200,600]","moderate",
            "Define the distance limit [m] which is used for Gaussian Plume computations"
            + " with the highest detail. A higher value means less computations to be done."},
        
        {PLUME_ZONE2_RADM,          "2400", "Integer","moderate",
            "Define the distance limit [m] which is used for the threshold of the Gaussian Plume / Puff computations."
            + " A higer value means that more emphasis is being put to Gaussian Plume. A low value, combined "
            + "with a low RME_RESOLUTION_M, makes the modelling more dynamic but more costly."},
        
        {PLUMEFORM_PERFORMANCE,     "fast", "String, one of [standard,fast,fastest]" ,"highest",
            "Define the PlumeForm perfomance level for Gaussian Plume computations. With setting 'fast' and 'fastest'"
            + " there will be significantly less computations being done at a slight cost of accuracy. Default:fast"},
        
        {USE_EXTENDED_PUFF_AREA,    "false","String, one of [false,true]","moderate",
            "One can set this as 'true' in case the surrounding area (outside of the actual modelling area) is covered with"
            + "emission sources. This can be the case if the modelling area is a sub-area of a larger metropolitan area."},
        
         {MULTI_THREAD_ARCHIVING,    "true","String, one of [false,true]","high",
            "If set as 'true' then allPollutant_x.zip production will be done in "
            + "a parallel thread while the model proceeds to the next time step's "
            + "computations. Default:true, Set to 'false' only if problems "
            + "related to this feature arise."},
         
         {PARALLEL_VIDEO_ENCODING,    "true","String, one of [false,true]","moderate",
            "If set as 'true' then video encoding will be done in "
            + "a separate thread while the animation creation proceeds to the next pollutant species."
            + "Default:true, Set to 'false' if problems "
            + "related to this feature arise, e.g., insufficient memory."},
    };

    public final static String PERF_FILENAME="performanceOptions_v2.csv";
    public final static int PLUMEPERF_STANDARD=0;
    public final static int PLUMEPERF_FAST=1;
    public final static int PLUMEPERF_FASTEST=2;
    public final static String[] PLUMEPERF_NAMES = {"standard","fast","fastest"};

    private static boolean FASTER_PUFFS = false;
    public static void setPuffModellingResolutionReduction(boolean b) {
        FASTER_PUFFS =b;
        EnfuserLogger.log(Level.INFO, "PerformanceOptions: puff modelling "
                + "resolution reduction = "+b);
        
    }
    int performanceMode =1;
    private HashMap<String,String> keyValues = new HashMap<>();
    private HashMap<String,Double> keyDoubles = new HashMap<>();

    
    private static String[] findSpecLine(String key) {
        for (String[] ele:SPECS) {
            if (ele[0].equals(key)) return ele;
        }
        return null;
    }
    /**
     * Initiate an instance. First, default values will be set and then
     * the content will be read from performanceOptions.csv..
     */
    public PerformanceOptions(File f) {
        
        //read default values in case some are missing from the file
        for (String[] ele:SPECS) {
            String key = ele[0];
            String value = ele[1];
            keyValues.put(key, value);
            try {
                //if value can be converted into a Double, then store it
                Double d = Double.valueOf(value);
                this.keyDoubles.put(key, d);
            } catch (NumberFormatException e) {
                
            }
        }
        
        ArrayList<String> arr = FileOps.readStringArrayFromFile(f);

        for (String line : arr) {
            line = line.replace(" ", "");
            
            String[] temp = line.split(";");
            if (temp.length<2) continue;
            String key = temp[0];
            String value = temp[1];
            keyValues.put(key, value);
        }
        
        for (String key:this.keyValues.keySet()) {
            String value = this.keyValues.get(key);
            try {
                //if value can be converted into a Double, then store it
                Double d = Double.valueOf(value);
                this.keyDoubles.put(key, d);
            } catch (NumberFormatException e) {
                //if this should be a number then we have an actual problem that
                //needs to be logged
                String[] ele =findSpecLine(key);
                if (ele==null) continue;//not an actual key, some comment line.
                String desc = ele[2];
                if (desc.contains("nteger") || desc.contains("ouble")) {
                    EnfuserLogger.log(e, Level.WARNING, this.getClass(),
                            f.getAbsolutePath()+": Number parsing error for "
                                    +key +", "+ value +". Instruction: "+desc);
                }
            }
        }
        
        //finally,set plume performance
        parseSetPerformanceMode(f.getAbsolutePath());
        this.elevatedRME();//parse this now
    }
    
    /**
     * Set the PlumeForm perfomance level based on the String value.
     * The higher the index, the less
     * cells will be assessed during Concentration Computations.
     */
    private void parseSetPerformanceMode(String filename) {
        String val = this.keyValues.get(PLUMEFORM_PERFORMANCE).toLowerCase();
        boolean found =false;
        for (int k =0;k<PLUMEPERF_NAMES.length;k++) {
            if (val.equals(PLUMEPERF_NAMES[k])) {
                performanceMode =k;
                found=true;
                break;
            }
        }
        
        if (!found) {
            EnfuserLogger.log( Level.WARNING, this.getClass(),
                            filename+": plume peformance mode is unknown:  "
                                    +val +". It should be one of [standard,fast,fastest]"); 
        }
        
    }

    /**
     * Get the set resolution in meters for meteorological data representation
     * (for HashedMet).
     * @return resolution in meters.
     */
    public int metRes_m() {
        double scaler = 1;
        if (FASTER_PUFFS) scaler = 1.5;//less unique location cells for HashedMet.
        return (int)(this.keyDoubles.get(MET_RESM).intValue()*scaler);
    }
  
    /**
     * Get initial puff release rate in seconds. This regulates the amount
     * of puffs an emitter cell can release. The lower this value is the
     * more simultaneous puffs there will be to model.
     * 
     * Note: this initial value can later on be modified based on emitter-specific rules
     * (release rate increase factors) 
     * @return 
     */ 
    public int puffBaseTick_s() { 
        int tick=this.keyDoubles.get(PUFF_TICK_S).intValue();
        if (tick< 1) tick =1;
        return tick;
    }
    
    /**
     * Define the rate at which existing puffs will be updated in terms of
     * meteorology and pollutant mass. This does not consider the positional
     * updates for puffs, however.
     * @return update interval in seconds.
     */
    public int puffRespec_s() {
        return this.keyDoubles.get(PUFF_RESPEC_S).intValue();
    }
    
    /**
     * get the puff concentration modelling resolution based on the actual
     * modelling area resolution. Most often this is 10% to 20% of the original
     * since higher resolution does not incrase the overall quality significantly.
     * 
     * @param original_resm actual modelling area resolution in meters
     * @return puff concentration computation resolution in meters.
     */
    protected double ppf_resolution(double original_resm) {
        double scaler = this.keyDoubles.get(PUFF_SCALED_RESOLUTION);
        if (scaler < 0.05) scaler = 0.05;
        if (scaler >0.3) scaler =0.3;
        

        if (FASTER_PUFFS) scaler *=0.33f;//should be nine times faster after this modification
        
        double resm = original_resm/scaler;
        if (resm < 10) resm =10;
        if (resm>2000) resm=2000;
        return resm;
    }
    public static boolean PPF_DIM_LIMIT = true;
    public int getPPF_dimensionLimit() {
        if (!PPF_DIM_LIMIT) return Integer.MAX_VALUE;
        int dim= this.keyDoubles.get(PUFF_RESOLUTION_LIMITER).intValue();
        if (dim < 50) dim = 50;
        if (dim > 500) dim=500;
        if (FASTER_PUFFS)dim*=0.33f;
        return dim;
    }
    
    /**
     * Get the maximum distance for puffs in which it can contribute
     * to computed concentrations. A smaller value van significantly
     * reduce the computational cost at the slight expense of accuracy.
     * @return maximum effect distance of a puff in meters.
     */
    public int ppfBinRes_m() {
        int radm= this.keyDoubles.get(PUFF_SELECTION_RADIUS_M).intValue();
        return radm;
    }
    
    /**
     * get the resolution of an emitter cell [m] for the RangedMapEmitter that
     * bundles the high-res emitters in the modelling area into larger cells.
     * @return emitter cell resolution in meters.
     */
    public int ppfMapPuffRes_m() {
        double scaler = 1;
        if (FASTER_PUFFS) scaler = 1.3;//larger emitter cells => fewer
        return (int)(this.keyDoubles.get(RME_RESOLUTION_M).intValue()*scaler);
    }

    /**
     * get the release rate incrase factor for RangedMapEmitter cells.
     * @return increase factor (default: 1, no increase)
     */
    public int ppfMapPuff_genIncr() {
        int inc = this.keyDoubles.get(RME_RELEASE_RATE_FACTOR).intValue();
        if (inc < 1) inc=1;
        if (inc > 5) inc=5;
        return inc;
    }

    /**
     * Get the distance limit in which the modelling switches from Gaussian Plume
     * into Gaussian Puff. (Zone 2 maximum).
     * @return threashold distance in meters.
     */
    public int puffPlumeSwitchThreshold_m() {
        return this.keyDoubles.get(PLUME_ZONE2_RADM).intValue();
    }

    /**
     * Get the distance limit in which the modelling uses the highest resolution
     * and accuracy for Gaussian Plume concentrations (Zone 1).
     * @return threashold distance in meters.
     */
    public int PlumeZone1_m() {
        return this.keyDoubles.get(PLUME_ZONE1_RADM).intValue();
    }

    public static boolean NO_RESPEC_RANDOM =false;
    /**
     * Get a small randomization amount in degrees that can be applied
     * to the wind direction for puffs during the puff respec operation.
     * Example: with a value of 5, a uniform random value in between
     * [-5,5] degrees will be applied.
     * @return randomization limit in degrees to wind direction.
     */
    public double puffRespecRandomness() {
        if (NO_RESPEC_RANDOM) return 0;
        return this.keyDoubles.get(PUFF_RESPEC_RANDOMDIR);
    }

    /**
     * Get the classification index for PlumeForm performance setting.
     * One of [0,1,2]. The higher the index the faster (less cells to be evaluated)
     * the PlumeFrom assessement will be.
     * @return 
     */
    public int plumeFormPerformanceValue() {
       return this.performanceMode;
    }

    /**
     * Get a factor that requlated how often nearby puffs should
     * merge together and thereby reduce the amount of puffs being
     * modelled. A larger value (maximum of 1) makes the puffs merge
      more often. A value of 0 (minimum) disables the merging method.
     * @return merging factor [0,1].
     */
    public float getPuffMergingStrength() {
        Double d = this.keyDoubles.get(PUFF_MERGING_RATE);
        float f = d.floatValue();
        //some safety checks, this needs to be in between [0,1]
        if (f < 0) {
            f = 0;
        }
        if (f > 1) {
            f = 1;
        }
        return f;
    }
 
    /**
     * This returns true if puff modelling has been set to cover a somewhat
     * larger area around the actual modelling area. This can be used
     * as 'true' in cases where the modelling area is just a piece of
     * a larger metropolitan area and emission relases in the surroundings
     * are needed to be modelled with higher accuracy.
     * @return true if extended puff modelling area.
     */
    public boolean expandedPuffArea() {
        String value = this.keyValues.get(USE_EXTENDED_PUFF_AREA);
        if (value.toLowerCase().contains("true")) {
            return true;
        } 
        return false;
    }
    
    public boolean multiThreadArchiving() {
        String value = this.keyValues.get(MULTI_THREAD_ARCHIVING);
        if (value.toLowerCase().contains("false")) {
            return false;
        } 
        return true;
    }
        
    public boolean videoAsRunnable() {
       String value = this.keyValues.get(PARALLEL_VIDEO_ENCODING);
        if (value.toLowerCase().contains("false")) {
            return false;
        } 
        return true;
    }

    private Boolean elevRME =null;
    public boolean elevatedRME() {
        if (elevRME==null) {
             String value = this.keyValues.get(RME_ELEVATED);
             if (value==null) value ="false";

            if (value.toLowerCase().contains("true")) {
                elevRME= true;
                EnfuserLogger.log(Level.FINE, this.getClass(),
                        "Elevated RME has been set ON.");
            } else {
                elevRME =false;
            } 
        }
        return this.elevRME;
    }

     /**
     * Set the PlumeForm perfomance level.The higher the index, the less
     * cells will be assessed during Concentration Computations.
     * @param setting performance index to be set [0,1,2].
     */
    public void setPlumePerformance(int setting) {
       this.performanceMode = setting;
    }



}
