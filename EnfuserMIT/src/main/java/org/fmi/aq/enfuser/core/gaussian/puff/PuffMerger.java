/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 *This static class can merge multiple puffs into a single super puff.
 * @author johanssl
 */
public class PuffMerger {
    
      /**
     * Merge multiple puff into a single one that holds all the total emissions.
     * @param puffs an array of existing puffs that are to be merged into a
     * single super-puff.
     * @param pf
     * @return a merged puff with averaged location and life time, holding
     * the total combined emission mass.
     */
    public static PuffCarrier merge(ArrayList<PuffCarrier> puffs, PuffPlatform pf) {
        
        //start from ID, category and total emission mass 
        String ID = PuffCarrier.MERGED;
        FusionOptions ops = pf.ens.ops;
        //emission masses
        float[] Q = emissionTotals(puffs, ops);
        //all of the merged puffs should be somewhat similar, e.g, Their travel distance
        //and location. Most of the attrbibutes we can simply copy from any of the
        //puffs to be merged.
        PuffCarrier first = puffs.get(0);
        //WindState wstate = first.windState;
        int initialCat = first.sourcType;//some category must be specified, this can be a composite of many, so let's use this one.
        boolean singleCat = true;//assume that all the puffs belong to the same source category.
        //this will be verified naturally.
        
        int discard_zone=first.discard_zone;//update this, if smaller exists
        int NSR_limit_m = first.NSR_limit_m;
        
        float[] aveHYX = new float[3];
        float[] latlon =new float[2];
        float N = puffs.size();
        
        float average_life_s=0;
        float average_dist_m =0;
        
        for (PuffCarrier p : puffs) {
            
            if (p.sourcType!= initialCat || p.fractionalCategories_cq!=null) {
                singleCat = false;//do not match, there will be a multi-category representation
                initialCat = Categories.CAT_MISC;
            }
            
            //maximum counts
            if (p.discard_zone < discard_zone) {//very expanded is 0, expanded is 1...
                discard_zone = p.discard_zone;//update if larger
            }
            
            if (p.NSR_limit_m > NSR_limit_m) {
                NSR_limit_m = p.NSR_limit_m;//update if larger
            }
            
            //lifspan
            average_life_s += p.timer_total_s/N;
            average_dist_m += p.dist_total_m/N;
            
            //position averaging
            for (int j = 0; j < p.HYX.length; j++) {
                aveHYX[j] += p.HYX[j] / N;
            }
            
            latlon[0]+=p.currentLat(pf)/N;
            latlon[1]+=p.currentLon(pf)/N;
            
            
            //these puffs should be removed from the Stack via BuffBins. However,
            //lets make doubly sure that these merged puffs will not live to see
            //another concentration computation step.
            p.toBeRemoved=true;
            p.immaterial_meters_left = Float.MAX_VALUE;
        }
        
        float immaterial_until_m =-1;//a merged puff is always material (merging allowed only for these)
        PuffCarrier p = new PuffCarrier(pf.ens, pf.getDt(),discard_zone,  aveHYX, Q,
            latlon, ID, initialCat, immaterial_until_m,NSR_limit_m);
        p.merged = true;
        p.mergedFrom = puffs.size();
        p.timer_total_s = average_life_s;
        p.dist_total_m = average_dist_m;//this is very important NOT to start from zero (affects the spread).
        
        if (!singleCat) { //the sourec category assignments need to be addressed manually
            float[][] cq= CQcombination(puffs, ops);
            byte[][] catFractions= RP.expsToContributionPercents(cq,ops);
            p.fractionalCategories_cq = catFractions;
        }
        validate(p,puffs);
        return p;
    } 
    
