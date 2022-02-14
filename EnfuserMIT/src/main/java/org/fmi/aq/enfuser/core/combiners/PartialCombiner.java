/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.combiners;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.fpm.Footprint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.core.receptor.EnvArray;
import static org.fmi.aq.enfuser.core.combiners.PartialCombiner.CombinerCallable.COUNTER;
import static org.fmi.aq.enfuser.core.combiners.PartialCombiner.CombinerCallable.EVALS;
import static org.fmi.aq.enfuser.core.combiners.PartialCombiner.CombinerCallable.FILLS;
import org.fmi.aq.enfuser.core.varproxy.ProxyProcessor;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FuserTools;

/**
 *
 * @author johanssl
 */
public class PartialCombiner {

    public final static int H_STRIP_LEN = 28;

    public static AreaFusion processArea(AreaInfo inf,FusionCore aThis) {
        
       aThis.fastGridder.forceBlockAnalysisToMatch(inf,aThis);//debugging, this seems to be necessary 
       PartialCombiner pc = new PartialCombiner(inf, aThis);
       
       pc.initPartialGrids(aThis);
       AreaFusion af =pc.process(aThis);
       return af;
    }
    
    public final int minRange_m;
    final ArrayList<int[]> partialRanges;
    AreaInfo inf;
    public AreaFusion af;
   
    ArrayList<PartialGrid> pGrids;
    RadiusList RAD2 = RadiusList.withFixedRads(2, 10, 0);

    public PartialCombiner(AreaInfo inf, FusionCore ens) {
 
        this.inf = inf;
        this.af = new AreaFusion(inf,ens.ops);
        int microRad = (int) ens.ops.plumeZone1_m();
        //assess partial ranges
        this.partialRanges = new ArrayList<>();
        if (ens.puffModellingAvailable(inf.dt)) {//ppf data IS available
            
                EnfuserLogger.log(Level.FINER,PartialCombiner.class,
                        "Partial combiner: ppf data is available for " + inf.shortDesc());
            
            this.partialRanges.add(new int[]{microRad, ens.ops.perfOps.puffPlumeSwitchThreshold_m()});
            this.minRange_m = microRad;

        } else {
            
                EnfuserLogger.log(Level.INFO,PartialCombiner.class,
                        "Partial combiner: ppf data is <<NOT>> available for " + inf.shortDesc());
            
            this.partialRanges.add(new int[]{microRad, Integer.MAX_VALUE});
            this.minRange_m = microRad;
        }

            EnfuserLogger.log(Level.FINER,PartialCombiner.class,"FpmCell counts at breakPoints:");
            for (int[] ints : this.partialRanges) {
                int count = getFpmCellCount(ints[0], ens);
                int count2 = getFpmCellCount(Math.min(ints[1], 10000), ens);

                EnfuserLogger.log(Level.FINER,PartialCombiner.class,"Cells at " + ints[0] + "m => " + count 
                        + " cells. At " + ints[1] + "m => " + count2 + " cells.");
            }

            EnfuserLogger.log(Level.FINER,PartialCombiner.class,"Types to process:");
            for (Byte type : inf.types) {
                EnfuserLogger.log(Level.FINER,PartialCombiner.class,"\t " + ens.ops.VAR_NAME(type));
            }
    }

    private int getFpmCellCount(int dist_m, FusionCore ens) {
            Footprint form = ens.forms.standard;
            float[] sums = ens.forms.standard.getWeightSum_xmin_U_ZHD_SIG(
                    form.cells, dist_m, 4, 2, 2, 100, 0, ens.ops);
            return (int) sums[1];
    }

    public void initPartialGrids(FusionCore ens) {

        pGrids = new ArrayList<>();
        for (int[] minMax : this.partialRanges) {
            PartialGrid pg = new PartialGrid(minMax[0], minMax[1], inf, ens);
            pg.multiThread_init(ens);
            this.pGrids.add(pg);
        }

    }

    public AreaFusion process(FusionCore ens) {

        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();
        ens.setProgressBar(0);

        for (int h = 0; h < this.inf.H + H_STRIP_LEN-1; h += H_STRIP_LEN) {//TODO last strips might not be added
            FutureTask<int[]> fut;
            CombinerCallable cal = new CombinerCallable(h, inf, ens, new int[]{0, this.minRange_m}, pGrids);
            fut = new FutureTask<>(cal);

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
                EnfuserLogger.log(Level.FINER,PartialCombiner.class,prog 
                        + "% ready for final parts (0-" + this.minRange_m + "m).");
            }

             ens.setProgressBar(prog);
             EnfuserLogger.sleep(1000,PartialCombiner.class);
        }// while not ready 

