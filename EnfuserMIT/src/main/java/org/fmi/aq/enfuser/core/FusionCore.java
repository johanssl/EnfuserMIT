/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

/**
 * This class is the model's core. It contains a datapack, which in turn contain
 * all data sources and observations. There's also everything else needed for
 * the production of all ENFUSER data: GIS-data package, statistical database
 * and pre-calculated Plume Solution values.
 *
 * @author johanssl
 */
import org.fmi.aq.enfuser.core.assimilation.HourlyAdjustment;
import org.fmi.aq.enfuser.core.assimilation.AdjustmentBank;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.core.assimilation.Assimilator;
import org.fmi.aq.enfuser.customemitters.AbstractEmitter;
import org.fmi.aq.enfuser.customemitters.AgePack;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.ftools.Sector;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.HashMap;
import org.fmi.aq.enfuser.meteorology.HashedMet;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.ftools.FileCleaner;
import org.fmi.aq.enfuser.customemitters.BGEspecs;
import org.fmi.aq.enfuser.customemitters.ByteGeoEmitter;
import org.fmi.aq.enfuser.customemitters.TrafficEmitter;
import org.fmi.aq.enfuser.core.gaussian.fpm.SimpleAreaCell;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintBank;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.combiners.PartialCombiner;
import org.fmi.aq.enfuser.mining.feed.roadweather.RoadWeather;
import org.fmi.aq.enfuser.core.fastgrids.FastAreaGridder;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.datapack.source.Layer;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.ftools.RotationCell;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.core.setup.Setup;
import org.fmi.aq.enfuser.core.statistics.AvailabilityFilter;
import org.fmi.aq.enfuser.core.assimilation.PersistentAdjustments;
import org.fmi.aq.enfuser.meteorology.HashMetTask;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.enfuser.core.varproxy.ProxyProcessor;
import org.fmi.aq.interfacing.Neuralizer;
import org.fmi.aq.interfacing.WindProfiler;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.output.CompressedArchiver;
import org.fmi.aq.enfuser.customemitters.DustEmitter;
import org.fmi.aq.enfuser.parametrization.RESP;
import org.fmi.aq.enfuser.core.gaussian.fpm.Footprint;
import org.fmi.aq.enfuser.core.receptor.PredictionPrep;
import org.fmi.aq.enfuser.meteorology.RoughPack;
import org.fmi.aq.enfuser.datapack.reader.DefaultLoader;
import org.fmi.aq.enfuser.mining.feed.roadweather.Maintenance;
import org.fmi.aq.enfuser.options.ModellingArea;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.core.varproxy.AQI;
import org.fmi.aq.enfuser.mining.feed.roadweather.RoadMaintenance;
import org.fmi.aq.enfuser.mining.feed.roadweather.RoadWeatherFeed;
import org.fmi.aq.enfuser.options.PerformanceOptions;
import org.fmi.aq.enfuser.logging.DataPackCheck;
import org.fmi.aq.interfacing.Feeder;
import org.fmi.aq.interfacing.GraphicsMod;
import org.fmi.aq.interfacing.Launcher;

public class FusionCore extends DataCore{


    public static FusionCore getSoftInstance(TZT tzt, FusionOptions ops) {
       FusionCore ens = new FusionCore();
       ens.tzt = tzt;
       ens.ops = ops;
       ens.DB = new DataBase(ops);
       ens.overs_default = new AdjustmentBank(ops);
       ens.aqi = new AQI(ops);
      
       DefaultLoader loader = new DefaultLoader(ops);
       loader.loadMaps(ops.boundsExpanded(false),false);
       ens.mapPack = loader.getMaps();
       return ens;
    }

    private FusionCore() {
        
    }
    
    public AQI aqi;
    public RESP roadEmsSpeedProfiles;
    public Neuralizer neural;
    public ProxyProcessor prox;
    public ArrayList<RotationCell> orthoCells;
    private RoughPack rough;
    
    public Object jbar;
    public Object jbar2;
    
    private AvailabilityFilter availabilityFilter_stats = null;
    public FootprintBank forms;
    public Sector sectorCalc = new Sector();
    public FastAreaGridder fastGridder;

    public GeoGrid[] popDens_q_full = null;
    //overrides controller - one for typical operative use, many for evaluation time series generation, in which an OverrideController
    //is needed to be evaluated SEPARATELY for each station (the banned station). 
    private AdjustmentBank overs_default;

    private PuffPlatform ppf;
    public ArrayList<SimpleAreaCell> customCellArray;
    public boolean guiMode;
    public Dtime performanceAfter = null;

    public ArrayList<AbstractEmitter> ages;
    public RoadWeather roadWeather = null;
    public Maintenance roadMaintenance = null;
    public WindProfiler windprofiler;
    public static short FUSION_HEIGHT_M = 2;
    public Boundaries superBounds;
    public PersistentAdjustments CQL=null;
    public Feeder feeder;
    
    public AdjustmentBank getCurrentOverridesController() {
       return this.overs_default;//the usual case
    }

