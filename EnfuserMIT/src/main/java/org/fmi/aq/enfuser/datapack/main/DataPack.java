/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.main;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.mining.feeds.IDsearcher;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.options.VarAssociations;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.enfuser.logging.GridQC;

/**
 *This class presents the highest hierarchy for input dataset holding and
 * access. This holds a collection of VariableData instances (2nd tier) which
 * hold variable-specific data.
 * 
 * The content of DataPacks come from Feeds. For parallel data read we
 * create a new DatapPack for each feed and later on merge multiple instances
 * into a single DataPack.
 * 
 * The class has also a 'shadow clone' storage of data that has been omitted
 * during the load.
 * 
 * Some of the data containers given by Feeds are not instances of VariableData
 * (the data type has not been specified in varAssociations) and these
 * data containers are treated as Objects in hash. 
 * 
 * @author johanssl
 */
public class DataPack {

    public VariableData[] variables;
    private final VariableData[] variables_omitted;
    protected HashMap<String,Object> customs = new HashMap<>();
    public int size = 0;
    final VarAssociations VARA;
    
    private HashMap<String, Boolean> newDBentries = new HashMap<>();
    
    public DataPack(FusionOptions ops) {
        VARA = ops.VARA;
        this.variables = new VariableData[VARA.VAR_LEN()];
        this.variables_omitted = new VariableData[VARA.VAR_LEN()];
    }

    public DataPack(DataBase DB, FusionOptions ops, MapPack mp, ArrayList<Feed> feeds) {
        VARA = ops.VARA;
        this.variables = new VariableData[VARA.VAR_LEN()];
        this.variables_omitted = new VariableData[VARA.VAR_LEN()];
        RunParameters p = ops.getRunParameters();

        // multi var extraction
        for (Feed feed : feeds) {
           feed.read(this, p, DB, mp); 
        }
        this.newDBentries = DB.newDBlines;
    }
    
        public DataPack(DataBase DB, FusionOptions ops,
                MapPack mp, ArrayList<String> typeInts, Dtime start, Dtime end) {
        VARA = ops.VARA;
        ArrayList<Feed> feeds = FeedFetch.getFeeds(ops, FeedFetch.MUST_READ);
        this.variables = new VariableData[VARA.VAR_LEN()];
        this.variables_omitted = new VariableData[VARA.VAR_LEN()];
        RunParameters p = ops.getRunParameters();

        // multi var extraction
        for (Feed feed : feeds) {
           feed.read(this, p, DB, null); 
        }
        this.newDBentries = DB.newDBlines;
    }
    
    /**
     * Take all content from the new DataPack instance and merge it to
     * this instance.
     * @param newdp new DataPack instance
     * @param ops options
     * @param DB database
     */
    public void merge(DataPack newdp, FusionOptions ops, DataBase DB) {
        boolean[] basic_omitted = {true,false};
       for (boolean basic:basic_omitted) { 
           
            for (VariableData newvd:newdp.variables(basic)) {
                if (newvd==null) continue;

                if (this.getVarData(newvd.typeInt,basic)==null) {
                    this.addVariable(newvd,basic);

                } else {
                   VariableData existing = this.getVarData(newvd.typeInt,basic);
                    ArrayList<AbstractSource> newSources = newvd.sources();
                    for (AbstractSource s: newSources) {
                        if (s instanceof StationSource) {
                            StationSource st = (StationSource)s;
                                existing.addSource(st,ops,DB);
                            

                        }else {
                            GridSource gs = (GridSource)s;
                                existing.addSource(gs,ops,DB);  
                        }
                    }//for new sources
                }//if variable data merge
            }//for variableData
       }//for basic - omitted
       
       for (String key:newdp.customs.keySet()) {
           Object o = newdp.customs.get(key);
           if (this.customs.get(key)!=null) {
               EnfuserLogger.log(Level.WARNING,this.getClass(),
               "Merging custom objects when the key "+key +" is already in use!");
           }
           this.customs.put(key, o);
       }
       
       
       for (String key:newdp.newDBentries.keySet()) {
           this.newDBentries.put(key, Boolean.TRUE);
       }
    }
    
    /**
     * Causes new DB-lines to be printed to a proper db.csv file.
     * This should be used to a 'merged' DataPack instance.
     * @param DB 
     */
    public void updateDBwithNewEntries(DataBase DB) {
       if(this.newDBentries.size()>0) DB.newDBlinesToFile(newDBentries);
    }
    
