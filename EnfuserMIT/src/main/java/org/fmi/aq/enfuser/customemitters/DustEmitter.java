/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import java.io.File;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import java.util.ArrayList;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.customemitters.AbstractEmitter.OPERM_MAP;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_BUS;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_CAR;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_HEAVY;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.IND_LIGHT;


/**
 *
 * @author johanssl
 */
public class DustEmitter extends AbstractEmitter {

    private final OsmLayer ol;
    final FusionOptions ops;
    public DustEmitter(OsmLayer ol, FusionOptions ops) {
        super(ops);
        this.ops = ops;
        this.height_def = 2.5;
        this.ol = ol;
        
        File test= buildLTM_File(ops);
        if (!test.exists()) {
            EnfuserLogger.log(Level.WARNING,ByteGeoEmitter.class,
                    "LTM does not exist: " + test.getAbsolutePath() + ", creating it now."
                            + " For road dust emitter this should already be there!");
            this.ltm = LightTempoMet.buildDummy(1,ops, test);
        } else {
            this.ltm = LightTempoMet.create(ops,test);
        } 
        
        in = new AreaInfo(ol.b, ol.H, ol.W, Dtime.getSystemDate(), new ArrayList<>());
        cat = Categories.CAT_RESUSP;

        myName = "OSM_DustEmitter";
        gridType = AbstractEmitter.BYTEGEO_2D;
        nameBounds = ol.b.clone();

        canFlexMode = false;
        operMode = OPERM_MAP;
        //the OSMemitter cannot flex to puff source, it is fixed to operate as a map source.
        puffDistRamp_m = 0;
        puffGenIncr = 1;
        
        if (this.ltm.hasNormalizingSum()) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    myName +": LTM has a normalizing sum defined but this feature"
                            + " has been reserved only for annual-totals -type of gridded emitters!");
        }
        
    }
    
        private File buildLTM_File(FusionOptions ops) {
        String name = "xprofile_LTM_osmDust.csv";
        return new File(ops.getEmitterDir() + name);
    }


    private TZT private_ts;
    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
         float[] traffic_content = new float[VARA.ELEM_LEN];
        int sparser = 5;//it enough to check every ninth cell to make this faster.
        if (this.private_ts==null) this.private_ts = new TZT(ops);
        Dtime[] dts = this.private_ts.getDates(dt);

        float cellA_m2 = (float) (ol.resm * ol.resm) * sparser * sparser;//the absolute value does not matter here and default value for osmLAyer resm is 5
        //In case the osmLayer has DIFFERENT resolution than 5m, then this must be taken into account like this.
        
        for (int h = 0; h < ol.H; h += sparser) {
            for (int w = 0; w < ol.W; w += sparser) {
                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp == null) {
                    continue;
                }
                float scaler = 1f/rp.roadWidth_m;
                float dust = this.getRoadDust(rp, dt, dts);
                
                float f = dust*scaler;
                //NaN check (OSM roads, especially the width information can be weird)
                if (Float.isNaN(f)) f =0;
                
                traffic_content[VARA.VAR_COARSE_PM] += f * cellA_m2 * UGPS_TO_KGPH;
            }//for w
        }//for h
        return traffic_content;
    }

    @Override
    public float[] getAreaEmissionRates_µgPsm2(OsmLocTime loc,
            Met met, int elemLen, FusionCore ens) {
        
       //note: to save some time we don't "align" the OsmLocTime. The ol_h, ol_w are already
       //in place and there will be no cellSparser methods used for this emitter
       //LTM must be applied still.
        RoadPiece rp = ol.getRoadPiece(loc.ol_h, loc.ol_w); 
            //traffic  ===========================
            if (rp != null) { 
                loc.alignWithEmitter(this);
                float scaler = 1f/rp.roadWidth_m;
                //there will be no unit considerations for this estimate. It's empirical

                //also, add a singular dust element
                float[] dust_content = new float[VARA.ELEM_LEN];
                dust_content[VARA.ELEMI_CAT] = cat;
                dust_content[VARA.ELEMI_H] = (float)height_def;//fast default => use negative
                dust_content[VARA.ELEMI_RD] = Math.min(12,ltm.distanceRamp_m);;//nearby sources the modelling peak is softened
                float dust = this.getRoadDust(rp, loc.dt, loc.allDates);
                
                dust*=this.ltm.getScaler(loc, met, VARA.VAR_COARSE_PM);
                dust_content[VARA.VAR_COARSE_PM] = dust *scaler;

                //NaN check (OSM roads, especially the width information can be weird)
                if (Float.isNaN(dust_content[VARA.VAR_COARSE_PM])) dust_content[VARA.VAR_COARSE_PM] =0;

               return dust_content;
            }


        return null;
    }

    @Override
    public float getEmHeight(OsmLocTime loc) {
        return (float) this.height_def;
    }

    @Override
    public ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs,
            PuffPlatform pf, Dtime[] allDates,
            FusionCore ens) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


    private float getRoadDust(RoadPiece rp, Dtime dt, Dtime[] dates) {
        
        float dynFlowFactor = ops.getFlowAdjustmentFactor(dt);
        //get flows
        float[] flows = rp.getHourlyFlows(dynFlowFactor, new float[FlowEstimator.VC_INDS.length],
                dates[TZT.LOCAL], dates[TZT.NEXT_LOCAL], dt.min);
        
        float[] flows_prev =rp.getHourlyFlows(dynFlowFactor, new float[FlowEstimator.VC_INDS.length],
                dates[TZT.PREV2H_LOCAL], null, 0);

        //wear factors
        float[] wearFactors = getRoadErosionFactors(dt);
        float total=0;
        for (int i = 0; i < flows.length; i++) {
            total += (flows[i]+ flows_prev[i])/2 * wearFactors[i];
        }

        total = (float) Math.pow(total, 0.6);

        float surfaceType_mod =1f;
        if (rp.surface == RoadPiece.SURFACE_COBBLE) {
            surfaceType_mod = 1f;
        } else if (rp.surface == RoadPiece.SURFACE_DIRT) {
            surfaceType_mod = 2.5f;
        }
        total*=surfaceType_mod;
        
        //TODO "dust plate" x wind -addition that is not traffic dependent?
        return total;
               
    }
    
    private float[] getRoadErosionFactors(Dtime dt) {
        float[] facts = new float[FlowEstimator.VEHIC_CLASS_NAMES.length];
        facts[IND_CAR] = 0.04f;
        facts[IND_BUS] = 0.13f;
        facts[IND_HEAVY] = 0.2f;
        facts[IND_LIGHT] = 0.01f;

        //then some studded tiers
        int week = dt.hoursSinceThisYear / 24 / 7;
        float studFactor = ol.seasonals.tireType_studded_weekly[week];
        facts[IND_CAR] += studFactor * 0.3f;

        return facts;
    }

    @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    
}
