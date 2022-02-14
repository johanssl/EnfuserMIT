/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

import org.fmi.aq.interfacing.Launcher;
import org.fmi.aq.enfuser.mining.miner.RegionalMiner;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import org.fmi.aq.enfuser.core.tasker.TaskQueue;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.core.setup.RegionProcessor;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import java.util.logging.Level;import java.util.logging.Logger;
import org.fmi.aq.enfuser.core.tasker.RunTask;
import org.fmi.aq.enfuser.ftools.Streamer;
import org.fmi.aq.enfuser.options.ModellingArea;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.interfacing.Guard;

/**
 *This is the main class of the Enfuser modelling system.
 * The main purpose of it is to determine the run-mode for the launch, of which
 * there are several different ones. The most notable ones being,
 *  - area modelling task
 *  - DataMiner
 *  - osmLayer creation and region setup
 *  - server mode (scheduled area modelling tasks)
 * 
 * In case the runmode, which carried in args[] for the main method,
 * is unknown then this launch call is passed to addon-launcher. This
 * addon launcher may be able to address the runmode. For example, most of 
 * the runmodes that has a GUI will be dealt with in the addon packages.
 * 
 * Besides the runmode there are other static parameters that can be used to modify
 * what actually happens with any given runmode. Examples:
 *  - hourspan=a,b can be used to modify the length of a modelling task
 *  - rundate=x can be used to modify the origin of a modelling task
 *  - value=x can be used to define a key variable for runmodes that needs attributes
 *  - name=x can be used for runmodes that targets a specific ID or creates something
 *  that has a name.
 * 
 * @author johanssl
 */
public class EnfuserLaunch {

    public final static String ARG_RUNMODE = "runmode=";
    public final static String ARG_REGIONDEF = "region=";
    public final static String ARG_NAME = "name=";
    public final static String ARG_VALUE = "value=";
    public final static String ARG_FILEDIR = "filedir=";
    public final static String ARG_RUNDATE = "rundate=";
    public final static String ARG_HOURSPAN = "hourspan=";
    public final static String ARG_MINIMIZE_ON_CLOSE = "minimize_on_close=";
    public final static String ARG_OVERWRITE = "overwrite=";
    public final static String ARG_ARCHIVING ="archiving_mode=";
    public final static String ARG_ERCF_SWITCH = "ercf_replace=";
    public final static String ARG_ERCF_ADD = "ercf_add=";
    public final static String ARG_CUSTOM = "custom=";
    public static final String ARG_TESTING ="testingvars=";

    public final static String[] CATCH_ARGS = {
        ARG_RUNMODE,//0
        ARG_REGIONDEF,//1
        ARG_NAME,//2
        ARG_VALUE,//3
        ARG_FILEDIR,//4
        ARG_RUNDATE,//5
        ARG_HOURSPAN,//6
        ARG_MINIMIZE_ON_CLOSE,//7
        ARG_OVERWRITE,//8
        ARG_ARCHIVING,//9
        ARG_ERCF_SWITCH,//10
        ARG_ERCF_ADD,//11
        ARG_CUSTOM,
        ARG_TESTING
    };

    public static HashMap<String, String> argHash;


    public final static String RUNM_SERVER = "server";
    public final static String RUNM_MINER = "miner";
    public final static String RUNM_FLEX = "flex";
    public final static String RUNM_MANUAL = "manual";

    public final static String RUNM_HLESS_SETUP = "headless_setup";
    public final static String RUNM_HLESS_MAPF = "headless_mapf";
    public final static String RUNM_AREA_TASK = "area_task";
    public final static String RUNM_CRYPT_ADD = "crypt_add";
    private final static String RUNM_CGEN = "cgen";
    public final static String RUNM_TESTRUN = "testrun";
    
        public final static String[] RUNMODES = {
        RUNM_SERVER,
        RUNM_MINER,
        RUNM_FLEX,
        RUNM_MANUAL,
        RUNM_HLESS_SETUP,
        RUNM_HLESS_MAPF,
        RUNM_AREA_TASK,
        RUNM_CRYPT_ADD,
        RUNM_CGEN,
        RUNM_TESTRUN
    };
        
    private static Streamer str_o;
    private static Streamer str_err;
    
