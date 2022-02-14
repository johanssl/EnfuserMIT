/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.core.gaussian.fpm.SimpleAreaCell;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.core.receptor.EnvProfiler;
import static org.fmi.aq.enfuser.core.receptor.EnvProfiler.safetyMarginBounds;
import static org.fmi.aq.enfuser.datapack.main.TZT.LOCAL;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;

/**
 * This class represent a single emitter cell of a RangedMapEmitter.
 * It can be initiated with profileEmissions() that uses the EmitterShort list.
 * 
 * The main purpose is to hold emission release rates as in ug/s for different
 * types and categories, but to do this with separate release heights for 
 * puff creation.
 * @author johanssl
 */
public class MapEmitterCell {
  
     public final float discardThresh = 0.3f;
     public int maximumContribution_percent =0;
     private final FusionOptions ops;
     
     public float[][] rates_cq;//emission release rates as in [ug per second]
     public float[] rates_q;//emission release rates as in [ug per second] without category distinction
     private byte[][] contribs_cq;//category contributions in percent [0,100]
     private int singleCat =-1;
     
     public float[][] rates_cq_elevated;//emission release rates as in [ug per second]
     public float[] rates_q_elevated;//emission release rates as in [ug per second] without category distinction
     private byte[][] contribs_cq_elevated;//category contributions in percent [0,100
     private int singleCat_elev =-1;
     
     final boolean groundedRME;// if true the elevated additional layer will not be used at all.  
     private boolean hasContent=false;//if true at least some non-zero addition was made
     String nanWarning =null;//if non-null at least one NaN was added. This shows which type and category.
     private final static int ELEVATED_M = 100;
     private final static int DEFAULT_M = 10;
     
     
    public MapEmitterCell(FusionOptions ops) {
         rates_cq = new float[ops.CATS.CATNAMES.length][ops.VARA.Q_NAMES.length];
         this.rates_q = new float[ops.VARA.Q_NAMES.length];
         
         this.groundedRME = !ops.perfOps.elevatedRME();
         this.ops = ops;    
    }

    public void addRates(EmitterShortList emlist, float cellA_m2) {
        
        for (float[] elem : emlist.currEmitters_µgPsm2()) {
            
           int CAT = (int) elem[ops.VARA.ELEMI_CAT];
           int height = (int) elem[ops.VARA.ELEMI_H];
           float[][] dat = this.getRateContainer(height);
           float[] totals = this.getRateContainer_allCats(height);
            for (int q : ops.VARA.Q) {
                float add = elem[q] * cellA_m2;
                if (Float.isNaN(add)) {
                    nanWarning = ops.VARA.VAR_NAME(q)+"," + ops.CATS.CATNAMES[CAT];
                    add=0;
                }
                if (add>0) hasContent=true;
                dat[CAT][q] += add;
                totals[q]+=add;
                
            }//for q
        }//for full elements
    }
    
    public boolean hasElevated() {
        return this.rates_cq_elevated!=null;
    }
    
    private float[][] getRateContainer(int height) {
    
    if (this.groundedRME || height < ELEVATED_M/2) return this.rates_cq;
    if (this.rates_cq_elevated==null)  {
        rates_cq_elevated = new float[ops.CATS.CATNAMES.length][ops.VARA.Q_NAMES.length];
        this.rates_q_elevated = new float[ops.VARA.Q_NAMES.length];
    }
    return this.rates_cq_elevated;
    
    }
    
    private float[] getRateContainer_allCats(int height) {
        if (this.groundedRME || height < ELEVATED_M/2) return this.rates_q;
        return this.rates_q_elevated;
    }
    
    /**
     * For a given area that is a collection of assessment pixels, combine all
     * emission releases found from the area and return a MapEmitterCell
     * that holds this information.
     * @param cells the area definition as a list of smaller areas that
     * are used to check emission content. 
     * @param ens the core
     * @param lat latitude for center
     * @param lon longitude for center
     * @param dt time
     * @param dates all Dtimes
     * @return a MapEmitterCell instance
     */
    public static MapEmitterCell profileEmissions(ArrayList<SimpleAreaCell> cells, FusionCore ens,
            float lat, float lon, Dtime dt, Dtime[] dates) {
        Met met = ens.getStoreMet(dt, lat, lon);
        boolean print = GlobOptions.allowFinestLogging(); 
        EmitterShortList emTemp = new EmitterShortList(dt, dates, ens, safetyMarginBounds(lat, lon));
        if (print) {
            EnfuserLogger.log(Level.FINEST,EnvProfiler.class,"GridEmitters: " + emTemp.ages.size() + "grids listed for this location and time");
            EnfuserLogger.log(Level.FINEST,EnvProfiler.class,"EnvProfiler: " + dt.getStringDate());
            EnfuserLogger.log(Level.FINEST,EnvProfiler.class,"\t local: " + dates[LOCAL].getStringDate());
        }

        MapEmitterCell mec = new MapEmitterCell(ens.ops);
        //pre-fetch the most important data layers
        OsmLayer osmMask = ens.mapPack.getOsmLayer(lat, lon);// must have some mapping
        if (osmMask == null) {
            return mec;
        }

        //TEMP VARS==============
        int h, w;
        float currLat;
        float currLon;
        //iterate over cells
        for (SimpleAreaCell e : cells) {//THE MAIN LOOP=====================

            currLat = e.currentLat(lat, 0, ens.ops);//lat + (float)(hw_m[0]/degreeInMeters);
            currLon = e.currentLon(lat, lon, 0, ens.ops);//lon + (float)(hw_m[1]/degreeInMeters/cos_midlat);
            //STEP 1 complete. now the coordinates along with rotation have been FIXED.
            if ((e.x_m() > 0 || e.y_m() > 0) && currLat == lat && currLon == lon) {
                EnfuserLogger.log(Level.FINER,EnvProfiler.class,"PuffEmissionEnv: coordinates do not seem to update!");
            }

            //========STEP 3: Check the availability of OsmLayer and then check whether or not this loc needs to be processed (something to add) 
            //Important: locations with nothing to add will be skipped, if boolean print is false.
            if (osmMask == null || !osmMask.b.intersectsOrContains(currLat, currLon)) {
                osmMask = ens.mapPack.getOsmLayer(currLat, currLon);
            }
            h = osmMask.get_h(currLat);
            w = osmMask.get_w(currLon);
            emTemp.listEmissionDensities(met, currLat, currLon, osmMask, h, w,ens.ops);
            mec.addRates(emTemp, e.cellArea_m2);

        } //cells

        return mec;
    }
    