        int[] allStats = new int[4];

        for (FutureTask fut : futs) {
            try {
                int[] stat = (int[]) (fut.get());
                for (int k = 0; k < stat.length; k++) {
                    allStats[k] += stat[k];
                    allStats[3]++;
                }

            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }
        }
        Threader.executor.shutdown();
        //stats printout
        EnfuserLogger.log(Level.FINER,PartialCombiner.class,
                "\t Amount of h-strips processed: " + allStats[3]);
        EnfuserLogger.log(Level.FINER,PartialCombiner.class,
                "\t Amount of processed elements: " + allStats[COUNTER] / 1000 + "k");
        EnfuserLogger.log(Level.FINER,PartialCombiner.class,
                "\t Amount of processed Env-objects: " + allStats[EVALS] / 1000 + "k");
        EnfuserLogger.log(Level.FINER,PartialCombiner.class,
                "\t Amount of Env-object fast fills (dynamic resolution): " + allStats[FILLS] / 1000 + "k");
        
        return this.af;
    }

    public class CombinerCallable implements Callable<int[]> {

        AreaInfo inf;
        final Dtime[] allDates;
        FusionCore ens;

        final int h_first;
        final int[] range;
        final float[] lats;
        final float[] lons;
        final ArrayList<PartialGrid> pGrids;
        private ProxyProcessor pp;
        RadiusList R1;
        RadiusList R2;

        public CombinerCallable(int h_first, AreaInfo inf, FusionCore ens,
                int[] range, ArrayList<PartialGrid> pGrids) {
            this.inf = inf;
            this.allDates = ens.tzt.getDates(inf.dt);
            this.ens = ens;
            this.h_first = h_first;
            this.range = range;
            this.pp = ens.prox.cloneWithSecondaryVars();
            lats = new float[H_STRIP_LEN];
            lons = new float[inf.W];
            R1 = RadiusList.withFixedRads_SQUARE(1, 10, 0);
            R2 = RadiusList.withFixedRads_SQUARE(2, 10, 0);

                    for (int i = 0; i < H_STRIP_LEN; i++) {
                        lats[i] = (float) inf.getLat(h_first + i);
                    }

                    for (int w =0;w<inf.W;w++) {
                        lons[w] = (float) inf.getLon(w);
                    }

            //this.nonMets = nonMets;
            this.pGrids = pGrids;
        }
        public final static int COUNTER = 0;
        public final static int EVALS = 1;
        public final static int FILLS = 2;

        @Override
        public int[] call() throws Exception {

            int[] stats = new int[3];
            float[][][][] expsPrecomp_hw_qc = new float[lats.length][inf.W][][];
            boolean[][] toFill = new boolean[lats.length][inf.W];
            ArrayList<Integer> types = FuserTools.arrayConvert(inf.types);
            Observation fob=null;
            boolean warned = false;
            //FORST PASS: process every 3rd
            for (int w = 0; w < inf.W; w += 3) {
                for (int h = 0; h < lats.length; h += 3) {
                    int trueH = h_first + h;
                    if (trueH >= inf.H) {
                        continue;
                    }
                    stats[COUNTER]++;
                    stats[EVALS]++;

                    float lat = lats[h];
                    float lon = lons[w];
                    if (fob==null) {
                        fob = Observation.AreaFusionPoint(lat, lon, inf.dt, FusionCore.FUSION_HEIGHT_M,ens.ops);
                    } else {
                        fob.lat =lat;
                        fob.lon=lon;
                    }
                    //Met met = ens.getStoreMet(inf.dt, lat, lon);
                    BlockAnalysis ba = ens.fastGridder.getStoredBA(trueH, w);
                    if (ba==null) {
                        if (!warned) {
                            EnfuserLogger.log(Level.WARNING, this.getClass(),
                                    "Null BA encountered with "+ trueH +", "+w +" ("+lat+","+lon+")"
                            +", " + ens.fastGridder.printBBspecs());
                            warned = true;
                        }
                     continue;   
                    }
                    EnvArray enva = PartialProfiler.profile_fastArea(range, allDates, ens, fob, null,ba);

                    enva.stackAll(this.pGrids, lat, lon);//cumulate contribution from other distance interval products.

                    RP env = new RP(enva, lat, lon, ens, inf.dt, allDates);
                    float[][] env_qc = env.getPrimaryExps(types, ens);
                    expsPrecomp_hw_qc[h][w] = env_qc;

                    af.update(types, env, trueH, w, ens, false);
                    
                    //fill markers
                    RadiusList rl=null;
                    int RAD= env.variableRes(inf.res_m, ens.ops);
                    if (RAD==1) {
                        rl = R1;
                    } else if (RAD==2) {
                        rl=R2;
                    }
                    if (rl != null) {
                    for (RadListElement e: rl.elems) {
                            if (oobs(e,h,w))continue;
                                toFill[h + e.hAdd][w + e.wAdd] = true;//flag as fillable 
                    }
                    }

                }//for miniH
            }//for w

            //==============SECOND PASS, all elements this time
            //fill blanks
            ArrayList<float[][]> temp = new ArrayList<>();
            ArrayList<Float> fillW = new ArrayList<>();

            for (int w = 0; w < inf.W; w++) {
                for (int h = 0; h < lats.length; h++) {
                    
                    int trueH = h_first + h;
                    if (expsPrecomp_hw_qc[h][w] != null || trueH >= inf.H) {
                        continue;//already processed!
                    }
                    stats[COUNTER]++;//fill or create a new RP
                    boolean border = (trueH ==inf.H-1 || w == inf.W-1);
                    if (toFill[h][w] && !border) {////it is NULL and needs to be filled ================================
                        temp.clear();
                        fillW.clear();
                        float wsum = 0;
                        stats[FILLS]++;
                        for (RadListElement ele : RAD2.elems) {//check vicinity. There will be at least TWO.
                                if(oobs(ele,h,w)) continue;
                                int hadd = ele.hAdd;
                                int wadd = ele.wAdd;
                                float[][] test = expsPrecomp_hw_qc[h + hadd][w + wadd];

                                if (test != null) {
                                    temp.add(test);
                                    float weight = 1f / (hadd * hadd + wadd * wadd + 0.01f);
                                    fillW.add(weight);
                                    wsum += weight;
                                }

                            
                        }//for rad2

                        //just average and update
                        float[][] clone = new float[inf.types.size()][ens.ops.CATS.CATNAMES.length];
                        //float scaler = 1f/temp.size();
                        int z = -1;//indexing for temp and fillW
                        for (float[][] existing : temp) {
                            z++;

                            for (int q = 0; q < inf.types.size(); q++) {
                                float[] catArr = existing[q];
                                if (catArr == null) {
                                    continue;//if not ptimary
                                }
                                for (int c = 0; c < catArr.length; c++) {
                                    clone[q][c] += catArr[c] * fillW.get(z) / wsum;
                                }//for cat

                            }//for q
                        }//for existing
                        //clone is ready => update
                        af.update(types, clone, trueH, w, true);
                    } else {//==================================================================================
                        stats[EVALS]++;

                        fob.lat = lats[h];
                        fob.lon = lons[w];
                        BlockAnalysis ba = ens.fastGridder.getStoredBA(trueH, w);
                        EnvArray enva = PartialProfiler.profile_fastArea(range, allDates, ens, fob, null,ba);
                        enva.stackAll(this.pGrids, fob.lat, fob.lon);//cumulate contribution from other distance interval products.
                        RP env = new RP(enva, fob.lat, fob.lon, ens, inf.dt, allDates);
                        af.update(types, env, trueH, w, ens, false);

                    }

                }//for h
            }//for w

         //finally, secondaries
         HashMap<String, Double> tempHash = new HashMap<>();
         for (int w = 0; w < inf.W; w++) {
                for (int h = 0; h < lats.length; h++) {
                  int currH = h_first + h;
                  if (currH >= inf.H) {
                        continue;
                    }
                   pp.processOne(af, currH, w, ens,tempHash);
                }
         }   
            
            return stats;
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
       if (h+e.hAdd>= this.lats.length) return true;
       
       if (w+e.wAdd<0) return true;
       if (w+e.wAdd>=this.lons.length) return true;
       
       return false;       
   }   
    }
    
    
    


}
