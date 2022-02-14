/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class is used to describe and distinguish emission source categories.
 * Some of the categories must exist, e.g., traffic, bg, but it is also possible
 * to add and customize all kinds of addtional categories usinf
 * categoryAssociations.csv.
 *
 * For data storage, these category associations are used. APPLICABLE_VARIABLES
 * and ARCHIVED_VARIABLES_SKIP can be used to significantly reduce the amount of
 * stored data without losing anything important. To do this, this class
 * provides easy-to-use indexing features.
 *
 * One use case for this class is to specify Override ranges for Adapter class.
 * As an example, it is possible to specify how much hourly fine-tuning can be
 * allowed for "traffic" emissions based on measurement evidence.
 *
 * @author johanssl
 */
public class Categories {

    public static float NO2_FRAC_FLAT = 0.20f;
    public static float NO_FRAC_FLAT = 0.15f;
    public static float NO2_FRAC_PHOTOCH = 0.65f;

    HashMap<Integer, String[]> catLines;
    public String[] CATNAMES;
    public final int[] C;
    public String[] CATNAMES_SHORT;
    public String[] DESCR;
    HashMap<String, Integer> nameToIndex = new HashMap<>();
    public float[][] numerics;
    //for Overrides===============================
    //automatic tuning of emission rates for categories based on most recent measurements
    //min factor, max factor, step increment
    public final static int OVER_MINF = 0;//minimum override factor
    public final static int OVER_MAXF = 1;//max override factor
    public final static int OVER_INCR = 2;//step size
    public final static int OVER_MAXSTEPS = 3;//max steps (using incr) from previous value
    public final static int OVER_DEFAULT = 4;//max steps (using incr) from previous value
    public final static int NO2_FRAC = 5;

    int LEN = -1;
    public static int CAT_TRAF = -1;
    public static int CAT_HOUSEHOLD = -1;
    public static int CAT_POWER = 2;
    //public static int CAT_INDUST =3;
    public static int CAT_SHIP = -1;
    public static int CAT_BG = -1;
    public static int CAT_RESUSP = -1;
    public static int CAT_MISC = -1;

    private final static int IND_CATNAM = 1;
    private final static int IND_NAMSHORT = 2;
    private final static int IND_DESC = 3;


    public Categories(String ROOT) {
        String dir = ROOT + "data" + Z;
        catLines = new HashMap<>();
        ArrayList<String> arr;
        File f = new File(dir + "categoryAssociations.csv");
        arr= FileOps.readStringArrayFromFile(f);

        for (String s : arr) {
            if (!s.contains(";") ||s.contains("CATEGORY_INT")) continue;//empty last line or header.
            String[] split = s.split(";");
            String nums = split[0];
            if (nums.toLowerCase().contains("disabled")) continue;
            int num = Integer.valueOf(nums);
            catLines.put(num, split);

        }//adds if the first value is a legit number

        LEN = catLines.size();

        CATNAMES = new String[LEN];
        C = new int[LEN];
        CATNAMES_SHORT = new String[LEN];
        DESCR = new String[LEN];
        numerics = new float[LEN][NO2_FRAC + 1];

        for (int i = 0; i < LEN; i++) {
            C[i] = i;
            String[] dat = catLines.get(i);
            if (dat == null) {
                String err = "Categories: CRITICAL ERROR: discontinous numbering o"
                        + "f emission categories. Missing = " + i + ". Indexing must start"
                        + " from 0 and missing category numbers are NOT allowed.";
                EnfuserLogger.log(Level.WARNING,this.getClass(),err);
            }

            CATNAMES[i] = dat[IND_CATNAM];
            this.nameToIndex.put(CATNAMES[i], i);

            //detect some of the mandatory category indices
            if (CATNAMES[i].toLowerCase().contains("traffic")) {
                CAT_TRAF = i;
            } else if (CATNAMES[i].toLowerCase().contains("househ")) {
                CAT_HOUSEHOLD = i;
            } else if (CATNAMES[i].toLowerCase().equals("bg")) {
                CAT_BG = i;
            } else if (CATNAMES[i].toLowerCase().contains("resusp")) {
                CAT_RESUSP = i;
            } else if (CATNAMES[i].toLowerCase().contains("ship")) {
                CAT_SHIP = i;
            } else if (CATNAMES[i].toLowerCase().contains("misc")) {
                CAT_MISC = i;
            } else if (CATNAMES[i].toLowerCase().contains("power")) {
                CAT_POWER = i;
            }

            CATNAMES_SHORT[i] = dat[IND_NAMSHORT];
            DESCR[i] = dat[IND_DESC];

            for (int j = OVER_MINF; j <= NO2_FRAC; j++) {
                numerics[i][j] = Double.valueOf(dat[j + 4]).floatValue();
            }
        }//for len
        

    }

    public int indexFromName(String nam) {
     return indexFromName(nam, true);
    }
    
        public int indexFromName(String nam, boolean warn) {

        Integer ind = this.nameToIndex.get(nam);
        if (ind == null) {
           if (warn) EnfuserLogger.log(Level.FINER,this.getClass(),
                   "WARNING! emission category index was not found for String: " + nam);
            return -1;
        } else {
            return ind;
        }

    }
        
        public int getSourceType(String source, boolean warning) {
        String s = source.toLowerCase();
        s = s.replace(" ", "");
        if (s.contains(",")) {
            s = s.split(",")[0];
        }

        for (int c : C) {
            if (s.contains(CATNAMES[c])) {
                return c;
            }
        }
        EnfuserLogger.log(Level.FINER,this.getClass(),"DID NOT find a source category for String " + source);
        return -1;

    }    

    public double numeric(int c, int ind) {
        return this.numerics[c][ind];
    }

    public String getArchComponentName(FusionOptions ops, int q, int c) {
        String name = ops.VARA.Q_NAMES[q] + "_" + this.CATNAMES[c];
        return name;
    }
    
    public int[] archComponentName_toCQ(String name, FusionOptions ops) {
        String[] split = name.split("_");
        String var = split[0];
        String cat = split[1];
        int c = ops.CATS.indexFromName(cat, true);
        int q = ops.VARA.getTypeInt(var);
        return new int[]{c,q};
    }


    public float[] nullComponents(float fillValue) {
       float[] ff = new float[this.LEN];
       ff[0]=fillValue;
       return ff;
    }

    public String headerString() {
       String s ="";
       for (String name:CATNAMES) {
           s+= name+";";
       }
       return s;
    }

    public int parseContainingCategory(String var) {
       int k =-1;
       for (String s:this.CATNAMES) {
           k++;
           if (var.contains(s)) return k;
       }
       return -1;
    }


    public float[] zeroArray() {
      return new float[C.length];
    }


}
