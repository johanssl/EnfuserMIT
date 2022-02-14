/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import java.io.File;
import org.fmi.aq.enfuser.meteorology.WindConverter;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.ftools.ObsSearch;
import static org.fmi.aq.enfuser.datapack.main.TZT.LOCAL;
import java.util.ArrayList;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.datapack.main.ObsNumerics;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;

/**
 *
 * @author johanssl
 */
public class StationSource extends AbstractSource {

    public boolean offsetCorrected = false;
    private final HashMap<Integer, Integer> absoluteTrusts = new HashMap<>();
    /**
     * A HashMap for Promilles Of Average weights when contributing to data
     * fusion.Key is for system hour.
     */
    private final HashMap<Integer, Double> absoluteWeights_POA = new HashMap<>();
    private final HashMap<Integer, Double> weightsRelative = new HashMap<>();//this is actualPOA/naivePOA.
    //large value: more trust is put to the source. Low value: not much trust is but to the source.

    private float[] hourlyAutoCovars;
    private Float autoCovar_all = null;
    private Float simpleVar =null;

    /**
     * return trust rating stored by datafusion algorithm (Adpater, overrides).
     * This is a class rating 0,1,2,3,4... the larger the value the LESS trust
     * has been put to the observation during data fusion.
     *
     * @param current time, the value is fetched based on systemHour.
     * @param ens the core
     * @return rating
     */
    public Integer getAbsoluteTrustClass(Dtime current, DataCore ens) {
        return this.absoluteTrusts.get(current.systemHours());
    }

    /**
     * Get the stored data fusion weight given to this observation.
     * It has been measured as %% of total weight pot. This means
     * that the value is generally higher for cases when there are many
     * observations contributing to data fusion. In case there is only one
     * measurement, it gets a value of 1000 regardless of data fusion. 
     * @param current the time
     * @param ens the core
     * @return %% of total assigned data fusion weight.
     */
    public Double getDF_weight_POA(Dtime current, DataCore ens) {
        return this.absoluteWeights_POA.get(current.systemHours());
    }
    
    /**
     * Get a relative measure for the percieved reliability of the observation.
     * This is computed as the df_weight_POA / naive_even_distribution_weight.
     * @param current the time
     * @param ens the core
     * @return relative reliability measure based on data fusion.
     */
        public Double getRelativeTrustEffect(Dtime current, DataCore ens) {
        return this.weightsRelative.get(current.systemHours());
    }

    public void add_observation(Observation obs) {

        //negativity check
        if (!VARA.IS_METVAR(obs.typeInt)) {//not temperature, or any met. variable
            if (obs.value < 0) {
                obs.value = 0; //One options that MAY NOT WORK is to skip the add process. 
                //However, A layer must be created regardless in case this is the only observation for this source.
            }
        }

        if (Float.isNaN(obs.value)) {
            obs.value = 0;
            this.err++;
            if (err < 100) {
                String msg=(this.ID + ": " + VARA.VAR_NAME(this.typeInt)
                        + ", " + obs.dt.getStringDate() + " NaN value! switched to" + obs.value);
                EnfuserLogger.log(Level.WARNING,this.getClass(),msg);      
            }
            obs.dt.freeByte += 48;//makes this observation artificially old and makes it possible
            //that this will be replaced by a better observation and at least, has high variance.

        }

        if (obs.dt.timeZone != 0 || obs.dt.inSummerTime()) {
            obs.dt.convertToUTC();
        }
        dom.update(obs);//update domain
        Layer lold = this.getLayer(obs.dt);

        if (lold == null) {//was not added previously, create a new Layer
            Layer l = new Layer(obs, this);
            this.putLayer(obs.dt, l);

        } else {//was found
            lold.addObservation(obs);
        }
    }

    public double lat() {
        return this.dom.latmax;
    }
    public double lon() {
        return this.dom.lonmax;
    }

