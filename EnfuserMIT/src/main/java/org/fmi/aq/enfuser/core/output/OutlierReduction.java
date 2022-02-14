/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import java.util.ArrayList;
import java.util.Collections;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 *
 * @author johanssl
 */
public class OutlierReduction {
    
   public static void cutOutliers(AreaFusion af, FusionOptions ops) {
       ArrayList<Float> values = new ArrayList<>();
       
       double[] cutPercentiles = ops.getArguments().getOutlierCut();
       if (cutPercentiles==null) {
           EnfuserLogger.log(Level.INFO,OutlierReduction.class,
                   "OutlierCut: parameters are null.");
           return;
       }
       
       int sparser = (int)cutPercentiles[1];
       if (sparser <1) {
           sparser=1;
           EnfuserLogger.log(Level.FINER,OutlierReduction.class,
                   "Outlier cut: Warning: sparser value is below one. "+ cutPercentiles[1]);
       }
       double cutPercentile = cutPercentiles[0];
       if (cutPercentile>=1) {
            EnfuserLogger.log(Level.FINER,OutlierReduction.class,
                    "Outlier cut: Warning: cut percentile should never exceed 1. "
                            + "Value was "+ cutPercentiles[0] +", setting to 0.998");
            cutPercentile=0.998;
       } else if (cutPercentile<0.95) {
            EnfuserLogger.log(Level.FINER,OutlierReduction.class,
                    "Outlier cut: Warning: cut percentile should never"
                    + " be lower than 0.95. Value was"+ cutPercentiles[0]);
            cutPercentile=0.95;
       }
       
               
       long t = System.currentTimeMillis();
      VarAssociations VARA = ops.VARA;
      for (int i=0;i<VARA.VAR_LEN();i++) {
          
          if (VARA.isMetVar[i]) continue;
          if (!af.hasConcentrationGrid(i)) continue;
          
          values.clear();
            float max = Float.MAX_VALUE*-2;
            for (int h = 0; h < af.H; h += sparser) {
                for (int w = 0; w < af.W; w += sparser) {
                    float val = af.getConsValue(i, h, w);//consGrid_qhw[typeInt][H][W];
                    boolean wasmax = false;
                    if (val>max) {
                        max =val;
                        wasmax = true;
                    }
                    if (wasmax 
                            || (h%(sparser*3) == 0 && w%(sparser*3)==0)
                            ) values.add(val);
                }//for w
            }//for h

            Collections.sort(values); 
            
            int index = (int) (values.size() * cutPercentile);
            float listMax = values.get(index);
             EnfuserLogger.log(Level.FINE,OutlierReduction.class,"Value list size: " 
                     + values.size() + ", " + (cutPercentile * 100) 
                      + "% percentile max value from list is " + listMax +". Type = "+ VARA.VAR_NAME(i));


            //then apply an outlier reduction
            for (int h = 0; h < af.H; h ++) {
              for (int w = 0; w < af.W; w ++) {
                  float val = af.getConsValue(i, h, w);//consGrid_qhw[typeInt][H][W];
                  if (val>listMax) {
                      af.putValue(i, listMax, h, w);
                  }
              }
            }//for h
      }//for types
      
 long t2 = System.currentTimeMillis();
 EnfuserLogger.log(Level.FINER,
         OutlierReduction.class,"Outlier cut took: "+ (t2-t)+"ms");
   } 
 
   public static void cutOutliers(GeoGrid g, FusionOptions ops) {
       ArrayList<Float> values = new ArrayList<>();
       double[] cutPercentiles = ops.getArguments().getOutlierCut();
       if (cutPercentiles==null) {
            EnfuserLogger.log(Level.INFO,
                    OutlierReduction.class,"OutlierCut: parameters are null.");
           return;
       }
       int sparser = (int)cutPercentiles[1];
       if (sparser <1) {
           sparser=1;
           EnfuserLogger.log(Level.FINER,OutlierReduction.class,
                   "Outlier cut: Warning: sparser value is below one. "+ cutPercentiles[1]);
       }
       double cutPercentile = cutPercentiles[0];
       if (cutPercentile>=1) {
            EnfuserLogger.log(Level.WARNING,OutlierReduction.class,
                    "Outlier cut: Warning: cut percentile should never exceed 1."
                            + " Value was "+ cutPercentiles[0] +", setting to 0.998");
            cutPercentile=0.998;
       } else if (cutPercentile<0.95) {
            EnfuserLogger.log(Level.WARNING,OutlierReduction.class,
                    "Outlier cut: Warning: cut percentile should never be"
                            + " lower than 0.95. Value was"+ cutPercentiles[0]);
            cutPercentile=0.95;
       }
       
         long t = System.currentTimeMillis();
            float max = Float.MAX_VALUE*-2;
            for (int h = 0; h < g.H; h += sparser) {
                for (int w = 0; w < g.W; w += sparser) {
                    float val =g.values[h][w];
                    boolean wasmax = false;
                    if (val>max) {
                        max =val;
                        wasmax = true;
                    }
                    if (wasmax 
                            || (h%(sparser*3) == 0 && w%(sparser*3)==0)
                            ) values.add(val);
                }//for w
            }//for h

            Collections.sort(values); 
            
            int index = (int) (values.size() * cutPercentile);
            float listMax = values.get(index);
             EnfuserLogger.log(Level.FINE,
                     OutlierReduction.class,"Value list size: " 
                     + values.size() + ", " + (cutPercentile * 100) 
                      + "% percentile max value from list is " + listMax);


            //then apply an outlier reduction
            for (int h = 0; h < g.H; h ++) {
              for (int w = 0; w < g.W; w ++) {
                  float val = g.values[h][w];
                  if (val>listMax) {
                     g.values[h][w]=listMax;
                  }
              }//for w
            }//for h
      
             long t2 = System.currentTimeMillis();
    EnfuserLogger.log(Level.FINER,OutlierReduction.class,"Outlier cut took: "+ (t2-t)+"ms");
   }
}
