/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.kriging.KrigingElements;
import org.fmi.aq.enfuser.kriging.CombinedValue;
import org.fmi.aq.enfuser.kriging.KrigingAlgorithm;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.enfuser.solar.SolarBank;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_CLASS_NAMES;

/**
 *
 * @author johanssl
 */
public class Met {

    public static Met average(ArrayList<Met> mets) {
        VarAssociations VARA = mets.get(0).VARA;
        int[] counter = new int[VARA.METVAR_LEN];
        Met met = new Met(0, 0,VARA,0);
        for (Met m : mets) {
            met.sysSecs = m.sysSecs;
            for (int t = 0; t < VARA.METVAR_LEN; t++) {

                Float val = m.metVars[t];
                if (val != null && t != VARA.VAR_WINDDIR_DEG && t != VARA.VAR_WINDSPEED) {
                    counter[t]++;
                    if (met.metVars[t] == null) {
                        met.metVars[t] = 0f;
                    }
                    met.metVars[t] = met.metVars[t] + val;
                }
            }//for types

        }//for mets

        for (int t = 0; t < VARA.METVAR_LEN; t++) {
            if (counter[t] > 0) {
                met.metVars[t] /= counter[t];
            }
        }

        met.processWindComponents("-AVERAGED MET");
        return met;
    }

    public long sysSecs=0;
    public final float lat;
    public final float lon;
    protected Float[] metVars;
    public final VarAssociations VARA;
    private Met(float lat, float lon, VarAssociations VARA, long sysSecs) {
        this.VARA = VARA;
        this.metVars = new Float[VARA.METVAR_LEN];
        this.lat = lat;
        this.lon = lon;
        this.sysSecs = sysSecs;
    }
    
    public static Met fromHash(HashMap<Integer,Double> hash, float lat, float lon,
            FusionOptions ops, long sysSecs) {
        Met met = new Met(lat,lon,ops.VARA,sysSecs);
        for (Integer typeInt:hash.keySet()) {
            Double val = hash.get(typeInt);
            if (val==null) continue;
            met.set(val.floatValue(), typeInt);
        }
        return met;
    }

    public static Met customize(ArrayList<float[]> arr, DataCore ens, 
            double lat, double lon, Dtime dt) {
        Met m = new Met(arr, ens, dt, lat, lon);
        return m;
    }

    protected Met(ArrayList<float[]> arr, DataCore ens, Dtime dt, double lat, double lon) {
        this.lat = (float) lat;
        this.lon = (float) lon;
        this.VARA =ens.ops.VARA;
        this.metVars = new Float[ens.ops.VARA.METVAR_LEN];
        for (float[] dat : arr) {

            int index = (int) dat[0];
            float f = dat[1];
            this.set(f, index);

        }
        Float solar =null;
        if (ens!=null)solar =ens.hashMet.getStoreSolarSpecs(dt, lat, lon)[SolarBank.POWER];
        fillNullMetVars(solar);
    }

    public Met(ArrayList<float[]> arr, float lat, float lon, VarAssociations VARA) {
        this.VARA = VARA;
        this.lat = lat;
        this.lon = lon;
        this.metVars = new Float[VARA.METVAR_LEN];
        for (float[] dat : arr) {

            int index = (int) dat[0];
            float f = dat[1];
            this.set(f, index);

        }
    }

    private void setStabilityClass() {

        byte stabi;
        Float imoL = get(VARA.VAR_IMO_LEN);
        if (imoL != null) {
            float invMO = imoL;
            stabi = StabiClasses.basedOnInvMO(invMO);

        } else {//crude approximation

            //stability based on insolation, cloudcover and windspeed (Pasquil-Gilford adaptation)
            double effRad = this.totalSolarRadiation();//solRad_pow/Math.max(1,this.getCloudCover_nullSafe());
            float ws = this.getWindSpeed_raw();
            stabi = StabiClasses.basedOnWindRad(effRad, ws);
        }//else cruder approach

        this.set((float) stabi, VARA.VAR_STABI);

    }


    public final static String MET_ID = "MET";

