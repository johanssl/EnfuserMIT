/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.colmap.GeoImage;
import org.fmi.aq.essentials.gispack.osmreader.colmap.MonoColorMap;
import java.io.File;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;

/**
 * Use a monochromatic, grayscale image in KMZ-file as an emission grid.
 * Brightness map will be normalized to [0,1], assuming 1g/s emission rate for
 * max.value.
 *
 * @author Lasse Johansson
 */
public class BWemitter extends AbstractEmitter {

    GeoGrid ems_µgPsm2;
    final float cellA_m2;
    public static boolean REFINE = true;
    
        public BWemitter(File file, FusionOptions ops) {
        super(ops);
        GeoImage gi2 = GeoImage.loadFromKMZ(file.getName(), file.getParent() + FileOps.Z);
        MonoColorMap mcm = new MonoColorMap(gi2);
        EnfuserLogger.log(Level.FINER,BWemitter.class,"Black & White -emitter (BW) loaded successfully.");

        this.in = new AreaInfo(mcm.b, mcm.H, mcm.W, Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
        float[][] dat = new float[in.H][in.W];

        this.readAttributesFromFile(file);
        this.ltm = LightTempoMet.readWithName(ops, this.myName);
        

        
        this.ems_µgPsm2 = new GeoGrid(dat, in.dt, mcm.b);
        this.isZeroForAll = new boolean[in.H][in.W];
        this.cellA_m2 = in.cellA_m2;
        
        float maxVal = Float.MAX_VALUE * -1;
        float ave = 0;
        int n = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {

                //get brightness
                int val = mcm.getBrightness(h, w);
                if (val > maxVal) {
                    maxVal = val;
                }
                this.ems_µgPsm2.values[h][w] = val;
                ave += val;
                n++;
            }
        }//for h

        ave /= n;
        float thresh = ave * ZERO_THRESHOLD_OF_AVERAGE;
        //normalize max to 1, but filter the smallest values
        float nonz = 0;
        float zeros = 0;

        if (REFINE) {
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                if (this.ems_µgPsm2.values[h][w] > thresh) {
                    this.ems_µgPsm2.values[h][w] /= maxVal;
                    this.isZeroForAll[h][w] = false;
                    nonz++;
                } else {
                    this.ems_µgPsm2.values[h][w] = 0;
                    this.isZeroForAll[h][w] = true;
                    zeros++;
                }
            }
        }

        EnfuserLogger.log(Level.FINER,BWemitter.class,"Zero emission content [%] = " + zeros * 100f / n);
        float nonz_frac_01 = nonz / n;
        EmitterCellSparser.editSparserBasedOnContent(file.getAbsolutePath(), nonz_frac_01, ltm);
        this.ems_µgPsm2 =ltm.normalizeEmissionGridSum(ops,this.ems_µgPsm2,cellA_m2,
                LightTempoMet.NORMALIZE_UGPSM2,file.getAbsolutePath());
        }
    }
        

    @Override
    public float getEmHeight(OsmLocTime loc) {
        return (float) this.height_def;
    }

    @Override
    public ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs, PuffPlatform pf,
            Dtime[] allDates, FusionCore ens) {
            return null;//This type of emitter never releases individual puffs.
    }

    @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        float[] Q = new float[VARA.Q.length];
        for (int q : VARA.Q) {
            Q[q] = this.ems_µgPsm2.values[h][w];
        }
        return Q;
    }

    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
        float[] Q = new float[VARA.Q.length];

        for (int h = 0; h < this.ems_µgPsm2.H; h++) {
            for (int w = 0; w < this.ems_µgPsm2.W; w++) {
                for (int q : VARA.Q) {
                    float val = this.ems_µgPsm2.values[h][w] * this.cellA_m2 *UGPS_TO_KGPH;
                    Q[q] += val;
                }//for q

            }//for h
        }//for w

        return Q;
    }

    public GeoGrid grid() {
       return this.ems_µgPsm2;
    }



}
