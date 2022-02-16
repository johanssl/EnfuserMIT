/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.parametrization;

import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.flowestimator.AreaFilter;
import java.io.File;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import static org.fmi.aq.enfuser.customemitters.AbstractEmitter.BILLION;
import org.fmi.aq.enfuser.customemitters.OsmLocTime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;

/**
 * The names comes from Light Temporal Meteorological profile (LTM profile).
 * There are historical reasons for this: (its lighter than its predecessors
 * and it handles temporal and meteorological scaling factors.
 * 
 * The purpose of it is to sequence emission releases based on a emission
 * inventory that it is attached to - and each emitter must have an LTM-profile
 * to do this. 
 * 
 * In simplest form, an emission inventory is a static annual gridded dataset
 * and LTM will be able to sequence emissions in terms of ug/s for any given
 * time. The profile itself is a CSV-file that defines these parameters
 * in format that makes it possible to manually edit them relatively easily.
 * 
 * Besides the temporal profiling the CSV files also carry some supporting
 * attributes that can be used to further customize the behavior of emitters.
 * These include a) emission total normalizing, b) emission content sparsing
 * c) represent the inventory with separate components that behave differently (subs)
 * 
 *
 * @author johanssl
 */
public class LightTempoMet {

    public float[] masterCoeffs_q;
    public boolean[] has_q;
    public TemporalFunction tempo;
    public ArrayList<MetFunction2>[] mfs;
    private boolean hasMetFunctions = false;
    public float puffGenIncreaseF = 1f;
    public float distanceRamp_m = 0;
    public final File file;
 
    public String identifier = "def";
    
    public ArrayList<LightTempoMet> subs = null;
     public ArrayList<File> subFiles = null;
     
    public HashMap<Byte, Double> osmRuleSets = null;
    public double osmElseCondition = 0;
    public boolean hasOSMconditions = false;

    public int emissionCellSparser = 1;
    public boolean hasSpatialDifferences = false;
    private final boolean subLTM;
    public AreaFilter afilt = null;
    private Double normalizingSum=null;
    private final VarAssociations VARA;
    private HashMap<String,String[]> keyValues = new HashMap<>();
    

    public final static String GENINC = "PUFFGEN_INCREASER";
    public final static String NORMALIZE_TO_SUM = "NORMALIZE_TO_SUM";
    public final static String SWITCH_SUM = "SWITCH_SUM<";
    public final static String ID = "IDENTIFIER";
    
    public final static String SUB_LTM = "SUB_LTM";
    public final static String DIST_RAMP = "PUFF_DISTRAMP_M";
    public final static String CELL_COUNT_SPARSE = "EMITTER_CELLCOUNT_SPARSER";

    public final static String TYPE_MASTER_COEFF = "TYPE_MASTER_COEFF";
    public final static String MET_FUNCS = "MET_FUNCTIONS";
    
    public final static int IND_LINETYPE = 0;
    public final static int IND_VAR = 1;


    
    /**
     * Get a printout as a single String, showing the key attributes for this
     * LTM profile.
     *
     * @return String output
     */
    public String printout() {
      
        if (this.subLTM) return null;
 
        String line = "LightTempoMet - " + this.file.getAbsolutePath() + "\n";
        String masters = "\t";
        for (byte q = 0; q < this.masterCoeffs_q.length; q++) {
            if (this.has_q[q]) {
                masters += VARA.VAR_NAME(q) + "=" + this.masterCoeffs_q[q] + ",";
            }
        }
        line += masters + "\n";
        if (this.puffGenIncreaseF != 1) {
            line += "\tHas non-default puffGenInc = " + this.puffGenIncreaseF + "\n";
        }
        if (this.distanceRamp_m != 0) {
            line += "\tHas non-default distaceRamp[m] = " + this.distanceRamp_m + "\n";
        }
        
        if (this.normalizingSum!= null ) {
            line += "\tsum normalizer: " +this.normalizingSum+ "\n";   
        }
        
        if (this.hasOSMconditions) {
            line += "\tHas osmConditons, n = " + this.osmRuleSets.size() 
                    + ", else condition=" + this.osmElseCondition + "\n";
        }
        
        if (this.afilt!=null) {
            line += "\tHas areaFilter\n";
        }
        if (this.mfs!=null) {
            for (int q =0;q<this.mfs.length;q++) {
                ArrayList<MetFunction2> ar = this.mfs[q];
                if (ar!=null) {
                    String var = VARA.VAR_NAME(q);
                    for (MetFunction2 mf:ar) {
                         line+="\t\t metfunction: "+var +", "+mf.reserveID 
                                 +"("+VARA.VAR_NAME(mf.metTypeInt)+")\n";
                    }//for mfs  
                }//if mfs
            }//for types
        }//if any mfs
        
       
        if (!this.subLTM && this.subs!=null) {
            for (LightTempoMet lsub:this.subs) {
                line+= "\t\t has sub: "+ lsub.identifier +"\n";
            }
        }
        
        return line;
    }

