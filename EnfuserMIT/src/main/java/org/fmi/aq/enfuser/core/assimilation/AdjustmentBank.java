/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.core.receptor.PredictionPrep.getExp;
import static org.fmi.aq.enfuser.core.receptor.PredictionPrep.getExps;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.StationSource;

/**
 * This class holds and maintains OverrideList instances, and has some fill and
 * smooth operations for this objects.
 *
 * The most important features are the put and get-methods, which both utilize
 * pollutant type (integer) and systemHour (integer) for indexing.
 *
 * @author Lasse Johansson
 */
public class AdjustmentBank {

    private final ConcurrentHashMap<Integer, HourlyAdjustment>[] overrides; //contains best-fit rules for hourly fusion.
    private final ConcurrentHashMap<Long, HourlyAdjustment>[] overrides_quarts; //contains best-fit rules for 15,30 and 45 mins smoothing
    private ConcurrentHashMap<String,HourlyAdjustment> dummies = new ConcurrentHashMap<>();//for forecasting tests and if data fusion is disabled.
    
    private boolean[] smoothened;
    private final FusionOptions ops;
    //e.g. traffic seems to be lower than with default setting for some special hour
    private final int[] minHC;
    private final int[] maxHC;

    /**
     * Constructor
     * @param ops
     */
    public AdjustmentBank(FusionOptions ops) {

        this.ops =ops;
        this.overrides = new ConcurrentHashMap[ops.VARLEN()];

        this.overrides_quarts = new ConcurrentHashMap[ops.VARLEN()];
        this.minHC = new int[ops.VARLEN()];
        this.maxHC = new int[ops.VARLEN()];
        this.smoothened = new boolean[ops.VARLEN()];

        for (int t = 0; t < minHC.length; t++) {
            minHC[t] = Integer.MAX_VALUE;
            maxHC[t] = -1;
        }

        for (int i = 0; i < overrides.length; i++) {
            this.overrides[i] = new ConcurrentHashMap<>();
            this.overrides_quarts[i] = new ConcurrentHashMap<>();
        }
    }

    /**
     * Iterate over the existing OverrideLists and create new smoother instance
     * based on previous and next OverrideLists. This will sacrifice some
     * accuracy but will make sure that the instantaneous scalers for emission
     * categories behave in a less volatile manner during the modelling. This
     * method also fills gap hours, for which no measurement data was present to
     * facilitate the adaptation. In such cases the most recent previous
     * OverrideList is copied.
     *
     * @param ens the core
     * @param typeInt variable type index
     */
    public void fillAndSmoothen(FusionCore ens, int typeInt) {
        if (smoothened[typeInt]) {
            return;
        }
        ConcurrentHashMap<Integer, HourlyAdjustment> hash_new = new ConcurrentHashMap<>();
        //since we iterate over 3 consecutive overrideLists at a time, a new temporary hashArray must be created and once done this will replace the existing hashArray.

        // for (int typeInt=0;typeInt<FusionOptions.VARLEN();typeInt++) {
        int procs = 0;
        int fills = 0;

        if (!ens.ops.VARA.isPrimary[typeInt]) {
            return;//cannot have an override
        }
        ConcurrentHashMap<Integer, HourlyAdjustment> hash = overrides[typeInt];
        if (hash == null || hash.isEmpty()) {
            return;
        }

        int HCmin = minHC[typeInt];
        int HCmax = maxHC[typeInt];
        //TODO add minHC and maxHC instantly to the new hash!
        HourlyAdjustment olmin = this.get(HCmin, 0, typeInt, ens.ops);
        hash_new.put(HCmin, olmin);
        HourlyAdjustment olmax = this.get(HCmax, 0, typeInt, ens.ops);
        hash_new.put(HCmax, olmax);

        for (int sysH = HCmin + 1; sysH < HCmax; sysH++) {

            HourlyAdjustment current = this.get_int(sysH, 0, typeInt, ens.ops);
            if (current == null) {//a fill is needed
                HourlyAdjustment ofill = this.createFiller(sysH, ens, typeInt);
                ofill.setupPrintouts(ens);
                hash_new.put(sysH, ofill);
                hash.put(sysH, ofill);//add this to main hash as well so that this can be accessed as 'prev' in the next iteration.
                fills++;
                continue;
            }

            //current exists. Find previous, that also exists, and next (that may NOT exist)
            HourlyAdjustment prev = this.get_int(sysH - 1, 0, typeInt, ens.ops);
            HourlyAdjustment next = this.get_int(sysH + 1, 0, typeInt, ens.ops);

            if (prev == null) {//this should not happen.
                EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"OverrideController.fill: there is a gap for " 
                        + ens.ops.VAR_NAME(typeInt) + ", time = " + Dtime.fromSystemSecs(sysH * 3600, 0, false));
                continue;
            }

            try {
                HourlyAdjustment ol = HourlyAdjustment.smoothClone(prev, current, next);
                //OverrideList statistics must be recomputed as well
                ol.setupPrintouts(ens);
                hash_new.put(sysH, ol);
                procs++;

            } catch (Exception e) {

            }
        }//for hc 

        EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"OverridesController: added" + procs 
                + " smoothed overrides for " + ens.ops.VAR_NAME(typeInt) + ", fills = " + fills);
        // }//for vars
        smoothened[typeInt] = true;

        //clear 15-min interpolation, if they already exist
        this.overrides_quarts[typeInt] = new ConcurrentHashMap<>();
        this.overrides[typeInt] = hash_new;//replace

    }
    public final static int FORECAST_TESTER_H = 24;


    /**
     * Create and add an interpolated future HourlyAdjustment for forecasting period
 based on the a) previous OL with same diurnal hour and b) all the most
 recent OverrideLists.
     *
     * @param HC the Hour Counter, Dt.systemHours()
     * @param prevs a list of previously created actual OverrideLists.
     * @param typeInt pollutant species
     * @param ops options
     * @param last the last HourlyAdjustment that was created previously before
 entering the forecast mode
     * @param forecastH amount of hours to the forecasting time (e.g., 12h to
     * the future)
     */
    private void addFutureOverride(int HC, ArrayList<HourlyAdjustment> prevs,
            int typeInt, FusionOptions ops, HourlyAdjustment last, int forecastH) {
        float lastFrac = 1f - forecastH / 12f;//this is 0 after 10h of forecasting
        if (lastFrac < 0) {
            lastFrac = 0f;
        }

        float df = (1f - lastFrac) * 0.4f;
        float prf = (1f - lastFrac) * 0.6f;

        int dHC = HC;
        ArrayList<HourlyAdjustment> pds = this.getPreviousDiurnals(dHC, typeInt, ops);
//fetch a couple of previous Overrides with the same diurnal hour. For frecast testing the fetch starts after a virtual unavailability period.
        if (pds.isEmpty()) {
            prf += df;
        }

        if (prevs.isEmpty()) {
            EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"OverridesController.addFutureOverride:"
                    + " Warning! list of previous overrides is empty!"
                    + Dtime.fromSystemSecs(HC * 3600, 0, false) + ", " 
                    + ops.VAR_NAME(typeInt));
        }

        //Create base
        HourlyAdjustment ol = HourlyAdjustment.filler(last, forecastH);
        //manipulate content
        for (int c = 0; c < ol.overrides.length; c++) {
            if (c != CAT_BG) {
                ol.overrides[c] = last.overrides[c] * lastFrac 
                        + averageFactor(pds, c) * df + averageFactor(prevs, c) * prf;
            }
        }//for cats
                EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"====> hourdiff from last = " 
                        + forecastH + ", adding override for "
                        + ol.date + " with averaging fracs (recent)" 
                        + lastFrac + " and (diurnal)" + df + ", and prevList " + prf);
            
            this.put(ol, HC, typeInt);
        

    }

    /**
     * Create forecasted OverrideLists up until the specified end time.Note:
 actual OverrideLists should exist (assessed by Adapter) before this is
 used.
     *
     * @param start
     * @param end end time for which the future OverrideLists are generated for.
     * @param typeInt pollutant species.
     * @param ens the core
     */
    public void futureFill(Dtime start, Dtime end, int typeInt, FusionCore ens) {

       if (ens.ops.neuralLoadAndUse && ens.neural.hasONC() && ens.ops.VARA.isPrimary[typeInt]) {
           EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"RF-model future fill for Overrides - "+ ens.ops.VAR_NAME(typeInt));
           Dtime current = start.clone();
           double lat = ens.ops.bounds().getMidLat();
           double lon = ens.ops.bounds().getMidLon();
           while(current.systemHours()<=end.systemHours()) {
               try {
                   HourlyAdjustment test = this.get (current, typeInt, ens.ops);
                   if (test!=null) {
                       current.addSeconds(3600);
                       continue;
                   }
                   
                   Object olo = ens.neural.getOverride(ens, current, lat, lon, typeInt);
                   if (olo!=null) { 
                    HourlyAdjustment ol =(HourlyAdjustment)olo;   
                    ol.neural = true;
                    ol.filled = true;
                    
                   this.put(ol, current.systemHours(), typeInt);
                       EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"\t successfully created for "+ current.getStringDate());
                   }
                   
               }catch(Exception e) {
                   e.printStackTrace();
               }
               current.addSeconds(3600);
           }
           return;
       }//if randomForest 
        
        
        
        int lastH = this.maxHC[typeInt];
        if (lastH >= end.systemHours()) {
            EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"OverridesController: futureFill unnecessary,"
                    + " all overrides are available until " + end.getStringDate());
            return;
        }

        try {
            HourlyAdjustment last = this.get(lastH, 0, typeInt, ens.ops);//this should always exist
            if (last==null) {
                EnfuserLogger.log(Level.INFO,AdjustmentBank.class,
                        "Overrides fill operation could not complete "
                                + "- insufficient measurement data for "+ ens.ops.VAR_NAME(typeInt));
                return;
                
            }
            EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"Adding future Overrides,"
                    + " the last existing is for " + last.date);

            //list of previous overrides
            ArrayList<HourlyAdjustment> prevs = this.getPreviousList(lastH, 
                    typeInt, 48, ens.ops);

            //create future Overrides
            int forecastH = 0;
            while (lastH <= end.systemHours()) {
                lastH++;
                forecastH++;

                addFutureOverride(lastH, prevs, typeInt, ens.ops,
                        last, forecastH);
            }//until end.sysH

        } catch (Exception e) {
            String line = "Warning! Overrides.FutureFill did not work for " 
                    + ens.ops.VAR_NAME(typeInt) + "."
                    + "\n Forecasting endTime= " + end.getStringDate() 
                    + ", time difference (h) to last existing override = " 
                    + (end.systemHours() - lastH);
            EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                  line);
        }

    }

    /**
     * Get text output for all stored instances, for all specified variable
     * types in arrayList. This output is in tabled CSV-format and can be
     * written to a CSV-file as it is.
     *
     * @param dt the time (hour)
     * @param ops options
     * @param nonMetTypes the list of pollutant species to be summarized.
     * @return a list of lines in CSV-table format.
     */
    public ArrayList<String> getAllOverridesInText(Dtime dt, FusionOptions ops,
            ArrayList<Integer> nonMetTypes) {

        ArrayList<String> arr = new ArrayList<>();
        for (int typeInt : nonMetTypes) {
            ArrayList<String> test = getTypeOverridesInText(typeInt, ops, dt.systemHours());
            if (test.size() > 1) {//has something more than the header.
                arr.addAll(test);
            }
        }
        return arr;
    }

    /**
     * Get text output for all stored instances, for all specified variable
     * types in arrayList.This output is in tabled CSV-format and can be
 written to a CSV-file as it is.
     *
     * @param start start time
     * @param end end time
     * @param ens the core
     * @param nonMetTypes the list of pollutant species to be summarized.
     * @param hidable if true, then some sensor data can be omitted.
     * @return a list of lines in CSV-table format.
     */
    public ArrayList<String> getObservationStatistics(Dtime start, Dtime end,
            FusionCore ens, ArrayList<Integer> nonMetTypes, boolean hidable) {

        ArrayList<String> arr = new ArrayList<>();
        boolean first = true;
        for (int typeInt : nonMetTypes) {
            ArrayList<String> test = getObservationStatisticsLines(hidable,typeInt, ens,
                    start, end, first);
            if (test.size() > 1) {//has something more than the header.
                arr.addAll(test);
                first = false;
            }
        }
        return arr;
    }
    
        public ArrayList<String> getObservationStatistics_forecast(Dtime start, Dtime end,
            FusionCore ens, ArrayList<Integer> nonMetTypes) {

        ArrayList<String> arr = new ArrayList<>();
        boolean first = true;
        for (int typeInt : nonMetTypes) {
            ArrayList<String> test = getObservationStatisticsLines_forecast(typeInt, ens,
                    start, end, first);
            if (test.size() > 1) {//has something more than the header.
                arr.addAll(test);
                first = false;
            }
        }
        return arr;
    }

    /**
     * Get text output for OverrideLists for the specified variable type. This
     * output is in tabled CSV-format and can be written to a CSV-file as it is.
     *
     * @param typeInt the pollutant species
     * @param ops options
     * @param sysH the systemHour (Dt.systemHour()))
     * @return a list of lines in CSV-table format.
     */
    public ArrayList<String> getTypeOverridesInText(int typeInt,
            FusionOptions ops, Integer sysH) {
        ArrayList<String> out = new ArrayList<>();

        String header = HourlyAdjustment.getHeader(typeInt);
        String headAdd = null;// coordinates, measurement height, some lables, the amount of content varies depending on the amount of measurement points.
        //out.add(HourlyAdjustment.getHeader(typeInt));

        //if (ops.forecastingOffsetTester_h != 0) EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"AdjustmentBank: WARNING! getTextOutput invoked while forecasting test offset has been set!!!!====================================================" );
        int min = minHC[typeInt];
        int max = maxHC[typeInt];

        if (sysH != null) {//eaxt time is used, not many
            min = sysH;
            max = sysH;
        }
        for (int hc = min; hc <= max; hc++) {

            HourlyAdjustment olist = this.get(hc, 0, typeInt, ops);
            if (olist != null) {
                //out.add(olist.getString());
                String s = olist.date + ";" + ops.VAR_NAME(typeInt) 
                        + ";" + olist.modsString() + ";";
                for (int c : ops.CATS.C) {

                    String temp = editPrecision(olist.overrides[c], 1) + ";";
                    s += temp;
                }//for modTypes
                s += olist.infoOneLine;
                if (headAdd == null) {
                    headAdd = olist.infoHeaderLine;
                }
                out.add(s);
            }

        }
        out.add(0, header + headAdd);//add header as first line
        return out;
    }
    
    
    private static String obsStatsHeader(FusionCore ens) {
        String header = "DATE;IMPRECISION_RATING;VAR_NAME;ID;LAT;LON;OBSERVED_VALUE;ORGINAL_VALUE;"
                + "PREDICTION;DATA_FUSION;";//OverrideList.getHeader_v2(typeInt);
        for (String cname : ens.ops.CATS.CATNAMES) {
            header += cname + ";";
        }
        header += "RAW_BG;LOGWIND;" + Met.getMetValsHeader(";",ens.ops);
        //overrides
        for (String cname : ens.ops.CATS.CATNAMES) {
            header += cname + "_override;";
        }
        header+="over_tags;";
         //overrides - combined with longerTerm
        for (String cname : ens.ops.CATS.CATNAMES) {
            header += cname + "_memoryOverride;";
        }
        return header;
    }
    
    private static String buildObsStatsLine(HourlyAdjustment olist, FusionCore ens,
            Observation o, int typeInt) {
        //Build the String!
        String s = olist.date + ";"+ o.dbImprecision(ens) +";"+ ens.ops.VAR_NAME(typeInt) + ";";//+olist.modsString()+";";

        //not previous hour and exists.
        String addLine = o.ID + ";" + o.lat + ";" + o.lon + ";" //id and coordinates
                + editPrecision(o.value, 2) + ";" + o.origObsVal() 
                + ";" + editPrecision(getExp(o,ens, typeInt), 2) 
                + ";" + o.reliabilityString() + ";"; 
    //value, originalValue, modelPrediction, datafusion Quality
        float[] exps = getExps(o,ens, typeInt);
        for (float f : exps) {
            addLine += editPrecision(f, 2) + ";";
        }//all components that form the prediction
        float bg = -1;
        Float bgf = ens.getModelledBG(typeInt, o.dt, o.lat, o.lon, false);
        if (bgf != null) {
            bg = bgf;
        }
        addLine += editPrecision(bg, 2) + ";" + o.incorporatedEnv.logWind + ";";//raw background value, profiled wind
        addLine += o.incorporatedEnv.met.metValsToString(";");

        for (int c : ens.ops.CATS.C) {
            addLine += editPrecision(olist.overrides[c], 1) + ";";
        }
        addLine += olist.modsString() +";";

        //second time, with memory
        for (int c : ens.ops.CATS.C) {
            double fact = olist.getCQLfactoredValue(c,ens);
            addLine += editPrecision(fact, 2) + ";";
        }
        s += addLine;
        return s;
    }

    /**
     * Get text output for OverrideLists for the specified variable type.This
 output is in tabled CSV-format and can be written to a CSV-file as it is.
     *
     * @param hidable if true, then some sensor data can be omitted.
     * @param typeInt the pollutant species
     * @param ens the core
     * @param start start time
     * @param end end time
     * @param first if true, then this is the first time this method is used and
     * a header is added to the list.
     * @return a list of lines in CSV-table format.
     */
    public ArrayList<String> getObservationStatisticsLines(boolean hidable,int typeInt,
            FusionCore ens, Dtime start, Dtime end, boolean first) {
        ArrayList<String> out = new ArrayList<>();

        String header = obsStatsHeader(ens);
        for (int hc = start.systemHours(); hc <= end.systemHours(); hc++) {

            HourlyAdjustment olist = this.get(hc, 0, typeInt, ens.ops);
            if (olist != null) {
                if (olist.filled) {
                    continue;//by definition does not have any real observations
                }
                //then go through the observations
                int k = -1;
                for (Observation o : olist.obs) { 
                    k++;
                    if (o == null) {
                        continue;
                    }
                    try { 
                        //hidden?
                        DBline dbl = ens.DB.get(o.ID);
                        if (hidable && dbl.hideFromOutput) {
                            continue;
                        }
                        
                        Boolean isPrev = olist.isPrevObs.get(k);
                        if (isPrev) {
                            continue;
                        }
                        
                        String s = buildObsStatsLine(olist, ens, o,typeInt);
                        out.add(s);
                    } catch (Exception e) {

                    }
                }//for obs
            }
        }
        if (first) {
            out.add(0, header);//add header as first line
        }
        return out;
    }
    
    
     /**
     * Get text output for observationStatistics, but for the forecasting period
     * for which measurements do not exist.
     *
     * @param typeInt the pollutant species
     * @param ens the core
     * @param start start time
     * @param end end time
     * @param first if true, then this is the first time this method is used and
     * a header is added to the list.
     * @return a list of lines in CSV-table format.
     */
    public ArrayList<String> getObservationStatisticsLines_forecast(int typeInt,
            FusionCore ens, Dtime start, Dtime end, boolean first) {
        ArrayList<String> out = new ArrayList<>();

        String header = obsStatsHeader(ens) +"FORECAST_H;";
        Dtime now = Dtime.getSystemDate_FH();
        VariableData vd = ens.datapack.getVarData(typeInt);
        ArrayList<StationSource> sts = null;
        if (vd!=null) sts= vd.stationSources();
        
        for (int hc = start.systemHours(); hc <= end.systemHours(); hc++) {

            
            HourlyAdjustment olist = this.get(hc, 0, typeInt, ens.ops);
            if (olist == null) continue;
            if (!olist.filled) continue;//filled => not created based on data fusion.
            if (hc< now.systemHours()) continue;
            int diff = hc- now.systemHours();//this many hours in to the future.
            if (sts==null) continue;//no stations for this variable at all.
            
            for (StationSource st:sts) {
                try {
                Observation o = new Observation(st.typeInt, st.dbl, olist.dt.clone(), -1, st.ID);
                String s = buildObsStatsLine(olist, ens, o,typeInt) +diff+";";
                        out.add(s);
                        
                } catch (Exception e) {
                    EnfuserLogger.log(e, Level.ALL, this.getClass(), "");
                }
                
            }//for stations    
        }
        if (first) {
            out.add(0, header);//add header as first line
        }
        return out;
    }

    /**
     * Store output for all stored instances (CSV), for all specified variable
     * types in arrayList.In this method each pollutant species will have their
     * own file.
     *
     * @param regID area identifier String (added to file name)
     * @param ops options
     * @param nonMet_typeInts the list of pollutant species to be summarized.
     * @param dir directory where files are created
     * @param statCrunchDir if true, then the output is stored to GUI-dedicated
     * directory. This should be false for operational use.
     */
    public void timeSeriesToFile(String regID, FusionOptions ops,
            ArrayList<Integer> nonMet_typeInts,
            String dir, boolean statCrunchDir) {

        for (Integer typeInt : nonMet_typeInts) {
            String fileName = regID + "_" + ops.VAR_NAME(typeInt) 
                     + "_overrides.csv";
            try {
                ArrayList<String> arr = getTypeOverridesInText(typeInt, ops, null);
                if (arr==null || arr.size()<2) return;//just header, nothing to print
                if (statCrunchDir) {
                    dir = ops.statsCrunchDir(typeInt);
                }
                FileOps.printOutALtoFile2(dir, arr, fileName, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Fetch an HourlyAdjustment based on the time and pollutant species.
     *
     * @param dt the time (for times with uneven hours an interpolated
 HourlyAdjustment will be returned.
     * @param typeInt the pollutant species
     * @param ops options
     * @return an HourlyAdjustment instance
     */
    public HourlyAdjustment get(Dtime dt, int typeInt, FusionOptions ops) {
        int sysH = dt.systemHours();
        return get(sysH, dt.min, typeInt, ops);
    }

    private boolean smoothOverrides = true;

    /**
     * Return existing OverrideList if created and found. In case minutes are
     * larger than 15 minutes a smooth composite is computed and stored from two
     * other stored OverrideLists.
     *
     * @param sysH the systemHour
     * @param mins minutes - used for interpolation if non-zero
     * @param typeInt the pollutant species
     * @param ops options
     * @return an OL instance
     */
    private HourlyAdjustment get_int(int sysH, int mins, int typeInt, FusionOptions ops) {
// sysH-= ops.forecastingOffsetTester_h;//take one from the past if this is different than zero.

        ConcurrentHashMap<Integer, HourlyAdjustment> hash = this.overrides[typeInt];
        if (mins >= 15 && smoothOverrides) {//prepare to get a composite of two existing overrideLists.
            float f2;//weight for next ol.
            int keyAdd;
            if (mins >= 45) {
                f2 = 0.75f;
                keyAdd = 3;
            } else if (mins >= 30) {
                f2 = 0.5f;
                keyAdd = 2;
            } else {
                f2 = 0.25f;
                keyAdd = 1;
            }
            float f1 = 1f - f2;

            //check custom key
            long customK = (long) (sysH) * 10 + keyAdd;
            HourlyAdjustment oc = this.overrides_quarts[typeInt].get(customK);
            if (oc != null) {
                return oc;//already existed,just return

            } else {//create new composite and put it to hash
                HourlyAdjustment o1 = hash.get(sysH);
                HourlyAdjustment o2 = hash.get(sysH + 1);

                if (o1 != null && o2 != null) {//can be computed and stored

                    oc = new HourlyAdjustment(o1.date, null, null, null, typeInt);//not technically accurate but will do.
                    for (int i = 0; i < o1.overrides.length; i++) {
                        oc.overrides[i] = f1 * o1.overrides[i] + f2 * o2.overrides[i];
                    }

                    this.overrides_quarts[typeInt].put(customK, oc);
                    return oc;

                }//both exist
            }//if creation needed

        }//if smooth overrides    

//otherwise do the simple thing.    
        return hash.get(sysH);
    }

    /**
     * Fetch an OverrideList based on the time and pollutant species. will be
     * returned.
     *
     * @param sysH system hours
     * @param min minutes (interpolation occurs if non-zero)
     * @param typeInt the pollutant species
     * @param ops options
     * @return an HourlyAdjustment instance
     */
    public HourlyAdjustment get(int sysH, int min, int typeInt, FusionOptions ops) {
        HourlyAdjustment ol = get_int(sysH, min, typeInt, ops);
        return ol;

    }

    /**
     * Add an instance to the OverrideController's hashMap.
     *
     * @param ol the instance to be added
     * @param sysH systemHour
     * @param typeInt the pollutant species.
     */
    public void put(HourlyAdjustment ol, int sysH, int typeInt) {
        this.overrides[typeInt].put(sysH, ol);
        //log penalty weights
        ol.logPenaltyWeights();
        if (sysH < this.minHC[typeInt]) {
            this.minHC[typeInt] = sysH;
        }
        if (sysH > this.maxHC[typeInt]) {
            this.maxHC[typeInt] = sysH;
        }

    }
    
        public void put_fromStats(HourlyAdjustment ol, int sysH, int typeInt) {
        this.overrides[typeInt].put(sysH, ol);

        if (sysH < this.minHC[typeInt]) {
            this.minHC[typeInt] = sysH;
        }
        if (sysH > this.maxHC[typeInt]) {
            this.maxHC[typeInt] = sysH;
        }
    }


    /**
     * Clear all HourlyAdjustment instances that the OverrideController currently
 holds.
     */
    public void clearAll() {
        EnfuserLogger.log(Level.FINER,AdjustmentBank.class,"Clearing all stored overrides.");

        for (int t = 0; t < minHC.length; t++) {
            minHC[t] = Integer.MAX_VALUE;
            maxHC[t] = -1;
        }
        for (ConcurrentHashMap<Integer, HourlyAdjustment> ov : this.overrides) {
            ov.clear();
        }

        for (ConcurrentHashMap<Long, HourlyAdjustment> ov : this.overrides_quarts) {
            ov.clear();
        }
        this.smoothened = new boolean[ops.VARA.VAR_LEN()];
    }

    private HourlyAdjustment createFiller(int sysH, FusionCore ens, int typeInt) {
        int addH = 0;
        for (int sh = sysH; sh >= minHC[typeInt]; sh--) {
            HourlyAdjustment oltest = this.get(sh, 0, typeInt, ens.ops);
            if (oltest != null) {
                HourlyAdjustment ol = HourlyAdjustment.filler(oltest, addH);
                return ol;
            }
            addH++;
        }
        return null;//should never happen 
    }

    private ArrayList<HourlyAdjustment> getPreviousDiurnals(int HC, int typeInt,
            FusionOptions ops) {
        ArrayList<HourlyAdjustment> arr = new ArrayList<>();
        for (int i = 1; i < 4; i++) {
            int sysH = HC - 24 * i;
            HourlyAdjustment ol = this.get(sysH, 0, typeInt, ops);
            if (ol != null) {
                arr.add(ol);
            }
        }
        return arr;
    }

    private ArrayList<HourlyAdjustment> getPreviousList(int lastH, int typeInt,
            int N, FusionOptions ops) {
        ArrayList<HourlyAdjustment> arr = new ArrayList<>();
        for (int i = 1; i < N; i++) {
            int sysH = lastH - i;
            HourlyAdjustment ol = this.get(sysH, 0, typeInt, ops);
            if (ol != null) {
                arr.add(ol);
            }
        }
        return arr;
    }

    private float averageFactor(ArrayList<HourlyAdjustment> pds, int c) {
        if (pds.isEmpty()) {
            return 0f;
        }

        float factor = 0;
        for (HourlyAdjustment ol : pds) {
            factor += ol.overrides[c];
        }
        return factor / pds.size();
    }

    
    
    public HourlyAdjustment getStoreDummy(FusionOptions ops, int typeInt, Dtime time) {
       String key = typeInt +"_"+time.systemHours();
       HourlyAdjustment ol = this.dummies.get(key);
       if (ol==null) {
           ol = HourlyAdjustment.createDummy(time, ops,typeInt);
           this.dummies.put(key, ol);
       }
       return ol;
    }


}