    /**
     * Fork console printouts to a default log file destination.
     * An area modelling task will have its own log file, but the startup
     * and misc. GUI operations will use this default logging destination.
     */
    public static void setDefaultPrintStreams() {
        String rootdir = GlobOptions.get().getRootDir();
        ArrayList<String> soutFiles = new ArrayList<>();
        
        String soutf = rootdir + "logs"+Z +"sOut_default.txt";
        String erf =rootdir + "logs"+Z +"errOut_default.txt";
        //delete existing default log files.
            try {
                File f = new File(soutf);
                if (f.exists()) f.delete();
                f = new File(erf);
                if (f.exists()) f.delete();
                
            }catch (Exception e) {
                
            }
        
            soutFiles.add(soutf);
        ArrayList<String> errFiles = new ArrayList<>();
            errFiles.add(erf);

        PrintStream ps = System.out;
        PrintStream eps = System.err;
        str_o = new Streamer("sout",  ps, soutFiles);
        str_err = new Streamer("ERROR",  eps, errFiles);

        System.setOut(new PrintStream(str_o));
        System.setErr(new PrintStream(str_err));
    }
    
    public static void closePrintStreams() {
        if (str_o!=null) {
            str_o.resetFileStream(new ArrayList<>(), false);
        }
        
        if (str_err!=null) {
            str_err.resetFileStream(new ArrayList<>(), false);
        }
    }
    
    public static void flushLogsToOutputDirectory(FusionOptions ops) {
        String targetDir = ops.operationalOutDir();
        ArrayList<File> originals = new ArrayList<>();
        //list all open log files.
        if (str_o!=null) {
            for (String file:str_o.files) {
               originals.add(new File(file));
               EnfuserLogger.log(Level.INFO, "log file copy for: "+file);
            }
        }
        
        if (str_err!=null) {
            for (String file:str_err.files) {
               originals.add(new File(file));
               EnfuserLogger.log(Level.INFO, "errLog file copy for: "+file);
            }
        }
        //close the current streams before file copy
        closePrintStreams();
        for (File f:originals) {
            File dest = new File(targetDir + f.getName());
            try {
                FuserTools.copyTo(f,dest);
            } catch (IOException ex) {
                Logger.getLogger(EnfuserLaunch.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Reset the log files and redirect the printouts to new set of file input
     * streams.
     * @param soutFiles console printouts are copied to these files
     * @param errFiles errors and warnings will be copied here
     */
    public static void setPrintStreams(ArrayList<String> soutFiles,
            ArrayList<String> errFiles) {
        if (str_o==null) {
           PrintStream ps = System.out;
           str_o = new Streamer("sout",  ps, soutFiles); 
           
        } else {
           str_o.resetFileStream(soutFiles, true); 
        }
                     
        if (str_err ==null) {
           PrintStream eps = System.err;
           str_err = new Streamer("ERROR",  eps, errFiles);
           
        } else {
            str_err.resetFileStream(errFiles, true);
        }       
                
    }

    /**
     * form a hash of tag-value pairs. This uses the raw args[] from main method
     * and then finds tag-value pairs from them. 
     * @param args main arguments
     * @return 
     */
    public static HashMap<String,String> argumentHash(String[] args) {
      HashMap<String,String> argHash = new HashMap<>();
        for (String s : args) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"argument: " + s);
       
            for (String tester : CATCH_ARGS) {
           
                if (s.contains(tester)) {
                    String value = s.split(tester)[1];
                    argHash.put(tester, value);
                    EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                            "\t parsed " + tester + " => " + value);

                    if (tester.equals(ARG_FILEDIR)) {
                        value = FuserTools.correctDir(value);
                        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                                "This must be a valid directory  (after correction: " + value + ")");
                        argHash.put(tester, value);
                    }
                }
            }
            //check results after parsing to hash    
        }
        //testing variables
     Launcher.logTestingArguments(argHash);
     return argHash;   
    }
   
    /**
     * The main method of Enfuser. Here it begins.
     * @param args arguments that are used to define the runmode.
     * In case the runmode is not defined, then GlobOptions define what is the
     * runmode. It can also be manually determined, so that user input can be given
     * to define it. 
     * 
     * In case the user fails to define the runmode manually,
     * then there is a distinct procedure to define the default run mode.
     * If the DataMiner is not running, then it will be DataMiner. If the DataMiner
     * is running in the background, then 'server'. 
     * 
     */
    public static void main(String[] args) {  
        argHash = argumentHash(args);//must be done first
        setDefaultPrintStreams();
                EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                        "\n================\n"+DataCore.VERSION_INFO+", "
                + Dtime.getSystemDate().getStringDate()
        +"\n==================");
        argHash = argumentHash(args);//will print to log this time.
        //check region state

        //Some configurations support 'do A, then B and C'.
        Launcher.multiRunScriptCheck(args);
        