    protected VariableData[] variables(boolean basic) {
        if (basic) return this.variables;
        return this.variables_omitted;
    }
    
    protected VariableData getVarData(int typeInt, boolean basic) {
        return variables(basic)[typeInt];
    }
    
    protected void addVariable(VariableData vd, boolean basic) {
        variables(basic)[vd.typeInt] = vd;
    }
    
    public Float getMaxObserved(Dtime dt, double distMax_km, int typeInt,
            DataCore ens, double lat, double lon) {
        Float max = null;

        VariableData vd = this.variables[typeInt];
        if (vd == null) {
            return null;
        }

        for (AbstractSource s : vd.sources()) {
            if (s.banned) {
                continue;
            }
            if (s instanceof StationSource) {
                StationSource st = (StationSource) s;
                if (Observation.getDistance_m(lat, lon, st.lat(), st.lon()) * 1000 > distMax_km) {
                    continue;//too far away, skip
                }
                Observation o = st.getExactObservation(dt, 0);
                if (max == null && o != null) {
                    max = o.value;
                } else if (o != null && o.value > max) {
                    max = o.value;
                }
            }
        }

        return max;
    }

    public void finalizeStructures(DataCore ens) {
        
        for (VariableData vd : this.variables) {
            if (vd == null) {
                continue;
            }

            for (AbstractSource s : vd.sources()) {
                s.finalizeStructure(ens);
            }

        }
        
    }

    public ArrayList<double[]> getUniqueCoordinatesForStatSource(int typeInt) {
        ArrayList<double[]> c = new ArrayList<>();

        if (!this.containsVar(typeInt)) {
            EnfuserLogger.log(Level.FINER,DataPack.class,""
                    + "DataPack.getUniqueCoordinatesForSource: "
                    + "no variableData for this typeInt.");
            return c;
        }

        VariableData vd = this.getVarData(typeInt);

        ArrayList<String> uniques = new ArrayList<>();
        String coords;
        for (AbstractSource s : vd.sources()) {
            if (!s.dom.isStationary()) {
                continue;
            }
            if (!(s instanceof StationSource)) {
                continue;
            }
            StationSource st = (StationSource) s;
            for (Observation o : st.getLayerObs()) {
                coords = o.lat + "_" + o.lon;
                if (!uniques.contains(coords)) {
                    uniques.add(coords);
                }
            }

        }

        EnfuserLogger.log(Level.FINER,DataPack.class,
                "DataPack.getUniqueCoordinatesForSource: Unique "
                        + "coordinate pairs: " + uniques.size());

        for (String s : uniques) {
            String[] temp = s.split("_");
            double lat = Double.valueOf(temp[0]);
            double lon = Double.valueOf(temp[1]);
            double[] latlon = {lat, lon};
            c.add(latlon);
        }

        return c;
    }

    public String getFileContributionList() {
        String s = "";
        for (VariableData vd : this.variables) {
            if (vd == null) {
                continue;
            }
            s += "VariableType=" + VARA.VAR_NAME(vd.typeInt) + "\n";

            for (String file : vd.readFiles.keySet()) {
                s += "\t\t" + file + "\n";
            }
        }

        return s;
    }

    final static int T_LOOKBACK = 24;
    public static final boolean ALL_VARS = true;
    public static final boolean NO_MET_VARS = false;

    public Dtime getMinDt(boolean allVars) {

        long min = Long.MAX_VALUE;
        Dtime dtMin = null;
        for (int i = 0; i < this.variables.length; i++) {
            if (this.variables[i] != null) {

                if (!allVars && VARA.IS_METVAR(i)) {
                    continue;
                }

                Dtime dt = this.variables[i].start;
                if (dt.systemHours() < min) {
                    min = dt.systemHours();
                    dtMin = dt.clone();
                }
            }
        }

        
        if (dtMin==null) {
            String text = "DataPack: oldest date was searched across all variables and NOTHING was found. "
                    + "The dataPack is probably completely empty, and the program will terminate.";
            EnfuserLogger.log(Level.WARNING,this.getClass(),text);
            return null;        
        }
        
        EnfuserLogger.log(Level.FINER,DataPack.class,"Datapack: Min dt = " + dtMin.getStringDate());
        return dtMin;
    }

