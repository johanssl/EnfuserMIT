/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import java.util.ArrayList;
import java.util.Calendar;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;

/**
 *
 * @author johanssl
 */
public class ConditionalTempoAverager extends ConditionalAverager{

    public final static int FILT_ALL = 0;//accept all
    public final static int FILT_MONTH = 1;//indexing based on month
    public final static int FILT_DOY = 2;//indexing based on day-of-year
    public final static int FILT_WEEKDAY = 3;//indexing based on day-of-week
    public final static int FILT_DIURNAL = 4;//indexing based on hour-of-day (with sat, and Sun separatly)
    
    public final static String [] FILT_NAMES = {
    "All",
    "Month",
    "Day-of-Year",
    "WeekDay",
    "HourOfDay"
    };
    
    final int type;
    final String customization;
    final String[] xHeader;
    //for averaging for each inserted data point. 
    final TZT tzt;
    public ConditionalTempoAverager(int type, String customization, FusionOptions ops, TZT tzt) {
        this.ops = ops;
        this.type = type;
        this.datasetLabel = "#" + FILT_NAMES[type];
        this.customization=customization;
        this.tzt = tzt;
        
        if (type == FILT_ALL) {
            xHeader = new String[]{"All"};
            
        } else if (type == FILT_MONTH) {
            xHeader = new String[]{
                "Jan",
                "Feb",
                "Mar",
                "Apr",
                "May",
                "Jun",
                "Jul",
                "Aug",
                "Sep",
                "Oct",
                "Nov",
                "Dec"};
            
        } else if (type == FILT_DOY) {
            String[] temp = new String[366];
            for (int i =0;i<temp.length;i++) {
                temp[i]=i+1+"";
            }
            xHeader =temp;
              
         } else if (type == FILT_WEEKDAY) {
            xHeader = new String[]{
                "Monday",
                "Tuesday",
                "Wednesday",
                "Thursday",
                "Friday",
                "Saturday",
                "Sunday"};
            
        } else if (type == FILT_DIURNAL) {
            String[] temp = new String[24*3];
            for (int i =0;i<temp.length;i++) {
                int mod = i%24;
                String s ="Mon-fri_";
                if (i>23) s ="Sat_";
                if (i>47) s="Sun_";
                temp[i]=s + mod;
            } 
            xHeader=temp;
            
        } else {
            xHeader=null;//ERROR
        }
        
        
        if (this.dat_yx==null) {
            int ylen = StatsCruncher.HEADERS_SOURCTYPE.length +ops.CATS.CATNAMES.length;
            int xlen = this.xHeader.length;
            this.dat_yx = new float[ylen][xlen];
            this.counters = new int[ylen][xlen];
        }
        this.ySkips.put(StatsCruncher.Y_NAME(StatsCruncher.RAW_OBS), Boolean.TRUE);
    }
    
    public static ArrayList<ConditionalAverager> getDefault(FusionOptions ops, TZT tzt) {
        ArrayList<ConditionalAverager> arr = new ArrayList<>();
            for (int type =0;type < FILT_NAMES.length;type++) {
                arr.add(new ConditionalTempoAverager(type,null,ops,tzt));
            }
        
        return arr;        
    }

    @Override
    protected int getXIndex(EvalDat ed) {
        Dtime dt = ed.dt();
        Dtime[] all = this.tzt.getDates(dt);
        Dtime local = all[TZT.LOCAL];
       if (type == FILT_ALL) {
           return 0;
            
        } else if (type == FILT_MONTH) {
            return local.month_011;
            
        } else if (type == FILT_DOY) {
            return local.hoursSinceThisYear/24;
              
         } else if (type == FILT_WEEKDAY) {
            
            if ((int)local.dayOfWeek==Calendar.MONDAY) return 0; 
            if ((int)local.dayOfWeek==Calendar.TUESDAY) return 1;
            if ((int)local.dayOfWeek==Calendar.WEDNESDAY) return 2; 
            if ((int)local.dayOfWeek==Calendar.THURSDAY) return 3;
            if ((int)local.dayOfWeek==Calendar.FRIDAY) return 4; 
            if ((int)local.dayOfWeek==Calendar.SATURDAY) return 5;
            return 6;
            
        } else if (type == FILT_DIURNAL) {
            int ind = local.hour;
            if ((int)local.dayOfWeek==Calendar.SATURDAY) ind = 24+local.hour;
            if ((int)local.dayOfWeek==Calendar.SUNDAY) ind = 48+local.hour;
            return ind;
            
        } else {
            return -1;//error!
        }
    }

    @Override
    public String[] xHeader() {
       return this.xHeader;
    }

}
