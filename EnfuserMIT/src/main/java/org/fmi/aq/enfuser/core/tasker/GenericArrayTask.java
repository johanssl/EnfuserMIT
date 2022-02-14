/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.tasker;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.enfuser.core.FusionCore.FUSION_HEIGHT_M;
import org.fmi.aq.enfuser.core.tasker.GenericArrayTask.ArrayResult;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.core.receptor.EnvProfiler;
import static org.fmi.aq.enfuser.solar.SolarBank.POWER;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_ELEVATION;
import org.fmi.aq.enfuser.ftools.Threader;

/**
 *
 * @author Lasse
 */
public class GenericArrayTask implements Callable<ArrayResult> {

    final FusionCore ens;
    final AreaFusion af;
    final int id;
    final int h;
    final Dtime dt;

    final Met met;
    final int typeInt;

    public final static int SOLAR = 0;
    public final static int ENV_TEST = 1;
//public final static int ENV_EXP =2;

    public final static float[] WDIRS = {
        0,
        90,
        180,
        270,};

    public final static int[] ARRAY_LENGTH = {1, 3, WDIRS.length};

    public GenericArrayTask(int h, Dtime dt, FusionCore ens, AreaFusion af, int taskID, Met met, int typeInt) {
        this.ens = ens;
        this.af = af;
        this.id = taskID;
        this.h = h;
        this.dt = dt;
        this.met = met;
        this.typeInt = typeInt;
    }

    @Override
    public ArrayResult call() throws Exception {

        if (id == SOLAR) {

            double lat, lon;
            float[][] dat = new float[af.W][ARRAY_LENGTH[id]];
            lat = af.bounds.latmax - af.dlat * h;

            for (int w = 0; w < af.W; w++) {
                lon = af.bounds.lonmin + af.dlon * w;

                OsmLayer ol = ens.mapPack.getOsmLayer( lat, lon);
                ByteGeoGrid bg = null;
                if (ol != null) {
                    bg = ol.getByteGeo(BGN_ELEVATION);
                }

                try {
                    dat[w][0] = (float) BlockAnalysis.getSolarPower(lat, lon, dt, ens, false, bg)[POWER];
                } catch (Exception e) {
                }
            }//w

            return new ArrayResult(dat, h);
        } else if (id == ENV_TEST) {

            double lat, lon;
            float[][] dat = new float[af.W][ARRAY_LENGTH[id]];
            lat = af.bounds.latmax - af.dlat * h;

            for (int w = 0; w < af.W; w++) {
                lon = af.bounds.lonmin + af.dlon * w;
                Observation fob = Observation.ObservationPointDummy(lat, lon, dt, FUSION_HEIGHT_M,ens.ops);
                
                try {

                    RP env = EnvProfiler.getFullGaussianEnv( ens, fob, met);
                    float exp = env.getExpectance(typeInt, ens);

                    dat[w][0] = exp;
                    dat[w][2] = env.logWind;

                    if (env.mirroringSum > 0) {
                        dat[w][1] = env.mirroringSum;
                    } else if (OSMget.isHighway(env.groundZero_surf)) {
                        dat[w][1] = -5;
                    } else if (OSMget.isBuilding(env.groundZero_surf)) {
                        dat[w][1] = -30;
                    }

                } catch (Exception e) {
                }
            }//w

            return new ArrayResult(dat, h);

        }

        return null;
    }

    public static float[][][] multiThreadTask(int id, FusionCore ens,
            AreaFusion af, Dtime dt, Met met, int typeInt) {

        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();

        //Create threads
        for (int h = 0; h < af.H; h++) {

            Callable cal = new GenericArrayTask(h, dt, ens, af, id, met, typeInt);
            FutureTask<GenericArrayTask> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);

        }

        // MANAGE callables           
        boolean notReady = true;
        while (notReady) {//
            notReady = false; // assume that ready 
            float readyCount = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } else {
                    readyCount++;
                }

            }//for futs

            EnfuserLogger.log(Level.INFO,GenericArrayTask.class,
                    "Threads ready: " + readyCount * 100.0 / futs.size() + "%");
            
            EnfuserLogger.sleep(300,GenericArrayTask.class);
        }// while not ready 

        //ready, collect data from strips
        float[][][] dat = new float[ARRAY_LENGTH[id]][af.H][af.W];
        for (FutureTask fut : futs) {
            try {
                // EnfuserLogger.log(Level.FINER,this.class,"future "+i +" updating the grid..." );
                ArrayResult ar = (ArrayResult) (fut.get());
                for (int k = 0; k < ARRAY_LENGTH[id]; k++) {
                    for (int w = 0; w < af.W; w++) {
                        dat[k][ar.h][w] = ar.dat[w][k];

                    }
                }
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

        }// for futures              

        Threader.executor.shutdown();
        return dat;
    }

    class ArrayResult {

        float[][] dat;

        int h;

        public ArrayResult(float[][] dat, int h) {
            this.dat = dat;
            this.h = h;
        }
    }

}