    public ArrayList<String> lines;

    /**
     * Create a dummy LTM profile that can be stored to file.The profile itself
     * has 1.0 coefficients everywhere, i.e.does not introduce any temporal
     * variation or dependencies to meteorology.Main use case: osmLayer byteGeo
     * emitters.
     *
     * @param cellSparser factor to reduce the amount of active cells that act
     * as emitters (See more on LTM)
     * @param ops options that carry MetFunctions.
     * @param file absolute file for the dummy profile at the regional emitter
     * directory
     * @return the profile
     */
    public static LightTempoMet buildDummy(int cellSparser, FusionOptions ops,
            File file) {
        ArrayList<String> arr = new ArrayList<>();

        String masters = "TYPE_MASTER_COEFF;";
        for (int q : ops.VARA.Q) {
            masters += ops.VARA.VAR_NAME(q) + "=1.0;";
        }
        arr.add(masters);
        arr.add(MET_FUNCS + ";;");
        arr.add(GENINC + "=1;");
        arr.add(DIST_RAMP + ";0;");
        arr.add(CELL_COUNT_SPARSE + ";" + cellSparser + ";");
        arr.add(ID + ";dummy");

        arr.add("////////////;mon;tue;wed;thu;fri;sat;sun;");
        arr.add(TemporalFunction.TEMPORAL_WD+";1;1;1;1;1;1;1;");//weekday dummy
        arr.add("////////////;local_h0;h1;h2;h3;h4;h5;h6;h7;h8;h9;h10;h11;h12;h13;h14;h15;h16;h17;h18;h19;h20;h21;h22;h23;");
        arr.add(TemporalFunction.TEMPORAL_DIUR+";1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;1;");
        arr.add("////////////;jan;feb;mar;apr;may;jun;jul;aug;sep;oct;nov;dec;");
        arr.add(TemporalFunction.TEMPORAL_MONTHLY+";1;1;1;1;1;1;1;1;1;1;1;1;");
        

        FileOps.printOutALtoFile2(file, arr, false);
        return create(ops, file);
    }


