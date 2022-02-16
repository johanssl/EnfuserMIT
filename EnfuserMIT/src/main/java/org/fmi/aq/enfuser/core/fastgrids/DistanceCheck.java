/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.fastgrids;

import java.util.ArrayList;;
import org.fmi.aq.enfuser.core.FusionCore;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.interfacing.Imager;

/**
 * This class is for the assessment of emission cell distances. The use case is 
 * for profilers (especially PartialProfiler) in which the amount of assessment
 * locations can be reduced in case it is known that there are no nearby emissions
 * to model. Each HashedEmissionGrid can hold an istance of this, but it is only
 *  created and utilized in case specified by FusionOptions.
 * 
 * The main logic is simple: we take the hashed emissions in 20m and check
 * the closest distance to non-zero elements.
 * @author johanssl
 */
public class DistanceCheck {

    public final static int H_STRIP_LEN = 30;
    byte[][] workGrid;
    boolean[][] existGrid;

    
    final int resDiv;
    final int H;
    final int W;
    float cellRes;
    int maxRad =14;//times 20m
    final Dtime dt;
    final Boundaries b;
    public DistanceCheck(int lowH, int lowW, Dtime dt,
            Boundaries b, int resDiv, OsmLayer ol) {
        this.H = lowH;
        this.W = lowW;
        
        this.cellRes = resDiv*ol.resm;
        existGrid= new boolean[H][W];
        workGrid = new byte[H][W];  
        this.dt = dt;
        this.b = b;
        this.resDiv = resDiv;
    }
    
    /**
     * First form a grid of booleans to assess whether or not a location has
     * emissions or not. Then do the distance checks in multi-threading.
     * @param heg 
     */
    public void distanceGridInit(HashedEmissionGrid heg) {
        
        for (int h =0;h<H;h++) {
            for (int w=0;w<W;w++) {
                
                int key = OsmLayer.getSpatialHashKey(h, w);
                float[] test = heg.hash_20m_emPsm2.get(key);
                if (test!=null) {
                   existGrid[h][w]=true;  
                }
            }
        }

        process();  
    }    
    
    /**
     * Get a grid representation of the assessed emission distances.
     * @return 
     */
    private GeoGrid getDistanceGrid() {
        
       float[][] dat = new float[this.H][this.W];
       GeoGrid g = new GeoGrid(dat,this.dt,b);
       
       for (int h =0;h<this.H;h++) {
           for (int w =0;w<this.W;w++) {
               float dist = workGrid[h][w]*this.cellRes;
               g.values[h][w]=dist;
           }
       }
        return g;
    }
    
    /**
     * Launch the distance check algorithm in multi-threading.
     */
    public void process() {

        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();

        
        for (int h = 0; h < H + H_STRIP_LEN-1; h += H_STRIP_LEN) {
            
            DistCheckCallable cal = new DistCheckCallable(h, existGrid, maxRad, workGrid);
            //it will take some time before all Callables are added as futures (thread completed)
            FutureTask<Integer> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);

        }

        // MANAGE callables           
        boolean notReady = true;
        int K = 0;
        while (notReady) {//
            K++;
            notReady = false; // assume that ready 
            double ready_N = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } else {
                    ready_N++;
                }
            }
            int prog = (int) (100 * ready_N / futs.size());
            if (K % 5 == 0) {
                EnfuserLogger.log(Level.FINER,DistanceCheck.class,prog 
                        + "% ready.");
            }

             EnfuserLogger.sleep(100,DistanceCheck.class);
        }// while not ready 

       int totals =0;
        for (FutureTask fut : futs) {
            try {
                Integer stat = (Integer) (fut.get());
                if (stat!=null) totals+=stat;

            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        }
        
        EnfuserLogger.log(Level.FINER,DistanceCheck.class,
                "Done. Encounter= "+totals);
        Threader.executor.shutdown();
    }

    /**
     * Visualize the emission distance mapping as a figure and also show
     * it as a pop-up panel.
     * @param ens
     * @param ol 
     */
    void visualize(FusionCore ens, OsmLayer ol) {
       GeoGrid g = getDistanceGrid();
       String dir = ens.ops.defaultOutDir();
       String styleTag ="default";
       String[] txt = {"emDistCheck_" + ol.name, "Emission cell distance check [m, to nearest cell]"};
       boolean toPane = false;
       boolean kmz = false;
       double[] mm = null;
       Double curver = 1.0;
       Imager.visualize(dir, g, styleTag,txt, ens.mapPack, toPane,kmz,mm,curver); 
    }
    
    /**
     * for an osmLayer location index (h,w) return the assessed closest distance
     * to a mapped emitter cell.
     * @param ol_h
     * @param ol_w
     * @return 
     */
    int getClosestEmissionDistance(int ol_h, int ol_w) {
        int h = ol_h/resDiv;
        int w = ol_w/resDiv;
        if (oobs(h,w)) return 0;
        
        int dist= (int)(this.workGrid[h][w]*cellRes -20);
        return Math.max(dist,0);
    }
    
       private boolean oobs(int h, int w) {
       if (h<0) return true;
       if (h>= H) return true;
       
       if (w<0) return true;
       if (w>=W) return true;
       
       return false;       
   } 

    public class DistCheckCallable implements Callable<Integer> {

       
        final int h_first;
        final boolean[][] exists;
        final byte[][] workGrid;
        final RadiusList R;
        final int W;
        final int H;
        final byte maxR;
        
        public DistCheckCallable(int h_first, boolean[][] exists,
                int maxRad, byte[][] workGrid) {       
            this.h_first = h_first;
            this.exists = exists;
            R = RadiusList.withFixedRads_SQUARE(maxRad, 1, 0);
            this.W = exists[0].length;
            this.H = exists.length;
            this.workGrid = workGrid;
            this.maxR = (byte)maxRad;
        }

        @Override
        public Integer call() throws Exception {

            int encounters = 0;
            for (int w = 0; w < W; w ++) {//parse sweep
                for (int h = 0; h < H_STRIP_LEN; h ++) {
                    int trueH = h_first + h;
                    if (trueH >= H) {
                        continue;
                    }
                    //this.workGrid[trueH][w] = (byte)-1;
                    boolean wasFound =false;
                    for (RadListElement e: R.elems) {
                        int nh = trueH + e.hAdd;
                        int nw = w+e.wAdd;
                        if (oobs(e,nh,nw)) continue;
                        if (e.dist_m>maxR+1) continue;
                        boolean hasContent = this.exists[nh][nw];
                        if (hasContent) {
                            this.workGrid[trueH][w] = (byte)Math.round(e.dist_m);
                            wasFound =true;
                            encounters++;
                            break;
                        }
                        
                    }//for cells
                    if (!wasFound) {
                        this.workGrid[trueH][w] =(byte)(maxR+1);
                    }
                }//for miniH
            }//for w
           
         return encounters;   
        }
        
    /**
    * Check for outOfBounds exception. If true is returned, skip the iteration.
    * @param e
    * @param h
    * @param w
    * @return 
    */     
   private boolean oobs(RadListElement e, int h, int w) {
       if (h+e.hAdd<0) return true;
       if (h+e.hAdd>= H) return true;
       
       if (w+e.wAdd<0) return true;
       if (w+e.wAdd>=W) return true;
       
       return false;       
   }   

    }
    
    
    


}
