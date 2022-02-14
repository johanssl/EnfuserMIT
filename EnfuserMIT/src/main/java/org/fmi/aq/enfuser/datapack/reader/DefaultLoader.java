/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.reader;

import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.mining.feeds.Feed;
import org.fmi.aq.enfuser.mining.feeds.FeedFetch;

/**
 *
 * @author johanssl
 */
public class DefaultLoader {

   public final static String FPM_FORMS_NAME ="forms.dat"; 
   public final static String EMITTER_PACK_NAME ="abstractEmitterPack";

    public static MapPack loadAndGetMaps(FusionOptions ops) {
       DefaultLoader dl = new DefaultLoader(ops);
       Boundaries rangeLimit = ops.getRunParameters().bounds();
       dl.loadMaps(rangeLimit, false);
       return dl.getMaps();
    }
   
    private final HashMap<String, Object> loads = new HashMap<>();
    public FusionOptions ops;
    
    private MapPack mp;
    private DataBase db;
    public final static int LOADSPAN_INCREASE_H = 24 * 5;
    private ArrayList<Feed> ff;
    private DataPack merged=null;
    public DefaultLoader(FusionOptions ops) {
        this.ops = ops;
    }

    public void loadMaps(Boundaries mapLimiter, boolean forms) {
        EnfuserLogger.log(Level.FINE,this.getClass(),
                "Loader: loading osmLayers content.");
        ArrayList<File> mapFiles = getOsmLayerFiles(mapLimiter);

        ArrayList<String> names = new ArrayList<>();
        for (File f:mapFiles) {
            names.add(f.getAbsolutePath());
        }
        if (forms) names.add(FPM_FORMS_NAME);
        load(names);          
    }
    
    public DataPack getMerged() {
        
        if (merged!=null) return merged;
        
        ArrayList<DataPack> dps = new ArrayList<>();        
        for (Object o : loads.values()) {
           
                if (o instanceof DataPack) {
                    dps.add((DataPack) o);
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "\t Loaded: a DataPack");
                }
           
        }//for all loaded objects
       
        if (dps.size()==0) return null;
        if (dps.size()==1) return dps.get(0);
        
