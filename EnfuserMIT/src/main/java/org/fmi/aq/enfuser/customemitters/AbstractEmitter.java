/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.parametrization.TemporalFunction;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;
import static org.fmi.aq.enfuser.ftools.FuserTools.evaluateSpan;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.customemitters.EmitterSynch.synchEmitterFiles;
import org.fmi.aq.enfuser.core.gaussian.puff.ApparentPlume;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier;
import static org.fmi.aq.enfuser.core.gaussian.puff.PuffCarrier.DZ_EXP;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.core.gaussian.puff.Transforms;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.core.receptor.RPstack;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.core.varproxy.EmitterProxies;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.interfacing.Feeder;

/**
 * The AbstractEmitter describes a source of emissions represented spatially and
 * temporally. It has a distinct emission source category (e.g., power) and a
 * release height that can also have a spatial distribution.
 *
 * Each emitter should have a unique name. The characteristics of the emitter
 * are described via LightTempoMet object (LTM) that is tagged to it's emitter
 * by this unique name.
 *
 * There are a number of different kinds of emitters and these all extend this
 * abstract class.
 *
 * The Abstract emitters use the coordinate system of OsmLayer (h,w). The class
 * also provides methods to make a "short list" of emitters that are relevant
 * for the given time and location.
 *
 * To make things a bit more complicated, AbstractEmitters can be used to either
 * provide emission release data for Gaussian Plume modelling and also for
 * Gaussian Puff modelling. In ENFUSER the modelling contains both of these
 * simultaneously - some of the emitters can only work in Plume-mode but some
 * can work in both roles (such as an SHGemitter for shipping emissions). This
 * dynamic role swap is handled by operMode().
 *
 * @author Lasse Johansson
 */
public abstract class AbstractEmitter {
    public static boolean SHIPPING_AS_HQLINE = false;//if true, then CSVLINE emitters will not be read.
    //this causes that these emitters are read as add-on emitters => HQLINE emitter.
    
    public final static float BILLION = 1e9f;
    public final static float ZERO_THRESHOLD_OF_AVERAGE = 0.05f;//in percentages
    //of average grid cell. If a cell is lower than this amount in %, then it 
    //will be ignored (helps with ByteGeoGrids)

    protected AbstractEmitter(FusionOptions ops) {
        this.ep = new EmitterProxies(ops.VARA);
        this.VARA =ops.VARA;
        this.CATS = ops.CATS;
    }
    
    /**
     * Load an AbstractEmitter (or more precisely a list of AbstractEmitters)
     * from file. The emitter type instruction is defined by argument 'emType'.
     *
     * @param f the File to be read as emitter(s)
     * @param emType the emitter type.
     * @param ops FusionOptions that can affect the load operation (especially
     * for SHGemitter => temporal/spatial resolution can be adjusted)
     * @return a list of emitters.
     */
    private static ArrayList<AbstractEmitter> load(File f, int emType, FusionOptions ops) {

        ArrayList<AbstractEmitter> arr = new ArrayList<>();
        if (emType == LINES) {//lines in a grid
            arr.add(new Lines3Demitter(f, ops));

        } else if (emType == S2D_BWCOL) {//multi-q netCDF
            arr.add(new BWemitter(f, ops));

        } else if (emType == POINTS) {//multi-component
            ArrayList<Pem> ages = PemReader.readPemsFromDir(ops, f);
            for (Pem p : ages) {
                arr.add(p);
            }

        } else if (emType == CSVLINE_DYN) { //CSVLINE emitter
               //start with something special
               boolean added = false;
               if (ops.getArguments().hasCustomBooleanSetting("HQ_SHIPPING") || SHIPPING_AS_HQLINE) {
                   //attempt to read this as HQLINE emitter.
                    AbstractEmitter ae = new Feeder().readEmitter(emType, f, ops);
                    if (ae!=null) {  
                      added = true;  
                      arr.add(ae); 
                    }
               }//if special
               
               if (!added) {//nothing special was added, do the standard thing.
                CsvLineEmitter csvem = new CsvLineEmitter(f, ops);
                arr.add(csvem);
               }
               
        } else if (emType == NC2D) { //multi-Q 2D netCDF emitter grid
            NCemitter ncem = new NCemitter(f, ops);
            arr.add(ncem);

        } else if (emType == SINGLENC) { //mono, typeless 2D netCDF emitter grid
            SingleNCemitter ncem = new SingleNCemitter(f, ops);
            arr.add(ncem);    
            
        } else {//not known. Addon emitter?
            
            AbstractEmitter ae = new Feeder().readEmitter(emType, f, ops);
            if (ae!=null) {
                arr.add(ae);
                
            } else {
            EnfuserLogger.log(Level.FINER,AbstractEmitter.class,
                    "Emitter type NOT SUPPORTED YET! " + f.getAbsolutePath());
            }
        }

        return arr;
    }

    //fast checks
    //IMPLEMENT THIS GRID FOR EACH extending class!
    public boolean[][] isZeroForAll;//regardless of tempoMet scaling, these cells are ZEROS and can be skipped.
    public String dataUnit = "gs-1";
    public String emissionRateUnit = "µg/s/m2";

