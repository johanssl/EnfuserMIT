/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.RegionalFileSwitch;
import org.fmi.aq.interfacing.Feeder;

/**
 * This class contains a database for a) DBline entries and b) for a custom
 * mapping of coordinate pairs for designated ID-identifiers. The DBlines are
 * held in multiple hashMaps with different ID-keys, which makes it possible to
 * associate a proper DBline to its source even if the data uses one of the
 * sources "alias" identifiers.
 *
 *
 * The class offsers some easy-to-use get-methods for DBlines. In particular, if
 * an db-entry does not aleady exist an new entry can be created automatically
 * (with updates to db.csv).
 *
 * The class also offers some older methods for setting up information source
 * auto-covariance data.
 *
 * @author Lasse Johansson
 */
public class DataBase {

    HashMap<String, DBline> db;
    HashMap<String, DBline> alias_db;
    HashMap<String, DBline> alias2_db;
    private final VarAssociations VARA;
    public final static int MAX_LAG_TAU = 35;
    public HashMap<String, double[]> coordmap = new HashMap<>();
    public String dir_dbcsv;

    /**
     * Create a DataBase instance, taking into account the region specified in
     * the given FusionOptions. The constructor browses the regional db.csv file
     * for DBline entries. However, if the regional file does not exist then a
     * similar file is searched from the data root directory.
     *
     * In addition, the initiation of DB read through files under /sourceNfo/
     * for stored auto-covariance data (a bit older feature)
     *
     * @param ops regional options/settings
     */
    public DataBase(FusionOptions ops) {
        this.VARA = ops.VARA;
        this.db = new HashMap<>();
        this.alias_db = new HashMap<>();
        this.alias2_db = new HashMap<>();

        File f = RegionalFileSwitch.selectFile(ops, RegionalFileSwitch.DB_FILE);
        this.dir_dbcsv = f.getParentFile() + File.separator;
        ArrayList<String> lines = FileOps.readStringArrayFromFile(f);
        
        boolean first = true;
        for (String line:lines) {
            if (first) {
                first = false;
                continue;//for header
            }
            String[] line_values = line.split(";");
            DBline dbl = new DBline(line_values,VARA);
            this.db.put(dbl.ID, dbl);
            this.alias_db.put(dbl.alias_ID, dbl);
            this.alias2_db.put(dbl.alias_ID2, dbl);
      
        }//for db lines
        
        //read additional coordinate mappings
        String filen = ops.getDir(FusionOptions.DIR_DAT_COMMON) + "coordinateMapping.csv";
        f = new File(filen);
        if (f.exists()) {
            ArrayList<String> arr= FileOps.readStringArrayFromFile(filen);
            int k = -1;
            for (String s : arr) {
                k++;
                if (k == 0) {
                    continue;
                }

               try { 
                String[] split = s.split(";");
                String id = split[0];
                double lat = Double.valueOf(split[1]);
                double lon = Double.valueOf(split[2]);
                this.coordmap.put(id, new double[]{lat, lon});
                
               } catch (IndexOutOfBoundsException | NumberFormatException e) {
                   EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                  f.getAbsolutePath() +": line cannot be parsed: " + s);
               }
            }//for lines
        }//if coordinateMapping exists
 
    }

    /**
     * Get the listed coordinates (lat,lon) for DBline with the given
     * identification String.
     *
     * @param ID String key for identification (can be one of the listed alias
     * names)
     * @return Double[]{lat,lon}. If no DB-entry was found OR the ID is for
     * gridded source, then the returned Doubles are null.
     */
    public Double[] getCoordsFromSource(String ID) {

        Double[] coords;

        DBline dbl = this.get(ID);
        if (dbl == null || !dbl.hasCoordinates) {
            coords = new Double[]{null, null};
        } else {
            coords = new Double[]{dbl.lat, dbl.lon};
        }

        return coords;

    }

    /**
     * Get the DBline associated with the String identifier (an alias name can
     * be given)
     *
     * @param ID String ID key.
     * @return DBline entry, which is null if no entry was found.
     */
    public DBline get(String ID) {

        DBline dbl = db.get(ID);
        if (dbl == null) {
            dbl = alias_db.get(ID);
        }
        if (dbl == null) {
            dbl = alias2_db.get(ID);
        }

        return dbl;
    }

    public DBline getWithProximitySearch(double lat, double lon, int tol_m) {

        for (String ida : this.db.keySet()) {
            DBline test = this.db.get(ida);
            if (test.hasCoordinates) {

                double dist = Observation.getDistance_m(test.lat, test.lon, lat, lon);
                if (dist < tol_m) {
                    return test;
                }
            }
        }

        return null;
    }

    /**
     * Get the listed coordinate (latitude) for DBline with the given
     * identification String.
     *
     * @param ID String key for identification (can be one of the listed alias
     * names)
     * @return Double latidue. If no DB-entry was found OR the ID is for gridded
     * source, then the returned Double is null.
     */
    public Double getLatitude(String ID) {
        Double[] coords = this.getCoordsFromSource(ID);
        return coords[0];

    }

    /**
     * Get the listed coordinate (longitude) for DBline with the given
     * identification String.
     *
     * @param ID String key for identification (can be one of the listed alias
     * names)
     * @return Double longitude. If no DB-entry was found OR the ID is for
     * gridded source, then the returned Double is null.
     */
    public Double getLongitude(String ID) {
        Double[] coords = this.getCoordsFromSource(ID);
        return coords[1];

    }

    /**
     * Get the defined value scaler for specific variable type with the given
     * ID-string tag.
     *
     * @param ID identifier String name
     * @param typeInt variable type
     * @return a Double, which is null in case no such scaler has been defined.
     */
    public Double getValueScaler(String ID, int typeInt) {

        DBline dbl = this.get(ID);
        if (dbl == null) {
            return 1.0;
        } else {
            return dbl.getValScaler(typeInt);
        }

    }

    /**
     * Get an estimate of the total variability of a data point with respect to
     * another point of interest. This relates to the older system that utilized
     * Variograms (Johansson et al 2015)
     *
     * @param s Source for data
     * @param lag_tau_h temporal difference in hours
     * @param t_off forecasting period length in hours
     * @param dist_km spatial separation in kilometers.
     * @param ops used options/settings
     * @return a variability estimate (standard deviation^2)
     */
    public float getTotalVar(AbstractSource s, float lag_tau_h, short t_off, float dist_km,
            FusionOptions ops) {
        return getVariance(s.dbl, s.typeInt, dist_km, lag_tau_h, t_off, ops);
    }

    /**
     * Assess auto-covariance values (for older Variogram methods). In case no
     * previously assessed auto-covariance data is found then some very generic
     * profiles are used as a function of the variable type.
     *
     * @param typeInt variable type index
     * @param tau temporal separation in hours
     * @param dbl DBline entry
     * @return index 0 contains the nugget, when tau is zero. index [i] is the
     * variance with full tau. Hence the temporal variance component = [1] -
     * [0].
     */
    private float[] calculateNuggetAndTemporal(int typeInt, float tau, DBline dbl) {
        // check if more accurate historical data can br found from dbl
        if (dbl != null) {
            if (dbl.hasTemporalVarioram(typeInt)) { // tau-0 vis usally quite large, half of it is better estimation for the nugget
                return new float[]{dbl.getTemporalVario(typeInt, 0),
                    dbl.getTemporalVario(typeInt, (int) tau)};
            }

        }
        float t5, t4, t3, t2, t1; //parameters for t-polynomial and distance function
        float b;
        if (typeInt == VARA.VAR_SO2) {
            t5 = 0f;
            t4 = 0f;
            t3 = 0.0015f;
            t2 = -0.0934f;
            t1 = 1.874f;
            b = 4f;

        } else if (typeInt == VARA.VAR_PM25) {
            t5 = 0f;
            t4 = 0f;
            t3 = 0.004f;
            t2 = -0.2799f;
            t1 = 7.1896f;
            b = 4f;

        } else if (typeInt == VARA.VAR_CO) {
            t5 = 0f;
            t4 = 0.3374f; // about 17000 increase in 10h. R^2 = 0.998
            t3 = -16.084f;
            t2 = 145.44f;
            t1 = 1651.3f;
            b = 1000f;

        } else if (typeInt == VARA.VAR_O3) {
            t5 = 0f;
            t4 = 0.0105f; // about 800 increase in 10h. R^2 = 0.9987
            t3 = -0.4687f;
            t2 = 2.799f;
            t1 = 85.227f;
            b = 20f;

        } else if (typeInt == VARA.VAR_NO2) {

            t5 = 0f;
            t4 = 0.0023f * 2f;
            t3 = -0.05f * 2f;
            t2 = 72.9f * 2f;
            t1 = 37.41f * 2f;
            b = 20f;

        } else if (typeInt == VARA.VAR_PM10
                || typeInt == VARA.VAR_COARSE_PM) {

            t5 = 0f;
            t4 = 0.0f;
            t3 = 0.0302f;
            t2 = -1.7555f;
            t1 = 32.061f;
            b = 20f;

        } else if (typeInt == VARA.VAR_TEMP) {

            t5 = 0;
            t4 = 0.00005f;
            t3 = -0.00036f;
            t2 = 0.0711f;
            t1 = 0.3737f;
            b = 0.9f;

        } else if (typeInt == VARA.VAR_WIND_N
                || typeInt == VARA.VAR_WIND_E) {

            t5 = 0;
            t4 = 0.0000005f;
            t3 = -0.00033f;
            t2 = 0.0247f;
            t1 = 0.5793f;
            b = 0.9f;

        } else if (typeInt == VARA.VAR_RAIN) {

            t5 = 0;
            t4 = 0.00005f;
            t3 = -0.00036f;
            t2 = 0.0711f;
            t1 = 0.3737f;
            b = 0.9f;

        } else {//GENERIC

            t5 = 0;
            t4 = 0;
            t3 = 0.004f;
            t2 = -0.2799f;
            t1 = 7.1896f;
            b = 20f;
        }

        if (tau > MAX_LAG_TAU) {
            tau = MAX_LAG_TAU;
        }
        //[0] is the variance when tau =0 => b
        return new float[]{b, (t5 * tau * tau * tau * tau * tau + t4 * tau * tau * tau * tau
            + t3 * tau * tau * tau + t2 * tau * tau + t1 * tau) + b};

    }

    public final static float MAX_DIST_KM = 60;

    /**
     * Get an estimate of the total variability of a data point with respect to
     * another point of interest. This relates to the older system that utilized
     * Variograms (Johansson et al 2015)
     *
     * @param dbl DBline entry
     * @param typeInt variable type
     * @param lag_tau_h temporal difference in hours
     * @param t_off forecasting period length in hours
     * @param dist_km spatial separation in kilometers.
     * @param ops used options/settings
     * @return a variability estimate (standard deviation^2)
     */
    public float getVariance(DBline dbl, int typeInt, float dist_km, float lag_tau_h,
            short t_off, FusionOptions ops) {

        lag_tau_h = Math.abs(lag_tau_h);
        if (lag_tau_h > MAX_LAG_TAU) {
            lag_tau_h = MAX_LAG_TAU;
        }

        //[0] nugget term, when tau =0, [1] is nugget + temporal, at full tau
        float[] nuggetTemporal = this.calculateNuggetAndTemporal(typeInt, lag_tau_h, dbl);
        float nugget = nuggetTemporal[0];

        float t_offScaler = 1.0f + t_off / 48.0f; // assumption: 1 day model value has double nugget
        float dist_scaler = 1f + 10 * dist_km;
        float temporal_scaler = 1f + lag_tau_h;

        float dbl_variance_scaler = 1f;//this is a manual scaler that can be manipulated in db.csv
        if (dbl != null) {
            dbl_variance_scaler = dbl.varScaler(typeInt);
        }

        //make the final product
        float total_var = nugget * t_offScaler * dbl_variance_scaler * dist_scaler * temporal_scaler;
        return total_var;

    }

    private static String getGenericMeasurementID(Feed f,double lat, double lon) {
        String gen = "sensor_";
        int decimals =1000;
        if (f != null) {
            gen = f.genID + "_";
            decimals = f.genID_decimals;
        }
        String ID = gen + (int) (lat * decimals) + "_" + (int) (lon * decimals);
        return ID;

    }

    /**
     * Fetch and if not found, Add and return a new DBline entry for a
     * measurement location.
     *
     * @param f
     * @param ID name identifier for the new entry, if null, a generic ID will
     * be created.
     * @param IDalias alias name for the source
     * @param timezone tz for the data
     * @param lat latitude
     * @param lon longitude
     * @param imprecision a data credibility rating (larger is for worse
     * quality) If the given value is larger than 5, then sensor autoCorrection
     * setting is enabled.
     * @param measurementHeight measurement height
     * @param outputHide if true then the source statistics will be hidden
     * @param autoOffsets if true, then the time series for the source can be
     * automatically offset/corrected right after the load phase. This is allowed
     * only if the imprecision rating is larger than 9.
     * @return the created new DBline (also written to db.csv)
     */
    public DBline getStoreMeasurementSource(Feed f,String ID,
            String IDalias, byte timezone, double lat,
            double lon, int imprecision, double measurementHeight,
            boolean outputHide, boolean autoOffsets) {

        if (ID == null) {
            ID = getGenericMeasurementID(f, lat, lon);
        }
//find by name
        DBline dbl = this.get(ID);
        if (dbl != null) {
            return dbl;
        }

        if (f.searchRad_m != null) {
            for (String ida : this.db.keySet()) {
                DBline test = this.db.get(ida);
                if (test.hasCoordinates) {

                    //fast skip
                    if (Math.abs(test.lat - lat) > 1) {
                        continue;
                    }
                    if (Math.abs(test.lon - lon) > 1) {
                        continue;
                    }

                    double dist = Observation.getDistance_m(test.lat, test.lon, lat, lon);
                    if (dist < f.searchRad_m) {
                        return test;
                    }
                }
            }
        }//if search rad

        if (IDalias == null) {
            IDalias = getGenericMeasurementID(f, lat, lon);
        }
        
        if (IDalias.equals(ID)) {//use reverse geo-coding since this adds litle value
            String geoc = Feeder.reverseGeocode(lat, lon, true);
            if (geoc!=null) {
                EnfuserLogger.log(Level.FINER,DataBase.class,"DB:adding a new source with a "
                        + "reverse-geocoded secondary name: "+ geoc);
                IDalias =geoc;
            }
        }
        
//search proper alias name from listed sources that have fixed coordinates

//nothing was found - addd new
String oHide ="FALSE";
if (outputHide) oHide ="TRUE";
        String autoOff = "FALSE";
        if (autoOffsets && imprecision > 5) {
            autoOff = "TRUE";
        }

        String[] line = {
            ID,
            lat + "",
            lon + "",
            "0",//disloc
            imprecision + "",//var_sc
            (int) measurementHeight + "",//h
            timezone + "",
            "1",//val_sc
            IDalias,
            ID,
            "",
            autoOff,
            "",
            oHide
        };

        String fullLine = "";
        for (String l : line) {
            fullLine += l + ";";
        }
        newDBlines.put(fullLine, Boolean.TRUE);
        //ArrayList<String> arr = new ArrayList<>();
        //arr.add(fullLine);

        dbl = new DBline(line,VARA);//create
        EnfuserLogger.log(Level.FINER,DataBase.class,"NEW DB entry: "+ ID+", "+ lat +", " + lon +", "+ IDalias);
        //FileOps.printOutALtoFile2(this.dir_dbcsv, arr, "db.csv", true);// write (append) to file
        this.db.put(dbl.ID, dbl);//add to DB currently in memory
        return dbl;
    }
    
    
    public DBline getStoreMeasurementSource(Feed f,String ID,
            String IDalias, byte timezone, double lat,double lon,
            int imprecision, double measurementHeight) {
        
        return getStoreMeasurementSource(f,ID,IDalias, timezone, lat,lon,
            imprecision, measurementHeight,false, true);
    }
    
    /**
     * Add a generic line to database for a gridded data source with
     * the given name.
     * @param ID the name for source
     * @return generic db-line instance after the addition.
     */
    public DBline storeGridSource(String ID) {
    DBline dbl;
    int imprecision =3;
     String autoOff = "FALSE";


    String[] line = {
        ID,
         "",
         "",
        "5",//disloc
        imprecision + "",//var_sc
        10 + "",//h
        0 + "",
        "1",//val_sc
        ID,
        ID,
        "",
        autoOff
    };

    String fullLine = "";
    for (String l : line) {
        fullLine += l + ";";
    }
    newDBlines.put(fullLine, Boolean.TRUE);
    //ArrayList<String> arr = new ArrayList<>();
    //arr.add(fullLine);

    dbl = new DBline(line,VARA);//create

    //FileOps.printOutALtoFile2(this.dir_dbcsv, arr, "db.csv", true);// write (append) to file
    this.db.put(dbl.ID, dbl);//add to DB currently in memory
    EnfuserLogger.log(Level.FINER,DataBase.class,"A new gridded data source was added to DB: "+ID);
    return dbl;
}

