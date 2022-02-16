/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.parametrization;

import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.ftools.CurverFunction;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.GlobOptions;

/**
 * This class represents a meteorological function than can be used to multiply
 * all kinds of estimations. It has a specific meteorological variable e.g.
 * temperature. Then as a function this met. variable the function
 * defines a curve starting from MIN to MAX. The function is monotonic
 * (always decreases or increases) but it may not be a linear function.
 * 
 * The function also has a specific application range. In case the meteorological
 * value is e.g., lower the minimum applicable value is inserted to the function.
 * 
 * 
 * @author johanssl
 */
public class MetFunction2 {

    public final String reserveID; //associate this metFunction to certain grid or point emitter based on this ID.
    private final CurverFunction cf;

    //public final int typeInt;//variable type that is being operated on.
    boolean unknownType = false;
    public final int metTypeInt;//met variable type that is being operated on.

    public final float notAvailable_metval;

    private final static int PRECALC_N = 100;
    private final float[] precalcs = new float[100];
    private final float precalc_incr;

    public final static int I_RESERVE_ID = 0;
    public final static int I_TYPE = 1;//no longer needed
    public final static int I_METVAR = 2;
    //numerics
    public final static int I_METVAR_START = 3;
    public final static int I_METVAR_END = 4;
    public final static int I_METVAR_NA = 5;
    public final static int I_FUNCVAL_START = 6;
    public final static int I_FUNCVAL_END = 7;
    public final static int I_CURVER = 8;

    public MetFunction2(String s, Integer forcedType, VarAssociations vara) {
        String[] temp = s.split(";");

        // 0 ID
        this.reserveID = temp[I_RESERVE_ID].replace(" ", "");

        //metVariable 2
        String metType = temp[I_METVAR];
        this.metTypeInt = vara.getTypeInt(metType, true, s);
        if (metTypeInt==-1) this.unknownType = true;
        
        //3 numerics
        double minMetVal = Double.valueOf(temp[I_METVAR_START]);
        double maxMetVal = Double.valueOf(temp[I_METVAR_END]);
        this.notAvailable_metval = Double.valueOf(temp[I_METVAR_NA]).floatValue();
        double min = Double.valueOf(temp[I_FUNCVAL_START]);
        double max = Double.valueOf(temp[I_FUNCVAL_END]);
        double curver = Double.valueOf(temp[I_CURVER]);

        this.cf = new CurverFunction(min, max, curver, new float[]{(float) minMetVal, (float) maxMetVal});
        this.precalc_incr = cf.valueRange / PRECALC_N;

        this.initPreCalc();
    }

    public void printoutRanges(VarAssociations vara) {

        String varname = "anyType";
        String header = "METFUNC_" + this.reserveID + "_" + varname + "_" 
                + vara.VAR_NAME(this.metTypeInt) + ";";
        String valString = "PRECALC_FUNC_VALUE;";
        String valString2 = "FUNC_VALUE;";

        for (float mfval : testRange()) {
            header += mfval + ";";
            float val = getMetScaler(mfval);
            float valPre = fromPreCalc(mfval);
            valString += valPre + ";";
            valString2 += val + ";";
        }

        EnfuserLogger.log(Level.FINER,this.getClass(),header);
        EnfuserLogger.log(Level.FINER,this.getClass(),valString);
        EnfuserLogger.log(Level.FINER,this.getClass(),valString2);
        EnfuserLogger.log(Level.FINER,this.getClass(),"\n");

    }

    private void initPreCalc() {

        for (int ind = 0; ind < PRECALC_N; ind++) {
            float metValue = this.cf.valueSpace_min + this.precalc_incr * ind;
            float value = this.assessMetScaler(metValue);
            this.precalcs[ind] = value;
        }

    }

    protected float fromPreCalc(float value) {
        int ind = (int) ((value - this.cf.valueSpace_min) / this.precalc_incr);
        if (ind < 0) {
            ind = 0;
        } else if (ind >= PRECALC_N - 1) {
            ind = PRECALC_N - 1;
        }
        return this.precalcs[ind];
    }

    public float getMetScaler(Met met) {
        
        Float f = null;
        if (met!=null)f=met.get(metTypeInt);
        
        if (f == null) {
            return fromPreCalc(notAvailable_metval);
        } else {
            return fromPreCalc(f);
        }
    }

    private float getMetScaler(float metval) {
        return assessMetScaler(metval);
    }

    private float assessMetScaler(float metValue) {
        return cf.getValue(metValue);
    }

    ArrayList<Float> testRange() {
        float inc = (float) (this.cf.valueSpace_max - this.cf.valueSpace_min) / 20;
        ArrayList<Float> vals = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            float current = (float) this.cf.valueSpace_min + i * inc;
            vals.add(current);
        }
        return vals;
    }

    public static HashMap<String, MetFunction2> readFunctionsFromFile(
            String filename, VarAssociations vara) {
        
        ArrayList<String> arr = FileOps.readStringArrayFromFile(filename);

        HashMap<String, MetFunction2> hash = new HashMap<>();
        int i = 0;
        for (String line : arr) {
            i++;
            if (i == 1) {
                continue;//header
            }
            try {
                MetFunction2 mf = new MetFunction2(line, null, vara);
                if (mf.unknownType) {
                    EnfuserLogger.log(Level.WARNING, MetFunction2.class,
                            "MetFunction uses a variable type that is unknown: "+ line);
                }
                hash.put(mf.reserveID, mf);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return hash;
    }

    public static HashMap<String, MetFunction2> readFunctions(ArrayList<String> lines,
            VarAssociations vara) {
        HashMap<String, MetFunction2> hash = new HashMap<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);

            try {
                String tester = line.replace(";", "");
                tester = tester.replace(" ", "");

                if (tester.length() < 7) {
                    continue;//empty line
                }
                MetFunction2 mf = new MetFunction2(line, null, vara);
                if (mf.unknownType) {
                    EnfuserLogger.log(Level.WARNING, MetFunction2.class,
                            "MetFunction uses a variable type that is unknown: "+ line);
                }
                if (GlobOptions.allowFinestLogging()) {
                    mf.printoutRanges(vara);
                }
                hash.put(mf.reserveID, mf);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }//for metfunctions to be added

        return hash;
    }

}
