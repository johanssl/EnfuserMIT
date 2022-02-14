/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.fmi.aq.essentials.netCdf.NetcdfHandle;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import static org.fmi.aq.essentials.plotterlib.Visualization.FileOps.readStringArrayFromFile;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class is for loading a zipped archive file (e.g., named as
 * allPollutants_YYYY-MM-DDTHH.zip) These archive files have been created out of
 * an AreaFusion instance and one of the use cases for this class is the
 * possibility to convert it to an AreaFusion. This makes it possible to dump
 * archives to file during a modelling task and read them up once the large
 * netCDF files and animations need to be generated.
 *
 * Several visualization tools also utilize this class as it can be provide
 * modelling results (and supporting meteorology and observations). Also,
 * emission-source specific datasets are available.
 *
 * The new Sensor Location Assessment tool (V2) uses this archive for loading up
 * a large amount of modelled data for the area of interest.
 *
 * For faster loading this class supports file filters: e.g., if only
 * meteorology is of interest, then no other larger datasets needs to be read.
 *
 * @author johanssl
 */
public class ArchiveContent {

    public final static String[] GRID_TYPES = {
        "met_", //0
        "regional",//1
        "components",//2
        "allPollutants",//3
        "sentinel",//4
        "metEx",//5
        "tropomi_"//6     
    };

    public final static int HASHI_MET = 0;
    public final static int HASHI_REGIN = 1;
    public final static int HASHI_COMP = 2;
    public final static int HASHI_ALLPOLS = 3;
    public final static int HASHI_SENT = 4;
    public final static int HASHI_METEX = 5;
    public final static int HASHI_TROPOMI = 6;

    public final static int[] READ_ALL_PRODS = {0, 1, 2, 3, 4, 5, 6};
    public final static int[] READ_COMPS_ONLY = {2};
    public final static int[] READ_AP_ONLY = {3};
    public final static int[] READ_FOR_ANIM = {0, 1, 3};

    public final String arch_path;//points to the directory the data was loaded from.
    public final String filename;

    public Dtime dt;
    //public String loc;//e.g., Tampere
    //public String region;//e.g., Finland

    public HashMap<String, GeoGrid>[] hashArr = new HashMap[GRID_TYPES.length];
    public HashMap<String, GeoGrid> allHash = new HashMap<>();

    public ArrayList<String> evalDat = new ArrayList<>();
    public String[] varMustContain = null;

    public HashMap<Integer, ArrayList<Observation>> obsHash = new HashMap<>();
    public final FusionOptions ops;
    public static String[] getTypesThatMustExist_nc() {
        return new String[]{
        GRID_TYPES[HASHI_MET],
        GRID_TYPES[HASHI_REGIN], 
        GRID_TYPES[HASHI_COMP],
        GRID_TYPES[HASHI_ALLPOLS],     
        };
    }
    
    /**
     * Init ArchiveContent to be read from a file
     *
     * @param dir directory for data
     * @param filename filename (without parent)
     */
    private ArchiveContent(String dir, String filename, FusionOptions ops) {
        for (int i = 0; i < GRID_TYPES.length; i++) {
            this.hashArr[i] = new HashMap<>();
        }
        this.arch_path = dir;
        this.filename = filename;
        this.ops = ops;
    }

    /**
     * Read a complete ArchiveContent instance from a file.
     *
     * @param filename full filename (with parent directories)
     * @param tempdir directory for temporary files (unzip)
     * @param ops
     * @return and ArchiveContent instance.
     */
    public static ArchiveContent read(String filename, String tempdir, FusionOptions ops) {
        File f = new File(filename);
        String fname = f.getName();
        String dir = f.getAbsolutePath().replace(fname, "");
        EnfuserLogger.log(Level.FINER,ArchiveContent.class,"ArchiveContent.read: " + dir + " ][ " + fname);
        //get Dt
        String date = fname.replace("allPollutants_", "");
        date = date.replace("-30.zip", ":30:00");
        date = date.replace(".zip", ":00:00");
        EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Date => " + date);
        Dtime dt = new Dtime(date);

        return read(READ_ALL_PRODS, dir, fname, dt, tempdir, null, false,ops);
    }