    public FusionCore(FusionOptions ops, DefaultLoader loader) {
        
        EnfuserLogger.log(Level.INFO,FusionCore.class,
                "\n================\nFusionCore init: "+FusionCore.VERSION_INFO+", "
                + Dtime.getSystemDate().getStringDate()
        +"\n==================");
        
                this.aqi = new AQI(ops);
                this.roadEmsSpeedProfiles = new RESP(ops);
        
        EnfuserLogger.log(Level.INFO, this.getClass(), "Merging DataPack...");
        this.datapack = loader.getMerged();
        EnfuserLogger.log(Level.INFO, this.getClass(), "Done...");
        this.DB = new DataBase(ops);//re-read can be needed. Note: the merging of DataPack needs
        //to happen first so that the db gets updated.
        this.g = GlobOptions.get();
        this.neural = new Neuralizer();
        if (ops.neuralLoadAndUse) neural.loadONC(ops);
        overs_default = new AdjustmentBank(ops);
       
        this.superBounds = this.datapack.getSuperBounds();
        this.guiMode = ops.guiMode;
        this.mapPack = loader.getMaps();
        this.ops = ops;
        this.forms = (FootprintBank)loader.getCustom(DefaultLoader.FPM_FORMS_NAME);
        RunParameters p = ops.getRunParameters();
        
        //proxy variable processing via interface
        this.prox = new ProxyProcessor(ops.VARA);
        this.prox.init(ops,p.nonMetTypes());
        Object o =loader.getCustom(DefaultLoader.EMITTER_PACK_NAME);
        if (o != null) {
            AgePack agep = (AgePack)o;
            this.ages = agep.ages;
        }
        //add OSMemitters
        for (OsmLayer ol : this.mapPack.layers()) {
            this.ages.add(new TrafficEmitter(ol,ops));
             this.ages.add(new DustEmitter(ol,ops));
        }

        if (ops.guiMode) {//check new areas based on maps
            Setup.checkNewAreas(ops);
        }
        
        if (ops.dfOps.CQL_restore) {
           this.CQL = PersistentAdjustments.load(ops, p.start(), 14);
        }
        
        Object rw = datapack.getCustom(RoadWeatherFeed.RW_OBJECT);
            if (rw!=null) {
                this.roadWeather = (RoadWeather)rw;
            }
        
        Object rm = datapack.getCustom(RoadMaintenance.RM_OBJECT);
            if (rm!=null) {
                this.roadMaintenance = (Maintenance)rm;
                this.roadMaintenance.printoutCheck(this.mapPack);
            }
        
        this.hashMet = new HashedMet(this.datapack, this.ops, ops.perfOps.metRes_m(), (float) ops.areaFusion_dt_mins());

        this.tzt = new TZT(p.loadStart().clone(), p.loadEnd().clone(),
                ops.local_zone_winterTime, ops.switchSummerTime);//true for automatic-ish summertime conversions


        this.windprofiler = new WindProfiler();
        if (ops.loadWindProfileGrids) {
            double[] coords = ops.bounds().toDoubles();
            String mapDir = ops.getDir(FusionOptions.DIR_MAPS_REGIONAL);
            windprofiler.loadForArea(coords, mapDir);
        }
        
        feeder = new Feeder();
        feeder.ingestCustomDataSets(this.datapack);

        FileCleaner.clean(ops);//cleanup old files in designated directories AFTER reading data. Before would be more natural, but this does the trick just as well when Enfuser is launched hour after hour
        this.orthoCells = FastMath.FM.getOrthoWindCells(ops.bounds());
        
        this.afterInit();
    }
    
    private void loadPuffPlatform() {
         RunParameters p = ops.getRunParameters();
        //prepare PuffPlatform and roughness===================

        //puff modelling area resolution
        int p_resm = (int)ops.getPuffModellingResolution_m();
        AreaNfo in= new AreaNfo(p.bounds(),p_resm,Dtime.getSystemDate());
        this.rough = new RoughPack(in, ops,mapPack);
        boolean mapSources = true;
        this.ppf = new PuffPlatform(this, p.start().clone(), p.end().clone(),
               mapSources,ops.areaFusion_dt_mins(), p_resm);
        //=====================================================
    }
    
        /**
     * Launch a series of additional data loads and processes which are to occur
     * right after FusionCore constructor has been used.
     */
    private void afterInit() {
        loadPuffPlatform();
        RunParameters p = ops.getRunParameters();
        //emitters
        if (this.ages == null || this.ages.isEmpty()) {
            this.ages = AbstractEmitter.readAllFromDir(this.ops, p.start(), p.end());
        }//requires ppf to have been initiated.
        //then browse osmLayer emitters and add them to list
        //browse OSM-layer emitters!
        readOsmLayerEmitters();
        
        prox.dataPackPreparations(this);
        this.datapack.finalizeStructures(this);
        EnfuserLogger.log(Level.INFO, this.getClass(), "Checking DataPack content...");
        DataPackCheck check = new DataPackCheck(this.datapack,ops);
        check.printout();
        
        //check osmLayer content and abstractEmitters
        EnfuserLogger.log(Level.INFO, this.getClass(), "List of loaded emitters:");
        for (AbstractEmitter e:this.ages) {
            String s = e.printout_oneline(ops);
            EnfuserLogger.log(Level.INFO, this.getClass(), "\t"+s);
        }
         EnfuserLogger.log(Level.INFO, this.getClass(), "List of loaded osmLayers:");
         int count =0;
        for (OsmLayer ol:this.mapPack.layers()) {
            EnfuserLogger.log(Level.INFO, this.getClass(), "\t"+ol.name);
            count++;
        }
        if (count==0) {
            EnfuserLogger.log(Level.SEVERE, this.getClass(), "There are no osmLayers loaded!");
        }
        if (this.forms==null) {
             EnfuserLogger.log(Level.SEVERE, this.getClass(), "form.dat for dispersion"
                     + " modelling precomputations have NOT been loaded properly!");
        }
        //go over sensor data if loaded
        Launcher.autoOffsetAllSensors(this);
        new GraphicsMod().stationLocationMapImages(this);//make PNG's with Geoapify for ALL unique & new station locations.
        Launcher.applyTestingMods(this);//does nothing in operational systems.
    }
    