    /**
     * Create a Met instance that uses the meteorological data held by DataPack
     * in FusionCore. For all meteorological values held by Met the simple data
     * fusion algorithm is used for data assimilation. This method should only
     * be invoked by "getStoreMet()"
     *
     * @param dt time
     * @param lat latitude
     * @param lon longitude
     * @param ens the core
     * @return created Met instance
     */
    public static Met create(Dtime dt, double lat, double lon, DataCore ens) {
        VarAssociations VARA = ens.ops.VARA;
        Met met = new Met((float) lat, (float) lon,VARA,dt.systemSeconds());
        Observation o = Observation.MetObsDummy(lat, lon, dt, ens.ops);
        for (int typeInt : VARA.metVars) {
            //wind speed is based on N,E components, the direction and speed are post-processed data.
            if (typeInt == VARA.VAR_WINDDIR_DEG || typeInt == VARA.VAR_WINDSPEED) {
                continue;
            }
            KrigingElements ae = new KrigingElements();
            if (ens.datapack.containsVar(typeInt)) {

                if (ens.ops.metFromHighestQualitySource()
                        && typeInt != VARA.VAR_RAIN
                        && typeInt != VARA.VAR_ABLH) {
                    VariableData vd = ens.datapack.getVarData(typeInt);
                    if (vd != null) {
                        Float smooth = vd.getHighestQualityGriddedSmoothValue(dt, lat, lon);
                        if (smooth != null) {
                            met.set(smooth, typeInt);
                            continue;
                        }//if found
                    }//if non-null VD
                }//if data search based on quality

                CombinedValue cv = KrigingAlgorithm.calculateCombinationValue_IWD(typeInt, ens, ae, false, o);  //mapPack is null but this is OK for the iterated met types      
                met.set(cv.value, typeInt);
            }

        }

//check rainCast data which overrides other rain datasets.
        Float rainCast = ens.hashMet.getRainCastValue(lat, lon, dt, ens.ops);
        if (rainCast != null) {
            met.set(rainCast, VARA.VAR_RAIN);
        }
        Float solar =ens.hashMet.getStoreSolarSpecs(dt, lat, lon)[SolarBank.POWER];
        met.fillNullMetVars(solar);
        return met;

    }

