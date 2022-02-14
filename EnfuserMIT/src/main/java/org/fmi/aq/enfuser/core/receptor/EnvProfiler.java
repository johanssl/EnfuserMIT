/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.customemitters.EmitterShortList;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell;
import org.fmi.aq.enfuser.core.gaussian.fpm.Footprint;
import java.util.ArrayList;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.enfuser.core.combiners.PartialProfiler.profile_fastArea;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class EnvProfiler {

    final static int LOC_SPECS_MIN_RING = 3;
    public final static int TEN_KM = 10000;
    public static int LIMIT_BG_REP_km = 1;
    public final static int[] FULL_RANGE = {0, Integer.MAX_VALUE};
    
    public static RP getFullGaussianEnv(FusionCore ens, Observation ob,  Met met) {

        int[] range = FULL_RANGE;
        EnvArray enva;
        if (ob.fusionPoint()) {
            enva = profile_fastArea(range, ob.dates(ens), ens, ob, met,null); 
        } else {
            enva = profile_obs(range,  ens, ob, met);
        }
        return  new RP(enva, ob.lat, ob.lon, ens, ob.dt, ob.dates(ens));
    }

    


    /**
     * This method creates an environmental data array (EnvArray) that is
     * essence, is an emission footprint assessment processed into concentration
     * predictions at the location of interest (at Observation), under the
     * conditions described by Met.To do this, nearby emission sources are
     * "scanned" towards the wind direction, also consider local effects caused
     * by buildings and vegetation. There are two methods
     *
     * for this process and this one is aimed for true Observations, actual
     * measurements. This means that the emission assessment does NOT use
     * precomputed, hashed emission grids to gain fast access to local emission
     * data. This makes this assessment less time dependent easy to manage and
     * more flexible.
     *
     * @param distRange defines the "zone" of analysis in terms of meters. The
     * full computation (model prediction) can be formed using multiple zones of
     * assessment: e.g., for each location of interest the zone 1 [0,300m], and
     * then a more sparse assessment for range [300,2000m]. At a final state for
     * model predictions the mid to long-range dispersion components are
     * gathered, effectively taken care of the farther "zones" that are not
     * covered by this method.
     *
     * @param ens FusionCore which contains all the datasets.
     * @param met meteorology
     * @param ob the observation, a defining the height, location and time.
     * @return the processed EnvArray.
     */
    public static EnvArray profile_obs(int[] distRange,
            FusionCore ens, Observation ob, Met met) {

        if (ob.fusionPoint()) {
            return null;//THIS should not happen
        }
        EnvArray enva;
        if (met == null) {
            met = ens.getStoreMet(ob.dt, ob.lat, ob.lon);
        }

        BlockAnalysis ba = ens.getBlockAnalysis(null, ob);
        enva = new EnvArray(ob, met, ens.ops, ba);
        Dtime dt = ob.dt;
        float[] wdirs = getWdirFan(false, ens.ops, ob.lat, ob.lon, dt, met, met.getWindSpeed_raw());
        Dtime[] dates = ob.dates(ens);
        
        
        WeightManager man = new WeightManager(enva,ens,ob);
        if (man.locationNotCovered()) return enva;//osmLayer is null.
       
        //Distance skip conditions
        int maxDist = distRange[1];
        int minDist = distRange[0];

        boolean puffModellingAvailable = ens.puffModellingAvailable(ob.dt);
        boolean closeRangePart = distRange[0]==0;
        boolean addLongPlumes = (!puffModellingAvailable && closeRangePart);
        enva.groundZeroAdditions(man.osmLayer(), dt, dates, ens,addLongPlumes,met.getWdir());
        boolean hashedEmissionsAvailable = ens.hashedEmissionsAvailable(ob, man.osmLayer().name);
        EmitterShortList emTemp =null;
        if (!hashedEmissionsAvailable) {//estimate emission in the area during this process then.
            emTemp = new EmitterShortList(dt, dates, ens, safetyMarginBounds(ob.lat, ob.lon));
        } else {
            man.updateHEGs(true,ens);
        }
        //limit maximum evaluation distance in case puffModelling is available
        if (puffModellingAvailable) {
            maxDist = (int) Math.min(ens.ops.perfOps.puffPlumeSwitchThreshold_m(), maxDist);
        }// avoid double-counting this way.

        float allDirsFactor = 1f / wdirs.length;
        boolean forMeasurement = true;
        Footprint form = ens.selectForm(wdirs,forMeasurement);

        ArrayList<FootprintCell> cells = form.cells;
        float[] qtemp = new float[ens.ops.VARA.Q_NAMES.length];
        PlumeBender pb = new PlumeBender();
        
        //iterate over cells
        for (FootprintCell e : cells) {//THE MAIN LOOP=====================

            if (e.dist_m > maxDist) {
                break;//cells are sorted by distance so each cell after this would exceed this max.
            } else if (e.dist_m < minDist) {
                continue;//not in range just yet.
            }

            for (int dirIndex = 0; dirIndex < wdirs.length; dirIndex++) {//one or more "rays" towards the wind. Usually just one, really.
                float current_wd = wdirs[dirIndex];
                float gen_main_wFactor = allDirsFactor;//in case there are several rays, the weight will be split evenly.

                //some urban plume bending? This is needed in complex urban terrain.
                //in some cases the plume shape is mirrored so there's two computations instead of 1.
                pb.setWindReversals(enva.ba, current_wd, e.dist_m, ens.ops);

                for (boolean mirror: pb.mirrors()) {//this is [false] or [false,true]
                    float stren = pb.getStrength(mirror);//this is [0,1]
                    current_wd = pb.getWD(mirror);//this is the "current_wd" or 180 degrees rotated directory, if mirrored.
                    float gen_wFactor = gen_main_wFactor * stren;

                    //now that the rotation is known, its time to fix the location.
                    float currLat = e.currentLat(ob.lat, (int) current_wd, ens.ops);
                    float currLon = e.currentLon(ob.lat, ob.lon, (int) current_wd, ens.ops);
                    man.resetLocation(currLat, currLon, ens, e,current_wd, dirIndex);
                    //at this point it is possible that we are outside of the osmLayer - check.
                    if (man.locationNotCovered()) continue;
                    //time to browse emission for the location
                    
                    if (emTemp==null) {//FAST GRIDS
                        
                        float[] compact = man.getHEG_Element_hw_emPsm2();
                        if (compact == null) {
                            continue;
                        }
                        enva.shortestEmDist_m = (short) Math.min(enva.shortestEmDist_m, e.dist_m);
                        man.prepareWeights(ens, gen_wFactor);
                        EmitterShortList.stackEmissionsIntoConcentrations_compressed(compact, man,ens);
                      
                    } else {//ESTIMATE EMISSIONS NOW, then stack
                        
                        man.listEmissionDensities(emTemp);
                        if (!emTemp.hasContent()) continue;//nothing to add here.
                        //ok, dispersion modelling additions will occur. Prepare everything
                        //and stack content.
                        enva.shortestEmDist_m = (short) Math.min(enva.shortestEmDist_m, e.dist_m);
                        man.prepareWeights(ens, gen_wFactor);    
                        emTemp.stackEmissionsIntoConcentrations(man, qtemp,ens);  
                    }
                }//wdMirrors
            }//dirs

        } //cells
        return enva;
    }

    public static Boundaries safetyMarginBounds(float lat, float lon) {
        return new Boundaries(lat - 0.1, lat + 0.1, lon - 0.1, lon + 0.1);
    }
    
    
    private final static float[] NULLDIRS = {0, 60, 120, 180, 240, 300};

    /**
     * For low-windspeeds it is possible to use multiple directions to soften
     * the outcome.With this method up to 3 wind directions can be set up for
      * EnvProfiler.
     *
     * @param randomizeAdd if true, then adds a slight randomization of a couple of
     * degrees that can reduce visual artifacts.
     * @param ops options
     * @param lat lat coordinate
     * @param lon lon coordinate
     * @param dt time
     * @param met meteorology instance
     * @param v speed
     * @return array of wind directions
     */
    public static float[] getWdirFan(boolean randomizeAdd, FusionOptions ops,
            double lat, double lon, Dtime dt, Met met, float v) {
       
        float wdir = met.getWdir();
        if (randomizeAdd) wdir = FastMath.FM.randomizeWind(wdir,(int)(ops.fpmRand*4),false);
                
            
        if (v <= FusionOptions.NULL_WINDSPEED && ops.wDirFan) {
            return NULLDIRS;
        } else if (v <= FusionOptions.VLOW_WINDSPEED && ops.wDirFan) {

            float[] wdirs = new float[3];
            wdirs[0] = wdir;
            wdirs[1] = wdir + 30;
            if (wdirs[1] > 360) {
                wdirs[1] -= 360;
            }

            wdirs[2] = wdir - 30;
            if (wdirs[2] < 0) {
                wdirs[2] += 360;
            }

            return wdirs;
        } else if (v <= FusionOptions.LOW_WINDSPEED && ops.wDirFan) {

            float[] wdirs = new float[2];
            wdirs[1] = wdir + 10;
            if (wdirs[1] > 360) {
                wdirs[1] -= 360;
            }

            wdirs[0] = wdir - 10;
            if (wdirs[0] < 0) {
                wdirs[0] += 360;
            }

            return wdirs;
            
        } else {
            return new float[]{wdir};
        }

    }

}
