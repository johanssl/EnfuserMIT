/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl IndexedData is a gridded data structure that benefits from
 * the sparsity of gridded data. Only non-zero values in H x W x Z -grid will
 * consume memory Proper indexing of occupied cells enable fast updating of new
 * and existing cells; ArrayList-searches are not used
 */
public abstract class SparseGrid implements Serializable {

    private static final long serialVersionUID = 7526488293822776147L;//Important: change this IF changes are made that breaks serialization!  
    public final HashMap<Long, String> dateStrs; // list of h-creatures 
    public final short W;
    public final short H;

    public final String[] cellContentNames;

    public final int cellContentSize;

    public String wDescription = "degrees, east";
    public String hDescription = "degrees, north";
    public String tDecription = "time";

    public long minKey = Long.MAX_VALUE;
    public long maxKey = Long.MIN_VALUE;

    public final double minLat;
    public final double maxLat;
    public final double minLon;
    public final double maxLon;
    public final Boundaries b;
    public final double dlat, dlon;

    public String unit = "kg/cell";

    public ByteConverter byteConverter;

    public final int timeInterval_min;
    public static final int TIME_HOUR = 60;

    public static final int TIME_5MIN = 5;
    public static final int TIME_15MIN = 15;
    public static final int TIME_30MIN = 30;
    public static final int TIME_DAY = (24 * 60);
    public static final int TIME_6H = (6 * 60);
    public static final int TIME_3H = (3 * 60);
    //  public static final int TIME_YEARS =4;

    public final static String[] TIME_INTERVALS = {"HOUR", "3 HOUR", "6 HOURS", "DAYS", "5-MINUTES", "15-MINUTES", "30-MINUTES"};
    public final static int[] TIME_INTERVALS_MINS = {TIME_HOUR, TIME_3H, TIME_6H, TIME_DAY, TIME_5MIN, TIME_15MIN, TIME_30MIN};

    public SparseGrid(short maxH, short maxW, Boundaries b,
            String[] cellContentNames, int timeInterval) {

        this.timeInterval_min = timeInterval;
        this.cellContentNames = cellContentNames;
        this.cellContentSize = cellContentNames.length;
        this.byteConverter = new ByteConverter(cellContentSize);

        this.b = b;
        minLat = b.latmin;
        maxLat = b.latmax;
        minLon = b.lonmin;
        maxLon = b.lonmax;
        ;
        this.W = maxW;
        this.H = maxH;

        this.dlat = (b.latmax - b.latmin) / H;
        this.dlon = (b.lonmax - b.lonmin) / W;

        this.dateStrs = new HashMap<>();
        EnfuserLogger.log(Level.FINER,this.getClass(),this.getDescription());
        

    }

    public int getCellContentIndex(String type) {

        for (int i = 0; i < this.cellContentNames.length; i++) {
            if (type.equals(cellContentNames[i])) {
                return i;
            }
        }
        return -1;//not found
    }

    public String getSaveFileName() {
        String s = "";
        s += this.getStartDate().replace(":", "-") + "_" + this.getEndDate().replace(":", "-");
        s += "_" + this.b.toText_fileFriendly(null);

        return s + ".shg";
    }

    /**
     * Prints out the relevant specs of the IndexedData instance, such as
     * dimensions, units, variable descriptions.
     *
     * @return a description text.
     */
    public String getDescription() {

        String n = System.getProperty("line.separator");
        //   String o = "IndexedData (t="+this.maxT+", h="+ this.maxH + ", w ="+this.maxW+", z =" +maxZ+ ")"+n;
        String o = "SparseGrid ( h=" + this.H + ", w =" + this.W + ")" + n;

        o += "includes " + (this.maxKey - this.minKey + 1) + " layers." + n;
        o += "Time interval in minutes is " + this.timeInterval_min + n;
        o += "\t" + this.dateStrs.get(this.minKey) + "  -  " + this.dateStrs.get(this.maxKey) + n;

        o = o + this.hDescription + ": " + this.minLat + " to " + this.maxLat + n;
        o = o + this.wDescription + ": " + this.minLon + " to " + this.maxLon + n;
        //  o = o+this.tDecription+": "+this.dtDescription+n;
        o = o + "unit: " + this.unit + " per " + this.timeInterval_min + " minutes" + n;

        if (this.byteConverter.wasConverted) {
            o = o + "All values have been compressed to BYTES. Round-up errors may exist." + n;
        }

        String variables = "";
        for (String cellContentName : this.cellContentNames) {
            variables += cellContentName + ", ";
        }
        o = o + "Variables: " + variables + n;
        return o;
    }

