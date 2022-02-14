/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.customemitters.BGEspecs;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.netCdf.NcInfo;
import org.fmi.aq.essentials.netCdf.NetCDFout2;

/**
 * This emitter type has been designed for osmLayers: it can be created with a
 * combination of ByteGeoGrid and a BGEspecs instance that holds information on
 * how to transform the byteGeoGrid into emitter.
 *
 * Since this emitter is not loaded from file there are some notable expections:
 * - This emitter does not require (but still can use) LTM-profile loaded from
 * file. - Parsing of attributes from filename is not done in a similar way that
 * is done for other emitters. - Basic unit for gridded data is 'kg per year per
 * cell'.
 *
 * @author johanssl
 */
public class ByteGeoEmitter extends AbstractEmitter {

    GeoGrid ems_kgPerA;
    public final float kgA_TO_µgPsm2;
    final BGEspecs bges;
    public ByteGeoEmitter(FusionOptions ops, ByteGeoGrid bg,
            BGEspecs bges, OsmLayer ol) {
        super(ops);
        this.bges = bges;
        File prox = createProxyFile(ops, bges.olKey_filesafe(), bges.catS, bges.height_m, bg.gridBounds);
        EnfuserLogger.log(Level.FINER,ByteGeoEmitter.class,
                "ByteGeoEmitter name proxy: " + prox.getAbsolutePath());
        this.readAttributesFromFile(prox);
        this.in = new AreaInfo(bg.gridBounds, bg.H, bg.W, Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
        this.ems_kgPerA = bg.convert();
        this.isZeroForAll = new boolean[in.H][in.W];

        File test = this.buildLTM_File(bges, ops);
        if (!test.exists()) {
            EnfuserLogger.log(Level.INFO,ByteGeoEmitter.class,
                    "LTM does not exist: " + test.getAbsolutePath() + ", creating it now.");
            this.ltm = LightTempoMet.buildDummy(bges.cellSparser,ops, test);
        } else {
            this.ltm = LightTempoMet.create(ops,test);
        }
        float yearSecs = 8760 * 3600f;
        //1 kg is equal to billion µg
        this.kgA_TO_µgPsm2 = BILLION / yearSecs / (float) bg.cellA_m2;

        float maxVal = Float.MAX_VALUE * -1;
        float ave = 0;
        int n = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {

                //get brightness
                double val = bg.getValueAtIndex(h, w);
                if (val > maxVal) {
                    maxVal = (float) val;
                }
                ave += val;
                n++;
            }
        }//for h

        ave /= n;
        float thresh = ave * ZERO_THRESHOLD_OF_AVERAGE;
        //normalize max to 1, but filter the smallest values
        int zeros = 0;
        float nonz_frac_01 = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                double val = bg.getValueAtIndex(h, w);
                if (val > thresh) {
                    this.isZeroForAll[h][w] = false;
                    nonz_frac_01++;
                } else {
                    this.isZeroForAll[h][w] = true;
                    zeros++;
                }
            }
        }

        EnfuserLogger.log(Level.FINER,ByteGeoEmitter.class,
                "ByteGeoEmitter: zero emission content [%] = " + 100f / (bg.H * bg.W) * zeros);
        nonz_frac_01 = nonz_frac_01 / (float) (bg.H * bg.W);//now it's in between [0,1]
        EmitterCellSparser.editSparserBasedOnContent(prox.getAbsolutePath(), nonz_frac_01, ltm);
        fromOsmLayerToNC(ol, ops,  bges);
        
        this.ems_kgPerA =ltm.normalizeEmissionGridSum(ops,this.ems_kgPerA,in.cellA_m2,
                LightTempoMet.NORMALIZE_KGPA,bges.nameID);

    }
    
    private String ncName(BGEspecs bges) {
       //"SINGULAR2D_myname_cat_heightDef_tmin_tmax_latmin_latmax_lonmin_lonmax_.xxx" 
       int height = bges.height_m;
       Boundaries b = this.ems_kgPerA.gridBounds;
       String boundsS = b.toText_fileFriendly(3);
       return "SINGLENC_"+this.myName+"_"
               +this.CATS.CATNAMES[cat]+"_"+height +"m_tmin_tmax_" + boundsS +".nc"; 
       
    }
    
    public boolean hasNetCDFversion(FusionOptions ops) {
         String rootdir = ops.getEmitterDir();
            String outfilename=ncName(bges);
            File test = new File(rootdir+outfilename);
            return test.exists();
    }
    
    private void fromOsmLayerToNC(OsmLayer ol, FusionOptions ops, BGEspecs bges) {
        //ERCFarguments args = ops.getArguments();
        boolean ncDump = true;//this works so well that let's have this enabled all times.
        if (ncDump) {
            
            String rootdir = ops.getEmitterDir();
            String outfilename=ncName(bges);
            File test = new File(rootdir+outfilename);
            if (test.exists()) {//already there
                return;
            }
            
            EnfuserLogger.log(Level.INFO, this.getClass(), "disabling " + myName 
                    +" byteGeo emitter from" + ol.name);
            String key = bges.olKey;
            
                     
            ArrayList<GeoGrid> gg = new ArrayList<>();
            ArrayList<NcInfo> infos = new ArrayList<>();
            gg.add(this.ems_kgPerA);
            NcInfo in = new NcInfo(SingleNCemitter.SINGLE_VAR,
                    "generic emissions as [kg/a]", SingleNCemitter.SINGLE_VAR);
            infos.add(in);

        //all done, produce netCDF file.
         EnfuserLogger.log(Level.INFO, this.getClass(),
                 "filename will be: " + test.getAbsolutePath());
        try {
            NetCDFout2.writeNetCDF_statc(false, infos, gg, new ArrayList<>(), test.getAbsolutePath());
            //ok, remove and save
            bges.setLoadAsEmitter(false);
            ol.layerEms_specs.put(key,bges);
            ol.reSave();
            
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
            
        }//if ncDump
    }
    

    private File createProxyFile(FusionOptions ops, String nameID, String catS, int height_m, Boundaries b) {
        //"SINGULAR2D_myname_cat_heightDef_tmin_tmax_latmin_latmax_lonmin_lonmax_.xxx" 

        int emitterType = BYTEGEO_2D;
        String[] spline = AbstractEmitter.SPECS[emitterType];
        String fnam
                = spline[SP_TYPE] + "_"
                + nameID + "_"
                + catS + "_"
                + height_m + "m_"
                + "tmin_tmax_"
                + editPrecision(b.latmin, 3) + "_" + editPrecision(b.latmax, 3) + "_"
                + editPrecision(b.lonmin, 3) + "_" + editPrecision(b.lonmax, 3)
                + spline[SP_FORMAT];

        return new File(ops.getEmitterDir() + fnam);
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
            Q[q] = (float) (this.ems_kgPerA.getValueAtIndex(h, w)) * kgA_TO_µgPsm2;
        }
        return Q;
    }

    private File buildLTM_File(BGEspecs bges, FusionOptions ops) {
        String name = "xprofile_LTM_" + bges.olKey_filesafe() + "-" + bges.catS + ".csv";
        return new File(ops.getEmitterDir() + name);
    }

    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
        float[] Q = new float[VARA.Q.length];

        for (int h = 0; h < this.ems_kgPerA.H; h++) {
            for (int w = 0; w < this.ems_kgPerA.W; w++) {
                for (int q : VARA.Q) {
                  
                    float val = (float) this.ems_kgPerA.getValueAtIndex(h, w) /8760;
                    Q[q] += val;
                }//for q   
            }//for h
        }//for w

        return Q;
    }

}
