/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.varproxy;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.options.RegionalFileSwitch;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.parametrization.USepa;

/**
 *This class is for the computation of the Air Quality Index (AQI).
 * Used by the AQIprox, this class can compute the AQI as a function of
 * other available pollutant species.
 * 
 * Special computation rules may apply based on e.g., surface properties (indoor AQI).
 * 
 * As default the computations should use the 'maximum principle' meaning that
 * the maximum AQI index of any pollutant species defines the overall AQI.
 * The basic principle is to define a simple curved function for all pollutant species
 * with three parameters: MIN, MAX and CURVER.
 * The MIN value defines the pollutant concentration in which equal or below
 * the minimum AQI is obtained for pollutant species. The MAX is for the higher
 * end respectively. the CURVER defines the progression of the function from MIN
 * to MAX. A value of 1 results in a linear progression.
 * 
 * @author johanssl
 */
public class AQI {

    public static final int AQI_BYMAX = 0;
    public static final int AQI_SMOOTHW = 1;
    public static final int AQI_EQUAL_W = 2;

    public static final String[] AQI_NAMES = {"AQI_BYMAX", "AQI_MAX_SMOOTH", "AQI_EQUAL_W"};

    HashMap<Integer, double[]> curveInfo = new HashMap<>();

    public final static String CAL_METHOD = "CAL_METHOD";
    public final static String AQI_RANGE = "AQI_RANGE";
    public final static String BUILDING_VALUE = "BUILDING_VALUE";
    public final static String US_EPA = "US_EPA_CONVENTION";

    private final static int AQI_IND_MIN = 0;
    private final static int AQI_IND_MAX = 1;
    private final static int AQI_IND_CURVER = 2;

