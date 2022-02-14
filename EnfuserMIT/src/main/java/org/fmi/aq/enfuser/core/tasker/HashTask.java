/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.tasker;

import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.core.FusionCore.FUSION_HEIGHT_M;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.core.receptor.EnvProfiler;
import org.fmi.aq.enfuser.core.fastgrids.HashedEmissionGrid;
import static org.fmi.aq.enfuser.core.fastgrids.HashedEmissionGrid.RESDIV;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.gaussian.puff.MapEmitterCell;
import org.fmi.aq.enfuser.core.FusionCore;

/**
 *
 * @author johanssl
 */
public class HashTask implements Callable<HashMap> {

    final FusionCore ens;
    final AreaInfo inf;
    final int id;
    final int h0;
    final int hmax;
    final OsmLayer specific_ol;
    final HashedEmissionGrid heg;
    public final static int TASK_ENV = 0;
    public final static int TASK_EMSOURCE_PUFF = 1;
    public final static int TASK_BLOCKA = 2;
    public final static int TASK_EMLIST = 3;
    public final static int TASK_EMLIST_SPARSE = 4;
    private final static int[] WAIT_MS = {300, 10, 100, 100, 100};
    private final static boolean[] PROGRESS_SOUT = {true, false, true, false, false};

    public HashTask(int h, FusionCore ens, AreaInfo inf, int taskID, int hmax, OsmLayer ol, HashedEmissionGrid heg) {
        this.ens = ens;
        this.inf = inf;
        this.id = taskID;
        this.h0 = h;
        this.hmax = hmax;
        this.specific_ol = ol;
        this.heg = heg;
        //this.dt = dt;
    }

    public static HashMap<String, Object> multiThreadHash_ENVS(FusionCore ens, AreaInfo inf, Met met) {
        return multiThreadHash(TASK_ENV, ens, inf, met, null, null);
    }

    public static HashMap<String, Object> multiThreadHash_EMPUFF(FusionCore ens, AreaInfo inf) {
        return multiThreadHash(TASK_EMSOURCE_PUFF, ens, inf, null, null, null);
    }

    public static void multiThreadHash_BLOCKS(FusionCore ens, AreaInfo inf, BlockAnalysis[][] bas) {
        HashMap<String, Object> hash = multiThreadHash(TASK_BLOCKA, ens, inf, null, null, null);
        String[] temp;
        for (String key : hash.keySet()) {
            temp = key.split("_");
            int h = Integer.valueOf(temp[0]);
            int w = Integer.valueOf(temp[1]);
            BlockAnalysis ba = (BlockAnalysis) hash.get(key);
            bas[h][w] = ba;
        }
    }

    public static void multiThreadHash_EMLIST(FusionCore ens, HashedEmissionGrid heg) {
        HashMap<String, Object> hash = multiThreadHash(TASK_EMLIST, ens, heg.inf, null, heg.ol, heg);

        for (String key : hash.keySet()) {
            int ikey = Integer.valueOf(key);
            heg.hash_emPsm2.put(ikey, (float[]) hash.get(key));
        }
    }

    public static void multiThreadHash_EMLIST_SPARSE(FusionCore ens, HashedEmissionGrid heg) {
        HashMap<String, Object> hash = multiThreadHash(TASK_EMLIST_SPARSE, ens, heg.inf, null, heg.ol, heg);

        for (String key : hash.keySet()) {
            int ikey = Integer.valueOf(key);
            heg.hash_20m_emPsm2.put(ikey, (float[]) hash.get(key));
        }
    }

    private static HashMap<String, Object> multiThreadHash(int id, FusionCore ens, AreaInfo inf,
            Met met, OsmLayer ol, HashedEmissionGrid heg) {

        HashMap hash = new HashMap<>();// store output here   
        Threader.reviveExecutor();
        ArrayList<FutureTask> futs = new ArrayList<>();  

        //Create threads that does at least af.H x af.W individual processess. There will be af.H threads.
        int n = Math.max(1, inf.H / ens.ops.cores / 4);//e.g., there will be 40 threads for a large task with 10 cores.
        if (id == TASK_EMLIST_SPARSE) {
            n = (int) (n / RESDIV) * RESDIV;
            if (n < RESDIV) {
                n = RESDIV;//now it will be multiple of 4 (5m => 20m) 
            }
        }

        for (int h = 0; h < inf.H + n - 1; h += n) {

            //the last iteration is the problem, since usually H%n !=0   
            int hmax = h + n;
            if (hmax > inf.H - 1) {
                hmax = inf.H - 1;
            }
            if (hmax == h) {
                continue;
            }

            Callable cal = new HashTask(h, ens, inf, id, hmax, ol, heg);
            FutureTask<GenericArrayTask> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);

        }

