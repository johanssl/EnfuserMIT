/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Logger;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import org.fmi.aq.enfuser.options.VarAssociations;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.ftools.FileOps;


/**
 * A class to hold constantly updated OverrideList results (in the form of
 * LearningTemporal), which can also be used to operate on emission sources.
 *
 * This class provides the way for automatic learning, but is still in
 * development. TODO: one would need to store and read these objects from file,
 * to continue the work.
 *
 * @author Lasse Johansson
 */
public class PersistentAdjustments implements Serializable{
    
    private static final long serialVersionUID = 2526475294122776147L;//Important: change this IF changes are made that breaks serialization!

    private PA[][] cql;
    public static boolean IS_LEARNING = true;
    public final String[] init_categories;
    public final String[] initQ;
    public PersistentAdjustments(Categories CATS, VarAssociations VARA) {

        this.cql = new PA[CATS.C.length][VARA.Q_NAMES.length];
        for (int c : CATS.C) {
            for (int q : VARA.Q) {
                this.cql[c][q] = new PA(c, (byte) q,CATS,VARA);
            }
        }
        this.init_categories = CATS.CATNAMES;
        this.initQ = VARA.Q_NAMES;
    }
    
    public final static String CQL_NAME1 ="PAlearning_";
    public final static String CQL_NAME2 =".dat";
    public static String getLocalName(Dtime dt) {
        Dtime dt2=dt.clone();
        dt2.addSeconds(-dt.hour*3600 - dt.min*60 - dt.sec);
        return CQL_NAME1 + dt2.getStringDate_YYYY_MM_DD()+CQL_NAME2;
    }
        public void saveToBinary(FusionOptions ops, Dtime dt) {
        
        String fileName = ops.statsOutDir(dt) + getLocalName(dt);
        EnfuserLogger.log(Level.FINER,PersistentAdjustments.class,"Saving CQ-learning coefficients to: " + fileName);
        this.trimContent(ops);//store only 3 month worth of logged activities to keep the file size from growing ever bigger. 
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fileName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(this);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                Logger.getLogger(PersistentAdjustments.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(PersistentAdjustments.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
        
    public void trimContent(FusionOptions ops) {
        try {
        for (int c : ops.CATS.C) {
            for (int q : ops.VARA.Q) {
                 this.cql[c][q].trim(ops.getRunParameters().end(), 744*3);
            }
        }
        } catch (Exception e) {
            EnfuserLogger.log(e,Level.WARNING,PersistentAdjustments.class,
                    "Learning temporals trim operation failed.");
        }
    }    
       
    public static boolean CQL_RESET =false;
    public static PersistentAdjustments load(FusionOptions ops, Dtime start, int daysSearch) {
        // Read from disk using FileInputStream
        
         if (CQL_RESET) {
              EnfuserLogger.log(Level.INFO,PersistentAdjustments.class,
                    "CQlearners: reset: creating a new instance. "
                    + start.getStringDate());
            return new PersistentAdjustments(ops.CATS,ops.VARA);
         }
        
        Dtime dt = start.clone();
        dt.addSeconds(-3600*24);//start the search the previous day. This will
        //then NOT use updated stats if the run is repeated for the same time.
        String filename=null;
        for (int i =0;i<daysSearch;i++) {
            String dir = ops.statsOutDir(dt);
            String local = getLocalName(dt);
            File test = new File(dir+local);
            EnfuserLogger.log(Level.INFO,PersistentAdjustments.class,"CQlearners: checking " + test.getAbsolutePath());
            if (test.exists()) {//found one
                EnfuserLogger.log(Level.INFO,PersistentAdjustments.class,"\tCQlearners: previous restore point found: "
                    + test.getAbsolutePath());
                filename = test.getAbsolutePath();
                break;
            }
            dt.addSeconds(-3600*24);//update to previous day
            
        }//for days search
       
        if (filename==null) {
            EnfuserLogger.log(Level.INFO,PersistentAdjustments.class,
                    "CQlearners: NO previous restore point found for "
                    + start.getStringDate());
            return new PersistentAdjustments(ops.CATS,ops.VARA);
        }
        
        FileInputStream f_in;
        try {
            f_in = new FileInputStream(filename);

            // Read object using ObjectInputStream
            ObjectInputStream obj_in;
            try {
                obj_in = new ObjectInputStream(f_in);
                try {
                    // Read an object
                    Object obj = obj_in.readObject();
                    if (obj instanceof PersistentAdjustments) {
                        EnfuserLogger.log(Level.INFO,PersistentAdjustments.class,
                                "CQlearners loaded from .dat-file successfully.");
                        PersistentAdjustments gg = (PersistentAdjustments) obj;
                        gg.checkIndexing(ops);
                        gg.printoutMasters(ops," (after load) ");
                        return gg;
                        
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(PersistentAdjustments.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException ex) {
                Logger.getLogger(PersistentAdjustments.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(PersistentAdjustments.class.getName()).log(Level.SEVERE, null, ex);
        }

        return new PersistentAdjustments(ops.CATS,ops.VARA);
        
    }
    

    /**
     * Get a scaling factor.
     *
     * @param c category index
     * @param q primary type index Q (one of VariableAssociation's int[] Q)
     * @param dates time object to selected temporal index.
     * @return a float scaler.
     */
    private float getLearned(int c, int q, Dtime[] dates, FusionOptions ops) {
        if (!ops.VARA.isPrimary[q]) {
            if (c == Categories.CAT_BG)return 0f;
            return 1f;
        }
        return this.cql[c][q].getLearnedScaler(dates);
    }
    
     /**
     * Get a scaling factor.
     *
     * @param c category index
     * @param q primary type index Q (one of VariableAssociation's int[] Q)
     * @param dates time object to selected temporal index.
     * @param ens
     * @return a float scaler.
     */
     public static float getLearnedScaler(int c, int q, Dtime[] dates, FusionCore ens) {
         if (ens.CQL==null) {
             if (c == Categories.CAT_BG)return 0f;
             return 1f;
         }
         return ens.CQL.getLearned(c, q, dates, ens.ops);
     }
     
   /**
    * Apply data fusion based learning to the given value, for the given c-pair.
    * @param ol HourlyAdjustment, matching the type and time.
 If this is non-null, then the content of this is taken into account (hourly adjustment)
    * @param c category index (if BG then the operation is additive)
    * @param q species index (primary)
    * @param dates time information (if overrideLis is non-null, then this is not used)
    * @param ens the core
    * @param val the value which to operate on
    * @return adjusted value.
    */
   public static float operate(HourlyAdjustment ol,int c, int q, Dtime[] dates, FusionCore ens, float val) {
       if (ol!=null) return ol.operate(c, ens, val);
       float comp = getLearnedScaler(c, q, dates, ens);
       if (c == CAT_BG) {
            return Math.max(0, val + comp); 
        } else {
            return val * comp;
        }
   }
    

    /**
     * Printout a summary on the coefficient and how it has evolved through
     * time.
     *
     * @param ens FusionCore for TZT and out directories.
     * @param start start time for printout
     * @param end end time for printout
     * @param cat category index
     * @param q primary type index Q (one of VariableAssociation's int[] Q)
     * @param dir String dir
     */
    public void printoutHistory(FusionCore ens, Dtime start, Dtime end,
            int cat, int q, String dir) {
        this.cql[cat][q].printoutHistory(ens.ops,dir, start, end);
    }
    
    public void printoutWeekly(FusionCore ens, Dtime start, Dtime end,String dir) {
       
        if (start==null) {
            start = ens.ops.getRunParameters().start();
            end = ens.ops.getRunParameters().end();
        }
         Dtime dt = start.clone();
         
        ArrayList<String> arr = new ArrayList<>();
        String header ="YEAR;MONTH;weekOfYear;";
        for (int q:ens.ops.VARA.Q) {
            for (int c:ens.ops.CATS.C) {
                header+= ens.ops.VAR_NAME(q)+"_"+ens.ops.CATS.CATNAMES[c]+";";
            }
        }
        arr.add(header);
        while (dt.systemHours()<end.systemHours()) {
            try {
                
                String line =dt.year+";"+ (dt.month_011+1)+";"+ dt.hoursSinceThisYear/24/7+";";
                for (int q:ens.ops.VARA.Q) {
                    for (int c:ens.ops.CATS.C) {
                        line+=this.cql[c][q].getWeeklyMaster(dt)+";";
                    }
                }
                arr.add(line);
            }catch (Exception e) {
                e.printStackTrace();
            }
            dt.addSeconds(3600*24*7);//add one week
        }//while time    
        FileOps.printOutALtoFile2(dir, arr, "weeklyCQL.csv", false);
    }

    /**
     * Update the current state of parameters.
     *
     * @param ol Override list that holds the most recent adjustments. These
     * adjustments are to be transfered to this Object as well.
     * @param dates the time for the given HourlyAdjustment.
     */
    private void update(HourlyAdjustment ol, Dtime[] dates, FusionOptions ops) {
        
        //not all overrides should update longer term memory.
        if (!IS_LEARNING || ol.filled || ol.neural) {
            return;
        }
        if (!ops.VARA.isPrimary[ol.typeInt]) {
            return;
        }

        for (int c : ops.CATS.C) {
            PA lt = cql[c][ol.typeInt];
            lt.updateStats(ol, dates,ops);
            lt.updateDailyStats(dates[TZT.UTC]);
            //for cats
        }

    }
    
    /**
     * Update the current state of parameters.
     *
     * @param ol Override list that holds the most recent adjustments. These
     * adjustments are to be transfered to this Object as well.
     * @param dates the time for the given HourlyAdjustment.
     * @param ens
     */
    public static void updateValue(HourlyAdjustment ol, Dtime[] dates, FusionCore ens) {
        if (ens.CQL!=null) {
            ens.CQL.update(ol, dates, ens.ops);
        }
    }

    private void checkIndexing(FusionOptions ops) {
        PA[][] cql_new = new PA[ops.CATS.C.length][ops.VARA.Q_NAMES.length];
        for (int c : ops.CATS.C) {
            for (int q : ops.VARA.Q) {
                cql_new[c][q] = new PA(c, (byte) q,ops.CATS,ops.VARA);
            }
        }
        
        //check old ones
        for (int c =0;c<this.cql.length;c++) {
            for (int q = 0;q<this.cql[0].length;q++) {
                PA lt = this.cql[c][q];
                if (c== Categories.CAT_BG && !lt.additive) {
                    lt = new PA(c, (byte)q, ops.CATS, ops.VARA);
                }
                lt.adjustIndex(ops);
                if (lt.c==-1 ||lt.typeInt==-1) {
                    EnfuserLogger.log(Level.INFO, this.getClass(), "CQlearner: unknown"
                            + " indexing in a loaded instance: "+ lt.catName +", "+ lt.type);
                    continue;
                }
                
                cql_new[lt.c][lt.typeInt] = lt;
            }
        }
        this.cql = cql_new;
    }

    public void printoutMasters(FusionOptions ops, String add) {
       try {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("========CQL state:"+add+"==========");
            String header ="TYPE;";
            for (String cat:ops.CATS.CATNAMES) {
                header+=cat+";";
            }
            lines.add(header);

            for (int q:ops.VARA.Q) {
                String line = ops.VAR_NAME(q) +";";
                for (int c :ops.CATS.C) {
                    PA elem = this.cql[c][q];
                    if (elem!=null) {
                        line+=editPrecision(elem.master,3)+";";
                    } else {
                        line+="-;";
                    }
                }//for cats
                lines.add(line);
            }//for q

            for (String line:lines) {
                EnfuserLogger.log(Level.INFO, this.getClass(), line);
            }
       
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