    public void setUnit(String st) {
        this.unit = st;
    }

    public String getUnit() {
        return this.unit;
    }

    /*
        protected String adjustDateKeyString(Datetime dt) {
           
         //Datetime newDt = dt.clone();
         
         if (this.timeInterval_min == TIME_HOUR) {
             
            // newDt.addSeconds(-dt.sec - 60*dt.min);
             return dt.getStringDate_YYYY_MM_DDTHH()+":00:00";
             
         } else if (this.timeInterval_min == TIME_DAY) {
             
            return dt.getStringDate_YYYY_MM_DD()+"T00:00:00";
            
         } else if (this.timeInterval_min == TIME_30MIN) {

            if (dt.min>=30) {
                return dt.getStringDate_YYYY_MM_DDTHH()+":30:00";
            } else {
                return dt.getStringDate_YYYY_MM_DDTHH()+":00:00";
            }
            
         } else  { // 15-min
             
            if (dt.min>=45) {
                return dt.getStringDate_YYYY_MM_DDTHH()+":45:00";
            } else if (dt.min>=30) {
                return dt.getStringDate_YYYY_MM_DDTHH()+":30:00";
            } else if (dt.min>=15) {
                return dt.getStringDate_YYYY_MM_DDTHH()+":15:00";
            } else {
                return dt.getStringDate_YYYY_MM_DDTHH()+":00:00";
            }
                    
            
         }
         
         
        // return newDt.getStringDate();
         
        }
     */
 /*
 public long dateKeyToSysSecs(long dateKey) {
  return dateKey*timeInterval_min*60;
         
}
     */
    public long getT_key(Dtime dt) {
        return getT_key(dt.systemSeconds());
    }

    public long getT_key(long sysSecs) {
        return getT_key(sysSecs, this);
    }

    public static long getT_key(long sysSecs, SparseGrid shg) {
        long l = (long) (sysSecs / (shg.timeInterval_min * 60));
        l *= (shg.timeInterval_min * 60);
        return l;
    }

    public Long getClosestTimeKey(Dtime dt, boolean mustBeWithinLimits) {

        long key = getT_key(dt);//search time key
        if (this.dateStrs.get(key) != null) {
            return key;//was found instantly
        }
        //OOB checks
        if (key < this.minKey) {
            if (mustBeWithinLimits) {
                return null;
            }
            return minKey;
        }
        if (key > this.maxKey) {
            if (mustBeWithinLimits) {
                return null;
            }
            return maxKey;
        }

        Long bestKey = null;
        long minDiff = Long.MAX_VALUE;
        //is within the limits, but the proper value must be searched
        for (long testKey : this.dateStrs.keySet()) {
            long diff = Math.abs(testKey - key);
            if (diff < minDiff) {
                minDiff = diff;
                bestKey = testKey;

                if (diff <= this.timeInterval_min * 60) {
                    break;//a better value cannot be found.
                }
            }
        }
        return bestKey;
    }

    public Set<Long> getDates_forDataIncluded() {
        Set<Long> keys = this.dateStrs.keySet();
        return keys;
    }

    public ArrayList<Long> getDates_wholeInterval() {
        ArrayList<Long> allKeys = new ArrayList<>();

        for (long l = this.minKey; l <= this.maxKey; l += this.timeInterval_min * 60) {
            allKeys.add(l);
        }

        return allKeys;

    }

    public boolean HWok(int h, int w) {
        return GeoGrid.HWok(h, w, this.H, this.W);
    }

    public String getStartDate() {
        return this.dateStrs.get(this.minKey);
    }

    public String getEndDate() {
        return this.dateStrs.get(this.maxKey);
    }

    /**
     * Return all non-null data vectors closest to this time instance.
     *
     * @param dt the selected time
     * @return a list of datapoint locations (non-empty) for the defined time.
     */
    public abstract ArrayList<CoordinatedVector> getCoordinatedDataVectors(Dtime dt);

    public boolean sumOrCreateContent_dateLatLon(float[] incr, Dtime dt, double lat, double lon, boolean forceNew) {
        //check if this is a new geoX
        short h = (short) getH(this.b, lat, dlat);
        short w = (short) getW(this.b, lon, dlon);
        boolean ok = this.HWok(h, w);
        if (!ok) {
            return false;
        }

        return this.sumOrCreateContent_HW(incr, h, w, forceNew, dt, dt.systemSeconds());
    }