        DataPack base = dps.get(0);
        for (int i =1;i<dps.size();i++) {
            DataPack dp2 = dps.get(i);
            base.merge(dp2, ops, this.getDB());
        }
        merged = base;
        merged.updateDBwithNewEntries(this.getDB());
        return merged;
    }
    
    public void loadDataPack(boolean emitters) {
        ArrayList<Feed> feeds = this.getReadables();
        
        ArrayList<String> names = new ArrayList<>();
        for (Feed f:feeds) {
            names.add("FEED="+f.mainProps.argTag);
        }
        if (emitters) names.add(EMITTER_PACK_NAME);
        load(names); 
    }
    
    public void loadDataPack(boolean emitters, ArrayList<String> feedNameFilter) {
        ArrayList<Feed> feeds = this.getReadables();
        
        ArrayList<String> names = new ArrayList<>();
        for (Feed f:feeds) {
            boolean listed = false;
            for (String test:feedNameFilter) {
                if (f.mainProps.feedDirName.contains(test) ||f.mainProps.argTag.contains(test)) {
                    listed = true;
                    EnfuserLogger.log(Level.INFO, this.getClass(),
                            "adding to feed list for DataPack: "+ f.mainProps.feedDirName);
                    break;
                }
            }
            if (!listed) continue;
            names.add("FEED="+f.mainProps.argTag);
        }
        if (emitters) names.add(EMITTER_PACK_NAME);
        load(names); 
    }
    
    public ArrayList<Feed> getReadables() {
        if (ff!=null) return ff;
        ff = FeedFetch.getFeeds(ops, FeedFetch.MUST_READ);
        return ff;
    }

    
    public ArrayList<File> getOsmLayerFiles(Boundaries mapLimiter) {
        ArrayList<File> maps = new ArrayList<>();
        
        String mapDir = ops.getDir(FusionOptions.DIR_MAPS_REGIONAL);
        EnfuserLogger.log(Level.FINE,this.getClass(),"Loader maps dir: " + mapDir);
        
        File f = new File(mapDir);
        File[] ff = f.listFiles();
        for (File test : ff) {
            String nam = test.getAbsolutePath();
            if (nam.contains("osmLayer.dat")) {

                if (mapLimiter != null) {
                    //check area
                    String[] temp = test.getName().split("_");
                    String name = temp[0];

                    //bounds
                    double latmin = Double.valueOf(temp[1]);
                    double latmax = Double.valueOf(temp[2]);
                    double lonmin = Double.valueOf(temp[3]);
                    double lonmax = Double.valueOf(temp[4]);
                    Boundaries btest = new Boundaries(latmin, latmax, lonmin, lonmax);

                    if (!btest.intersectsOrContains(mapLimiter)) {
                        EnfuserLogger.log(Level.FINE,this.getClass(),
                                "Loader: skipped a mapFile that is not within bounds: " + name);
                        continue;
                    }

                }

                maps.add(test);
                EnfuserLogger.log(Level.FINE,this.getClass(),
                        "Loader: adding map filename to list: " + nam);
            }
        }
        
      return maps;
    }
    
    public DataBase getDB() {
        if (db==null) db = new DataBase(ops);
        return db;
    }
    
    public MapPack getMaps() {
        if (mp!=null) return mp;
        
        ArrayList<OsmLayer> ols = new ArrayList<>();        
        for (String s:this.loads.keySet()) {
            if (s.contains("osmLayer")) {
                Object o = loads.get(s);
                
                if (o instanceof OsmLayer) {
                    ols.add((OsmLayer) o);
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t Loaded: an OsmLayer");
                }
            }//this stored object must be an osmLayer
        }//for all loaded objects
        
        //save OsmLayers
        if (!ols.isEmpty()) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Loader: there are " 
                    + ols.size() + " loaded OsmLayers");
            mp= new MapPack(ols,true);
        }
        
        return mp;
    }
    
    protected void load(ArrayList<String> instructions) {
        Threader.reviveExecutor();
        ArrayList<FutureTask> futs = new ArrayList<>();
        
        //Create threads
        for (String s: instructions) {//skip osmLayer, these are added below
            
            Callable cal = new DefaultLoadTask(s, ops, this.getMaps(),
                    this.getDB(), this.getReadables());
            FutureTask<DefaultLoadTask> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);

        }

        // MANAGE callables           
        boolean notReady = true;
        while (notReady) {//
            notReady = false; // assume that ready 
            float readyCount = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } else {
                    readyCount++;
                }

            }//for futs

            EnfuserLogger.log(Level.FINER,this.getClass(),"Threads ready for "
                    + "LoadTask: " + readyCount * 100.0 / futs.size() + "%");
            
            EnfuserLogger.sleep(2000,DefaultLoader.class);
        }// while not ready 

        
        
        //ready, collect data from strips
        for (FutureTask fut : futs) {
            try {
                if (fut == null) {
                    EnfuserLogger.log(Level.SEVERE, this.getClass(), "Loader: future-instance is null!");
                    continue;
                }
                if (fut.get() == null) {
                    continue;
                }
                Object[] o_name = (Object[]) (fut.get());
                Object o = o_name[0];
                String desc = (String)o_name[1];
                EnfuserLogger.log(Level.INFO, this.getClass(), "Loader: adding loaded object "+desc);
                this.loads.put(desc, o);
             

            } catch (InterruptedException | ExecutionException ex) {
                EnfuserLogger.log(ex, Level.SEVERE, this.getClass(), "Loader encountered"
                        + " an exception when casting results into objects.");
            } 

        }// for futures              

        Threader.executor.shutdown();

    }

    public void addMaps(MapPack maps) {
        this.mp = maps;
    }

    public void addCustom(String key, Object forms) {
        this.loads.put(key, forms);
    }

    public Object getCustom(String key) {
        return this.loads.get(key);
    }

}//Loader class END