    ConcurrentHashMap<Integer, Boolean> isZeroForAll_h = new ConcurrentHashMap<>();//regardless of Met and lat,lon, the output is ZERO for these hours

    public long[] sysSecRanges;
    public LightTempoMet ltm;
    public AreaInfo in;
    protected final EmitterProxies ep;
    public int cat;
    public String myName;
    public String[] splittedName;
    public File origFile;
    public int gridType;
    public double height_def = 15;
    public Dtime nameStart = null;
    public Dtime nameEnd = null;
    public Boundaries nameBounds = null;

    public final VarAssociations VARA;
    public final Categories CATS;
    
    //MODE PROPERTIES
    public final static String[] OPERMODES = {"MapAreaSource", "IndividualSource(s)"};
    public final static int OPERM_MAP = 0; //map: deal as an map area source, 
    public final static int OPERM_INDIV = 1;//individual: singular entity puff source (or if no puff modelling is used then as added plume at ground_zero_location). Best option for e.g., power plants
    public boolean canFlexMode = false; //flexible: is an area source when puffModelling is not used, but an individual when puffModelling is used. This nice for, e.g., gridded shipping emissions
    protected int operMode = OPERM_MAP;
    public float puffDistRamp_m = 0;
    public float puffGenIncr = 1;

    public final static int S2D = 0;
    public final static int NC2D = 1;
    public final static int LINES = 2;
    public final static int S2D_MC = 3;
    public final static int S2D_BWCOL = 4;
    public final static int POINTS = 5;
    public final static int NC_DYN = 6;
    public final static int MOVING_POINTS = 7;
    public final static int BYTEGEO_2D = 8;
    public final static int CSVLINE_DYN = 9;
    public final static int HQLINE_DYN = 10;
    public final static int SINGLENC = 11;

    //typename,  format,     hasTemporal,    operationMode(map,flexible,individual), 
    public final static int SP_TYPE = 0;
    public final static int SP_FORMAT = 1;
    public final static int SP_DATES = 2;
    public final static int SP_OMODE = 3;

    //flex: 
    public final static String[][] SPECS = {
        {"S2D"          ,".nc"  ,"false", "map"}, //raw data is absolute emissions per cell => div by cellA_m
        {"NC2D"         ,".nc"  , "false", "map"}, //raw data is absolute emissions per cell => div by cellA_m
        {"LINES"        ,".csv" , "false", "map"}, //raw data is µg/s per line METER =>  converts into µgs-2m-2
        {"S2D-MC"       ,".nc"  , "false", "map"}, //raw data is absolute emissions per cell => div by cellA_m
        {"S2D-BWCOLOR"  ,".kmz" , "false", "map"}, //raw data is EMISSION DENSITY => no factoring of cellA_m => resolution should not affect much  
        {"POINT"        ,".csv" , "false", "individual"}, //this is g/s - grid representation is NOT used
        {"NC-DYN"       ,".nc"  , "true", "flex"}, //raw data is absolute emissions per cell => div by cellA_m (for puff individual mode cellA_m2 does not matter)
        {"MOVINGP"      ,".csv" , "true", "individual"}, //this is g/s - grid representation is NOT used
        {"BYTEGEO"      ,".dat" , "false", "map"}, //raw data is absolute emissions per cell (kg/A)=> div by cellA_m   
        {"CSVLINE-DYN"  ,".csv" , "true", "flex"},
        {"HQLINE-DYN"   ,".csv" , "true", "individual"},
        {"SINGLENC"     ,".nc"  , "false", "map"}    
    };

    //naming conventions
    //"SINGULAR2D_myname_cat_heightDef_tmin_tmax_latmin_latmax_lonmin_lonmax_.xxx" 
    public final static int NAMEIND_TYPE = 0;
    public final static int NAMEIND_ID = 1;
    public final static int NAMEIND_CAT = 2;
    public final static int NAMEIND_HDEF = 3;
    public final static int NAMEIND_TMIN = 4;
    public final static int NAMEIND_TMAX = 5;
    public final static int NAMEIND_LATMIN = 6;

    /**
     * Read AbstractEmitter default parameters from file name directly. All
     * filenames should be formatted according to the fixed order of
     * information, e.g., "TYPE-IDENTIFIER_ID-NAME_CATEGORY_...
     *
     * @param file emitter file
     */
    public void readAttributesFromFile(File file) {
        this.origFile = file;
        String nam = file.getName();

        if (nam.contains("FLOAT_allHeights")) {
            String rep = "SHG-DYN_steam_ship_60m";
            EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"\t switching Emitter filename (FLOAT_allHeights) to " + rep);
            
            nam = nam.replace("FLOAT_allHeights", rep);
        }

        this.gridType = getGridType(nam);
        this.splittedName = nam.replace(SPECS[this.gridType][SP_FORMAT], "").split("_");
        this.myName = this.splittedName[NAMEIND_ID];
        this.cat = CATS.indexFromName(splittedName[NAMEIND_CAT]);
        this.height_def = Double.valueOf(splittedName[NAMEIND_HDEF].replace("m", ""));

