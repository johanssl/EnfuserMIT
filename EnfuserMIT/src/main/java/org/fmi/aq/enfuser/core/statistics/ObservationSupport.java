/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import java.util.ArrayList;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.receptor.Blocker;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.core.receptor.PlumeBender;
import org.fmi.aq.enfuser.core.receptor.PredictionPrep;
import org.fmi.aq.enfuser.mining.feed.roadweather.Maintenance;
import org.fmi.aq.enfuser.mining.feed.roadweather.RoadCond;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;


/**
 *
 * @author johanssl
 */
public class ObservationSupport {

    public final Observation o;
    public final Met met;
    public final float exp;

    public final float[] exps;
    public float[] vehics;
    public RoadCond rc;
    public final Dtime[] dts;

    public final Float modBG;
    public final float exp_forc;

    public final int flowData;
    public int congestion = 0;
    public String closest_roadWeather_ref = null;
    final RP env;
    final BlockAnalysis ba;
    Blocker reds;
    RoadPiece rp;
    final OsmLayer ol;
    int wdir =0;
    final FusionOptions ops;
    public ArrayList<Met> previous = new ArrayList<>();
    public float[] maintenance;
    
    public ObservationSupport(Observation o, FusionCore ens, Met met, float[] exps) {

        this.o = o;
        this.ops = ens.ops;
        if (met == null) {
            this.met = ens.getStoreMet(o.dt, o.lat, o.lon);
        } else {
            this.met = met;
        }
        Float wr  = met.getWdir();
        if (wr!=null) {
            wdir =wr.intValue();
        }
        if (exps == null) {
            exps = o.incorporatedEnv.getExpectances(o.typeInt, ens);
        }
        this.exps = exps;
        float sum = 0;
        for (float f : exps) {
            sum += f;
        }
        this.exp = sum;
        this.dts = ens.tzt.getDates(o.dt);
        ol = ens.mapPack.getOsmLayer(o.lat, o.lon);
        
        modBG = ens.getModelledBG(o.typeInt, o.dt, o.lat, o.lon, false);;//false for non-orthogonal anthropogenic reduction
        exp_forc = PredictionPrep.getForecastTestExp(o,ens, o.typeInt);//o.getStoreFusionValue_forecasted_noMT(ens, ae,false, FORECAST_HOURS);

        flowData = (int) ens.getTrafficFlowData((double) o.lat, (double) o.lon, o.dt);

        this.closest_roadWeather_ref = ens.getStoreClosest_WSref(o);

        if (ens.roadWeather!=null) {
            if (closest_roadWeather_ref != null) {
                rc = ens.roadWeather.getRoadCond(closest_roadWeather_ref, o.dt.systemHours());
            }
            if (rc == null) {//data not available with this closest referenceID, find another 
                rc = ens.roadWeather.getClosestHourlyRC(o.dt, o.lat, o.lon);
            }
        }
        
 
        env = o.incorporatedEnv;
        vehics = new float[FlowEstimator.VEHIC_CLASS_NAMES.length];

        if (env !=null) {
            ba = ens.getBlockAnalysis(ol, o);
            if (ba!=null) {
                if (ba.closestRoad != null) {
                    this.rp = ba.closestRoad;
                    vehics = ba.closestRoad.getHourlyFlows( null,
                            dts[TZT.LOCAL], dts[TZT.LOCAL], 0);

                   float dynFlowFactor = ens.ops.getFlowAdjustmentFactor(o.dt);
                   for (int c:FlowEstimator.VC_INDS) {
                       vehics[c]*= dynFlowFactor;
                   }

                } 
                reds = new Blocker();
                int sector = BlockAnalysis.getIndex(wdir);
                reds.set(env.observationElevation, ba, o.dt, ops, sector, 50);
                reds.setElevationDifferenceReduction(env.relativeGroundElevation, 0,0,ops);
            }//if ba
                
        } else {
            ba = null;
        }
        
        //met history
        long secs = o.dt.systemSeconds();
        for (int h =8;h<25;h+=8) {
            Met m = ens.hashMet.getIfExists(secs - h*3600, o.lat, o.lon);
            if (m!=null) this.previous.add(m);
        }
        
        if (ens.roadMaintenance!=null) {
           this.maintenance = ens.roadMaintenance.getDailyTotals(o.dt);
        }
        
    }

    String hardBlock() {
       if (this.reds!=null) {
         return (int)(100*reds.bbf) +"";
       } else {
           return "0";
       }
        
    }

    String naturalBlock() {
       if (this.reds!=null) {
           return (int)(reds.vege_coarseF*100) +"";
       } else {
           return "0";
       }
    }

    String mirroring() {
       if (this.ba!=null) {
           PlumeBender pb = new PlumeBender();
           pb.setWindReversals(ba, wdir, 0, ops);
           float f =pb.getStrength(true);
           return (int)(f*100) +"";
       } else {
           return "0";
       }
    }

    String eleBlock() {
       if (this.reds!=null) {    
           return (int)((1f-reds.eleRed)*100) +"";
       } else {
           return "0";
       }
    }

    String studdedFrac() {
     if (ol!=null) {
        int week = o.dt.hoursSinceThisYear / 24 / 7;
        return (int)(100*ol.seasonals.tireType_studded_weekly[week]) +"";
     }
     return "0";
    }

    String dominantRoadRef() {
       if (this.rp!=null) {
           return rp.ref_number_int+"";
       } else {
           return "0";
       }
    }

    String getMetAverage(int ind) {
       float f =0;
       if (previous.isEmpty()) return "0";
        
       for (Met m:this.previous) {
           f+= m.getSafe(ind);
       }
       
      f/=previous.size();
      return editPrecision(f,2)+"";
    }

    String dustSanding() {
     /* 
    MAINT_SANDING = 0;
    MAINT_SALTING = 1;
    MAINT_DBIND = 2;
    MAINT_WASH = 3;
    MAINT_BRUSHING = 4;
    MAINT_DREM = 5;
       */
     if (this.maintenance==null) return "0";
     float f = this.maintenance[Maintenance.MAINT_SANDING] 
             + this.maintenance[Maintenance.MAINT_SALTING];
     return (int) f +"";
     
    }

    String dustBinding() {
    if (this.maintenance==null) return "0";    
    float f = this.maintenance[Maintenance.MAINT_DBIND] 
             + this.maintenance[Maintenance.MAINT_WASH];
     return (int) f +"";
    }

    String dustBrushing() {
     if (this.maintenance==null) return "0";   
     float f = this.maintenance[Maintenance.MAINT_BRUSHING] 
             + this.maintenance[Maintenance.MAINT_DREM];
     return (int) f +"";
    }

}