    public Dtime getMaxObservedDt() {
        long max = Long.MIN_VALUE;
        Dtime dtMax = null;
        for (VariableData vd : this.variables) {
            if (vd == null) {
                continue;
            }

            for (AbstractSource s : vd.sources()) {
                if (!s.dom.isStationary()) {
                    continue;
                }
                if (!(s instanceof StationSource)) {
                    continue;
                }
                StationSource st = (StationSource) s;
                for (Observation o : st.getLayerObs()) {

                    if (o.dt.systemSeconds() > max) {
                        max = o.dt.systemSeconds();
                        dtMax = o.dt;
                    }
                }//for obs
            }//for sourcs
        }//for vars

        if (dtMax == null) {
            EnfuserLogger.log(Level.INFO,DataPack.class,
                    "Datapack.getmaxObservedDt: No observed datapoints!");
            return null;
        }
        return dtMax.clone();
    }

    public Dtime getMaxDt(boolean allVars) {
        long max = Long.MIN_VALUE;
        Dtime dtMax = null;
        for (int i = 0; i < this.variables.length; i++) {
            if (this.variables[i] != null) {
                if (!allVars && VARA.IS_METVAR(i)) {
                    continue;
                }

                Dtime dt = this.variables[i].end;
                if (dt.systemHours() > max) {
                    max = dt.systemHours();
                    dtMax = dt.clone();
                }
            }
        }
        return dtMax;
    }

    public Boundaries getSuperBounds() {

        double latmin = Double.MAX_VALUE;
        double lonmin = Double.MAX_VALUE;

        double latmax = Double.MIN_VALUE;
        double lonmax = Double.MIN_VALUE;

        for (int i = 0; i < this.variables.length; i++) {
            if (this.variables[i] != null) {

                Boundaries b = variables[i].boundaries;
                if (b.latmax > latmax) {
                    latmax = b.latmax;
                }
                if (b.lonmax > lonmax) {
                    lonmax = b.lonmax;
                }

                if (b.latmin < latmin) {
                    latmin = b.latmin;
                }
                if (b.lonmin < lonmin) {
                    lonmin = b.lonmin;
                }
            }
        }

        Boundaries bSuper = new Boundaries(latmin, latmax, lonmin, lonmax);

        EnfuserLogger.log(Level.FINER,DataPack.class,
                "Datapack: superBounds = " + bSuper.toText());
        return bSuper;
    }


    public double[] getObservedMinMax(Dtime dt_start, Dtime dt_end, int typeInt,
            Boundaries b, DataCore ens) {
        double[] minMax = {Double.MAX_VALUE, Double.MIN_VALUE};

        VariableData vd = this.getVarData(typeInt);
        if (vd == null) {
            return minMax;
        }

        Dtime dt = dt_start.clone();
        ArrayList<Integer> sysHs = new ArrayList<>();
        for (int i = dt_start.systemHours(); i <= dt_end.systemHours(); i++) {
            int offsetH = i - dt_start.systemHours();
            sysHs.add(offsetH);
        }

        for (AbstractSource s : vd.sources()) {

            if (!(s instanceof StationSource)) {
                continue;//not stationary
            }
            StationSource st = (StationSource) s;
            if (s.banned) {
                continue;
            }
            if (!b.intersectsOrContains(st.lat(), st.lon())) {
                continue; // is stationary but out of bounds
            }
            for (int offH : sysHs) {
                Observation o = st.getExactObservation(dt_start, offH);
                if (o != null && o.value > minMax[1]) {
                    minMax[1] = (double) o.value;//update max
                }
                if (o != null && o.value < minMax[0]) {
                    minMax[0] = (double) o.value;//update min
                }
            }
        }//for sources

        dt.addSeconds(60 * ens.ops.areaFusion_dt_mins());

        return minMax;
    }
    
    
    public void addObservation(Observation obs, FusionOptions ops, DataBase DB) {

        boolean ok = this.checkObsContent(obs, ops);
        if (!ok) {
            if (this.variables_omitted[obs.typeInt] == null) {
                this.variables_omitted[obs.typeInt] = new VariableData(obs, ops, DB);
                return;
            }
            variables_omitted[obs.typeInt].addObservation(obs, ops, DB);// adds new source if needed
            return;
        }//add to "omitted"

        //check  
//check if variable already exists
        if (this.variables[obs.typeInt] == null) {
            this.variables[obs.typeInt] = new VariableData(obs, ops, DB);
            return;
        }
        variables[obs.typeInt].addObservation(obs, ops, DB);// adds new source if needed

    }

