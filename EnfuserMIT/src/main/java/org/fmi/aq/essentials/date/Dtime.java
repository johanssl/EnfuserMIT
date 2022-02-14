package org.fmi.aq.essentials.date;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author Lasse
 */
public class Dtime implements Serializable {

    private static final long serialVersionUID = 7526472294622776147L;//Important: change this IF changes are made that breaks serialization!

    public static void roundToHour(Dtime time) {
        time.addSeconds(-time.sec - 60 * time.min);
    }
    public short year;
    public byte month_011;
    public byte day;
    public byte hour;
    public byte min;
    public byte sec;

    public int systemHours; //previously this was a 'long'. Switched to int, since 2000*8760 is wayy below integer max. value.
//NOTE: hours FROM UTC-time!!!!!!! Not local timezone.

    public short hoursSinceThisYear;
    public byte dayOfWeek;

    public byte freeByte = 0;

    public byte timeZone = 0;//timestamp zone
    public boolean autoSummerTime = false;
    private byte summerAdd = -1;//not initialized

    public static String[] PATTERNS = {"yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyyMMdd-HHmmss",
        "yyyy-MM-dd' 'HH:mm:ss",
        "yyyy-MM-dd' 'HH:mm",
        "yyyy-MM-dd-HH",
        "dd.MM.yyyy' 'HH.mm",
        "dd.MM.yyyy'T'HH:mm",
        "yyyyMMddHHmm",
        "yyyyMMddHH",
        "dd.MM.yyyy'T'HH:mm",
        "dd.MM.yyyy' 'HH:mm",
        "yyyy-MM-dd'T'HH-mm-ss",
        "yyyy-MM-dd'T'HH-mm"

    };

    public final static int STAMP_NOZONE = 0;
    public final static int STAMP_FULL_ZONED = 4;
    public final static int STAMP_NO_YEAR = 1;
    public final static int STAMP_SHORT = 2;
    public final static int STAMP_NO_TIME = 3;

    private static void ensureFormat() {
        if (!CALENDAR_TO_UTC) {
            TimeZone tzone = TimeZone.getTimeZone("UTC");
            tzone.setDefault(tzone);
            CALENDAR_TO_UTC = true;
            EnfuserLogger.log(Level.FINER,Dtime.class,"UTCdate: calendar initialized.");
        }

    }
    
    private final static String[] MONTHS = {
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    };
    public String getMonthName() {
        return MONTHS[this.month_011];
    }

    public static String[] separateZone_fullhours(String stamp) {
        //assume formate.g., 2018-08-20T09:00:00+08:00
        String[] split = stamp.split(":00:00");
        String zone = split[1].replace(":00", "");
        zone = zone.replace("+", "");

        int tz = Integer.valueOf(zone);
        return new String[]{split[0] + ":00:00", tz + ""};

    }

    public Dtime(String timestamp, int ts, boolean autoSummer) {

        ensureFormat();

        this.autoSummerTime = autoSummer;
        Date date = null;
        Calendar cal;

        SimpleDateFormat formatter;

        for (String PATTERNS1 : PATTERNS) {
            formatter = new SimpleDateFormat(PATTERNS1);
            formatter.setLenient(false);
            try {
                date = formatter.parse(timestamp);
                break;
            } catch (ParseException ex4) {
            }
        }

        cal = GregorianCalendar.getInstance(); // creates a new calendar instance
        cal.setTime(date);   // assigns calendar to given date                   

        this.init(cal, ts);
        this.applySummer();

    }

    public Dtime(String timestamp) {
        this(timestamp, 0, false);
    }

    protected Dtime() {
        ensureFormat();
    }

    public int SecOfYear() {
        int s = this.hoursSinceThisYear * 3600;
        s += (this.min * 60 + this.sec);
        return s;
    }

    public void convertToTimeZone(byte newZone) {
        convertToTimeZone(newZone, true);
    }

    public void convertToUTC() {
        this.summerAdd = -1;
        this.autoSummerTime = false;
        int diff = (0 - this.timeZone) * 3600;
        this.timeZone = 0;
        this.addSeconds(diff, false);
    }

    private void convertToTimeZone(byte newZone, boolean autoSummer) {
        this.summerAdd = -1;
        this.autoSummerTime = true;

        int diff = (newZone - this.timeZone) * 3600;
        this.timeZone = newZone;
        this.addSeconds(diff, autoSummer);

    }


