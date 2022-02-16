/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.parametrization.MetFunction2;
import org.fmi.aq.enfuser.ftools.FileOps;


/**
 *
 * @author Lasse Johansson
 */
public class VarAssociations {

//NATIVE types that must be defined===================   
    public final int VAR_PM25;
    private final static String NATIVE_PM25 = "PM25";
    public final int VAR_COARSE_PM;
    private final static String NATIVE_CORSE_PM = "coarsePM";
    public final int VAR_SO2;
    private final static String NATIVE_SO2 = "SO2";
    public final int VAR_O3;
    private final static String NATIVE_O3 = "O3";
    public final int VAR_NO2;
    private final static String NATIVE_NO2 = "NO2";
    public final int VAR_NO;
    private final static String NATIVE_NO = "NO";
    public final int VAR_CO;

    private final static String NATIVE_CO = "CO";
    public final int VAR_PM10;
    private final static String NATIVE_PM10 = "PM10";
    public final int VAR_AQI;
    private final static String NATIVE_AQI = "AQI";
    
//mandatory metVars
    public final int VAR_TEMP;
    private final static String NATIVE_TEMP = "temperature";

    public final int VAR_WIND_N;
    private final static String NATIVE_WIND_N = "wind_N";

    public final int VAR_WIND_E;
    private final static String NATIVE_WIND_E = "wind_E";

    public final int VAR_WINDDIR_DEG;
    public static final String NATIVE_WDIR_DEG = "windDirection";

    public final int VAR_WINDSPEED;
    public static final String NATIVE_WS_DEG = "windSpeed";

    public final int VAR_RAIN;
    public static final String NATIVE_RAIN = "rain";

    public final int VAR_IMO_LEN;
    private final static String NATIVE_IMOLEN = "InvMOlength";

    public final int VAR_ABLH;
    public static final String NATIVE_ABLH = "ABLH";

    public final int VAR_STABI;
    private final static String NATIVE_STABI = "stabilityClass";

    public final int VAR_RELHUMID;
    private final static String NATIVE_HUMID = "humidity";

    public final int VAR_SHORTW_RAD;//new var  
    private final static String NATIVE_SWR = "swRad";

    public final int VAR_ROADWATER;
    private final static String NATIVE_ROADWATER = "roadSurfaceWater";

    public final int VAR_SKYCOND;
    private final static String NATIVE_SKYCOND = "skyCondition";

    private final ArrayList<String[]> rawLines = new ArrayList<>();

    private final ArrayList<String[]> primLines = new ArrayList<>();

    private final ArrayList<String[]> secLines = new ArrayList<>();
    public final ArrayList<Integer> secVars = new ArrayList<>();

    private final ArrayList<String[]> metLines = new ArrayList<>();
    public final ArrayList<Integer> metVars = new ArrayList<>();

    private final HashMap<Integer, String[]> hashedLines = new HashMap<>();//in order
    private final ArrayList<Integer> allVarsArr = new ArrayList<>();
    public final HashMap<String, Integer> assocToType_native = new HashMap<>();
    public final String[] Q_NAMES;
    public final int[] Q;
    private final float[][] VEGEBLOCKS_QM;

    private final float[] nullVars;
    public final boolean[] isMetVar;
    public final boolean[] isPrimary;
    public final boolean[] isSecondary;
    
    public boolean[] hasSecondaryBuildRule;
    public boolean[] naturalLower;
    public final int[] metI;
    public final int METVAR_LEN;//this many mat variables (role = "MET")
    
    //emitter short list indices====
    public final int ELEM_LEN;
    public final int ELEMI_CAT;//em source category
    public final int ELEMI_H;//exact height for modelled emission. IMPORTANT: use negative or zero value to utilize discrete and faster Gaussian weighting functions (ZHWmanager)
    public final int ELEMI_RD;
    //==============================

    public HashMap<String, MetFunction2> metFuncs = new HashMap<>();

    private String[] header;
    private HashMap<String, Integer> headerIndex = new HashMap<>();
    public final String HEADER_VARSTATUS = "var_status";
    public final String HEADER_VARNAME = "var_name";
    public final String HEADER_ROLE = "role";

