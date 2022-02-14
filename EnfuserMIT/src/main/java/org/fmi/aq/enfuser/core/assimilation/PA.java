/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import java.io.Serializable;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.essentials.date.Dtime;
import java.util.Calendar;
import java.util.HashMap;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.gispack.flowestimator.TemporalDef;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 * A class to hold automated learning factors (multiplicative coefficients)
 * which are updated based on OverrideList (Adapter results).
 *
 * These learning factors have specific temporal discrete dimension and they
 * apply to specific type-category pair (Q-C).
 *
 * @author Lasse Johansson
 */
public class PA implements Serializable{
    private static final long serialVersionUID = 2526475214122776147L;//Important: change this IF changes are made that breaks serialization!
    public int typeInt;
    boolean additive = false;
    public String type;
    public int c;
    public String catName;
    
    public float[][] WKD_DIURNAL = new float[TemporalDef.WKDS_NAMES.length][TemporalDef.DIURNALS];//use these if present
    public float master;

    public HashMap<Integer, float[][]> dailyLearn_diurnal = new HashMap<>();
    public HashMap<Integer, Float> dailyLearn_master = new HashMap<>();
    public HashMap<Integer, Boolean> learnedHours = new HashMap<>();
    
    public HashMap<String,String> custom = null;//use-case not yet defined
    public HashMap<String,float[]> customF = null;//use-case not yet defined

    /**
     * Constructor for LearningTemporal
     *
     * @param C emission category index.
     * @param typeI variable type index.
     * @param CATS
     * @param VARA
     */
    public PA(int C, byte typeI, Categories CATS, VarAssociations VARA) {
        this.c = C;
        this.catName = CATS.CATNAMES[C];
        this.typeInt = typeI;
        this.type = VARA.VAR_NAME (typeInt);
        float start = 1f;
        if (C == Categories.CAT_BG) {
            this.additive = true;
            start =0;
        }
        
        //init values: ones => no initial modifications but this can be updated hour after hour Through Adapter & Overrides
        this.master = start;
        for (int wk = 0; wk < WKD_DIURNAL.length; wk++) {
            for (int d = 0; d < WKD_DIURNAL[wk].length; d++) {
                WKD_DIURNAL[wk][d] = start;
            }
        }
        
    }
    
    public void adjustIndex(FusionOptions ops) {
        this.c = ops.CATS.indexFromName(this.catName);
        this.typeInt = ops.VARA.getTypeInt(type);
    }
    
    
    /**
     * Remove older entries and keep the data for the selected amount of
     * past hours
     * @param last a date from which the past will be recorded.
     * @param hoursPast hours past with respect the current date
     */
    public void trim(Dtime last, int hoursPast) {
       Dtime dt = last.clone();
         HashMap<Integer, float[][]> dailyLearn_diurnal_new = new HashMap<>();
         HashMap<Integer, Float>  dailyLearn_master_new = new HashMap<>();
         HashMap<Integer, Boolean> learnedHours_new = new HashMap<>();
       for (int i =0;i<hoursPast;i++) {
           
           Boolean b = this.learnedHours.get(dt.systemHours());
           if (b!=null) learnedHours_new.put(dt.systemHours(), b);
           
           if (i%24==0) {
               Integer day = this.getDayKey(dt);
               if (this.dailyLearn_diurnal.containsKey(day)) {
                   dailyLearn_diurnal_new.put(day,this.dailyLearn_diurnal.get(day));  
               }
               if (this.dailyLearn_master.containsKey(day)) {
                   dailyLearn_master_new.put(day, this.dailyLearn_master.get(day));
               }
           }
           
           dt.addSeconds(-3600);
       }//for time
       
       this.learnedHours = learnedHours_new;
       this.dailyLearn_diurnal = dailyLearn_diurnal_new;
       this.dailyLearn_master = dailyLearn_master_new;
    }

    
    
        public void printoutHistory(FusionOptions ops, String dir, Dtime start, Dtime end) {
        ArrayList<String> arr = new ArrayList<>();
        GlobOptions g = GlobOptions.get();
        String filename = "learningStats_" + ops.VARA.VAR_NAME(this.typeInt)
                + "_" + g.CATS.CATNAMES[this.c]
                + "_" + start.getStringDate_fileFriendly() + "_" + end.getStringDate_fileFriendly() + " .csv";

        //start with last state

        String header = "TIME;MASTER;";//5 elements
        for (int t = 0; t < 24; t++) {
            header += "H=" + t + ";";
        }
        for (int t = 0; t < 24; t++) {
            header += "satH=" + t + ";";
        }
        for (int t = 0; t < 24; t++) {
            header += "sunH=" + t + ";";
        }
        
        arr.add(header);

        //add progression to the left (easy to handle in Excel this way)
        Dtime current = start.clone();
        current.addSeconds(-3600 * 24);
        while (current.systemHours() <= end.systemHours()) {
            current.addSeconds(3600 * 24);

            String line ="";
            int key = this.getDayKey(current);
            if (!this.dailyLearn_diurnal.containsKey(key)) {
                continue;
            } else {
                float[][] dat = this.dailyLearn_diurnal.get(key);
                float masterf = this.dailyLearn_master.get(key);
                line +=  current.getStringDate_YYYY_MM_DD() + ";" + masterf + ";";
                
                for (int wk = 0; wk < WKD_DIURNAL.length; wk++) {
                    for (int d = 0; d < WKD_DIURNAL[wk].length; d++) {
                       line+= editPrecision(masterf*dat[wk][d],2)+";";
                    }//for diurnal
                }//for wkd
            }
            arr.add(line);
        }

        EnfuserLogger.log(Level.FINER,PA.class,"Saving to file: " + dir + filename);
        FileOps.printOutALtoFile2(dir, arr, filename, false);
        EnfuserLogger.log(Level.FINER,PA.class,"Done.");
    }

