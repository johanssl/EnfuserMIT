/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

import java.util.ArrayList;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.meteorology.HashedMet;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.reader.DefaultLoader;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.Masks.MapPack;

/**
 * The data core is a container that holds structured data, such as gridded
 * sources, stationary sources and pre-processed meteorological data, GIS-data etc.
 * 
 * The main purpose of this class is to provide a super type for FusionCore.
 * With the help of this, it is possible to use the software in supporting
 * role when all the core-packages have been removed.
 * 
 * 
 * @author johanssl
 */
public class DataCore {
    
     /*Lasse Johansson 2011-2021 FMI
	 * This class controls model instances and combines their estimation values etc.
     */
    public static String VERSION_INFO = "FMI-ENFUSER 2.11.1 (updated 16.2.2022)";
    
    public MapPack mapPack;
    public DataBase DB;
    public FusionOptions ops;
    public DataPack datapack;
    public HashedMet hashMet;
    public TZT tzt;  
    public GlobOptions g;
    public DataCore() {
        this.g = GlobOptions.get();
    }
    
    public static DataCore loadInstance(FusionOptions ops, boolean withMaps,
            ArrayList<String> feedNameFilter) {
        
       DataCore ens = new DataCore();
       ens.tzt = new TZT(ops);
       ens.ops = ops;
       ens.DB = new DataBase(ops);

       DefaultLoader loader = new DefaultLoader(ops);
       
       if (withMaps) {
        loader.loadMaps(ops.boundsExpanded(false),false);
        ens.mapPack = loader.getMaps();
       }
       
        ens.DB = loader.getDB();
        loader.loadDataPack(false,feedNameFilter);
        ens.datapack = loader.getMerged();
        ens.hashMet = new HashedMet(ens.datapack,ops, ops.perfOps.metRes_m(),
                (float) ops.areaFusion_dt_mins());
        ens.datapack.finalizeStructures(ens);
       
       return ens;
    }
  
    
     public Met getStoreMet(Observation o) {
      return this.hashMet.getStoreMet(o.dt, o.lat, o.lon, this);
    }
     
     public Met getStoreMet(Dtime dt, double lat, double lon) {
        return this.hashMet.getStoreMet(dt, lat, lon, this);
    } 
    
}