    public final String HEADER_PRED_PROXY = "prediction_recipee";
    public final String HEADER_INPUT_PROXY = "input_proxy_recipee";
    public final String HEADER_EMITTER_PROXY = "emitter_proxy_recipee";
    public final String HEADER_STATCRUNCH_BREAKPOINTS = "statcruncher_breakpoints";
    public final String HEADER_SETTLING_DISP = "settling_dispersion";

    public final String HEADER_OUTPUT_SKIPS = "output_skips";
    public final String HEADER_DF_SECONDARY = "df_for_secondary";
    
    public final String HEADER_NAMECONV = "name_conventions";
    public final String HEADER_NAME_SHORT = "name_short";
    public final String HEADER_VARUNIT = "unit";
    public final String HEADER_ACCEPTANCE = "accept_range";
    public final String HEADER_BGLEARN = "bglearn_range";
    public final String HEADER_VARDESC = "description";
    public final String HEADER_NULLVALUE = "null_value";
    public final String HEADER_1HSTD = "def_1h_std";
    public final String HEADER_PPM_CONV = "ppm_conversion";
    public final String HEADER_MOLECMASS = "molec_mass";
    public final String HEADER_MASS_REDUCT_H = "mass_reduction_h";
    public final String HEADER_REDUCT_METFUNCS = "mass_decay_metfuncs";
    public final String HEADER_VEGEBLOCKS = "monthly_vegefuncs";
    public final String HEADER_NATLOW = "natural_lower";
    public final String HEADER_CATCAPS = "category_caps";
    
    public final String HEADER_VIS_MINMAX = "visual_minmax";
    public final String HEADER_VIS_MAXMAX = "visual_maxmax";
    
    public final String HEADER_VIS_TAG = "visual_style_id";

    public final String HEADER_ASSOCS = "associations";

    
    public final float[] puffQ_reductionPerH;
    public final ArrayList<MetFunction2>[] QreductionMetFuncs;

    private HashMap<Integer, Double> ppmConversions = new HashMap<>();

    //public final HashMap<String,Integer> assocToType = new HashMap<>();
    public ArrayList<VarConversion>[] assocVars_byType;
    public HashMap<String, VarConversion> allVC_byName = new HashMap<>();

    private final static int ROLE_PRIMARY = 0;
    private final static int ROLE_SEC = 1;
    private final static int ROLE_MET = 2;
    private final static String DISABLED_TAG = "DISABLED";

