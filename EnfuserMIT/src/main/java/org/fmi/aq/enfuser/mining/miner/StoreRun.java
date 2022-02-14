/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.miner;

import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 * This thread is for a single feed store-operation.
 * It terminates after the store operation has been completed. Also,
 * the RegionalMiner that creates these keep track of these threads
 * and can terminate them if they seem unresponsive.
 * 
 * In this thread also the status of the feed is updated.
 * @author johanssl
 */
public class StoreRun implements Runnable {

    Feed feed;
    public final static String STATUS_MISSING_APIK="missingCredentials";
    public final static String STATUS_DONE="done";
    public final static String STATUS_EXTRACTING="extracting";
    public final static String STATUS_EXCEPTION="done/Exception";
    
    public StoreRun(Feed f) {
        this.feed = f;
    }

    @Override
    public void run() {

        try {
            feed.statusString = STATUS_EXTRACTING;
            feed.lastSuccessStoreCount = -1;
            feed.previousWasSuccess = false;
            Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
            feed.setSout_forStore();
            feed.println("DataExtraction launched: " + feed.regname + " " 
                    + feed.mainProps.feedDirName + ", " + dt.getStringDate());

            //check file amount limit
            ArrayList<String> arr=null;
            boolean enough = feed.fileAmountCapped();
            if (enough) {
                feed.println("File amount capped.");
                arr = new ArrayList<>();

                feed.lastStoreCount = 0;
            } else {
                //check credentials block
                boolean cannotStore = feed.cannotStore_missingCreds();
                if (cannotStore) {
                    feed.println("Cannot extract data due to missing credentials/API-key.");
                } else {
                    arr = feed.store();
                }
            }

            feed.AWSdump(arr);//does nothing if AWS S3 settings have not been specified.
            feed.previousWasSuccess = feed.lastStoreCount >= 0;
            feed.println("DataExtraction finished: " + feed.regname + " " + feed.mainProps.feedDirName
                    + ", success = " + feed.previousWasSuccess + " with " + feed.lastStoreCount + " adds");
            feed.println("\t was success = " + feed.previousWasSuccess);

            if (feed.previousWasSuccess) {
                feed.lastSuccessStoreDt = dt.clone();
                feed.lastSuccessStoreCount = feed.lastStoreCount;
            }

            feed.statusString = STATUS_DONE;
            if (feed.cannotStore_missingCreds()) feed.statusString = STATUS_MISSING_APIK;
            
        } catch (Exception e) {
            EnfuserLogger.log(e,Level.WARNING, feed.getClass(),
                    feed.mainProps.feedDirName +" encountered an exception during store operation.");
            feed.printStackTrace(e);
            feed.statusString = STATUS_EXCEPTION;//TODO you don't see these, can this happen?

        } finally {
            try {
                if (feed.SOUT != System.out) {
                    feed.SOUT.close();
                }
            } catch (Exception ee) {
            }
        }
    }

}