    public void addGrid(DBline dbl, int typeInt, GeoGrid g, String filename,
            DataBase db, FusionOptions ops) {

                //check quality
        try {
            
        GridQC qc = GridQC.assessGridQuality(g, typeInt,ops);
            if (!qc.isAcceptable()) {
                    //String s = qc.getPrintout();
                    //s  = (dbl.ID +", "+s);
                    //EnfuserLogger.log(Level.WARNING, GridSource.class,s);
                    //add to omits
               if (g==null) return;     
                if (this.variables_omitted[typeInt] == null) {
                    this.variables_omitted[typeInt] = new VariableData(g,db,filename,dbl,typeInt,ops); 
                    return;
                }
                variables_omitted[typeInt].addGgrid(dbl, g, db, filename,ops);// adds new source if needed
                return;
                }
     
        } catch (Exception e) {
           EnfuserLogger.log(e,Level.WARNING, GridSource.class,"Grid addition to "
                   + "ource encounted an exception when checking grid quality. " 
                   + this.VARA.VAR_NAME(typeInt) +", "+ filename);
        }

        //check if variable already exists
        if (this.variables[typeInt] == null) {
            this.variables[typeInt] = new VariableData(g, db, filename, dbl, typeInt,ops); 
            return;
        }

        variables[typeInt].addGgrid(dbl, g, db, filename,ops);// adds new source if needed

    }

    /**
     * Print data source time series to a text array (csv-lines) that can be stored
     * as a csv-file.
     * @param lat latitude coordinate for non stationary data
     * @param lon longitude coordinate for non stationary data
     * @param start start time for series
     * @param end end time for series
     * @param typeInt variable type index
     * @param arr array of source ID's for which this task should be done for.
     * @param simple if true, then replaced data values are not shown.
     * A replaced data value can occur when an Observation is read from file
     * with higher priority source of information than was available previously.
     * In such cases the older value becomes a 'replced value'.
     * @return a list of lines.
     */
    public ArrayList<String> compareTimeSeries_multiSource(double lat, double lon,
            Dtime start, Dtime end, int typeInt, ArrayList<String> arr, boolean simple) {

        ArrayList<String> lines = new ArrayList<>();
        String header = ("lat=" + lat + "," + "lon=" + lon + "," 
                + start.getStringDate() + "," + end.getStringDate() + ";");

        VariableData vd = this.variables[typeInt];
        for (AbstractSource s : vd.sources()) {
             if (!arr.contains(s.ID)) continue;
            header += s.ID + ";";
            if (!simple)header+=";";
        }
        lines.add(header);

        Dtime current = start.clone();
        current.addSeconds(-3600);
        while (current.systemHours() <= end.systemHours()) {
            current.addSeconds(3600);
            String line = current.getStringDate() + ";";
            if (simple) line = line.replace(":00:00+0:00", "");
            //get data

            for (AbstractSource s : vd.sources()) {
                if (!arr.contains(s.ID)) continue;
                Observation o = s.getExactRawValue(current, lat, lon);
                if (o == null) {
                    line += ";";
                    if (!simple)line+=";";
                } else {
                    line += o.value + ";";
                    if (!simple)line+=o.replacedValue()+";";
                }
            }
            lines.add(line);
        }

        return lines;
    }

    public void clearTrustData() {
        for (VariableData vd : this.variables) {
            if (vd == null) {
                continue;
            }
            for (AbstractSource s : vd.sources()) {
                if (s instanceof StationSource) {
                    StationSource st = (StationSource) s;
                    st.clearTrustClasses();
                }
            }
        }
    }