    //private HashMap<Integer, Float> nullValues = new HashMap<>();
    private HashMap<Integer,double[]> acceptRanges = new HashMap<>();
    
    
    
    
    /**
     * get column index for the specified property
     *
     * @param headerTag tag for which the column index is sought
     * @return integer value specifying where the property can be read. return
     * -1 if the tag has not been found (a crash is imminent and
     * 'variableAssociations.csv' should be modified accordingly)
     */
    private int hInd(String headerTag) {
        Integer ind = this.headerIndex.get(headerTag);
        if (ind == null) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"VarAssociations: "
                    + "COLUMN INDEX NOT FOUND FOR: " + headerTag);
            //this will result in a crash, but the information of the cause
            //is important to printout.
            return -1;
        } else {
            return ind;
        }
    }
    

    public VarAssociations(File f) {
     
        arr = FileOps.readStringArrayFromFile(f);
        this.header = arr.get(0).split(";");
        //parse header
        for (int k = 0; k < header.length; k++) {
            String test = header[k].replace(" ", "").toLowerCase();
            this.headerIndex.put(test, k);
        }

        for (int i = 1; i < arr.size(); i++) {
            String[] line = arr.get(i).split(";");
            String enabled = (line[hInd(HEADER_VARSTATUS)]);

            if (enabled.contains(DISABLED_TAG)) {
                    EnfuserLogger.log(Level.FINE,this.getClass(),
                            "Skipping disabled variable: " + arr.get(i));
                continue;
            }
            int roleDef = -1;
            //check role
            String role = line[hInd(HEADER_ROLE)];
            if (role.contains("PRIM")) {
                roleDef = ROLE_PRIMARY;
                this.primLines.add(line);
            } else if (role.contains("MET")) {
                roleDef = ROLE_MET;
                this.metLines.add(line);
            } else if (role.contains("SEC")) {
                roleDef = ROLE_SEC;
                this.secLines.add(line);
            }

            if (roleDef == -1) {
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "variableAssociations: no proper role definition "
                        + "(PRIMARY,SECONDARY, MET) for " + arr.get(i));
                continue;
            }

            rawLines.add(line);

        }//for lines -> rawLines (that are enabled and role-defined)

        //go over the lines once more, this time with proper INDEXING==============
        this.isMetVar = new boolean[rawLines.size()];
        this.nullVars = new float[rawLines.size()];
        this.isPrimary = new boolean[rawLines.size()];
        this.isSecondary = new boolean[rawLines.size()];
        this.hasSecondaryBuildRule = new boolean[rawLines.size()];

        this.naturalLower = new boolean[rawLines.size()];
        int index = 0;

        //first are the primaries===========
        //Primary variables MUST start from index 0 (that's why dealt first)
        String[] primNames = new String[primLines.size()];
        Q = new int[primNames.length];
        for (String[] line : primLines) {

            this.hashedLines.put(index, line);
            String name = line[hInd(HEADER_VARNAME)];
            this.assocToType_native.put(name, index);
            this.isPrimary[index] = true;
            primNames[index] = name;
            Q[index] = index;

            index++;//update index
        }
        this.Q_NAMES = primNames;

        //then secondaries=============
        for (String[] line : secLines) {

            this.hashedLines.put(index, line);
            String name = line[hInd(HEADER_VARNAME)];
            this.assocToType_native.put(name, index);
            this.secVars.add(index);
            this.isSecondary[index] = true;

            index++;//update index
        }

        //then metVars================
        this.METVAR_LEN = metLines.size();
        this.metI = new int[rawLines.size()];
        int metInd = 0;
        for (String[] line : metLines) {

            this.hashedLines.put(index, line);
            String name = line[hInd(HEADER_VARNAME)];
            this.assocToType_native.put(name, index);
            this.metVars.add(index);
            this.isMetVar[index] = true;

            metI[index] = metInd;

            index++;//update main index
            metInd++;//update met index (used by Met-class)

        }

        //setup native varible indexing (which must exist)
        VAR_PM25 = this.assocToType_native.get(NATIVE_PM25);
        VAR_COARSE_PM = this.assocToType_native.get(NATIVE_CORSE_PM);
        VAR_SO2 = this.assocToType_native.get(NATIVE_SO2);
        VAR_O3 = this.assocToType_native.get(NATIVE_O3);
        VAR_NO2 = this.assocToType_native.get(NATIVE_NO2);
        VAR_NO = this.assocToType_native.get(NATIVE_NO);
        VAR_CO = this.assocToType_native.get(NATIVE_CO);
        VAR_PM10 = this.assocToType_native.get(NATIVE_PM10);
        VAR_AQI = this.assocToType_native.get(NATIVE_AQI);

        VAR_TEMP = this.assocToType_native.get(NATIVE_TEMP);
        VAR_WIND_N = this.assocToType_native.get(NATIVE_WIND_N);
        VAR_WIND_E = this.assocToType_native.get(NATIVE_WIND_E);
        VAR_WINDDIR_DEG = this.assocToType_native.get(NATIVE_WDIR_DEG);
        VAR_WINDSPEED = this.assocToType_native.get(NATIVE_WS_DEG);
        VAR_RAIN = this.assocToType_native.get(NATIVE_RAIN);
        VAR_IMO_LEN = this.assocToType_native.get(NATIVE_IMOLEN);
        VAR_ABLH = this.assocToType_native.get(NATIVE_ABLH);
        VAR_STABI = this.assocToType_native.get(NATIVE_STABI);
        VAR_RELHUMID = this.assocToType_native.get(NATIVE_HUMID);
        VAR_SHORTW_RAD = this.assocToType_native.get(NATIVE_SWR);
        VAR_SKYCOND = this.assocToType_native.get(NATIVE_SKYCOND);
        VAR_ROADWATER = this.assocToType_native.get(NATIVE_ROADWATER);

        //Processes AFTER proper order of variables has been setup=========
        for (Integer ind : hashedLines.keySet()) {
            String conv = hashedLines.get(ind)[hInd(HEADER_NAMECONV)];
            this.assocToType_native.put(conv, ind);
           
            String[] line = this.hashedLines.get(ind);
            String varname = line[hInd(HEADER_VARNAME)];
            
            
            if (!isMetVar[ind]) {
                String ppmC = line[hInd(HEADER_PPM_CONV)];
                if (ppmC != null && ppmC.length() > 0) {
                    try {
                        double d = Double.valueOf(ppmC);
                        this.ppmConversions.put(ind, d);
                            EnfuserLogger.log(Level.FINER,this.getClass(),
                                    "\t PPM conversion added for " + varname);
                        
                    } catch (NumberFormatException e) {

                    }
                }
            }

            String nullVal = line[hInd(HEADER_NULLVALUE)];
            if (nullVal != null && nullVal.length() > 0) {
                double d = Double.valueOf(nullVal);
                this.nullVars[ind] =(float)d;
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "\t\t NullValue Added for " + varname);
                
            }
            
            //acceptance ranges
            String accepts = line[hInd(HEADER_ACCEPTANCE)];
            if (accepts!=null && accepts.contains(",")) {
                try {
                String[] split = accepts.split(",");
                double min = Double.valueOf(split[0]);
                double max = Double.valueOf(split[1]);
                if (min<max) {
                    
                   EnfuserLogger.log(Level.FINER,this.getClass(),
                           "Adding Acceptance Range for "+ varname +": "+min +" to "+ max);
                   this.acceptRanges.put(ind, new double[]{min,max});
                   
                } else {
                    EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "VarAssociations: incorrect format for " + HEADER_ACCEPTANCE+": "
                            + accepts +". This should be 'a,b' where a and b are min,max values as Doubles."); 
                }
                
                } catch (Exception e) {
                    EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                  "VarAssociations: incorrect format for " + HEADER_ACCEPTANCE+": "
                            + accepts +". This should be 'a,b' where a and b are min,max values as Doubles.");
                }
            }
            
        }//for index

        for (int b = 0; b < this.hashedLines.size(); b++) {
            this.allVarsArr.add(b);
        }

        //time to define all associations
        this.setVarAssociations();

        this.puffQ_reductionPerH = new float[Q_NAMES.length];
        this.QreductionMetFuncs = new ArrayList[Q_NAMES.length];

        for (int i = 0; i < Q_NAMES.length; i++) {
            this.QreductionMetFuncs[i] = new ArrayList<>();
            String[] line = this.hashedLines.get(i);
            float val_h = Double.valueOf(line[hInd(HEADER_MASS_REDUCT_H)]).floatValue();
            puffQ_reductionPerH[i] = val_h;
            //at this point MetFunctions cannot be read because both FusionOptions.CATS and FusionOptions.VARA needs to be initiated. This process is done after FusionCore init.
        }

        int meti = 0;
        for (int i = 0; i < metI.length; i++) {
            if (isMetVar[i]) {
                metI[i] = meti;
                meti++;
            }
        }
        //vegeblocks
        this.VEGEBLOCKS_QM = new float[Q_NAMES.length][12];

        for (int i = 0; i < Q_NAMES.length; i++) {

            String[] line = this.hashedLines.get(i);
            String mveges = (line[hInd(HEADER_VEGEBLOCKS)]);

            mveges = mveges.replace(" ", "");
            String[] months = mveges.split(",");
            for (int m = 0; m < 12; m++) {
                this.VEGEBLOCKS_QM[i][m] = Double.valueOf(months[m]).floatValue();
            }
        }

        //now the rest that is left
        for (int i = 0; i < this.naturalLower.length; i++) {
            this.naturalLower[i] = true;
            try {
                String[] line = this.hashedLines.get(i);
                String natLow = line[hInd(HEADER_NATLOW)].toLowerCase();
                if (natLow.contains("false")) {
                    this.naturalLower[i] = false;
                }
            } catch (Exception e) {

            }
        }
        
        this.readVariableMetFuncs(GlobOptions.get().ROOT);
        
    //emitter list indices    
    ELEMI_CAT = Q_NAMES.length;//em source category
    ELEMI_H = Q_NAMES.length + 1;//exact height for modelled emission. IMPORTANT: use negative or zero value to utilize discrete and faster Gaussian weighting functions (ZHWmanager)
    ELEMI_RD =Q_NAMES.length + 2;
    ELEM_LEN = Q_NAMES.length + 3;
    }
    
    
    public ArrayList<Integer> getTypeInts(ArrayList<String> types,
            boolean warning, String id) {

        ArrayList<Integer> ar = new ArrayList<>();
        for (String type : types) {
            int typeI =getTypeInt(type, warning, id);
            if (typeI>=0) ar.add(typeI);
        }

        return ar;
    }
        
        /**
         * Get a list of all modelling variables, selectively for primary types
         * (P), secondary (S) and met.variables (M).
         * @param prim if true adds primaries
         * @param sec if true adds secondary variables
         * @param met if true adds met.vars.
         * @return list of variable names.
         */
        public ArrayList<String> allVarsList_PSM(boolean prim, boolean sec, boolean met) {
        ArrayList<String> ar = new ArrayList<>();
        for (int i : allVars()) {

            if (isMetVar[i] && met) {
                ar.add(VAR_NAME(i));
            } else if (isSecondary[i] && sec) {
                ar.add(VAR_NAME(i));
            } else if (isPrimary[i] && prim) {
                ar.add(VAR_NAME(i));
            }

        }
        return ar;
    }
        
    public int[] getIndexArrayFromHeader(String[] splitline) {
        int[] inds = new int[splitline.length];
        for (int i = 0; i < inds.length; i++) {
            int ti = getTypeInt(splitline[i], true, "");
            inds[i] = ti;
        }
        return inds;
    }

    public String[] ALL_VAR_NAMES() {
        String[] types = new String[allVars().size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = VAR_NAME(i);
        }
        return types;
    }

    public ArrayList<String> allSpeciesList() {
        ArrayList<String> arr = new ArrayList<>();
        for (String var : Q_NAMES) {
            arr.add(var);
        }
        return arr;
    }    

    public float getNullValue(int typeInt) {
        return this.nullVars[typeInt];//this.nullValues.get(typeInt);
    }

    public Double getPPMconverter(int typeInt) {
        return this.ppmConversions.get(typeInt);
    }

    public int VAR_LEN() {
        return this.hashedLines.size();
    }

    public ArrayList<Integer> allVars() {
        return this.allVarsArr;
    }

    public Integer varInt(String var) {
        return this.assocToType_native.get(var);
    }

    public Integer getVarInt_all(String var) {
        if (var.contains(" ")) {
            var = var.replace(" ", "");
        }
        VarConversion vc = this.allVC_byName.get(var);
        if (vc == null) {
            return null;
        }
        return vc.typeInt;
    }

    private ArrayList<String> arr;

    private int readVariableMetFuncs(String ROOT) {
        String funcs = ROOT + "data/metFunctions2.csv";
        this.metFuncs = MetFunction2.readFunctionsFromFile(funcs, this);

        for (int q : Q) {
            this.QreductionMetFuncs[q] = new ArrayList<>();
            String[] line = this.hashedLines.get(q);
            float val_h = Double.valueOf(line[hInd(HEADER_MASS_REDUCT_H)]).floatValue();
            puffQ_reductionPerH[q] = val_h;
            String mfuncs = (line[hInd(HEADER_REDUCT_METFUNCS)]);

            if (mfuncs.length() > 1) {
                String[] split = mfuncs.split(",");
                for (String mfID : split) {
                    MetFunction2 mf = this.metFuncs.get(mfID);
                    if (mf != null) {
                            EnfuserLogger.log(Level.FINEST,this.getClass(),"VariableAssociations: Added mass "
                                    + "reduction metFunction " + mf.reserveID + " for " + Q_NAMES[q]);
                        
                        this.QreductionMetFuncs[q].add(mf);
                    }
                }
            }

        }
        return 0;
    }

    public String VAR_NAME(int typeInt) {
        return this.hashedLines.get(typeInt)[hInd(HEADER_VARNAME)];
    }

    public float molecMass(int typeInt) {
        String mm = this.hashedLines.get(typeInt)[hInd(HEADER_MOLECMASS)];
        return Double.valueOf(mm).floatValue();
    }
    
    public boolean IS_METVAR(int i) {
        return this.isMetVar[i];
    }

    public String LONG_DESC(int typeInt) {
        return LONG_DESC(typeInt, true);
    }
    
    public String LONG_DESC(int typeInt, boolean units) {

        String desc = this.hashedLines.get(typeInt)[hInd(HEADER_VARDESC)];
        if (units) {
            desc += ", [" + this.hashedLines.get(typeInt)[hInd(HEADER_VARUNIT)] + "]";
        }
        return desc;

    }
    
    public HashMap<String, String[]> getPredictionInputRecipees() {
       HashMap<String, String[]> hash = new HashMap<>();
       for (int i =0;i<this.metVars.size();i++) {
           String pred = this.hashedLines.get(i)[hInd(HEADER_PRED_PROXY)];
           String inp =  this.hashedLines.get(i)[hInd(HEADER_INPUT_PROXY)];
           
           if (pred!=null && !pred.contains("<")) pred = null;
           if (inp!=null && !inp.contains("<")) inp = null;
           
           if (pred==null && inp==null) continue;
           String name = this.VAR_NAME(i);
           hash.put(name, new String[]{pred,inp});
           EnfuserLogger.log(Level.FINER,this.getClass(),"getPredictionInputRecipees: added "+ name +", "+pred +","+inp);
       }
       
      return hash;
    }
    

    public String VARNAME_CONV(int typeInt) {
        return this.hashedLines.get(typeInt)[hInd(HEADER_NAMECONV)];
    }

    public String UNIT(int typeInt) {
        return this.hashedLines.get(typeInt)[hInd(HEADER_VARUNIT)];
    }
    
        public boolean outputContentSkipped(int typeInt, String content) {
        String skips= this.hashedLines.get(typeInt)[hInd(HEADER_OUTPUT_SKIPS)];
        if (skips==null) return false;
        return skips.contains(content);
    }

    /**
     * Return a list of all possible var type associations and their scaler and
     * offset values.
     *
     * @param print
     */
    private void setVarAssociations() {

        this.assocVars_byType = new ArrayList[this.hashedLines.size()];
        // add default naming conventions
        for (Integer i : this.hashedLines.keySet()) {
            this.assocVars_byType[i] = new ArrayList<>();
            VarConversion vc = new VarConversion(VAR_NAME(i), i, VAR_NAME(i), 1, 0);
            this.assocVars_byType[i].add(vc);
            this.allVC_byName.put(VAR_NAME(i), vc);
            this.allVC_byName.put(VARNAME_CONV(i), vc);
            
            String assoc = null;

            try {
                String[] line = this.hashedLines.get(i);
                assoc = line[hInd(HEADER_ASSOCS)];//6th element contains type assocs in variableAssociations.csv

                String[] split = assoc.split("&");

                for (String elem : split) {
                    try {
                        elem = elem.replace(" ", "");
                        if (elem.length() < 1) {
                            continue;
                        }

                        double scaler = 1f;
                        double add = 0;
                        String specName = elem;
                        if (elem.contains(",")) {//has also scaler and add_offset
                            String[] multi = elem.split(",");
                            specName = multi[0];
                            scaler = Double.valueOf(multi[1]);
                            add = Double.valueOf(multi[2]);
                        }

                        this.assocVars_byType[i].add(new VarConversion(specName, i, VAR_NAME(i), scaler, add));
                        if (this.allVC_byName.get(specName) == null) {
                                EnfuserLogger.log(Level.FINER,this.getClass(),
                                        "varConversion added: '" + specName + "', for '" + VAR_NAME(i) 
                                                + "', with scale/add " + scaler + ", " + add);
                            
                        }

                        this.allVC_byName.put(specName, new VarConversion(specName, i, VAR_NAME(i), scaler, add));
                    } catch (Exception e) {
                        EnfuserLogger.log(e,Level.SEVERE,this.getClass(),
                                "Parsing exception encountered in variableAssociations.csv - "
                                + "'associations' variableName= " + VAR_NAME(i) + ", element=" + elem);
                    }

                }//for elems

            } catch (Exception e) {
                EnfuserLogger.log(e,Level.SEVERE,this.getClass(),
                        "GetVarAssocs error: " + assoc + ", type = " + VAR_NAME(i));
                e.printStackTrace();

            }

        }//for lines

    }

    
    public final static double MV_BASE_KELVINS = 298.15;
    public final static double MOLAR_VOL_BASE = 24.45 / MV_BASE_KELVINS;

    public float ppmTo_µgm(float ppm, int typeInt, Float temperature_C) {
        //for simplicity the pressure dependency is ignored since the effect of pressure is at best 3% approx.
        //Vm = R/P * T
        float T = 273.15f;
        if (temperature_C != null) {
            T = temperature_C + 273.15f;
        }
        float Vm = (float) MOLAR_VOL_BASE * T;

        return (float) (ppm * molecMass(typeInt) / Vm);

    }
        public double convertPPM_table(int typeInt, double value) {
        Double d = getPPMconverter(typeInt);
        if (d != null) {
            return value * d; //WHO, 25C, 1013mb
        } else {
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "PPM conversion: type not supported! " + VAR_NAME(typeInt));
            return value;
        }
    }
    
    public int getTypeInt(String type) {
        return getTypeInt(type, false, null);
    }
    
    /**
     * This method parses the given variable type as text and returns an index
     * for a known variable type. In case the String is NOT identified this returns
     * -1. NOTE: the '-1' can easily cause IndexOutOfBoundsExceptions if not
     * dealt accordingly.
     * @param s the variable name as text
     * @param warning if true, then a warnings log is given (FINE) if the parse fails.
     * @param id additional source description for a possible warning log,
     * for example a file name.
     * @return 
     */
    public int getTypeInt(String s, boolean warning, String id) {
        VarConversion vc = this.allVC_byName.get(s);
        if (vc == null) {
            if (warning) {
                EnfuserLogger.log(Level.FINE,this.getClass(),
                        "VAR_TYPE not identified for '" + s + "', ID = " + id);
            }
            return -1;
        }
        return vc.typeInt;
    }

    public Double visualMinMax(Integer key) {

        String s = this.hashedLines.get(key)[hInd(HEADER_VIS_MINMAX)];
        try {
            return Double.valueOf(s);
        } catch (NullPointerException | NumberFormatException e) {
          return null;
        }
    }
        public Double visualMaxMax(Integer key) {

        String s = this.hashedLines.get(key)[hInd(HEADER_VIS_MAXMAX)];
        try {
            return Double.valueOf(s);
        } catch (NullPointerException | NumberFormatException e) {
          return null;
        }

    }
    
    /**
     * Apply maximum rules for visualization for the given pollutant type
     * and an initial [min,max] range.
     * @param key typeIndex
     * @param mm initial min,max range for the visualization
     * @return adjusted min-max range.
     */    
    public double[] applyVisualizationLimits(Integer key, double[] mm) {
        Double minmax = this.visualMinMax(key);
        if (minmax!=null) {
            if(mm[1] < minmax) mm[1] =minmax;
        }
        Double maxmax = this.visualMaxMax(key);
        if (maxmax!=null) {
            if (mm[1]> maxmax) mm[1]=maxmax;
        }
        return mm;
    }    
    

    public String shortName(int i) {
        return this.hashedLines.get(i)[hInd(HEADER_NAME_SHORT)];
    }
   
   private ConcurrentHashMap<Integer, double[]> catcaps= new ConcurrentHashMap<>(); 
   public double[] categoryCaps(int i, Categories CATS) {
       
       double[] existing = this.catcaps.get(i);
       
       if (existing == null) {
        existing = new double[CATS.C.length];//init mith max values - same as no effect.
        for (int c :CATS.C) {
            existing[c] = Double.MAX_VALUE;
        }   
           
        int ind = hInd(HEADER_CATCAPS);
        if (ind >=0) {
            
             String cont =this.hashedLines.get(i)[ind];
             if (cont.contains("=")) {
                 existing = new double[CATS.C.length];
                for (int c :CATS.C) {
                    existing[c] = Double.MAX_VALUE;
                }
                 String[] temp = cont.split(",");
                 for (String s:temp) {
                     String[] cat_val = s.split("=");
                     String cat = cat_val[0];
                     double val = Double.valueOf(cat_val[1]);
                     int c = CATS.indexFromName(cat);
                     if (c>=0) {
                         existing[c]=val;
                         EnfuserLogger.log(Level.INFO, "Category limit parsed: "
                                 + this.VAR_NAME(i) +", "+ cat +" => "+ val);
                     }//if known category
                     else {
                        EnfuserLogger.log(Level.INFO, "UNKNOWN Category limit encountered! "
                                 + this.VAR_NAME(i) +", "+ cat); 
                     }
                 }//for elements, e.g., traffic=100
             }//has actual parameters (contains =)
        }//if valid header 
               
        //put for later use
        this.catcaps.put(i, existing);  
        
       }//if null => assessment
       return existing;
    }

    public float getPuffReductionFactor(int q, Met met, float hours) {

        float base = hours * this.puffQ_reductionPerH[q];
        for (MetFunction2 mf : this.QreductionMetFuncs[q]) {
            base *= mf.getMetScaler(met);
        }
        if (base >0.9) base = 0.9f;
        return base;
    }

    public float monthlyVegetationReduct(int typeInt, Dtime dt) {
        return this.VEGEBLOCKS_QM[typeInt][dt.month_011];
    }

    public int varLen() {
        return this.isMetVar.length;
    }

    public boolean naturalLower(int typeInt) {
        return this.naturalLower[typeInt];
    }

    public boolean hasSecondaryBuildRule(int typeInt) {
        if (this.hasSecondaryBuildRule[typeInt]) {
            //TODO: implement.
            return false;
        } else {
            return false;
        }
    }

    public String[] metVariableNames() {
       String[] names = new String[this.metVars.size()];
       for (int i =0;i<this.metVars.size();i++) {
           names[i] = this.VAR_NAME(this.metVars.get(i));
       }
       return names;
    }

    public double[] getAcceptRange(int typeInt) {
        return this.acceptRanges.get(typeInt);
    }

    public Integer shortMetNameToIndex(String head) {
        for (int i =0;i<this.isMetVar.length;i++) {
          if (!this.isMetVar[i]) continue;
          String shortn = shortName(i);
          if (shortn.equals(head)) return i;
       }
        return null;
    }

    /**
     * Fetch the String property for the given variable type, at the column
     * specified.
     * @param q the type
     * @param HEADER the column identifier
     * @return String setting at the specified cell.
     */
    public String getVariablePropertyString(int q, String HEADER) {
        String[] split = hashedLines.get(q);
        if (split==null) return null;
        int index = this.hInd(HEADER);
        if (index<0) return null;
        return split[index] + "";
    }
    
    /**
     * Return a set of breakpoints for meteorological StatsCruncher analysis.
     * For example: for wind speed there can be a String '1,2,3,4' and 
     * this would cause conditional averages to be computed for these wind speed
     * 'buckets'.
     * @param q typeInt
     * @return parsed vector of breakpoint doubles. Returns null if no such
     * info has been set for the type.
     */
    public double[] getStatsCrunchBreakPoints(int q) {
        
        if (!this.isMetVar[q]) return null;
        String cont = this.getVariablePropertyString(q, this.HEADER_STATCRUNCH_BREAKPOINTS);
        if (cont==null|| !cont.contains(",")) return null;
        
        //content found. parse as doubles.
        cont = cont.replace(" ", "");
        String[] sp = cont.split(",");
        double[] breaks = new double[sp.length];
        for (int i =0;i<breaks.length;i++) {
            breaks[i] = Double.valueOf(sp[i]);
        }
        return breaks;
    }

    /**
     * Find the variable index that has the corresponding conventions name 
     * within the given String
     * @param var the String for matching
     * @return type index. Returns -1 if none applicable is found.
     */
    public int parseTypeConventions(String var) {
        int vari =-1;
        for (int i =0;i< this.VAR_LEN();i++) {
            String conv = this.VARNAME_CONV(i);
            if (var.contains(conv)) {
                vari =i;
            }
        }
        return vari;
    }

    private final static float[] DEF_BGLEARN = {0,0};
    /**
     * Return data fusion gradual learning limits for background concentrations
     * for the given primary variable type index.
     * If the type index is not primary, returns [0,0] that means, no additive
     * offset is allowed. This is also returned if no value has been specified
     * with label 'bglearn_range'.
     * @param q variabe type index
     * @return [min,max] range where the minimum is -1 times the specified value.
     */
    public float[] getBackgroundLearningLimits(int q) {
       if (!this.isPrimary[q]) return DEF_BGLEARN;
        String cont = this.getVariablePropertyString(q, this.HEADER_BGLEARN);
        if (cont ==null) return DEF_BGLEARN;
        double val = Double.valueOf(cont);
        float fval = (float)Math.abs(val);
        return new float[]{-fval,fval};
    }

    String getVisualizationStyleID(int typeInt) {
        return this.getVariablePropertyString(typeInt, this.HEADER_VIS_TAG);
    }


}
