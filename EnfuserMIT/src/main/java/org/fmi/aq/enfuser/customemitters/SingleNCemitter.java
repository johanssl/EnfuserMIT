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
import org.fmi.aq.essentials.netCdf.NetcdfHandle;
import java.io.File;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;

/**
 * This emitter type reads a single variable static netCDF file. The variable
 * must be named "emitter_data" and units should correspond to kg/A per cell.
 * Note: this "emission sum" does not yet point to any specific pollutant 
 * species - the LTM file should handle that part.
 * 
 * As a special feature ByteGeoEmitter can transform itself into this type
 * of emitter (saving a netCDF file to the emitter directory).
 *
 * @author Lasse Johansson
 */
public class SingleNCemitter extends AbstractEmitter {

    GeoGrid ems_µgPsm2;
    float qAverages;
    float qFilterThreshold;

    public final static String SINGLE_VAR ="emitter_data";
    public SingleNCemitter(File file, FusionOptions ops) {
        super(ops);
       
        EnfuserLogger.log(Level.FINER,SingleNCemitter.class,"Opening netCDF emitter (singleQ): " 
                + file.getAbsolutePath());
        NetcdfHandle nc = new NetcdfHandle(file.getAbsolutePath());
        //check content

                try {
                        //read data
                        nc.loadVariable(SINGLE_VAR);
                        GeoGrid g = nc.getContentOnGeoGrid_2d();
                        float cellA_m2 = (float) g.cellA_m;
                        //scale values, as instructed in VarConversion

                        for (int h = 0; h < g.H; h++) {
                            for (int w = 0; w < g.W; w++) {
                                g.values[h][w] /= cellA_m2;
                                g.values[h][w] *= (BILLION / (8760 * 3600));//annual g to seconds
                                qAverages += g.values[h][w];
                            }
                        }

                        if (in == null) {
                            this.in = new AreaInfo(g.gridBounds, g.H, g.W,
                                    Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
                        }

                        EnfuserLogger.log(Level.FINER,SingleNCemitter.class,
                                "\t\t added GeoGrid with dimensions "
                                + g.gridBounds.toText() + ", H = " + g.H + " W = " + g.W);

                        ems_µgPsm2 = g;//add
  

                } catch (Exception e) {
                    e.printStackTrace();
                }



        EnfuserLogger.log(Level.FINER,SingleNCemitter.class,
                "Average emission factors for " + file.getAbsolutePath());
        int n = in.H * in.W;

            qAverages /= n;
            this.qFilterThreshold = qAverages * ZERO_THRESHOLD_OF_AVERAGE;
            EnfuserLogger.log(Level.FINER,SingleNCemitter.class, qAverages + " µgs-1m-2");
        
        this.readAttributesFromFile(file);
        this.ltm = LightTempoMet.readWithName(ops, this.myName);

        //check the amount of empty cells (must evaluate all emission types for this)
        this.isZeroForAll = new boolean[in.H][in.W];

        float nonz = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                this.isZeroForAll[h][w] = true;//assume this to be empty initially.


                    float val = ems_µgPsm2.values[h][w];
                    if (val > this.qFilterThreshold) {
                        //for at least one emission type the threshold is exceeded => nonEmpty cell.
                        nonz++;
                        this.isZeroForAll[h][w] = false;
                        break;
                    }


            }//for w
        }//for h
        float zeros = n - nonz;

        EnfuserLogger.log(Level.INFO,SingleNCemitter.class,
                "Zero emission content [%] = " + zeros * 100f / n);
        float nonz_frac_01 = nonz / n;
        EmitterCellSparser.editSparserBasedOnContent(file.getAbsolutePath(), nonz_frac_01, ltm);
        this.ems_µgPsm2 =ltm.normalizeEmissionGridSum(ops,this.ems_µgPsm2,
                in.cellA_m2, LightTempoMet.NORMALIZE_UGPSM2,file.getAbsolutePath());
    }

    @Override
    public float getEmHeight(OsmLocTime loc) {
        return (float) this.height_def;
    }

    @Override
    public ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs,
            PuffPlatform pf, Dtime[] allDates,
            FusionCore ens) {
       return null;//This type of emitter never releases individual puffs.
    }

    @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        if (this.isZeroForAll[h][w]) {
            return null;
        }
        float[] dat = null;
        for (int q : VARA.Q) {
                if (dat == null) {
                    dat = new float[VARA.Q_NAMES.length];
                }

                dat[q] = this.ems_µgPsm2.values[h][w];

        }
        return dat;
    }

    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
        float[] Q = new float[VARA.Q.length];

        for (int h = 0; h < this.in.H; h++) {
            for (int w = 0; w < this.in.W; w++) {

                if (this.isZeroForAll != null && this.isZeroForAll[h][w]) {
                    continue;
                }
                float[] inc = getRawQ_µgPsm2(dt, h, w);
                if (inc == null) {
                    continue;
                }

                for (int q : VARA.Q) {
                    Q[q] += inc[q] * in.cellA_m2 * UGPS_TO_KGPH;
                }

            }//for w
        }//for h
        return Q;
    }

}
