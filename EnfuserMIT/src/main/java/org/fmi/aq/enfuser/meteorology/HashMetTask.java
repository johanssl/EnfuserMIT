/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ConcurrentHashMap;
import org.fmi.aq.enfuser.meteorology.HashMetTask.MetWrap;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLat;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLon;
import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;

/**
 *A class to handle multi-threaded processing of meteorological data.
 * Temporally, the addition and creation of Met instances can be done
 * independently. Therefore for selected time for a location,
 * the Met objects can be computed in a grid using multi-threading,
 * and then stored to HashedMet class afterwards.
 * 
 * @author johanssl
 */

public class HashMetTask implements Callable<MetWrap> {

Dtime dt;
Boundaries bounds;
HashedMet hmet;
DataCore ens;
    public HashMetTask(Boundaries b, Dtime dt, DataCore ens) {
        this.hmet = ens.hashMet;
        this.dt = dt;
        this.bounds=b;
        this.ens = ens;
    }

    @Override
    public MetWrap call() throws Exception {
        
         ConcurrentHashMap<Long,Met> mets = new ConcurrentHashMap<>();
        double latrange = (bounds.latmax - bounds.latmin);
        double lonrange = (bounds.lonmax - bounds.lonmin);

        double latrange_m = latrange * degreeInMeters;
        double lonrange_m = lonrange * degreeInMeters * hmet.cos_lat;

        int H = (int) (latrange_m / hmet.res_m);
        if (H < 1) {
            H = 1;
        }
        int W = (int) (lonrange_m / hmet.res_m);
        if (W < 1) {
            W = 1;
        }
        double dlat = latrange / H;
        double dlon = lonrange / W;
        
        int count = 0;
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {

                double lat = getLat(bounds, h, dlat);
                double lon = getLon(bounds, w, dlon);
                
                        long key = hmet.getKey(lat, lon);
                        double discr_lat = hmet.discretizedLat(lat);
                        double discr_lon = hmet.discretizedLon(lon);
                        Met met = Met.create(dt, discr_lat, discr_lon, ens);
                        mets.put(key, met);
                        count++;
                
            }//for w
        }//gor h

        EnfuserLogger.log(Level.FINER,HashMetTask.class,
                "Created/checked " + count + " met objects. for "
                    + bounds.toText_fileFriendly(3) +", "+ H +"x"+W+", "
                        +dt.getStringDate());
        
        
        return new MetWrap(mets,dt);

    }

    /**
     * Create all Met objects for a location and time span using multi-threading.
     * Temporally, the addition and creation of Met instances can be done
     * independently. Therefore for selected time for a location,
     * the Met objects can be computed in a grid using multi-threading,
     * and then stored to HashedMet class afterwards.
     * 
     * Note that the choice of met-resolution (performance options) and temporal
     * resolution of the modelling task affects the amount of created Met objects.
     * @param ens fusion core
     * @param b boundaries for Met creation
     * @param start start time
     * @param end end time
     */
    public static void createForSpan(DataCore ens, Boundaries b, Dtime start, Dtime end) {
        int layers =0;
        int mets =0;
        
        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();
        Dtime current = start.clone();
        
        int stepSecs = (int)ens.hashMet.res_min*60;
        current.addSeconds(-stepSecs);

        while (current.systemSeconds()<=end.systemSeconds()+ stepSecs) {
            current.addSeconds(stepSecs);
            
            Callable cal = new HashMetTask(b, current.clone(), ens);
            FutureTask<ConcurrentHashMap> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);
        }//while time
        
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

            if (K % 10 == 0) {
                EnfuserLogger.log(Level.FINE,HashMetTask.class,"HashedMet: Threads ready: " 
                        + readyCount * 100.0 / futs.size() + "%");
            }

             EnfuserLogger.sleep(300,HashMetTask.class);
        }// while not ready 

        //ready, collect data from strips
        for (FutureTask fut : futs) {
            try {
                MetWrap mw = (MetWrap) (fut.get());
                layers++;
                mets+=mw.mets.size();
                mw.putToHashedMet(ens.hashMet);
                
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
            }

        }// for futures              

        Threader.executor.shutdown();
        EnfuserLogger.log(Level.INFO,HashMetTask.class,"HashTask done for Meteorology. Layers = "+ layers 
                +", holding "+ mets +" met objects. ("+(mets/layers)+" on average)");
    }
    
 /**
  * A simple wrapper class to hold hashed Met-data, but also the timestamp
  * that makes it possible to add the data on HashedMet class.
  */   
 public class MetWrap {
     public ConcurrentHashMap<Long,Met> mets;
     public Dtime dt;
     public MetWrap(ConcurrentHashMap<Long,Met> mets, Dtime dt) {
         this.mets = mets;
         this.dt=dt;
     }

    private void putToHashedMet(HashedMet hashMet) {
       Integer key = hashMet.getTemporalKey(dt);
       hashMet.hash.put(key, mets);
    }
     
 }   

}
