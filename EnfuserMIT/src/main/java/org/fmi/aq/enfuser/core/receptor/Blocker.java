/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell;
import static org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell.IND_CPM;
import static org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell.IND_GAS;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.date.Dtime;

/**
 * This class is used to scale down pollutant concentrations as a function of
 * physical, vegetation and elevation blocker effects. This is done separately
 * for gaseous species and for coarse particles.
 * 
 * The the assessment location, direction and distance is being used and the
 * input comes from GIS-datasets (via BlockAnalysis).
 * 
 * Physical: for locations inside buildings and behind obstacles.
 * Vegetation: OsmLayer vegetation data for different assessment directions.
 * Elevation: relative ground elevation, e.g., if the location is heavily
 * elevated the pollutant concentrations can be reduced to "adjust" the Gaussian
 * methods that natively cannot address these things.
 * 
 * @author johanssl
 */
public final class Blocker {
    
    private final float[] blockers = new float[2];
    
    public float eleRed;
    public float bbf;
    public float vege_coarseF;
    public float vege_other;
    public float buildingBlock;
    public Blocker() {
        this.reset();
    }
    
    /**
     * Create an instance and set it for the given assessment location/time.
     * @param z0 assessment height above ground in meters
     * @param dt time
     * @param ba BlockAnalysis for the assessment location
     * @param windDir ambient wind direction
     * @param ops options
     * @param eleDiff relative elevation difference used for elevation-based 
     * reductions [m]
     * @param range distance from the assessment location
     * @return 
     */
    public static Blocker getForCustomRange(float z0, Dtime dt, BlockAnalysis ba,
            float windDir, FusionOptions ops, float eleDiff, float range) {
      Blocker block = new Blocker();
      int sec = BlockAnalysis.getIndex(windDir);
      block.set(z0, ba, dt, ops, sec, range);
      block.setElevationDifferenceReduction(eleDiff, 10,range,ops);
      return block;
    }
    
    public static Blocker getForMidRange(float z0, Dtime dt, BlockAnalysis ba,
            float windDir, FusionOptions ops, float eleDiff) {
        Blocker inst = getForCustomRange(z0,dt,ba,windDir,ops,eleDiff,BlockAnalysis.BLOCKER_DIST_MAX);
        inst.setEffectPower(0.7);
        return inst;
    }
    
    private void setEffectPower(double d) {
        blockers[0] = (float)Math.pow(blockers[0], d);
        blockers[1] = (float)Math.pow(blockers[1], d);
        eleRed = (float)Math.pow(eleRed, d);
    }
    
    
    public void setElevationDifferenceReduction(float eleDiff, float H, float dist_m, FusionOptions ops) {
        eleRed = 1f -eleDiff/80f + H/100f;//eleDiff is negative.
        //so, if sourceH = z => no effect. If sourceH is low and eleDiff is large => a reduction.
        if (eleRed < 0.2) eleRed =0.2f;
        if (eleRed >1)eleRed=1;
    }
    
    
    public void set(float z0, BlockAnalysis ba, float current_wd, Dtime dt, FootprintCell e, FusionOptions ops) {
         int sec = e.getTrueSector(current_wd, BlockAnalysis.SECTOR_ANGLE); // sector of rotated element (0-35)
         set(z0, ba, dt,ops, sec, e.dist_m);
    }
    
     /**
     * Set reduction scaler values ready for the given assessment location/time.
     * This will not set up the elevation based component yet.
     * @param z0 assessment height above ground in meters
     * @param dt time
     * @param ba BlockAnalysis for the assessment location
     * @param sec wind direction sector (as defined in BlockAnalysis based on wind direction)
     * @param ops options
     * @param dist_m distance from the assessment location
     */
    public void set(float z0, BlockAnalysis ba,Dtime dt,FusionOptions ops, int sec, float dist_m) {

           float wd_sec = BlockAnalysis.SECTOR_ANGLE * sec; // sectors in 10 degree intervals.
           bbf = ba.getWallReductionFactor_zeroNoRed(wd_sec,
                   dist_m, z0, ops);//zero => no reduction, max 1.
           
           float raw_vegeBlocker = ops.vegeAmp*(float)ba.VEGE[sec]/100f;//zero => no reduction, max 1.
           if (raw_vegeBlocker>1)raw_vegeBlocker=1f;
           vege_coarseF = (float)ops.blockerStrength*raw_vegeBlocker*ops.VARA.monthlyVegetationReduct(ops.VARA.VAR_COARSE_PM, dt);
           vege_other = (float)ops.blockerStrength*raw_vegeBlocker*ops.VARA.monthlyVegetationReduct(ops.VARA.VAR_NO2, dt);
           
           buildingBlock =1f;
           if (ba.atBuilding) {
               buildingBlock = ops.BUILDING_PENETRATION;
           }
           
           blockers[IND_GAS] = buildingBlock*Math.max(0.05f, (1f -bbf - vege_other));
           blockers[IND_CPM] = buildingBlock*Math.max(0.05f, (1f -bbf - vege_coarseF));
    }

    void reset() {
         this.blockers[IND_GAS] = 1f;
         this.blockers[IND_CPM] = 1f;
         this.eleRed =1f;
    }

    void resetElevationComponent() {
        this.eleRed=1f;
    }

    public float getReduction(int k) {
       return this.blockers[k]*this.eleRed;
    }
    
    /**
     * Get reduction as a function of category (c) and species (q),
     * For background, no reduction will be applied (returns 1).
     * This is used for mid range puff dispersion when added to model predictions.
     * 
     * @param c category index
     * @param q species index
     * @param VARA variable definitions
     * @return [0,1].
     */
    float getReduction_CQ(int c, int q, VarAssociations VARA) {
        if (c== Categories.CAT_BG) return 1f;
        int k =IND_GAS;
        if (q == VARA.VAR_COARSE_PM) k = IND_CPM;
        return this.getReduction(k);
    }


    
}
