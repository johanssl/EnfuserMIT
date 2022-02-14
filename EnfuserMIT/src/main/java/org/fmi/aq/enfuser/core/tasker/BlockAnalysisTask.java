/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.tasker;

import org.fmi.aq.enfuser.core.tasker.BlockAnalysisTask.ObjArrayResult;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;

/**
 *
 * @author Lasse
 */


public class BlockAnalysisTask implements Callable<ObjArrayResult> {

    public class ObjArrayResult {

        public HashMap<Integer, BlockAnalysis[]> obs;

        public ObjArrayResult(HashMap<Integer, BlockAnalysis[]> obs) {
            this.obs = obs;
        }
    }

    final FusionCore ens;
    final AreaInfo in;
    final ArrayList<Integer> allH;

    public BlockAnalysisTask(ArrayList<Integer> allH, FusionCore ens, AreaInfo in) {
        this.ens = ens;
        this.in = in;
        this.allH = allH;
    }

    @Override
    public ObjArrayResult call() throws Exception {
        HashMap<Integer, BlockAnalysis[]> obs = new HashMap<>();

        for (int h : allH) {

            BlockAnalysis[] ob = new BlockAnalysis[in.W];
            for (int w = 0; w < in.W; w++) {
                double lat = in.getLat(h);
                double lon = in.getLon(w);
                OsmLayer ol = ens.mapPack.getOsmLayer(lat, lon);
                    ob[w] = new BlockAnalysis(ol, ens.ops, lat, lon, null);
            }//for w
            obs.put(h, ob);
        }//for h

        return new ObjArrayResult(obs);

    }

    public static BlockAnalysis[][] multiThreadTask(FusionCore ens, AreaInfo in) {

        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();
        int N = in.H / ens.ops.cores;
        if (N < 1) {
            N = 1;
        }
        //Create threads

        ArrayList<Integer> allH = new ArrayList<>();
        for (int h = 0; h < in.H; h++) {
            allH.add(h);

            if (allH.size() > N) {

                //make a clean copy of allH
                ArrayList<Integer> Hcopy = new ArrayList<>();
                for (Integer H : allH) {
                    Hcopy.add(H);
                }
                allH.clear();

                Callable cal = new BlockAnalysisTask(Hcopy, ens, in);
                FutureTask<GenericArrayTask> fut = new FutureTask<>(cal);
                Threader.executor.execute(fut);
                futs.add(fut);

            }//if size exceeded

        }//for h

//some h-values may still be there as leftovers.
        if (allH.size() > 0) {
            Callable cal = new BlockAnalysisTask(allH, ens, in);
            FutureTask<GenericArrayTask> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);
        }

        // MANAGE callables           
        boolean notReady = true;
        int K = 0;
        while (notReady) {//
            K++;
            notReady = false; // assume that ready 
            float readyCount = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } else {
                    readyCount++;
                }

            }//for futs

            if (K % 20 == 0) {
                EnfuserLogger.log(Level.FINER,BlockAnalysisTask.class,
                        "BA-array: Threads ready: " + readyCount * 100.0 / futs.size() + "%");
            }
            
             EnfuserLogger.sleep(300,BlockAnalysisTask.class);
        }// while not ready 

        //ready, collect data from strips
        BlockAnalysis[][] dat = new BlockAnalysis[in.H][];
        for (FutureTask fut : futs) {
            try {
                ObjArrayResult ar = (ObjArrayResult) (fut.get());
                for (Integer h : ar.obs.keySet()) {
                    dat[h] = ar.obs.get(h);
                }
            } catch (InterruptedException | ExecutionException ex) {
               EnfuserLogger.log(ex,Level.SEVERE,BlockAnalysisTask.class,
                       "BA multiThreading process failed!");
            }

        }// for futures              

        //count nulls
        int nulls = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                if (dat[h] == null) {
                    nulls += in.W;
                } else if (dat[h][w] == null) {
                    nulls++;
                }
            }//for w
        }//for h  

        EnfuserLogger.log(Level.FINER,BlockAnalysisTask.class,
                "BlockAnalysis: null-count = " + nulls + "in " + in.H + "x" + in.W + " grid.");

        Threader.executor.shutdown();
        return dat;
    }

}
