/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.tasker;

import java.io.File;
import org.fmi.aq.enfuser.core.EnfuserLaunch;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_ARCHIVING;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_HOURSPAN;
import static org.fmi.aq.enfuser.core.EnfuserLaunch.ARG_RUNDATE;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.ERCF;
import static org.fmi.aq.enfuser.options.FusionOptions.busyFileName;
import org.fmi.aq.enfuser.options.ModellingArea;
import org.fmi.aq.enfuser.options.Regions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.ftools.FileOps;

/**
 *This class is for the runmode 'server' in which the program either
 * stays in continuous loop (daemon) and waits for scheduled tasks to launch, or
 * checks schedules for one time and terminates.
 * 
 * NOTE: the daemon style of using the program has suffered from memory leakage
 * problems and is NOT recommended to be used at the moment.
 * 
 * The decision on what tasks to launch are based on run triggers that are UTC-hours
 * that are matched against current system time. These UTC-hours are properties
 * of ModellingArea instances, that are held by ERCF's.
 * 
 * @author johanssl
 */
public class TaskQueue {

    /**
     * Check system time and form a list of queued tasks.This list in essence
 is a list of FusionOptions that hold the necessary RunParameters to do the task.
     * @param testing if true, the method does NOT launch any of the scheduled tasks
     * 
     * but prints-out logging information to see what's would've happened.
     * @return 
     */
    public static int launchQueuedTasks(boolean testing) {
        Dtime currentDate = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);
        int hour = currentDate.hour;
        
        EnfuserLogger.log(Level.INFO, TaskQueue.class,
                        "RunServer launching queued tasks for UTC hour "+ hour);
        
        Regions regs = GlobOptions.get().allRegs;
        
        ArrayList<FusionOptions> ops = new ArrayList<>();
        for (ERCF ercfs:regs.ercfs) {//iterate over regions
            for (ModellingArea m:ercfs.areas) {//over areas
                if (m.runTriggers.isEmpty()) continue;
                String mod = m.runTriggers.get(hour);
                String list = m.runTiggerString();
                 EnfuserLogger.log(Level.INFO, TaskQueue.class,
                        "\t "+ m.regionName+","+m.name + " has "+ m.runTriggers.size() +list
                                +" trigger hours.");
                if (mod==null) {
                     EnfuserLogger.log(Level.INFO, TaskQueue.class,
                        "\t\t will not be added to list this hour.");
                    continue;
                }//nothing scheduled
                
                FusionOptions op = new FusionOptions(m.regionName(),m.name);//this inherits RunParameters from 'm'
                //deal the mod as an argument line
                HashMap<String, String> hash = EnfuserLaunch.argumentHash(mod.split(" "));
                modifyOptionsFromArguments(op, hash);
                EnfuserLogger.log(Level.INFO, TaskQueue.class,
                 "ADDING Scheduled run with parameters: " +op.runParameterPrintout());
                if (!testing) ops.add(op);
                
            }
        }
        
        if (!ops.isEmpty()) {//there are tasks to be launched.
            Runnable runnable = new RunTask(ops);
            Thread thread = new Thread(runnable);
            thread.start();
        }
        