public HashMap<String,Boolean> newDBlines = new HashMap<>();
/**
 * Add this new collection of DB-lines to the db.csv file.
 * This should be done once when all DataPacks have been merged.
 * @param newDBlines2 
 */
public void newDBlinesToFile(HashMap<String,Boolean> newDBlines2) {
    ArrayList<String> arr = new ArrayList<>();
    
    for (String key:newDBlines2.keySet()) {
        arr.add(key);
    }

    FileOps.printOutALtoFile2(this.dir_dbcsv, arr, "db.csv", true);// write (append) to file
    EnfuserLogger.log(Level.INFO,DataBase.class,
            arr.size() + "  new DBlines were added to DB: "+this.dir_dbcsv + "db.csv");
}

    /**
     * Find the secondary ID for the given String identifier, mainly aimed for
     * generic auto-generated DB-names. The secondary alias ID is often a more
     * human readable version.
     *
     * @param id String tag
     * @return another String tag (improved readability)
     */
    public String getSecondaryID(String id) {

        if (!id.contains("sensor")) {
            return id;
        }
        DBline dbl = this.get(id);
        return dbl.alias_ID;

    }

    /**
     * Fetch a pair of custom coordinates that has been associated to this
     * String key.
     *
     * @param id String key
     * @return double[]{lat,lon}, can be null if not found.
     */
    public double[] getCustomCoords(String id) {
        return this.coordmap.get(id);
    }

    /**
     * Add an instance to primary DBline hash.
     *
     * @param copy the instance to be added.
     */
    public void add(DBline copy) {
        this.db.put(copy.ID, copy);
    }

    public boolean typeAutoBanned(String ID, int typeInt) {
        DBline dbl = this.get(ID);
        if (dbl == null) {
            return false;
        }

        return dbl.typeAutoBanned(typeInt);

    }


    public ArrayList<DBline> findNearby(double rad_m, double lat, double lon, String mustContain) {
        ArrayList<DBline> arr = new ArrayList<>();
        
        for (String ida : this.db.keySet()) {
            DBline test = this.db.get(ida);
            if (mustContain!=null && !test.ID.contains(mustContain)) continue;
            if (test.hasCoordinates) {
                
                double dist = Observation.getDistance_m(test.lat, test.lon, lat, lon);
                if (dist < rad_m) {
                    arr.add(test);
                }
            }
        }
        
        return arr;
    }

        public DBline findClosest(double rad_m, double lat, double lon, String mustContain) {
        DBline dbl = null;
        double rad = Double.MAX_VALUE;
        
        for (String ida : this.db.keySet()) {
            DBline test = this.db.get(ida);
            if (mustContain!=null && !test.ID.contains(mustContain)) continue;
            if (test.hasCoordinates) {
                
                double dist = Observation.getDistance_m(test.lat, test.lon, lat, lon);
                if (dist < rad_m) {
                   if (dist<rad ) {
                       rad = dist;
                       dbl=test;
                   }
                }
            }
        }
        
        return dbl;
    }

    public ArrayList<String> allIDs() {
       ArrayList<String> arr = new ArrayList<>();
       for (String ID:this.db.keySet()) {
           arr.add(ID+"");
       }
       return arr;
    }

    Iterable<String> keySet() {
        return this.db.keySet();
    }

}// class END