        // MANAGE callables           
        boolean notReady = true;
        while (notReady) {//wait until every thread is finished
            notReady = false; // assume ready - if even one thread is NOT, then change this status
            float readyCount = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;// not yet then.
                } else {
                    readyCount++;
                }

            }//for futs

            if (PROGRESS_SOUT[id]) {
                EnfuserLogger.log(Level.FINER,HashTask.class,"Threads ready: " 
                        + readyCount * 100.0 / futs.size() + "%");
            }

          EnfuserLogger.sleep(WAIT_MS[id],HashTask.class);//wait a bit
        }// while not ready 

        //ready - collect data from all finished subtasks
        for (FutureTask fut : futs) {
            try {

                HashMap<String, Object> map = (HashMap) (fut.get());
                hash.putAll(map);//combine

            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

        }// for futures              

        Threader.executor.shutdown();
        return hash;
    }

    @Override
    public HashMap<String, Object> call() throws Exception {
        HashMap hash = new HashMap<>();
        Dtime[] dates = ens.tzt.getDates(inf.dt);

        EmitterShortList tl = null;
        if (this.id == TASK_EMLIST) {
            tl = new EmitterShortList(inf.dt, dates, ens, inf.bounds);
        }

        if (this.id == TASK_EMLIST_SPARSE) {//special method for this one. Note: TASK_EMLIST needs to be processed before this one!
            HashMap<Integer, float[]> elems = new HashMap<>();

            ArrayList<float[]> compacts = new ArrayList<>();
            Boundaries b_exp = ens.ops.boundsExpanded(false);
            for (int h = this.h0; h < this.hmax; h += RESDIV) {
                for (int w = 0; w < this.specific_ol.W; w += RESDIV) {

                    //Boundaries check
                    double lat = this.specific_ol.get_lat(h);
                    double lon = this.specific_ol.get_lon(w);
                    if (!b_exp.intersectsOrContains(lat, lon)) {
                        continue;//cannot be relevant (skip)
                    }
                    compacts.clear();

                    //seach the lower resolution area for high-res data elements
                    for (int i = 0; i < RESDIV; i++) {
                        for (int j = 0; j < RESDIV; j++) {

                                int ch = h + i;
                                int cw = w + j;
                                float[] compact = this.heg.getElement_hw_emPsm2(ch, cw, null);
                                if (compact != null) {//found some!
                                    compacts.add(compact);
                                }
                        }//for i
                    }//for j

                    if (compacts.isEmpty()) {
                        continue;
                    }

                    int lh = h / RESDIV;
                    int lw = w / RESDIV;
                    //at least one element
                    String key = "" + OsmLayer.getSpatialHashKey(lh, lw);
                    Object o = EmitterShortList.combineCompactDensities(compacts, RESDIV, elems,ens.ops);
                    if (o != null) {
                        hash.put(key, o);
                    }
                }//for sparse w
            }//for sparse h 

            return hash;
        }//if TASK_EMLIST_SPARSE   

        float lat, lon;
        for (int h = h0; h < hmax; h++) {//iterate over all h
            lat = (float) inf.getLat(h);

            for (int w = 0; w < inf.W; w++) {
                lon = (float) inf.getLon(w);

                Object o = null;
                String key = null; //h,w,met_i as an unique key (3-dim)

                try {

                    if (this.id == TASK_ENV) {
                        Observation fob = Observation.ObservationPointDummy(lat, lon, inf.dt, FUSION_HEIGHT_M,ens.ops);
                        RP env = EnvProfiler.getFullGaussianEnv(ens, fob, null);
                        o = env;
                        key = h + "_" + w; //h,w,met_i as an unique key (3-dim)

                    } else if (this.id == TASK_EMSOURCE_PUFF) {
                        MapEmitterCell mec = MapEmitterCell.profileEmissions(ens.customCellArray,
                                 ens, (float) lat, (float) lon, inf.dt, dates);
                        o = mec;
                        key = h + "_" + w;

                    } else if (this.id == TASK_BLOCKA) {

                        OsmLayer ol = ens.mapPack.getOsmLayer(lat, lon);
                        BlockAnalysis ba = new BlockAnalysis(ol, ens.ops, lat, lon, null);
                        o = ba;
                        key = h + "_" + w;

                    } else if (this.id == TASK_EMLIST) {
                        Met met = ens.getStoreMet(inf.dt, lat, lon);
                        //Boundaries check
                        if (!ens.ops.boundsExpanded(false).intersectsOrContains(lat, lon)) {
                            continue;//cannot be relevant (skip)
                        }
                        if (tl == null) {
                            continue;//should never happen
                        }
                        tl.listEmissionDensities(met, lat, lon,this.specific_ol, h, w,ens.ops);
                        float[] dat = tl.compress();
                        if (dat != null) {//something to add
                            key = OsmLayer.getSpatialHashKey(h, w) + "";
                            o = dat;
                        }

                    }

                } catch (Exception e) {
                }

                if (o != null && key != null) {//store if process was successful
                    hash.put(key, o);
                }

            }//w
        }//for h

        return hash;

    }

}