    private static int CHECK_COUNTER =0;
    /**
     * Printout a test validation for the outcome. This can only occur
     * infrequently and only if the logging level is FINE or lower.
     * @param merged the outcome
     * @param puffs the puffs that were used to get the outcome
     */
    private static void validate(PuffCarrier merged, ArrayList<PuffCarrier> puffs) {
        GlobOptions g = GlobOptions.get();
        if (g.getLoggingLevel().intValue()<= Level.FINE.intValue() 
                && CHECK_COUNTER %50==0) {
            CHECK_COUNTER++;
            
            String s = "Merging validation "+CHECK_COUNTER +" with "+puffs.size();
            
            float individuals = 0;
            float mergedSum =emissionCheckSum(merged);
            int k =-1;
            for (PuffCarrier p:puffs) {
                k++;
                individuals+=emissionCheckSum(p);
                if (k%20==0) {
                    s+=differenceCheck(merged,p);
                }
            } 
            s+="mergedCheckSum="+mergedSum +", individuals="+individuals;
            EnfuserLogger.log(Level.FINE, PuffMerger.class, s);
        }//if validation  
    }
    
    private static float emissionCheckSum(PuffCarrier p) {
        float sum =0;
        for (float e:p.Q) {
            sum+=e;
        }
        return sum;
    }
    
    /**
     * Compare the meteorology, location and other metrics for the two puffs.
     * @param p1 the puff number one
     * @param p2 the puff number two
     * @return a String report for comparison.
     */
    private static String differenceCheck(PuffCarrier p1, PuffCarrier p2) {
        float x2 = p1.HYX[2] - p2.HYX[2];
        x2*=x2;
        
        float y2 = p1.HYX[1] - p2.HYX[1];
        y2*=y2;
        int dist_m = (int)Math.sqrt(x2 +y2);
        
        return "\t"
                + "ABLH "+ (int)p1.windState.maxABLH() +" vs " +(int)p2.windState.maxABLH() +", "
                + "U "+ p1.windState.U +" vs " +p2.windState.U +", " 
                + "wdir "+ (int)p1.windState.wdir +" vs " +(int)p2.windState.wdir +", " 
                + "H "+ p1.HYX[0] +" vs " +p2.HYX[0] +", " 
                + "distance_m "+ dist_m +", "  
                + "travel_m "+ p1.dist_total_m +" vs " +p2.dist_total_m;        
    }
            
    
        /**
         * Stack all the emission masses into a single vector.
         * @param puffs the puffs holding the total emission masses
         * @param ops options
         * @return emission masses as float[variable type dimension]
         */
        private static float[] emissionTotals(ArrayList<PuffCarrier> puffs, FusionOptions ops) {
            float[] ems = new float[ops.VARA.Q_NAMES.length];
             for (PuffCarrier p:puffs) {
                 for (int q : ops.VARA.Q) {
                    ems[q] += p.Q[q];
                 }//for q
             }//for puffs
           return ems;
        }
    
    /**
     * Stack the emission masses of the given puffs into a Category-VariableType (Q)
     * representation.
     * @param puffs the puffs for which the emission masses are combined
     * @param ops options
     * @return emission masses as float[category dimension ][variable type dimension]
     */
    private static float[][] CQcombination(ArrayList<PuffCarrier> puffs, FusionOptions ops) {
        
        float[][] cq = new float[ops.CATS.CATNAMES.length][ops.VARA.Q_NAMES.length];
        
        for (PuffCarrier p:puffs) {
            
        for (int q : ops.VARA.Q) {
                if (p.fractionalCategories_cq == null) {//single-category
                    int c = p.sourcType;
                    cq[c][q] += p.Q[q];

                } else {//a bit more complex sum operation. The puff has multiple origin categories (mapPuff)
                    //the fractional categories have this information available as percent bytes [0,100]
                    for (int c:ops.CATS.C) {
                        float frac = (float) p.fractionalCategories_cq[c][q] / 100f;
                        cq[c][q] += p.Q[q] * frac;

                    }//for cats

                }
                
        }//for q
        }//for puffs
      return cq;  
    }
    
}
