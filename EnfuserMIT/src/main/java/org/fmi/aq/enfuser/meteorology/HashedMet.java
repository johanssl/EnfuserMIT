/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import static org.fmi.aq.enfuser.datapack.main.TZT.UTC;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLat;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLon;
import static java.lang.Math.PI;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.shg.SparseHashGrid;
import static org.fmi.aq.enfuser.solar.SolarBank.getSolarSpecs;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.interfacing.Feeder;
import org.fmi.aq.interfacing.WindProfiler;


/**
 *
 * @author johanssl
 */
public class HashedMet {

    protected ConcurrentHashMap< Integer, ConcurrentHashMap<Long, Met>> hash = new ConcurrentHashMap<>();
    public final double cos_lat;
    
    //rainCast is a high resolution precipitation dataset provided by a custom feed. This is most often missing: NULL.
    private SparseHashGrid rainCast = null;

    public final double res_m;
    public final int res_min;
    private final AreaNfo in;
    public final int[] windTypes;
    ConcurrentHashMap< Integer, ConcurrentHashMap<Integer, float[]>> solarHash = new ConcurrentHashMap<>();
    public WindProfiler wp;

    public HashedMet(DataPack datapack, FusionOptions ops, float res_m, float res_minutes) {
        this.rainCast = Feeder.getRainNowCastDataset(datapack);
        
        double midlat;
        if (ops.bounds() != null) {
            midlat = ops.bounds().getMidLat();
        } else {
            midlat = datapack.getSuperBounds().getMidLat();
        }

        this.res_m = res_m;

        this.in = new AreaNfo(ops.boundsClone(), res_m, Dtime.getSystemDate_utc(false, Dtime.ALL));//usage: for discretizing lat,lon coordinates into h-w index.
        double minutes = res_minutes / 3;
        if (minutes < 15) {
            minutes = 15;
        }
        this.res_min = (int)Math.round(minutes);
        this.cos_lat = Math.cos(midlat / 360 * 2 * PI);

        
        this.windTypes =   new int[]{ops.VARA.VAR_WIND_N,ops.VARA.VAR_WIND_E};
        
        this.wp = WindProfiler.getInstanceForMeasurementFusion(datapack,ops);
        
    }
    
    
        public float[] getStoreSolarSpecs(Dtime dt, double lat, double lon) {

        //form location key
        int locKey = (int) lat + (int) (lon) * 1000; //this resolution & accuracy should be sufficient for our purposes.
        ConcurrentHashMap<Integer, float[]> sols = solarHash.get(locKey);
        if (sols == null) {
            sols = new ConcurrentHashMap<>();
            solarHash.put(locKey, sols);
        }

        int tempoKey = getTemporalKey(dt);//(int) ((dt.systemHours() * 60 + dt.min) / res_minutes);
        float[] solar = sols.get(tempoKey);
        if (solar == null) {
            solar = getSolarSpecs((int) lat, (int) lon, 0, dt);
            sols.put(tempoKey, solar);
        }
        return solar;
    }

    public void clear() {
        this.hash.clear();
    }
    
    public float[] getMeasurementBasedWindOffsets(FusionCore ens, Dtime dt) {
        return this.wp.getMeasurementBasedWindOffsets(ens, dt);
    }

    public SparseHashGrid getRainCast_SHG() {
        return this.rainCast;
    }
    
    protected int getTemporalKey(Dtime dt) {
        int t = (int) (dt.systemSeconds() / 60.0 / res_min);
        return t;
    }


    /**
     * Find the proper HashMap for this particular time New HashMap instances
     * are created automatically.
     *
     * @param dt
     * @return
     */
    private ConcurrentHashMap<Long, Met> getAreaHash(long sysSecs) {

        int t = (int) (sysSecs / 60.0 / res_min);
        ConcurrentHashMap<Long, Met> ah = hash.get(t);
        if (ah == null) {
            ah = new ConcurrentHashMap<>();
            hash.put(t, ah);
        }
        return ah;
    }

    private void remove(Dtime dt) {
        int t = getTemporalKey(dt);
        hash.remove(t);
    }