    private void init(Calendar cal, int ts) {

        this.year = (short) cal.get(Calendar.YEAR);
        this.month_011 = (byte) (cal.get(Calendar.MONTH));
        this.day = (byte) cal.get(Calendar.DAY_OF_MONTH);
        this.hour = (byte) cal.get(Calendar.HOUR_OF_DAY);
        this.timeZone = (byte) ts;

        try {
            this.min = (byte) cal.get(Calendar.MINUTE);
            this.sec = (byte) cal.get(Calendar.SECOND);

        } catch (Exception e) {
            this.min = 0;
            this.sec = 0;
        }

        this.systemHours = (int) ((cal.getTimeInMillis() / 1000 / 3600) - this.timeZone); // systemhours must be independentof time zone
        this.hoursSinceThisYear = (short) ((cal.get(Calendar.DAY_OF_YEAR) - 1) * 24 + cal.get(Calendar.HOUR_OF_DAY));
        this.dayOfWeek = (byte) cal.get(Calendar.DAY_OF_WEEK);

        //summerTime     
    }

    private void applySummer() {

        if (this.autoSummerTime) {

            boolean isSummer = isSummerTime(this);
            if (isSummer && this.summerAdd <= 0) {
                // EnfuserLogger.log(Level.FINER,Dtime.class,"Switching to summer time!");
                this.convertToTimeZone((byte) (timeZone + 1), false);
                this.summerAdd = 1;
            } else if (!isSummer && this.summerAdd > 0) {
                // EnfuserLogger.log(Level.FINER,Dtime.class,"Switching to winter time!");
                this.convertToTimeZone((byte) (timeZone - 1), false);
                this.summerAdd = 0;
            }

        }

    }
    
    public String dowString() {
       if (dayOfWeek == Calendar.MONDAY) {
            return "Mon";
        } else if (dayOfWeek == Calendar.TUESDAY) {
            return "Tue";
        } else if (dayOfWeek == Calendar.WEDNESDAY) {
            return "Wed";
        } else if (dayOfWeek == Calendar.THURSDAY) {
            return "Thu";
        } else if (dayOfWeek == Calendar.FRIDAY) {
            return "Fri";
        } else if (dayOfWeek == Calendar.SATURDAY) {
            return "Sat";
        } else {
            return "Sun";
        }
    }
    
     public String getDescriptiveDate() {
        String s = "";
        if (dayOfWeek == Calendar.MONDAY) {
            s += "Mon, ";
        } else if (dayOfWeek == Calendar.TUESDAY) {
            s += "Tue, ";
        } else if (dayOfWeek == Calendar.WEDNESDAY) {
            s += "Wed, ";
        } else if (dayOfWeek == Calendar.THURSDAY) {
            s += "Thu, ";
        } else if (dayOfWeek == Calendar.FRIDAY) {
            s += "Fri, ";
        } else if (dayOfWeek == Calendar.SATURDAY) {
            s += "Sat, ";
        } else {
            s += "Sun, ";
        }

        String h = hour + ":";
        if (hour < 10) {
            h = "0" + h;
        }

        String mins = this.min + "";
        if (this.min < 10) {
            mins = "0" + min;
        }
        s += h + mins;
        return s;
    }

    public boolean inSummerTime() {
        return this.summerAdd == 1;
    }

    public static Dtime UTC_fromSystemSecs(int sysSecs) {
        return fromSystemSecs(sysSecs, 0, false);
    }

    public static Dtime fromSystemSecs(int sysSecs, int ts, boolean autoSummer) {
        Dtime dt = new Dtime("1970-01-01T00:00:00", ts, autoSummer);//evaluates system timezone as well!
        dt.addSeconds(sysSecs + ts * 3600, true);
        return dt;
    }