        //runMode?
        String runMode = argHash.get(ARG_RUNMODE);
        // a couple of additional rules 1: if missing set to manual
        if (runMode == null) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "RunMode missing, setting default from globOptions (RUNMODE_WHEN_UNDEFINED).");
            runMode = GlobOptions.get().runmodeWhenUndefined();
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"=> " + runMode);
        }

        //rule 2: if manual, then parse input now directlu.
        if (runMode.equals(RUNM_MANUAL)) {
            String msg=("Run mode has been set to manual: type-in the "
                    + "intended run mode: "
                    + "\n\t "+RUNM_SERVER+" - checks scheduled modelling tasks and executes them if found."
                    + "\n\t gui - launch Enfuser in GUI mode for manual work"
                    + "\n\t "+RUNM_MINER+" - launch the DataMiner for continous data extraction for all regions"
                    + "\n\t "+RUNM_AREA_TASK+" - launch a specific area modelling task (scheduled or not)"
                    + "\n\t mapf - launch mapFUSER GUI for manual osmLayer work"
                    + "\n\t "+RUNM_HLESS_MAPF+" - launch mapFUSER for scripted creation of one or more osmLayers"
                    + "\n\t "+RUNM_HLESS_SETUP+" - set up data structures for a new modelling region based on osmLayer data"
                    + "\n\t "+RUNM_TESTRUN+" - launch a controlled test-run for built-in test region and area"
                    );
                   
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,msg);
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "Depending on model version one can attempt also the following"
                            + " run modes:  full_mapf, selecter, jmap, logwinder)");
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "\nPlease type in the chosen run mode within 20 seconds.");
            int x = 20; // wait 20 seconds at most
            String manualIn = FuserTools.readSystemInput(x);
            if (manualIn ==null) {
                 EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"You did not enter runMode: assuming flex.");
                    runMode = RUNM_FLEX;
            } else {
              runMode = manualIn;  
            }     
        }        
           
        Launcher.testRunCheck(runMode,argHash);
        
        if (runMode.equals(RUNM_FLEX)) {
            flexRunMode(args);

        } else if (runMode.equals(RUNM_MINER)) {//launch a DataMiner instance
           closePrintStreams();
           RegionalMiner.main(args);

        } else if (runMode.equals(RUNM_SERVER)) {//launch server
            TaskQueue.schedulerLoop();

        } else if (runMode.equals(RUNM_CRYPT_ADD)) {//add crypted parameters
            cryptAdd(argHash);

         } else if (runMode.equals(RUNM_CGEN)) {//create either a csv-base for crypts OR create the object based on it. A password is required as a "value=x"
            Guard.create(argHash);
    
        } else if (runMode.equals(RUNM_AREA_TASK)) {//launch a single modelling area task
            areaRunTask(argHash,false);
               
        } else if (runMode.equals(RUNM_HLESS_SETUP)) {//launch headless regional setup
            headlessSetup(argHash);

        } else if (runMode.equals(RUNM_HLESS_MAPF)) {//launch mapFUSER when data is locally there for a region
            Launcher.fullRegionalHeadlessMapFuser(argHash);

        } else {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "Unknown runMode: " + runMode + ", attempting to "
                    + "launch the process from Addons...");
            ArrayList<String> arr = new ArrayList<>();
            arr.add(ARG_RUNMODE + runMode);
            if (args != null) {
                for (String s : args) {
                    arr.add(s);
                    EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Adding argument " + s);
                }
            }
            String[] nargs = new String[arr.size()];
            nargs = arr.toArray(nargs);
            new Launcher().launch(nargs);
        }

    }

    /**
     * The flex runmode decides between DataMiner or the 'server'.
     * Basically, if a DataMiner exists then 'server'.
     * @param args 
     */
    public static void flexRunMode(String[] args) {

        //Rule 4: If flex then check miner. If exists then launch server. Otherwise launch miner.
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                "Flex-mode: check that the miner is running:");
        EnfuserLogger.sleep(1000,EnfuserLaunch.class);
        boolean minerIsOn = RegionalMiner.minerIsOn();

        if (minerIsOn) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "\t Miner is running. Server then.");
            TaskQueue.schedulerLoop();

        } else {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "\t Miner is NOT running. switching to Miner then.");
            RegionalMiner.main(args);
        }

         EnfuserLogger.sleep(1000,EnfuserLaunch.class);
    }
    

    /**
     * Add a single key-value pair to crypts.dat and save the file afterwards.
     * This uses name=x, value=y arguments that are to be given to String[] args,
     * and then passed to a HashMap
     * @param argHash the hash representation of run argument String[].
     */
    public static void cryptAdd(HashMap<String, String> argHash) {
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Adding a key-value pair to crypts.dat. ");
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"The 'key' will be given by '" + ARG_NAME
                + "', and the value is given by '" + ARG_VALUE + "'");
        String name = argHash.get(ARG_NAME);
        String value = argHash.get(ARG_VALUE);
        if (name != null && value != null) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "Both the key and values have been given.");
            GlobOptions.get().addKeyValueToCrypts(name, value);
        } else {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,
                    "Both the key and values have NOT been given successfully. Abort.");
        }
    }

    /**
     * Run the headless Setup process, that is, sets up one modelling region
     * and all required static configuration files for it.
     * This should target a directory that has one or more area sub-directories,
     * which have a valid osmLayer file in them.
     * @param argHash the argument hash that should have at least filedir=x.
     */
    public static void headlessSetup(HashMap<String, String> argHash) {
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Launching headless region SETUP...");

        String osmDir = argHash.get(ARG_FILEDIR);
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"osm directory = " + osmDir
                + " (given by " + ARG_FILEDIR + "=...)");

        String regName = argHash.get(ARG_NAME);
        String sourceNfo = ARG_NAME;
        if (regName == null) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Region name has not"
                    + " been specified, taking it from the directory name.");
            File f = new File(osmDir);
            regName = f.getName();
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"\t => " + regName);
            sourceNfo = ARG_FILEDIR;
        }

        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Region name = " + regName
                + " (given by " + sourceNfo + "=...)");

        String shrink = argHash.get(ARG_VALUE);
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"areaShrink = " + osmDir
                + " (given by " + ARG_VALUE + "=...)");
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Launching in 2 seconds...");
        EnfuserLogger.sleep(2000,EnfuserLaunch.class);

        String over = argHash.get(ARG_OVERWRITE);
        if (over != null && over.contains("true")) {
            RegionProcessor.OVERWRITES = true;
        } else {
            RegionProcessor.OVERWRITES = false;
        }
        
        RegionProcessor.processRegion(null, regName, shrink, osmDir);
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Region Setup is done.");
    }

    /**
     * Create default FusionOptions instance from area name only.
     * @param name the area name.
     * @return options.
     */
    public static FusionOptions fromArea(String name) {
   
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Area name = " + name + " (given by "
                + ARG_NAME + "=...)");
        // find the region
        ModellingArea m = GlobOptions.get().getModellingArea(name);
        if (m==null) {
            EnfuserLogger.log(Level.SEVERE,EnfuserLaunch.class,"FusionOptions search based"
                    + " on area name was unsuccessful. Unknown area="+name);
            return null;
        }
        
        String region = m.regionName();
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Region = " + region);
        FusionOptions ops = new FusionOptions(region,name);
        
        return ops;
    }

    /**
     * Launch a single modelling area run with the defined area name.THe model
     * run will be launched regardless of schedulers, which will be bypassed.
     *
     * @param argHash hargument hash that must contain the area name with a key
     * "name".
     * @param asRunnable if true, then the task will be run on a separate Thread.
     */
    public static void areaRunTask(HashMap<String, String> argHash, boolean asRunnable) {
        if(TaskQueue.EnfuserIsBusy()) {
            return;
        }
        
        FusionOptions ops = hashIntoOptions(argHash);
        if (ops==null) return;
       
        ArrayList<FusionOptions> op= new ArrayList<>();
        op.add(ops);
        
        RunTask.ALLOW_EXIT = false;//prevent System.exit() after the execution of the thread.
        if (asRunnable) {
            Runnable runnable = new RunTask(op);
            Thread thread = new Thread(runnable);
            thread.start();
            
        } else {
            RunTask task = new RunTask(op);
            task.run();
        } 
    }

public static FusionOptions hashIntoOptions(HashMap<String, String> argHash) {
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Launching single area modelling task...");
        String name = argHash.get(ARG_NAME);
        EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Area name = " + name + " (given by "
                + ARG_NAME+ "=...)");
        // find the region
        FusionOptions ops = fromArea(name);
        if (ops==null) {
            EnfuserLogger.log(Level.SEVERE,EnfuserLaunch.class,
                    "AreaRun task could not launch. Matching ModellingArea"
                            + " was NOT found from any currently installed ERCF's. Searched name was "+name); 
            return null;
        }
        TaskQueue.modifyOptionsFromArguments(ops,argHash);
        return ops;
}
    

    public static class LaunchRunnable implements Runnable {

        private String[] args;
        public LaunchRunnable(String[] args) {
            this.args = args;
        }

        @Override
        public void run() {
            EnfuserLaunch.main(args);
        }

    }

}
