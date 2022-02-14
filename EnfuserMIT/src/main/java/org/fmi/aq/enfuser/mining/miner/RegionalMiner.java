/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.mining.miner;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.ftools.Streamer;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HttpsURLConnection;
import static org.fmi.aq.enfuser.ftools.FuserTools.tab;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.ERCFarguments;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.fmi.aq.enfuser.core.EnfuserLaunch;
import org.fmi.aq.enfuser.mining.feeds.FeedConstructor;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;
import org.fmi.aq.interfacing.GraphicsMod;
import org.fmi.aq.interfacing.Launcher;

/**
 * This class is a regional DataMiner that can launch and monitor each
 * Feed's data extraction methods. The DataMiner, which launches from this class's
 * main method, is a collection of multiple RegionalMiner instances that are
 * maintained in an infinite while-loop. While doing so, this class also
 * updated the 'minerBeacon' so that no overlapping instances will be running
 * unintentionally in parallel.
 * 
 * @author johanssl
 */
public class RegionalMiner implements Runnable {

    public FusionOptions ops;
    String name;

    public ArrayList<Feed> feeds;
    public Thread[] storeThreads;
    public String[] threadIDS;

    public long[] lastUpdate_ticks;
    public long[] update_counts;
    public long[] lastTry_secs;

    public long current_sysSecs;

    /**
     * This will define the forecasting period
     * for ALL feeds that can deliver forecasts.
     */
    public final static int FORECASTING_HOURS =24*2;//
    public final static int MINER_BEACON_TOLERANCE_S = 60*2;
    public static int SLEEP_PER_REG_MS;//ms
    public static void start() {

        EnfuserLogger.log(Level.INFO,RegionalMiner.class,"Launching RegionalMiner!");
        Runnable runnable = new RegionalMiner();
        Thread thread = new Thread(runnable);
        thread.start();

    }
    
    /**
     * Get a list of hour steps to be used to extract forecasted data
     * with the given hourly step size.
     * @param step the step size, e.g., every 3 hours
     * @return a list of hours
     */ 
    public static ArrayList<Integer> forecastingOffsets(int step) {
       ArrayList<Integer> hours = new ArrayList<>();
       for (int i =0;i<=FORECASTING_HOURS;i+=step) {
           hours.add(i);
       }
       return hours;
    }

    @Override
    public void run() {
        main(null);
    }

    public static boolean checkContains_NonZero(String file, String ID, PrintStream out) {
        out.println(ID + " scheduler: checking existance of " + file);
        File f = new File(file);
        if (f.exists()) {
            out.println("\t yep. it does exist...");

            if (f.length() > 0) {
                out.println("\t...and the size of the file is non-zero.");
                return true;
            } else {
                out.println("\t...but the size is 0 bytes.");
            }
        } else {
            out.println("\t The file does not exist.");
        }

        return false;

    }

    /**
     * Check whether or not a miner instance is currently operating in the
     * background.If so, then a new instance launch can be terminated. In case
     * this check has been disabled in globOps.txt then this always returns
     * false.
     *
     * @return true/false if a DataMiner instance is already running.
     */
    public static boolean minerIsOn() {

        if (GlobOptions.get().minerBeaconDisabled()) {
            EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                    "RegionalMiner.minerIsOn(): miner beacon has been disabled (check ignored)");
            return false;
        }

