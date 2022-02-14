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
import java.util.HashMap;
import org.fmi.aq.essentials.netCdf.NetcdfHandle;
import java.io.File;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.VarConversion;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;

/**
 * Use a netCDF file (static multi-Q) as an emission grid. 
 * Unit assumption: kg/a per cell.
 * @author Lasse Johansson
 */
public class NCemitter extends AbstractEmitter {

    GeoGrid[] ems_µgPsm2;
    float[] qAverages;
    float[] qFilterThreshold;
    float[] totals_kg;
    public NCemitter(File file, FusionOptions ops) {
        super(ops);
        ems_µgPsm2 = new GeoGrid[VARA.Q_NAMES.length];
        qAverages = new float[VARA.Q_NAMES.length];
        totals_kg = new float[VARA.Q_NAMES.length];
        qFilterThreshold = new float[VARA.Q_NAMES.length];

        EnfuserLogger.log(Level.INFO,NCemitter.class,"Opening netCDF emitter (multiQ): " + file.getAbsolutePath());
        NetcdfHandle nc = new NetcdfHandle(file.getAbsolutePath());
        //check content
        HashMap<String, VarConversion> allConverts = VARA.allVC_byName;
        for (String var : nc.varNames) {
            VarConversion vc = allConverts.get(var);
            if (vc != null) {
                try {
                    int typeInt = vc.typeInt;
                    if (typeInt ==-1) continue;
                    //primary type?
                    if (VARA.isPrimary[typeInt]) {
                        EnfuserLogger.log(Level.FINER,NCemitter.class,
                                "\t found primary emission type: " + var + " => " + VARA.VAR_NAME(typeInt));
                        //read data
                        nc.loadVariable(var);
                        GeoGrid g = nc.getContentOnGeoGrid_2d();
                        float cellA_m2 = (float) g.cellA_m;
                        //scale values, as instructed in VarConversion

                        for (int h = 0; h < g.H; h++) {
                            for (int w = 0; w < g.W; w++) {
                                g.values[h][w] = vc.applyScaleOffset(g.values[h][w]);
                                totals_kg[typeInt]+= g.values[h][w];
                                //convert to area source taking into account cell area
                                g.values[h][w] /= cellA_m2;
                                g.values[h][w] *= (BILLION / (8760 * 3600));//annual kg to ug/s
                                //now the unit is [µg/s/m2]
                                //add raw data to averages
                                qAverages[typeInt] += g.values[h][w];
                            }
                        }

                        if (in == null) {
                            this.in = new AreaInfo(g.gridBounds, g.H, g.W,
                                    Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
                        }

                        EnfuserLogger.log(Level.FINER,NCemitter.class,"\t\t added GeoGrid with dimensions "
                                + g.gridBounds.toText() + ", H = " + g.H + " W = " + g.W);

                        ems_µgPsm2[typeInt] = g;//add
                        float seconds = (8760 * 3600);
                        float kg = totals_kg[typeInt];
                        float kgPs = kg/seconds;
                        EnfuserLogger.log(Level.INFO,NCemitter.class,"\t "+VARA.VAR_NAME(typeInt)
                                +": kg/a total = "+kg +", or "+kgPs +"kg per second (excluding LTM-profile)");
                        
                    }//if primary

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }//if varConversion found
        }//for nc variables

        EnfuserLogger.log(Level.FINER,NCemitter.class,
                "Average emission factors for " + file.getAbsolutePath());
        int n = in.H * in.W;
        for (int q : VARA.Q) {
            if (ems_µgPsm2[q] == null) {
                continue;
            }
            qAverages[q] /= n;
            this.qFilterThreshold[q] = qAverages[q] * ZERO_THRESHOLD_OF_AVERAGE;
            EnfuserLogger.log(Level.FINER,NCemitter.class,VARA.VAR_NAME(q) + ": " + qAverages[q] + " µgs-1m-2");
        }

        this.readAttributesFromFile(file);
        this.ltm = LightTempoMet.readWithName(ops, this.myName);

        //check the amount of empty cells (must evaluate all emission types for this)
        this.isZeroForAll = new boolean[in.H][in.W];

        float nonz = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                this.isZeroForAll[h][w] = true;//assume this to be empty initially.

                for (int q : VARA.Q) {
                    if (ems_µgPsm2[q] == null) {
                        continue;
                    }

                    float val = ems_µgPsm2[q].values[h][w];
                    if (val > this.qFilterThreshold[q]) {
                        //for at least one emission type the threshold is exceeded => nonEmpty cell.
                        nonz++;
                        this.isZeroForAll[h][w] = false;
                        break;
                    }

                }//for q (emission types)
            }//for w
        }//for h
        float zeros = n - nonz;

        EnfuserLogger.log(Level.FINER,NCemitter.class,"Zero emission content [%] = " + zeros * 100f / n);
        float nonz_frac_01 = nonz / n;
        EmitterCellSparser.editSparserBasedOnContent(file.getAbsolutePath(), nonz_frac_01, ltm);
        if (this.ltm.hasNormalizingSum()) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    myName +": LTM has a normalizing sum defined but this feature"
                            + " has been reserved only for annual-totals -type of gridded emitters!");
        }

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
            if (this.ems_µgPsm2[q] != null) {
                if (dat == null) {
                    dat = new float[VARA.Q_NAMES.length];
                }

                dat[q] = this.ems_µgPsm2[q].values[h][w];
            }
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
                    Q[q] += inc[q] *  in.cellA_m2 * UGPS_TO_KGPH;
                }
            }//for w
        }//for h

        return Q;
    }

}