    /**
     * A dummy method to get traffic flow measurement data. 
     * @param lat
     * @param lon
     * @param dt
     * @return 
     */
    public double getTrafficFlowData(double lat, double lon, Dtime dt) {
        return 0;
    }

    /**
     * For the processing of large output datasets, clear all memory intensive
     * temporary data structures.
     */
    public void clearMemoryForOuputPostprocessing() {
        EnfuserLogger.log(Level.FINER,FusionCore.class,"FusionCore: clearing temporary data structures from memory.");
        if (this.fastGridder != null) {
            this.fastGridder.clearTemporaryContent();
        }
        this.fastGridder = new FastAreaGridder(this.fastGridder.in, this);
        System.gc();
    }

    private int ortho_errs = 0;
    public Float getModelledBG(int typeInt, Dtime dt, double lat, double lon, boolean windOrthogonal) {
        Float f = null;
        try {
            GridSource gs = (GridSource) datapack.variables[typeInt].getSource(ops.regionalBG_name);
            f = gs.getSmoothValue(dt, lat, lon);

            if (windOrthogonal) {
                Met met = this.getStoreMet(dt, lat, lon);
                int wd = met.getWdir().intValue();
                Float[] orthos = new Float[this.orthoCells.size()];
                int i = -1;
                for (RotationCell e : this.orthoCells) {
                    i++;
                    float clat = e.currentLat_fast((float) lat, wd);
                    float clon = e.currentLon_fast((float) lon, wd);

                    orthos[i] = gs.getSmoothValue(dt, clat, clon);

                }//for ortho
                
                //there should be three values in the array. To get rid of local
                //anthropogenic contributes, take the minimum (or for some specific pollutants e.g. O3 take max)
                boolean naturalLower = ops.VARA.naturalLower(typeInt);
                float ave = 0;
                float natural;
                if (naturalLower) {
                    natural = Float.MAX_VALUE;
                } else {
                    natural = Float.MAX_VALUE * -1;
                }

                for (float o : orthos) {
                    if (naturalLower && o < natural) {
                        natural = o;
                    } else if (!naturalLower && o > natural) {
                        natural = 0;
                    }
                    ave += o / this.orthoCells.size();
                }
                return natural;
            }//if wind orthogonal 

        } catch (Exception e) {
            ortho_errs++;
        }
        return f;
    }


    /**
     * Check whether puff modelling is available for this time.
     *
     * @param dt Time for the check. IF this is null, then just the availability
     * of PuffPlatform will decide this.
     * @return true or false.
     */
    public boolean puffModellingAvailable(Dtime dt) {
        if (ppfForcedAvailable) return true;
        if (this.ppf != null) {
            return ppf.checkAvailabilityOfData(dt);
        } else {
            return false;//not available
        }
    }
    
private boolean ppfForcedAvailable =false;
public void ppfForcedAvailable(boolean b) {
    this.ppfForcedAvailable =b;
}

    public void readOsmLayerEmitters() {
        //browse OSM-layer emitters!
        Boundaries bexp = ops.boundsExpanded(false);
        for (OsmLayer ol : mapPack.layers()) {
            if (ol.b.intersectsOrContains(bexp)) {//some overlap found
                for (BGEspecs specs : ol.layerEms_specs.values()) {
                    if (!specs.loadsAsEmitter()) {
                        continue;
                    }
                    ByteGeoGrid bg = ol.getByteGeo(specs.olKey);
                    
                    EnfuserLogger.log(Level.INFO,FusionCore.class,
                            "Abstract emitters: adding from osmLayer " + ol.name 
                                    + ", " + specs.olKey + "-" + specs.catS);
                    int catI = g.CATS.indexFromName(specs.catS);
                    if (catI < 0) {
                        EnfuserLogger.log(Level.WARNING,this.getClass(),
                                "Unknown emission category definition! "
                                + specs.catS + " found from osmLayer " + ol.name);
                        continue;
                    }
                    this.ages.add(new ByteGeoEmitter(ops, bg, specs,ol));
                }
            }
        }
    }

    public float[] getStoreSolarSpecs(Dtime dt, double lat, double lon) {
        return this.hashMet.getStoreSolarSpecs(dt, lat, lon);
    }

    public void addProgressBars(Object jbar, Object jbar2) {
        this.jbar = jbar;
        this.jbar2 = jbar2;
    }

    /**
     * Get a list of sources that have been banned for the given variable type.
     *
     * @param type Variable type in String form.
     * @return an array of Source names.
     */
    public ArrayList<String> getBansList(String type) {
        ArrayList<String> bans = new ArrayList<>();

        VariableData vd = this.datapack.getVarData(ops.getTypeInt(type));

        for (int i = 0; i < vd.sources().size(); i++) {
            if (vd.sources().get(i).banned) {
                bans.add(vd.sources().get(i).ID);
            }
        }

        return bans;
    }