    public boolean sumOrCreateContent_fastLatLon(float[] incr, long sysSecs, double lat, double lon, boolean forceNew) {

        short h = (short) getH(this.b, lat, dlat);
        short w = (short) getW(this.b, lon, dlon);
        boolean ok = this.HWok(h, w);
        if (!ok) {
            return false;
        }

        return this.sumOrCreateContent_HW(incr, h, w, forceNew, null, sysSecs);

    }

    public boolean sumOrCreateContent_fastHW(float[] incr, long sysSecs, short h, short w, boolean forceNew) {
        return this.sumOrCreateContent_HW(incr, h, w, forceNew, null, sysSecs);
    }

//public abstract boolean sumOrCreateContent_HW_singular(float inc, int q_ind, short h, short w, boolean forceNew,Dtime dt, long sysSecs) ;
    public boolean sumOrCreateContent_HW_singular(float inc, int q_ind, short h, short w, boolean forceNew, Dtime dt, long sysSecs) {
        long tKey = this.getT_key(sysSecs);

        //get array
        float[] incr = this.getCellContent_tkHW(tKey, h, w);
        if (incr == null) {//does not exist => create new
            incr = new float[this.cellContentNames.length];
            incr[q_ind] = inc;//it is zero initially so "forceNew" does not matter.
            return sumOrCreateContent_HW(incr, h, w, forceNew, dt, sysSecs);
        } else {//exists already
            if (forceNew) {
                incr[q_ind] = inc;//replace
            } else {
                incr[q_ind] += inc;//sum
            }
            return true;
        }
    }

    public abstract boolean sumOrCreateContent_HW(float[] incr, short h, short w, boolean forceNew, Dtime dt, long sysSecs);

    protected void updateDateStrings(long tKey, Dtime dt) {
        String dateStr;
        if (dt != null) {
            dateStr = dt.getStringDate_noTS();
        } else {
            dateStr = Dtime.UTC_fromSystemSecs((int) tKey).getStringDate_noTS();
        }
        // if (dateStr == null) dateStr = Datetime.fromSystemSecs((int)tKey, 0).getStringDate_noTS();
        //EnfuserLogger.log(Level.FINER,this.getClass(),"SHG: new date stamp from system seconds: "+dateStr);
        dateStrs.put(tKey, dateStr);

        if (tKey < this.minKey) {
            minKey = tKey;
        }
        if (tKey > this.maxKey) {
            maxKey = tKey;
        }
    }

    public float[] getCellContent(Dtime dt, double lat, double lon) {

        long tKey = this.getT_key(dt);

        short h = (short) getH(this.b, lat, dlat);
        short w = (short) getW(this.b, lon, dlon);
        boolean ok = this.HWok(h, w);
        if (!ok) {
            return null;
        }

        return getCellContent_tkHW(tKey, h, w);
    }

    public abstract float[] getCellContent(long sysSecs, double lat, double lon);

    public float[] getCellContent_timeSafe(Dtime dt, double lat, double lon, boolean withinLimits) {

        Long tKey = this.getClosestTimeKey(dt, withinLimits);
        if (tKey == null) {
            return null;
        }

        short h = (short) getH(this.b, lat, dlat);
        short w = (short) getW(this.b, lon, dlon);
        boolean ok = this.HWok(h, w);
        if (!ok) {
            return null;
        }

        return getCellContent_tkHW(tKey, h, w);
    }

    public abstract float[] getCellContent_tkHW(long tKey, short h, short w);

    public Float getValue_tkHW(long tKey, short h, short w, int typeIndex) {
        float[] vals = this.getCellContent_tkHW(tKey, h, w);
        if (vals != null) {
            return vals[typeIndex];
        } else {
            return null;
        }
    }

    public Float getValue(double lat, double lon, Dtime dt, int typeIndex) {
        long tKey = this.getT_key(dt);

        short h = (short) getH(this.b, lat, dlat);
        short w = (short) getW(this.b, lon, dlon);
        boolean ok = this.HWok(h, w);
        if (!ok) {
            return null;
        }

        return getValue_tkHW(tKey, h, w, typeIndex);
    }

    public abstract int getMaxT();

    public abstract void saveToFile(String fullName);

}
