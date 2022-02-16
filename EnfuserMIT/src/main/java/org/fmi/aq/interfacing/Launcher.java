/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.util.HashMap;
import org.fmi.aq.enfuser.core.EnfuserLaunch;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.assimilation.PersistentAdjustments;
import org.fmi.aq.enfuser.customemitters.TrafficEmitter;
import org.fmi.aq.enfuser.datapack.source.StationSource;


/**
 *This class handles an unknown runmode launch command that the EnfuserLaunch
 * was unable to parse. These runmodes are most often complementary methods
 * that utilize addon-packages.
 * @author johanssl
 */
public class Launcher {

    public static void launchCustom(String[] args) {
    EnfuserLogger.log(Level.FINER,Launcher.class,"Unknown runmode, which is not "
            + "supported by the existing addon-packages.");
    }

    public static void multiRunScriptCheck(String[] args) {
    }

    public static void testRunCheck(String runMode, HashMap<String, String> argHash) {

    }
    
     /**
     * In case there are no region set up, this will create a testing region.
     * Several run modes do require at last a test region to function.
     * @param force if true, then the TestRegion will be created regardless
     * of the availability of already set up regions.
     */
    public static void testRegionCheck(boolean force, String name) {

    }

     /**
     * Define the process to be launched after an unsuccessful launch attempt in
     * enfuser.core.EnfuserLaunch. 
     * @param strings launch arguments passed on. One of the element is of the form
     * runmode=X where X was a previously unknown parameter to EnfuserLaunch.
     */
    public void launch(String[] strings) {
        EnfuserLogger.log(Level.INFO,this.getClass(),"Addons: launch.");
        launchCustom(strings);
    }
    

   /**
   * Launch the assessment of power plant point emitters for a region (specified in
   * FusionOptions) and create the necessary emitter datasets.
   * @param ops_RegionName the options that contain directory paths and the region in question.
   * This can also be a String: the region name.
     * @param b if a region name is used, then the area is to be given as Boundaries
     * with this parameter.
   * @param copyLTMs if true, then LTM-profile files are copied when needed. (use true)
   * @param gePins if true, then additional visualization in the gridEmitter directory
   * will be produced.
     * @param areaTest if true, then do the process for a smaller area as a test
   */
    public void pointEmitterSetup(Object ops_RegionName, Boundaries b,
            boolean copyLTMs, boolean gePins, boolean areaTest) {
 
    }
    
    public Object manualCustom(String def, String[] args, Object input) {
        return null;
    }
    
    private static String TESTVARS="";  
    
    /**
     * After the creation of FusionCore and load operations, perform a series
     * of operations to make the specified testing scenariors to occur.
     * In operational use this method shoud do nothing.
     * @param ens the core
     */
    public static void applyTestingMods(FusionCore ens) {
    }
    
    /**
     * After launching the program, log given testing arguments.
     * This may trigger a series of operations that e.g., change the values
     * of global static variables.
     * @param argHash run arguments, given as key-value -pairs.
     */
    public static void logTestingArguments(HashMap<String,String> argHash) {
        //testing variables
        String val = argHash.get(EnfuserLaunch.ARG_TESTING);
        if (val!=null) {
            TESTVARS =val+"";
            if (val.contains("cripple_flow")) TrafficEmitter.toggleDFtesting(true);
            if (val.contains("cql_reset")) PersistentAdjustments.CQL_RESET=true;
        }
    }
   
        
    /**
     *Launch a sequence of tests and send email alerts if notable
     *issues are found. The list of recipients is maintained at globOps.txt
     * For the same topic, only one alert message can be sent per day. 
     */
    public static void operativeEmailAlertCheck() {
    }
    
    
        /**
     * Run the osmLayer creation process, targeting a REGIONAL directory that
     * may contain multiple areas. For each of these areas (that are sub-directories)
     * the mapFUSER will be used to create osmLayers.
     * @param argHash 
     */
    public static void fullRegionalHeadlessMapFuser(HashMap<String, String> argHash) {

    }
    
    
    public static void applyOffsetCorrection(StationSource st, FusionCore ens) {

    }  

    public static void autoOffsetAllSensors(FusionCore aThis) {
       
    } 
    
}