    /**
     * Get the Met instance for this particular location and time. New Met
     * instances are created automatically if such does not exist already.
     *
     * @param dt time
     * @param lat latitude
     * @param lon longitude
     * @param ens the core
     * @param forceNew if true, then a new instance is always created and
     * stored.
     * @return the Met object.
     */
    public Met getStoreMet(Dtime dt, double lat, double lon, DataCore ens, boolean forceNew) {
        if (this.useTestDummy) return this.testDummy;
        ConcurrentHashMap<Long, Met> ah = getAreaHash(dt.systemSeconds());
        long key = getKey(lat, lon);
        Met met = ah.get(key);

        if (met == null || forceNew) {//create it, store and return

            double discr_lat = this.discretizedLat(lat);
            double discr_lon = this.discretizedLon(lon);

            met = Met.create(dt, discr_lat, discr_lon, ens);
            ah.put(key, met);
        }
        return met;
    }
    
    public Met getIfExists(long sysSecs, double lat, double lon) {
        ConcurrentHashMap<Long, Met> ah = getAreaHash(sysSecs);
        if (ah==null) return null;
        long key = getKey(lat, lon);
        Met met = ah.get(key);
        return met;
    }
    
    public Met getNext(Met met, FusionCore ens) {
        long secs = met.sysSecs+ this.res_min*60;
        Met next = getIfExists(secs, met.lat, met.lon);
        if (next==null && ens!=null) {
            Dtime dt = Dtime.fromSystemSecs((int)secs, 0, false);
            return this.getStoreMet(dt, met.lat, met.lon, ens);
        } else {
            return next;
        }
   
    }

    public Met getStoreMet(Dtime dt, double lat, double lon, DataCore ens) {
        return getStoreMet(dt, lat, lon, ens, false);
    }
    
    public ArrayList<Met> getNearbyMets(Dtime dt, double lat, double lon,
            DataCore ens, ArrayList<Met> arr) {
        arr.clear();
        for (RadListElement e:RADS1.elems) {
            double clat = lat -e.hAdd*in.dlat;
            double clon = lon + e.wAdd*in.dlon;
            Met met = getStoreMet(dt,clat,clon,ens);
            if (!arr.contains(met))arr.add(met);
        }
        return arr;
    }
    public final static RadiusList RADS1 = RadiusList.withFixedRads_SQUARE(1, 1, 0);
    public final static RadiusList RAD1 = RadiusList.withFixedRads(1, 10, 0);

    /**
     * Find the nearest 3x3 Met instances for this location and time. Intended
     * to be used for getSmoothProperty.
     *
     * @param dt time
     * @param lat latitude
     * @param lon longitude
     * @param ens the core
     * @param temp an array of Mets where the ones found during this call are
     * stored.
     * @return an array of "nearby" Met objects found.
     */
    /*
    public ArrayList<Met> getNearbyMets(Dtime dt, double lat, double lon,
            DataCore ens, ArrayList<Met> temp) {

        temp.clear();

        int virtualH = in.getH(lat);
        int virtualW = in.getW(lon);

        for (RadListElement e : RAD1.elems) {
            int ch = virtualH + e.hAdd;
            int cw = virtualW + e.wAdd;
            double clat = in.getLat(ch);
            double clon = in.getLon(cw);
            Met m = this.getStoreMet(dt, clat, clon, ens);
            if (m != null) {
                temp.add(m);
            }
        }

        return temp;
    }
    */
    /**
     * Use an array of local Met instance to interpolate a smooth property
     * value. Uses simple geographic Kriging interpolation.
     *
     * @param typeInts an array of met. type indices, as defined in
     * VariableAssociations. for these types the smoothed values are computed.
     * @param lat latitude
     * @param lon longitude
     * @param temp an array holding Met objects that are used to generate the
     * smooth values.
     * @return Kriging-smoothed (interpolated) values for types defined in
     * typeInts.
     */
    public float[] getSmoothProperties(int[] typeInts, float lat, float lon, ArrayList<Met> temp) {

        if ( temp.isEmpty()) {
            return null;
        }
        //min distance squared
        float minDist = (float) this.res_m * 0.25f;
        minDist*=minDist;
        //keep track of total weighted sum
        float wsum=0;
        float[] vals = new float[typeInts.length];
        
        for (int k = 0; k < temp.size(); k++) {//go over list of Mets
            Met m = temp.get(k);

            float dist_m2 = (float) Observation.getDistance_m_SQ(lat, lon, m.lat, m.lon);
            if (dist_m2 < minDist) {
                dist_m2 = minDist;
            }

            float w = 1f / dist_m2;
            wsum+=w;
            
            int j = -1;
            for (int typeInt : typeInts) {
                j++;
                //update by summing un-normalized weighted value
                vals[j]+=m.getSafe(typeInt)*w;
            }//for types
        }//for mets
        //finally, normalize
        for (int j =0;j<vals.length;j++) {
            vals[j]/=wsum;
        }

        return vals;
    }

