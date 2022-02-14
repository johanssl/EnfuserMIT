/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
    public class PuffCalcCallable2 implements Callable<ArrayList<QCd>> {

        PuffPlatform pf;
        ArrayList<int[]> hw;

    public static QCd getCons(PuffPlatform ppf,int y, int x) {
        FusionOptions ops = ppf.ens.ops;
       QCd qcd = new QCd(ops);

        float lat = (float)ppf.in.getLat(y);
        float lon =  (float)ppf.in.getLon(x);
        ArrayList<PuffCarrier> localPuffs = ppf.bins.getPuffList(lat, lon);
        float ym = ppf.getMetricY(y);
        float xm = ppf.getMetricX(x);

        for (PuffCarrier p : localPuffs) {
            p.stackConcentrations_fast(qcd, ym, xm, ppf.ens.ops);//p.stackConcentrations(c, null, z, y, x, ens);
        }//for puffs

        return qcd;
    }
          
    public PuffCalcCallable2(PuffPlatform pf, ArrayList<int[]> hw) {
        this.pf = pf;
        this.hw = hw;
    }

    public final static boolean MAP_PUFFS_ONLY = true;
    public final static boolean ALL_PUFFS = true;

    @Override
    public ArrayList<QCd> call() throws Exception {
        ArrayList<QCd> arr = new ArrayList<>();   
        for (int[] inds:this.hw) {
                int y = inds[0];
                int x = inds[1];
                QCd qcd = getCons(pf,y, x);
                qcd.addIndex(y,x);
                arr.add(qcd);
        }//inds  

       return arr;
    }
    
        static ArrayList<int[]>[] sortComputations(PuffPlatform pf) {
        FusionOptions ops = pf.ens.ops;
        int len = ops.cores*2;
        ArrayList<int[]>[] all = new ArrayList[len];
        for (int i =0;i<all.length;i++) {
            all[i]=new ArrayList<>();
        }
        
        int placement =0;
        for (int h =0;h< pf.in.H;h++) {
            for (int w =0;w<pf.in.W;w++) {
                if (pf.bins.sparseComputationsFor(h,w)) continue;
                
                int[] ind = {h,w};
                placement++;
                if (placement >=all.length) placement =0;
                all[placement].add(ind);
            }
        }
        return all;
    }
    
   
    }
    