    private void processWindComponents(String warningID) {
        Float wn = get(VARA.VAR_WIND_N);
        Float we = get(VARA.VAR_WIND_E);

        Float WDS = get(VARA.VAR_WINDDIR_DEG);
        Float WSS = get(VARA.VAR_WINDSPEED);

        if (wn != null && we != null) {

            float[] dirV = WindConverter.NE_toDirSpeed_from(wn, we, false);
            float dir = dirV[0];
            float wspeed = dirV[1];

            if (dir > 360) {
                dir -= 360;
            } else if (dir < 0) {
                dir += 360;
            }

            set(dir, VARA.VAR_WINDDIR_DEG);
            set(wspeed, VARA.VAR_WINDSPEED);

        } else if (WDS != null && WSS != null) {
            float[] NE = WindConverter.dirSpeedFrom_toNE(WDS, WSS);
            set(NE[0], VARA.VAR_WIND_N);
            set(NE[1], VARA.VAR_WIND_E);
        } else {
            set(3f, VARA.VAR_WIND_N);
            set(0f, VARA.VAR_WIND_E);
            set(0f, VARA.VAR_WINDDIR_DEG);
            set(3f, VARA.VAR_WINDSPEED);
            
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "Met.processWindComponents error: neither the"
                    + " 'N-E-compoments' or 'DIR-speed' were available!" + warningID);
        }

    }

    private void fillNullMetVars( Float solar) {
        processWindComponents("");
        estimateSwRadiation(solar);
        setStabilityClass();
    }

    /**
     * return the stored metVariable with a typeInt 'type'. Uses VarAssociations
     * to get the proper index specifically for these met.variables.
     *
     * @param typeIndex type index as defined in VariableAssociations. Should of
     * meteorological types.
     * @return float value, can be null if this Met instance doesn't hold value
     * of this type.
     */
    public Float get(int typeIndex) {
        return this.metVars[VARA.metI[typeIndex]];
    }
    
    /**
     * Same as the "get() method, but in case the value of this type is not present
     * then the null value of variableAssociations.csv is returned.
     * 
     * @param typeIndex the variable type index
     * @return float value, cannot be null but can be a generic default. 
     */
    public float getSafe(int typeIndex) {
        Float f = this.metVars[VARA.metI[typeIndex]];
        if (f==null) {
            return VARA.getNullValue(typeIndex);
        }
        return f;
    }

    private void set(float f, int typeIndex) {
        //sanity checks
        if (typeIndex == VARA.VAR_RAIN && f > 1000) {
            return;//do not set, will be zero
        } else if (typeIndex == VARA.VAR_SHORTW_RAD && f > 10000) {
            return;//do not set, will use other estimation method based on solar angle and cloudiness   
        }

        this.metVars[VARA.metI[typeIndex]] = f;
    }

    public static String getMetValsHeader(String comma, FusionOptions ops) {
        String s = "";
        for (int i : ops.VARA.metVars) {
            s += ops.VARA.shortName(i) + comma;
        }

        return s;
    }

    public static void addMetValsHeaders(ArrayList<String> arr, FusionOptions ops) {
        VarAssociations VARA = ops.VARA;
        for (int i : VARA.metVars) {
            arr.add(VARA.VAR_NAME(i));
        }
    }

    public String metValsToString(String comma) {
        String s = "";
        for (int i : VARA.metVars) {
            Float mv = this.get(i);
            if (mv != null) {

                s += editPrecision(mv.doubleValue(), 3) + comma;

            } else {
                s += comma;
            }
        }

        return s;
    }

    public String fullPrintOut() {
        String s = "";
        for (int i : VARA.metVars) {
            Float mv = this.get(i);

            if (mv != null) {
                s += VARA.LONG_DESC(i, true) + ":"
                        + editPrecision(mv.doubleValue(), 2) + "\n";
            }
        }
        return s;
    }

    public String metValsToStringWithExpl(String sep) {
        String s = "";
        for (int i : VARA.metVars) {
            Float mv = this.get(i);

            if (mv != null) {
                s += VARA.shortName(i) + "="
                        + editPrecision(mv.doubleValue(), 3) + sep;
            } else {
                s += VARA.shortName(i) + "=N/A" + sep;
            }

        }

        return s;
    }

    public float getWindSpeed_raw() {
        return getSafe(VARA.VAR_WINDSPEED);
    }


    /**
     * Correction (guestimate) for stability class in case of high elevation
     * sources. Assumption: conditions become more stable as a function of
     * height from surface.
     *
     * @param stabiClass stability class at ground level
     * @param h height in meters
     * @return modified stability class
     */
    public static int getStabClass_profileMod(int stabiClass, float h) {

        int stabiAdd = (int) (h / 30);
        return Math.min(STABILITY_CLASS_NAMES.length - 1, stabiClass + stabiAdd);
    }


    private void estimateSwRadiation(Float solar) {
        if (this.get(VARA.VAR_SHORTW_RAD) == null) {
            float solPow = 0;
            if (solar!=null)solPow=solar; 

            float rad = Math.max(0, solPow);
            float fact = 0.2f + 2f / (getCloudCover_nullSafe() + 1); //minimum 0.2 + 2/9 at full overcast.
            if (fact > 1) {
                fact = 1;
            }
            rad *= fact;
            this.set(rad, VARA.VAR_SHORTW_RAD);

        }
    }

    public boolean hasWindData() {
        return (get(VARA.VAR_WIND_N) != null
                && get(VARA.VAR_WIND_E) != null);
    }

    public float getRoadWater_safe() {
        return this.getSafe(VARA.VAR_ROADWATER);
    }

    public float getCloudCover_nullSafe() {
         return this.getSafe(VARA.VAR_SKYCOND);
    }

    public float getTemp_nullSafe() {
        return this.getSafe(VARA.VAR_TEMP);
    }
    
    public float getRH_safe() {
      return getSafe(VARA.VAR_RELHUMID);  
    }

    public float getRain_nullSafe() {
        Float rain = get(VARA.VAR_RAIN);
        if (rain != null) {
            return rain;
        } else {
            return 0f;
        }
    }

    public float totalSolarRadiation() {
        return getSafe(VARA.VAR_SHORTW_RAD);//cannot be null beause an estimate is set in FillNullMetVars
    }
    
    public Float getWdir() {
       return get(VARA.VAR_WINDDIR_DEG); 
    }

    public int getWdirInt() {
        return get(VARA.VAR_WINDDIR_DEG).intValue();
    }
    
    public Float[] allMetVars() {
        return this.metVars;
    }

    public float getABLh_nullSafe() {
       return get(VARA.VAR_ABLH); 
    }

    public int stabClass() {
       return (int)getSafe(VARA.VAR_STABI); 
    }

public float lesWindScaler() {
    return this.getWindSpeed_raw()/8f;//for testing
}





}