    public int[] getHeightsWithData() {
        if (this.rates_cq_elevated==null) {
            return new int[]{DEFAULT_M};
        } else {
            return new int[]{DEFAULT_M,ELEVATED_M};
        }
    }

    boolean hasContent() {
       return this.hasContent;
    }

    float typeRates_allElevations(int q) {
        float sum =0;
        if (this.rates_q!=null) sum+= this.rates_q[q];
        if (this.rates_q_elevated!=null) sum+= this.rates_q_elevated[q];
        return sum;
    }

    /**
     * For the given height layer, get the category contribution representation
     * in percents. 
     * This is needed for puffs that do not carry emission masses while holding
     * the full Category-Type split information.
     * @param hm height (m).
     * @return 
     */
    byte[][] getContributions(int hm) {
        
        if (groundedRME || hm < ELEVATED_M/2) {//ground level
            if (this.contribs_cq==null) {
                this.contribs_cq = RP.expsToContributionPercents(rates_cq, ops);
                this.singleCat = hasSingleCategory(contribs_cq);
            }
            return this.contribs_cq;
            
            
        } else {//elevated
            if (this.contribs_cq_elevated==null) {
                this.contribs_cq_elevated = RP.expsToContributionPercents(rates_cq_elevated, ops);
                this.singleCat_elev = hasSingleCategory(contribs_cq_elevated);
            }
            return this.contribs_cq_elevated;
        }
   
    }
    
    public int getSingleCategory(int hm) {
         if (groundedRME || hm < ELEVATED_M/2) {
            return this.singleCat; 
         } else {
             return this.singleCat_elev;
         }
    }
    
    private int hasSingleCategory( byte[][] contribs_cq) {
        
        int nonZ_first =-1;
        for (int c:ops.CATS.C) {
            for (int q:ops.VARA.Q) {
               
                byte cont = contribs_cq[c][q]; // 0 to 100
                if (cont > 0 && nonZ_first ==-1) {//some contribution found for this cateory, this is the first one.
                    nonZ_first = c;
                } 
                
                if (cont > 0 && c!= nonZ_first) {//non-zero contribution but the category is different than the first encountered
                    return -1;//by definition there are several source categories represented here.
                }
                
            }//for q
        }//for c
        return nonZ_first;
    }
    
    /**
     * With the the given time interval, get the emission release masses based
     * on the rates held by this instance.
     * @param hm to define height index for which this computation is done.
     * @param timeTick_s time interval used to scale the rates.
     * @return float[] emission masses, indexing based on variableAssocations
     * primary types.
     */
    float[] getEmissionMasses(int hm, float timeTick_s) {
      
       float[] typeTotals = this.getRateContainer_allCats(hm);
       if (typeTotals==null) return null;
        
       float[] Q = new float[ops.VARA.Q_NAMES.length];
     
        //copy content and scale by time tick 
        boolean zero = true;
        for (int q:ops.VARA.Q) {
            Q[q] = typeTotals[q] * timeTick_s;
            if (Q[q] > 0) {
                zero = false;
            }
        }
        if (zero) {
            return null;
        }
        return Q;
    }

    int h=-1;
    int w=-1;
    AreaInfo in = null;
    void setLocation(int h, int w, AreaInfo in) {
       this.h = h;
       this.w = w;
       this.in = in;
    }

    void initContributionPercentages(float[] rateAverages) {
        for (int q : ops.VARA.Q) {
            if (q == ops.VARA.VAR_O3) {
                continue;//not needed, these can be negative and NOX emissions correlate with these anyways.
            }
            if (rateAverages[q]==0) continue;

            int percentage = (int)(100*this.typeRates_allElevations(q)/rateAverages[q]);
            if (percentage > this.maximumContribution_percent) {
                this.maximumContribution_percent = percentage;
            }

        }//for q
    }

    boolean removableContribution() {
        return this.maximumContribution_percent < this.discardThresh;
    }
    
    
}
