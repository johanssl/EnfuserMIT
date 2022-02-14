/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import java.util.logging.Logger;
import static org.fmi.aq.enfuser.mining.miner.Mining.DOWNL_SUCCESS;
import static org.fmi.aq.enfuser.mining.miner.Mining.downloadFromURL_static;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.Zipper.UnZip;

/**
 * A class to handle file download in a Thread.
 * @author johanssl
 */
public class Downloader implements Runnable {

    public final String fname;
   
    int totalMegs;
    int currMegs = 0;

    final String dir;
    final String url;
    final String[] creds;
    boolean certBypass = false;

    public Downloader(String fname, String dir, int totalMegs,
            String url, String[] creds, boolean certBypass) {
        this.fname = fname;
        this.dir = dir;
        this.totalMegs = totalMegs;
        this.url = url;
        this.creds = creds;
    }

    @Override
    public void run() {

        boolean simple = false;
        boolean forceRefresh = false;
        try {
            String fullName = this.dir + this.fname;
            int succ = downloadFromURL_static(fullName, simple, certBypass,
                    this.url, System.out, this.creds, (float) this.totalMegs, null, forceRefresh);

            if (succ == DOWNL_SUCCESS && fname.contains(".zip")) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"Download complete: unzipping");

                UnZip(fullName, dir);
            }

        } catch (Exception ex) {
            Logger.getLogger(Downloader.class.getName()).log(Level.SEVERE, null, ex);
        }
        //close this thread now, complete
    }

    }

