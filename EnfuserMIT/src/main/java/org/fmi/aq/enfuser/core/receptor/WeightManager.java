/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.meteorology.WindState;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import java.util.HashMap;
import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.core.fastgrids.HashedEmissionGrid;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;

/**
 * Easy management of z,H and gaussian weight based on observation height,
 * emission source height etc.
 *
 * @author johanssl
 */
public class WeightManager {

    public float z_g0;
    public float absolute_elev_g0;
    
    public FusionOptions ops;
    public final EnvArray enva;

    final Dtime dt;
    public Blocker reds = new Blocker();
    WindState windState;
    
    float genW;
    int mixingAdd_m = 0;
    
    private final HashMap<Integer,float[]> elevatedWeights= new HashMap<>();
    //temporary
    private OsmLayer ol;
    private int updateCounter=0;
    public float currLat;
    public float currLon;
    private int ol_h;
    private int ol_w;
    private float current_absoluteElev=0;
    private float current_eleDiff=0;
    RoadPiece rp;
    private FootprintCell e;

    public WeightManager(EnvArray enva, FusionCore ens,Observation ob) {

        this.ol = ens.mapPack.getOsmLayer(ob.lat, ob.lon);
        this.dt = ob.dt;
        this.enva = enva;
        if (ol==null) return;
        
        this.ops = ens.ops;
        this.z_g0 = enva.obsHeight_aboveG;
        if (ops.applyElevation) {
            absolute_elev_g0 = ol.getFastElevation(ob.lat, ob.lon);
            this.current_absoluteElev =  absolute_elev_g0;
        } else {
            absolute_elev_g0 =0;
            this.current_absoluteElev =0;
        }
        
        this.windState = new WindState();
        this.windState.geoInterpolation=true;
        this.windState.timeInterpolation=true;
        this.windState.fineScaleRL=true;//will use local scale, wind directed assessment for roughness length.
        //set smooth meteorology and roughness at origin location.
        this.windState.setTimeLoc(ob.lat, ob.lon, dt, ens, enva.ba);
        //set log-wind value.
        enva.logWind = this.windState.profileForHeight(z_g0, ens);
        
        this.currLat = ob.lat;
        this.currLon = ob.lon; 
    }
    
    private float current_wd;
    int dirIndex;
    private HashedEmissionGrid heg = null;
    boolean update_heg = false;
    public void resetLocation(float currLat, float currLon, FusionCore ens,
            FootprintCell e, float current_wd, int dirIndex) {
        
       this.elevatedWeights.clear(); 
       this.current_wd = current_wd;
       this.dirIndex = dirIndex;
       this.e = e; 
       updateCounter++;
       
       //location and osmLayer update
       this.currLat = currLat;
       this.currLon = currLon;
       if (updateCounter%10==0 ) {//check if a change in layer is needed
          this.ol = ens.mapPack.getOsmLayer(currLat, currLon);
          if (ol!=null && update_heg) this.heg =ens.fastGridder.get(ol);   
       }

        if (this.ol!=null) {
            ol_h = ol.get_h(currLat);
            ol_w = ol.get_w(currLon);
        }//if osmLayer exists for loc  
    }
    
    public boolean locationNotCovered() {
        return this.ol ==null;
    }
    
    public void prepareWeights(FusionCore ens, float genW) {
       
      this.genW = genW;
      this.windState.setSecondaryPoint(currLat, currLon, ens);
        
        //elevation at loc 
        if (ops.applyElevation) {
            this.current_absoluteElev = ol.getFastElevation(this.currLat, this.currLon);
            this.current_eleDiff= (float) this.current_absoluteElev - this.absolute_elev_g0;//e.g., target is at 100m and g0 is at 20 => this is 80
            if (e.dist_m < 30) {
                this.current_eleDiff *= e.dist_m / 30f;
            }//elevation data has coarse grid resolution so this must be taken into account to avoid artifacts
        }
        
        if (ops.blockerStrength<=0) {
            this.reds.reset();
        } else {
             reds.set(this.z_g0, enva.ba,current_wd,dt,e,ops);
        }


         int h = ol.get_h(this.currLat);
         int w = ol.get_w(this.currLon);
         rp = ol.getRoadPiece(h, w);
        if (rp != null) {
            this.mixingAdd_m = (int)(rp.speedLimit * ops.vehicMixer);
        } else {
            this.mixingAdd_m = 0;
        }

    }