        String mode = SPECS[gridType][SP_OMODE];
        if (mode.contains("map")) {
            this.operMode = OPERM_MAP;
            this.canFlexMode = false;
        } else if (mode.contains("indiv")) {
            this.operMode = OPERM_INDIV;
            this.canFlexMode = false;
        } else {//flex
            this.operMode = OPERM_MAP;
            this.canFlexMode = true;
        }

        try {
            String st = this.splittedName[NAMEIND_TMIN];
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"Attempting emitter temporal read (start) with " + st);
            
            long t1 = new Dtime(st).systemSeconds();
            this.nameStart = new Dtime(st);
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"\t success");
            
            String en = this.splittedName[NAMEIND_TMAX];
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"Attempting emitter temporal read (end) with " + en);
            
            long t2 = new Dtime(en).systemSeconds();
            this.nameEnd = new Dtime(en);
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"\t success");
            
            this.sysSecRanges = new long[]{t1, t2};
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"Abstract emitter: sysSec range set. " + nam);
            
        } catch (Exception e) {

        }
        //name bounds
        try {
            this.nameBounds = new Boundaries(this.splittedName, NAMEIND_LATMIN);
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"Abstract emitter: name bounds. " + this.nameBounds.toText());
            
        } catch (Exception e) {

        }
    }

    /**
     * Assess the geographic relevance of an EmitterFile with respect to the
     * modelling domain. Files that are not deemed as relevant can therefore be
     * skipped.
     *
     * @param GRIDTYPE emitter type index.
     * @param nam the file name (not the absolute path).
     * @param bexp The Boundaries for the evaluation.
     * @return a boolean value for relevance.
     */
    public static boolean hasGeoRelevance(int GRIDTYPE, String nam, Boundaries bexp) {
        String[] sp = nam.replace(SPECS[GRIDTYPE][SP_FORMAT], "").split("_");
        double latmin = Double.valueOf(sp[NAMEIND_LATMIN]);
        double latmax = Double.valueOf(sp[NAMEIND_LATMIN + 1]);
        double lonmin = Double.valueOf(sp[NAMEIND_LATMIN + 2]);
        double lonmax = Double.valueOf(sp[NAMEIND_LATMIN + 3]);
        Boundaries b = new Boundaries(latmin, latmax, lonmin, lonmax);

        if (b.intersectsOrContains(bexp) || bexp.intersectsOrContains(b)) {
                EnfuserLogger.log(Level.FINE,AbstractEmitter.class,"\tAbstractEmitter: has geoRelevance " + nam);
            
            return true;
        }
        return false;
    }

    /**
     * Parse the emitter type from filename.
     *
     * @param nam String file name.
     * @return emitter type index. Returns -1 if no proper type was found.
     */
    public static int getGridType(String nam) {

        String[] split = nam.split("_");
        String type = split[0];
        int j = -1;
        for (String[] typs : SPECS) {
            String typ = typs[SP_TYPE];
            j++;
            if (typ.equals(type) && nam.contains(typs[SP_FORMAT])) {//a match by two criteria
                return j;
            }
        }
        EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"\tAbstractEmitter: type not found! " + nam);
        return -1;
    }

    /**
     * Assess the necessity to check modelling dates for emitter files of the
     * given type.
     *
     * @param typ emitter type index
     * @return return boolean value for temporal check necessity.
     */
    private static boolean typeNeedsTemporalRelevance(int typ) {
        return SPECS[typ][SP_DATES].contains("true");
    }

    /**
     * Assess emitter file relevance with respect time. A file that is not
     * relevant in this respect can be omitted from modelling.
     *
     * @param typ emitter type index.
     * @param nam file name
     * @param start modelling start time
     * @param end modelling end time
     * @return a boolean value for relevance.
     */
    public static boolean hasTemporalRelevance(int typ, String nam, Dtime start, Dtime end) {
        boolean needsCheck = typeNeedsTemporalRelevance(typ);
        if (!needsCheck) {
            return true;//all dates are accepted, since not relevant
        }
        String[] sp = nam.replace(SPECS[typ][SP_FORMAT], "").split("_");
        Dtime dt1;
        Dtime dt2;
        try {
            dt1 = new Dtime(sp[NAMEIND_TMIN]);
            dt2 = new Dtime(sp[NAMEIND_TMAX]);
        } catch (Exception e) {
            dt1 = new Dtime(sp[NAMEIND_TMIN] + ":00:00");
            dt2 = new Dtime(sp[NAMEIND_TMAX] + ":00:00");
        }
        boolean ok = evaluateSpan(start, end, new int[]{dt1.systemHours(),dt2.systemHours()});
        if (ok) {
                EnfuserLogger.log(Level.FINE,AbstractEmitter.class,"\tAbstractEmitter: has temporal Relevance " + nam + ", "
                        + dt1.getStringDate() + " - " + dt2.getStringDate());    
        }
        return ok;
    }

    /**
     * Read all different kinds of AbstractEmitters from the regional emitter
     * directory.
     *
     * @param ops Options in FusionCore defines the directory and geo bounds for
     * emitter loading operations. Note: all sub-directories of
     * /region/emitters/gridEmitters/ are also included in the search. Emitters
     * that do not have relevance (time, spatial) will not be read.
     * @param start Start time for relevance check.
     * @param end End time for relevance check.
     * @return an Array of AbstractEmitters that were found and matched the
     * given criteria.
     */
    public static ArrayList<AbstractEmitter> readAllFromDir(FusionOptions ops, Dtime start, Dtime end) {

        try {//new feature: synch additional emitter files from AWS S3 (if Addons libraries exist)
            synchEmitterFiles(ops,start,end);//will create additional files automatically if missing.
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<AbstractEmitter> arr = new ArrayList<>();
        File f = new File(ops.getEmitterDir());
        File[] ff = f.listFiles();
        if (ff == null) {
            return arr;
        }

        //form a list of files, also considering sub-dirs
        ArrayList<File> allFiles = new ArrayList<>();
        for (File test : ff) {
            try {
                if (test.isDirectory()) {//search content inside this dir
                    if (test.getName().contains("copy")) {
                        EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"SKIPPING emitter read from: "+ test.getAbsolutePath());
                        continue;
                    }
                    File[] sub = test.listFiles();
                    if (sub != null) {
                        for (File sf : sub) {
                            if (!sf.isDirectory()) {
                                allFiles.add(sf);
                            }
                        }
                    }
                } else {//not a directory, add
                    allFiles.add(test);
                }

            } catch (Exception e) {

            }
        }//for files in dir

        Boundaries bexp = ops.boundsMAXexpanded().clone();//significantly expanded area around the modelling domain.

        for (File test : allFiles) {
            try {
                if (test.isDirectory()) {
                    continue;
                }

                String nam = test.getName();
                if (nam.contains("LTM")) {
                    continue;//not an emitter.
                }
                if (nam.contains("FLOAT_allHeights")) {
                    String rep = "SHG-DYN_steam_ship_60m";
                        EnfuserLogger.log(Level.FINE,AbstractEmitter.class,
                                "\t switching Emitter filename (FLOAT_allHeights) to " + rep);
                    
                    nam = nam.replace("FLOAT_allHeights", rep);
                }

                //check data load filter
                if (ops.readDisabled(nam)) {
                    EnfuserLogger.log(Level.INFO,AbstractEmitter.class,"====> DISABLED READ for " + test.getAbsolutePath());
                    continue;
                }

                int typ = getGridType(nam);//both the typeID and file-format must match to get something that is >= 0
                if (typ < 0) {
                    continue;
                }

                if (!nam.contains(SPECS[typ][SP_FORMAT])) {
                    EnfuserLogger.log(Level.INFO,AbstractEmitter.class,"\t not an emitter file: " + nam);
                    continue;
                }//not the actual emitter file (lightTempoMet propably)

                if (hasGeoRelevance(typ, nam, bexp)) {
                    if (hasTemporalRelevance(typ, nam, start, end)) {
                        try {
                            ArrayList<AbstractEmitter> collection = load(test, typ, ops);
                            arr.addAll(collection);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

            } catch (Exception e) {

            }
        }//for files in dir
        return arr;
    }

    
    public static void printoutEmitters(FusionCore ens) {
        EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"======Loaded emitters: (" + ens.ages.size() + ")");
        for (AbstractEmitter age : ens.ages) {
            String s = age.printout(ens.ops);
            EnfuserLogger.log(Level.FINER,AbstractEmitter.class,s);
        }
    }

    /**
     * get the operational mode for the emitter. There can be two possibilities:
     * the emitter works in "individual" mode and releases puffs. These kind of
     * emitters will NOT contribute to EmitterShortList and HashedEmissionGrids
     * - they will work through PuffPlatform. The distinction "individual" comes
     * from the idea that in puff-modelling the emission sources are dealt as
     * individuals. In Map-mode all emission sources are represented on a map
     * that is combination of many sources.
     *
     * The other mode is the "map" mode. These emission will work as roads in
     * the osmLayer do - they will be a part of EmitterShortList and
     * HashedEmissionGrids. (To complicate things a bit: these will ALSO be
     * modelled in PuffPlatform via RangedMapEmitter, but only for mid-to-long
     * range transportation.)
     *
     * Some of the emitter can be set as "flexMode". This means that in case
     * PuffModelling is set OFF, the emitter switch to map-mode, or
     * alternatively, switch from map-mode to inidividual mode.
     *
     * @param ops FusionOptions settings
     * @param puffModellingOn true in case PuffModelling is ON.
     * @return integer operatation model (map or individual)
     */
    public int operMode(FusionOptions ops, boolean puffModellingOn) {
        int mode = this.operMode;
        if (this.canFlexMode && puffModellingOn) {
            mode = OPERM_INDIV;
        }
        return mode;
    }

    public final static int REL_OK = 0;
    public final static int REL_NO_TIME = 1;
    public final static int REL_NO_BOUNDS = 2;
    public final static int REL_NO_MODE = 3;
    public final static String[] RELS = {" (ok)", " (time)", " (bounds)", " (mode)"};

    /**
     * For making a short list of relevant emission sources- does this emitter
     * have relevance with respect the time and Boundaries?
     *
     * @param dt hour of validity to be checked
     * @param dates multi-Dtime array (local time,...), as given by TZT
     * getDates(dt).
     * @param b area for check
     * @param modeMustBe requirement for emitter operational mode.
     * @param ops FusionOptions
     * @param puffModellingOn true in case PuffModelling is set ON.
     * @return index that is one of REL_OK, REL_NO_TIME, REL_NO_BOUNDS or
     * REL_NO_MODE. Only the emitter that pass REL_OK will be included in the
     * modelling.
     */
    public int hasCurrentRelevance(Dtime dt, Dtime[] dates, Boundaries b,
            int modeMustBe, FusionOptions ops, boolean puffModellingOn) {

        int mode = operMode(ops, puffModellingOn);

        if (mode != modeMustBe) {
            return REL_NO_MODE;
        }
        if (b.intersectsOrContains(this.in.bounds)) {

        } else {
            return REL_NO_BOUNDS;
        }

        boolean temporalZero = this.getStoreTemporalZero(dt, dates);
        if (temporalZero) {
            return REL_NO_TIME;
        } else {
            return REL_OK;
        }

    }
    
    public final static float UGPS_TO_KGPH = 3600f/BILLION;

    /**
     * Get the raw amount of emissions as kg per hour. This does not consider
     * LTM scaling, emission fills.
     *
     * @param dt time for analysis.
     * @param ens the core
     * @return raw emission outputs across the whole emitter.
     */
    public abstract float[] totalRawEmissionAmounts_kgPh(Dtime dt);

    public static float[][] getEmissionAmounts_gPs_CQ(Dtime dt, FusionCore ens) {
        float[][] cq = new float[ens.ops.CATS.C.length][ens.ops.VARA.Q.length];
        Boundaries b = ens.ops.bounds();
        Dtime[] dts = ens.tzt.getDates(dt);
        Met met = ens.getStoreMet(dt, (float) b.getMidLat(), (float) b.getMidLon());
        float unitConv = 1000f/3600f;//kg to g, h to s.
        ArrayList<AbstractEmitter> ages = AbstractEmitter.shortListRelevant(ens.ages, dt, dts,
                b, AbstractEmitter.OPERM_MAP, ens);

        ArrayList<AbstractEmitter> individualAges = AbstractEmitter.shortListRelevant(ens.ages, dt, dts,
                b, AbstractEmitter.OPERM_INDIV, ens);

        for (AbstractEmitter ae : individualAges) {
            if (!ages.contains(ae)) {
                ages.add(ae);
                EnfuserLogger.log(Level.FINER,AbstractEmitter.class,
                        "\t merging non-map emitter to stack for total emissions assessment: " + ae.myName);
            }
        }
        //go over the emitters
        for (AbstractEmitter age : ages) {
            EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"Computing emissions for " + age.myName);
            float[] kgPh = age.totalRawEmissionAmounts_kgPh(dt);
            int cat = age.cat;
                age.ep.fillQ(kgPh);
                
                RPstack.scaleAndAutoNOX(kgPh,ens.ops.VARA);

                for (int q : ens.ops.VARA.Q) {
                    cq[cat][q] += unitConv*kgPh[q]*age.ltm.getScaler(dt, dts, met, q);
                }//for q


        }//for ages
        return cq;
    }

    /**
     * Form a short-list of relevant emitters, using a larger list of all
     * possible emitters.
     *
     * @param all the full list of emitters to be checked.
     * @param dt time for the check
     * @param dates full Dtime[] array for the check (from TZT)
     * @param b Boundaries to the check.
     * @param modeMustBe requirement for emitter operational mode.
     * @param ens the FusionCore
     * @return a List of relevant emitters.
     */
    public static ArrayList<AbstractEmitter> shortListRelevant(
            ArrayList<AbstractEmitter> all, Dtime dt, Dtime[] dates,
            Boundaries b, int modeMustBe,  FusionCore ens) {
        
            EnfuserLogger.log(Level.FINEST,AbstractEmitter.class,
                    "AbstractEmitter: shortlist: " + dt.getStringDate() + ", " + b.toText());
        
        boolean print = GlobOptions.allowFinestLogging();    
        FusionOptions ops = ens.ops;
        boolean puffModellingOn = ens.puffModellingAvailable(dt);
        ArrayList<AbstractEmitter> arr = new ArrayList<>();
        for (AbstractEmitter age : all) {
            if (print) {
                EnfuserLogger.log(Level.FINEST,AbstractEmitter.class,"\t" + age.myName + ", operMode="
                        + OPERMODES[age.operMode(ops, puffModellingOn)] + " vs. "
                        + OPERMODES[modeMustBe] + ", " + ens.ops.CATS.CATNAMES[age.cat]);
            }

            int accept = age.hasCurrentRelevance(dt, dates, b, modeMustBe, ops, puffModellingOn);
            boolean ok = (accept == 0);
            if (ok) {
                arr.add(age);
                if (print) {
                    EnfuserLogger.log(Level.FINEST,AbstractEmitter.class,"\t\t IS relevant.");
                }
            } else {
                if (print) {
                    EnfuserLogger.log(Level.FINEST,AbstractEmitter.class,"\t\t not relevant. " + RELS[accept]);
                }
            }
        }

        return arr;
    }

    /**
     * Produce debugging printouts for LTM-emission factors and the temporal
     * relevance.
     *
     * @param ages emitters to be checked.
     * @param q pollutant spiecies index
     * @param ens FusionCore for data
     * @param start start time for printout
     * @param end end time for printout
     * @return an ArrayList of lines - data separated with ";" for
     * CSV-production.
     */
    public static ArrayList<String> checkTemporals(ArrayList<AbstractEmitter> ages,
            int q, FusionCore ens, Dtime start, Dtime end) {
        ArrayList<String> arr = new ArrayList<>();
        String header = "DATE - " + "_" + ens.ops.VAR_NAME(q) + ";WEEKDAY;";

        for (AbstractEmitter ae : ages) {
            header += "TF_VAL_" + ae.myName + ";TF_BOOLEAN;";
        }
        arr.add(header);

        Dtime current = start.clone();
        while (current.systemHours() <= end.systemHours()) {
            Dtime[] allDates = ens.tzt.getDates(current);
            String line = current.getStringDate() + ";" + Dtime.weekDayString(allDates[TZT.LOCAL], false) + ";";

            for (AbstractEmitter ae : ages) {

                TemporalFunction tf = ae.ltm.tempo;

                if (tf == null) {
                    line += ";;";
                } else {
                    float val = tf.getTemporal(current, allDates);
                    boolean precheck = ae.getStoreTemporalZero(current, allDates);
                    line += val + ";" + precheck + ";";
                }

            }//for ages
            arr.add(line);
            current.addSeconds(3600);
        }//while current

        return arr;
    }

    /**
     * Check whether this emitter is always offline with this specific time. The
     * results are stored to HashMap, so each hour will be checked only once.
     *
     * @param dt time
     * @param allDates times
     * @return boolean, true in case this emitter is always offline for all
     * pollutant species.
     */
    protected boolean getStoreTemporalZero(Dtime dt, Dtime[] allDates) {
        Boolean tr = this.isZeroForAll_h.get(dt.systemHours());
        if (tr != null) {
            return tr;
        }

        //check for non-zero temporal
            float f = this.ltm.tempo.getTemporal(dt, allDates);
            if (f > 0) {
                this.isZeroForAll_h.put(dt.systemHours(), Boolean.FALSE);
                return false;
            }
        

        //all temporals were zeros it seems
        this.isZeroForAll_h.put(dt.systemHours(), Boolean.TRUE);
        return true;
    }

    /**
     * Return the raw Q[] array, that is not processed by LTM
     *
     * @param dt
     * @param em_h
     * @param em_w
     * @return he raw Q[] array
     */
    protected abstract float[] getRawQ_µgPsm2(Dtime dt, int em_h, int em_w);

    /**
     * get the emission release height in meters for OsmLayer indexing (h,w) for
     * this emitter.
     *
     * @param loc
     * @return float emission height (m)
     */
    public abstract float getEmHeight(OsmLocTime loc);

    /**
     * For puff dispersion modelling (individuals) First array: Q, apply LTM
     * during this method. Q array needs to have the same indexing and shape as
     * Q_NAMES. Second array latlon_z_m
     *
     * @param last The exact time for emission release
     * @param secs time duration in seconds, which will multiply the emission
     * rate (µg/s)
     * @param pf the PuffPlatform for puff modelling
     * @param allDates extended Dates array from TZT
     * @param ens the FusionCore
     * @return An arrayList of raw puff emission data, which can be converted
     * into PuffCarriers.
     */
    public abstract ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs,
            PuffPlatform pf, Dtime[] allDates,FusionCore ens);

    public int injectsGiven = 0;

    /**
     * Get a list of Puff emission releases from this emitter.
     *
     * @param last the exact time for release
     * @param secs seconds, step length (scales emission amount linearly)
     * @param pf the PuffPlatform in which the dispersion of puffs is modelled
     * @param allDates all dates as given by TZT
     * @param ens FusionCore
     * @param arr an Array in which the created PuffCarriers are added to.
     * @return a list of puffCarriers.
     */
    public ArrayList<PuffCarrier> getPuffs(Dtime last, float secs, PuffPlatform pf, Dtime[] allDates,
            FusionCore ens, ArrayList<PuffCarrier> arr) {

        ArrayList<PrePuff> dats = getAllEmRatesForPuff(last, secs, pf, allDates,ens);
        if (dats == null) {
            return arr;
        }
        //emitter fill (for zero values and if enabled in variableAssociations.csv) 
        for (PrePuff pp:dats) {
             this.ep.fillQ(pp.Q);
        }
        
        float genIncr = this.ltm.puffGenIncreaseF;
        if (genIncr == 0) {
            EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"SHG Puff generation increase factor is ZERO!");
            return arr;
        }

        int distRampUp = (int) this.ltm.distanceRamp_m;

        //add puffs  
        int pufCount = 0;
        for (PrePuff  dat : dats) {

            float lat = dat.lat;
            float lon = dat.lon;

            float z_m = dat.z;
            float[] latlon = {lat, lon};
            float[] Q = dat.Q;

            float[] yx = Transforms.latlon_toMeters(lat, lon, pf.in.bounds);
            float[] hyx = {z_m, yx[0], yx[1]};

            //check that NO is taken care of
            RPstack.scaleAndAutoNOX(Q, ens.ops.VARA);
            int pcs =1;
            if (dat.canSplitToMiniPuffs) {
                pcs = pf.getPuffReleaseRateIncreaser(z_m,lat,lon, last,genIncr);//(int)pf.getPuffCreationRate(!dat.lespuff,profiledU, genIncr,true);//resolution here for point emitter is based on grid resolution
            }
            //automatic puff release count management
            
            float miniTick = (float) pf.timeTick_s / pcs;
            float Q_scaler = 1f / pcs;
            for (int k = 0; k < pcs; k++) {

                float[] Qmini = PuffCarrier.copyArr(Q, Q_scaler);

                PuffCarrier p;
                if (dat.usesMicroMetModule && pf.mm.enabled()) {
                   //customized, rare treatment 
                   p= pf.mm.createPuffInstance(ens, last,
                        DZ_EXP,  hyx, Qmini, latlon, this.myName,
                        this.cat, this.in.res_m, distRampUp);
                   
                } else {//the business as usual case.
                   p= new PuffCarrier(ens, last,
                        DZ_EXP,  hyx, Qmini, latlon, this.myName,
                        this.cat, this.in.res_m, distRampUp); 
                }
                p.canMerge = dat.canMerge;
                injectsGiven++;
                if (k > 0) {
                    p.evolve(miniTick * k, pf, last);
                }
                
                if (dat.secEvolve>0) {
                    p.evolve(dat.secEvolve, pf, last);
                    
                } else if (dat.secEvolve<0) {
                    p.activate_after_s = -dat.secEvolve;
                }
                
                if (dat.ID!=null) p.ID = dat.ID;
                arr.add(p);
                pufCount++;
            }

        }//for prepuffs
        
        EnfuserLogger.log(Level.FINER,AbstractEmitter.class,"\t added " + pufCount + " puffs from "
                    + this.origFile.getAbsolutePath() + " with a distanceRampUp of " + distRampUp + "m");
        

        return arr;

    }

    /**
     * Get a list of GaussianPlumes originating from this emitter's release
     * locations.Then the concentration contributions can be assessed for a
 certain location. Note: the operational use of this method should be
 avoided for all GRIDDED EMITTERS: this is computationally costly. This
 can be used for long time-series evaluations when puffMode is OFF and for
 a small collection of ships and power plants.
     *
     * @param dt the time
     * @param allDates more Dtimes as given by TZT
     * @param ens the FusionCore
     * @param b the Boundaries used for x-y transformations in metric coordinate
     * system.
     * @return a List of ApparentPlumes (Gaussian steady-state solution)
     */
    public ArrayList<ApparentPlume> getPlumes(Dtime dt, Dtime[] allDates,
            FusionCore ens, Boundaries b) {

        ArrayList<PrePuff> latlonz_qs = this.getAllEmRatesForPuff(dt, 1,
                null, allDates,ens);//second (1s) release rate is used
        ArrayList<ApparentPlume> arr = new ArrayList<>();
        if (latlonz_qs == null) {
            return arr;
        }

        Met mlast = null;

        for (PrePuff pr : latlonz_qs) {
            
            float lat = pr.lat;
            float lon = pr.lon;
            float h = pr.z;
            int c = this.cat;
            if (pr.hasCustomCategory()) {
                cat = pr.cat();//for Pem
            }
            Met met = ens.getStoreMet(dt, lat, lon);
            if (mlast == null || mlast != met) {//update tempoMet scalers if the Met is different
                mlast = met;
            }

            float[] Q = pr.Q;
            if (Q == null) {
                continue;//emitter is "offline"
            }
            ep.fillQ(Q);
            
            RPstack.scaleAndAutoNOX(Q,ens.ops.VARA);
            float[] yx = Transforms.latlon_toMeters(lat, lon, b);
            float[] hyx = {h, yx[0], yx[1]};
            float[] latlon = {lat, lon};

            //automatic puff release count management
            ApparentPlume ap = new ApparentPlume(ens, dt, hyx, Q, 0, 0, latlon, "", c, 0);
            arr.add(ap);

        }//for point sources   

        return arr;

    }

    /**
     * Return an array of type specific (Q_NAMES) emission release rates in
     * [µg/s/m2].
     * 
     * Before allocating emission rates, a check for fast null return
     * (all zeros) is performed.This check (and near-zero value removal) will
     * make dispersion modelling faster.
     *
     * @param loc
     * @param met meteorological conditions at the location and time
     * @param elemLen the size of float[] array to be created. This is usually
     * Q_NAMES.length and possibly +2 larger for supporting information
     *
     * @param ens
     * @return float[] emission release rates [µgm per square meter] for each
     * pollutant type (Q).
     */
    public float[] getAreaEmissionRates_µgPsm2(OsmLocTime loc, Met met,
            int elemLen, FusionCore ens) {

        loc.alignWithEmitter(this);
        // skip conditions
        if (loc.outOfBounds()) return null;
        if (loc.temporalsZero()) return null;
        float scaler = loc.cellSparserScaler();
        if (scaler<=0) return null;
        
        //ok, there's something to compute. In case cell sparsing is in place lets take the scaler.
        float[] tempq = null;//initialize only if nonzero value is encounteres

        //not singular===========================
        float[] qs = this.getRawQ_µgPsm2(loc.dt,loc.emitter_h,loc.emitter_w);
        if (qs == null) {
            return null;
        }
        
        for (int q : VARA.Q) {
            if (qs[q] == 0) {
                continue;//cannot be anything other than zero
            }
            float val = qs[q] * this.ltm.getScaler(loc, met, q);
            if (val != 0) {
                if (tempq == null) {
                    tempq = new float[elemLen];
                }
                tempq[q] = val * scaler;
            }
        }
        //emitter fill (for zero values and if enabled in variableAssociations.csv) 
        this.ep.fillQ(tempq);
        
        //cat and height for non-null emission array
        if (tempq != null) {
            tempq[VARA.ELEMI_H] = getEmHeight(loc);
            tempq[VARA.ELEMI_CAT] = this.cat;
            tempq[VARA.ELEMI_RD] = this.ltm.distanceRamp_m;
        }

        return tempq;
    }
    
    

    /**
     * get information printout for the current state of this emitter.
     *
     * @param ops options
     * @return a text output
     */
    public String printout(FusionOptions ops) {
        String line = "\n======================================\n";
        
        try {

            line += CATS.CATNAMES[cat] + " - " + AbstractEmitter.SPECS[gridType][AbstractEmitter.NAMEIND_TYPE]
                    + " - " + myName + " - ";

            if (this.origFile != null) {
                line += this.origFile.getAbsolutePath() + "\n";
            } else {
                line += "nullFile \n";
            }

            line += in.shortDesc() + "\n";
            line += "Operational mode with puffs = " + OPERMODES[this.operMode(ops, true)] + "\n";
            line += "Operational mode WITHOUT puffs = " + OPERMODES[this.operMode(ops, false)] + "\n";
            //line += ("LTM is null= " + (this.ltm==null) + "\n");
            line += this.ltm.printout();
            Float fill = this.getFillRatio();
            if (fill != null) {
                line += "Grid cells that are non-zero: " + editPrecision(fill.doubleValue(), 2) + "%\n";
            }
            line+=ep.shortDescription()+"\n";
            line += "\n======================================\n";
            return line;

        } catch (Exception e) {
           EnfuserLogger.log(e,Level.WARNING, this.getClass(),
                    "Emitter specs could not be printed! "+myName +","+this.origFile);
           
            line += ("\nError with " + this.origFile.getAbsolutePath() + " - " + myName);
            return line;
        }

    }
    
        public String printout_oneline(FusionOptions ops) {
        String line="";
        try {

            line += CATS.CATNAMES[cat] + ", " + AbstractEmitter.SPECS[gridType][AbstractEmitter.NAMEIND_TYPE]
                    + ", " + myName + ", ";

            if (this.origFile != null) {
                line += this.origFile.getAbsolutePath() + ", ";
            } else {
                line += "nullFile, ";
            }

            line += in.shortDesc() + ", ";
            Float fill = this.getFillRatio();
            if (fill != null) {
                line += "non-zero cells: " + editPrecision(fill.doubleValue(), 2) + "%";
            }
            line+=ep.shortDescription();
            line+= ",LTM="+ltm.file.getName();
            if (ltm.subs!=null) line+= " with "+ltm.subs.size() +" subs.";
            return line;

        } catch (Exception e) {
            EnfuserLogger.log(e,Level.WARNING, this.getClass(),
                    "Emitter specs could not be printed! "+myName +","+this.origFile);
            
            line += ("ERROR with " + this.origFile.getAbsolutePath() + " - " + myName);
            return line;
        }

    }

    public Float getFillRatio() {

        if (this.isZeroForAll == null) {
            return null;
        }

        float nonz = 0;
        float all = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                all++;
                if (!this.isZeroForAll[h][w]) {
                    nonz++;
                }
            }//for w
        }//for h

        return 100f * nonz / all;

    }

}
