/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import static org.fmi.aq.enfuser.meteorology.RoughPack.RL_DEFAULT;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.date.Dtime;

/**
 *This interface is for accessing pre-computed wind profiling information on
 * a local scale. During modelling task, physically realistic high-resolution
 * wind fields can be too costly to produce. 
 * 
 * By exploiting the static properties
 * of the target area (elevation, vegetation, buildings, surface types)
 * It is possible to predict local wind conditions as a function of "low resolution" 
 * wind direction. To actually do this, there are several approaches and this
 * interface defines how any of such adaption should connect to the model. This
 * includes:
 *  - The creation process for osmLayers
 *  - Loading data for a given area
 *  - obtaining wind data as a function of location, elevation and low-res
 *    meteorology
 * 
 * @author johanssl
 */
public class WindProfiler {

    public static WindProfiler getInstanceForMeasurementFusion(DataPack datapack, FusionOptions ops) {
        WindProfiler wp = new WindProfiler();
        wp.initWindMeasurements(datapack, ops);
        return wp;
    }

    public WindProfiler() {
        
    }
    
    private void initWindMeasurements(DataPack dp, FusionOptions ops) {
        
    }
    
    public void windFieldFusionForPeriod(Dtime start, Dtime end, FusionCore ens) {

    }
    
    public float[] getMeasurementBasedWindOffsets(FusionCore ens, Dtime dt) {
       return new float[]{0,0};
    }
    

  /**
  * Loads the object that is responsible for pre-computed wind profiling.
  * @param bounds define the area of interest: latmin,latmax,lonmin, lonmax.
  * @param mapDirectory path for regional map directory, e.g., D:/Enfuser/data/Regions/Finland/maps/.
  * Wind profiles are intended to cover the the same area as the osmLayers and therefore the wind grids
  * should be located under the respecive /maps/ directory.
  */  
    public void loadForArea(double[] bounds, String mapDirectory) {
       
    }

    /**
    * Fetch applicable grid dataset from the loaded ones.
     * @param osmLayerName short osmLayer name String that is also present in the
     * osmLayer file name. For example: The name of "pks_60.106_60.368_24.562_25.221_osmLayer.dat"
     * is "pks".
     * @return true if applicable grid was found, otherwise return false.
    */
    public boolean findApplicable(String osmLayerName) {
        return false;
    }

    /**
    * Fetch applicable grid dataset from the loaded ones.
    * @param lat latitude coordinate
    * @param lon longitude coordinate
    * @return true if applicable grid was found, otherwise return false.
    */
    public boolean findApplicable(double lat, double lon) {
        return false;
    }

    /**
    * Get the precomputed dynamic roughness length value that is used for wind
    * profiling.
    * @param wdir wind direction (degrees, from. 0 for North, 90 for East etc)
    * @param lat the latitude coordinate
    * @param lon the longitude coordinate
    * @param reassessGrid if true, then the available grids are browsed
    * and the one applicable for the given coordinates is set as the active grid.
    * If false, then the active grid needs to cover these coordinates.
    * @param RL_scaler additional modifier for the roughness length [0,1].
    * Use a value of 1 as default (no external reductions)
    * @return dynamic surface roughness length value [m].
    */ 
    public float getRoughness(int wdir, float lat, float lon, boolean reassessGrid,
            float RL_scaler) {
        return RL_DEFAULT;
    }

    /**
    * Return true in case after loading there are no applicable pre-computed
    * data to base the wind profiling on for the modelling area.
    * @return true if empty, otherwise false.
    */
    public boolean isEmpty() {
        return true;
    }

  /**
  * Launch the grid data creation process for the given area (defined with osmLayer
  * names). This process should create and store precomputed data to file so that
  * the LoadForArea() can be used. In this case, the creation process
  * targets ONLY the areas that do not yet have these precomputed datasets.
  * 
  * @param mapDir directory where osmLayer data is stored (usually a .../Regions/'regionname'/maps/)
  * @param olName_short the short osmLayer name identification. For example: for
  * pks_60.106_60.368_24.562_25.221_osmLayer.dat the short name is "pks"
  * 
  * @param resScaler resolution divisor (reducer) based on osmLayer resolution.
  * A value of 1 is the default value, and should result in a grid structure that
  * is equal to the one the osmLayer has (usually 5m).
  */ 
    public void createIfMissing(String mapDir, String olName_short, float resScaler) {
       
    }

  /**
  * Launch the grid data creation process for the given area (defined with osmLayer
  * names).This process should create and store precomputed data to file so that
  * the LoadForArea() can be used. 
  * 
  * @param dir directory where the targeted osmLayer is located.
  * @param olname local osmLayer file name, e.g.
  * pks_60.106_60.368_24.562_25.221_osmLayer.dat.
  * 
  * @param resScaler resolution divisor (reducer) based on osmLayer resolution.
  * A value of 1 is the default value, and should result in a grid structure that
  * is equal to the one the osmLayer has (usually 5m).
  * @param imgs if true, additional debugging image output is provided after
  * the creation process (default value: false)
  */ 
    public void createForArea(String dir, String olname, float resScaler, boolean imgs) {
      
    }

    public void compareUwithMeasurements(FusionCore ens, String statsCrunchDir) {
       
    }

    public void printCorrections(FusionOptions ops) {
        
    }


}
