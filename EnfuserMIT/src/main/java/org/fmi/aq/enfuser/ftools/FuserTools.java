/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import ucar.nc2.Attribute;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;

/**
 *
 * @author johanssl
 */
public class FuserTools {

    public static final String EMPTY_STRING = "";

    /**
     * Checks data time range validity with respect a timespan of interest.This
 is a brute-force check, iterating one hour at a time. If any hour matched
 within the span this returns true.
     *
     * @param start start time for domain of interest
     * @param end end time for domain of interest
     * @param sysHours_data [start,end] in terms of data date system hours.
     * @return true if an overlap of the time spans was observed, signaling that
     * the dataset is time-wise applicable. Otherwise, returns false.
     */
    public static boolean evaluateSpan(Dtime start, Dtime end, int[] sysHours_data) {

        for (int dh = sysHours_data[0]; dh <= sysHours_data[1]; dh++) {
            if (dh >= start.systemHours() && dh <= end.systemHours()) {
                return true;
            }
        }

        return false;
    }
    /**
     * A simple test method to check if the given h-w index would
     * cause on OutOfBoundsException in the pbject grid.
     * @param dat the grid
     * @param h h index
     * @param w w index
     * @return true if OutOfBounds.
     */
    public static boolean ObjectGridOobs(Object[][] dat, int h, int w) {
        if (h<0 || h > dat.length-1) return true;
        if (w<0 || w > dat[0].length-1) return true;
        return false;
    }

    public static String getUrlContent_old(String address) throws IOException {

        URL hirlam = new URL(address);
        EnfuserLogger.log(Level.FINER,FuserTools.class,"Opening connection to " + address);
        URLConnection yc = hirlam.openConnection();

        InputStream aa = yc.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(aa));

        String inputLine;
        String currContent = "";

        int counter = 0;
        while ((inputLine = in.readLine()) != null) {
//EnfuserLogger.log(Level.FINER,FuserTools.class,inputLine); 
            counter++;
            currContent += inputLine;
        }

        in.close();

        EnfuserLogger.log(Level.FINER,FuserTools.class,
                "Read successfully" + counter + " lines from url.\n\n" + currContent);

        return currContent;
    }

    public static String correctDir(String dir) {

        //could it be that the wrong file separator is being used?
        String original = dir + "";
        String other = "/";
        if (Z.equals(other)) {
            other = "\\";
        }

        if (dir.contains(other + "")) {
            dir = dir.replace(other + "", Z);
        }

        if (!dir.startsWith(Z + Z)) {// it is safe to remove double file separators
            dir = dir.replace(Z + Z, Z);
        }

        String finalDir;
        if (dir.endsWith(Z)) {
            finalDir = dir;//all ok
        } else {
            finalDir = dir + Z;
        }
        if (!original.equals(finalDir)) {
            EnfuserLogger.log(Level.FINER,FuserTools.class,
                    "correctDir: a correction has been made: " + original + " => " + finalDir);
        }
        return finalDir;
    }
    
    /**
     * Recursive delete method to clear a directory with sub-directories.
     * Deletion will occur only for the given file types (for safety) 
     * @param directoryToBeDeleted
     * @param delTypes
     * @return 
     */
    public static boolean deleteDirectoryContents(File directoryToBeDeleted, String[] delTypes) {
    File[] allContents = directoryToBeDeleted.listFiles();
    if (allContents != null) {
        for (File file : allContents) {
           deleteDirectoryContents(file, delTypes);
        }
    }
    
    boolean ok = false;
    for (String s:delTypes) {
        if (directoryToBeDeleted.getName().endsWith(s)) ok = true;
    }

    if (directoryToBeDeleted.isDirectory())ok=true;
    
    if (ok) {
      EnfuserLogger.log(Level.FINE, FuserTools.class,
              "Deleting: "+ directoryToBeDeleted.getAbsolutePath());
      return directoryToBeDeleted.delete(); 
    } else {
        return false;
    }
  
}
    
