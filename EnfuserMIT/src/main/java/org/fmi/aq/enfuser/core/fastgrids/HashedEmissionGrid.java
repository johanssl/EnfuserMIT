/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.fastgrids;

import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.tasker.HashTask;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.enfuser.core.receptor.RPstack;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class HashedEmissionGrid {

    public String ol_name;

    public HashMap<Integer, float[]> hash_emPsm2 = new HashMap<>();//5m hash
    public HashMap<Integer, float[]> hash_20m_emPsm2 = new HashMap<>();

    public Dtime dt;
    public final AreaInfo inf;

    final int lowH; 
    final int lowW; 
    
    public OsmLayer ol;
    float fillRate;
    float aveDatSize;
    final float cellAm2;

    float fillRate_low;
    float aveDatSize_low;
    public final static int RESDIV = 4;//5m base resolution and now it's going to be 20m => 

    RPstack dc = null;
    RPstack dc_low = null;
    DistanceCheck distCheck;
    public HashedEmissionGrid(OsmLayer ol, Dtime dt, FusionCore ens) {
        this.ol = ol;
        this.ol_name = ol.name;

        this.dt = dt.clone();
        this.inf = new AreaInfo(ol.b, ol.H, ol.W, this.dt, new ArrayList<>());

        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                    "Testing compressed emission arry hash creaton for a grid "
                            + "of the size of " + ol.H + "x" + ol.W);
        

        dc = new RPstack(ens.ops);
        dc_low = new RPstack(ens.ops);

        this.cellAm2 = ol.resm * ol.resm;
        aveDatSize = 0;
        this.lowW= ol.W/RESDIV;
        this.lowH = ol.H/RESDIV;

    }

    public void printoutDCs(FusionOptions ops) {
        GlobOptions g = GlobOptions.get();
        dc = new RPstack(ops);
        dc_low = new RPstack(ops);

        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "HashedEmissionGrid total sum test:");
        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w++) {
                int key = OsmLayer.getSpatialHashKey(h, w);
                float[] dat = hash_emPsm2.get(key);
                if (dat != null) {
                    EmitterShortList.stackToDC(this.cellAm2, dc, dat, null,ops);
                }

                //check sparse   
                if (h % RESDIV == 0 && w % RESDIV == 0) {
                    int lh = h / RESDIV;
                    int lw = w / RESDIV;
                    key = OsmLayer.getSpatialHashKey(lh, lw);
                    dat = this.hash_20m_emPsm2.get(key);

                    if (dat != null) {
                        EmitterShortList.stackToDC(this.cellAm2 * (RESDIV * RESDIV), dc_low, dat, null,ops);
                    }

                }

            }//for W
        }//for H

        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "Comparing emission total sums (individual plume point "
                        + "sources not taken into account)...");
        for (int q = 0; q < ops.VARA.Q_NAMES.length; q++) {
            for (int c : g.CATS.C) {
                EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,ops.VARA.Q_NAMES[q] + " - " + g.CATS.CATNAMES[c] 
                        + " => 5m sum = " + dc.stack_cq[c][q] + ", 20m sum = " + dc_low.stack_cq[c][q]);

            }//for c
        }//for q

    }


    public void initMT(FusionCore ens) {

        long t = System.currentTimeMillis();
        Dtime[] dts = ens.tzt.getDates(dt);

        HashTask.multiThreadHash_EMLIST(ens, this);
        HashTask.multiThreadHash_EMLIST_SPARSE(ens, this);

        long t2 = System.currentTimeMillis();
        fillRate = 100f * hash_emPsm2.size() / (ol.H * ol.W);

        aveDatSize = 0;

        EnfuserLogger.log(Level.INFO,HashedEmissionGrid.class,
                "Done! There are " + hash_emPsm2.size() / 1000000f 
                        + "M elements in hash. FillRate = " + fillRate + "%");
        
        EnfuserLogger.log(Level.INFO,HashedEmissionGrid.class,
                "Process took time " + (t2 - t) / 1000f + "s");
        fillRate_low = (RESDIV * RESDIV) * 100f * hash_20m_emPsm2.size() / (ol.H * ol.W);
        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "Done! There are " + hash_20m_emPsm2.size() / 1000000f 
                        + "M elements in hash. FillRate = " + fillRate_low + "%");


        //distance grid
        if (ens.ops.emissionDistanceChecking) {
            this.distCheck = new DistanceCheck(lowH,lowW,this.dt,this.ol.b,RESDIV,this.ol);
            distCheck.distanceGridInit(this);
        
        long t3 = System.currentTimeMillis();
         EnfuserLogger.log(Level.INFO,HashedEmissionGrid.class,
                "Process took time " + (t3 - t) / 1000f + "s, of which "
                        + (t3-t2)/ 1000f +"s was spent on distance checks.");
        }
        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,"HashedEmissionGrid.init: Done.");
    }

    



    public static void memoryTest(double lat, double lon, FusionCore ens, Dtime dt, int skipTest) {

        OsmLayer ol = ens.mapPack.getOsmLayer(lat, lon);
        HashMap<Integer, float[]> hash = new HashMap<>();

        Dtime[] dts = ens.tzt.getDates(dt);
        EmitterShortList tl = new EmitterShortList(dt, dts, ens, ol.b);
        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "Testing compressed emission arry hash creaton for a grid "
                        + "of the size of " + ol.H + "x" + ol.W);
        if (skipTest > 1) {
            EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                    "Note: only every " + skipTest + " element will be analysed in this test.");
        }
        if (skipTest == 0) {
            return;//infinite loop.
        }
        float aveDatSize = 0;
        int n = 0;
        int k = 0;
        long t = System.currentTimeMillis();
        Met met = null;
        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w += skipTest) {
                k++;
                float clat = (float) ol.get_lat(h);
                float clon = (float) ol.get_lon(w);
                byte osm = ol.luSurf[h][w];

                if (met == null || h % 10 == 0) {//ol has 5m resolution so this is every 50 meter.
                    met = ens.getStoreMet(dt, clat, clon);
                }

                tl.listEmissionDensities(met, clat, clon, ol, h, w,ens.ops);
                float[] dat = tl.compress();
                if (dat != null) {//something to add
                    int key = OsmLayer.getSpatialHashKey(h, w);
                    hash.put(key, dat);
                    aveDatSize += dat.length;
                    n++;
                }

            }//for w
        }//for h
        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "Done! There are " + hash.size() / 1000000f + "M elements in hash."
                + " FillRate = " + 100f * hash.size() / (ol.H * ol.W / (float) skipTest) + "%");
        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "Average datLine length: " + aveDatSize / n);
        EnfuserLogger.log(Level.FINER,HashedEmissionGrid.class,
                "Process took time " + (t2 - t) / 1000f + "s for " + k + " checks");

    }

    /**
     * Fetch element using the OsmLayer h-w indexing.
     *
     * @param h h index for osmLayer
     * @param w w dimension for osmLayer
     * @param e if null, then a coarser resolution density may be returned
     * depending on the distance specified in the FpmCell. The logic being, that
     * for longer distances a coarser representation of emissions yields more
     * stable modelling. With 5m resolution the finite amount of FpmCells can
     * cause hit-and-miss artifacts for modelling results.
     *
     * @return a compact emission array for the location (see: EmitterShortList)
     */
    public float[] getElement_hw_emPsm2(int h, int w, FootprintCell e) {

        //lower res data should be used?
        if (e != null && e.cellRes_m > 14 && e.dist_m > 80) {

            h /= RESDIV;
            w /= RESDIV;

            int key = OsmLayer.getSpatialHashKey(h, w);
            return this.hash_20m_emPsm2.get(key);
        }//Lower res

        int key = OsmLayer.getSpatialHashKey(h, w);
        return this.hash_emPsm2.get(key);
    }


    void visualizeDistanceGrid(FusionCore ens) {
        if (this.distCheck==null) {
            EnfuserLogger.log(Level.INFO, this.getClass(),
                    "Emission distance map not available.");
            return;
        }
      this.distCheck.visualize(ens,ol);
    }

}
