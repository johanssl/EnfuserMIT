/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.main;

import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.util.ArrayList;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.source.Layer;

/**
 *
 * @author johanssl
 */
public class VariableData {

    private final HashMap<String, AbstractSource> sources;
    private final ArrayList<AbstractSource> sources_arr;

    public int sizeObs=0;
    public int sizeGrid=0;

    public int typeInt;
    public Dtime start;
    public Dtime end;
    public String unit;
    public Boundaries boundaries; //lonmin, lonmax, latmin, latmax

    public HashMap<String, Boolean> readFiles = new HashMap<>();

    //pescado constructor
    public VariableData(Observation obs, FusionOptions ops, DataBase DB) {
        this.typeInt = obs.typeInt;
        this.boundaries = new Boundaries(obs.lat, obs.lon);
        this.sources = new HashMap<>();
        sources_arr = new ArrayList<>();

        this.unit = ops.UNIT(typeInt);
        this.start = obs.dt;
        this.end = obs.dt;

        this.addObservation(obs, ops, DB);
    }

    public VariableData(GeoGrid g, DataBase DB, String filesource,
            DBline dbl, int typeInt, FusionOptions ops) {
        this.typeInt = typeInt;
        this.boundaries = g.gridBounds.clone();
        this.sources = new HashMap<>();
        sources_arr = new ArrayList<>();

        this.unit = ops.VARA.UNIT(typeInt);
        this.start = g.dt.clone();
        this.end = g.dt.clone();

        this.addGgrid(dbl, g, DB, filesource,ops);
    }

    public ArrayList<AbstractSource> sources() {
        return this.sources_arr;
    }

    
     void addSource(StationSource st,FusionOptions ops, DataBase DB) {
        for (Observation o:st.getLayerObs()) {
            this.addObservation(o, ops, DB);
        }
    }
     
     void addSource(GridSource gs,FusionOptions ops, DataBase DB) {
        for ( Layer l :gs.layerHash.values()) {
            this.addGgrid(gs.dbl, l.g, DB, l.sourceFile(),ops);
        }
    }
    
    
    public boolean isBannedSource(String ID) {

        boolean banned = false;
        AbstractSource s = this.sources.get(ID);
        if (s != null && s.banned) {
            return true;
        }

        return banned;
    }

    public AbstractSource getSource(String name) {
        return this.sources.get(name);
    }

    public StationSource getStationSource(String name) {
        AbstractSource s = this.sources.get(name);
        if (s != null && s instanceof StationSource) {
            return (StationSource) s;
        }
        return null;
    }
    
        GridSource getGridSource(String name) {
       AbstractSource s = this.sources.get(name);
        if (s != null && s instanceof GridSource) {
            return (GridSource) s;
        }
        return null;
    }

    public void addObservation(Observation obs, FusionOptions ops, DataBase DB) {
        DBline dbl;
        if (obs.typeInt != this.typeInt) {
            EnfuserLogger.log(Level.FINER,VariableData.class,
                    "Observation couldn't be added -wrong TYPE!");
            return;
        }    
            
         dbl = DB.get(obs.ID);
        if (dbl == null) {//this thing should be dealt with in Feeds during read()!
            EnfuserLogger.log(Level.FINER,VariableData.class,
                    "WARNING! a missing DBline encountered when adding a measurement at VariableData: "
                    + ops.VAR_NAME(this.typeInt) + ", " + obs.ID + ", " + obs.sourceFile);
           return;
        }
        
        this.boundaries.expandIfNeeded(obs.lat, obs.lon);

        if (this.start.systemHours() > obs.dt.systemHours()) {
            this.start = obs.dt;
        }
        if (this.end.systemHours() < obs.dt.systemHours()) {
            this.end = obs.dt;
        }

        //dbl search
        obs.ID = dbl.ID;//a potential swap from secondary to primary ID can occur here.
        dbl.modifyDateTime(obs);
        StationSource existing_s = this.getStationSource(dbl.ID);


        if (existing_s != null) {
            existing_s.add_observation(obs);
        } else {//new source

            existing_s = new StationSource(obs, DB,ops);
            //check auto-ban via DB
            boolean autoban = DB.typeAutoBanned(obs.ID, typeInt);
            if (autoban) {
                EnfuserLogger.log(Level.INFO,this.getClass(),
                        "Auto-banning " + existing_s.ID + ", " + ops.VAR_NAME(typeInt));
                existing_s.banned = true;
            }
            
            this.sources.put(obs.ID, existing_s);
            this.sources_arr.add(existing_s);
        }

        this.sizeObs++;
        if (obs.sourceFile != null) {
            if (this.readFiles.get(obs.sourceFile) == null) {
                this.readFiles.put(obs.sourceFile, Boolean.TRUE);
            }
        }
        // update vd start and end dates

    }