    /**
     * Get the current scaler stored.
     *
     * @param dates time for which the applicable scaler is sought.
     * @return float multiplicative scaling value.
     */
    public float getLearnedScaler(Dtime[] dates) {
        Dtime local = dates[TZT.LOCAL];
        int dow = getSimpleWKDindex(local);
        
        if (additive) {
            return this.WKD_DIURNAL[dow][local.hour] + master;
        }
        //multiplicative
        return this.WKD_DIURNAL[dow][local.hour] * master;
    }
    
    
    /**
     * Get the learning rate taking into account whether or not this for
     * the master coefficient, or the diurnal slot. The master updates
     * each hour but the diurnals can update less frequently. All this
     * needs to be balanced.
     * 
     * For diurnals specifically,
     * there's 5 working days and only one sat/sun so this must also
     * be addressed in the learning rate.
     * @param master if true, then this is for the master that updates
     * hourly. False for diurnals
     * @param ol the hourly data fusion result
     * @param local local time
     * @param ops options
     * @return adjusted data fusion scaler that is suitable for
     * multiplication (update).
     */
    private float getMultiplicativeUpdateScaler(boolean master, HourlyAdjustment ol,
            Dtime local, FusionOptions ops) {
        
        float overScaler = ol.getvalue(c);
        float diff = overScaler - 1f;//1.3 = 0.3; 0.7 => -0.3
        
        if (master) {
            return  1f + ops.dfOps.LEARNING_RATE()*0.1f * diff;//e.g., if for traffic the override adjustment is 0.7f this is 0.98*1 + 0.7f*0.02;
        } else {
            //each week master gets to update 168 times, while sat/sun diurnal only once. mon-fri diurnal updates 5 times.
            if (local.dayOfWeek == Calendar.SATURDAY 
                    || local.dayOfWeek == Calendar.SUNDAY) {
                return 1f + ops.dfOps.LEARNING_RATE()*4f * diff;//16f would be more accurate but let's go with this to reduce volatility
            
            } else {
                return 1f + ops.dfOps.LEARNING_RATE()*1.5f * diff;//3.36f would be more accurate.
            }
            
        }
    }
    
        private float getAdditiveUpdateFactor(boolean master,
            Dtime local, FusionOptions ops) {
        
        if (master) {
            return  ops.dfOps.LEARNING_RATE()*0.1f;
        } else {
            //each week master gets to update 168 times, while sat/sun diurnal only once. mon-fri diurnal updates 5 times.
            if (local.dayOfWeek == Calendar.SATURDAY 
                    || local.dayOfWeek == Calendar.SUNDAY) {
                return ops.dfOps.LEARNING_RATE()*4f;
            
            } else {
                return ops.dfOps.LEARNING_RATE()*1.5f;
            }
            
        } 
    }

