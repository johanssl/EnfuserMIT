/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import org.fmi.aq.enfuser.customemitters.BGEspecs;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.essentials.gispack.osmreader.road.TunnelPoint;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadDefragmenter;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import org.fmi.aq.essentials.gispack.osmreader.colmap.ColorMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.LU_UNDEF;
import org.fmi.aq.essentials.gispack.osmreader.colmap.MonoColorMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_ELEVATION;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_VEGE;
import org.fmi.aq.essentials.tools.subgrid.SubIntGrid;
import org.fmi.aq.essentials.gispack.utils.Seasonals;
import static org.fmi.aq.essentials.gispack.utils.Tools.cropLongToInteger;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;

/**
 *
 * @author johanssl
 */
public class OsmLayer implements Serializable {

    /**
     * Form a unique key (single integer) using h,w index of a selected grid
     * cell pointer. This can be used e.g., for HashMapping of sparse objects.
     *
     * @param h h-index of any OsmLayer cell.
     * @param w w-index of any OsmLayer cell.
     * @return key integer
     */
    public static int getSpatialHashKey(int h, int w) {
        return h * 100000 + w;
    }

    /**
     * Transform a key integer back to h,w representation.
     *
     * @param key the key integer that was formed with getSpatialHashKey(h,w).
     * @param temp a temp array of two integers (will be added with (h,w) and
     * returned.
     * @return the modified temp int[2].
     */
    public static int[] decodeSpatialHashKey(Integer key, int[] temp) {

        int h = key / 100000;
        int w = key - h * 100000;

        temp[0] = h;
        temp[1] = w;

        return temp;
    }

    private static final long serialVersionUID = 7526472293822776147L;//Important: change this IF changes are made that breaks serialization!   
    private long randID = (long) (Math.random() * -10000000);

    public final Boundaries b;
    public final byte[][] luSurf;
    public final byte[][] luFunc;

    public SubIntGrid packed_refs = null;
    public int[][] unpacked_refs = null;
    public boolean usePackedRefs = false;

    public final String name;
    public final String creationTime;

    public final RadiusList radList_5m_100m;
    public final RadiusList radList_10m_100m;
    public final RadiusList radList_20m_200m;

    private final HashMap<Integer, RoadPiece> all_rps_int = new HashMap<>();//hash using ref numbers
    private final HashMap<Integer, Building> all_builds_int = new HashMap<>();//hash using ref numbers

    public final HashMap<Integer, TunnelPoint> tunnelPoints = new HashMap<>();

    public final HashMap<Long, String> buildTags = new HashMap<>();

    public final ArrayList<Place> places;
    public ArrayList<POI>[] POIs;

    protected HashMap<String, ByteGeoGrid> defaultBgs = new HashMap<>();
    //osmEmitters
    //public ArrayList<ByteGeoGrid> layerEmitters = new ArrayList<>();
    public HashMap<String, BGEspecs> layerEms_specs = new HashMap<>();

    public ColorMap colMap = null;
    public MonoColorMap nearInfraRedMap = null;

    public Seasonals seasonals;
    public final int resm;
    public final double dlat;
    public final double dlon;

    public final double midlat;
    public final double cos_lat;

    public final int H;
    public final int W;

    public Rotator rot;

    public boolean hasTrafficFlows = false;

    //things that may be utilized later on but has no designated purpose yet
    public HashMap<Integer, ByteGeoGrid> customGridHash = new HashMap<>();
    public HashMap<Integer, String[]> customStringHash = new HashMap<>();
    public HashMap<Integer, RoadPiece> outboundMainRoads = new HashMap<>();

    protected HashMap<Integer, Integer> regionalWeekDayConversions = new HashMap<>();
    protected HashMap<String, String> stringProperties = new HashMap<>();

    //fast search grids
    private boolean fastGetInit = false;
    private ByteGeoGrid fast_elevation = null;
    private ByteGeoGrid fast_veged = null;
    private ByteGeoGrid fast_bh = null;

    public String getStringProperty(String key) {
        if (this.stringProperties==null) return null;
        return this.stringProperties.get(key);
    }

    public Set<String> getStringPropertyKeys() {
         if (this.stringProperties==null) return null;
        return this.stringProperties.keySet();
    }
    
    public void putStringProperty(String key, String value) {
        if (this.stringProperties==null) this.stringProperties = new HashMap<>();
        this.stringProperties.put(key, value);
    }

