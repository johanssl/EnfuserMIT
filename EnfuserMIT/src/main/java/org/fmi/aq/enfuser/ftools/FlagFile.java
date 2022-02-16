/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import java.io.File;
import org.fmi.aq.essentials.date.Dtime;

/**
 *
 * @author johanssl
 */
public class FlagFile {
  
    private final static int FLAG_NA = Integer.MAX_VALUE;
    public final static String FLAG_FILE = "dateFlag.txt";
    
    /**
     * Check time stamp in the flag file and compute hours since that based
     * on system clock. If the file does not exist yet, a flag file is created
     * and this returns Integer maximum value.
     * @param dir directory for checking the dateFlag.txt
     * @return hours since the flagFiles's time.
     */
    private static int getFlagHours(String dir) {
        Dtime dt = Dtime.getSystemDate_utc(true, Dtime.ALL);
        String name = dir  + FLAG_FILE;

        File f = new File(name);
        if (!f.exists()) {
            updateFlagTime(dir);
            return FLAG_NA;
        }
        try {
            String content = FileOps.readStringArrayFromFile(name).get(0);
            Dtime dtLast = new Dtime(content);

            int secs = (int) (dt.systemSeconds() - dtLast.systemSeconds());
            return secs/3600;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return FLAG_NA;//for exceptions
    }
    
    /**
     * Check if the given amount of hours has passed since the last check
     * at the given directory. If this is the first time this check is performed
     * at the directory, this returns true and updates the flag file for later use.
     * In case this returns true the time saved in the flag file will be updated.
     * 
     * @param hourThresh the amount of hours that must be exceeded for this
     * to return true.
     * @param dir the directory for the check
     * @return 
     */
    public static boolean flagHourExceeds(int hourThresh, String dir) {
        int hours = getFlagHours(dir);
        if (hours > hourThresh) {
            updateFlagTime(dir);
            return true;
        }
        return false;
    }   
    
    /**
     * Store the current system time to the flag file at the given directory.
     * @param dir the directory.
     */
    private static void updateFlagTime(String dir) {

        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
        String name = dir+ FLAG_FILE;
        try { 
         FileOps.lines_to_file(name, dt.getStringDate_noTS() + "\n", false);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    
    
}