    public void evaluateOverrideConditions(int typeInt,
            Dtime start, Dtime end, boolean smooth, boolean forcs, Boundaries bounds) {
        ArrayList<HourlyAdjustment> ols = new ArrayList<>();
        
        VariableData vd = this.datapack.variables[typeInt];
        if (vd==null) {
            EnfuserLogger.log(Level.INFO,FusionCore.class, 
                 "Data fusion phase skipped for "+ops.VARA.VAR_NAME(typeInt)
                         +", no data (even gridded) available.");
            return;
        } 
        ArrayList<StationSource> stations = vd.stationSources();
        if (stations.isEmpty()) {
            EnfuserLogger.log(Level.INFO,FusionCore.class, 
                 "Data fusion phase skipped for "+ops.VARA.VAR_NAME(typeInt)
                         +", no measurement data available.");
            return;
        }
        
        float aveErr = 0;
        float avePen = 0;
        float avePen_count = 0;
        int trueObs =0;
  
            Dtime time = start.clone();
            time.addSeconds(-3600);
            
            while (time.systemHours() <= end.systemHours()) {
                time.addSeconds(3600);

                HourlyAdjustment ol = Assimilator.evaluateOverrideConditions_new(
                        this, time.clone(), typeInt, bounds);
                if (ol != null) {
                    ols.add(ol);
                    trueObs+=ol.amountOfObservations();
                }

            }
            
        //add them to database
        AdjustmentBank oc = this.getCurrentOverridesController();
        for (HourlyAdjustment ol : ols) {
            //set the best one as active
            oc.put(ol, ol.dt.systemHours(), typeInt);//must add the test override so that it can have an effect on the evaluation, which will happen soon
            aveErr += ol.aveErr();
            avePen += ol.avePen();
            avePen_count += ol.averagePenalitesGiven();
        }
        EnfuserLogger.log(Level.INFO,FusionCore.class,"======/Overrides "+ ops.VAR_NAME(typeInt) + " /==========");
        EnfuserLogger.log(Level.INFO,FusionCore.class,"\t created = "+ ols.size()+", MAE = "
                + (aveErr / ols.size()) +", observations total = "+trueObs);
        
        EnfuserLogger.log(Level.FINER,FusionCore.class,"\t, average datapoint credibility penalty=" 
                + avePen / ols.size() + ", average penalties count=" + avePen_count / ols.size());

        if (smooth && ops.VARA.isPrimary[typeInt]) {
            oc.fillAndSmoothen(this, typeInt);
        }
        if (forcs && ops.VARA.isPrimary[typeInt]) {
            oc.futureFill(start,end, typeInt, this);
        }

    }


    public HashMap<String, BlockAnalysis> pointBAs = new HashMap<>();
    public void clearPointBA() {
        this.pointBAs.clear();
    }

    ArrayList<Observation> getExactMeasurements(Dtime dt, ArrayList<Integer> nonMetTypes) {

        ArrayList<Observation> obs = new ArrayList<>();
        for (int byt : nonMetTypes) {
            VariableData vd = this.datapack.variables[byt];
            if (vd == null) {
                continue;
            }

            for (AbstractSource s : vd.sources()) {
                if (s instanceof StationSource) {
                    StationSource st = (StationSource) s;
                    Observation o = st.getExactObservation(dt);
                    if (o != null) {
                        obs.add(o);
                    }
                }
            }
        }
        return obs;
    }

private final GraphicsMod bars = new GraphicsMod();
    private void calcAreaFusionMultiThread(AreaInfo in, boolean last) {

        AreaInfo inf = new AreaInfo(in.bounds, in.H, in.W, in.dt.clone(), in.types);
        if (this.jbar != null) {
            bars.setJbarValue(jbar,0, false);

        }
        Dtime time = in.dt.clone();

        EnfuserLogger.log(Level.INFO,FusionCore.class,"AreaFusion in MultiThread for " 
                    + in.shortDesc());
        Boundaries bm = ops.boundsExpanded(true).clone();
        this.hashMet.forceInit(in.dt.clone(), bm, this, false);//if the mets exist (and they should), then no new ones will be created.

        if (this.fastGridder == null) {
            this.fastGridder = new FastAreaGridder(in, this);
        }//store BlockAnalysis objects here. IF there are a lot of time layers to be modelled then this Bank will save a lot of time.
        this.fastGridder.checkReinit(in, this,FastAreaGridder.INIT_FOR_AREAFUSION);

        //bb will be null only for the first hour layer in multi-our AreaFusion.
        if (this.jbar != null) {
            bars.setJbarValue(jbar,0, false);
        }

        AreaFusion af=PartialCombiner.processArea(inf,this);  
        //postProcess and archive
        CompressedArchiver.archive(this,af,in,time, last);

    }