    private void fastGetInit() {
        this.fastGetInit = true;
        this.fast_elevation = this.getByteGeo(BGN_ELEVATION);
        this.fast_veged = this.getByteGeo(BGN_VEGE);
        this.fast_bh = this.getByteGeo(Materials.BGN_BUILD_HEIGHT);
    }

    public float getFastBuildHeight_fromGrid(double lat, double lon) {
        if (!this.fastGetInit) {
            fastGetInit();
        }

        if (fast_bh == null) {
            return 0;
        }

        try {
            return (float) this.fast_bh.getValue(lat, lon);
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

        public final static int BH_RESDIV = 2;
    float getFastBuildHeight_fromGridHW(int h, int w) {
        if (!this.fastGetInit) {
            fastGetInit();
        }

        if (fast_bh == null) {
            return 0;
        }

        try {
            return (float) this.fast_bh.getValueAtIndex(h / BH_RESDIV, w / BH_RESDIV);
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

    public float getFastElevation(double lat, double lon) {
        if (!this.fastGetInit) {
            fastGetInit();
        }

        if (fast_elevation == null) {
            return 0;
        }

        try {
            return (float) this.fast_elevation.getValue(lat, lon);
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

    public float getFastVegeDepth(double lat, double lon) {
        if (!this.fastGetInit) {
            fastGetInit();
        }

        if (fast_veged == null) {
            return 0;
        }

        try {
            return (float) this.fast_veged.getValue(lat, lon);
        } catch (IndexOutOfBoundsException e) {
            return 0;
        }
    }

    private void beforeSave() {
        this.fast_elevation = null;
        this.fast_veged = null;
        this.fast_bh = null;
        this.fastGetInit = false;
    }

    public void put(String key, ByteGeoGrid bg) {
        this.defaultBgs.put(key, bg);
        this.fastGetInit = false;
    }

    public void packReferences() {
        EnfuserLogger.log(Level.FINER,this.getClass(),"Packing references...");
        this.packed_refs = new SubIntGrid(this.unpacked_refs, Dtime.getSystemDate(), b, SUBGRID_X);
        this.unpacked_refs = null;
        usePackedRefs = true;
        EnfuserLogger.log(Level.FINER,this.getClass(),"Done packing.");
    }

    public void unpackReferences() {

        this.unpacked_refs = new int[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                this.unpacked_refs[h][w] = this.packed_refs.getValueAtIndex(h, w);
            }
        }
        this.packed_refs = null;
        this.usePackedRefs = false;
        EnfuserLogger.log(Level.FINER,this.getClass(),"Reference layer has been unpacked for further modifications");
    }

    public String getIdentifierString() {
        return this.name + "_" + this.creationTime;
    }
    public static int SUBGRID_X = 10;

    public OsmLayer(ArrayList<Place> places, Boundaries bounds, int res_m, String name,
            ArrayList<POI>[] POIs, ByteGeoGrid elevation) {
        EnfuserLogger.log(Level.INFO,this.getClass(),"osmLayer - "+ name);
        this.b = bounds;
        this.name = name;
        Dtime dt = Dtime.getSystemDate_utc(false, Dtime.ALL);
        this.creationTime = dt.getStringDate();

        this.midlat = (this.b.latmax + this.b.latmin) / 2;
        this.cos_lat = Math.cos(this.midlat / 360 * (2 * Math.PI));
        this.resm = res_m;

        this.H = (int) ((this.b.latmax - this.b.latmin) * degreeInMeters / res_m);//e.g. latrange is 0.3, res =100m => 400ish 
        this.W = (int) ((this.b.lonmax - this.b.lonmin) * degreeInMeters / res_m * this.cos_lat);
        this.dlat = (this.b.latmax - this.b.latmin) / this.H;
        this.dlon = (this.b.lonmax - this.b.lonmin) / this.W;

        this.seasonals = new Seasonals(b.getMidLat());
        this.rot = new Rotator();

        this.luSurf = new byte[H][W];
        this.luFunc = new byte[H / FRD][W / FRD];
        int[][] refs = new int[H][W];
        //this.br_references = new SubIntGrid(refs, dt, b, SUBGRID_X);
        this.unpacked_refs = refs;
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                luSurf[h][w] = LU_UNDEF;
                luSurf[h / FRD][w / FRD] = LU_UNDEF;
            }
        }

        this.POIs = POIs;

        for (int i = 0; i < POIs.length; i++) {
            if (this.POIs[i] == null) {
                this.POIs[i] = new ArrayList<>();
            }
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "\tPointsOfInterest: " + POI.getName(i) + ": " + this.POIs[i].size() + " elements");
            if (!this.POIs[i].isEmpty()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\t example content:");
                EnfuserLogger.log(Level.FINER,this.getClass(),this.POIs[i].get(0).printOut());
            }
        }

        //elevation = op.elevation;
        if (elevation != null) {
            this.put(BGN_ELEVATION, elevation);
        }

        this.places = places;

        double mlat = this.b.getMidLat();
        this.radList_5m_100m = new RadiusList(this.resm, 100, mlat);
        this.radList_10m_100m = new RadiusList(10, 100, mlat);
        this.radList_20m_200m = new RadiusList(20, 200, mlat);
    }

    public static void saveToFile(String fileName, OsmLayer ol) {
        ol.beforeSave();

        EnfuserLogger.log(Level.INFO,OsmLayer.class,"Saving osmLayer to binary: " + fileName);
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fileName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(ol);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                Logger.getLogger(OsmLayer.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(OsmLayer.class.getName()).log(Level.SEVERE, null, ex);
        }

        EnfuserLogger.log(Level.FINER,OsmLayer.class,"Done writing.");
    }
    
     /**A consistency check is also done in case the osmLayer to-be-loaded
     * has some older legacy data structures in it.
     * @param ol a Loaded osmLayer.
     */
    private static void serializationConsistencyCheck(OsmLayer ol) {
        RoadPiece.reformAllFlowData(ol);
        
    }
    
    /**
     * Store a key-value String pair to the osmLayer.
     * @param key the key
     * @param value the value associated to the key.
     */
    public void addCustomStringProperty(String key, String value) {
        if (this.stringProperties ==null) this.stringProperties = new HashMap<>();
        this.stringProperties.put(key, value);
    }
    
    /**
     *Save the (modified) osmLayer to file using the same filename it was
     *originally loaded from. In case this layer has NOT been loaded from file
     *then this does nothing.
     */
    public void reSave() {
        if (this.stringProperties==null 
                ||this.stringProperties.get(LAST_LOAD_FILENAME)==null) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    "OsmLayer cannot re-save to file: " + this.name);
            return;
        }
        
        String filename = this.stringProperties.get(LAST_LOAD_FILENAME);
        EnfuserLogger.log(Level.INFO, this.getClass(),
                    "OsmLayer re-save: " + filename);
        OsmLayer.saveToFile(filename, this);
    }
    