    Float getRainCastValue(double lat, double lon, Dtime dt, FusionOptions ops) {
        if (this.rainCast == null) {
            return null;
        }
        try {
            int k = 0;
            float f = 0;
            //rainCast has data every 5 minutes, but the temporal unit for fusion can be 30min or 60min => average

            long sysSecs = dt.systemSeconds();
            for (int addS = 0; addS < ops.areaFusion_dt_mins() * 60; addS += this.rainCast.timeInterval_min * 60) {
                float[] rc = this.rainCast.getCellContent((long) (sysSecs + addS), lat, lon);
                if (rc != null) {
                    k++;
                    f += rc[0];
                }
            }

            if (k > 0) {
                return f / k;//return the average if some data was found
            }    
        } catch (Exception e) {
            // e.printStackTrace();
        }

        return null;
    }

    public ArrayList<Met> fillMetHistory(double lat, double lon, ArrayList<Met> mets,
            int sysH, int h_back, int h_inc, DataCore ens) {
        if (mets != null) {
            mets.clear();
        } else {
            mets = new ArrayList<>();
        }

        for (int sh = sysH; sh >= sysH - h_back; sh -= h_inc) {

            Dtime[] allDates = ens.tzt.getDates_notNullSafe(sh);//could be null.
            if (allDates == null) {
                continue;
            }

            mets.add(this.getStoreMet(allDates[UTC], lat, lon, ens));

        }

        return mets;
    }

    public ArrayList<Met> fillMetHistory(Observation ob, ArrayList<Met> mets,
            int h_back, int h_inc, DataCore ens) {
        return this.fillMetHistory(ob.lat, ob.lon, mets, ob.dt.systemHours(), h_back, h_inc, ens);
    }

    private final static int MIL = 1000000;

    /**
     * Form a key for HashMap based on the pair of coordinates. The selected
     * resolution (res_m) is taken into account.
     *
     * @param lat
     * @param lon
     * @return
     */
    Long getKey(double lat, double lon) {
        long h = this.in.getH(lat);
        long w = this.in.getW(lon);
        long key = MIL * h + w;

        return key;
    }

    private Long getKey_hw(long h, long w) {
        long key = MIL * h + w;
        return key;
    }

    /**
     * This method is used when new entry is added to HashedMet with some
     * accurate coordinate pair. To get smooth and continuous met. field the
     * exact coordinates must first be transformed back and forth to point at
     * the center of met grid cell.
     *
     * @param lat
     * @return
     */
    double discretizedLat(double lat) {
        int h = this.in.getH(lat);
        return this.in.getLat(h);
    }