    private void addSeconds(int seconds, boolean summer) {

        SimpleDateFormat formatter = new SimpleDateFormat(PATTERNS[0]);
        formatter.setLenient(false);
        try {

            Date date = formatter.parse(this.getStringDate());
            Calendar cal = GregorianCalendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.SECOND, seconds);

            this.init(cal, (int) this.timeZone); // doesn't change the timeZone

            if (summer) {
                this.applySummer();
            }

        } catch (ParseException ex) {
            Logger.getLogger(Dtime.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void addSeconds(int seconds) {
        this.addSeconds(seconds, true);
    }

    @Override
    public Dtime clone() {

        Dtime dt = new Dtime();

        dt.year = this.year;
        dt.month_011 = this.month_011;
        dt.day = this.day;
        dt.hour = this.hour;
        dt.min = this.min;
        dt.sec = this.sec;

        dt.systemHours = this.systemHours;
        dt.hoursSinceThisYear = this.hoursSinceThisYear;
        dt.dayOfWeek = this.dayOfWeek;
        dt.freeByte = this.freeByte;

        dt.timeZone = this.timeZone;
        dt.autoSummerTime = this.autoSummerTime;
        dt.summerAdd = this.summerAdd;

        return dt;
    }

    public int systemHours() {
        return this.systemHours;
    }

    public long systemSeconds() {
        long l = (long) (this.systemHours);
        l *= 3600;
        l += (this.min * 60 + this.sec);
        return l;
    }

    public String getStringDate() {
        return this.getStringDate(Dtime.STAMP_FULL_ZONED);
    }

    public String getStringDate_noTS() {
        return this.getStringDate(Dtime.STAMP_NOZONE);
    }

    public String getStringDate_netCDF_zoned() {
        String s = this.getStringDate(Dtime.STAMP_FULL_ZONED);
        s = s.replace("T", " ");
        return s;
    }

    public String getStringDate_netCDF_inUTC() {
        Dtime temp = UTC_fromSystemSecs((int) this.systemSeconds());

        String s = temp.getStringDate(Dtime.STAMP_FULL_ZONED);
        s = s.replace("T", " ");
        return s;
    }

    public void initOffset(Dtime other) {
        this.freeByte = (byte) Math.abs(other.systemHours() - this.systemHours());
    }

    /**
     * Returns 01 - 12.
     *
     * @return a String that has "0" for values 1 to 9.
     */
    public String getMonthString() {

        String smonth;
        int mon = this.month_011 + 1; //e.g., changes january (0) to 1.
        if (mon < 10) {
            smonth = "0" + mon;
        } else {
            smonth = mon + "";
        }
        return smonth;
    }

    public String getStringDate(int length) {

        String y = this.year + "-";
        if (length == Dtime.STAMP_NO_YEAR || length == Dtime.STAMP_SHORT) {
            y = "";
        }

        String smonth = this.getMonthString();

        String sday;
        if (this.day < 10) {
            sday = "0" + this.day;
        } else {
            sday = String.valueOf(this.day);
        }

        String shour;
        if (this.hour < 10) {
            shour = "0" + this.hour;
        } else {
            shour = String.valueOf(this.hour);
        }

        String smin;
        if (this.min < 10) {
            smin = "0" + this.min;
        } else {
            smin = String.valueOf(this.min);
        }

        String ssec;
        if (this.sec < 10) {
            ssec = ":0" + this.sec;
        } else {
            ssec = ":" + String.valueOf(this.sec);
        }

        if (length == Dtime.STAMP_SHORT) {
            ssec = "";
        }

        String temp = y + smonth + "-" + sday;
        if (length != Dtime.STAMP_NO_TIME) { // add hour min and sec
            temp += "T" + shour + ":" + smin + ssec;
        }

        if (length == STAMP_FULL_ZONED) {
            temp += this.getZoneString();
        }

        return temp;
    }

    public String getZoneString() {
        if (this.timeZone < 0) {
            return "" + this.timeZone + ":00";
        } else {
            return "+" + this.timeZone + ":00";
        }
    }

    public String getStringDate_YYYYMMDD() {
        String sday;
        if (this.day < 10) {
            sday = "0" + this.day;
        } else {
            sday = String.valueOf(this.day);
        }
        return this.year + this.getMonthString() + sday;
    }

    public String getStringDate_YYYY_MM_DD() {

        String y = this.year + "-";

        String smonth = this.getMonthString();

        String sday;
        if (this.day < 10) {
            sday = "0" + this.day;
        } else {
            sday = String.valueOf(this.day);
        }

        String temp = y + smonth + "-" + sday;

        return temp;
    }

    public String getStringDate_YYYY_MM() {
        String y = this.year + "-";

        String smonth = this.getMonthString();
        String temp = y + smonth;

        return temp;
    }

    public String getStringDate_fileFriendly() {
        return getStringDate_YYYY_MM_DDTHH();
    }

    public String getStringDate_YYYY_MM_DDTHH() {

        String y = this.year + "-";

        String smonth = this.getMonthString();

        String sday;
        if (this.day < 10) {
            sday = "0" + this.day;
        } else {
            sday = String.valueOf(this.day);
        }

        String shour;
        if (this.hour < 10) {
            shour = "0" + this.hour;
        } else {
            shour = String.valueOf(this.hour);
        }

        String temp = y + smonth + "-" + sday + "T" + shour;

        return temp;
    }

// this method returns date in String form (3rd format)
    public String getStringDateFormat3() {

        String smonth = this.getMonthString();

        String sday;
        if (this.day < 10) {
            sday = "0" + this.day;
        } else {
            sday = String.valueOf(this.day);
        }

        String shour;
        if (this.hour < 10) {
            shour = "0" + this.hour;
        } else {
            shour = String.valueOf(this.hour);
        }

        String smin;
        if (this.min < 10) {
            smin = "0" + this.min;
        } else {
            smin = String.valueOf(this.min);
        }

        String ssec;
        if (this.sec < 10) {
            ssec = "0" + this.sec;
        } else {
            ssec = String.valueOf(this.sec);
        }

        String temp = this.year + smonth + sday + shour + "00";

        return temp;
    }

//older JavaSteam methods
    private String getStringDate(boolean year, boolean month) {

        String smonth = this.getMonthString();
        String sday;
        if (this.day < 10) {
            sday = "0" + this.day;
        } else {
            sday = String.valueOf(this.day);
        }

        String shour;
        if (this.hour < 10) {
            shour = "0" + this.hour;
        } else {
            shour = String.valueOf(this.hour);
        }

        String smin;
        if (this.min < 10) {
            smin = "0" + this.min;
        } else {
            smin = String.valueOf(this.min);
        }

        String ssec;
        if (this.sec < 10) {
            ssec = "0" + this.sec;
        } else {
            ssec = String.valueOf(this.sec);
        }

        String temp = "";
        if (year) {
            temp += this.year;
        }
        if (month) {
            temp += "-" + smonth;
        }
        temp += "-" + sday + "T" + shour + ":" + smin + ":" + ssec;

        return temp;
    }

    public String getStringDate_noYM() {
        return getStringDate(false, false);
    }

    public final static int ALL = 0;
    public final static int FULL_HOURS = 1;
    public final static int NO_HOURS_MINS = 2;

    public static boolean IN_UTC = true;
    public static boolean SYSTEM_TS = false;

    public static Dtime getSystemDate() {
        return getSystemDate_utc(false, ALL);
    }

    public static Dtime getSystemDate_FH() {
        return getSystemDate_utc(false, FULL_HOURS);
    }

    public static Dtime getSystemDate_utc(boolean print, int details) {

        ensureFormat();

        Calendar cal = Calendar.getInstance();
        int rawZone = (int) (cal.getTimeZone().getRawOffset() / 3600 / 1000);

        Date date = cal.getTime();
        boolean inDayl = cal.getTimeZone().inDaylightTime(date);
        if (inDayl) {
            rawZone++;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        String dateNow = formatter.format(cal.getTime());

        Dtime initDt = new Dtime(dateNow, rawZone, false);
        if (print) {
            EnfuserLogger.log(Level.FINER,Dtime.class,"System time: " + dateNow);
        }
        if (print) {
            EnfuserLogger.log(Level.FINER,Dtime.class,"System timeZone: " + rawZone + ", dayLight = " + inDayl);
        }

        Dtime dt = initDt.clone();

        if (details == ALL) {

        } else if (details == FULL_HOURS) {
            dt.addSeconds(-dt.min * 60 - dt.sec, false);
        } else {
            dt.addSeconds(-dt.hour * 3600 - dt.min * 60 - dt.sec, false);
        }

        return dt;

    }

    private static int[][] summerDays_2005 = {
        {27, 30},//2005
        {26, 29},//2006
        {25, 28},//2007
        {30, 26},//2008
        {29, 25},//2009
        {28, 31},//2010
        {27, 30},//2011
        {25, 28},//2012
        {31, 27},//2013
        {30, 26},//2014
        {29, 25},//2015
        {27, 30},//2016
        {26, 29},//2017
        {25, 28},//2018
        {31, 27},//2019
        {29, 25},//2020
        {28, 31},//2021
        {27, 30},//2022
        {26, 29},//2023
        {31, 27},//2024
        {30, 26},//2025
        {29, 25},//2026
        {28, 31},//2027
    };

    public static boolean isSummerTime(Dtime dt) {
        if (dt.month_011 >= Calendar.MARCH && dt.month_011 <= Calendar.OCTOBER) {//summer time, roughly 

            int[] days = summerDays_2005[dt.year % 2005];//TODO: this will get OBSOLETE in 2028!
            int mard = days[0];
            int octd = days[1];

            if (dt.month_011 == Calendar.MARCH) {//special treatment

                if (dt.day > mard || (dt.day == mard && dt.hour > 2)) {
                    return true;
                } else {
                    return false;
                }

            } else if (dt.month_011 == Calendar.OCTOBER) {//special treatment

                if (dt.day < octd || (dt.day == octd && dt.hour <= 2)) {
                    return true;
                } else {
                    return false;
                }

            } else {//months 4,5,6,7,8,9
                return true;
            }

        } else {
            return false;
        }
    }
    
     public static Dtime doyToDate_UTC(int year, int doy) {
         return doyToDate_UTC(year, doy, 0, false);
     }
    
    /**
     * Generate a Dtime object from the year and day of year (doy).
     *
     * @param year
     * @param doy = Day of year (1-365)
     * @param ts time zone for source time stamp
     * @param autoSummer use true if the source region has summer time switch
     * @return Dtime object
     */
    public static Dtime doyToDate_UTC(int year, int doy, int ts, boolean autoSummer) {
        
        if (ts==0 && !autoSummer) return new Dtime(year + "-01-01T00:00:00");
        
        Dtime dt = new Dtime(year + "-01-01T00:00:00", ts, autoSummer);
        dt.addSeconds(24*3600*doy);
        dt.convertToUTC();//convert it to UTC
        return dt;
    }

    private static boolean CALENDAR_TO_UTC = false;

    public static void main(String[] args) {
        EnfuserLogger.log(Level.FINER,Dtime.class,"UNIX time test");

        Dtime dtest = new Dtime("1970-01-01T00:00:00");
        int secs = 1361873040;
        dtest.addSeconds(secs);
        EnfuserLogger.log(Level.FINER,Dtime.class,dtest.getStringDate());

        String st1 = "1.1.2016T01:00";
        EnfuserLogger.log(Level.FINER,Dtime.class,"TESTER = " + st1);
        Dtime ddd = new Dtime(st1, 2, true);
        EnfuserLogger.log(Level.FINER,Dtime.class,"LOCAL =" + ddd.getStringDate());
        ddd.convertToUTC();
        EnfuserLogger.log(Level.FINER,Dtime.class,"UTC = " + ddd.getStringDate());

        st1 = "2018-02-20T09:00:00";
        EnfuserLogger.log(Level.FINER,Dtime.class,"TESTER = " + st1);
        ddd = new Dtime(st1, 2, true);
        EnfuserLogger.log(Level.FINER,Dtime.class,"LOCAL =" + ddd.getStringDate());
        ddd.convertToUTC();
        EnfuserLogger.log(Level.FINER,Dtime.class,"UTC = " + ddd.getStringDate());

        String stampp = "2018-08-20T09:00:00+08:00";
        String stampp2 = "2018-08-20T09:00:00-11:00";
        String[] tmp = separateZone_fullhours(stampp);
        EnfuserLogger.log(Level.FINER,Dtime.class,tmp[0] + "\t\t" + tmp[1]);
        tmp = separateZone_fullhours(stampp2);
        EnfuserLogger.log(Level.FINER,Dtime.class,tmp[0] + "\t\t" + tmp[1]);

        if (true) {
            return;
        }
        EnfuserLogger.log(Level.FINER,Dtime.class,"January: " + Calendar.JANUARY);
        EnfuserLogger.log(Level.FINER,Dtime.class,"feb: " + Calendar.FEBRUARY);
        EnfuserLogger.log(Level.FINER,Dtime.class,"mar: " + Calendar.MARCH);
        EnfuserLogger.log(Level.FINER,Dtime.class,"apr: " + Calendar.APRIL);
        EnfuserLogger.log(Level.FINER,Dtime.class,"May: " + Calendar.MAY);
        EnfuserLogger.log(Level.FINER,Dtime.class,"June: " + Calendar.JUNE);
        EnfuserLogger.log(Level.FINER,Dtime.class,"July: " + Calendar.JULY);
        EnfuserLogger.log(Level.FINER,Dtime.class,"aug: " + Calendar.AUGUST);
        EnfuserLogger.log(Level.FINER,Dtime.class,"sep: " + Calendar.SEPTEMBER);
        EnfuserLogger.log(Level.FINER,Dtime.class,"oct: " + Calendar.OCTOBER);
        EnfuserLogger.log(Level.FINER,Dtime.class,"nov: " + Calendar.NOVEMBER);
        EnfuserLogger.log(Level.FINER,Dtime.class,"dec: " + Calendar.DECEMBER);

        Dtime sys = Dtime.getSystemDate_utc(true, ALL);
        Dtime sysLocal = sys.clone();
        sysLocal.convertToTimeZone((byte) 2);
        EnfuserLogger.log(Level.FINER,Dtime.class,"System local time: " + sysLocal.getStringDate());

        sysLocal = sys.clone();
        sysLocal.convertToTimeZone((byte) 2);
        EnfuserLogger.log(Level.FINER,Dtime.class,"System local (winter) time: " + sysLocal.getStringDate());

        Dtime test = new Dtime("2017-03-26T01:00:00", 0, true);
        EnfuserLogger.log(Level.FINER,Dtime.class,test.getStringDate() + ", hours = " + test.systemHours);
        Dtime epoc = Dtime.fromSystemSecs((int) test.systemSeconds(), 0, true);
        boolean summerT = isSummerTime(test);
        EnfuserLogger.log(Level.FINER,Dtime.class,"\t\t" + test.getStringDate() + " is summerTime = " + summerT);
        EnfuserLogger.log(Level.FINER,Dtime.class,"\t\t from seconds: " + epoc.getStringDate());
        test.addSeconds(3600, true);
        EnfuserLogger.log(Level.FINER,Dtime.class,"\tAdding 1h =>" + test.getStringDate() + ", hours = " + test.systemHours);
        test.convertToTimeZone((byte) 2);
        EnfuserLogger.log(Level.FINER,Dtime.class,"\tchanging ts+2 =>" + test.getStringDate() + ", hours = " + test.systemHours);
        epoc = Dtime.fromSystemSecs((int) test.systemSeconds(), 2, true);
        EnfuserLogger.log(Level.FINER,Dtime.class,"\t\t from seconds: " + epoc.getStringDate());

        Dtime current = new Dtime("2017-03-25T00:00:00");
        Dtime curLocal = new Dtime("2017-03-25T02:00:00", (byte) 2, true);

        for (int k = 0; k < 48; k++) {
            String stamp = curLocal.getStringDate_netCDF_inUTC();
            boolean sum = curLocal.inSummerTime();

            Dtime curLoc2 = curLocal.clone();
            curLoc2.convertToTimeZone((byte) (-4));

            EnfuserLogger.log(Level.FINER,Dtime.class,current.getStringDate() + "\t" + current.systemHours() + " sum=" + sum + "\t" + curLocal.getStringDate()
                    + "\t" + curLocal.systemHours() + "\t diff = " + (current.systemHours() - curLocal.systemHours()) + "\t" + stamp + "\t" + curLoc2.getStringDate());

            current.addSeconds(3600);
            curLocal.addSeconds(3600);
        }

        current = new Dtime("2017-10-28T00:00:00");
        curLocal = new Dtime("2017-10-28T02:00:00", (byte) 2, true);

        for (int k = 0; k < 48; k++) {
            String stamp = curLocal.getStringDate_netCDF_inUTC();
            boolean sum = curLocal.inSummerTime();

            Dtime curLoc2 = curLocal.clone();
            curLoc2.convertToTimeZone((byte) (-4));

            Dtime ut = curLoc2.clone();
            ut.convertToUTC();

            EnfuserLogger.log(Level.FINER,Dtime.class,current.getStringDate() + "\t" + current.systemHours() + " sum=" + sum + "\t" + curLocal.getStringDate()
                    + "\t" + curLocal.systemHours() + "\t diff = " + (current.systemHours() - curLocal.systemHours()) + "\t" + stamp + "\t" + curLoc2.getStringDate()
                    + "\t" + ut.getStringDate());

            current.addSeconds(3600);
            curLocal.addSeconds(3600);
        }

    }



}
