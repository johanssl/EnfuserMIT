/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.Calendar;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class described a DataBase line for specific information source such as
 * a model (SILAM, HIRLAM) and measurement stations. All of the lines are read
 * and maintained in "db.csv" file: one line per entry.
 *
 * The most notable pieces of information included in the DBline are: -
 * identification tags/names (up to three) for matching. - coordinate and
 * height(m) information, if applicable.
 *
 * For measurement stations there are a couple of additional features: - sector
 * clears (in case the measurement device is near a wall and and the proper
 * analysis of the local environment benefits of "ignoring" certain wind
 * directions towards the obstacles. - sensor auto-offsetting: the values of
 * sensors can be automatically adjusted if enabled.
 *
 * For some measurements the averaging period definition may change from source
 * to source. It is possible to define a date manipulation for all Observations
 * attached to this DBline entry to counter this effect. For example: the
 * Vaisala sensor data taken from NM10 seem to benefit from one hour automatic
 * date manipulation. This is done via 'tz' and 'tz_summer' field.
 *
 * The class has also some older features related to 'Variograms' as described
 * Johansson et al 2015. These are, however, not utilized much in ENFUSER
 * anymore.
 *
 * @author Lasse Johansson
 */
public class DBline {

    public final float NO_VARIO = -1;
    public String ID;
    public String alias_ID;
    public String alias_ID2;
    public String custom ="";
    
    public boolean hasCoordinates;
    public double lat;
    public double lon;

    public boolean hideFromOutput = false;//this can be used for sensitive sensor data under development.
    private final float varScaler;
    private HashMap<Integer, Double> typeVarScalers;
    private float[][] nuggetTemporal;

    public final double height_m;
    private final double[] value_scaler;

    public byte tz = 0;
    public byte tz_summer = 0;

    protected String[] oLine;
    public HashMap<Integer, Integer> sectorClearDists_30deg;
    public boolean autoOffset = false;
    private HashMap<Integer, Boolean> typeBans;

    /**
     * Make a copy of DBline entry.
     *
     * @param dbl2 the instance to be copied.
     * @param VARA
     * @return a new copy using the data of the original DBline.
     */
    public static DBline copyOriginal(DBline dbl2, VarAssociations VARA) {
        DBline dbl = new DBline(dbl2.oLine,VARA);
        return dbl;
    }
    
    /**
     * Return a variability metric (lower value means more reliable)
     * for the source taking into account the variable type.
     * Variable-specific modifications are only
     * taken into account if such instructions are present in the DB-line
     * when read from db.csv.
     * 
     * @param typeInt variable type index
     * @return a value in between [1,1000]
     */
    public float varScaler(int typeInt) {
        float f = (float)Math.pow(this.varScaler,1.4);
        if (this.typeVarScalers!=null) {
            Double fact = this.typeVarScalers.get(typeInt);
            if (fact!=null) f*= fact;//multiply
            //some limitations are in order
            if (f>1000) f =1000;
            if (f<1) f=1;
        }
        return f;
    }
    
    
    /**
     * Read customization for variability rating, defined separately for pollutant
     * species. These are modifiers that operate the base variability rating.
     * @param varString for example: 'NO2=2.0, CO=3.0, SO2=10' will result
     * in a 2x rating for NO2, 3x for CO and 10x for SO2. Higher value
     * means LOWER emphasis on the data-points given by the source.
     * @param VARA variable definitions
     * @return hash of modifiers, key is the type index.
     */
    private static HashMap<Integer, Double> getVariabilityModifiers(String varString,
            VarAssociations VARA) {

        if (varString== null) return null;
        if (!varString.contains("=")) return null;
        
        HashMap<Integer, Double> type_varHash = new HashMap<>();
        
            String[] split = varString.split(",");
            for (String varline : split) {
                varline = varline.replace(" ", "");
                try {
                    String[] var_value = varline.split("=");
                    String var = var_value[0];
                    double q = Double.valueOf(var_value[1]);
                  
                    int typeInt = VARA.getTypeInt(var, true, "DBline qvar");
                     EnfuserLogger.log(Level.FINE,DBline.class,
                                "\t adding type-specific imprecision modifier" + var + ", " + q);
                    
                    type_varHash.put(typeInt, q);
                } catch (Exception e) {
                    EnfuserLogger.log(e,Level.WARNING,DBline.class,
                            "Something wrong with DB-line variability definitions:"+varString);
                }
            }

        return type_varHash;
    }
    

    /**
     * Constructor for DBline, based on a splitted text entry.The order of
     * elements should be as specified in db.csv.
     *
     * @param line splitted text line.
     * @param VARA
     */
    public DBline(String[] line, VarAssociations VARA) {
        this.oLine = line;
        this.ID = line[0];
        try {
            this.lat = Double.valueOf(line[1]);
            this.lon = Double.valueOf(line[2]);
            this.hasCoordinates = true;
        } catch (Exception e) {
            this.hasCoordinates = false;
        }

        this.nuggetTemporal = new float[VARA.VAR_LEN()][DataBase.MAX_LAG_TAU + 1];
        for (int h = 0; h < nuggetTemporal.length; h++) {
            for (int w = 0; w < nuggetTemporal[0].length; w++) {
                nuggetTemporal[h][w] = NO_VARIO;
            }
        }

        //density = line[4];
        //this.d_uncertainty_km = Float.valueOf(line[3]);   
        this.varScaler = Float.valueOf(line[4]);
        this.height_m = Double.valueOf(line[5]);

        if (line[6].contains(",")) {
            this.tz = Integer.valueOf(line[6].split(",")[0]).byteValue();
            this.tz_summer = Integer.valueOf(line[6].split(",")[1]).byteValue();
        } else {
            this.tz = Integer.valueOf(line[6]).byteValue();
            this.tz_summer = tz;
        }
        this.value_scaler = new double[VARA.VAR_LEN()];
        String allScalers = line[7];

        this.alias_ID = line[8];
        this.alias_ID2 = line[9];

        for (int i = 0; i < this.value_scaler.length; i++) {
            this.value_scaler[i] = 1.0; // default
        }//init

        if (allScalers.contains("=")) {
            EnfuserLogger.log(Level.FINER,DBline.class,ID + " contains var scaler info...");
            
            String[] temp = allScalers.split(",");
            //NO2=3     PM10=x     PM2.5=y    
            for (int j = 0; j < temp.length; j++) {
                String[] tem2 = temp[j].split("=");

                if (tem2.length == 2) {

                    double v = Double.valueOf(tem2[1]);
                    String vs_type = tem2[0];

                    int vs_typeInt = VARA.getTypeInt(vs_type);

                    this.value_scaler[vs_typeInt] = v;
                        EnfuserLogger.log(Level.FINER,DBline.class,
                                "\t" + ID + " var scaler " + vs_type + " = " + v);
                    
                }
            }
        }

        //manual sector clears
        try {

            String sects = line[10];//may not exist
            if (sects.contains("deg")) {//there is something to read
                sectorClearDists_30deg = new HashMap<>();
                String[] sectSplit = sects.split(",");
                for (String sp : sectSplit) {

                    String deg = sp.split("deg")[0];
                    String range = sp.split("deg")[1].replace("m", "");

                    this.sectorClearDists_30deg.put(Integer.valueOf(deg), Integer.valueOf(range));
                    EnfuserLogger.log(Level.FINER,DBline.class,
                            "DB:adding a custom sector block distance clear for "
                                + this.ID + " for direction " + deg + " -> " + range + "m");
                    
                }
            }

            String autoOff = line[11].toLowerCase();
            if (autoOff.contains("true")) {
                this.autoOffset = true;
            }

        } catch (Exception e) {

        }

        //type auto-bans
        try {

            String[] bans = line[12].split(",");//may not exist
            for (String type : bans) {
                int typeInt = VARA.getTypeInt(type);
                if (typeInt >= 0) {
                    if (this.typeBans == null) {
                        this.typeBans = new HashMap<>();
                    }
                    this.typeBans.put(typeInt, Boolean.TRUE);
                }
            }

        } catch (Exception e) {

        }

        //hidden stats
        try {
            String hide = line[13].toLowerCase();//may not exist
            if (hide.contains("true")) {
                this.hideFromOutput = true;
            }

        } catch (Exception e) {

        }
        
        if (line.length >14 ) {
            String content = line[14];
            this.typeVarScalers =  getVariabilityModifiers(content,VARA);
            
        }
        
        if (line.length >15 ) {
            this.custom = line[15];
        }
    }

    /**
     * Method for variograms (OLD). shift by one hour so that tau-0 is the
     * nugget effect. Otherwise the variance with tau-0 will be too large since
     * it contains temporal variation as well
     *
     * @param vals hourly auto-covariance data for the source
     * @param typeInt pollutant species
     */
    public void addNuggetTemporal(float[] vals, int typeInt) {

        float[] trueVals = new float[vals.length + 1];
        trueVals[0] = vals[0] / 2f;
        for (int k = 1; k < trueVals.length; k++) {
            trueVals[k] = vals[k - 1];
        }
        this.nuggetTemporal[typeInt] = trueVals;
    }

    /**
     * get a distance of BlockAnalysis clear direction. When BlockAnalysis is
     * created for this source (a measurement source) the wind direction up to
     * the specified distance will be artifically "cleared" from obstacles.
     *
     * @param dir the wind direction [0,360]
     * @return clear distance. Returns -1 if such has not been specified.
     */
    public int getCustomBlockDistClear(float dir) {
        if (this.sectorClearDists_30deg == null) {
            return -1;
        }
        int idir = (int) (dir / 10f) * 10;//e.g. 35 => 30
        for (int i = -1; i <= 1; i++) {
            int currWd = idir + i * 10;// 20,30,40
            Integer dist = this.sectorClearDists_30deg.get(currWd);
            if (dist != null) {
                return dist;//found, return
            }
        }

        return -1;
    }

    /**
     * Check whether this DBLine's source has been added with auto-covariance
     * data.
     *
     * @param typeInt pollutant species index
     * @return boolean value for data availability
     */
    public boolean hasTemporalVarioram(int typeInt) {
        return (this.nuggetTemporal[typeInt][0] != NO_VARIO);
    }

    /**
     * Get the temporal auto-covariance as function of variable species and hour
     * lag (tau)
     *
     * @param typeInt the species index
     * @param tau lag amount in hours
     * @return average autocovar
     */
    public float getTemporalVario(int typeInt, int tau) {
        if (tau >= this.nuggetTemporal[typeInt].length) {
            tau = this.nuggetTemporal[typeInt].length - 1;
        }
        return this.nuggetTemporal[typeInt][tau];
    }

    /**
     * return a value scaler for variable species. If no such has been specified
     * then return 1. (NOTE: Historic method, avoid using value scalers this
     * way).
     *
     * @param typeInt the variable species.
     * @return value scaler
     */
    public double getValScaler(int typeInt) {
        return this.value_scaler[typeInt];
    }

    /**
     * Manipulate Observation date object for pollutant measurement data in case
     * the averaging period definition differs from the generally agreed one.
     *
     * @param o the observation to be modified (o.dt).
     */
    public void modifyDateTime(Observation o) {

        int t = tz;
        if (isSummerTime(o.dt)) {
            t = this.tz_summer;
        }

        if (t == 0) {
            return;//nothing to do
        }
        if (o.dtCanChanged) {
            return;
        }

        o.dt = o.dt.clone();
        o.dt.addSeconds(-3600 * t);
    }

    /**
     * Manipulate GeoGrid date object for pollutant measurement data in case the
     * averaging period definition differs from the generally agreed one.
     *
     * @param g the GeoGrid to be modified (g.dt).
     */
    public void modifyDateTime(GeoGrid g) {

        int t = tz;
        // if (DT_MOD == DTMOD_ARCHIVE) t = tz_arch;
        if (t == 0) {
            return;//nothing to do
        }
        g.dt.addSeconds(-3600 * t);

    }

    private static int[][] summerDays_2005 = {
        {27, 30},//2005
        {26, 29},//2006
        {25, 28},//2007
        {30, 26},//2008
        {29, 25},//2009
        {28, 31},//2010
        {27, 30},//2011
        {25, 28},//2012
        {31, 27},//2013
        {30, 26},//2014
        {29, 25},//2015
        {27, 30},//2016
        {26, 29},//2017
        {25, 28},//2018
        {31, 27},//2019
        {29, 25},//2020
        {28, 31},//2021
        {27, 30},//2022
        {26, 29},//2023
        {31, 27},//2024
        {30, 26},//2025
        {29, 25},//2026
        {28, 31},//2027
    };

    /**
     * A method to test whether summer time is to be used. TODO: This method has
     * issues and is potentially dangerous.
     *
     * @param dt the date
     * @return boolean value in case the Date is in summer time.
     */
    private static boolean isSummerTime(Dtime dt) {
        if (dt.month_011 >= Calendar.MARCH && dt.month_011 <= Calendar.OCTOBER) {//summer time, roughly 

            int[] days = summerDays_2005[dt.year % 2005];//TODO: this will get OBSOLETE in 2028!
            int mard = days[0];
            int octd = days[1];

            if (dt.month_011 == Calendar.MARCH) {//special treatment

                if (dt.day > mard || (dt.day == mard && dt.hour > 2)) {
                    return true;
                } else {
                    return false;
                }

            } else if (dt.month_011 == Calendar.OCTOBER) {//special treatment

                if (dt.day < octd || (dt.day == octd && dt.hour <= 2)) {
                    return true;
                } else {
                    return false;
                }

            } else {//months 4,5,6,7,8,9
                return true;
            }

        } else {
            return false;
        }
    }

    boolean typeAutoBanned(int typeInt) {
        if (this.typeBans == null) {
            return false;
        }

        if (this.typeBans.get(typeInt) == null) {
            return false;
        } else {
            return true;
        }

    }

    public void shakeCoordinates(double[] latlon) {
        this.lat = latlon[0];
        this.lon = latlon[1];
    }

    public final static String MINUTE_DATA_ID_FLAG = "MINUTEDATA";
    public boolean providesMinuteData() {
       return this.custom.contains(MINUTE_DATA_ID_FLAG);
    }


}// end class