    /**
     * Return an ArrayList holding all Observations
     *
     * @return list of observations
     */
    public ArrayList<Observation> allObservations() {
        ArrayList<Observation> arr = new ArrayList<>();
        for (ArrayList<Observation> obs : this.obsHash.values()) {
            for (Observation o : obs) {
                arr.add(o);
            }
        }
        return arr;
    }
             

    /**
     * Read a customized ArchiveContent instance from a file, which is
     * identified based on time, region and area.
     *
     * @param readVars filters for file types to be read (indices of
     * GRID_TYPES).
     * @param ops options that define the region and area
     * @param dt time
     * @param varMustContain a number of text filters for pollutant types. If
     * not null, then any read grid dataset must contain one of these names
     * (e.g., NO2, PM10)
     * @param filterPolDataOnly set true if the String filter is to be applied
     * to pollutant data only. For example, for NO2 animation that needs
     * meteorological data setting this true makes data loading faster.
     * @param tempdir directory for temporary files (unzip)
     * @return and ArchiveContent instance.
     */
    public static ArchiveContent read(int[] readVars, FusionOptions ops, Dtime dt,
            String tempdir, String[] varMustContain, boolean filterPolDataOnly) {
        String dir = ops.archOutDir(dt);//ops.areaID must be set properly
        String name = "allPollutants_" + dt.getStringDate_YYYY_MM_DDTHH() + ".zip";
        if (dt.min != 0) {
            name = name.replace(".zip", "-" + dt.min + ".zip");
        }
        return read(readVars, dir, name, dt, tempdir, varMustContain, filterPolDataOnly,ops);
    }

    /**
     * Read a customized ArchiveContent instance from a file.
     *
     * @param readVars filters for file types to be read (indices of
     * GRID_TYPES).
     * @param dir parent directory
     * @param fname local filename
     * @param dt time
     * @param varMustContain a number of text filters for pollutant types. If
     * not null, then any read grid dataset must contain one of these names
     * (e.g., NO2, PM10)
     * @param filterPolDataOnly set true if the String filter is to be applied
     * to pollutant data only. For example, for NO2 animation that needs
     * meteorological data setting this true makes data loading faster.
     * @param tempdir directory for temporary files (unzip)
     * @param ops
     * @return and ArchiveContent instance.
     */
    public static ArchiveContent read(int[] readVars, String dir, String fname,
            Dtime dt, String tempdir, String[] varMustContain, boolean filterPolDataOnly,
            FusionOptions ops) {
        if (readVars == null) {
            readVars = READ_ALL_PRODS;
        }
        //TODO 30 min data? The filename does not consider minutes.
        EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Searching data for " + dir + fname);
        
        File f = new File(dir + fname);
        if (!f.exists()) {
            return null;//nothing to read
        }
        ArchiveContent ac = new ArchiveContent(dir, fname,ops);
        ac.varMustContain = varMustContain;

        //form a list of files that are going to be unzipped, which can save a lot of I-O time.
        ArrayList<String> unzipThese = new ArrayList<>();
        unzipThese.add("eval_");
        for (int j : readVars) {
            unzipThese.add(GRID_TYPES[j]);
        }
        //====================
        //ac.loc = ops.areaID+"";
        //ac.region = ops.getArguments().regName+"";
        ac.dt = dt.clone();

        ArrayList<String> arr = UnZip(dir + fname, tempdir, unzipThese);
        NetcdfHandle ncHandle = null;

        for (String unzipped : arr) {

            if (unzipped.contains("eval_")) {// a text-file
                ac.evalDat = readStringArrayFromFile(tempdir + unzipped);

            } else if (unzipped.contains(".nc")) {

                for (int j : readVars) {
                    String id = GRID_TYPES[j];

                    if (unzipped.contains(id)) {
                        //read data
                        try {

                            ncHandle = new NetcdfHandle(tempdir + unzipped);
                            for (String ncVar : ncHandle.varNames) {
                                if (ncVar.equals("time") || ncVar.equals("latitude") || ncVar.equals("longitude")) {
                                    continue;
                                }

                                if (ac.varMustContain != null && filterPolDataOnly && (j != HASHI_ALLPOLS && j != HASHI_COMP)) {
                                    //there is a filter by name, but this dataset is not 'allPollutants' or 'components', so we read it normally

                                } else if (ac.varMustContain != null) {//this limiter has been set and the String is not contained in the name.
                                    boolean ok = false;

                                    for (String test : ac.varMustContain) {//e.g., cnc_NO2_gas, NO2,
                                        if (ncVar.contains(test)) {
                                            ok = true;
                                        }
                                    }//test each string, one match is needed for read.

                                    if (!ok) {
                                        EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                                                "Skipped variable: " + ncVar + " for /" + id);
                                        continue;
                                    }//SKIP
                                }//if additional limiters

                                ncHandle.loadVariable(ncVar);
                                try {
                                    GeoGrid g = ncHandle.getContentOnGeoGrid_2d();
                                    //TODO the Dtime in this geoGrid is always NULL! not good
                                    if (g.dt == null) {
                                        String bname = ncVar;
                                        int typeI = ops.VARA.getTypeInt(ncVar);
                                        if (typeI >= 0) {
                                            bname =  ops.VARA.VAR_NAME(typeI);
                                            EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                                                    "\t GeoGrid read: switched name from " + ncVar + " to " + bname);
                                            
                                        }
                                        g = new GeoGrid(g.values, ac.dt.clone(), g.gridBounds.clone(bname));
                                    }

                                    
                                    EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                                            "\t Adding GeoGrid " + id + " - " + ncVar + " with dimensions " + g.H + " x " + g.W);
                                    
                                    ac.hashArr[j].put(ncVar, g);
                                    String allHashName = GRID_TYPES[j] + "_" + ncVar;
                                    ac.allHash.put(allHashName, g);
                                } catch (Exception e) {
                                    EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Read fail for /" + id + ", " + ncVar);
                                    e.printStackTrace();
                                }
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }//if type match
                }//for types
            }//if netCDF

            //clean-up
            //EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Deleting "+ tempdir + unzipped);
            if (ncHandle != null) {
                ncHandle.close();
            }
            f = new File(tempdir + unzipped);
            f.delete();

        }//for unzipped files