    public float MIN_VAL = 1f;
    public float MAX_VAL = 5f;
    public Float BUILDING_VAL = null;
    private int calMeth = 0;
    private boolean usEpa = false;
    private final USepa conv;
 
    
    public AQI(FusionOptions ops) {
        VarAssociations VARA = ops.VARA;
        
        EnfuserLogger.log(Level.FINER,this.getClass(),"Initializing AQIdef...");
        this.conv = new USepa(VARA);
        File f = RegionalFileSwitch.selectFile(ops, RegionalFileSwitch.AQI_FILE);
        ArrayList<String> arr = FileOps.readStringArrayFromFile(f);

        for (int i = 1; i < arr.size(); i++) {//header is not being read
            String[] split = arr.get(i).split(";");
            if (split[0].contains("argument")) {

                if (split[1].contains(CAL_METHOD)) {
                    calMeth = Integer.valueOf(split[2]);
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "\t calculation method = " + AQI_NAMES[calMeth]);
                    

                } else if (split[1].contains(AQI_RANGE)) {
                    double min = Double.valueOf(split[2]);
                    MIN_VAL = (float) min;
                    double max = Double.valueOf(split[3]);
                    MAX_VAL = (float) max;

                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "\t range = " + min + "-" + max);
                } else if (split[1].contains(BUILDING_VALUE)) {
                    double bv = Double.valueOf(split[2]);
                    BUILDING_VAL = (float) bv;
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "\t building value = " + BUILDING_VAL);
                    
                } else if (split[1].contains(US_EPA)) {
                    try {
                        String value = split[2].toLowerCase();
                        if (value.contains("true")) {
                    this.usEpa = true;
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "\t US-EPA AQI convention = " + this.usEpa);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } else {//type definitions

                String type = split[0];
                int typeI = VARA.getTypeInt(type,true,"AQIdef.csv");
                if (typeI<0) continue;
                double min = Double.valueOf(split[1]);
                double max = Double.valueOf(split[2]);
                double curver = Double.valueOf(split[3]);

                curveInfo.put(typeI, new double[]{min, max, curver});

            }

        }
    }
    
    public float getMinimum() {
        if (this.usEpa)return 0;
        return this.MIN_VAL;  
    }
    
   public float getMaximum() {
        if (this.usEpa)return 500;
        return this.MAX_VAL;  
    }
    

    /**
     * Given an array of pollutant concentrations (indexed as in variableAssociations)
     * compute the overall AQI. In case a value is null in the array, the type
     * of that index will be ignored.
     * @param vals the concentration values (of which one or more can be null)
     * @param osm location surface type (in case a building special rules may apply)
     * @return AQI value.
     */
    public float getWeightedAQI(Float[] vals, byte osm) {

        if (OSMget.isBuilding(osm) && BUILDING_VAL != null) {
            return BUILDING_VAL;
        }

        ArrayList<Float> aqis = new ArrayList<>();
        float byMax = -1;
        for (int i = 0; i < vals.length; i++) {

            if (this.curveInfo.get(i) == null) {
                continue;  //this type does not have AQI curves     
            }
            if (vals[i] == null) {
                continue;
            }

            float aq_i = getAQI(i, vals[i]);
            if (aq_i > byMax) {
                byMax = aq_i;
            }
            aqis.add(aq_i);

        }

        if (this.calMeth == AQI_BYMAX) {
            return byMax;//largest index can be returned at this point, if BY MAX.
        }

        float aq = 0;
        float[] w = new float[aqis.size()];

        float sum = 0;
        for (int i = 0; i < aqis.size(); i++) {

            if (this.calMeth == AQI_SMOOTHW) {
                w[i] = aqis.get(i);
                sum += w[i];
            } else if (this.calMeth == AQI_EQUAL_W) {
                w[i] = 1.0f / w.length;
                sum += w[i];
            }
        }

        for (int i = 0; i < aqis.size(); i++) {
            aq += aqis.get(i) * w[i] / sum;
        }
        return (aq);
    }

   /**
    * Get a raw AQI value for the given pollutant species type.
    * @param typeInt the species type index
    * @param value the concentration value
    * @return computed AQI based on MIN, MAX and CURVER values.
    */
    private float getAQI(int typeInt, float value) {
        double[] scale_vals = this.curveInfo.get(typeInt);

        if (this.usEpa) {
            return conv.convertCons_toAQI(value, typeInt);
        }

        float refMax = (float) scale_vals[AQI_IND_MAX];// reference value for maximum AQI index 5.
        float refMin = (float) scale_vals[AQI_IND_MIN];// reference value for minimum AQI index 1.
        float range = refMax - refMin;
        double curver = scale_vals[AQI_IND_CURVER];//e.g. 1.3 which means that index increases more slowly in the lower end (O3). 1 => linear progression. curver < 1 => fast progression in the lower end (pm2.5) 

        if (value > refMax) {
            value = refMax;//index cannot go above the reference max. AQI will be 1+ 4 = 5.
        }
        if (value < refMin) {
            value = refMin;//AQI will be 1 + 0 =1.
        }
        float relative = (value - refMin) / range;// if value > maxVal this is 1.0.
//curve
        float curvedRelative = (float) Math.pow(relative, curver);

        float aq = MIN_VAL + (MAX_VAL - MIN_VAL) * curvedRelative;
        return aq;
    }

    public float getWeightedAQI(Observation ob, FusionCore ens) {
        RP env = ob.incorporatedEnv;
        Float[] Q = new Float[ens.ops.VARA.Q.length];
        for (int q : ens.ops.VARA.Q) {
            
            Q[q] = env.getExpectance(q, ens);
        }
        byte osm = env.groundZero_surf;
        return this.getWeightedAQI(Q, osm);
    }

/**
     * Creates a natural colorGradient recipee that is somewhere in between
     * green - yellow - red - purple.
     *
     * @param minMax observed minMax range.
     * @return a String 'recipee' that defines the HSB-patterns used for the
     * color gradient. This recipee is utilized and interpreted by FigureData
     * class under essentials.plotterlib.
     */
    public String getCustomNaturalPalette(double[] minMax) {

        float refMax = MAX_VAL;
        float refMin = MIN_VAL;
        if (BUILDING_VAL != null && BUILDING_VAL < refMin && minMax[0] < refMin) {
            refMin = BUILDING_VAL;
        }

        //e.g. min = 2 = minMax[0], max =3 = minMax[1];
        double h1 = (minMax[0] - refMin) / (refMax - refMin); // 2-1 / 5-1 = 0.25
        if (h1 < 0) {
            h1 = 0;
        }
        if (h1 > 1) {
            h1 = 1;
        }

        double h2 = (minMax[1] - refMin) / (refMax - refMin); // 3-1 / 5 -1 = 0.4
        if (h2 < 0) {
            h2 = 0;
        }
        if (h2 > 1) {
            h2 = 1;
        }

        String s = h1 + ",1,1_" + h2 + ",1,1_natural";
        return s;

    }    
    
    
}