         EnfuserLogger.sleep(1000,TaskQueue.class);
         return ops.size();
    }
    
    /**
     * Enter a while-loop and occasionally check for scheduled modelling tasks.
     * In case the daemon mode is disabled (false) then the loop terminates
     * after a single iteration.
     */ 
    public static void schedulerLoop() {
       
        if (EnfuserIsBusy()) {
             return;
        }
        
        int lastCheckHour =-1;
        int lastSize =0;
        while (true) {
            
            Dtime dt = Dtime.getSystemDate_FH();
            int hour = dt.systemHours;
            if (hour!=lastCheckHour) {
                lastCheckHour = hour;
                boolean testing = false;
               lastSize = launchQueuedTasks(testing);
            }
            
            if (!GlobOptions.get().serverDaemon()) {
                EnfuserLogger.log(Level.INFO, TaskQueue.class,
                        "RunServer will exit after the tasks have been completed"
                                + " (Last task size ="+lastSize+")");
                break;
            }

           EnfuserLogger.sleep(5000,TaskQueue.class);    
           logBusy();     
        }// while true
    }
      
      /**
       * Mark that an instance is currently running to avoid parallel instances
       * of modelling tasks to launch.
       */
      public static void logBusy() {
      
        if (GlobOptions.get().busyLoggerDisabled()) {
            return;//do nothing, which means that
        }        //the file is not updated => all tasks are allowed to run in parallel.

        String name = GlobOptions.get().getRootDir() + busyFileName;
        try {
            Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
            FileOps.lines_to_file(name, dt.getStringDate(), false);
        } catch (Exception e) {
        }
    } 
      
      
      private final static int BEACON_NA = 3600 * 24 * 365;

    /**
     * Check the amount of time that has passed since a modelling task has been
     * reported to be operational. The purpose of this check is to cancel any
     * attempts to launch a parallel modelling task before the last one has been
     * completed to make sure that enough memory and processing power is
     * available for each task.
     *
     * @return amount of time in seconds since last logged modelling task
     * update.
     */
    private static int getBusySeconds() {
        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
        String name = GlobOptions.get().getRootDir()  + busyFileName;

        File f = new File(name);
        if (!f.exists()) {
            return BEACON_NA;
        }
        try {
            String content = FileOps.readStringArrayFromFile(name).get(0);
            Dtime dtLast = new Dtime(content);

            int secs = (int) (dt.systemSeconds() - dtLast.systemSeconds());
            return secs;

        } catch (Exception e) {

        }

        return BEACON_NA;//for exceptions
    }
    
    /**
     * Check the beacon file for logged activity markers. In case there
     * are recently added markers then the model is deemed to be currently
     * running.
     * @return true if an active instance seems to be running.
     */
    public static boolean EnfuserIsBusy() {
        int secs = getBusySeconds();
        if (secs < 5*60) {
             EnfuserLogger.log(Level.WARNING, TaskQueue.class,
                     "RunServer cannot launch since there seems to be a running"
                             + " instance of it already. This assessment is based on "
             +busyFileName +" (There is a tolerance of 5 minutes, so it is possible"
                     + "that a running isntance just recently terminated). "
                     + "This check can be disabled via globOps.txt");
            
            return true;
        }
        return false;
    } 
    
    /**
     * Modify runParameters according to custom content given in the hash.
     * This can include, e.g., a switch of time origin for the modelling task, or
     * a modification of temporal modelling span.
     * @param ops The options used for a scheduled modelling run.
     * @param argHash the argument hash used to apply modifications. The keys
     * of the hash should follow the naming conventions of EnfuserLaunch keys.
     */  
    public static void modifyOptionsFromArguments(FusionOptions ops,
            HashMap<String, String> argHash) {
        
        //fetch modellingArea
        ModellingArea m = GlobOptions.get().getModellingArea(ops.areaID());
        RunParameters p = ops.getRunParameters();
        //start from base time
        Dtime dt = Dtime.getSystemDate_FH();
        int backW = m.backW;
        int forwH = m.forwH;
        
        //1: check output modifications.
        String arch = argHash.get(ARG_ARCHIVING);
        if (arch != null && arch.toLowerCase().contains("true")) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Output skip -instruction found.");
            p.setToArchivingMode();
            
        } else if (!p.videoOutput() && !p.imagesOutput() && !p.gridsOutput()) {//if it already is in archiving mode, then notify
              EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Video, Images and gridded output has been "
                      + "disabled - this task will only produce archived zip-files.");       
        }
        
        //2. check temporal span
        String hspan = argHash.get(ARG_HOURSPAN);
        if (hspan != null) {
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Custom modelling hour"
                    + " span given. This should be 1 or 2 (or 3) integers");
        
                String[] tsp = hspan.split(",");
             if (tsp.length==3) {
                 EnfuserLogger.log(Level.FINE, EnfuserLaunch.class, "Modelling area parameters"
                        + " have been defined with 3 integers (a,b,c) for "
                        +m.regionName+","+ m.name +", Consider switching to "
                                + "using 'b,c' since the spinoff-hour (a) need not to be defined anymore.");

               backW = Math.abs(Integer.valueOf(tsp[1]));
               forwH = Math.abs(Integer.valueOf(tsp[2]));

            } else if (tsp.length ==2) {
                backW = Math.abs(Integer.valueOf(tsp[0]));
                forwH = Math.abs(Integer.valueOf(tsp[1]));
                
            } else if (tsp.length ==1) {
                backW = 0;
                forwH = Math.abs(Integer.valueOf(tsp[0]));
                EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Temporal span has been given with"
                        + " a single integer '"+forwH +"'. Setting this as 'forecasting' period.");
                
            } else {
                EnfuserLogger.log(Level.SEVERE, EnfuserLaunch.class, "Modelling area parameters"
                        + " have been incorrectly set for modelling time span! "
                        +m.regionName+","+ m.name);
                backW=2;
                forwH=2;
            }
        }
       
        //3: custom time
         String date = argHash.get(ARG_RUNDATE);
        if (date != null) {
            
            boolean rollingDate = false;
            if (arch!=null &&arch.contains("rolling")) {
                rollingDate=true;
            }
            
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Custom run-date found: " + date);
            dt = new Dtime(date);
            
            if (rollingDate) {
                if (p.archivingMode()) {
                    dt = getArchiverStartPoint(ops, dt, backW, forwH);
                    EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Custom run-date is date"
                            + " from which archives are to be build up. Current start date = " + dt.getStringDate());
                }
            }
        }
        
         //set span
        p.setDates(dt, backW, forwH);
        FuserTools.mkMissingDirsFiles(ops); 
    }
  
    private final static String ARCHFLAG_FILE = "_rollingArch.txt";
    /**
     * For a modelling region and area set in the given FusionOptions, find a
     * continuation point for building up modelling results archives.
     * @param ops the options that also hold the region-area definition
     * necessary to define the task.
     * @param dt starting point time. In case this time is already covered
     * in the archives, then the next hour without data is searched and
     * taken instead.
     * @return new, possibly modified starting time for modelling task.
     */
    public static Dtime getArchiverStartPoint(FusionOptions ops, Dtime dt, int backW, int forwH) {

        
        //first check date file flag
        String flagfile = GlobOptions.get().getRootDir() +ops.areaID() + ARCHFLAG_FILE;
        File test = new File(flagfile);
        if (test.exists()) {
            ArrayList<String> arr = FileOps.readStringArrayFromFile(test);
            Dtime dtfile = new Dtime(arr.get(0));
            //end marker at line 2?
            Dtime dtEnd = null;
            if (arr.size()>1)dtEnd= new Dtime(arr.get(1));
            
            if (dtEnd!=null && dtfile.systemHours()> dtEnd.systemHours()) {
                EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Archiving restore "
                        + "point: End marker exceeded: "+dtEnd.getStringDate_noTS() +". Terminating now.");
                System.exit(0);
            }
            
            Dtime next = dtfile.clone();//starting point for next run
            next.addSeconds(3600*forwH);
            arr.clear();
            arr.add(next.getStringDate_noTS());
            
            if (dtEnd!=null)arr.add(dtEnd.getStringDate_noTS());//end marker
            //update the flag file for next run
            FileOps.printOutALtoFile2(test, arr, false);
            EnfuserLogger.log(Level.INFO,EnfuserLaunch.class,"Found restore point from: "
                    +test.getAbsolutePath() +", "+dtfile.getStringDate_noTS()
                    +", next will be: "+ next.getStringDate_noTS());
            
            return dtfile; 
        }
        
        //first check the first day that has no data
        Dtime current = dt.clone();
        current.addSeconds(-3600*dt.hour - 60*dt.min - dt.sec);
        
        
        String dirBase = ops.archOutDir(null);
        EnfuserLogger.log(Level.INFO,TaskQueue.class,"Finding continuation point for archive task at " 
                + dirBase + ", browsing data from " + dt.getStringDate_noTS());
        
        int maxN = 10000;
        for (int n = 0; n < maxN; n++) {
            String filepath = dirBase + current.year + FileOps.Z 
                    + "allPollutants_" + current.getStringDate_fileFriendly() + ".zip";
            File f = new File(filepath);
            if (!f.exists()) {
                EnfuserLogger.log(Level.INFO,TaskQueue.class,
                        "\t found the nearest hour WITHOUT existing data: " + current.getStringDate());
                EnfuserLogger.log(Level.INFO,TaskQueue.class,
                        "This did not exist: "+ f.getAbsolutePath());
                return current;
            }

            current.addSeconds(3600);
        }

        return null;
    }

   
    
}