        //parse measurements  
        ac.parseObservations();

        return ac;
    }
    

    public static ArrayList<ArchiveContent> getComponentSample(String[] nameFilters,
            Dtime start, Dtime end, int hourStep, String tempDir, FusionOptions ops) {
        ArrayList<ArchiveContent> arr = new ArrayList<>();

        Dtime current = start.clone();

        while (current.systemHours() <= end.systemHours()) {

            ArchiveContent ac = read(READ_COMPS_ONLY, ops, current, tempDir, nameFilters, false);
            if (ac != null) {
                arr.add(ac);
            }

            current.addSeconds(hourStep * 3600);
        }
        EnfuserLogger.log(Level.FINER,ArchiveContent.class,"getComponentSample: returning " + arr.size() + " archiveContent packs for a duration of "
                + start.getStringDate() + " -> " + end.getStringDate() + " using a timestep of " + hourStep + "h");
        return arr;
    }

    /**
     * Get a specific GeoGrid data container for specified variable type (q) and
     * category (c)
     *
     * @param c emission category index
     * @param q varable type index
     * @return a GeoGrid instance. Returns null if no such dataset has been
     * loaded from file.
     */
    public GeoGrid getComponent(int c, int q) {
        String name = ops.VARA.VARNAME_CONV(q) + "_" + ops.CATS.CATNAMES[c];
        return this.hashArr[HASHI_COMP].get(name);
    }

    /**
     * Get a specific GeoGrid data container, without emission source category,
     * for specified variable type (q)
     *
     * @param q varable type index
     * @return a GeoGrid instance. Returns null if no such dataset has been
     * loaded from file.
     */
    public GeoGrid getPollutantGrid(int q) {
        String name = ops.VARA.VARNAME_CONV(q);
        return this.hashArr[HASHI_ALLPOLS].get(name);
    }

   
    public GeoGrid getRegionalGrid(int i) {
        String name = ops.VARA.VARNAME_CONV(i);
        return this.hashArr[HASHI_REGIN].get(name);
    }

    public String[] listNcNames() {

        ArrayList<String> arr = new ArrayList<>();

        for (String key : this.allHash.keySet()) {
            arr.add(key);
        }
        Collections.sort(arr);

        String[] nams = new String[arr.size()];
        for (int i = 0; i < nams.length; i++) {
            nams[i] = arr.get(i);
        }
        return nams;
    }

    public GeoGrid getGridByName(String nam) {

        GeoGrid g = this.allHash.get(nam);
        return g;
    }

    private final static int IND_CCS_ID = 0;//in header this is "lat,lon,height", for data this is "ID" string.
    private final static int IND_OVAL = 1;//the value
    private final static int IND_PRED = 2;//model prediction at the point
    private final static int IND_BG = 3;
    private final static int IND_RAWBG = 4;
    private final static int IND_VARPEN = 5;
    private final static int IND_ORIGVAL = 6;//for sensor data the original value may be different than the value (that can have been corrected)

    private void parseObservations() {
        GlobOptions g = GlobOptions.get();
        try {

            for (int i = 0; i < evalDat.size(); i += 2) {//read two lines simulataneously. The first one is a header line. The second one has most of the data that is being read into Observations.

                String[] header = evalDat.get(i).split(";");
                String[] dat = evalDat.get(i + 1).split(";");

                //find the column that has "POINTS". After this String the point data is described
                int firstCol = -1;
                for (int k = 0; k < header.length; k++) {
                    if (header[k].contains("POINTS")) {//found it.
                        firstCol = k + 1;
                        break;
                    }
                }

                //read type and Dtime
                Dtime dtt = new Dtime(dat[0]);
                String varname = dat[1];
                int typeInt = ops.VARA.getTypeInt(varname);

                //start reading data
                for (int K = firstCol; K < header.length; K += 7) {//there are 7 data fields per each observation.
                    try {
                        String[] latlonH = header[K + IND_CCS_ID].split(",");

                        double lat = Double.valueOf(latlonH[0]);
                        double lon = Double.valueOf(latlonH[1]);
                        double h = Double.valueOf(latlonH[2]);
                        String ID = dat[K + IND_CCS_ID];

                        String sval = dat[K + IND_OVAL];
                        if (sval.equals("-") || sval.length() == 0) {
                            continue;
                        }
                        float val = Double.valueOf(sval).floatValue();

                        Observation o = new Observation(typeInt, ID, (float) lat, (float) lon, dtt, val, (float) h, null);

                       
                            int relIndex = 0;
                            float wPOAu=0;
                            float wPOA=0;
                            byte dbq =1;
                            float autoc =1;
                            float wPOAeven =0;
                        try {
                           relIndex= Double.valueOf(dat[K + IND_VARPEN]).intValue();
                           
                        } catch (NumberFormatException e) {

                        }
                             
                         o.setDataFusionOutcome(relIndex,wPOAeven, wPOAu,wPOA,dbq,autoc);     
                        //put to hash
                        ArrayList<Observation> arr = this.obsHash.get(typeInt);
                        if (arr == null) {
                            arr = new ArrayList<>();
                            this.obsHash.put(typeInt, arr);
                        }
                        //EnfuserLogger.log(Level.FINER,ArchiveContent.class,"\t Put an obervation to hash: "+ VAR_NAME(typeInt) + ", "+ dt.getStringDate() + ", value = "+ val);
                        arr.add(o);

                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {

                    }
                }//for points 
            }//for two lines

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static ArrayList<String> UnZip(String zipFile, String toFolder, ArrayList<String> unzipThese) {

        byte[] buffer = new byte[1024];
        ArrayList<String> names = new ArrayList<>();
        try {

            //get the zip file content
            ZipInputStream zis
                    = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {

                String fileName = ze.getName();
                boolean ok = false;
                for (String test : unzipThese) {
                    if (fileName.contains(test)) {
                        ok = true;
                    }
                }
                if (!ok) { 
                   EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                           "Unzip: filename did not contain any listed keywords: " + fileName);
                    
                } else {
                    File newFile = new File(toFolder + fileName);
                    
                    EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                            "file unzip : " + newFile.getAbsoluteFile());
                    
                    //create all non exists folders
                    //else you will hit FileNotFoundException for compressed folder
                    new File(newFile.getParent()).mkdirs();

                    FileOutputStream fos = new FileOutputStream(newFile);

                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }

                    fos.close();
                    names.add(fileName);

                }//if name matches

                ze = zis.getNextEntry();
            }//while

            zis.closeEntry();
            zis.close();

           EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Unzipping Done.");
            
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return names;
    }

}
