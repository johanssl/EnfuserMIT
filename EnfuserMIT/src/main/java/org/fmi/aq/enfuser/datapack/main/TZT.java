/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.main;

import org.fmi.aq.essentials.date.Dtime;
import java.util.concurrent.ConcurrentHashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class (the name stands for TimeZoneTransformations) is for
 * pre-calculated diurnal and seasonal values for specified datetime instances,
 * specifically designed for the target area's time zone.
 *
 * The idea: often for specific time (Dtime instance in UTC) it is necessary to
 * have the time in local time zone readily at hand. Automatic conversions to on
 * and off summer time are also needed. Then, in many situations it also
 * required to know the previous and next full hour Dtime in local time.
 *
 * It is not feasible to always create new instances of Dtimes and change the
 * time zones. The solution is to do this process only once per unique hour and
 * store the results on a HashMap. This is what this class does.
 *
 * @author Lasse Johansson
 */
public class TZT {

    public ConcurrentHashMap<Integer, Dtime[]> dtHash = new ConcurrentHashMap<>();//concurrent because the map may be updated in multithreading environment
    public final int localTS;

    public static final int PREV_LOCAL = 0; //can be used for the interpolation 
    //of local phenomena, e.g., regression coefficient for traffic emissions BUT IS NOT USED atm.
    public static final int LOCAL = 1; //can be used for local phenomena, 
    //e.g., regression coefficient for traffic emissions
    public static final int NEXT_LOCAL = 2; //can be used for the interpolation 
    //of local phenomena, e.g., regression coefficient for traffic emissions
    public static final int UTC = 3;//the basic date format used for most methods
    public static final int NEXT_UTC = 4;//can be used for linear interpolation of data
    public static final int UTC_WEEK_BACK = 5;// can be used for e.g., traffic 
    //congestion or shipping emission "forecasting"
    public static final int UTC_PREV = 6;// can be used for e.g., traffic 
    //congestion or shipping emission "forecasting"
    public static final int PREV2H_LOCAL = 7; //can be used for the interpolation
    //of local phenomena, e.g., regression coefficient for traffic emissions BUT IS NOT USED atm.

    private final static int tolerance = 96;
    private boolean autoSummertime = true;

    /**
     * Construct a new TimeZoneTransformations instance. An initial guess on the
     * date ranges that are needed are given, for which the transformations are
     * done immediately. However, the class does work outside of the given date
     * range as well.
     *
     * @param start start time
     * @param end end time
     * @param ops options to define the local timezone and the swith to summer
     * time.
     */
    public TZT(Dtime start, Dtime end, FusionOptions ops) {
        this(start, end, ops.local_zone_winterTime, ops.switchSummerTime);
    }
    
        public TZT(FusionOptions ops) {
        this(ops.getRunParameters().loadStart(),
               ops.getRunParameters().loadEnd(),
               ops.local_zone_winterTime, ops.switchSummerTime);
    }

    /**
     * Construct a new TimeZoneTransformations instance. An initial guess on the
     * date ranges that are needed are given, for which the transformations are
     * done immediately. However, the class does work outside of the given date
     * range as well.
     *
     * @param start start time
     * @param end end time
     * @param localTS local timezone
     * @param switchSummertime boolean for summertime switch
     */
    public TZT(Dtime start, Dtime end, int localTS, boolean switchSummertime) {
        this.localTS = localTS;
        this.autoSummertime = switchSummertime;
        EnfuserLogger.log(Level.FINER,TZT.class,"Setting up DSP with " 
                + start.getStringDate() + " => " + end.getStringDate());
        Dtime current = start.clone();
        current.addSeconds(-tolerance * 3600);
        int n = 0;
        while (current.systemHours() <= end.systemHours() + tolerance) {
            store(current);
            n++;

            current.addSeconds(3600);
        }
        EnfuserLogger.log(Level.FINER,TZT.class,"==========<{DSpreCalc: added " 
                + this.dtHash.size() + " entries.}>==========");
        // EnfuserLogger.log(Level.FINER,TZT.class,"\t stored "+n + " times");
    }

    /**
     * Fetch precalculated array of Dtimes (e.g., previous, next and current
     * local time) for a given Dtime in UTC-zone.
     *
     * In case no ready made Dtime[] array is found, it is created and stored to
     * hash.
     *
     * @param dt_utc time instance in UTC
     * @return an array of Dtime[] instances. The indexing and meaning of the
     * arrayed Dtimes have been defined in the static indices, e.g., element
     * [PREV_LOCAL] is the previous hour local time Dtime object, etc.
     */
    public Dtime[] getDates(Dtime dt_utc) {

        if (dt_utc.timeZone != 0) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "FusionCore.TZT.get invoked with a timestamp "
                    + "that is NOT in UTC-time!");
        }

        Dtime[] dates = this.dtHash.get(dt_utc.systemHours());
        if (dates == null) {
            EnfuserLogger.log(Level.FINER,TZT.class,"TimezoneTransforms: date "
                    + "not available? " + dt_utc.getStringDate());
            this.store(dt_utc);
            dates = this.dtHash.get(dt_utc.systemHours());

        }
        return dates;
    }

    /**
     * Fetch precalculated array of Dtimes (e.g., previous, next and current
     * local time) for a given Dtime in UTC-zone.
     *
     * @param sysH_utc systemHour (Dtime.systemHour())
     * @return an array of Dtime[] instances. The indexing and meaning of the
     * arrayed Dtimes have been defined in the static indices, e.g., element
     * [PREV_LOCAL] is the previous hour local time Dtime object, etc. NOTE:
     * This can be null if not yet available!
     */
    public Dtime[] getDates_notNullSafe(int sysH_utc) {
        return this.dtHash.get(sysH_utc);
    }

    /**
     * For a given Dtime, create and store Dtime[] array to hash.
     *
     * @param dt_utc_original the UTC-Dtime instance for which the data is
     * generated.
     */
    private void store(Dtime dt_utc_original) {

        Dtime dt_utc = dt_utc_original.clone();
        dt_utc.addSeconds(-dt_utc.sec - 60 * dt_utc.min);//remove possible seconds and minutes from the date

        Dtime dt_local = dt_utc.clone();
        dt_local.autoSummerTime = this.autoSummertime;
        dt_local.convertToTimeZone((byte) (localTS));//Takes care of summer time switch automatically

        Dtime dt_local_next = dt_local.clone();
        Dtime dt_local_prev = dt_local.clone();
        Dtime dt_local_prev2h = dt_local.clone();

        dt_local_next.addSeconds(3600);
        dt_local_prev.addSeconds(-3600);
        dt_local_prev2h.addSeconds(-3600 * 2);

        Dtime utc_next = dt_utc.clone();
        utc_next.addSeconds(3600);

        Dtime utc_weekBack = dt_utc.clone();
        utc_weekBack.addSeconds(-7 * 24 * 3600);

        Dtime utc_prev = dt_utc.clone();
        utc_prev.addSeconds(-3600);

        Dtime[] dateArr = new Dtime[8];
        dateArr[PREV_LOCAL] = dt_local_prev;
        dateArr[LOCAL] = dt_local;
        dateArr[NEXT_LOCAL] = dt_local_next;
        dateArr[UTC] = dt_utc.clone();
        dateArr[NEXT_UTC] = utc_next;
        dateArr[UTC_WEEK_BACK] = utc_weekBack;
        dateArr[UTC_PREV] = utc_prev;
        dateArr[PREV2H_LOCAL] = utc_prev;

        this.dtHash.put(dt_utc_original.systemHours(), dateArr);
    }

}// CLASS END
