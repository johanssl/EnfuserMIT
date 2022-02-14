/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;

/**
 * This class handles the merging of puffs in order the reduce the total
 * amount of puffs modelled. To be exact, this handles an array of merging
 * 'cells' in which the merging can occur. The selection of the cell
 * will be based on the location but also depends on puff release height,
 * type of puff and puff travel distance. 
 * 
 * The idea is to merge similar puffs
 * that are nearby each other.
 * 
 * @author johanssl
 */
public class MergingPool {
    
   final AreaNfo in;
   final MergingCell[][][] cells_ZHW;
   ArrayList<PuffCarrier> bypassed = new ArrayList<>();
   
   private final static int[] HEIGHTS = {40,80,1000};
   
   public boolean allowRemerges = false;
   public final int mergerMinDistance_m;
   public final float mergeTendency;
   boolean merges = true;
   boolean randomizer = true;
   
   int puffsHeld =0;
   int mergedInputPuffs =0;
   int mergedOutputPuffs =0;
   
   
  public MergingPool(Boundaries b_expanded, FusionOptions ops) {
      
        //puff merging options
        this.mergeTendency = ops.perfOps.getPuffMergingStrength();//in between 0, 1, default value is 0.5
        float cellWidth_percent = 1f + 2*mergeTendency; //Smaller value means smaller merging cells which means less merging.
        if (this.mergeTendency <= 0) {
            this.merges = false;
        }
      
        EnfuserLogger.log(Level.FINER,PuffBins.class,"MergingPool: init merger arrays...");
        float latRangeEx_m = (float) ((b_expanded.latmax - b_expanded.latmin) * degreeInMeters);
        
        this.mergerMinDistance_m = (int)(0.25f*latRangeEx_m);
        float mergerRes_m = latRangeEx_m * cellWidth_percent/100f;
        
        in = new AreaNfo(b_expanded.clone(), mergerRes_m, Dtime.getSystemDate_utc(false, Dtime.ALL));
         EnfuserLogger.log(Level.INFO,PuffBins.class,"PuffBins: merger cell resolution [m]= " 
                 +mergerRes_m +", minimum travel distance for merging = "
                 +this.mergerMinDistance_m +"\nH="+ in.H +" x "+in.W +", merges = "+this.merges);
         
         this.cells_ZHW = new MergingCell[HEIGHTS.length][in.H][in.W];
         for (int z =0;z<HEIGHTS.length;z++) {
             for (int h =0;h< in.H;h++) {
                 for (int w =0;w<in.W;w++) {
                     this.cells_ZHW[z][h][w] = new MergingCell();
                 }//for w
             }//for h
         }//for z

  }
  
    /**
     * Insert a PuffCarrier to the Merging pool. If the puff can be merged
     * a proper cell is sought for it and the puff is added to it.
     * @param p the puff to be added
     * @param ppf the puff platform
     * @return true if added and applicable for merging.
     */
    protected boolean put(PuffCarrier p, PuffPlatform ppf) {
        if (!this.merges) return false;
        if (p.isInactive() 
                || !p.canMerge
                ||  p.dist_total_m < mergerMinDistance_m
                || (!this.allowRemerges && p.merged)
                ||  p instanceof MarkerCarrier
                ) {
              
            this.bypassed.add(p);
            return false;
        }
        //randomization check
        boolean randomSkip = false;//if true the puff will not attempt merging
        if (randomizer) {
            double rand01 = p.rs.nextRand01();
            float skipIfLower = 0.7f;//skip merging if rand is LOWER than this
            skipIfLower -=this.mergeTendency*0.8f;//at maximum setting this threshold is lower than 0
            if (p.merged) skipIfLower+=0.3;//make the skip MORE likely to occur for already merged puffs.
            randomSkip = (rand01 < skipIfLower);
        }
        //coordinate check
        float lat = p.currentLat(ppf);
        float lon = p.currentLon(ppf);
        int h = in.getH(lat);
        int w = in.getW(lon);
        boolean oobs = in.oobs(h, w);
        
        if (randomSkip || oobs) {
            this.bypassed.add(p);
            return false;
        }
        //adding to merger cells. Define the index
        //get the index - Z
        int Z = HEIGHTS.length - 1;//assume last
        float height = p.HYX[0];
        for (int z = 0; z < HEIGHTS.length; z++) {
            if (height < HEIGHTS[z]) {//if the release height is smaller than this, update and break.
                Z = z;
                break;
            }
        }

        this.cells_ZHW[Z][h][w].add(p);
        puffsHeld++;
        return true;
    }


    /**
     * Clear all merging cells and puff lists.
     */
    protected void clear() {
        this.bypassed.clear();
        this.puffsHeld = 0;
        
        if (!this.merges) return;
            for (MergingCell[][] ccc:this.cells_ZHW) {
                for (MergingCell[] cc:ccc) {
                    for (MergingCell c:cc) {
                       c.clear();
                    }

                }//for h
            }//for w
    }

    ArrayList<PuffCarrier> process(PuffPlatform ppf) {
        this.mergedInputPuffs=0;
        this.mergedOutputPuffs=0;
        if (!this.merges) return ppf.instances;

        ArrayList<PuffCarrier> all = new ArrayList<>();
        all.addAll(this.bypassed); 
        
         for (MergingCell[][] ccc:this.cells_ZHW) {
                for (MergingCell[] cc:ccc) {
                    for (MergingCell mc:cc) {
                    
                      if (!mc.hasMergableContent()) {//empty or too few
                          //if too dew then these must be returned to the stack as they are.
                          mc.flushUnmergedContentTo(all);
                         
                      } else {
                        this.mergedInputPuffs+= mc.mergeAndAdd(all,ppf);//this many default puffs was consumed
                        this.mergedOutputPuffs++;//one merged puff emerges.
                      }
                }//for w           
            }//for h
        }//for d
         
         return all;
    }
    
    
}