        int secs = getBeaconSeconds();
        EnfuserLogger.log(Level.INFO,RegionalMiner.class,"Beacon update seconds: " + secs);
        if (secs < MINER_BEACON_TOLERANCE_S) {
            EnfuserLogger.log(Level.INFO,RegionalMiner.class,"\t there is another miner instance running => terminate.");
            return true;

        } else {
            EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                    "Last beacon update was some time ago => no duplicate processes around.");
            return false;
        }

    }

    public static void resetStreamerFiles(Streamer str_o, Streamer str_err,
            String rootdir, String customOut, String customErr, boolean deleteCustoms) {
        String stamp = streamerStamp();
        ArrayList<String> soutFiles = new ArrayList<>();
        soutFiles.add(rootdir + "logs/MINER_sOut_" + stamp + ".txt");
        if (customOut != null) {
            File f = new File(customOut);
            if (f.exists() && deleteCustoms) {
                f.delete();
            }
            soutFiles.add(customOut);
        }

        ArrayList<String> errFiles = new ArrayList<>();
        errFiles.add(rootdir + "logs/MINER_errOut_" + stamp + ".txt");
        if (customErr != null) {
            File f = new File(customErr);
            if (f.exists() && deleteCustoms) {
                f.delete();
            }
            errFiles.add(customOut);
        }

        str_o.resetFileStream(soutFiles, true);
        str_err.resetFileStream(errFiles, true);
    }

    protected static String streamerStamp() {
        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);
        String stamp = dt.getStringDate_YYYY_MM_DD();
        return stamp;
    }
    
    /**
     * Launches the DataMiner, which takes all regions and creates
     * RegionalMiner instances for them. Data extraction will occur
     * in an infinite while loop.
     * 
     * The TestRegions will not participate in this activity as long as
     * there is a non-test region installed region available.
     * @param args 
     */
    public static void main(String[] args) {

        Launcher.operativeEmailAlertCheck();
        FusionOptions[] opsTemp = GlobOptions.get().allRegionalOptions();
        ArrayList<FusionOptions> ops = new ArrayList<>();
        for (FusionOptions o:opsTemp) {
            
            if (o.getArguments().isDuplicantRegion()) {
                EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                        "DataMiner: skipping a duplicant region "+ o.getRegionName());
            }
            
            String name = o.getRegionName();
            if (name.contains("Test")) {
                EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                        "TestRegion excluded from DataMiner regions: "
                                + name +" due to tag 'Test' in the region name.");
            } else {
                ops.add(o);
            }
        }
        
        if (ops.isEmpty()) {//allow test regions then
            EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                        "Array of options (regions) is empty. "
                                + "Allowing test regions to be included.");
            for (FusionOptions o:opsTemp) {
                ops.add(o);
            }
        }
        
        int LEN = ops.size();
        RegionalMiner[] miners = new RegionalMiner[LEN];
        int sleepMillis = MINER_BEACON_TOLERANCE_S*1000/(LEN*2+1);
        if (sleepMillis > 5000) sleepMillis =5000;
        SLEEP_PER_REG_MS = sleepMillis;
            
        int i = 0;
        for (FusionOptions op:ops) {
           
            mkMissingDirsFiles_miner(ops.get(i));
            miners[i] = new RegionalMiner(ops.get(i));
            i++;
        }

        //check that only one global is active
        HashMap<String,Boolean> globs = new HashMap<>();

        for (RegionalMiner rm : miners) {
             String name = rm.ops.getRegionName();
             EnfuserLogger.log(Level.INFO,RegionalMiner.class,"name=" + name);
             
            for (Feed f:rm.feeds) {
                
                String nfo = "\t" + f.mainProps.feedDirName +",global="+f.global
                        +",stores="+f.stores+",reads="+ f.readable;
                EnfuserLogger.log(Level.INFO,RegionalMiner.class,nfo);
                
                if (!f.global) continue;//does not count, only global feeds are checked here.
                if (!f.stores) continue;//does not count since disabled
                String tag = f.mainProps.argTag;//this is a feed in global mode.
           
                Boolean alreadyGlobal = globs.get(tag);
                
                if (alreadyGlobal!=null) {//this is global and there is one already global
                    f.stores = false;//disable this duplicate global feed
                    EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                            "Global feed disabled since another one is active: " 
                                    + tag + " -> " + rm.name);
                    
                } else {//mark as a feed working in global mode for this type.
                    globs.put(tag, Boolean.TRUE);
                }    
            }
        }

        //check beacon timer. Idea: if this file was updated just recently it means that there is ANOTHER instance of Miner already running => terminate.
       boolean minerIsOn = minerIsOn();
        if (minerIsOn) {
            EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                    "\t there is another miner instance running => terminate.");
            EnfuserLogger.sleep(1000,RegionalMiner.class);
            System.exit(ALREADY_RUNNING_EXIT);

        } else {
            EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                    "Last beacon update was some time ago => no duplicate processes around.");
        }

        try {
        updateBeacon(miners[0].ops, "init",false);
        } catch (Exception e) {
            
        }
        long j=0;
        //==========WHILE TRUE main loop==================
        while (true) {
            String allUps = "";
            for (int r = 0; r < LEN; r++) {

                if (miners[r] != null) {

                    miners[r].update();
                    ArrayList<String> upds = miners[r].getStatusStrings();
                    boolean printAll = false;//j%5==0;
                    for (String line: upds) {
                        if (!printAll && line.contains(OFFLINE_TAG))continue;
                        
                        if (line.contains("[W]:")) {
                            EnfuserLogger.log(Level.WARNING,RegionalMiner.class,line);
                        } else {
                           EnfuserLogger.log(Level.INFO,RegionalMiner.class,line); 
                        }
                        allUps += line + "\n";
                    }
                }
                
                EnfuserLogger.sleep(SLEEP_PER_REG_MS,RegionalMiner.class);
            }
            j++;
            boolean manualTerminate = checkManualTerminate();
            updateBeacon(miners[0].ops, allUps, manualTerminate);
            
            System.gc();
        }//while true

    }
    public final static int ALREADY_RUNNING_EXIT =101;
    private final static String TERMINATION_FILE ="TERMINATE_MINER.txt";
    /**
     * Check whether or not the miner has been instructed to terminate
     * with a flag-file. In case the flag file is there it will be consumed
     * and the miner terminates. If this happens the flag-file is deleted so 
     * it can only terminate a single miner once.
     * 
     * This feature can be used to update miner parameters in case the miner
     * has been scheduled to launch repeatedly.
     */
    private static boolean checkManualTerminate() {
        try {
            String fname = GlobOptions.get().getRootDir() + TERMINATION_FILE;
            File test = new File(fname);
            if (test.exists()) {
                test.delete();
                EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                        "Miner manually terminated due to "+ test.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public RegionalMiner() {

    }

    public RegionalMiner(FusionOptions ops) {
        this.ops = ops;
        this.name = ops.getRegionName();
        this.feeds =FeedFetch.getFeeds(ops, FeedFetch.MUST_STORE);

        int FEEDS_LEN = feeds.size();
        this.storeThreads = new Thread[FEEDS_LEN];
        this.threadIDS = new String[FEEDS_LEN];
        this.lastUpdate_ticks = new long[FEEDS_LEN];
        this.update_counts = new long[FEEDS_LEN];
        this.lastTry_secs = new long[FEEDS_LEN];

        for (int i = 0; i < FEEDS_LEN; i++) {
            this.lastUpdate_ticks[i] = -1; //will update instantly 
        }
        GlobOptions.get().setHttpProxies();
    }

    private final static int BEACON_NA = 3600 * 24 * 365;
    public final static String BEACON_FILE = "minerBeacon.txt";

    /**
     * Update the status of DataMiner by logging the recent state in
     * minerBeacon text file.
     * @param ops
     * @param content 
     * @param manualTerminate 
     */
    protected static void updateBeacon(FusionOptions ops, String content, boolean manualTerminate) {

        if (GlobOptions.get().minerBeaconDisabled()) {
            return;
        }

        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
        EnfuserLogger.log(Level.INFO,RegionalMiner.class,"Time now: " + dt.getStringDate());
        
        String name = GlobOptions.get().getRootDir()  + BEACON_FILE;
        try {
            if (manualTerminate) content += "Miner will be manually terminated now.\n";
            FileOps.lines_to_file(name, dt.getStringDate() + "\n" + content, false);
        } catch (Exception e) {
        }
        if (manualTerminate) System.exit(0);
    }

    protected static int getBeaconSeconds() {
        Dtime dt = Dtime.getSystemDate_utc(true, Dtime.ALL);
        String name = GlobOptions.get().getRootDir()  + BEACON_FILE;

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
     * Check the timers and extract data if the current tick marker is different
     * from the last extraction time.
     */
    public void update() {

        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
        this.current_sysSecs = dt.systemSeconds();
        //Dtime dt_fullH = Dtime.getSystemDate_utc(false, Dtime.FULL_HOURS);

        EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                "RegionMiner: current systemDate = " + dt.getStringDate());
        int ind =-1;
        for (Feed f:feeds) {
           ind++;
           if (!f.stores) continue;
            long currentTicker = (dt.systemSeconds() / 60 / f.mainProps.ticker_min);
            if (currentTicker != this.lastUpdate_ticks[ind]) {
                EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                        "====================GET DATA===============" 
                                + f.mainProps.feedDirName + "===============GET DATA====================");
                this.lastUpdate_ticks[ind] = currentTicker;
                this.update_counts[ind]++;
                this.lastTry_secs[ind] = this.current_sysSecs;

                if (this.storeThreads[ind] == null || !this.storeThreads[ind].isAlive()) {
                    EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                            "\t launching new extraction thread for " + f.mainProps.feedDirName  + " -/- " + f.regname);
                    EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                            "\t log will printed out to: " + f.storeSout_fnam);
                    Runnable runnable = new StoreRun(f);
                    this.storeThreads[ind] = new Thread(runnable);
                    this.storeThreads[ind].start();
                    this.threadIDS[ind] = this.storeThreads[ind].getId() + "";
                } else if (this.storeThreads[ind].isAlive() && f.interrupt) {
                    //this.lastUpdate_ticks[ind]--;//remove one tick so that a retry will occur
                    try {
                        EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                                "\t INTERRUPTING extraction thread for " + f.mainProps.feedDirName  + " -/- " + f.regname);
                        //is the thread name the same?
                        EnfuserLogger.log(Level.INFO,RegionalMiner.class,
                                "Thread ID comparison: " + this.threadIDS[ind] + " vs " + this.storeThreads[ind].getId() + "");
                        this.storeThreads[ind].interrupt();
                        this.storeThreads[ind] = null;
                        f.statusString = "INTERRUPTED";
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }//if fetch data

        }
    }

   public final static String OFFLINE_TAG= " offline,";
   
   /**
    * Describe the state of the RegionalMiner with status lines, one for
    * each feed.
    * @return and array of feed status lines. After feed status lines, there can
    * be warnings if issues have been identified.
    */
   public ArrayList<String> getStatusStrings() {
        ArrayList<String> arr = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();
        Dtime now = Dtime.getSystemDate();
        arr.add( "===MINER " + ops.getRegionName() + " ======\n");
        int i =-1;
        for (Feed f:feeds) {
            i++;
            if (!f.stores) continue;
            
            String line ="";
            line += tab(f.mainProps.feedDirName  + ", ");//+ this.feeds[i];

            if (f.global) {
                line += tab(" global,");
            } else {
                line += tab(" local,");
            }

            long lastTryAgo = this.current_sysSecs - this.lastTry_secs[i];
            long nextUp_min = (int) (f.mainProps.ticker_min - lastTryAgo / 60);
            line += tab(" nxt " + nextUp_min + "min,") + tab(" lastAdd=" 
                    + f.lastStoreCount + ", ",20) + tab(f.statusString+",");
            if (f.lastSuccessStoreDt != null) {
                line += tab(" Sucss=" + (this.current_sysSecs 
                        - f.lastSuccessStoreDt.systemSeconds()) / 60 + "min ago, ",20) 
                        + tab(f.lastSuccessStoreCount + " adds");
            }
            String warn = getFeedWarning(f,now);
            if (warn !=null) warnings.add(warn);
            arr.add(line);
            
        }//for feeds
        //add warnings last.
        for (String warns:warnings) {
            arr.add("[W]: "+warns);
        }
        return arr;

    }

    /**
     * Establish a connection and download a file from the source in case the
     * file does not already exist (non-zero).
     *
     * @param localFileName file name for the download (full path)
     * @param simple if true connection is established in a simple way that
     * works for most datasources
     * @param certBypass disable SSL auth, which maybe required for Vaisala
     * servers etc, which do not have proper certificates.
     * @param address the url-address.
     * @param prout printstream for console output (or for a log file)
     * @param usrPass user, password for simpleAuthentication for https. can be
     * null if not needed.
     * @param sizeEstimateMegs an estimate for file size in mega bytes
     * @param jbar ja progress bar to show the download progress, can be null
     * @param forceRefresh set true to bypass the check for already existing
     * data with the exact same name.
     * @return boolean value that signals if the download was successful or the
     * file existed already.
     * @throws Exception something went wrong!
     */
    public static boolean downloadFromURL_static(String localFileName, boolean simple,
            boolean certBypass, String address, PrintStream prout, String[] usrPass,
            Float sizeEstimateMegs, Object jbar, boolean forceRefresh) throws Exception {

        long numWritten = 0;
        GraphicsMod g = null;
        
        if (jbar != null) {
            g =new GraphicsMod();
            g.setJbarValue(jbar, 0, false);
        }
        OutputStream out = null;
        URLConnection conn;
        InputStream in = null;

        prout.println("=============FILE DOWNLOAD =================\n"
                + address + "\n========================================================\n" 
                        + "TO: " + localFileName);

        boolean alreadyContains = checkContains_NonZero(localFileName, "", prout);
        if (alreadyContains && forceRefresh) {
            alreadyContains = false;

        }
        if (alreadyContains) {
            prout.println("folder already contains this dataset, aborting.");
            return true;
        }

        try {
            URL url = new URL(address);
            out = new BufferedOutputStream(new FileOutputStream(localFileName));
            if (simple) {
                conn = url.openConnection();
            } else {//auth, certOverride can occurr

                //auth
                if (usrPass != null ) {//set basic authentication
                    Authenticator.setDefault(new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(usrPass[0], usrPass[1].toCharArray());
                        }
                    });
                }

                conn = (HttpURLConnection) url.openConnection();

                if (certBypass && address.contains("https")) {
                     prout.println("Bypass certificate authentication once for " + address);
                     setAcceptAllVerifier((HttpsURLConnection) conn);   
                }

            }//if not simple

            in = conn.getInputStream();

            byte[] buffer = new byte[1024];

            int numRead;

            long lastMegs = 0;
            if (g != null) {
                g.setJbarString(jbar,"Downloading data", true);
                //jbar.setStringPainted(true);
            }
            while ((numRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, numRead);
                numWritten += numRead;
                //check size
                long megs = numWritten / 1000000;
                if (megs != lastMegs) {
                    lastMegs = megs;
                    String percs = "";
                    if (sizeEstimateMegs != null) {
                        float perc = 100f * lastMegs / sizeEstimateMegs;
                        if (perc > 100) {
                            perc = 100;
                        }
                        percs = "(" + (int) perc + "%)";
                        if (g != null) {
                           g.setJbarValue(jbar, (int)perc, false);
                           g.setJbarString(jbar,"Downloading data (" + (int) perc + "% complete)",false);
                        }
                    }
                    prout.println("\t downloaded: " + lastMegs + " Mb " + percs);
                }
            }
            if (g != null) {
                g.setJbarString(jbar,"",false);
            }
            prout.println(localFileName + "\t" + numWritten);
        } catch (Exception exe) {
            exe.printStackTrace(prout);
            
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException ioe) {
                prout.println("IO-Error! could not close stream "
                        + "for local file: "+localFileName);
            }
        }

        if (numWritten > 100) {
            return true;
        } else {
            return false;
        }

    }
    
  
  public static String mineFromURL_legacy(boolean certBypass, String address,
        PrintStream out, String[] usrPass) throws Exception {
     


if (usrPass!=null && address.contains("https:")) {//set basic authentication
        Authenticator.setDefault (new Authenticator() {
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication (usrPass[0], usrPass[1].toCharArray());
    }
}); 
}