    public void addGgrid(DBline dbl, GeoGrid g, DataBase DB, String filesource,
            FusionOptions ops) {

        if (typeInt != this.typeInt) {
            EnfuserLogger.log(Level.FINER,VariableData.class,"GeoGrid couldn't be added -wrong TYPE!");
            return;
        }
        dbl.modifyDateTime(g);//timezones, summertime
        this.boundaries.expandIfNeeded(g.gridBounds.latmin, g.gridBounds.lonmin);
        this.boundaries.expandIfNeeded(g.gridBounds.latmax, g.gridBounds.lonmax);

        if (this.start.systemHours() > g.dt.systemHours()) {
            this.start = g.dt;
        }
        if (this.end.systemHours() < g.dt.systemHours()) {
            this.end = g.dt;
        }

        AbstractSource existing_s = this.getSource(dbl.ID);

        if (existing_s != null && (existing_s instanceof GridSource)) {
            //EnfuserLogger.log(Level.FINER,VariableData.class,"DataPack: Adding a layer to existing GridSource =============\n=====================");
            GridSource gs = (GridSource) existing_s;
            gs.addGrid(g,filesource);

        } else {
            //EnfuserLogger.log(Level.FINER,VariableData.class,"DataPack: Adding a NEW  GridSource =============\n=====================");  
            GridSource gs = new GridSource(DB, dbl, this.typeInt,ops);
            //check auto-ban via DB
            boolean autoban = DB.typeAutoBanned(dbl.ID, typeInt);
            if (autoban) {
                EnfuserLogger.log(Level.INFO,this.getClass(),
                        "Auto-banning " + gs.ID + ", " + ops.VAR_NAME(typeInt));
                gs.banned = true;
            }
            
            
            boolean success =  gs.addGrid(g,filesource);
            if (!success) return;
            
            this.sources.put(dbl.ID, gs);
            this.sources_arr.add(gs);
        }

        this.sizeGrid ++;
        if (filesource != null) {
            if (this.readFiles.get(filesource) == null) {
                this.readFiles.put(filesource, Boolean.TRUE);
            }
        }
        // update vd start and end dates

    }

   public ArrayList<StationSource> stationSources() {

        ArrayList<StationSource> sts = new ArrayList<>();
        for (AbstractSource s : this.sources_arr) {
            if (s instanceof StationSource) {
                sts.add((StationSource) s);
            }
        }
        return sts;
    }

   public  ArrayList<GridSource> griddedSources() {

        ArrayList<GridSource> sts = new ArrayList<>();
        for (AbstractSource s : this.sources_arr) {
            if (s instanceof GridSource) {
                sts.add((GridSource) s);
            }
        }
        return sts;
    }

    public Float getHighestQualityGriddedSmoothValue(Dtime dt, double lat, double lon) {
        float lowestVar = Float.MAX_VALUE;
        Float val = null;

        for (AbstractSource s : this.sources_arr) {
            if (s instanceof GridSource) {
                GridSource ss = (GridSource) s;
                float rating = ss.dbl.varScaler(this.typeInt);
                Float testVal = ss.getSmoothValue(dt, lat, lon);
                if (testVal != null && rating < lowestVar) {
                    lowestVar = rating;
                    val = testVal;
                }//if update best
            }//if gridded
        }//for sources

        return val;
    }

    public ArrayList<String> getSourceNames(boolean stations) {
        ArrayList<String> arr = new ArrayList<>();
        for (AbstractSource s:this.sources()) {
            String id = s.ID;
            if (stations) {
                if (s instanceof StationSource) {
                    arr.add(id);
                } 
            } else {
               arr.add(id);     
            }
    
        }
        return arr;
    }

    public ArrayList<String> printout(FusionOptions ops) {
       return null;
    }

    public String sizeString() {
       return ": observations="+this.sizeObs +", grids="+this.sizeGrid;
    }

    public int size() {
        return this.sizeGrid + this.sizeObs;
    }


}//end class

