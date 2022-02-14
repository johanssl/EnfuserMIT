/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.main;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.core.receptor.RP;
import java.io.Serializable;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.ftools.FuserTools.tab;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import static org.fmi.aq.enfuser.core.assimilation.WSSEbuilder.STATE_NAMES;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.options.FusionOptions;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;

/**
 *
 * @author Lasse
 */
public class Observation implements Serializable {

    private static final double PI = Math.PI;
//private final static double degreeInKM = 2*PI*6371/360;    

    public boolean autoCorrected = false;
    private float origVal = 0;
    public Dtime dt;
    public final int typeInt;
    public float value;
    public float lat;
    public float lon;
    public short height_m;
    public String ID;
    public String sourceFile;
    
    public RP incorporatedEnv = null;
    private HashMap<Integer,float[]> loovExps = null;
    
    private int dataFusion_qualityIndex = 0;
    private float dataFusion_wPOA = -1;
    private float dataFusion_wPOA_even = -1;
    private float dataFusion_wPOA_unadjusted = -1;
    private byte dataFusion_dbq=1;//variability class from db.
    private String dataFusion_string=null;
    private float dataFusion_autoc=0;
    
    private boolean reliabilityAssessed = false;
    private Dtime[] allDates = null;

    public boolean dtCanChanged = false;
    private boolean fusionPoint = false;
    public static final String FUSION_OB_ID = "FUSION_POINT";
    public final static String MET_OB_ID = "MET";
    public boolean unitScaled=false;

    public Observation(int typeInt, String ID, float lat, float lon,
            Dtime dt, float value, float height_m, String sourceFile) {

        this.typeInt = typeInt;
        this.value = value;
        this.dt = dt;
        this.lat = lat;
        this.lon = lon;
        this.height_m = (short) height_m;
        this.ID = ID;
        this.sourceFile = sourceFile;
    }

    public Observation(int typeInt, DBline dbl, Dtime dt, float fval, String source) {
        this(typeInt, dbl.ID, (float)dbl.lat, (float)dbl.lon,
                dt,fval,(float)dbl.height_m, source);
    }

    /**
     * Setup full-hour dates for the observation time, included past, current
     * and future local times.
     *
     * @param ens the core
     * @return time instances (previous, next hour in UTC and local), given by
     * TZT.
     */
    public Dtime[] dates(DataCore ens) {
        if (allDates == null) {
            allDates = ens.tzt.getDates(dt);
        }
        return allDates;
    }
    
    private Double repVal = null;
    public String replacedValue() {
        if (repVal==null) return "";
       return editPrecision(repVal,2)+"";
    }
    
    public void logReplacement(Observation o) {
        this.repVal = (double)o.value;
    }
    
    /**
     * Creates an Observation that can be used for fast Area fusion.
     * It will use hashed emission grids and take BlockAnalysis from
     * FastAreaGrids.
     * @param lat
     * @param lon
     * @param dt
     * @param height_m
     * @param ops
     * @return 
     */
    public static Observation AreaFusionPoint(double lat, double lon,
            Dtime dt, float height_m, FusionOptions ops) {
        Observation o = new Observation((byte) ops.VARA.Q[0], 
                Observation.FUSION_OB_ID, (float) lat, (float) lon,
                dt, 0, (short) Math.max(2, height_m), null);
        o.fusionPoint = true;
        return o;
    }
        
        /**
         * Creates an observation for custom point as if it comes from
         * a measurement source location. 
         * Note: BlockAnalysis for the location will be created anew for
         * this location since the ID will be set as "TEMPORARY".
         * @param lat
         * @param lon
         * @param dt
         * @param height_m
         * @param ops
         * @return 
         */
        public static Observation ObservationPointDummy(double lat, double lon,
            Dtime dt, float height_m, FusionOptions ops) {
        Observation o = new Observation((byte) ops.VARA.Q[0], 
                BlockAnalysis.TEMPORARY_LOCATION, (float) lat, (float) lon,
                dt, 0, (short) Math.max(2, height_m), null);
        o.fusionPoint = false;
        return o;
    }

