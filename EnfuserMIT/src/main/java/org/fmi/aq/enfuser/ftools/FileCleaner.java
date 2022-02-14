/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import org.fmi.aq.enfuser.mining.feeds.Feed;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import static org.fmi.aq.enfuser.customemitters.BGEspecs.getTemporaryEmitterDir;
import static org.fmi.aq.enfuser.ftools.FuserTools.copyTo;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 *
 * @author johanssl
 */
public class FileCleaner {

    public final static String[] EXTENSIONS = {
        ".png",
        ".txt",
        ".nc",
        ".kmz",
        ".kml",
        ".csv",
        ".zip",
        ".gml",
        ".gr",
        ".shg"
    };

    public final static int TOO_FRESH_TO_DELETE = 14;
    public final static int TOO_OLD_TO_DELETE = 16;

    public static int clean(FusionOptions ops) {
        int k = 0;
        ArrayList<String> dirs = new ArrayList<>();
        try {

            String temp = ops.getArguments().get(ERCFarguments.MINER_CLEANER);
            if (temp != null) {
                String bool = temp.toLowerCase().replace(" ", "");
                if (bool.equals("yes") || bool.equals("true")) {

                    //getDirectories to clean
                    ArrayList<Feed> feeds = FeedFetch.getFeeds(ops,FeedFetch.MUST_ACT);
                    for (Feed feed : feeds) {
                        String dir = feed.primaryMinerDir;
                        dirs.add(dir);
                    }//for feeds

                }//if set true

            }//if arg specified
            //add logs dir

            String log = GlobOptions.get().getRootDir() + "logs/";
            dirs.add(log);

            File f = getTemporaryEmitterDir(ops);
            if (f.exists()) {
                dirs.add(f.getAbsolutePath());
            }

            EnfuserLogger.log(Level.FINER,FileCleaner.class,"Cleaner trigger, days = " + TOO_FRESH_TO_DELETE);
            if (TOO_FRESH_TO_DELETE < 0) {
                EnfuserLogger.log(Level.FINER,FileCleaner.class,"\t negative value, skip cleaning.");
                return k;
            }

            long secsNow = System.currentTimeMillis() / 1000;

            for (String dir : dirs) {
                f = new File(dir);
                File[] ff = f.listFiles();
                EnfuserLogger.log(Level.FINER,FileCleaner.class,"Cleaner: Checking dir " + dir);
                if (ff == null) {
                    EnfuserLogger.log(Level.WARNING,FileCleaner.class,
                  "Cleaner: directory does not exist! " + dir);
                    continue;
                }

                    for (File file : ff) {
                        String name = file.getName();
                        long secsFile = file.lastModified() / 1000;
                        //check maturity
                        long oldDays = (secsNow - secsFile) / 3600 / 24;

                        if (oldDays > TOO_FRESH_TO_DELETE && oldDays < TOO_OLD_TO_DELETE) {
                            //can be deleted.
                        } else {
                            continue;
                        }

                        for (String ext : EXTENSIONS) {//check that the file extension is something that can be safely deleted
                            if (name.contains(ext)) {
                                EnfuserLogger.log(Level.FINER,FileCleaner.class,"t Cleaning " + file.getAbsolutePath());
                                file.delete();
                                k++;
                                break;
                            }
                        }
                    }//for files in dir  


            }

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.WARNING, FileCleaner.class, 
                    "General file cleanup process (logs, old files) encountered an Exception.");
        }
        EnfuserLogger.log(Level.FINER,FileCleaner.class,"Cleaned " + k + " files.");
        return k;
    }
    
   /**
    * Clear files under the specified directory that are just old enough to be deleted
    * between a given maturity window.
    * This method can only delete files with specific extensions, such as
    * txt, csv, png or nc. (i.e., input data formats).
    * @param sout stream for console printout
    * @param dir directory in which files are deleted.
    * @param TOO_FRESH_TO_DELETE in hours, files newer than this are left alone.
    * @param TOO_OLD_TO_DELETE in hours, files older than this are left alone
    * @return number of deleted files.
    */ 
   public static int cleanSingleDir(PrintStream sout, String dir,
           int TOO_FRESH_TO_DELETE, int TOO_OLD_TO_DELETE) {
     int k = 0;
        
        try {

            sout.println("Cleaner trigger, hours = " + TOO_FRESH_TO_DELETE);
            if (TOO_FRESH_TO_DELETE < 1) {
                sout.println("\t This value should be at least 2 hours.");
                return k;
            }
            if (TOO_OLD_TO_DELETE <= TOO_FRESH_TO_DELETE) {
                sout.println("Incorrect parameters, return.");
                return k;
            }
            
            long secsNow = System.currentTimeMillis() / 1000;

               File f = new File(dir);
                File[] ff = f.listFiles();
                sout.println("Cleaner: Checking dir " + dir);
                if (ff == null) {
                    sout.println("Cleaner: directory does not exist! " + dir);
                    return 0;
                }



                    for (File file : ff) {
                        String name = file.getName();
                        long secsFile = file.lastModified() / 1000;
                        //check maturity
                        long hours = (secsNow - secsFile) / 3600;

                        if (hours > TOO_FRESH_TO_DELETE && hours < TOO_OLD_TO_DELETE) {
                            //can be deleted.
                        } else {
                            continue;
                        }

                        for (String ext : EXTENSIONS) {//check that the file extension is something that can be safely deleted
                            if (name.contains(ext)) {
                                sout.println("t Cleaning " + file.getAbsolutePath());
                                file.delete();
                                k++;
                                break;
                            }
                        }
                    }//for files in dir  

           
        } catch (Exception e) {
             EnfuserLogger.log(e,Level.WARNING, FileCleaner.class, 
                    "Singe directory cleanup process encountered an Exception for "+dir);
        }
        sout.println("Cleaned " + k + " files.");
        return k;
    }

    public static void relocateFiles(String mustContain, String sourceDir, String targetDir, int olderThan) {
        if (mustContain.length() < 2) {
            EnfuserLogger.log(Level.FINER,FileCleaner.class,
                    "Cleaner:relocator: identifier String is too short "
                            + "for safe operations (" + mustContain + "), abort");
            return;
        }
        File td = new File(targetDir);
        if (!td.exists()) {
            EnfuserLogger.log(Level.FINER,FileCleaner.class,
                    "Cleaner:relocator: target directory does NOT exist yet. (" + targetDir + "), abort");
            return;
        }

        File f = new File(sourceDir);
        File[] ff = f.listFiles();
        long secsNow = System.currentTimeMillis() / 1000;

        for (File test : ff) {
            String path = test.getAbsolutePath();
            String nam = test.getName();
            if (!nam.contains(mustContain)) {
                continue;
            }

            long secsFile = test.lastModified() / 1000;
            //check maturity
            long oldDays = (secsNow - secsFile) / 3600 / 24;
            if (oldDays < olderThan) {
                continue;
            }
            EnfuserLogger.log(Level.FINER,FileCleaner.class,
                    "\t checking " + path + ", which is " + oldDays + " days old.");

            String existing = targetDir + nam;
            File ex = new File(existing);
            if (ex.exists()) {
                EnfuserLogger.log(Level.FINER,FileCleaner.class,"\t\t safe to delete.");
                test.delete();
            } else {

                try {
                    EnfuserLogger.log(Level.FINER,FileCleaner.class,"\t\t relocate to " + existing);
                    EnfuserLogger.log(Level.FINER,FileCleaner.class,"\t\t\t=> copy: " + ex.getAbsolutePath());
                    copyTo(test, ex);

                    EnfuserLogger.log(Level.FINER,FileCleaner.class,"\t\t\t\t done, delete the original.");
                    test.delete();
                } catch (Exception e) {
                     EnfuserLogger.log(e,Level.WARNING, FileCleaner.class, 
                    "File relocation process encountered an Exception: "+ex.getAbsolutePath() +" -> "+test);
                }
            }

        }
    }

}