    /**
     * Update the current state of parameters.
     *
     * @param ol Override list that holds the most recent adjustments. These
     * adjustments are to be transfered to this Object as well.
     * @param dates the time for the given HourlyAdjustment.
     * @param ops
     */
    public void updateStats(HourlyAdjustment ol, Dtime[] dates, FusionOptions ops) {
        if (this.additive) {
            updateStats_additive(ol, dates, ops);
            return;
        }
        Dtime local = dates[TZT.LOCAL];
        int sysH = local.systemHours();
        if (this.learnedHours.containsKey(sysH)) {//second update is not allowed for the same hour
            return;
        }
        this.learnedHours.put(sysH, Boolean.TRUE);//flag as processed.

        float learningValue_M = getMultiplicativeUpdateScaler(true,ol,local,ops);
        float learningValue_D = getMultiplicativeUpdateScaler(false,ol,local,ops);
        
        this.master *= learningValue_M;//update master
        if (master < ops.dfOps.LEARNING_SAFETY_MIN()) {
            master = ops.dfOps.LEARNING_SAFETY_MIN();
        }
        if (master > ops.dfOps.LEARNING_SAFETY_MAX()) {
            master = ops.dfOps.LEARNING_SAFETY_MAX();
        }
        //update diurnal. Since there is a lot less data to play with,
        //make updates for +1,-1 hours (local time)
        for (int k =0;k<3;k++) {
            float D = learningValue_D;//this is a value very close to 1. 
            if (k==1) {
                local = dates[TZT.NEXT_LOCAL];
                D = getMultiplicativeUpdateScaler(false,ol,local,ops);
            } else if (k==2) {
                local = dates[TZT.PREV_LOCAL];
                D = getMultiplicativeUpdateScaler(false,ol,local,ops);
            }
            if (k>0) D = (1f-D_SPLASH) + D_SPLASH*D;//reduced effect
            
            int dow = getSimpleWKDindex(local);
            this.WKD_DIURNAL[dow][local.hour] *= D;

            if (this.WKD_DIURNAL[dow][local.hour] < ops.dfOps.LEARNING_SAFETY_MIN()) {
                this.WKD_DIURNAL[dow][local.hour] = ops.dfOps.LEARNING_SAFETY_MIN();
            }
            if (this.WKD_DIURNAL[dow][local.hour] > ops.dfOps.LEARNING_SAFETY_MAX()) {
                this.WKD_DIURNAL[dow][local.hour] = ops.dfOps.LEARNING_SAFETY_MAX();
            }
        }//for local, next, previous hour.
    }
    
   private final static float D_SPLASH =0.33f; 
   private void updateStats_additive(HourlyAdjustment ol, Dtime[] dates, FusionOptions ops) {
        Dtime local = dates[TZT.LOCAL];
        int sysH = local.systemHours();
        if (this.learnedHours.containsKey(sysH)) {//second update is not allowed for the same hour
            return;
        }
        this.learnedHours.put(sysH, Boolean.TRUE);//flag as processed.
        float up = ol.getvalue(this.c);//flat correction value
        float fmaster = this.getAdditiveUpdateFactor(true,local,ops);
        
        this.master = up*fmaster + (1f-fmaster)*master;//update master
        float[] lim = ops.VARA.getBackgroundLearningLimits(this.typeInt);
        if (master < lim[0]) master = lim[0];
        if (master > lim[1]) master = lim[1];
        //update diurnal. Since there is a lot less data to play with,
        //make updates for +1,-1 hours (local time)
        
        for (int k =0;k<3;k++) {
            float fdiur = this.getAdditiveUpdateFactor(false,local,ops);
            if (k==1) {
                local = dates[TZT.NEXT_LOCAL];
                fdiur = this.getAdditiveUpdateFactor(false,local,ops)*D_SPLASH;
                
            } else if (k==2) {
                local = dates[TZT.PREV_LOCAL];
                fdiur = this.getAdditiveUpdateFactor(false,local,ops)*D_SPLASH;
            }
            int dow = getSimpleWKDindex(local);
            float d = this.WKD_DIURNAL[dow][local.hour];//previous state
            d = up*fdiur + (1f-fdiur)*d;//update
            if (d < lim[0]) d = lim[0];
            if (d > lim[1]) d = lim[1];
            this.WKD_DIURNAL[dow][local.hour] =d;

        }//for local, next, previous hour.
    }

    /**
     * Get a simple 3-tier index for day-of-week (mon-fri,Sat, Sun).
     *
     * @param local local time.
     * @return index [0,2].
     */
    public static int getSimpleWKDindex(Dtime local) {
        int dow = 0;

        if (local.dayOfWeek == Calendar.SATURDAY) {
            dow = 1;
        } else if (local.dayOfWeek == Calendar.SUNDAY) {
            dow = 2;
        }

        return dow;
    }

    /**
     * Update daily average learning factor stats.
     *  (for statistics)
     * @param dt time
     */
    public void updateDailyStats(Dtime dt) {
        float[][] diurs = new float[3][24];

        for (int wk = 0; wk < diurs.length; wk++) {
            for (int d = 0; d < diurs[wk].length; d++) {
                diurs[wk][d] = WKD_DIURNAL[wk][d];
            }
        }

        Integer key = this.getDayKey(dt);
        this.dailyLearn_diurnal.put(key, diurs);
        this.dailyLearn_master.put(key, master);

    }

    /**
     * Simple day index based on Dtime object.
     *
     * @param dt the time.
     * @return index integer.
     */
    public Integer getDayKey(Dtime dt) {
        int d = (int) (dt.systemHours() / 24);
        return d;
    }

    double getWeeklyMaster(Dtime dt) {
        int key = this.getDayKey(dt);
        
        float sum =0;
        int n =0;
        
        for (int day =0;day<7;day++) {
            int dayKey = key+day;
            Float dailyMaster = this.dailyLearn_master.get(dayKey);
            if (dailyMaster==null) continue;
            sum+=dailyMaster;
            n++;
        }
        
        if (n>0) sum/=n;
        return editPrecision(sum,2);
    }
}