    double discretizedLon(double lon) {
        int w = this.in.getW(lon);
        return this.in.getLon(w);
    }

// this should not be necessary anymore.
    public void forceInit(Dtime dt, Boundaries bounds, DataCore ens, boolean forceNew) {
        if (forceNew) {
            remove(dt);
        }
        double latrange = (bounds.latmax - bounds.latmin);
        double lonrange = (bounds.lonmax - bounds.lonmin);

        double latrange_m = latrange * degreeInMeters;
        double lonrange_m = lonrange * degreeInMeters * cos_lat;

        int H = (int) (latrange_m / res_m);
        if (H < 1) {
            H = 1;
        }
        int W = (int) (lonrange_m / res_m);
        if (W < 1) {
            W = 1;
        }
        double dlat = latrange / H;
        double dlon = lonrange / W;
  
            EnfuserLogger.log(Level.FINER,HashedMet.class,
                    "Actual metgrid structure: h = " + H + ", w = " + W);
            EnfuserLogger.log(Level.FINER,HashedMet.class,bounds.toText());
        
        int count = 0;
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                double lat = getLat(bounds, h, dlat);
                double lon = getLon(bounds, w, dlon);

                Met met = this.getStoreMet(dt, lat, lon, ens);
                if (met != null) {
                    count++;
                }
            }
        }


            EnfuserLogger.log(Level.FINE,HashedMet.class,
                    "Created/checked " + count + " met objects.");
        

    }

    public final static int WSM_RAW =-1;
    public final static int WSM_PROCESSED=1;
    
    public GeoGrid[] toGeoGrids(Boundaries bounds, Dtime dt, DataCore ens, int windStateMode) {
        VarAssociations VARA = ens.ops.VARA;
        GeoGrid[] grids = new GeoGrid[VARA.VAR_LEN()];

        ConcurrentHashMap<Long, Met> ah = this.getAreaHash(dt.systemSeconds());
        if (ah == null) {
            EnfuserLogger.log(Level.FINER,HashedMet.class,
                    "HashMet: no met layer for " + dt.getStringDate());
            return grids;
        }

        double latrange = (bounds.latmax - bounds.latmin);
        double lonrange = (bounds.lonmax - bounds.lonmin);

        double latrange_m = latrange * degreeInMeters;
        double lonrange_m = lonrange * degreeInMeters * cos_lat;

        int H = (int) (latrange_m / res_m);
        if (H < 1) {
            H = 1;
        }
        int W = (int) (lonrange_m / res_m);
        if (W < 1) {
            W = 1;
        }
        double dlat = latrange / H;
        double dlon = lonrange / W;

            EnfuserLogger.log(Level.FINER,HashedMet.class,
                    "Actual metgrid structure: h = " + H + ", w = " + W);
            EnfuserLogger.log(Level.FINER,HashedMet.class,bounds.toText());

        for (int i : VARA.metVars) {
            if (ens.datapack.variables[i] == null) {
                continue;
            }
            grids[i] = new GeoGrid(VARA.VAR_NAME(i), new float[H][W], dt, bounds);
        }
        
        WindState ws = null;
        FusionCore fens=null;
        if (ens instanceof FusionCore) {
            fens =(FusionCore)ens;
        }
        if (windStateMode!=WSM_RAW && fens!=null) {
           ws = new WindState();
           ws.fineScaleRL = false;
           ws.geoInterpolation=true;
           ws.timeInterpolation=true;
        }
        
        int wsFails =0;
        
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                double lat = getLat(bounds, h, dlat);
                double lon = getLon(bounds, w, dlon);
                Met met = this.getStoreMet(dt, lat, lon, ens);
                
                boolean wsOn = false;
                if (ws!=null) {
                    try {
                    ws.setTimeLoc((float)lat, (float)lon, dt, fens, null);
                    ws.profileForHeight(10, fens);
                    wsOn = true;
                    } catch (Exception e) {
                        wsFails++;
                        if (wsFails <2) {
                            EnfuserLogger.log(e, Level.WARNING, this.getClass(),
                                    "WindState processing for Met.GeoGrids failed.");
                        }//log fail
                    }//fail  
                }//if windState special
                
                for (int i : VARA.metVars) {
                    if (ens.datapack.variables[i] == null) {
                        continue;
                    }
                    Float f = met.get(i);
                    if (f==null) continue;//do nothing, data is missing.
                    
                    if (ws!=null && isWindType(i,VARA) && wsOn) {
                        f = ws.getProfiledType(VARA,i);
                    }
                    
                    grids[i].values[h][w] = f;

                }//for met types
            }//for w  
        }//for h

        return grids;
    }
    
    private boolean isWindType(int i, VarAssociations VARA) {
        if (i ==VARA.VAR_WINDDIR_DEG) return true;
        if (i ==VARA.VAR_WINDSPEED) return true;
        if (i ==VARA.VAR_WIND_E) return true;
        if (i ==VARA.VAR_WIND_N) return true;
        return false;
    }

    public Met average(ArrayList<Observation> obs, DataCore ens) {
        VarAssociations VARA = ens.ops.VARA;
        float[] metAves = new float[VARA.VAR_LEN()];//store met averages(sums) here
        float[] counts = new float[VARA.VAR_LEN()];// count non-null met variables
        //float solP_ave =0;
        Observation oDummy = obs.get(0);
        for (Observation o : obs) {

            Met met = this.getStoreMet(o.dt, o.lat, o.lon, ens);//fetch Met for this Observation

            for (int i = 0; i < VARA.VAR_LEN(); i++) {//iterate over variables
                Float f = met.get(i);
                if (f != null) {//exists
                    metAves[i] += f;//update
                    counts[i]++;
                }

            }

        }//for observations

        //build Met object
        ArrayList<float[]> custom = new ArrayList<>();
        for (int i = 0; i < VARA.VAR_LEN(); i++) {
            if (counts[i] > 0) {//average exists
                float ave = metAves[i] / counts[i];
                custom.add(new float[]{i, ave});
            }

        }

        Met met = Met.customize(custom, ens, oDummy.lat, oDummy.lon, oDummy.dt);
        return met;

    }
    private Met testDummy=null;
    private boolean useTestDummy =false;
    public void setDummyMode(Met met) {
       this.testDummy = met;
       this.useTestDummy =true;
       if (met==null) useTestDummy=false;
    }

    public void windFieldFusionForPeriod(Dtime start, Dtime end, FusionCore ens) {
       this.wp.windFieldFusionForPeriod(start, end, ens);
    }

}