    public static Observation MetObsDummy(double lat, double lon, Dtime dt, FusionOptions ops) {
        Observation o = new Observation(ops.VARA.metVars.get(0),
                Observation.MET_OB_ID, (float) lat, (float) lon,
                dt, 0, 2, null);
        return o;
    }

    /**
     * Data point maturity, which is used for forecasted modelled datapoints,
     * has no designated property in the Observation. This information in hours
     * is carried in Dtime.freeByte with a maximum value of 128h.
     *
     * @return data maturity value [h]
     */
    public byte dataMaturity_h() {
        return this.dt.freeByte;
    }
    
    public void setDataMaturity_h(byte b) {
        this.dt.freeByte = b;
    }

    @Override
    public Observation clone() {
        Observation ob = new Observation(this.typeInt, this.ID, lat, lon,
                dt.clone(), value, (float) this.height_m, this.sourceFile);
        return ob;
    }

    public Observation cloneDifferentType(int type) {
        Observation ob = new Observation(type, this.ID, lat, lon,
                dt.clone(), value, (float) this.height_m, this.sourceFile);
        return ob;
    }

    public long getSecCounter() {
        return (this.dt.systemSeconds());
    }

    //This method calculates the distance between the given coordinates and this observation
    public double getDistance_m(double lat, double lon) {
        return Observation.getDistance_m(lat, lon, this.lat, this.lon);
    }

    /**
     * This method calculates the distance between the given coordinates and
     * this observation
     *
     * @param lat first point latitude
     * @param lon first point longitude
     * @param lat2 second point latitude
     * @param lon2 second point longitude
     * @return distance in meters
     */
    public static double getDistance_m(double lat, double lon,
            double lat2, double lon2) {

        double distance;
        //if (false) {
        double scaler = Math.cos(2 * PI * lat / 360);
        double d2 = (lat - lat2) * (lat - lat2) + scaler * scaler 
                * (lon - lon2) * (lon - lon2);
        distance = Math.sqrt(d2);

        distance *= degreeInMeters; // now the distance is in meters
        return distance;

    }

        /**
     * This method calculates the SQUARED distance [m2] between the given coordinates and
     * this observation.
     *
     * @param lat first point latitude
     * @param lon first point longitude
     * @param lat2 second point latitude
     * @param lon2 second point longitude
     * @return squared distance in [m2]
     */
    public static double getDistance_m_SQ(double lat, double lon,
            double lat2, double lon2) {

        double scaler = FastMath.FM.getFastLonScaler(lat);
        double d2 = (lat - lat2) * (lat - lat2) + 
                scaler * scaler  * (lon - lon2) * (lon - lon2);
        return d2 * degreeInMeters * degreeInMeters;
    }

    /**
     * This method calculates the temporal difference in SECONDS 
     * between the given time and this observation
     * 
     * @param datetime1 the date
     */ 
    public int calcSecondsBetween(Dtime datetime1) {

        long checksum = (this.dt.systemSeconds());
        long checksum1 = (datetime1.systemSeconds());
        return (int) Math.abs(checksum1 - checksum);

    }

    public boolean reliabilityAssessed() {
        return this.reliabilityAssessed;
    }

    public String type(FusionOptions ops) {
        return ops.VARA.VAR_NAME(typeInt);
    }

    public boolean fusionPoint() {
        return this.fusionPoint;
    }

    /**
     * Get stats in simple String form for this Observation.
     * @param ops
     */
    public String getPrintout(FusionOptions ops) {
        String s = tab(this.ID, 20) + tab("," + this.lat + "", 10) 
                + tab("," + this.lon + "", 10)
                + tab("," + this.dt.getStringDate(), 15) + tab("," 
                        + this.type(ops) + "", 10);
        return s;
    }

    /**
     * In case the original value has been modified with automatic offset (for
     * sensors) this method returns the stored original value.
     *
     * @return the original, non-corrected or modified observation value.
     */
    public float origObsVal() {
        if (!this.autoCorrected) {
            return this.value;
        }
        return this.origVal;
    }