    /**
     * This method 'prepares' the modelling time span for final computations.
     * This can process meteorological data for fast computations,
 perform gaussian puff modelling and perform data fusion based on
 measurements.
     * 
     */
    public void prepareTimeSpanForFusion() {
        long t = System.currentTimeMillis();
        RunParameters p = ops.getRunParameters();
        boolean overs = p.overs;
        boolean puffs = p.puffs;
        Dtime spinStart = p.spinStart();
        Dtime start = p.start();
        int spinHours = start.systemHours() - spinStart.systemHours();
        Dtime end = p.end();
        this.hashMet.windFieldFusionForPeriod(start,end,this);
        boolean manualGUI = p.manualGUI;
        ArrayList<Integer> nonMet_typeInts = p.nonMetTypeInts(ops);
        EnfuserLogger.log(Level.INFO,FusionCore.class,
                "//////Preparation phase starting at "+ Dtime.getSystemDate().getStringDate_noTS());
        EnfuserLogger.log(Level.FINER,FusionCore.class,"Overrides="+overs);
        EnfuserLogger.log(Level.FINER,FusionCore.class,"Puffs="+puffs);
        EnfuserLogger.log(Level.FINER,FusionCore.class,"Meteorology:"+!manualGUI);
        //met testing
            Boundaries bexp = ops.boundsExpanded(true).clone();
            if (!manualGUI) {//do not do this for manual, long time series work! This process is relevant for operative use.
              
                if (ops.fasterHashedMet) {//launch a multi-threading task
                    //to create all needed Meteorology objects for the time span.
                    EnfuserLogger.log(Level.INFO,FusionCore.class,
                            "Preparation phase: creating met.dataset structures (multi-thread)");
                    HashMetTask.createForSpan(this, bexp, spinStart, end);
                }else {
                   EnfuserLogger.log(Level.INFO,FusionCore.class,
                           "Preparation phase: creating met.dataset structures (single-thread)"); 
                }
                
                Dtime dt = spinStart.clone();
                dt.addSeconds(-3600);
                while (dt.systemHours() <= end.systemHours()) {
                    dt.addSeconds(3600);
                        EnfuserLogger.log(Level.FINE,FusionCore.class,"HashedMet ForceReinit.");
                    
                    //do this only if not processed already - a slower single thread method
                    if (!ops.fasterHashedMet) this.hashMet.forceInit(dt, ops.boundsExpanded(true).clone(), this, false);
                    
                    //puffPlatform will be setup now and this means that if for some reason
                    //Env objects exist for Observations, these must be cleared for Assimilator and Overrides.
                    EnfuserLogger.log(Level.FINE,FusionCore.class,"Clearing Env object...");
                    clearSpecificEnvs(dt, this);
                }//while time
            }
            
            ///////////////PUFF PLATFORM ///////////////////////////
            if (this.ppf != null && puffs) {
                EnfuserLogger.log(Level.INFO,FusionCore.class,"////Preparation step - PuffModelling launching for "
                        + start.getStringDate_noTS() +" - " + end.getStringDate_noTS()
                +" with spin-up hours of "+ spinHours);
                
                this.ppf.iterateTimeSpan_forPuffs(spinHours, FUSION_HEIGHT_M, start.clone(), end, true);
            } else {
                EnfuserLogger.log(Level.FINE,FusionCore.class,"PuffPlatform not used.");
            }

            //then overrides
            if (!overs)return;
            EnfuserLogger.log(Level.INFO,FusionCore.class,"\n////DataFusion step");
            nonMet_typeInts = checkVariableOrderForDF(nonMet_typeInts);
            
            for (int typeI : nonMet_typeInts) {

                this.evaluateOverrideConditions(typeI, start, end,
                        p.smooth, p.forcs, p.bounds());
                if (ops.guiMode) {
                    EnfuserLogger.log(Level.FINER,FusionCore.class,
                            "Overrides done for: " + ops.VAR_NAME(typeI));
                }

            }//for types
            EnfuserLogger.log(Level.INFO,FusionCore.class,"////DataFusion step completed.\n");

        
        long t2 = System.currentTimeMillis();
        long secs = (t2-t)/1000;
         EnfuserLogger.log(Level.INFO,FusionCore.class,
                "//////Preparation phase completed at "+ Dtime.getSystemDate().getStringDate_noTS() 
                        +", took "+secs +" seconds.");
         
    }
    
         /**
      * Clear Env-objects from all Observations under the datapack
      * for which the timestamp (hour) matches.
     * @param dt the time for the clearing.
      * @param ens the core
      */  
    public static void clearSpecificEnvs(Dtime dt, FusionCore ens) {
        ArrayList<String> vars = ens.datapack.getVariablesNames();
        for (int i = 0; i < vars.size(); i++) {
            VariableData vd = ens.datapack.getVarData(vars.get(i));
            for (AbstractSource s : vd.sources()) {
                if (!s.dom.isStationary()) {
                    continue;
                }
                if (!(s instanceof StationSource)) {
                    continue;
                }
                StationSource st = (StationSource) s;
                PredictionPrep.resetSpecificEnvs(st,dt, ens);
            }
        }

    }
    
    private ArrayList<Integer> checkVariableOrderForDF(ArrayList<Integer> nonMet_typeInts) {
        int o3_ind = -1;
            for (int i = 0; i < nonMet_typeInts.size(); i++) {
                if ((int) nonMet_typeInts.get(i) == (int) ops.VARA.VAR_O3) {
                    o3_ind = i;
                    break;
                }
            }
            if (o3_ind != -1) {
                ArrayList<Integer> temp = new ArrayList<>();
                temp.add(ops.VARA.VAR_O3);
                for (int i = 0; i < nonMet_typeInts.size(); i++) {
                    if (i != o3_ind) {
                        temp.add(nonMet_typeInts.get(i));
                    }
                }
                nonMet_typeInts = temp;//replace this where O3 is now at index 0.
            }
            return nonMet_typeInts;
    }


