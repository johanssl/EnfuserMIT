/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;

/**
 *The idea of this class is to distribute active PuffCarrier instances
 * geographically into bins. Then when a computation of concentrations
 * using the puffs is being done, one can simply do the computation
 * inside a single bin and ignore the rest. This can drastically save
 * computational time.
 * 
 * Another purpose for this class is to handle the merging of matured
 * puffs within the bins. This can further reduce the computational
 * burden.
 * @author johanssl
 */
public class PuffBins {

    ArrayList<PuffCarrier>[][] arrays;//for concentration computations, the relevant list of puffs for the cell's location.
    public GeoGrid pCenterCounts;//used for GUI operations and stats at the moment
    int totalCenters=0;
    public GeoGrid pCenterCounts_relative;//used for GUI operations and stats at the moment
    AreaNfo in;
    final int H,W;
    MergingPool merger;//for merging of puffs into super puffs.
    final static int CENT_DIV =5;
    RadiusList rl = RadiusList.withFixedRads(3, 2, 2);
    public static boolean LARGER_RADIUS =false;
    public PuffBins(PuffPlatform pf) {
        Boundaries b = pf.in.bounds.clone();
        
        float res_m = (float)pf.ens.ops.perfOps.ppfBinRes_m(); 
        if (res_m<=0) {
            //automatic based on pf
            
            res_m = (float)(b.latRange()*degreeInMeters*0.05);
        }
        this.in = new AreaNfo(b,res_m,Dtime.getSystemDate());
        
        
        //center, using the PuffPlatform area and resolution.
        float[][] centers = new float[pf.in.H/CENT_DIV][pf.in.W/CENT_DIV];
        float[][] centers_rel = new float[pf.in.H/CENT_DIV][pf.in.W/CENT_DIV];
        pCenterCounts = new GeoGrid(centers,in.dt, pf.in.bounds);
        pCenterCounts_relative = new GeoGrid(centers_rel,in.dt, pf.in.bounds);
        
        if (in.H <3 || in.W < 3) {
             EnfuserLogger.log(Level.WARNING,PuffBins.class,"PuffBins grid size is weirdly small for: " 
                     + b.toText() +", res_m ="+res_m);
             this.in = new AreaNfo(b,3,3,Dtime.getSystemDate());
        }
        this.H = in.H;
        this.W = in.W;
        EnfuserLogger.log(Level.INFO,PuffBins.class,"PuffBins grid size: " + H + " x " + W +", resolution[m]="+(int)res_m);
        float potential = H * W / 9f;
        potential = 1f / potential;
        EnfuserLogger.log(Level.FINER,PuffBins.class,"\t time save potential: T => " + potential + "T");


        this.arrays = new ArrayList[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                arrays[h][w] = new ArrayList<>();
            }
        }

        this.merger = new MergingPool(pf.b_expanded,pf.ens.ops);
    }
    
    public AreaNfo getAreaDef() {
        return this.in;
    }

    