/**
 * Check the given directory for files and return the age of the most recent
 * file of them in hours.
 * @param dir the directory
 * @return the age of the most recent file in hours. If there are no files
 * returns null.
 */    
public static Integer getMostRecentFileAge(String dir) {

     int smallestH = Integer.MAX_VALUE;
        try {
            long secsNow = System.currentTimeMillis() / 1000;

               File f = new File(dir);
                File[] ff = f.listFiles();
                if (ff == null) {
                    return null;
                }

                    for (File file : ff) {
                        long secsFile = file.lastModified() / 1000;
                        //check maturity
                        long hours = (secsNow - secsFile) / 3600;

                        if (hours < smallestH) {
                          smallestH = (int)hours;
                        } 

                    }//for files in dir    
        } catch (Exception e) {
             EnfuserLogger.log(e,Level.WARNING, FileCleaner.class, 
                    "File age assessment process encountered an Exception for "+dir);
        }
        return smallestH; 
}    
    

    public static File findFileThatContains(String rootdir, String[] contains) {
        File f = null;
        File root = new File(rootdir);
        int k = 0;
        EnfuserLogger.log(Level.FINER,FuserTools.class,
                            "Finding files with '"+contains[0] +"' from all subdirectories of "+ rootdir);
        try {
            boolean recursive = true;

            Collection files = FileUtils.listFiles(root, null, recursive);

            for (Iterator iterator = files.iterator(); iterator.hasNext();) {
                File file = (File) iterator.next();
                boolean containsAll = true;
                for (String test : contains) {
                    if (!file.getName().contains(test)) {
                        containsAll = false;
                    }
                }
                k++;
                if (containsAll) {
                    EnfuserLogger.log(Level.FINER,FuserTools.class,
                            "Found the file matching description, k = " + k);
                    return file;
                }
            }
        } catch (Exception e) {
           EnfuserLogger.log(e,Level.SEVERE,FuserTools.class,
                   "File search encountered an error from "+ rootdir);
        }
        EnfuserLogger.log(Level.FINER,FuserTools.class,"Searched " + k + " files without match.");
        return f;
    }

    public static void clearDirContent(String dir) {
        EnfuserLogger.log(Level.FINER,FuserTools.class,"Clearing content of " + dir);
        File f = new File(dir);
        File[] files = f.listFiles();
        EnfuserLogger.log(Level.FINER,FuserTools.class,files.length + " files inside.");

        for (File ff : files) {
            if (!ff.isDirectory()) {
                ff.delete();
            } else if (ff.getName().contains("QC_locs")) {
                ff.delete();
            }
        }

    }

    public static float[][] transform_byteFloat(byte[][] dat) {
        float[][] ndat = new float[dat.length][dat[0].length];

        for (int h = 0; h < dat.length; h++) {
            for (int w = 0; w < dat[0].length; w++) {
                ndat[h][w] = (float) dat[h][w];
            }
        }
        return ndat;
    }

    private static int getFinnishTermicSeason(Dtime dt) {
        int month = dt.month_011;
        if (month == Calendar.JANUARY || month == Calendar.FEBRUARY || month == Calendar.DECEMBER) {
            return 0; //WINTER
        } else if (month == Calendar.MARCH || month == Calendar.APRIL || month == Calendar.MAY) {
            return 1; //SPRING 
        } else if (month == Calendar.JUNE || month == Calendar.JULY || month == Calendar.AUGUST) {
            return 2; //SUMMER {        
        } else {
            return 3;
            //AUTUMN
        }

    }

    public static String getTermicSeasonAverages(ArrayList<Observation> obs) {
        float[] aves = new float[7];
        float[] n = new float[7];

        aves[5] = obs.get(0).lat;
        aves[6] = obs.get(0).lon;

        for (Observation o : obs) {

            int season = getFinnishTermicSeason(o.dt);

            aves[0] += o.value;
            aves[season + 1] += o.value;
            n[season + 1]++;
            n[0]++;
        }

        for (int i = 0; i < 5; i++) {
            aves[i] /= n[i];
        }

        String s = "";
        for (int i = 0; i < aves.length; i++) {
            s += aves[i] + ";";
        }
        return s;
    }


    public static double getAverage(ArrayList<Observation> obs) {
        double ave = 0;

        for (Observation o : obs) {
            ave += o.value;
        }
        ave /= obs.size();
        return ave;
    }

    public static float editPrecisionF(double value, int precision) {

        if (precision >= 0) {
            long prec = (long) Math.pow(10, precision);
            long temp = (long) (prec * value);
            float result = (float) temp / prec;
            return result;
        } else {
            // reduction in precision, e.g. 12345 => 12000
            int divisor = (int) Math.pow(10, Math.abs(precision));
            int temp = (int) (value / divisor);
            float result = temp * divisor;
            return result;

        }

    }

    public static void copyfile(String srFile, String dtFile) 
            throws FileNotFoundException, IOException {
      
            File f1 = new File(srFile);
            File f2 = new File(dtFile);
            InputStream in = new FileInputStream(f1);
            OutputStream out = new FileOutputStream(f2);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            EnfuserLogger.log(Level.FINER,FuserTools.class,"File copied.");
       
    }


    public static void deleteFile(String fileName) {
        // A File object to represent the filename
        boolean doIt = true;
        File f = new File(fileName);

        // Make sure the file or directory exists and isn't write protected
        if (!f.exists()) {
            doIt = false;
            EnfuserLogger.log(Level.WARNING,FuserTools.class,
                    "Delete: no such file or directory: " + fileName);
        }

        if (!f.canWrite()) {
            doIt = false;
            EnfuserLogger.log(Level.WARNING,FuserTools.class,
                    "Delete: write protected: " + fileName);
        }

        // Attempt to delete it
        boolean success = false;
        if (doIt) {
            success = f.delete();
        }

        if (!success) {
            throw new IllegalArgumentException("Delete: deletion failed");
        }
    }

    public static final void unzip(String file, String rootdir) {
        Enumeration entries;
        ZipFile zipFile;

        try {
            zipFile = new ZipFile(file);
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
// Assume directories are stored parents first then children.
                    EnfuserLogger.log(Level.FINE,FuserTools.class,"Extracting directory: " + entry.getName());
// This is not robust, just for demonstration purposes.
                    (new File(entry.getName())).mkdir();
                    continue;
                }
                EnfuserLogger.log(Level.FINE,FuserTools.class,"Extracting file: " + entry.getName());

                String outFile = rootdir + entry.getName();
                OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
                InputStream in = zipFile.getInputStream(entry);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                in.close();
                out.close();

            }
            zipFile.close();
        } catch (IOException ioe) {
            EnfuserLogger.log(Level.WARNING,FuserTools.class,
                    "File unzip unsuccessful: "+ file +" => "+ rootdir);
        }
    }

    /**
     * Returns a FilenameFilter which accepts files with the given extension.
     *
     * @param extension The file extension (e.g. "csv"). Not case sensitive.
     * @return FilenameFilter accepting files with the extension.
     */
    public static FilenameFilter getFileExtensionFilter(String extension) {
        return new FilenameFilter() {
            public boolean accept(File file, String name) {
                return name.toLowerCase().endsWith("." + extension.replace(".", "")); // some tolerance for usage
            }
        };
    }
    
    
      public static String tab(String s, int len) {

        while (s.length() < len) {
            s += " ";
        }
        return s;
    }

    public static String tab(String s) {

        while (s.length() < 15) {
            s += " ";
        }
        return s;
    }
    
    // parent folders of dest must exist before calling this function
    public static void copyTo(File src, File dest) throws IOException {

            FileInputStream fileInputStream = new FileInputStream(src);
            FileOutputStream fileOutputStream = new FileOutputStream(dest);

            int bufferSize;
            byte[] bufffer = new byte[512];
            while ((bufferSize = fileInputStream.read(bufffer)) > 0) {
                fileOutputStream.write(bufffer, 0, bufferSize);
            }
            fileInputStream.close();
            fileOutputStream.close();

    }

    public static ArrayList<Attribute> getNetCDF_attributes(Dtime lastObservationTime) {
        ArrayList<Attribute> netAttr = new ArrayList<>();
        Dtime sysDt = Dtime.getSystemDate_utc(false, Dtime.STAMP_NOZONE);
        netAttr.add(new Attribute("Conventions", "CF-1.0"));
        netAttr.add(new Attribute("institution", "Finnish Meteorological Institute"));
        netAttr.add(new Attribute("creator", "Lasse Johansson, email lasse.johansson@fmi.fi"));
        netAttr.add(new Attribute("history", "model version: " + DataCore.VERSION_INFO + ", file produced " + sysDt.getStringDate(Dtime.STAMP_NOZONE) + "Z"));
        if (lastObservationTime != null) {
            netAttr.add(new Attribute("info", "last observed datapoint: " + lastObservationTime.getStringDate(Dtime.STAMP_NOZONE) + "Z"));
        }
        //netAttr.add(new Attribute("references", "Lasse Johansson, Victor Epitropou, Kostas Karatzas, Ari Karppinen, Leo Wanner," + " Stefanos Vrochidis, Anastasios Bassoukos, Jaakko Kukkonen and Ioannis Kompatsiaris, Fusion of meteorological and air quality data" + " extracted from the web for personalized environmental information services, Environmental Modelling & Software," + "Volume 64, February 2015, Pages 143\u2013155, Elsevier, 2014."));
        netAttr.add(new Attribute("title", "Modelled pollutant concentrations and supplemental data according to FMI-ENFUSER"));
        return netAttr;
    }

    
     public static ArrayList<Integer> arrayConvert(ArrayList<Byte> arr) {
        ArrayList<Integer> ar2 = new ArrayList<>();
        for (byte b : arr) {
            ar2.add((int) b);
        }
        return ar2;
    }

    public static ArrayList<Byte> arrayConvert2(ArrayList<Integer> arr) {
        ArrayList<Byte> ar2 = new ArrayList<>();
        for (int b : arr) {
            ar2.add((byte) b);
        }
        return ar2;
    }
    
    /**
     * Wait for the given amount of seconds and return a manually specified system
     * input line.
     * This method can be used to query user input in cases where a user is present
     * and manual input is required to proceed further.
     * @param waitSeconds amount of time in seconds allowed for input typing.
     * @return manually types input String. Returns null if none was given.
     */
    public static String readSystemInput(int waitSeconds) {
        String resp =null;
        int x = waitSeconds; // wait 20 seconds at most
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            long startTime = System.currentTimeMillis();
            try {
                while ((System.currentTimeMillis() - startTime) < x * 1000
                        && !in.ready()) {
                }

                if (in.ready()) {
                    resp = in.readLine();
                    EnfuserLogger.log(Level.INFO,FuserTools.class,"Input given: " + resp);
                } else {
                    //rule 3: if manual and no input has been defined, assume 'flex'
                    EnfuserLogger.log(Level.INFO,FuserTools.class,"Input was not given.");
                    return null;
                }

            } catch (IOException ex) {
                 EnfuserLogger.log(ex,Level.WARNING,FuserTools.class,"Input read failure!");
            }

     return resp;   
    }
    
    
    public static Dtime[] fullpreviousMonth(Dtime dt) {
        Dtime end = dt.clone();
        end.addSeconds(-1*(end.sec + end.min*60 + end.hour*3600 + (end.day-1)*24*3600));
        end.addSeconds(-3600);
        System.out.println("last month's last hour (END): "
                +end.getStringDate_noTS());
        
        Dtime start = end.clone();
        start.addSeconds(-1*(start.sec + start.min*60 + start.hour*3600 + (start.day-1)*24*3600));
        System.out.println("last month's first hour (START): "
                + start.getStringDate_noTS());
        return new Dtime[]{start,end};
    }
    
    private final static String CONCAT_NAME ="concat.csv";
    public static void concatenateCSVFiles(String dir, boolean headerRem, String[] replacements) {
        ArrayList<String> lines = new ArrayList<>();
        File f = new File(dir);
        int k =0;
        for (File test:f.listFiles()) {
            String name = test.getName();
            if (!name.endsWith(".csv")) continue;
            if (name.equals(CONCAT_NAME)) continue;
            k++;
            System.out.println("Added as "+ k+": "+ name);
            ArrayList<String> arr = FileOps.readStringArrayFromFile(test);
            if (!lines.isEmpty() && headerRem) {
                arr.remove(0);//remove header, it has been added once.
            }
            
            if (replacements!=null) {
                ArrayList<String> temp = new ArrayList<>();
                for (String line:arr) {
                    
                for (int i =0;i<replacements.length;i+=2) {
                    String target = replacements[i];
                    String rep = replacements[i+1];
                    line = line.replace(target, rep);
                        
                    }//for mods
                temp.add(line);
                }//for lines
                arr = temp;
            }//if mods
            
            lines.addAll(arr);
        }
        
        FileOps.printOutALtoFile2(dir, lines, CONCAT_NAME, false);
        System.out.println("Concat Done.");
    }
    
    public static void main(String[] args) {
       concatenateCSVFiles("E:\\Dropbox\\UTO_ICOS_wind_2020\\", true, new String[]{" ","T",",",";"});  
    }

    public static void mkMissingDirsFiles(FusionOptions ops) {
        //first check feed directories
        File f;
        ERCFarguments rargs = ops.getArguments();
        String mdir = rargs.getMinerDir();
        String[] cdirs = {GlobOptions.get().enfuserArchiveDir(null), //EnfuserArchive
        rargs.getMinerDir(true), //global
        rargs.getMinerDir(false)};
        for (String cdir : cdirs) {
            f = new File(cdir);
            if (!f.exists()) {
                EnfuserLogger.log(Level.FINER, " Creating non-existing directory: " + f.getAbsolutePath());
                f.mkdirs();
            }
        }
        ArrayList<Feed> feeds = FeedFetch.getFeeds(ops, FeedFetch.MUST_ACT);
        for (Feed feed : feeds) {
            if (feed == null || feed.primaryMinerDir == null) {
                continue;
            }
            String feedDir = feed.primaryMinerDir;
            f = new File(feedDir);
            if (!f.exists()) {
                EnfuserLogger.log(Level.FINER, "\t Creating non-existing directory: " + f.getAbsolutePath());
                f.mkdirs();
            }
        } //for ind
        String root = GlobOptions.get().getRootDir();
        String logs = root + "logs" + Z;
        f = new File(logs);
        if (!f.exists()) {
            EnfuserLogger.log(Level.FINER,  "\t Creating non-existing directory: " + f.getAbsolutePath());
            f.mkdirs();
        }
        //results dirs ==============
        String res = root + "Results" + Z;
        f = new File(res);
        if (!f.exists()) {
            EnfuserLogger.log(Level.FINER, "\t Creating non-existing directory: " + f.getAbsolutePath());
            f.mkdirs();
        }
        String resCT = root + "Results" + Z + "COMMON_TEMP" + Z;
        f = new File(resCT);
        if (!f.exists()) {
            EnfuserLogger.log(Level.FINER, "\t Creating non-existing directory: " + f.getAbsolutePath());
            f.mkdirs();
        }
        String resSC = root + "Results" + Z + "statCrunch" + Z;
        f = new File(resSC);
        if (!f.exists()) {
            EnfuserLogger.log(Level.FINER, "\t Creating non-existing directory: " + f.getAbsolutePath());
            f.mkdirs();
        }
    }

    public static float sum(float[] ex) {
         if (ex==null) return 0;
        float sum =0;
        for (float f:ex) {
            sum+=f;
        }
        return sum;
    }
}
