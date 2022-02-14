/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.netCdf.NcInfo;
import java.util.ArrayList;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class can fetch regional background datasets for given time and variable 
 * types. Main use-cases are for archive creation (allPollutants), animations,
 * and BG nesting for puff modelling.
 * @author johanssl
 */
public class RegionalGridProcessor {

    public static GeoGrid[] extractRegionalBGgeos(int typeInt, Dtime[] dts, FusionCore ens) {
        
        try {
            String regionalID = ens.ops.regionalBG_name;
            GeoGrid[] ggs = new GeoGrid[dts.length];
            float res_m = ens.ops.perfOps.metRes_m();
            DataPack dp = ens.datapack;
            //check structure
            VariableData vd = dp.getVarData(typeInt);
            if (vd == null) {
                return null;
            }

            GridSource silam;

            for (AbstractSource s : vd.sources()) {
                if (s.ID.contains(regionalID)) {
                    silam = (GridSource) s;
                    EnfuserLogger.log(Level.FINER,RegionalGridProcessor.class,
                            "regional MiniMap: " + ens.ops.VAR_NAME(typeInt) + " - " + s.ID);
                    //browse layers
                    int k = 0;
                    for (Dtime dt : dts) {
                        GeoGrid g = silam.getEnhancedGrid(dt, res_m);
                        ggs[k] = g;
                        k++;
                    }
                }//if SILAM
            }//for Source

            return ggs;

        } catch (Exception e) {
            return null;
        }
    }

    public static GeoGrid[] extractRegionalBGgeos(int typeInt, ArrayList<AreaFusion> afs,
            FusionCore ens) {
        Dtime[] dts = new Dtime[afs.size()];
        for (int i = 0; i < afs.size(); i++) {
            dts[i] = afs.get(i).dt;
        }
        return RegionalGridProcessor.extractRegionalBGgeos(typeInt, dts, ens);
    }

    public static ArchivePack extractSilamBGs(Dtime dt, FusionCore ens,
            AreaFusion af) {
        float res_m = ens.ops.perfOps.metRes_m();
        ArrayList<GeoGrid> bgs = new ArrayList<>();
        ArrayList<NcInfo> nfos = new ArrayList<>();
        DataPack dp = ens.datapack;
        String regionalID = ens.ops.regionalBG_name;
        
        for (VariableData vd : dp.variables) {
                if (vd == null) {
                    continue;
                }
                if (ens.ops.IS_METVAR(vd.typeInt)) {
                    continue;
                }

                AbstractSource s = vd.getSource(regionalID);

                if (s == null) {
                    continue;
                }

                {//interpolate
                    GridSource silam = (GridSource) s;
                    GeoGrid g = silam.getEnhancedGrid(dt, res_m);
                    if (g==null) {
                        EnfuserLogger.log(Level.WARNING, RegionalGridProcessor.class, 
                                "regional grids into archivePack encountered a null grid for "
                                + ens.ops.VAR_NAME(vd.typeInt)+ ", " + dt.getStringDate());
                        continue;
                    }
                    
                    bgs.add(g);
                    if (af != null) {
                        af.silamGrids[vd.typeInt] = g;
                    }
                }

                NcInfo nf = new NcInfo(
                        ens.ops.VARNAME_CONV(vd.typeInt),
                        ens.ops.UNIT(vd.typeInt),
                        ens.ops.LONG_DESC(vd.typeInt) + ", given by the regional model",
                        NcInfo.MINS,
                        dt,
                        ens.ops.areaFusion_dt_mins()
                );

                nfos.add(nf);
        }//for variables

        if (!bgs.isEmpty()) {
            return new ArchivePack(bgs, null, nfos);
        } else {
            return null;
        }

    }
    
        public static GeoGrid extractBG(String name, Dtime dt,
                FusionCore ens, int typeInt) {
        float res_m = ens.ops.perfOps.metRes_m();
        DataPack dp = ens.datapack;
        VariableData vd = dp.getVarData(typeInt);
                if (vd == null) {
                    return null;
                }
                if (ens.ops.IS_METVAR(vd.typeInt)) {
                    return null;
                }

                AbstractSource s = vd.getSource(name);
                if (s == null) {
                    return null;
                }

                    GridSource silam = (GridSource) s;
                    GeoGrid g = silam.getEnhancedGrid(dt, res_m);
                    if (g==null) {
                        EnfuserLogger.log(Level.WARNING, RegionalGridProcessor.class, 
                                "regional grids into archivePack encountered a null grid for "
                                + ens.ops.VAR_NAME(vd.typeInt)+ ", " + dt.getStringDate());
                        return null;
                    }          
        return g;
    }

    public static GeoGrid[] extracBG_Geos(int typeInt, ArrayList<AreaFusion> afs) {
        try {
            GeoGrid[] ggs = new GeoGrid[afs.size()];

            //browse layers
            int k = 0;
            for (AreaFusion af : afs) {
                GeoGrid g = af.silamGrids[typeInt];
                ggs[k] = g;
                k++;
            }
            return ggs;

        } catch (Exception e) {
            return null;
        }
    }

}
