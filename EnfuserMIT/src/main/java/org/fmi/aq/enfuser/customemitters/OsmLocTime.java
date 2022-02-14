/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.LU_UNDEF;

/**
 * This class is used to specify a point in time and space, with respect
 * an AbstractEmitter. The use case for it is to handle indexing
 * projections (h,w) but also manage skip conditions (OOB, zero emission factor) 
 * and scaling features (e.g. OSM-rules, or cellSparser).
 * 
 * 
 * @author johanssl
 */
public class OsmLocTime {
   
    public float lat;
    public float lon;
    public Dtime dt;
    public Dtime[] allDates;
    
    public byte osm;
    public byte ofunc;
    public int ol_h;
    public int ol_w;
    
     int emitter_h=-1;
     int emitter_w=-1;
     private AbstractEmitter em=null;
     
    public OsmLocTime() {
        
    }

    /**
     * Fix the dynamic content (h.w) for the emitter.
     * @param aThis 
     */
    void alignWithEmitter(AbstractEmitter aThis) {
        this.em =aThis;
        this.emitter_h = em.in.getH(lat);
        this.emitter_w = em.in.getW(lon);
    }
 

    
        /**
     * Use the OsmLayer spatial indexing to check whether this particular
     * location for this emitter is always releasing zero-emissions. If so, it
     * can speed up the processing by ignoring the emitter.
     *
     * @return boolean, true in case the emitter is always zero for this
     * location. This includes h,w values that results in OutOfBounds.
     */
     boolean temporalsZero() {
        if (em.isZeroForAll != null && em.isZeroForAll[emitter_h][emitter_w]) {
            return true;
        }
        return em.getStoreTemporalZero(dt, allDates);

    }
    
     /**
     * After alignment, this return true if the location is actually outside
     * of the boundaries of the emitter it was aligned with.
     **/ 
    boolean outOfBounds() {
      return em.in.oobs(emitter_h, emitter_w);
    }

     /**
     * After alignment, this return a scaling value to multiply emission rates.
     * If 1, then emission assessment should occur normally. If lower than 0
     * the emission release rate estimation can be skipped.
     * 
     * Note: the idea behind cellSparser skips is to drastically reduce the amount
     * of area emission sources being modelled. With a sparsing factor of 3 one
     * can have 1/9 fraction of the initial emission sources. This of course
     * means that once every 9 checked cells the rate will be multiplied by 9
     * to get a reasonably similar outcome.
     * @return 
     */
    float cellSparserScaler() {
        if (em.ltm.emissionCellSparser > 1) {
            if (ol_h % em.ltm.emissionCellSparser != 0 || ol_w % em.ltm.emissionCellSparser != 0) {
                return -1;
            }
            return em.ltm.emissionCellSparser * em.ltm.emissionCellSparser;
        }
        return 1;
    }

    /**
     * Set the location and time.
     * @param osmMask
     * @param currLat
     * @param currLon
     * @param ol_h
     * @param ol_w
     * @param dt
     * @param dates 
     */
    void set(OsmLayer osmMask, float currLat, float currLon,
            int ol_h, int ol_w, Dtime dt, Dtime[] dates) {
       this.lat = currLat;
       this.lon = currLon;
       this.ol_h = ol_h;
       this.ol_w = ol_w;
       this.dt = dt;
       this.allDates = dates;
       
       osm = LU_UNDEF;
       ofunc = LU_UNDEF;
        if (osmMask != null && !osmMask.oobs(ol_h, ol_w)) {
            osm = osmMask.luSurf[ol_h][ol_w];
            //osm = this.current_osm;
            ofunc = OSMget.getFunctionaltype_H0_UP(ol_h, ol_w, osmMask);
        }
    }
    
    
    /**
     * Apply OSM conditional rules. If no such conditionals have been defined
     * the returned value is the given value. In other cases, if the conditional
     * is not found then the given value is scaled by 'osmELseCondition'.
     *
     * @param value the value to be scaled by conditions.
     * @param osm osm byte class definition (See AllTags)
     * @param functional functional osm type (has secondary priority, osm is
     * evaluated first).
     * @return scaled value.
     */
    /*
    public float applyOSMrules(float value, byte osm, byte functional) {
        
                //filtered out?
        if (em.ltm.afilt != null) {
            boolean isIn = em.ltm.afilt.isIncluded_roughHash(lat, lon);
            if (!isIn) {
                return 0f;
            }
        }
        
        if (!em.ltm.hasOSMconditions) {
            return value;
        } else {

            Double scaler = em.ltm.osmRuleSets.get(osm);
            if (scaler == null) {
                scaler = em.ltm.osmRuleSets.get(functional);
            }
            if (scaler == null) {//apply else condition
                return (float) em.ltm.osmElseCondition * value;
            } else {
                return value * scaler.floatValue();
            }

        }
    }
  */  
}
