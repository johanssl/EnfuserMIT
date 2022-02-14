/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.combiners;

import java.util.ArrayList;
import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell;
import org.fmi.aq.enfuser.core.gaussian.fpm.Footprint;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.core.receptor.EnvArray;
import static org.fmi.aq.enfuser.core.receptor.EnvProfiler.getWdirFan;
import org.fmi.aq.enfuser.core.receptor.PlumeBender;
import org.fmi.aq.enfuser.core.receptor.WeightManager;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;

/**
 *
 * @author johanssl
 */
public class PartialProfiler {
    
     /**
     * This method creates an environmental data array (EnvArray) that is
     * essence, is an emission footprint assessment processed into concentration
     * predictions at the location of interest (at Observation), under the
     * conditions described by Met.To do this, nearby emission sources are
 "scanned" towards the wind direction, also consider local effects caused
 by buildings and vegetation. There are two methods for this process and this one is aimed for "fusion
 points". This means that the emission assessment uses precomputed, hashed
 emission grids to gain fast access to local emission data.
     *
     * @param distRange defines the "zone" of analysis in terms of meters. The
     * full computation (model prediction) can be formed using multiple zones of
     * assessment: e.g., for each location of interest the zone 1 [0,300m], and
     * then a more sparse assessment for range [300,2000m]. At a final state for
     * model predictions the mid to long-range dispersion components are
     * gathered, effectively taken care of the farther "zones" that are not
     * covered by this method.
     * @param dates time elements as given by TZT, which relate to
     * Observation.dt.
     * @param ens FusionCore which contains all the datasets.
     * @param met meteorology
     * @param ob the observation, a FusionDummy defining the height, location
     * and time.
     * @param ba
     * @return the processed EnvArray.
     */
    public static EnvArray profile_fastArea(int[] distRange,  Dtime[] dates,
            FusionCore ens, Observation ob, Met met, BlockAnalysis ba) {

        EnvArray enva;
        if (met == null) {
            met = ens.getStoreMet(ob.dt, ob.lat, ob.lon);
        }
        enva = new EnvArray(ob, met, ens.ops,ba);

        

        boolean puffModellingAvailable = ens.puffModellingAvailable(ob.dt);
        float[] wdirs = getWdirFan(true, ens.ops, ob.lat, ob.lon,
                ob.dt, met, met.getWindSpeed_raw());
        boolean closeRangePart = distRange[0]==0;
        boolean addLongPlumes = (!puffModellingAvailable && closeRangePart);
        
        WeightManager man = new WeightManager(enva,ens,ob);
        if (man.locationNotCovered()) return enva;
        
        man.updateHEGs(true,ens);
        //check evaluation range, min and max distance for fpmForm.
        int[] adjDistRange = man.updateDistanceRange(ob,ens,distRange);//can check nearby emission distances.
        //Distance skip conditions
        int maxDist = adjDistRange[1];
        int minDist = adjDistRange[0];
        //limit maximum evaluation distance in case puffModelling is available
        if (puffModellingAvailable) {
            maxDist = (int) Math.min(ens.ops.perfOps.puffPlumeSwitchThreshold_m(), maxDist);
        }// avoid double-counting this way.
        
        
        //so, in case the profile starts from 0m range and puffModelling is not available,
        //then e.g., power plant contribution comes from a seprate Gaussian Plume -based treatment.
        //in operative modelling this is always 'false'
        enva.groundZeroAdditions(man.osmLayer(), ob.dt, dates, ens, addLongPlumes,met.getWdir());
        
        float gen_main_wFactor = 1f / wdirs.length;
        boolean forMeasurement =false;
        Footprint form = ens.selectForm(wdirs, forMeasurement);
        ArrayList<FootprintCell> cells = form.cells;
        PlumeBender pb = new PlumeBender();
       
//iterate over cells
        for (FootprintCell e : cells) {//THE MAIN LOOP=====================

            //DISTANCE SKIP CONDITIONS, note: ENV_TYPE_PUFF are unaffected since min = 0, max = Integer.MAX_VALUE;
            if (e.dist_m > maxDist) {
                break;//cells are sorted by distance so each cell after this would exceed this max.
            } else if (e.dist_m < minDist) {
                continue;//not in range just yet.
            }

            for (int dirIndex = 0; dirIndex < wdirs.length; dirIndex++) {// happens only once if NOT nullDir, because dirIncrement is over 360.
               float current_wd = wdirs[dirIndex];
               //just a tiny bit of randomization for wind direction to remove some area fusion artifacts due to discrete mathematics.

                //STEP 1: do operations that might modify the ROTATION of evaluation cell as well as the general weighting
                pb.setWindReversals(enva.ba, current_wd, e.dist_m, ens.ops);

                for (boolean mirror:pb.mirrors()) {
                    float stren = pb.getStrength(mirror);
                    current_wd = pb.getWD(mirror);
                    float gen_wFactor = gen_main_wFactor * stren;

                    //==== STEP 2 Manage rotations and coordinates. rotate the evaluation cells based on current_wd
                    float currLat = e.currentLat(ob.lat, (int) current_wd, ens.ops);//lat + (float)(hw_m[0]/degreeInMeters);
                    float currLon = e.currentLon(ob.lat, ob.lon, (int) current_wd, ens.ops);//lon + (float)(hw_m[1]/degreeInMeters/cos_midlat);
                    man.resetLocation(currLat, currLon, ens, e, current_wd, dirIndex);
                    if (man.locationNotCovered()) continue;
                    
                    float[] compact = man.getHEG_Element_hw_emPsm2();
                    if (compact == null) {
                        continue;
                    }
                    //=============/=/=/=/=/ SKIP TRIGGER /=/=/=/=/=/=================
                    enva.shortestEmDist_m = (short) Math.min(enva.shortestEmDist_m, e.dist_m);
                    man.prepareWeights(ens, gen_wFactor);
                    EmitterShortList.stackEmissionsIntoConcentrations_compressed(compact, man,ens);

                }//wdMirrors
            }//dirs
        } //cells
        return enva;
    }  
      
   //static volatile int modulo=0; 
}