    public ArrayList<String> getTrustDataPrintout(DataCore ens, Dtime start,
            Dtime end, int typeInt) {
        ArrayList<String> arr = new ArrayList<>();
        //if (!ens.ops.progressiveTrust) return arr;
        VariableData vd = this.variables[typeInt];

        String header = "TIME;NON-COMPLIANCE_CLASS ([0,5], larger is WORSE);";
        for (StationSource s : vd.stationSources()) {
            header += s.ID + ";";
        }

        header += "DF_WEIGHTS (%% of total);";
        //running trust values
        for (StationSource s : vd.stationSources()) {
            header += s.ID + ";";
        }

        header += "RELATIVE_DF_EFFECT(larger is better=more trust);";
        //running trust values
        for (StationSource s : vd.stationSources()) {
            header += s.ID + ";";
        }

        arr.add(header);

        Dtime current = start.clone();
        current.addSeconds(-3600);
        while (current.systemHours() <= end.systemHours()) {
            current.addSeconds(3600);

            String line = current.getStringDate_noTS() + ";;";
            for (StationSource s : vd.stationSources()) {
                Integer trust = s.getAbsoluteTrustClass(current, ens);
                if (trust == null) {
                    line += ";";
                } else {
                    line += trust + ";";
                }
            }

            line += ";";
            //norm weights
            for (StationSource s : vd.stationSources()) {
                Double w = s.getDF_weight_POA(current, ens);
                if (w == null) {
                    line += ";";
                } else {
                    line += w + ";";
                }
            }

            line += ";";
            //running trust values
            for (StationSource s : vd.stationSources()) {
                Double w = s.getRelativeTrustEffect(current, ens);
                if (w == null) {
                    line += ";";
                } else {
                    line += editPrecision(w, 2) + ";";
                }
            }

            arr.add(line);
        }//while time

        String dir = ens.ops.statsCrunchDir(typeInt);
        String fname = "trustSeries_" + VARA.VAR_NAME(typeInt) + ".csv";
        FileOps.printOutALtoFile2(dir, arr, fname, false);

        return arr;
    }


    public AbstractSource getOmittedSource(int typeInt, String ID) {
        try {
            return this.variables_omitted[typeInt].getSource(ID);
        } catch (Exception e) {

        }
        return null;
    }

    private boolean checkObsContent(Observation obs, FusionOptions ops) {

        if (!VARA.IS_METVAR(obs.typeInt) && obs.value < 0) {
            obs.value = 0;
        }

        if (Float.isNaN(obs.value)) {
            obs.value = 0;
            obs.dt.freeByte = 120;
        }

        //check acceptance ranges
        return ops.isAcceptable(obs);
    }

    public final static int DTMOD_NONE = 0;
    public final static int DTMOD_BASIC = 1;
    public final static int DTMOD_ARCHIVE = 2;
//this method adds Observation to the DataPack. If the variable is new, a new Variable data is created
//Furthermore, if the source is new inside the proper vd, then a new source is created to that vd and Obs is added to it.

    public ArrayList<String> getVariablesNames() {
        ArrayList<String> types = new ArrayList<>();
        for (int i = 0; i < this.variables.length; i++) {
            if (variables[i] != null) {
                types.add(VARA.VAR_NAME(variables[i].typeInt));
            }
        }
        return types;
    }

    public boolean containsVar(int type) {
        if (this.variables[type] != null) {
            return true;
        }
        return false;
    }

    public VariableData getVarData(int type) {
        return variables[type];
    }

    public VariableData getVarData(String type) {
        int index = VARA.getTypeInt(type);
        return variables[index];

    }

    public String getInfo(DataCore ens) {
        StringBuilder sb = new StringBuilder();
        String n = System.getProperty("line.separator");

        for (VariableData vd : this.variables) {
            if (vd == null) {
                continue;
            }

            sb.append(VARA.VAR_NAME(vd.typeInt)).append(": ").append(n);
            sb.append("bounds: ").append(vd.boundaries.latmin).append("N - ")
                    .append(vd.boundaries.latmax).append("N , ").append(vd.boundaries.lonmin)
                    .append("E - ").append(vd.boundaries.lonmax).append("E").append(n);
            sb.append("Sources: ").append(n);

            for (AbstractSource s : vd.sources()) {
                sb.append(s.getSourceInfo(ens));
            }
        }

        return sb.toString();
    }
    
    /**
     * Get a non-standard data container that is stored with the given key
     * @param key the key
     * @return Object, that can be null if no such dataset have been added.
     * The returned object can be e.g., instance of class that holds traffic,
     * or road weather data.
     */
    public Object getCustom(String key) {
        return this.customs.get(key);
    }
    
    
    public void addCustom(String key, Object o) {
        if (o!=null)this.customs.put(key,o);
    }
    
}//class end