//private static final int RAD =1;
    /**
     * Distribute puffs to two types of grid: a) for concentration computations
     * - the ones that are relevant for the location b) to a merge grid, in
     * which multiple puffs can be combined into a single super puff. In the
     * merger grid the release height and maturity of the puff is considered so
     * that only similar puffs are being merged together.
     *
     * @param ppf the puffPlatform
     * @param z observation height in meters. This is used to pre-compute some
     * calculations for all puffs in the stack to avoid unnecessary computations
     * to be done multiple times for each puff.
     */
    public void distribute(PuffPlatform ppf, float z) {

        long t = System.currentTimeMillis();
        //clear all pointers
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                arrays[h][w].clear();
            }
        }
        //and puff centers
        this.totalCenters =0;
        for (int h = 0; h < pCenterCounts.H; h++) {
            for (int w = 0; w < pCenterCounts.W; w++) {
                this.pCenterCounts.values[h][w] = 0;
            }
        }

        //merging step
        if (merger.merges) {
            this.merger.clear();
            for (PuffCarrier pf : ppf.instances) {
                this.merger.put(pf, ppf);

            }
            //update the stack. Note: even if the merger is disabled this is safe to do.
            ppf.instances = merger.process(ppf);
        }//if merger
        
        //distribution step
        int distributed =0;
        int uniques =0;
        for (PuffCarrier pf : ppf.instances) {
            if (pf==null) continue;
             //to concentra
            if (pf instanceof MarkerCarrier) {
                continue;
            }
            if (pf.isInactive()) {
                continue;
            }
            uniques++;
            //determine effective radius
            int RAD = 2;
            float x = 0.25f;
            if (LARGER_RADIUS){
                RAD++;
                x =0.35f;
            }
            if (pf.usesMicroMeteorology) RAD =1;
            RAD+= (int)(x*pf.dist_total_m/this.in.res_m);//every 4 cells of travel increase radius by 1.

            //position, lat-lon to grid index.
            float lat = pf.currentLat(ppf);
            float lon = pf.currentLon(ppf);
            int ph = in.getH(lat);
            int pw = in.getW(lon);
            
            boolean added = false;
            //add to all buckets around this Radius.
            for (int i = -RAD; i <= RAD; i++) {
                for (int j = -RAD; j <= RAD; j++) {
                    if (FuserTools.ObjectGridOobs(arrays, ph+i, pw+j)) continue;
                        this.arrays[ph + i][pw + j].add(pf);
                        distributed++;
                        added = true;
                        //now this puff was distributed to a maximum of 9 cells.
                        //and the effective count radius of a puff is 1.5*cellRes_m; 
                }
            }

            if (added) {//this puff will contribute to computations
                pf.initForConcentrationCalcs(z, ppf.ens.ops);//prepare this puff for fast computations.
            }
            //puffCenters add
            boolean safeadd= pCenterCounts.SafeAddValue(lat, lon, 1);
            if (safeadd) this.totalCenters++;
        }//for puffs
        float ave = distributed / H / W;
        setupRelativeCenterGrid();
        
        
        
        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.INFO,PuffBins.class,"PuffBins distribution complete average stackSize: " 
                + (int) ave + ", puffBins distribution took " + (t2 - t) + "ms, total puffs"
                        + " in modelling domain ="+uniques + ". Merged = "
                +this.merger.mergedOutputPuffs +" from "+this.merger.mergedInputPuffs);
        
    }
    
    private void setupRelativeCenterGrid() {
        int cH = this.pCenterCounts.H;
        int cW = this.pCenterCounts.W;
        float aveCenters = (float)this.totalCenters/(cH*cW);
        if (aveCenters ==0) aveCenters =1;
        for (int h =0;h<cH;h++) {
            for (int w =0;w<cW;w++) {
                float rel = this.pCenterCounts.values[h][w]/aveCenters;
                this.pCenterCounts_relative.values[h][w]=rel;
            }
        }
        if (SPARSE_CELLS) {
        //some tolerance to nearby cells
        float[][] ndat = new float[cH][cW];
        float splasher = 1f/rl.elems.size();
        for (int h =0;h<cH;h++) {
            for (int w =0;w<cW;w++) {
                float val = this.pCenterCounts_relative.values[h][w];
                for (RadListElement elem: rl.elems) {
                    if (elem.oobs_HW_hw(cH, cW, h, w)) continue;
                    int hh = h+elem.hAdd;
                    int ww = w+elem.wAdd;
                    ndat[hh][ww]+=val*splasher;
                }//for rad1
            }//for w
        }//for h
        this.pCenterCounts_relative.values = ndat;
        }
    }
    
    public static boolean SPARSE_CELLS =true;
    boolean sparseComputationsFor(int h, int w) {
        if (!SPARSE_CELLS) return false;
        if (h%2 ==0 && w%2==0) return false;
        int ch = h/CENT_DIV;
        int cw = w/CENT_DIV;
        double ave = this.pCenterCounts_relative.getValueAtIndex_oobSafe(ch, cw);
        return ave <0.5;
    }
    
    /**
     * Get a list of puffs in a bin at the given location.
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     * @return array of PuffCarrier instances stored currently.
     */
    public ArrayList<PuffCarrier> getPuffList_latlon(float lat, float lon) {
        int h = in.getH(lat);
        int w =in.getW(lon);
        if (in.oobs(h, w)) return null;
        return this.arrays[h][w];
    }
    
     /**
     * Get a list of puffs in a bin at the given location.
     * @param lat
     * @param lon
     * @return array of PuffCarrier instances stored currently.
     */
    public ArrayList<PuffCarrier> getPuffList(float lat, float lon) {
        
        int h = in.getH(lat);
            if (h > H - 1) {
                h = H - 1;
            }
            if (h < 0) {
                h = 0;
            }
        int w = in.getW(lon);
            if (w > W - 1) {
                w = W - 1;
            }
            if (w < 0) {
                w = 0;
            }
        return this.arrays[h][w];

    }

    public ArrayList<PuffCarrier> getArray(int h, int w) {
        return this.arrays[h][w];
    }

}