    public final static String LAST_LOAD_FILENAME ="LastLoadFile";
    /**
     * Load an osmLayer from file.
     * A consistency check is also done in case the osmLayer to-be-loaded
     * has some older legacy data structures in it.
     * @param filename absolute path for the osmLayer to be loaded.
     * @return the read object casted as OsmLayer.
     */
    public static OsmLayer load(String filename) {
        // Read from disk using FileInputStream
        FileInputStream f_in;
        try {
            f_in = new FileInputStream(filename);

            // Read object using ObjectInputStream
            ObjectInputStream obj_in;
            try {
                obj_in = new ObjectInputStream(f_in);
                try {
                    // Read an object
                    Object obj = obj_in.readObject();
                    if (obj instanceof OsmLayer) {
                        EnfuserLogger.log(Level.INFO,OsmLayer.class,
                                "OsmLayer from .dat-file successfully.");
                        OsmLayer mp = (OsmLayer) obj;
                        serializationConsistencyCheck(mp);
                        //for potential re-save, store the load file name.
                        mp.addCustomStringProperty(LAST_LOAD_FILENAME, filename);
                        
                        return mp;
                    }
                } catch (ClassNotFoundException ex) {
                    EnfuserLogger.log(Level.WARNING,OsmLayer.class,
                  "OsmLayer casting error when loading: " + filename);
                }
            } catch (IOException ex) {
                Logger.getLogger(OsmLayer.class.getName()).log(Level.SEVERE, null, ex);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(OsmLayer.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public boolean isRoad(byte osm) {
        return Tags.isHighway(osm);
    }

    public boolean isBuilding(byte osm) {
        return Building.isBuilding(osm);
    }

    public int getRefKey(int h, int w) {
        if (this.usePackedRefs) {
            return this.packed_refs.getValueAtIndex(h, w);
        } else {
            return this.unpacked_refs[h][w];
        }
    }

    public RoadPiece getRoadPiece(int h, int w) {
        if (this.oobs(h, w)) return null;
        int key = getRefKey(h, w);
        return this.all_rps_int.get(key);
    }

    public Building getBuilding(int h, int w) {
        if (this.oobs(h, w)) return null;
        int key = getRefKey(h, w);//refs[h][w];
        return this.all_builds_int.get(key);
    }

    public byte getSurfaceType_hw(int h, int w) {
        return this.luSurf[h][w];
    }
    public final static int FRD = 3; //functional resoltution divisor

    byte getFunctionalType_hw(int h, int w) {
        return this.luSurf[h / FRD][w / FRD];
    }

    public int get_h(double lat) {
        return (int) ((b.latmax - lat) / (b.latmax - b.latmin) * this.H);
    }

    public int get_w(double lon) {
        return (int) ((lon - b.lonmin) / (b.lonmax - b.lonmin) * this.W);
    }

    public double get_lat(int h) {
        double lat = b.latmax - h * dlat;
        return lat;
    }

    public double get_lon(int w) {
        return b.lonmin + w * dlon;
    }

    public void addRoadPiece(int h, int w, RoadPiece rp, boolean forceAdd) {

        //check skip clause
        if (!forceAdd) {
            boolean skip = RoadDefragmenter.skipRoadAddtion(this, h, w, rp);
            if (skip) {
                return;
            }
        }

        int intKey = rp.ref_number_int;
        if (intKey == -1) {
            intKey = cropLongToInteger(rp.ref_number) * -1;
            rp.ref_number_int = intKey;
        }
        //this.br_references.changeValueAt(h, w, intKey);
        this.unpacked_refs[h][w] = intKey;
        this.all_rps_int.put(intKey, rp);
    }

    public HashMap<Integer, RoadPiece> allRPs() {
        return this.all_rps_int;
    }

    /**
     * Update and return the next free artificial ID number that can be used for
     * example, for added buildings that don't have an OSM id.
     *
     * @return a long that is quaranteed(?) to be unique.
     */
    public long nextArtificialID() {
        boolean unique = false;
        while (!unique) {
            this.randID--;
            int intKey = cropLongToInteger(randID);
            Building b = this.all_builds().get(intKey);
            RoadPiece rp = this.allRPs().get(intKey);
            if (b == null && rp == null) {
                unique = true;
            }
        }
        return this.randID;
    }

    public void addBuilding(Building b, int h, int w) {
        //buildings cannot be added if reference data is PACKED
        if (usePackedRefs) {
            EnfuserLogger.log(Level.FINER,this.getClass(),
            "Road/building references are packed => unpacking for modifications.");
            unpackReferences();
        }

        int intKey = b.IDint;
        if (intKey == -1) {
            intKey = cropLongToInteger(b.ID);
            b.IDint = intKey;
        }
        this.unpacked_refs[h][w] = intKey;
        this.all_builds_int.put(intKey, b);
    }

    public HashMap<Integer, RoadPiece> all_rps() {
        return this.all_rps_int;
    }

    public HashMap<Integer, Building> all_builds() {
        return this.all_builds_int;
    }

   

    /**
     * Add Generic, crude estimates for vehicular flows based on basic osmLAyer
     * properties at vicinity of roads as well as the road specs.
     *
     * @param dir root directory
     */
    public void addGenericFlows(String dir) {

        FlowEstimator.addDefaultTRVproxies(dir, this, FlowEstimator.SOURCE_PCLF);
        this.hasTrafficFlows = true;
    }

    public ByteGeoGrid getByteGeo(String key) {
        return this.defaultBgs.get(key);
    }

    public ByteGeoGrid getByteGeo_extendedSearch(String key) {

        ByteGeoGrid bg = this.getByteGeo(key);
        if (bg != null) {
            return bg;
        }

        //addtional search
        for (ByteGeoGrid bgg : customGridHash.values()) {
            String nam = bgg.varName;
            if (nam.contains(key)) {
                return bgg;
            }
        }
        return null;
    }

    public void removeByteGeo(String key) {
        this.defaultBgs.remove(key);
        this.layerEms_specs.remove(key);//this must also be done if the geo is indeed an emitter.
    }

    
    public RoadPiece getRoadPiece_intref(int ref, boolean allowReplacementSearch) {
        RoadPiece rp = this.all_rps_int.get(ref);
        
        if (rp==null && this.roadRefChanges!=null && allowReplacementSearch) {
            //attempt twice to trace back changes
            Integer nref = this.roadRefChanges.get(ref);
            if (nref!=null) {//a change exists for this key 'ref'
                rp = this.all_rps_int.get(nref);
                //first change log did not work, may be changed twice
                if (rp==null) {
                    Integer nref2 = this.roadRefChanges.get(nref);
                    if (nref2!=null) {
                        rp = this.all_rps_int.get(nref2);
                    }
                }//second
                
            }//first
        }//if change search allowed
       
        return rp;
    }
    
    private HashMap<Integer,Integer> roadRefChanges=null;
    public void replaceRoadHash(HashMap<Integer, RoadPiece> newHash,
            HashMap<Integer, RoadPiece[]> rems_old_new) {
        
        if (roadRefChanges==null) roadRefChanges =new HashMap<>();
        
        this.all_rps_int.clear();
        for (Integer key : newHash.keySet()) {
            this.all_rps_int.put(key, newHash.get(key));
        }
        
        //keep track of the lost roadPieces. These might be needed later on.
        for (RoadPiece[] old_new :rems_old_new.values()) {
            roadRefChanges.put(old_new[0].ref_number_int, old_new[1].ref_number_int);
        }
        
    }
    
    public void removeObsoleteElementKeys(HashMap<Integer,Integer> old_new_rpRefs) {
        EnfuserLogger.log(Level.FINER,this.getClass(),"Removing obsolete building and roadPiece keys...");
        HashMap<Integer, Building> actualBuilds = new HashMap<>();
        HashMap<Integer, RoadPiece> actualRPs = new HashMap<>();
        
        if (roadRefChanges==null) roadRefChanges =new HashMap<>();
        if (old_new_rpRefs!=null) {
            for (Integer old:old_new_rpRefs.keySet()) {
                roadRefChanges.put(old, old_new_rpRefs.get(old));
            }
        }
        
        for (int h = 0; h < H; h++) {
            if (h % 1000 == 0) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"H=" + h);
            }
            for (int w = 0; w < W; w++) {

                RoadPiece rp = this.getRoadPiece(h, w);
                if (rp != null) {
                    actualRPs.put(rp.ref_number_int, rp);
                }
                Building bu = this.getBuilding(h, w);
                if (bu != null) {
                    actualBuilds.put(bu.IDint, bu);
                }

            }//for w
        }//for h

        EnfuserLogger.log(Level.FINER,this.getClass(),"CrossCheck:");
        ArrayList<Integer> rp_rems = new ArrayList<>();
        for (Integer rpk : this.all_rps_int.keySet()) {//browse existing
            RoadPiece rp = actualRPs.get(rpk);//is this one in the current actual hash?
            if (rp == null) {//well, no!
                rp_rems.add(rpk);
            }
        }
        EnfuserLogger.log(Level.FINER,this.getClass(),"Removing " + rp_rems.size() + " obsolete roadPieces.");
        for (Integer key : rp_rems) {
            this.all_rps_int.remove(key);
        }

        //builds
        ArrayList<Integer> bu_rems = new ArrayList<>();
        for (Integer bk : this.all_builds_int.keySet()) {
            Building bu = actualBuilds.get(bk);
            if (bu == null) {
                bu_rems.add(bk);
            }
        }
        EnfuserLogger.log(Level.FINER,this.getClass(),"Removing " + bu_rems.size() + " obsolete Buildings.");
        for (Integer key : bu_rems) {
            this.all_builds_int.remove(key);
        }
    }

    public boolean oobs(int h, int w) {
       if (h<0 || h> H-1) return true;
       if (w<0 || w>W-1) return true;
       return false;
    }

    public byte getLU_functional(int h, int w) {
        
     int lh = h / FRD;
        if (lh>luFunc.length-1) return LU_UNDEF;
     int lw = w /FRD;
         if (lw>luFunc[0].length-1) return LU_UNDEF;
             
     return luFunc[lh][lw];
    }

    public HashMap<String,ByteGeoGrid> defaultBgs() {
        return this.defaultBgs;
    }

    public HashMap<Long, RoadPiece> getLonghashRoads() {
        HashMap<Long, RoadPiece> rps = new HashMap<>();
        for (RoadPiece rp:this.all_rps_int.values()) {
            rps.put(rp.ref_number, rp);
        }
        return rps;
    }

}
