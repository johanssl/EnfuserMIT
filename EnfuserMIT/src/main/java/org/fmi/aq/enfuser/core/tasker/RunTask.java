/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.tasker;

import org.fmi.aq.enfuser.core.FusionOneLiners;
import java.util.ArrayList;
import org.fmi.aq.enfuser.core.EnfuserLaunch;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.interfacing.Launcher;

/**
 * Answers a fusion request (starts a new thread)
 */
public class RunTask implements Runnable {


ArrayList<FusionOptions> runOptions;

    public RunTask(ArrayList<FusionOptions> runOptions) {
        this.runOptions = runOptions;
    }

    @Override
    public void run() {

        try {
          
            for (FusionOptions ops: runOptions) {
                System.gc();
                // String addInfo ="Fusion Server: requested data is being uploaded to your client directory (A new sub-directory has been created for this content.). Please, try another request! ";   
                
              Dtime currentDate = Dtime.getSystemDate();

                //streamers
                //reset output streams
                String slog = "logs/sout_" + ops.areaID()+"_"+ currentDate.getStringDate_YYYY_MM_DDTHH() + "Z.txt";
                String elog = "logs/errOut_" +ops.areaID()+"_"+  currentDate.getStringDate_YYYY_MM_DDTHH() + "Z.txt";
                String root = GlobOptions.get().getRootDir();
                ArrayList<String> soutFiles = new ArrayList<>();
                soutFiles.add(root + slog);

                ArrayList<String> errFiles = new ArrayList<>();
                errFiles.add(root + elog);

                EnfuserLaunch.setPrintStreams(soutFiles,errFiles);
                boolean compare = true;
                GlobOptions.get().printCryptedKeys(compare,"");
                try {
                    Launcher.operativeEmailAlertCheck();
                    FusionOneLiners.extractAndFuse(ops,null,null);
                    EnfuserLaunch.flushLogsToOutputDirectory(ops);
                } catch (Exception ex) {
                    // addInfo = "FusionServer: Whoops, something went wrong! \n" +ex.getMessage() + ex.toString();
                    ex.printStackTrace();
                }

            }//for requests

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //check force termination
            if (ALLOW_EXIT) {
                if (!GlobOptions.get().serverDaemon()) {
                    EnfuserLogger.log(Level.FINER,RunTask.class,"Server mode set to docker - force termination.");
                    System.exit(0);
                }
            }

        }

    }
    public static boolean ALLOW_EXIT = true;

}// answerTask
