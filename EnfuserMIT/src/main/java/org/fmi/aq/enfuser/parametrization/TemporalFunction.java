/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.parametrization;

import java.io.File;
import static org.fmi.aq.enfuser.datapack.main.TZT.LOCAL;
import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class is to describe a flexible temporal profile that can be used for a
 * number of use cases, but mostly for emission temporal profiles.
 *
 * The profile is setup by parsing a csv-text file with distinct format.
 * Generally, the first column of the csv-table contains a key-word to guide how
 * the next columns are to be parsed.
 *
 * The temporal profile is a product of many independent temporal factors: -
 * Monthly variability - Day-of-week variability (DoW) - diurnal variability -
 * weekly variability
 *
 * Not all of these need to be present. For example, if the monthly and weekly
 * variability has not been specified only the diurnal and DoW-variability will
 * be used.
 *
 * For smoother outcome the profile also interpolates the data into 30-minute
 * timesteps automatically.
 *
 * It is also possible to customize the temporal profile practically for every
 * day of the year by using the key word 'TEMPORAL_M_WD_D'. For any applicable
 * day such customized profile exists, the more generic diurnal-DoW-month
 * profile will be overridden by this custom profile.
 *
 * For recurring calendar days (such as New Year's Eve) a 'custom trigger'
 * profile can be defined.
 *
 * @author Lasse Johansson
 */
public class TemporalFunction {

    public float[] monthVariability = new float[12];
    private float[] wdVariability = new float[8];//there are of course seven days in the week but the Java Calendar object has some strange integers for different cal. days.
  

//advanced temporals thay may not need to be present
    public HashMap<Integer, float[]> customDiurnals_48 = null;
    public float[] weekVariability = null;
    public float[][][] custom_M_WD_diurnals48 = new float[12][8][48];//use these if present

    public final static String TEMPORAL_WD = "TEMPORAL_WD";
    public final static String TEMPORAL_DIUR = "TEMPORAL_DIURNAL";
    public final static String TEMPORAL_MONTHLY = "TEMPORAL_MONTHLY";

    public final static String TEMPORAL_WEEKLY = "TEMPORAL_WEEKLY";
    public final static String TEMPORAL_M_WD_D = "TEMPORAL_M_WD_D";
    public final static String TEMPORAL_TRIGGER_DIURNAL = "TEMPORAL_TRIGGER_DIURNAL";

    private TemporalFunction() {

    }
    private final static int C_OFF = 1;
    private final static int C_OFF_SPECIAL = 3;
    

    /**
     * Read a TemporalProfile from an array of read lines from
     * file. 
     * 
     * @param arr a list of read lines from file (csv)
     * @return the temporal profile
     */
    public static TemporalFunction fromFile( ArrayList<String> arr, File f) {
        
        
        TemporalFunction tf = TemporalFunction.getDefault();
        boolean basicDiurSet = false;
         int lineN =0;
        try {
           
        for (String line : arr) {
            lineN++;
            if (line == null || line.length() == 0) {
                continue;//empty line
            }
            line = line.replace(" ", "");
            String[] temp = line.split(";");
            if (temp==null || temp.length <2) continue;
            String keyName = temp[0];

                if (keyName.equals(TEMPORAL_WD)) {

                    float[] wd = new float[8];
                    for (int j = C_OFF; j < temp.length; j++) {
                        if (temp[j].length() > 0) {
                            wd[j - C_OFF] = Double.valueOf(temp[j]).floatValue();
                        }
                    }
                    tf.setWD_monToSun(wd);

                } else if (keyName.equals(TEMPORAL_DIUR)) {
                    basicDiurSet = true;

                    float[] d = new float[24];
                    for (int j = C_OFF; j < temp.length; j++) {
                        if (temp[j].length() > 0) {
                            d[j - C_OFF] = Double.valueOf(temp[j]).floatValue();
                        }
                    }
                    d = TemporalFunction.interpolate48(d);

                    //copy content to all diurnals as default
                    for (int i = 0; i < 48; i++) {
                        for (int m = 0; m < 12; m++) {
                            for (int wd = 0; wd < 8; wd++) {
                                tf.custom_M_WD_diurnals48[m][wd][i] = d[i];
                            }
                        }

                    }

                } else if (keyName.equals(TEMPORAL_MONTHLY)) {
                    if (line.contains("ONES")) {
                        //no need to do anything, monthly var is full of ones as default
                    } else {//something to read

                        float[] d = new float[12];
                        for (int j = C_OFF; j < temp.length; j++) {
                            if (temp[j].length() > 0) {
                                d[j - C_OFF] = Double.valueOf(temp[j]).floatValue();
                            }
                        }

                        tf.monthVariability = d;

                    }

                } else if (keyName.equals(TEMPORAL_WEEKLY)) {

                    float[] d = new float[53];
                    for (int j = C_OFF; j < temp.length; j++) {
                        if (temp[j].length() > 0) {
                            d[j - C_OFF] = Double.valueOf(temp[j]).floatValue();
                        }
                    }

                    tf.weekVariability = d;

                } else if (keyName.equals(TEMPORAL_TRIGGER_DIURNAL)) {
                    String trigPattern = temp[C_OFF];
                    float[] d = new float[24];
                    for (int j = C_OFF + 1; j < temp.length; j++) {
                        if (temp[j].length() > 0) {
                            d[j - C_OFF - 1] = Double.valueOf(temp[j]).floatValue();
                        }
                    }

                    tf.addCustomTrigger(trigPattern, d);

                } else if (keyName.equals(TEMPORAL_M_WD_D)) {//a more customized daily pattern
                    //this can be for a specific month-day-of-week -pair
                    if (!basicDiurSet) {
                        EnfuserLogger.log(Level.WARNING,TemporalFunction.class,
                                "TemporalFunction: reading " + TEMPORAL_M_WD_D
                                + ", but a general diurnal profiles has not been created yet (" + TEMPORAL_DIUR + ")");
                    }
                    String monthString = temp[C_OFF];//month identifier is here
                    String dow = temp[C_OFF + 1]; //day of week identifier is next
                   
                    if (monthString.contains("ALL")) {
                        monthString = ALL_MONTHS_011;
                    }
                    String[] months = monthString.split(",");
                    ArrayList<Integer> dowIs = TemporalFunction.getDowIndex(dow);
                    float[] d = new float[24];

                    for (int j = C_OFF_SPECIAL; j < temp.length; j++) {//diurnals parsing with offset +2
                        if (temp[j].length() > 0) {
                            d[j - C_OFF_SPECIAL] = Double.valueOf(temp[j]).floatValue();
                        }
                    }

                        EnfuserLogger.log(Level.FINER,TemporalFunction.class,
                                "\t adding custom M_WD_diurnal for Temporal function ");
                        EnfuserLogger.log(Level.FINER,TemporalFunction.class,"\t\t Month count =" + months.length
                                + ", DoW count =" + dowIs.size() + " ( " + dow + "),  fullLine=" + line);
                    

                    for (String M : months) {

                        int month = Integer.valueOf(M);
                        for (int dowI : dowIs) {//"monfri" will add these patterns for each working days
                            tf.custom_M_WD_diurnals48[month][dowI] = TemporalFunction.interpolate48(d);
                        }
                    }
                }

 
        }//for lines

        } catch (Exception e) {
            EnfuserLogger.log(Level.SEVERE, TemporalFunction.class, "Temporal function parse"
                    + " failed from: "+ f.getAbsolutePath() +", at line: "+lineN);
            e.printStackTrace();
            return null;
        }
        return tf;
    }
    private final static String ALL_MONTHS_011 = "0,1,2,3,4,5,6,7,8,9,10,11";

    /**
     * Create smooth interpolated data from a 24h data vector into 48 30min
     * steps.
     *
     * @param dat24 original 24h data
     * @return an array with length 48.
     */
    public static float[] interpolate48(float[] dat24) {
        float[] d48 = new float[48];
        for (int i = 0; i < 48; i++) {
            int index = i / 2;//index for 24-data
            if (index < 23) {//can be interpolated

                d48[i] = 0.5f * dat24[index] + 0.5f * dat24[index + 1];

            } else {//last index cannot be interpolated
                d48[i] = dat24[index];
            }
        }

        return d48;
    }

    /**
     * Get the proper diurnal index for the diurnal variability, taking into
     * account that the data is in 48-step form.
     *
     * @param dt time
     * @param dates dates as given by TZT.
     * @return index for diurnal profile.
     */
    private int diurIndex(Dtime dt, Dtime[] dates) {

        int ind = dates[LOCAL].hour * 2;
        if (dt.min > 29) {
            ind++;
        }
        return ind;

    }

    /**
     * Use the given Monday-to-Sunday factors and set them according to the Java
     * Calendar indexing.
     *
     * @param t float[] original data.
     */
    public void setWD_monToSun(float[] t) {

        wdVariability[Calendar.MONDAY] = t[0];
        wdVariability[Calendar.TUESDAY] = t[1];
        wdVariability[Calendar.WEDNESDAY] = t[2];
        wdVariability[Calendar.THURSDAY] = t[3];
        wdVariability[Calendar.FRIDAY] = t[4];
        wdVariability[Calendar.SATURDAY] = t[5];
        wdVariability[Calendar.SUNDAY] = t[6];
    }

    /**
     * Construct a TemporalFunction instance from the provided input.
     *
     * @param temporals a hashMap of float[] vectors that are associated to
     * specific String identifiers. The size of the elements vary according to
     * their use case: e.g., any diurnal profile has the length 24 while a
     * monthly data vector has the length of 12.
     * @param Mfunc String identifier for monthly profile. This key must be
     * present at 'temporals'.
     * @param WDfunc Day-of-Week profile identifier. This key must be present at
     * 'temporals'.
     * @param DiurFunc Diurnal profile identifier. This key must be present at
     * 'temporals'.
     * @param triggers custom trigger day instructor, can be null.
     */
    public TemporalFunction(HashMap<String, float[]> temporals, String Mfunc, String WDfunc,
            String DiurFunc, String triggers) {

        //temporals
        float[] t = (temporals.get(Mfunc));
        if (t.length != 12) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "Monthly temporal function " + Mfunc + " has different length than 7!");
        }

        for (int m = 0; m < monthVariability.length; m++) {
            monthVariability[m] = 1f;
            if (t != null) {
                monthVariability[m] = t[m];
            }
        }

        t = (temporals.get(WDfunc));
        if (t.length != 7) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "WeedDay temporal function " + WDfunc + " has different length than 7!");
        }

        setWD_monToSun(t);

        t = (temporals.get(DiurFunc));
        if (t.length != 24) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "Diurnal temporal function " + DiurFunc + " has different length than 7!");
        }

        float[] t48 = null;
        if (t != null) {
            t48 = interpolate48(t);
        }
        for (int d = 0; d < 48; d++) {
            for (int m = 0; m < 12; m++) {
                for (int wd = 0; wd < 8; wd++) {
                    custom_M_WD_diurnals48[m][wd][d] = 1f;
                    if (t48 != null) {
                        custom_M_WD_diurnals48[m][wd][d] = t48[d];
                    }
                }
            }
        }

        //init trigger days
        if (triggers != null && triggers.length() > 4) {

            String[] trigs = triggers.split(",");//YYYY/12/31_D2,YYYY/1/1_D3
            for (String tr : trigs) {
                String[] split = tr.split("_");
                String trigInfo = split[0];
                String diurKey = split[1];
                float[] ds = temporals.get(diurKey);
                if (ds != null) {
                    addCustomTrigger(trigInfo, ds);
                }
            }//for triggers

        }
    }

    /**
     * Add a customized day trigger for recurring calendar dates. These can
     * target a specific year, or every year if left open.
     *
     * @param trigInfo the instruction for the trigger, e.g., 'YYYY/12/31' (year
     * left open) or 2019/12/31
     * @param ds the custom diurnal profile to be associated to this trigger
     * day.
     */
    public void addCustomTrigger(String trigInfo, float[] ds) {
        //YYYY/12/31
        if (customDiurnals_48 == null) {
            customDiurnals_48 = new HashMap<>();
        }

        String[] YMD = trigInfo.split("/");//e.g. YYYY 12 31

        if (YMD[0].contains("Y")) {
            YMD[0] = "0";
        }
        int y = Integer.valueOf(YMD[0]);
        int m_011 = Integer.valueOf(YMD[1]) - 1;
        int day = Integer.valueOf(YMD[2]);
        int key = getCD_key(y, m_011, day);
        this.customDiurnals_48.put(key, interpolate48(ds));

        EnfuserLogger.log(Level.FINER,this.getClass(),"TemporalFunction: added "
                + "custom diurnal: " + y + "/" + (m_011 + 1) + "/" + day);

    }

    /**
     * get the key (integer for a hash) for Custom Diurnal (CD) profile.
     *
     * @param y year
     * @param m_011 month in range of [0,11]
     * @param d day of month
     * @return integer key for hash.
     */
    private int getCD_key(int y, int m_011, int d) {
        int key = d + 100 * m_011 + 100000 * y;
        return key;
    }

    /**
     * Get the LOCAL temporal profile (a numeric scaler) for the given date. The
     * returned numeric scaler is the product of diurnal, DoW and monthly
     * factors. Note: the local timezone is used (allDates[TZT.LOCAL])
     *
     * @param dt the exact time
     * @param allDates the dates as given by TZT
     * @return float profile scaler.
     */
    public float getTemporal(Dtime dt, Dtime[] allDates) {
        int diurInd = this.diurIndex(dt, allDates);
        int wd = allDates[LOCAL].dayOfWeek;
        int m = allDates[LOCAL].month_011;
        float diurF = this.custom_M_WD_diurnals48[m][wd][diurInd];

        //check trigger days, if exist
        if (this.customDiurnals_48 != null && !this.customDiurnals_48.isEmpty()) {
            int key = this.getCD_key(0, allDates[LOCAL].month_011, allDates[LOCAL].day);//first try without specified year
            float[] ds = this.customDiurnals_48.get(key);
            if (ds == null) {//then check with specified year
                key = this.getCD_key(allDates[LOCAL].year, allDates[LOCAL].month_011, allDates[LOCAL].day);
                ds = this.customDiurnals_48.get(key);
            }

            if (ds != null) {
                diurF = ds[diurInd];//ds existed, override diurnal
            }
        }//if triggers

        if (this.weekVariability != null) {//apply an additional modifier based on week of year
            diurF *= this.weekVariability[allDates[LOCAL].hoursSinceThisYear / 24 / 7];
        }

        return this.monthVariability[allDates[LOCAL].month_011]
                * diurF
                * this.wdVariability[allDates[LOCAL].dayOfWeek];//uses e.g., Calendar.MONDAY
    }
    
        public static TemporalFunction getDefault() {
        TemporalFunction tf = new TemporalFunction();
        for (int i = 0; i < 48; i++) {
            for (int m = 0; m < 12; m++) {
                for (int wd = 0; wd < 8; wd++) {
                    tf.custom_M_WD_diurnals48[m][wd][i] = 1f;
                }
            }
            if (i < 12) {
                tf.monthVariability[i] = 1f;
            }
            if (i < 8) {
                tf.wdVariability[i] = 1f;
            }
        }
        return tf;
    }

    public final static String[][] DOW_NAMES = {
        {"mon", Calendar.MONDAY + ""},
        {"tue", Calendar.TUESDAY + ""},
        {"wed", Calendar.WEDNESDAY + ""},
        {"thu", Calendar.THURSDAY + ""},
        {"fri", Calendar.FRIDAY + ""},
        {"sat", Calendar.SATURDAY + ""},
        {"sun", Calendar.SUNDAY + ""},};

    /**
     * Get the 7-tier day-of-week index from String description.
     *
     * @param dow String description, e.g., "mon", "tue", "sat" or "sun". Note:
     * the special case of "monfri" is also accepted, which results an array of
     * all working days to be returned.
     * @return a list of applicable DoW-indices.
     */
    private static ArrayList<Integer> getDowIndex(String dow) {
        ArrayList<Integer> dows = new ArrayList<>();
        String test = dow.toLowerCase();
        if (test.contains("monfri")) {//special case
            dows.add(Calendar.MONDAY);
            dows.add(Calendar.TUESDAY);

            dows.add(Calendar.WEDNESDAY);
            dows.add(Calendar.THURSDAY);
            dows.add(Calendar.FRIDAY);
            return dows;
        }

        //search index
        for (String[] ss : DOW_NAMES) {//find a match
            if (dow.contains(ss[0])) {//match found, add to list and break.
                dows.add(Integer.valueOf(ss[1]));
                break;
            }
        }

        return dows;
    }


}
