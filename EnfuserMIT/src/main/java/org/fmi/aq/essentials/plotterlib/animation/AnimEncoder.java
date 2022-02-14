/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.animation;

import org.fmi.aq.enfuser.ftools.Threader;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.interfacing.GraphicsMod;

/**
 * This callable class contains a list of images (frames) that
 * can be encoded into a video animation. An this is what the class does,
 * possibly multiple animations being processed in parallel.
 * @author johanssl
 */
public class AnimEncoder implements Callable<Boolean>{


    ArrayList<File> allFigs;
    VisualOptions ops;
    String name;
    AnimEncoder(ArrayList<File> animFigs, String name, VisualOptions ops) {
       
        this.allFigs =animFigs;
        this.name = name;
        this.ops = ops;
        
    }

    @Override
    public Boolean call() throws Exception {
                                        new GraphicsMod().produceVideo(allFigs,
                    name, ops.deleteVidImgs, ops,false);
            EnfuserLogger.log(Level.INFO,this.getClass(),"Animation created at: " + name);
            return true;
    }
    
    
        public static void encodeAll(ArrayList<AnimEncoder> encs, FusionOptions ops) {
       if (encs==null ||encs.isEmpty()) return;
       
        boolean runnable = ops.perfOps.videoAsRunnable();
        if (runnable) {
            
            ArrayList<FutureTask> futs = new ArrayList<>();
            Threader.reviveExecutor(8);

           for (AnimEncoder a: encs) {
                FutureTask<Boolean> fut = new FutureTask<>(a);
                Threader.executor.execute(fut);
                futs.add(fut);
            }//for slices        

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
                if (K % 10 == 0) {
                    EnfuserLogger.log(Level.FINE,AnimEncoder.class,
                            prog + "% of threads are ready.");
                }
                EnfuserLogger.sleep(200,Anim.class);
            }// while not ready 

            Threader.executor.shutdown();
            
        } else {//single threaded
            for (AnimEncoder a: encs) {
                try {
                    a.call();
                } catch (Exception ex) {
                    Logger.getLogger(AnimEncoder.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
    }
    
}