    public StationSource(Observation obs, DataBase DB, FusionOptions ops) {
        this.VARA=ops.VARA;
        this.DB = DB;
        this.dbl = DB.get(obs.ID);
        if (dbl == null) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"STATION SOURCE created with null DBline: " 
                    + obs.ID + "," + VARA.VAR_NAME(obs.typeInt));
        }
        if (dbl.providesMinuteData()) {
            this.layerStepSeconds = 60;
        }
        this.ID = dbl.ID;
        this.typeInt = obs.typeInt;
        this.dom = new Domain();
        this.add_observation(obs);
    }

    public Float orig_lat = null;
    public Float orig_lon = null;

    /**
     * Adjusts the coordinates in case this source is stationary and the
     * original coordinates points to a building (difficult to profile and use
     * in the Fusion process).
     *
     * @param ens
     */
    private double[] shakedStationaryCoordinates(DataCore ens) {
        if (ens.mapPack==null) return null;
        if (this.dom.isStationary()) {
            double lat = lat();
            double lon = lon();
                EnfuserLogger.log(Level.FINER,this.getClass(),ID + ", " + lat + "," + lon);
            
            OsmLayer ol = ens.mapPack.getOsmLayer(lat, lon);
            if (ol == null) {

                EnfuserLogger.log(Level.FINER,this.getClass(),"\tCannot evaluate buildings due "
                        + "to mask availability! " + this.ID);
                banned = true;
                return null;
            }

            if (needsShaking(lat, lon, ol)) {
               
                   EnfuserLogger.log(Level.INFO,this.getClass(),this.ID + ", Source.coordinateShake: "
                        + "needs re-evaluation (at building)");
                EnfuserLogger.log(Level.INFO,this.getClass(),"original coordinates: " + lat 
                        + ", " + lon + "");
               
                double[] latlon = getClosestLatLon_nonBuilding(
                        ol, lat, lon, ens.ops);

                if (latlon == null) {
                    EnfuserLogger.log(Level.INFO,this.getClass(),this.ID + ", Source.coordinateShake => "
                            + "null coordinates. No map/ very large building?");
                   
                } else {
                    EnfuserLogger.log(Level.INFO,this.getClass(),
                       "new shaked coordinates: " + latlon[0] 
                        + ", " + latlon[1] + " with distance [m] "+latlon[2]); 
                }
                 return latlon;
            }//if needs
        }//if stationary
        return null;
    }

    public Observation getExactObservation(Dtime dt) {
        return getExactObservation(dt, 0);
    }

    public Observation getExactObservation(Dtime dt, int offset) {
        Layer l = this.getLayer(dt,offset);
        if (l == null) {
            return null;
        }
        return l.getStationaryObs();
    }

    public Observation getExactObservation(Observation o) {
        Layer l = this.getLayer(o.dt);
        if (l == null) {
            return null;
        }
        return l.getStationaryObs();
    }

    public float hourlyAutoCovar(int hour) {
        
        if (this.hourlyAutoCovars == null) {
            this.hourlyAutoCovars = ObsNumerics.getOneHourAutoCovars(this);
            this.autoCovar_all = ObsNumerics.getOneHourAutoCovar_single(this);   
        }
        
       float f = this.hourlyAutoCovars[hour];
       if (f<=0) {//will not do, no data points for the hour. Use all data points then.
           f = autoCovar_all;
       }
       
       if (f<=0) {//there's pretty low amount of data or the data source has constant values
           if (this.simpleVar==null) this.simpleVar = ObsNumerics.getVar_obs(this.getLayerObs());
           f = this.simpleVar;
       }
       
       return f;
    }

    public ArrayList<Layer> getSortedLayers() {
        this.sortLayers();
        return this.timeSorted;

    }

   

    private static boolean needsShaking(double lat, double lon,
            OsmLayer ol) {

//check building at groundZero
            byte b = OSMget.getSurfaceType(ol, lat, lon);
            if (OSMget.isBuilding(b)) {// is INSIDE a building mapping
                return true;

            }//if building 

        return false;
    }

    /**
     * Finds the closest non-building location for the given location.The main
 use case is to find a virtual new location for stations/sensors which are
 located inside the building mask of osmLayers. This can happen due to the
 discrete nature of the building mapping with a resolution of 5m and also
 due to any fine scale uncertainties for station coordinates.
     *
     * @param ol the osmLayer
     * @param lat latitude (original)
     * @param lon longitude (original)
     * @param ops
     * @return {latitude, longitude} WGS84 coordinates which are the closest
     * non-building location found nearby the orginal coordinates.
     */
    public static double[] getClosestLatLon_nonBuilding(OsmLayer ol,
            double lat, double lon, FusionOptions ops) {

        RadiusList radList_3m_100m = new RadiusList(3, 100, lat);
        RadiusList radList_3m_short = new RadiusList(3, 10, lat);
        
        for (RadListElement e : radList_3m_100m.elems) {
           double clat =lat + e.latAdd;
           double clon =lon + e.lonAdd;
            
            try {
                    byte osm = OSMget.getSurfaceType(ol, clat, clon);
                    if (!OSMget.isBuilding(osm)) {// is INSIDE a building mapping
                        
                        //there can be no builds in the nearby cells either
                        int empty=0;
                        for (RadListElement eS : radList_3m_short.elems) {
                            if (eS.dist_m> 5) break;
                            double test_lat = clat + eS.latAdd;
                            double test_lon = clon + eS.lonAdd;
                            byte osm2 = OSMget.getSurfaceType(ol, test_lat, test_lon);
                            if (!OSMget.isBuilding(osm2)) empty++;
                        }
                        
                        if (empty>=8) return new double[]{clat, clon, e.dist_m};
                        
                        //BlockAnalysis test
                        //BlockAnalysis ba = new BlockAnalysis(ol,ops, clat,clon,null);
                        //if (ba.atBuilding()) continue;
                        //return new double[]{clat, clon, e.dist_m};
                    }
                    
                
            } catch (Exception ex) {

            }

        }

        return null;
    }


    /**
     * This method returns a list of observation which are acceptable (rules are
     * defined by ObsSearch).
     *
     * @param os Contains the rules for observation acceptance.
     * @param ens For datetime conversion (local time).
     * @param print if true, additional console printout are provided
     * @return List of observations that are within the accepted rules.
     */
    public ArrayList<Observation> extractObservations(ObsSearch os,
            DataCore ens,
            boolean print) {
        ArrayList<Observation> extObs = new ArrayList<>();

        for (Layer l : this.layerHash.values()) {
            Observation o = l.getStationaryObs();
            boolean ok = os.isAcceptable(o, ens);
            if (ok) {
                extObs.add(o);
            }
        }
        if (print) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Extracted " + extObs.size() 
                    + " observation based on ObsSearch rules.");
        }
        return extObs;
    }

    public ArrayList<Observation> getLayerObs() {
        ArrayList<Observation> obs = new ArrayList<>();
        for (Layer l : this.layerHash.values()) {
            obs.add(l.getStationaryObs());
        }
        return obs;
    }

    @Override
    public String getDataPrintout(DataCore ens) {
        String s = "";
        String n = "\n";
        String I = ",\t ";
        ArrayList<Layer> ll = this.getSortedLayers();

        String autos = "====1h_autcovars:=====";
        for (int h = 0; h < 24; h++) {
            float covar = this.hourlyAutoCovar(h);
            autos += "Hour (UTC) " + h + ": " + editPrecision(covar, 1) + "\n";
        }
        s += autos + n;

        StationSource wNalias = null;
        StationSource wEalias = null;

        if (!this.aliasTypes.isEmpty()) {

            for (AbstractSource ss : this.aliasTypes) {
                if (this.typeInt == VARA.VAR_WIND_N 
                        && ss.typeInt == VARA.VAR_WIND_E) {
                    wEalias = (StationSource) ss;
                } else if (this.typeInt == VARA.VAR_WIND_E 
                        && ss.typeInt == VARA.VAR_WIND_N) {
                    wNalias = (StationSource) ss;
                }

            }
        }//if alias
        if (dbl.autoOffset) s+="This source is allowed to autoOffset its values.\n";
        s+= "Imprecision rating at DB:"+(int)dbl.varScaler(this.typeInt) +"\n";
        s += "Observations: " + n + "Time,\t\t localH,\t Value, "
                + "\t originals \t priorityMarker \tsourceFile \n";
        Dtime[] dts;
        int k = 0;
        for (Layer l : ll) {
            k++;
            Observation o = l.getStationaryObs();
            dts = ens.tzt.getDates(o.dt);
            String date = o.dt.getStringDate_YYYY_MM_DDTHH();
            if (this.hasMinuteData()) date = o.dt.getStringDate_noTS();
            
            int localh = dts[LOCAL].hour;
            String source = l.sourceFile();
            String origVal = "-";
            if (o.autoCorrected) {
                origVal = " (" + editPrecision(o.origObsVal(), 2) + ")";
            }
            if (o.aveCount>1) {
                origVal+="["+o.aveCount+"]";
            }
            String line = date + I 
                    + localh + I
                    + editPrecision(o.value, 2) + I 
                    + origVal + I 
                    + o.replacedValue() +I
                    + o.dt.freeByte+ I 
                    +source + n;

            float[] NE = null;
            if (wNalias != null) {//this is E and the alias has N
                Observation nComp = wNalias.getExactObservation(o.dt, 0);
                if (nComp != null) {
                    NE = new float[]{nComp.value, o.value};
                }
            } else if (wEalias != null) {//this is N and alias has E
                Observation eComp = wEalias.getExactObservation(o.dt, 0);
                if (eComp != null) {
                    NE = new float[]{o.value, eComp.value};
                }
            }
            if (NE != null) {
                float[] dirV = WindConverter.NE_toDirSpeed_from(NE[0], NE[1], false);
                line += "\tdir = " + (int) dirV[0] + ",\t v=" + editPrecision(dirV[1], 1) + n;
            }

            s += line;

            if (k > 8690) {
                s += "...and many more." + n;
                break;
            }

        }//for obs

        return s;
    }

    @Override
    public void finalizeStructure(DataCore ens) {
        this.crossCheckAliasTypes(ens.datapack);
        double[] latlon = shakedStationaryCoordinates(ens);
        if (latlon!=null) {
            ArrayList<StationSource> all = new ArrayList<>();
            all.add(this);
            for (AbstractSource alias:this.aliasTypes) {
                StationSource s = (StationSource)alias;
                all.add(s);
            }
            
            for (StationSource s:all) {
                 
                s.addInfo = "(orginal coordinates: " + s.lat() 
                        + ", " + s.lon() + ")";
                s.orig_lat = (float) s.lat();
                s.orig_lon = (float) s.lon();//store these for map analysis (GE-pins)

                s.dom.latmax = latlon[0];
                s.dom.latmin = latlon[0];

                s.dom.lonmax = latlon[1];
                s.dom.lonmin = latlon[1];
               
                for (Layer l : s.layerHash.values()) {
                    Observation o = l.getStationaryObs();
                    o.lat = (float) (latlon[0]);
                    o.lon = (float) (latlon[1]);
                }
                s.dbl.shakeCoordinates(latlon);
            }
            
        }//if coordinate shake
    }

    @Override
    public Float getSmoothValue(Dtime dt, double lat, double lon) {

        Layer l = this.getClosestLayer_backwards(dt, 3);
        if (l == null) {
            return null;
        }
        //layer l exists.
        float val1 = l.getStationaryObs().value;

        Layer l2 = this.getClosestLayer_forwards(dt, 3);
        if (l2 == null) {
            return val1;//layer 2 was not found
        }            //both layer exist
        float val2 = l2.getStationaryObs().value;

        float diff_mins = l2.dt_system_min - l.dt_system_min;
        if (diff_mins == 0) {
            return val1;
        }

        float frac2 = (dt.systemSeconds() / 60 - l.dt_system_min) / diff_mins;
        float frac1 = 1f - frac2;
        float value = frac1 * val1 + frac2 * val2;
        return value;

    }

    @Override
    public Observation getExactRawValue(Dtime dt, double lat, double lon) {
        return this.getExactObservation(dt, 0);
    }

    public void clearTrustClasses() {
        this.absoluteTrusts.clear();
        this.absoluteWeights_POA.clear();
        this.weightsRelative.clear();
        //this.FreeProgressionTrusts.clear();
    }

    /**
     * Log data fusion quality ratings for this station. The observation given
     * is one of this source's and it carries the "qualityIndex" that has been
     * associated to it during data fusion. This class data is to be stored in
     * its raw form and hashed based on the Observation time. This makes it
     * possible to process progressive derivatives that relate to the overall
     * reliability of this source over the course of time.
     *
     * @param o the observation
     */
    public void storeDataFusionQualityRating(Observation o) {
        int hc = o.dt.systemHours();
        absoluteTrusts.put(hc, o.dataFusion_qualityIndex());
        this.absoluteWeights_POA.put(hc, o.dataFusion_Weight_POA());
        double relative = o.relativeDataFusionOutcome();
        this.weightsRelative.put(hc, relative);
    }

    @Override
    public String oneLinePrinout(FusionOptions ops) { 
        String s = this.ID +": stationarySource, ";
        Dtime end = ops.getRunParameters().end();
        Dtime current = ops.getRunParameters().start().clone();
        current.addSeconds(-3600);
        

        missing=0;
        n =0;
        File fileFirst =null;
        
        min = Double.MAX_VALUE;
        max = Double.MAX_VALUE*-1;
        ave = 0;
        checkGeo = this.dbl.lat+"," +this.dbl.lon;
        
        while (current.systemHours()< end.systemHours()) {
            current.addSeconds(3600);
            Layer l = this.getExactLayer(current);
            if (l==null ) {
                missing++;
                continue;
            }
             Observation o = l.getStationaryObs();
             if (o==null) {
                 missing++;
                 continue;
             }
            n++;
                if (o.value<min) min = o.value;
                if (o.value>max) max = o.value;
                ave+=o.value;
                if (o.sourceFile!=null && fileFirst==null) {
                    fileFirst = new File(o.sourceFile);
                }
            
        }//while time
        ave/=n;
        String filex ="";
        if (fileFirst!=null) filex =", fileExample="+fileFirst.getAbsolutePath();
        String constantAlert = "";
        if (this.min == this.max && n>10 ) {
            constantAlert =", CONSTANT measurement values.";
        }
       return s + "available="+n +", missing = "+missing + filex
                +"\t\t\tgeo="+checkGeo
                + ", min="+editPrecision(min,2) 
                +", max ="+editPrecision(max,2)
                +", ave="+editPrecision(ave,2)
                +constantAlert;

    }
    
        @Override
    public Level getSeverityDataQuality(FusionOptions ops) {
        return Level.INFO;
    }

    @Override
    public String getDataQualityAlert() {
        return "";
    }


}