URL server = new URL(address);
out.println("Opening connection to "+address);
HttpURLConnection connection = (HttpURLConnection)server.openConnection();

if (certBypass && address.contains("https")) {
          out.println("Bypass certificate authentication once for "+ address);
          setAcceptAllVerifier((HttpsURLConnection)connection);
}

connection.connect();
InputStream aa = connection.getInputStream();
BufferedReader in = new BufferedReader(new InputStreamReader(aa));

String content;

        char[] buf = new char[1024];
                    StringBuilder sb = new StringBuilder();
                    int count = 0;
                    while( -1 < (count = in.read(buf)) ) {
                        sb.append(buf, 0, count);
                    }

                    content = sb.toString();

in.close();
return content;     
} 
 

    /**
     * This method creates a connection to the site specified in the url-address.
     * Then the content of that site is formed into a single String and returned.
     * 
     * @param certBypass if true, then the connection will be allowed to an seemingly
     * insecure server. Many legit data providers (NM10) have this problem.
     * @param address the url address. This can be either http or https -based.
     * @param out Stream for printouts (System.out or a file stream will do)
     * @param usrPass if not null, then a [user,password] authentication can
     * be provided with this for https-connections.
     * @param hideAPI if non-null, then this String will be hidden in feedback 
     * streams, which can be used for API-keys.
     * @param reqProps if non-null, then connection.setRequestProperty([0], [1])
     * will be called before creating the connection.
     * @return the content String
     * @throws Exception 
     */
    public static String mineFromURL_static(boolean certBypass, String address,
            PrintStream out, String[] usrPass, String hideAPI,
            ArrayList<String[]> reqProps) throws Exception {

        if (usrPass != null) {//set basic authentication
            Authenticator.setDefault(new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(usrPass[0], usrPass[1].toCharArray());
                }
            });
        }
        String printurl = address;
        if (hideAPI!=null) {
            printurl = printurl.replace(hideAPI, "HIDDEN");
        }
        URL server = new URL(address);
        out.println("Opening connection to " + printurl);
        HttpURLConnection connection = (HttpURLConnection) server.openConnection();

        if (certBypass && address.contains("https")) {
            try {
                out.println("Bypass certificate authentication.");
                setAcceptAllVerifier((HttpsURLConnection) connection);
            } catch (NoSuchAlgorithmException ex) {
                ex.printStackTrace(out);
            } catch (KeyManagementException ex) {
                ex.printStackTrace();
            }
        }
        connection.setConnectTimeout(10 * 1000);
        if (reqProps!=null) {
            for (String[] ss:reqProps) {
                connection.setRequestProperty(ss[0], ss[1]);
            }
        }
        connection.connect();
        
        // Check if we get a gzipped stream and set the InputStream accordingly
        PushbackInputStream aa = new PushbackInputStream(connection.getInputStream(), 2);
        byte[] magic = new byte[2];
        aa.read(magic);
        aa.unread(magic);
        BufferedReader in;
        if ((magic[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (magic[1] == 
                (byte) (GZIPInputStream.GZIP_MAGIC >> 8))) {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(aa)));
        } else {
            in = new BufferedReader(new InputStreamReader(aa));
        }
        String content;

        char[] buf = new char[1024];
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (-1 < (count = in.read(buf))) {
            sb.append(buf, 0, count);
        }

        content = sb.toString();


        in.close();
        return content;
    }
    
  /**
   * Create directory strutures that the miner needs if currently
   * missing.
   * @param ops FusionOptions that carry directory path information. 
   */  
  public static void mkMissingDirsFiles_miner(FusionOptions ops) {

        //first check feed directories
        File f;
        ERCFarguments rargs = ops.getArguments();
        String mdir = rargs.getMinerDir();
        String[] cdirs = {
            mdir,
            mdir+ ERCFarguments.GLOBAL_DIRNAME + Z,
            mdir+ rargs.regName + Z
        };

        for (String cdir : cdirs) {

            f = new File(cdir);
            if (!f.exists()) {
                EnfuserLogger.log(Level.INFO,RegionalMiner.class," Creating non-existing directory: "
                        + f.getAbsolutePath());
                f.mkdirs();
            }
        }

        ArrayList<Feed> feeds = FeedFetch.getFeeds(ops, FeedFetch.MUST_ACT);

        for (Feed feed : feeds) {
            if (feed == null) {
                continue;
            }
            String feedDir = feed.primaryMinerDir;
            f = new File(feedDir);
            if (!f.exists()) {
                EnfuserLogger.log(Level.INFO,RegionalMiner.class,"\t Creating non-existing directory: "
                        + f.getAbsolutePath());
                f.mkdirs();
            }
        }//for ind


    }   
    
    /**
     * Create a warning message in case there are problems in Feed data extraction.
     * These issues can relate to missing API-keys and repeated unsuccessful data
     * extractions.
     * @param f the feed
     * @return message. Return null if no warnings are issued.
     */
    private String getFeedWarning(Feed f,Dtime now) {
      
        //check if credentials are needed but not provided
        if (f.statusString.equals(StoreRun.STATUS_MISSING_APIK)) {
             GlobOptions g = GlobOptions.get();
            //assume that user-password combination is missing
            String usr_pwd = g.feedUserKey(f) +", " + g.feedPasswordKey(f);
            if (f.mainProps.apikRequirement.equals(FeedConstructor.CREDENTALS_APIK)) {
                //a single APIkey is missing.
                usr_pwd = f.mainProps.argTag +"(or "+f.mainProps.argTag+"_APIK)";
                
            }
            return f.mainProps.feedDirName +" cannot extract data due to missing "
                    + "credentials. This feed requires content for keys: " + usr_pwd 
                    +" at crypts.dat. Consider running once: '-runmode=crypt_add -name=x -value=y' "
                    + "with proper content for 'x' (the key) and 'y' (the value).";
        }
         
        //check if several attempts hav been made without success
        if (f.storeTicks >4 && f.lastSuccessStoreDt == null) {
             return f.mainProps.feedDirName +" has attempted extraction for " +f.storeTicks
                    + " times but seems to unsuccessful.";
        }
        
        //check if the feed has been successful but at some point failed to be successful
        int storeFreq_mins = f.mainProps.ticker_min;
        if (f.lastSuccessStoreDt != null) {
            long minsFromLastSuccess = now.systemSeconds()/60 - f.lastSuccessStoreDt.systemSeconds()/60;
            if (minsFromLastSuccess > 4*storeFreq_mins ) {
                return f.mainProps.feedDirName +" has not been successful recently."
                        + " Last successful exctraction was " +f.lastSuccessStoreDt.getStringDate()
                    + ", " + minsFromLastSuccess +" minutes ago.";
            }
        }
        
        return null;
    }
    
    private static SSLSocketFactory sslSocketFactory = null;


    /**
     * Overrides the SSL TrustManager and HostnameVerifier to allow all certs
     * and hostnames.WARNING: This should only be used for testing, or in a
     * "safe" (i.e. firewalled) environment.
     *
     * @param connection the connection
     * @throws NoSuchAlgorithmException error
     * @throws KeyManagementException error
     */
    public static void setAcceptAllVerifier(HttpsURLConnection connection)
            throws NoSuchAlgorithmException, KeyManagementException {

        // Create the socket factory.
        // Reusing the same socket factory allows sockets to be
        // reused, supporting persistent connections.
        if (null == sslSocketFactory) {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, ALL_TRUSTING_TRUST_MANAGER, new java.security.SecureRandom());
            sslSocketFactory = sc.getSocketFactory();
        }

        connection.setSSLSocketFactory(sslSocketFactory);

        // Since we may be using a cert with a different name, we need to ignore
        // the hostname as well.
        connection.setHostnameVerifier(ALL_TRUSTING_HOSTNAME_VERIFIER);
    }

    private static final TrustManager[] ALL_TRUSTING_TRUST_MANAGER = new TrustManager[]{
        new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
    };

    private static final HostnameVerifier ALL_TRUSTING_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };
    
    

}