    /**
     * Apply an offset to the stored value and store the original given value.
     *
     * @param off offset value that is to be applied to the value held by this
     * Observation (added). The original value is stored for later reference.
     */
    public void replaceOriginalWithOffset(float off) {

        if (this.autoCorrected) {//already done, this is a bit suspicious...
            //the original value must not be modified
            value += off;
            if (value < 0) {
                value = 0;
            }
        }

        this.autoCorrected = true;
        this.origVal = this.value;

        value += off;
        if (value < 0) {
            value = 0;
        }
    }
    
    public void replaceValue(float newValue) {
            
        if (this.autoCorrected) return;

        this.autoCorrected = true;
        this.origVal = this.value;
        value =newValue;
        
    }

    public void setFusionPoint(boolean b) {
        this.fusionPoint = b;
    }

    /**
     * get a String representation of the data fusion outcome (reliability)
     * associated with the observation.
     *
     * @return numeric value that is the weight given to the observation (of
     * average weight) followed by a String for overall credibility. Example
     * "2.10,accepted" or "0.1,strongly_reduced".
     */
    public String reliabilityString() {
        return this.dataFusion_string;
    }
    
    private void buildDataFusionString() {
        this.dataFusion_string =
         (int)dataFusion_wPOA_even +"-->"       
        +(int)dataFusion_wPOA_unadjusted +"-->"
        +(int)dataFusion_wPOA
        +", " + STATE_NAMES[dataFusion_qualityIndex]
        +" ("+ this.dataFusion_dbq
        +", "+editPrecision(this.dataFusion_autoc,1)+")";        

    }

    public Integer dataFusion_qualityIndex() {
        return this.dataFusion_qualityIndex;
    }

    public double dataFusion_Weight_POA() {
        return editPrecision(this.dataFusion_wPOA, 2);
    }

    public void setDataFusionOutcome(int obsState, float wPOAeven, float wPOA, float wPOA_unadjusted,
            byte dbq, float autoc) {
        this.dataFusion_qualityIndex = obsState;
        this.dataFusion_wPOA = wPOA;
        this.dataFusion_wPOA_unadjusted = wPOA_unadjusted;
        this.dataFusion_wPOA_even = wPOAeven;
        this.dataFusion_dbq = dbq;
        this.dataFusion_autoc = autoc;
        this.reliabilityAssessed = true;
        buildDataFusionString();
    }

    //=========METHODS for model predictions=========////////////
    
    private Met backupMet = null;
    public Met getStoreMet(DataCore ens) {
        if (this.incorporatedEnv != null) {
            return this.incorporatedEnv.met;
        }

        if (backupMet == null) {
            backupMet = ens.getStoreMet(this.dt, this.lat, this.lon);
        }
        return backupMet;
    }
    
  

    public double relativeDataFusionOutcome() {
       return this.dataFusion_wPOA/this.dataFusion_wPOA_even;
    }

    public int dbImprecision(DataCore ens) {
        int imprec =10;
        if (this.fusionPoint)imprec=0;
        
        DBline dbl = ens.DB.get(ID);
        if (dbl!=null) {
            return (int)dbl.varScaler(this.typeInt);
        } else {
            return imprec;
        }
        
    }

    /**
     * Return stored source component expression for the model prediction, for
     * the given variable type. This return null if LOOV-data fusion iteration
     * has been disabled.
     * @param typeInt variable type index
     * @return source component expression for the model prediction
     */
    public float[] getLOOVexps(int typeInt) {
        if (this.loovExps==null) return null;
        return this.loovExps.get(typeInt);
    }
    
     /**
     * After True Leave One Out Validation (LOOV), log the results to the measurement
     * that was being operated on.This is the data fusion outcome when the
     * measurement was omitted from the data fusion by setting it's squared
     * error to zero.
     * @param typeInt variable type index
     * @param LOOVexps source component expression for the model prediction
     * when this Observation was omitted from data fusion.
     */
    public void addValidationValue(int typeInt, float[] LOOVexps) {
        if (LOOVexps==null) return;
        if (this.loovExps==null) this.loovExps = new HashMap<>();
        this.loovExps.put(typeInt, LOOVexps);
    }

    public int aveCount =0;
    public void logAveragingCount(int singular_aveN) {
       this.aveCount =singular_aveN;
    }

}