    /**
     * This method produces an ArrayList of AreaFusion output.With the given
    * resolution (m) and time span an AreaFusion modelling task is launched for
    * each time step (step length defined in FusionOptions in minutes). Before
    * AreaFusion modelling prefusion task launches, which includes Gaussian
    * puff dispersion modelling for the time span in the area and Overrides
    * assessment for each hour (i.e., data fusion with measurements.) Note:
    * during each time step data archiving (also for AWS S3) is done during the
    * step. Modelling height is defined in FusionOptions (2m as default)

     */
    public void processAreaFusionSpan() {
         RunParameters p = ops.getRunParameters();
        //inner areas

        EnfuserLogger.log(Level.INFO,FusionCore.class,
                "Area computation phase starting at actual time: "+Dtime.getSystemDate().getStringDate());
       
        Dtime start = p.start();
        Dtime end = ops.getRunParameters().end();

        int secs = (int) (end.systemSeconds() - start.systemSeconds());

        if (this.jbar2 != null) {
            bars.setJbarValue(jbar2,0, false);
            bars.setJbarValue(jbar2,secs, true);
        }
        //============MAIN LOOP==================
        Dtime dt = start.clone();
        
        //boolean first = true;
        while (dt.systemSeconds() <= end.systemSeconds()) {
            int currentSecs = (int) (dt.systemSeconds() - start.systemSeconds());
            long nextSecs = dt.systemSeconds() + 60*p.areaFusion_dt_mins;
            boolean last = nextSecs > end.systemSeconds();
            if (last) EnfuserLogger.log(Level.FINER,FusionCore.class,"This is the LAST one.");
            
            EnfuserLogger.log(Level.INFO,FusionCore.class,"\n=%=%=%=%=//Concentration Computations " 
                    + dt.getStringDate() + " minutes " + currentSecs/60 + " of " 
                    + secs/60 +" //=%=%=%=%=%");
            ops.logBusy();
            long t = System.currentTimeMillis();
            AreaInfo inf = p.intoAreaInfo(dt.clone(),ops);
            
            try {
                this.calcAreaFusionMultiThread(inf,last);
                if (this.jbar2 != null) {
                    bars.setJbarValue(jbar2,currentSecs, false);
                }

                long t2 = System.currentTimeMillis();
                EnfuserLogger.log(Level.INFO,FusionCore.class,"=%=%=%=%=// took "
                        + (t2 - t) / 1000 + " seconds. //=%=%=%=%=\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            dt.addSeconds(60 * p.areaFusion_dt_mins);// update the running datetime
        }// while slices   

        //inners need to be assessed in a SEPARATE while loop to gain benefit of fastGrids BlockAnalysis bank!
        if (p.hasInners()) {
            
            for (ModellingArea m: p.getInners()) {
                p.setActiveInnerArea(m);//this will force the area and resolution to change.
                
                EnfuserLogger.log(Level.FINER,FusionCore.class,"INNER AREA FUSION for " 
                        + p.areaID()+ " with resolution " + p.res_m());

                dt = start.clone();

                while (dt.systemSeconds() <= end.systemSeconds()) {
                    int currentSecs = (int) (dt.systemSeconds() - start.systemSeconds());
                    long nextSecs = dt.systemSeconds() + 60*p.areaFusion_dt_mins;
                    boolean last = nextSecs > end.systemSeconds();
                    if (last) EnfuserLogger.log(Level.FINER,FusionCore.class,"This is the LAST one.");
                    long t = System.currentTimeMillis();

                    try {
                        
                        AreaInfo inf = p.intoAreaInfo(dt.clone(),ops);
                        EnfuserLogger.log(Level.INFO,FusionCore.class,"\n=%=%=%=%=//Concentration Computations INNER -" + ops.areaID() + "- " 
                                + dt.getStringDate() + " minutes " + currentSecs/60 + " of " + secs/60 +" //=%=%=%=%=%");
                        this.calcAreaFusionMultiThread(inf,last);//stores the results to arch, which is all that is needed
                        if (this.jbar2 != null) {
                            bars.setJbarValue(jbar2,currentSecs, false);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    dt.addSeconds(60 * ops.areaFusion_dt_mins());// update the running datetime 

                    long t2 = System.currentTimeMillis();
                    EnfuserLogger.log(Level.INFO,FusionCore.class,"=%=%=%=%=// took " 
                            + (t2 - t) / 1000 + " seconds. //=%=%=%=%=\n");
                    
                    ops.logBusy();
                }// while slices - INNER  
            }//for inners

            //reset options
            p.clearActiveInnerArea();
        }//if Inners =============

    }

//This methods produces a long String for the given Source arrayList index
    public String getSourceInfo(int index, VariableData vd) {
        String temp = vd.sources().get(index).getSourceInfo(this);
        return temp;
    }


    private HashMap<String, String> closest_RWSrefs = new HashMap<>();
    private final static String CRWS_REF_NA = "NULL";

    public String getStoreClosest_WSref(Observation o) {

        if (o==null || o.fusionPoint() ||roadWeather==null) {
            return null;
        }
        String test = this.closest_RWSrefs.get(o.ID);
        if (test != null) {
            if (test.equals(CRWS_REF_NA)) {
                return null;//has been evaluated, but a close by station is not available.
            }
            return test;
        }

        //whas not been tested yet
        String mref = roadWeather.getClosestStation(o.lat, o.lon);
        if (mref != null) {
            EnfuserLogger.log(Level.FINER,FusionCore.class,"StatCruncher: found a roadStation " 
                   + mref + "for " + o.lat + ", " + o.lon);
            this.closest_RWSrefs.put(o.ID, mref);
            return mref;
        } else {//was not found, don't search again
            this.closest_RWSrefs.put(o.ID, CRWS_REF_NA);
            return null;
        }

    }

    public Met getStoreNextHourMet(Dtime dt, float lat, float lon) {
        Dtime[] dates = this.tzt.getDates(dt);
        Dtime next = dates[TZT.NEXT_UTC];
        return this.hashMet.getStoreMet(next, lat, lon, this);
    }

    public boolean hasWindGrids() {
        if (this.windprofiler != null && !this.windprofiler.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }
    
    public float macroLogWind(float lat, float lon, float height_m, Dtime dt) {
        Met met = this.getStoreMet(dt, lat, lon);
        int wdir = met.getWdirInt();
        float rl = this.rough.getRoughness(lat, lon, this, wdir);
        float u2 = met.getWindSpeed_raw();
        return this.rough.logWind_limited(ops.MIN_WINDSPEED, u2, rl, height_m);
    }
    
        public float[] macroLogWind_NE(float lat, float lon, float height_m, Dtime dt) {
        Met met = this.getStoreMet(dt, lat, lon);
        int wdir = met.getWdirInt();
        float rl = this.rough.getRoughness(lat, lon, this, wdir);
        float N = met.getSafe(ops.VARA.VAR_WIND_N);
            N= this.rough.logWind_limited(ops.MIN_WINDSPEED, N, rl, height_m);
        
        float E = met.getSafe(ops.VARA.VAR_WIND_E);
            E= this.rough.logWind_limited(ops.MIN_WINDSPEED, E, rl, height_m);
        return new float[]{N,E};
    }
    

    public void clearAllOverrides() {
        EnfuserLogger.log(Level.FINER,FusionCore.class,
                "Clearing all Overrides information from FusionCore.");
        this.overs_default.clearAll();
    }

    public PuffPlatform getPPF() {
        return this.ppf;
    }

    public void setPPF(PuffPlatform ppf) {
        this.ppf = ppf;
    }

    public float[][] getPuffModelledAdditions_QS(float lat, float lon, Dtime dt) {
        if (puffModellingAvailable(dt)) {//take mid range dispersed concentrations from here
            return ppf.getAll_shg_QS(true, lat, lon, dt);//fetch data using kriging interpolation.
        } else {
            return null;
        }
    }

    public GeoGrid[] orthogonalBGgrid(Dtime dt, GridSource gs, float res_m) {
        Layer l = gs.getClosestLayer(dt, 6);
        if (l == null) {
            return null;
        }
        GeoGrid g = l.g;
        Boundaries b = new Boundaries(gs.dom.latmin, gs.dom.latmax, gs.dom.lonmin, gs.dom.lonmax);
        AreaNfo in = new AreaNfo(b, res_m, dt.clone());
        this.hashMet.forceInit(dt, b, this, true);

        float[][] dat = new float[in.H][in.W];
        float[][] dat_wd = new float[in.H][in.W];

        GeoGrid gg = new GeoGrid(dat, in.dt, in.bounds);
        GeoGrid gg_wd = new GeoGrid(dat_wd, in.dt, in.bounds);
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {

                double lat = in.getLat(h);
                double lon = in.getLon(w);
                boolean ortho = true;
                if (!(ops.bounds().intersectsOrContains(lat, lon))) {
                    ortho = false;
                }

                Float val = this.getModelledBG(gs.typeInt, dt, lat, lon, ortho);
                if (val != null) {
                    gg.values[h][w] = val;
                }
                try {
                    Met met = this.getStoreMet(dt, lat, lon);
                    gg_wd.values[h][w] = met.getWdir();
                } catch (Exception e) {

                }
            }
        }
        EnfuserLogger.log(Level.FINER,FusionCore.class,"Ortho errors = " + this.ortho_errs);
        return new GeoGrid[]{gg, gg_wd};
    }
    
    public final static Boolean NEURAL_OVERRIDE = true;
    public final static Boolean DF_OVERRIDE = false;
    public HourlyAdjustment getOverrideForObservation(Observation o, boolean neural) {
        if (o==null) return null;
        if (neural) {
            
            if (!this.neural.hasONC()) return null;
            Object olo= this.neural.getOverride(this, o);
            if (olo==null) {
                return null;
            } else {
              HourlyAdjustment ol =(HourlyAdjustment)olo;  
              return ol;
            }
           
        }
       return this.getCurrentOverridesController().get(o.dt, o.typeInt, ops);
    }

    /**
     * For a 'debug line', add misc content that is not included in the traditional
     * set of information, such as BG values, components, meteorology etc.
     * This can be traffic, road condition related information for which there
     * is no dedicated variable type in the model.
     * The use-case for this information is statistics and debugging, and also
     * Deep learning method input creation.
     * @param ens the core
     * @param arr array where content is to be stacked
     * @param o The observation for which the stats are being generated.
     * This also holds the time and location that is to be used here.
     * @param HEADER if true, then add labels and not actual data.
     * If true, then the Observation is assumed to be null.
     */
    public static void addCustomDebugs(FusionCore ens,ArrayList<String> arr,
            Observation o, boolean HEADER) {
        
        Dtime dt;
        double lat;
        double lon;
        if (o!=null) {
            dt = o.dt;
            lat = o.lat;
            lon = o.lon;
        
        addCustomDebugs(ens,arr, dt, lat, lon, HEADER);
      }//for hour lags
        else {
            addCustomDebugs(ens,arr, null, 0, 0, true);
        }
    }
    
    
        public static void addCustomDebugs(FusionCore ens,ArrayList<String> arr,
            Dtime dt, double lat, double lon, boolean HEADER) {
      //make an implementation
      
      //historical wind
      for (int i =0;i<3;i++) {
          int hourLag = i*8;
          if (HEADER) {
              arr.add("2WIND_N_LAG"+hourLag);
              arr.add("2WIND_E_LAG"+hourLag);
          } else {
              Dtime dtt = dt;
              if (i>0) {
                  dtt = dt.clone();
                  dtt.addSeconds(-3600*hourLag);
              }
              Met met = ens.getStoreMet(dtt,lat,lon);
              long n = Math.round(met.getSafe (ens.ops.VARA.VAR_WIND_N)*2);//every 0.5 m/s intervals.
              long e = Math.round(met.getSafe (ens.ops.VARA.VAR_WIND_E)*2);
              arr.add(n+"");
              arr.add(e+"");
          } 
      }//for hour lags
    }

    public void setProgressBar(int i) {
        if (this.jbar!=null) {
             bars.setJbarValue(jbar, i, false);
        }
    }

      public BlockAnalysis getBlockAnalysis(OsmLayer ol, Observation o) {
          return getBlockAnalysis(ol, o.fusionPoint(),
            o.lat, o.lon, o.ID);
      }
    
    public BlockAnalysis getBlockAnalysis(OsmLayer ol, boolean fusionPoint,
            float lat, float lon, String ID) {
        
       if (fusionPoint) {
           if (ol==null) ol = this.mapPack.getOsmLayer(lat, lon); 
           return this.fastGridder.getBlock(ol, this, lat, lon);
           
       } else {//stationary
           
           BlockAnalysis ba = this.pointBAs.get(ID);
            if (ba == null) {
                
                if (ID.equals(BlockAnalysis.TEMPORARY_LOCATION)) {
                     if (ol==null) ol = this.mapPack.getOsmLayer(lat, lon); 
                     return new BlockAnalysis(ol, this.ops, lat, lon, null);
                } 
                
                DBline dbl = this.DB.get(ID);
                if (ol==null) ol = this.mapPack.getOsmLayer(lat, lon); 
                ba = new BlockAnalysis(ol, ops, lat, lon, dbl);
                this.pointBAs.put(ID, ba);
                EnfuserLogger.log(Level.FINER,FusionCore.class,
                        "Added new point BlockAnalysis to hash for " + ID 
                                + ", " + lat + ", " + lon);

            }
        return ba;
       }
    }

    public void addAvailabiltyFilter(String[] source_var) {
        if (availabilityFilter_stats==null) {
            availabilityFilter_stats = new AvailabilityFilter();
        }
        
        String sourc = source_var[0];
        String var = source_var[1];
        EnfuserLogger.log(Level.INFO,this.getClass(),"Adding availability filter: " + sourc + " - " + var);
        availabilityFilter_stats.addSourceVar_availabilityCondition(sourc, var);
    }

    public void printoutAvailabilityFilter() {
        if (this.availabilityFilter_stats==null) return;    
        Dtime start = datapack.getMinDt(true);
        Dtime end =   datapack.getMaxDt(true);
        availabilityFilter_stats.getUnavailableList(this, start, end, true);
    }

    public boolean sufficientDataForTime(FusionCore ens, Dtime current) {
       if (this.availabilityFilter_stats==null) return true;
       return availabilityFilter_stats.dataAvailable(ens, current);
    }

    public boolean hashedEmissionsAvailable(Observation ob, String osmLayerName) {
       if (this.fastGridder==null) return false;
       return this.fastGridder.hasHashedEmissionGrids(ob, osmLayerName);
    }

    public Footprint selectForm(float[] wdirs, boolean forMeasurement) {
      int perfSetting = ops.perfOps.plumeFormPerformanceValue();
      //for multi-dir case the standard slow assessment is NOT allowed.
      if (wdirs.length >1 && perfSetting == PerformanceOptions.PLUMEPERF_STANDARD) {
          perfSetting = PerformanceOptions.PLUMEPERF_FAST;
      }
      //take into account the use case
      if (forMeasurement) {//measurement - the fastest option should not be used.
          if (perfSetting == PerformanceOptions.PLUMEPERF_STANDARD) {
              return forms.standard;
          } else {
              return forms.performance;
          }
          
      } else {//area fusion
         if (perfSetting == PerformanceOptions.PLUMEPERF_STANDARD) {
            return forms.performance;//this is intentional. Let's not use standard in area fusion at all. 
         } else if (perfSetting == PerformanceOptions.PLUMEPERF_FAST) {
            return forms.performance; 
         } else {
           return forms.fastest;  
         }
      }

    }

    public RoughPack rough() {
        return this.rough;
    }

}//Class bracket end