     public static LightTempoMet create(FusionOptions ops, File file) {
         LightTempoMet ltm= new LightTempoMet(ops,file,false);
         if (ltm.subFiles!=null) {
             ltm.subs = new ArrayList<>();
             for (File subfile:ltm.subFiles) {
                  LightTempoMet lsub = new LightTempoMet(ops,subfile,true);
                  ltm.subs.add(lsub);
             }
         }
         
         String sout =ltm.printout();
         if (sout!=null) EnfuserLogger.log(Level.FINE,LightTempoMet.class,sout);
         return ltm;
     }
    
    
    /**
     * The main constructor for LTM instance
     *
     * @param arr a list of lines that make up the profile
     * @param ops options, note: can be bull
     * @param file file for the profile (usually this is the source for 'arr')
     */
    private LightTempoMet(FusionOptions ops, File file, boolean sub) {
        this.VARA = ops.VARA;
        ArrayList<String> arr = FileOps.readStringArrayFromFile(file);
        this.lines = arr;
        this.file = file;
        this.subLTM = sub;
        int LEN = ops.VARA.Q_NAMES.length;
        this.masterCoeffs_q = new float[LEN];
        this.has_q = new boolean[LEN];
        this.tempo = TemporalFunction.fromFile(arr,file);
        this.mfs = new ArrayList[LEN];

        ArrayList<String[]> typeMasters = new ArrayList<>();
        Double fillerValue = null;
        ArrayList<String[]> metFunc_vars =new ArrayList<>();
        int lineN=0;
        

        for (String s : arr) {
            lineN++;
            try {
            s = s.replace(" ", "");//white space removal
            
            String[] sp =s.split (";");
            if (sp==null || sp.length<2) continue;
            String def = sp[0];
            this.keyValues.put(def, sp);
 
            if (def.equals(TYPE_MASTER_COEFF)) {
                for (String test : sp) {
                    if (test.contains("=")) {
                        String[] type_coeff = test.split("=");
                        typeMasters.add(type_coeff);
                        if (type_coeff[0].equals("ALL")) {
                            fillerValue = Double.valueOf(type_coeff[1]);
                            System.out.println("LTM masterCoeff filler value found: "+ file.getAbsolutePath()+": "+fillerValue);
                        }
                    }
                }
            }
            
            if (def.contains(SUB_LTM) && !sub) {//component profiles (the new style). But a sub cannot have subs.
                this.subFiles = new ArrayList<>();
                for (String test:sp) {
                    if (test.endsWith(".csv")) {
                        String subpath = file.getParentFile().getAbsolutePath() + FileOps.Z + test;
                        File subfile = new File(subpath);
                        System.out.println("Adding subFile:"
                                +subfile.getAbsolutePath() +" for "+file.getName());
                        subFiles.add(subfile);
                    }//if csv
                }//for sub names
            }//if subs
            
            if (def.contains(MET_FUNCS)) {
                for (int k =1;k<sp.length;k++) {
                    String cand = sp[k];
                    if (cand.contains(",")) {
                        metFunc_vars.add(cand.split(","));
                    } 
                }
            }
            
            if (def.contains(ID)) {
                this.identifier = sp[1];
            }

            if (def.contains(GENINC)) {
                String ss = sp[1];
                this.puffGenIncreaseF = Double.valueOf(ss).floatValue();
            }

            if (def.contains(DIST_RAMP)) {
                String ss = sp[1];
                this.distanceRamp_m = Double.valueOf(ss).floatValue();
                if (this.distanceRamp_m<0) this.distanceRamp_m =0;
            }
            
            //total normalizer
            if (def.contains(NORMALIZE_TO_SUM)) {
                double val = Double.valueOf(sp[1]); 
                if (s.contains(SWITCH_SUM)) {
                    String area_value = getBetween(s,SWITCH_SUM,">");
                    EnfuserLogger.log(Level.INFO,this.getClass(),"SWITCH SUM found for LTM: "
                            + area_value +", this should have the format 'name1=x,name2=y'");
                    
                    String[] tokens = area_value.split(",");
                    for (String tok:tokens) {
                        if (!tok.contains("=")) continue;
                        String[] av = tok.split("=");//areaName=x
                        String destArea = av[0];
                        if (ops.areaID().equals(destArea)) {//correct area name found!
                           val = Double.valueOf(av[1]);  
                           EnfuserLogger.log(Level.INFO,this.getClass(),"\t FOUND it: "+destArea +" => "+val);
                           break;
                        } else {
                            EnfuserLogger.log(Level.INFO,this.getClass(),"\t\t wrong area: "+destArea);
                        }
                    }//for name=x   
                }//if switch sum by name
                this.normalizingSum = val;
            }//if normalize to sum

            //cell count sparser=============
            if (def.contains(CELL_COUNT_SPARSE)) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "emitter cell count sparser found...");

                try {
                    String ss = sp[1];
                    this.emissionCellSparser = Integer.valueOf(ss);
                        EnfuserLogger.log(Level.FINER,this.getClass(),"\t=> " + this.emissionCellSparser);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            } catch (Exception e) {
                 EnfuserLogger.log(e,Level.SEVERE,this.getClass(),
                  "LTM parse fail: "+file.getAbsolutePath() +" at line: "+lineN);       
            }
            
            
        }//for lines


        if (this.afilt != null) {
            this.hasSpatialDifferences = true;
        } 

        //set masters
        int adds =0;
        if (fillerValue!=null) {
            adds++;
            for (int q =0;q<this.masterCoeffs_q.length;q++ ) {
                this.masterCoeffs_q[q] = fillerValue.floatValue();
                this.has_q[q] = true;
            }
        }
        
        for (String[] varCoeff : typeMasters) {
            int typeInt = ops.getTypeInt(varCoeff[0]);
            if (typeInt==-1) continue;//unknown type.
            double coeff = Double.valueOf(varCoeff[1]);
            this.masterCoeffs_q[typeInt] = (float) coeff;
            this.has_q[typeInt] = true;
            adds++;
        }
        
        if (adds==0) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "LTM did not contain master scaling coefficinents : "
                          +file.getAbsolutePath());     
        }
        //fractions - metComponents
        
        HashMap<String, MetFunction2> mfsh =ops.allMfs;
        for (String[] met_var : metFunc_vars) {
            
            String metFuncsString = met_var[0];
            String typeString = met_var[1];

            int q0 =0;
            int qmax = LEN;
            if (typeString.equals("ANY") || typeString.equals("ALL")) {//add all
                
            } else {
                int typeI = ops.getTypeInt(typeString);
                q0=typeI;
                qmax = typeI+1;
            }
                
                for (Integer q = q0; q < qmax; q++) {
                    if (has_q[q]) {
                        if (this.mfs[q]==null) this.mfs[q] = new ArrayList<>();
                        MetFunction2 addMf = mfsh.get(metFuncsString);
                        if (addMf!=null) {
                            this.mfs[q].add(addMf);
                            this.hasMetFunctions =true;
                        } else {
                            EnfuserLogger.log(Level.WARNING,this.getClass(),
                                    "Missing metFunction? " + metFuncsString+", "
                                            + this.file.getAbsolutePath());
                        }
                    }
                }


        }//for metFuncs
        
        
        for (byte i = 0; i < this.masterCoeffs_q.length; i++) {
            if (this.mfs[i] == null) {
                this.mfs[i] = new ArrayList<>();
            }
                EnfuserLogger.log(Level.FINER,this.getClass(),ops.VAR_NAME(i) + ": mainFactor " + this.masterCoeffs_q[i]
                        + ", which is dependendent on metFunctions (" + this.mfs[i].size() + ")");
            
        }
    }
    
    /**
     * 
     * @param typeInt
     * @param ens
     * @param tzt
     * @return 
     */
    public float[] getAnnualSeries(int typeInt, DataCore ens,TZT tzt, Met met) {
        int N = 8760;
        float[] vals = new float[N];
        Dtime dt = new Dtime("2019-01-01T00:00:00");
        for (int i =0;i<N;i++) {
            Dtime[] dts = tzt.getDates(dt);
            vals[i]=this.getScaler(dt, dts, met, typeInt);
        }
        return vals;
    }
    

    /**
     * Get the scaler factor that is to be applied to the emitter, taking into
     * account the time, meteorology and pollutant species in question. 
     * @param dt time
     * @param dates time instances as given by TZT.
     * @param met meteorology for the time and location
     * @param typeI pollutant type index.
     * @return float scaled emission factor scaler.
     */
    public float getScaler(Dtime dt, Dtime[] dates,
            Met met, int typeI) {

        float f = this.getScaler_master( dt, dates, met, typeI);
        if (!this.subLTM && this.subs!=null) {
            for (LightTempoMet lsub:subs) {
                f+=lsub.getScaler_master(dt, dates, met, typeI);
            }
        }//if subs
        return f;
    }
    
    public float getSubScaler(int k, Dtime dt, Dtime[] dates,
            Met met, int typeI) {

        if (k==0) {
            return this.getScaler_master( dt, dates, met, typeI);
        } else {
            k--;
            return this.subs.get(k).getScaler_master(dt, dates, met, typeI);
        }
    }
    
    public String getSubName(int k) {
        if (k==0) {
            return this.identifier;
        } else {
            k--;
            return this.subs.get(k).identifier;
        }
    }
    
    
    public float getScaler(OsmLocTime loc, Met met, int q) {
          return getScaler(
                  loc.dt,
                  loc.allDates,
                  met,q);
    }

    /**
     * Get the scaler factor that is to be applied to the emitter, taking into
     * account the time, meteorology and pollutant species in question. In case
     * the profile has an AreaFilter the method returns 0 for areas outside the
     * filter.
     *
     * @param dt time
     * @param dates time instances as given by TZT.
     * @param met meteorology for the time and location
     * @param typeI pollutant type index.
     * @return float scaled emission factor scaler.
     */
    private float getScaler_master(Dtime dt, Dtime[] dates,Met met, int typeI) {
        //quick checks for zero return =================
        if (!has_q[typeI]) {
            return 0f;
        }

        //===============================================
            float tfm = tempo.getTemporal(dt, dates);
            if (tfm <= 0) {
                return 0f;//cannot be anything other than 0
            }
            float base = this.masterCoeffs_q[typeI];
            for (MetFunction2 mf : mfs[typeI]) {
                base *= mf.getMetScaler(met);
            }

            return tfm * (base);
    }


    public boolean hasMetFunctions() {
        if (!this.subLTM && this.subs!=null) {
            for (LightTempoMet lsub:subs) {
               if (lsub.hasMetFunctions) return true;
            }
        }
       return this.hasMetFunctions;
    }

    
     /**
     * Read the LightTempoMet object that is associated to this emitter by name
     * identification. The LTM-file must exist in the regional emitter
     * directory.
     *
     * @param ops FusionOptions for regional directory structure.
     */
    public static LightTempoMet readWithName(FusionOptions ops, String name) {

        String dir = ops.getEmitterDir();
        File f = new File(dir);
        File[] ff = f.listFiles();
        for (File test : ff) {
            if (test.getName().contains(".csv") && test.getName().contains("LTM") 
                    && test.getName().contains(name)) {
               return create(ops,test);
 
            }

        }//read all files and match for name
        
        EnfuserLogger.log(Level.WARNING, LightTempoMet.class,
                "Could not find an LTM file to read: " + name +", "+ dir);
        return null;
    }
    
    public boolean hasNormalizingSum() {
        return this.normalizingSum!=null;
    }

    public final static int NORMALIZE_UGPSM2 = 0;
    public final static int NORMALIZE_KGPA = 1;
    public GeoGrid normalizeEmissionGridSum(FusionOptions ops, GeoGrid g, float cellA_m2,
            int mode, String identifier) {
       
        if (this.normalizingSum==null) return g;//nothing to do.
        if (mode > 1) return g;
        
        float raws_annual_kg =0;
        float secsPerYear = 8760*3600;
        
        for (int h =0;h<g.H;h++) {
            for (int w =0;w<g.W;w++) {
                
                if (mode == NORMALIZE_UGPSM2) {
                    float val = g.values[h][w];
                    val*=secsPerYear;//remove per s
                    val*=cellA_m2;//remove per m2
                    val/=BILLION;//and ug to kg
                    raws_annual_kg+=val;//add
                    
                } else {//just sum the kg
                    raws_annual_kg+=g.values[h][w];
                }
                
            }//for w
        }//for h
         
        if (raws_annual_kg<= 0) {
            EnfuserLogger.log(Level.WARNING,this.getClass()," Cannot normalize emitter sum for "
                    + identifier+": original sum ="+raws_annual_kg);
            return g;
        }
        
        
            float util = this.getAnnualUtilization(ops, null);
            float raw_util = util;
             if (this.subs!=null) {
                for ( LightTempoMet ltm:this.subs) {
                     
                     float sub_util = ltm.getAnnualUtilization(ops, null);
                     EnfuserLogger.log(Level.INFO,this.getClass(),
                             "sub utilization added from"+ ltm.identifier +":"+sub_util);
                     util+=sub_util;
                }
             }
            double ratio = this.normalizingSum/(raws_annual_kg*util);
            
             EnfuserLogger.log(Level.INFO,this.getClass(), identifier + ", " +this.identifier 
                     +"\n"+"Current raw sum="+raws_annual_kg
                     +", annualUtilization="+ util+" => effective sum ="+raws_annual_kg*util 
                     +", TARGET = "+this.normalizingSum);

            EnfuserLogger.log(Level.INFO,this.getClass(),
                    "\t scaler = "+editPrecision(ratio,4));
          
        //here comes the adjustment that takes effect
        for (int h =0;h<g.H;h++) {
           for (int w =0;w<g.W;w++) {
               g.values[h][w]*=ratio;
           }
        }
        
        for (int i =0;i<this.masterCoeffs_q.length;i++) {
            float master = this.masterCoeffs_q[i];
            if (master <=0) continue;
            float total = (float)(master*raws_annual_kg*ratio*raw_util);

             EnfuserLogger.log(Level.INFO,this.getClass(),"\t " + ops.VARA.VAR_NAME(i) 
                     +" annual modelled [kg]: "+ total);
        }
        
        
        if (this.subs!=null) {
            for ( LightTempoMet ltm:this.subs) {
                 EnfuserLogger.log(Level.INFO,this.getClass(),"sub: "+ ltm.identifier);
                 float sub_util = ltm.getAnnualUtilization(ops, null);
                 for (int i =0;i<ltm.masterCoeffs_q.length;i++) {
                    float master = ltm.masterCoeffs_q[i];
                    if (master <=0) continue;
                    float total = (float)(master*raws_annual_kg*ratio*sub_util);

                     EnfuserLogger.log(Level.INFO,this.getClass(),"\t " + ops.VARA.VAR_NAME(i) 
                             +" annual modelled [kg] (sub): "+ total);
                }
            }
        }
        
        return g;
    }

    
    
    public float getAnnualUtilization(FusionOptions ops, Met met) {
        ArrayList<Dtime[]> year = ops.getFullYear();
        
        int masterType = -1;
        float masterF=1;
        for (int q: ops.VARA.Q) {
            if (this.has_q[q]) {
                masterType = q;
                masterF=this.masterCoeffs_q[q];
                break;
            }
        }
        
        float sum =0;
        for (Dtime[] allDates:year) {
            Dtime dt = allDates[TZT.UTC];
            float fact =this.getScaler_master(dt, allDates, met, masterType);
            fact/=masterF;
            
            sum+=fact;
            
        }//for time
        
        return sum/8760;
    }

    public String[] getAttribute(String key) {
        return this.keyValues.get(key);
    }



}