    public float[] getWeight(float H, FusionCore ens) {
     
        if (ops.plumeAssessmentDistanceCut > e.dist_m) return new float[]{0,0};
        if (H<2.5 )H =2.5f;
        int hkey = (int)H;
        float[] pre = this.elevatedWeights.get(hkey);
        if (pre!=null) return pre;
        

        float[] w = new float[2];
        int addMixm = 0;
        if (H<5) addMixm = this.mixingAdd_m;
        int stabi = this.windState.initialStabiClass();
        float U = this.windState.setSecondaryProfile(ens, H);
        U = this.averageWindSpeed(U,this.windState.U,e.dist_m);
        //elevation mods
        float z = this.z_g0;
        reds.resetElevationComponent();
        //pollution will not disperse to this location with easy Gaussian rules => just limit the contribution.
        if (ops.applyElevation ) {
            if (this.current_eleDiff > 0) {//emission source location is at higher elevation, so simply increase H
                H+=this.current_eleDiff;
            } else {//this trickier. Approximate by elevating (-1*negative) z instead
                z-=this.current_eleDiff;  
                 reds.setElevationDifferenceReduction(Math.abs(this.current_eleDiff),H,e.dist_m,ops);
            } 
        }
        
        e.setAreaWeightedCoeffs_U_zH_D_SIG(w, U, z, H, this.windState.ABLH(), stabi, ops, addMixm);
        
        //finishing touch
        for (int k = 0; k < w.length; k++) {
            w[k] *= this.genW * this.reds.getReduction(k);
        }
        
        this.elevatedWeights.put(hkey, w);
        return w;
    }
    /**
     * Gaussian Plume formula needs a single value for wind speed. Problem: at
     * assessment location there is one, at emission location there is another.
     * Between these two points the wind speed can of course be anything. 
     * So, what to use?
     * 
     * As an approximation, define the wind speed as a linear interpolation
     * based on distance. At 500m distance use the emission source' wind speed.
     * At closer distances, blend in the wind speed at assessment location.
     * @param U
     * @param U0
     * @param dist_m
     * @return 
     */
    private float averageWindSpeed(float U, float U0, float dist_m) {
        float frac2 = dist_m/500;
        if (frac2 > 0.8) frac2=0.8f;
        if (frac2 < 0.4) frac2 = 0.4f;
        return frac2*U + (1.0f - frac2)*U0;
    }

    public OsmLayer osmLayer() {
       return this.ol;
    }

    public void listEmissionDensities(EmitterShortList emTemp) {               
        int h = ol.get_h(currLat);
        int w = ol.get_w(currLon);
        emTemp.listEmissionDensities(this.windState.met, currLat, currLon, ol, h, w,ops);//goes over the list of gridEmitters and cumulates emissions from these. Also, flags possible emissions to add.
    }

    public FootprintCell getCurrentCell() {
       return this.e;
    }

    public void updateHEGs(boolean b, FusionCore ens) {
        this.update_heg =b;
       if (b) {
           this.heg =ens.fastGridder.get(ol);
       }
    }

    public float[] getHEG_Element_hw_emPsm2() {
       if (this.heg==null) return null;
       
       return heg.getElement_hw_emPsm2(ol_h,ol_w, e);
    }

    public int[] updateDistanceRange(Observation ob, FusionCore ens, int[] originalRange) {
        
        if (!ens.ops.emissionDistanceChecking) {
            return originalRange;
        }
        //if there are no emission nearby, a lot of unnecessary computations can be avoided.
        if (this.ol==null) return originalRange;
        ol_h = ol.get_h(ob.lat);
        ol_w = ol.get_w(ob.lon);
        int dist= ens.fastGridder.getClosestEmissionDistance(ol, ol_h, ol_w);
        //example: range is [0m,300m] and closest emission distance is 100m
        
        int max = originalRange[1];//300m
        int min = originalRange[0];//0m
        
        if (dist > max) dist = max;//distance still 100m
        if (dist > min) min  = dist;//min is now 100m
        return new int[]{min,max}; //returns {100,300m}.
    }

    public float getBlocker(int k) {
       return this.reds.getReduction(k);
    }
}
