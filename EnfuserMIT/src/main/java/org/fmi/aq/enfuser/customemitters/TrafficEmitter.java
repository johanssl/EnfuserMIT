/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.parametrization.RESP;
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
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;


/**
 *
 * @author johanssl
 */
public class TrafficEmitter extends AbstractEmitter {

    private final OsmLayer ol;
     final FusionOptions ops;
    public TrafficEmitter(OsmLayer ol, FusionOptions ops) {
        super(ops);
       
        this.ol = ol;
        this.ops =ops;
        
        File test= buildLTM_File(ops);
        if (!test.exists()) {
            EnfuserLogger.log(Level.WARNING,ByteGeoEmitter.class,
                    "LTM does not exist: " + test.getAbsolutePath() + ", creating it now."
                            + " For road traffic emitter this should already be there!");
            this.ltm = LightTempoMet.buildDummy(1,ops, test);
        } else {
            this.ltm = LightTempoMet.create(ops,test);
        }
        
        in = new AreaInfo(ol.b, ol.H, ol.W, Dtime.getSystemDate(), new ArrayList<>());
        cat = Categories.CAT_TRAF;

        myName = "OSM_TrafficEmitter";
        gridType = AbstractEmitter.BYTEGEO_2D;
        nameBounds = ol.b.clone();
        this.height_def = 2.5;
         
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
        String name = "xprofile_LTM_osmTraffic.csv";
        return new File(ops.getEmitterDir() + name);
    }

    private RESP privateResp=null;
    private TZT private_ts;
    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
         float[] traffic_content = new float[VARA.ELEM_LEN];
        int sparser = 3;//it enough to check every ninth cell to make this faster.
        if (this.privateResp==null) this.privateResp = new RESP(ops);
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
                float scaler  = 0.5f/rp.roadWidth_m;
                float[] emQ_traffic = this.emissionLine(rp, dts, dt,this.privateResp,null);
                for (int q : VARA.Q) {
                    float f = emQ_traffic[q]*scaler;
                    if (Float.isNaN(f)) f =0;
                    traffic_content[q] += f*cellA_m2 * UGPS_TO_KGPH;
                }
            }//for w
        }//for h
        return traffic_content;
    }

        @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
    @Override
    public float[] getAreaEmissionRates_µgPsm2(OsmLocTime loc,
            Met met, int elemLen, FusionCore ens) {
        
       //note: to save some time we don't "align" the OsmLocTime. The ol_h, ol_w are already
       //in place and there will be no cellSparser methods used for this emitter
       //LTM and emissionFills must be applied still.
       if (!OSMget.isHighway(loc.osm)) return null; 
       RoadPiece rp =ol.getRoadPiece(loc.ol_h,loc.ol_w);
       
            //traffic  ===========================
            if (rp != null) {
                loc.alignWithEmitter(this);
                //if the road is wide, then the emissions are spread thinner.
                //normalize with 5m pixel width. Wider road => lower emission density since more pixels.
                //original units: g/km/vehicle. Target: ug per m2 per second.
                //At 80km/h the vehicle travels  some 22m per second. This is 0.02 of that 1km.
                //TODO what am I missing?
                float scaler  = 0.5f/rp.roadWidth_m;
                //also note: emission factors evolve via data fusion, so these are for setting the baseline and the absolute value will not be definitive.
                
                float[] traffic_content = new float[VARA.ELEM_LEN];
                float[] emQ_traffic = this.emissionLine(rp, loc.allDates, loc.dt,ens.roadEmsSpeedProfiles,ens);
                for (int q : VARA.Q) {
                    traffic_content[q] = emQ_traffic[q]*scaler;
                    traffic_content[q]*=this.ltm.getScaler(loc, met, q);
                    
                    //NaN check (OSM roads, especially the width information can be weird)
                    if (Float.isNaN(traffic_content[q])) traffic_content[q] =0;
                }
                //note: emitter proxy fillQ is not used for traffic.
                //HEIGHT AND CAT
                traffic_content[VARA.ELEMI_CAT] = cat;
                traffic_content[VARA.ELEMI_H] = (float)height_def;//fast default => use negative
                traffic_content[VARA.ELEMI_RD] = Math.min(10,ltm.distanceRamp_m);//nearby sources the modelling peak is softened
                
                this.ep.fillQ(traffic_content);
                return traffic_content;
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
        //this emitter will never be used to releae puffs directly. Rather,
        //RangedMapEmitter aggregates the information into larger emitter cells, which in turn are used for puff releaes.
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public float[] emissionLine(RoadPiece rp, Dtime[] dates, Dtime dt, RESP resp, FusionCore ens) {
    
        float dynFlowFactor = ops.getFlowAdjustmentFactor(dt);
        float[] flows = rp.getHourlyFlows(dynFlowFactor, new float[FlowEstimator.VC_INDS.length],
                dates[TZT.LOCAL], dates[TZT.NEXT_LOCAL], dt.min);
 
        //modelling test without ANY hourly variation for flow profiles?
        //NOTE: NEVER HAVE THIS ENABLED IN OPERATIONAL MODELLING
        if (DATAFUSION_TESTING)  flows = rp.getHourlyFlows(1f, new float[FlowEstimator.VC_INDS.length],DF_TEST, DF_TEST, 0);
        
        float speed = rp.speedLimit;
        //speed can change however
        speed = ens.feeder.modifyFlowSpeedBasedOnCongestion(speed,rp,dt);
        return resp.convertFlowsIntoEmissions(flows, rp, speed, null);    
    }


private final static Dtime DF_TEST = new Dtime("2021-01-14T12:00:00");//just a friday at 12:00 local time.
private static boolean DATAFUSION_TESTING = false;//if true, then traffic emission model is severly crippled - on purpose! 
public static void toggleDFtesting(boolean b) {
    DATAFUSION_TESTING = b;
    if (b) {
        EnfuserLogger.log(Level.WARNING, "DATA FUSION TESTING HAS BEEN ENABLED for traffic flow profiles!");
    }
}
}
