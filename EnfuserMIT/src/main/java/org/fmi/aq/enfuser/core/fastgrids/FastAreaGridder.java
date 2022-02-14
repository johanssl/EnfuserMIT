/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.fastgrids;

import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.enfuser.customemitters.AbstractEmitter;
import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.meteorology.WindConverter;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.puff.ApparentPlume;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.receptor.RPstack;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.meteorology.WindState;

/**
 *
 * @author johanssl
 */
public class FastAreaGridder {

    private BlockBank bb;
    private final HashMap<String, HashedEmissionGrid> hegs = new HashMap<>();
    public AreaInfo in;

    public HashMap<String, OsmLayer> usableMaps = new HashMap<>();
    int olCheckMiss = 0;
    String boundsCheckString = "";
    String boundsTemporalCheckString = "";

    //for the created of hashedEmitter grids the expanded modelling domain
    //is used to define a list of relevant osmLayers
    public final float[] expanded_Lats;
    public final float[] expanded_Lons;
    private final static int COORDINATE_CHECKS = 20;

    public StaticAreaDef statics; 
    final int[] WIND_TYPES;//first is N, second is E.
    
    public FastAreaGridder(AreaInfo in, FusionCore ens) {
        this.in = in;
        Boundaries exp = ens.ops.boundsExpanded(false).clone();
        expanded_Lats = new float[COORDINATE_CHECKS];
        expanded_Lons = new float[COORDINATE_CHECKS];
        AreaNfo temp = new AreaNfo(exp, COORDINATE_CHECKS, COORDINATE_CHECKS, Dtime.getSystemDate());
        for (int hw = 0; hw < COORDINATE_CHECKS; hw++) {
            double lat = temp.getLat(hw);
            expanded_Lats[hw] = (float) lat;
            double lon = temp.getLon(hw);
            expanded_Lons[hw] = (float) lon;
        }
        
       WIND_TYPES = new int[]{ens.ops.VARA.VAR_WIND_N,
       ens.ops.VARA.VAR_WIND_E};//first is N, second is E.
    }
   
    public HashedEmissionGrid get(OsmLayer ol) {
        return this.hegs.get(ol.name);
    }
    
    public boolean hasHashedEmissionGrids(Observation o, String osmLayerName) {
        if (o.dt.systemSeconds()/60!= this.in.dt.systemSeconds()/60) return false;//time is different, not valid
        HashedEmissionGrid heg = this.hegs.get(osmLayerName);
        return (heg!=null);
    }

    private void initBasics(FusionCore ens) {
        //check String for testing for a need for re-evaluation.

        this.bb = null;//reset this as the area is different (or the first set area in which case this has no effect)
        EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"FastAreaGridder: initBasics");
        //premap osmLayers - are there multiple osmLayer that are needed? This is important for 'hegs'
        for (float lat : this.expanded_Lats) {
            for (float lon : this.expanded_Lons) {

                OsmLayer ol = ens.mapPack.getOsmLayer(lat, lon);
                if (ol != null) {
                    usableMaps.put(ol.name, ol);
                } else {
                    olCheckMiss++;
                }

            }//for w
        }//for h

        EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"There are " 
                + this.usableMaps.size() + " usable osmLayers to process.");
    }

    public void resetBlockBank() {
        this.bb = null;
    }

    public final static boolean INIT_FOR_AREAFUSION = true;
    public final static boolean LIGHT_INIT = false;

    /**
     * Prepare fast area grids for an area modelling task. For the given area
     * and resolution the following are set up if needed:
     *  - the current hashed emission grids
     *  - static mapping of local environment properties (BlockAnalysis)
     *  - The list of relevant osmLayer objects for the area
     *  - static land-use properties
     * Note: in case the modelling time or resolution has not been changed
     * one or more of these datasets may be left unmodified.
     * @param newIn the current area definition and resolution
     * @param ens the core
     * @param initForAreaFusion if true, then some AreaFusion-only datasets
     * are triggered for the assessment (BA's and static land-use)
     */
    public void checkReinit(AreaInfo newIn, 
            FusionCore ens, boolean initForAreaFusion) {
        
        String bTest = newIn.areaCheckString(false);
        String bTimeTest = newIn.areaCheckString(true);

        if (!bTest.equals(this.boundsCheckString)) {//change all since the area definition has changed.
            this.in = newIn;
            this.boundsCheckString = in.areaCheckString(false);
            EnfuserLogger.log(Level.FINER,FastAreaGridder.class,
                    "FastGridder.checkReinit: area definition has changed.");
            this.initBasics(ens);
            
        }

        //block analysis reinit?
        if (initForAreaFusion) {//also the blockbank can be assessed
            if (this.bb == null || !bTest.equals(this.boundsCheckString)) {//does not exist yet OR the area has been changed
                bb = new BlockBank(in, ens, true);//reinit
            }
            
                        //land use mapping for the AreaInfo needs also to be reset
            if (statics == null || statics.H != newIn.H || statics.W != newIn.W) {//does not exist or ha different dimensions (or different area)
                statics = new StaticAreaDef(newIn, ens.mapPack); 
            }
            
        }

        if (!bTimeTest.equals(this.boundsTemporalCheckString)) {//change the emission mapping only, the time has changed
            this.in = newIn;
            this.boundsTemporalCheckString = in.areaCheckString(true);
            ens.hashMet.forceInit(in.dt, in.bounds, ens, false);
            EnfuserLogger.log(Level.INFO,FastAreaGridder.class,
                    "FastGridder.checkReinit: hashed emission grids are "
                            + "empty or the time has changed.");
            this.initHegs(ens, in.dt);

        }
    }

    private void initHegs(FusionCore ens, Dtime dt) {
        this.hegs.clear();
        for (OsmLayer ol : this.usableMaps.values()) {
            HashedEmissionGrid heh = new HashedEmissionGrid(ol, dt, ens);
            heh.initMT(ens);
            hegs.put(ol.name, heh);
        }
    }

    public BlockAnalysis getBlock(OsmLayer ol, FusionCore ens, double lat, double lon) {
        return this.bb.getStoreBlockAnalysis(ol, ens, lat, lon);
    }

      
    public void visualizeHegDistance(FusionCore ens, double lat, double lon) {
        OsmLayer ol = ens.mapPack.getOsmLayer(lat, lon);
        if (ol==null) return;
        HashedEmissionGrid heg = this.hegs.get(ol.name);
        if (heg==null) {
             EnfuserLogger.log(Level.INFO, this.getClass(),
                    "HashedEmissionGrid (HEG) not available.");
            return;
        } 
        heg.visualizeDistanceGrid(ens); 
    }

    public int getClosestEmissionDistance(OsmLayer ol, int ol_h, int ol_w) {
        HashedEmissionGrid heg = this.hegs.get(ol.name);
        if ( heg!=null && heg.distCheck!=null) {
            return heg.distCheck.getClosestEmissionDistance(ol_h, ol_w);
        }
        return 0;
    }
    

    public static GeoGrid mapWind(boolean draw, float height_m, Dtime dt,
            FusionCore ens,  VisualOptions vops, AreaNfo in) {

        OsmLayer ol = ens.mapPack.getOsmLayer (
                in.bounds.getMidLat(), in.bounds.getMidLon());
        
        float[][] dat = new float[in.H][in.W];
        WindState ws = new WindState();
        ws.fineScaleRL = true;
        ws.geoInterpolation=true;
        
        for (int h = 0; h < in.H; h++) {
            if (h % 50 == 0) {
                EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"h=" + h);
            }
            for (int w = 0; w < in.W; w++) {
                double lat = in.getLat(h);
                double lon = in.getLon(w);
                ws.setTimeLoc((float)lat, (float)lon, dt, ens,null);
                ws.profileForHeight(height_m, ens);
                dat[h][w] =ws.U;
            }
        }

        GeoGrid g = new GeoGrid(dat, dt, in.bounds);
        if (draw) {
            if (vops==null) {
                vops = new VisualOptions();
                vops.fd_minimumWidth = 1024;
                vops.numCols = 80;
                vops.colorScheme = FigureData.COLOR_TEMPERATURE;
                vops.scaleMaxCut = 1;
                vops.scaleProgression = 1;
            
            }
            String dir = ens.ops.defaultOutDir();
            FigureData fd = new FigureData(g, vops);
            fd.pairedSize = true;//avoid this one: IllegalArgumentException: Component 1 height should be a multiple of 2 for colorspace: YUV420J
            fd.aboveScale1 = "Profiled wind speed [m/s]";
            fd.type = "profiledWindSpeed_" + dt.getStringDate_fileFriendly() + "_" + ol.name;
            //fd.drawImagetoPane();  
            fd.saveImage(FigureData.IMG_FILE_PNG, dir);
            fd.ProduceGEfiles(true, dir);
        }//if draw

        return g;
    }

    /**
     * Create a grid representation for emission densities (totals). This is
     * used for manual enfuserGUI work.
     *
     * @param cat if negative, sums all categories
     * @param q variable type index (primary, as defined in
     * VariableAssociations)
     * @param ol the osmLayer for which's area the process is done
     * @param resDiv reduces osmLayer resolution
     * @param height_range a filter based on emitter height range. Can be null,
     * If non-null, then emissions with effective height not withing the range
     * are omitted.
     * @param ens the core
     * @return geoGrid of stacked emissions in the area.
     */
    public GeoGrid mapScaledEmissionsDensities(int cat, int q, OsmLayer ol, int resDiv,
            float[] height_range, FusionCore ens) {
        HashedEmissionGrid heg = this.hegs.get(ol.name);
        float[][] dat = new float[ol.H / resDiv][ol.W / resDiv];
        float olCellResM2 = ol.resm * ol.resm;

        Dtime[] dts = ens.tzt.getDates(in.dt);

        RPstack dc = new RPstack(ens.ops);
        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w++) {

                for (int c : ens.ops.CATS.C) {
                    dc.stack_cq[c][q] = 0f;//reset all
                }

                try {

                    int r_h = h / resDiv;
                    int r_w = w / resDiv;

                    float[] compressed_emPsm2 = heg.getElement_hw_emPsm2(h, w, null);
                    if (compressed_emPsm2 != null) {
                        EmitterShortList.stackToDC(olCellResM2, dc, compressed_emPsm2, height_range,ens.ops);//em Per second /m2*singe cell area

                        if (cat >= 0) {
                            dat[r_h][r_w] += dc.stack_cq[cat][q];//add one
                        } else {
                            for (int c : ens.ops.CATS.C) {
                                dat[r_h][r_w] += dc.stack_cq[c][q];
                            }
                        }

                    }//if found

                } catch (Exception e) {

                }

            }
        }

        //points, which are not otherwise visible in this gridded map representation
        float pointRad_m = 50;
        int prad = (int) (pointRad_m / 2 / ol.resm / resDiv);
        if (prad < 1) {
            prad = 1;
        }
        int N = (2 * prad + 1) * (2 * prad + 1);
        //float pointArea_m2 = pointRad_m*pointRad_m;

        ArrayList<AbstractEmitter> ages
                = AbstractEmitter.shortListRelevant(ens.ages, in.dt, dts,
                        in.bounds, AbstractEmitter.OPERM_INDIV, ens);
        for (AbstractEmitter ae : ages) {

            if (cat >= 0 && cat != ae.cat) {
                EnfuserLogger.log(Level.FINER,FastAreaGridder.class,
                        "\t category is not correct: " + ae.myName + " - " 
                                + ens.ops.CATS.CATNAMES[ae.cat]);
                continue;
            }//not applicable category

            ArrayList<ApparentPlume> aps = ae.getPlumes(in.dt, dts, ens, in.bounds);
            for (ApparentPlume ap : aps) {

                EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"\t " + ae.myName + "," + ap.latlon_orig[0] + ", "
                        + ap.latlon_orig[1] + ", " + ap.HYX[0] + " => Q = " + ap.Q[q]);
                try {
                    int h = ol.get_h(ap.latlon_orig[0]);
                    int w = ol.get_w(ap.latlon_orig[1]);
                    int r_h = h / resDiv;
                    int r_w = w / resDiv;

                    //spread this emission content for N amount of cells
                    float inc = 0;
                    for (int i = -prad; i <= prad; i++) {
                        for (int j = -prad; j <= prad; j++) {
                            float add = ap.Q[q] / N;
                            dat[r_h + i][r_w + j] += add;
                            inc += add;

                        }//for j
                    }//for i 
                    EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"\t added " + inc + " vs. " + ap.Q[q]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }//for aps 
        }//for abst emitters

        heg.printoutDCs(ens.ops);
        GeoGrid g = new GeoGrid(dat, this.in.dt, ol.b);
        return g;
    }

    public void clearTemporaryContent() {
        this.hegs.clear();
        this.usableMaps.clear();
        this.boundsCheckString = "";
        this.boundsTemporalCheckString = "";
        this.resetBlockBank();
        System.gc();
    }

    public byte getAreaFusionLU(int h, int w) {
       return this.statics.luType[h][w];
    }

    public BlockAnalysis getStoredBA(int h, int w) {
      return this.bb.blocks[h][w];
    }

    public void forceBlockAnalysisToMatch(AreaInfo inf, FusionCore ens) {
       if (this.bb == null) {
           EnfuserLogger.log(Level.INFO,FastAreaGridder.class,"Creating new BlockBank (was null).");
           this.bb = new BlockBank(inf, ens, true);
       }
       String test = bb.in.timelessCheckString();
       String test2 = inf.timelessCheckString();
        EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"Checking "+ test +" vs "+test2+"...");
       if (!test.equals(test2)) {
           EnfuserLogger.log(Level.INFO,FastAreaGridder.class,
                   "\tThe area definition seems to have changed: create BlockBank again.");
           this.bb = new BlockBank(inf, ens, true);
       }else {
           EnfuserLogger.log(Level.FINER,FastAreaGridder.class,"\t no reset needed for Blocks.");
       }
    }

    public String printBBspecs() {
       if (this.bb== null) return " is null.";
       return this.bb.in.timelessCheckString() + ", null Elements = "+ this.bb.nullCount;
    }

}
