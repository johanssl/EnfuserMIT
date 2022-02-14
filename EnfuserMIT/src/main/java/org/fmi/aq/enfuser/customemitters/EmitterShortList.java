/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import org.fmi.aq.enfuser.core.receptor.RPstack;
import org.fmi.aq.enfuser.core.receptor.WeightManager;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 * This class is used for short-listing relevant AbstractEmitters for a given
 * time and location. The main purpose is to assess all emission contributions
 * from this specific location and time and to be able to turn this information
 * into concentration contributions percieved at a different location. This is
 * done e.g., via Profiler using the WeightManager and FpmCells.
 *
 * This class also contains methods to combine emission source data and produce
 * a compact float[] representation of them. These vectors can then be stored to
 * HashedEmissionGrid. Alternatively, this class provides methods to "stack"
 * emission contributions to DriverCategories or a float[][] CQ matrix.
 *
 * The core content of the class is a list of emission "elements". An element is
 * a float[] array with standardized length and structure. Basically, each
 * element has the release rates for each pollutant species but the last 3
 * elements contain information on emission height, category and a "ramp-up"
 * distance (the ramp-up distance can be used to eliminate strong outlier
 * concentrations near strong emitters). A collection of such elements can be
 * converted into a more compact, single float[] array.
 *
 * @author Lasse Johansson
 */
public class EmitterShortList {

    public ArrayList<AbstractEmitter> ages;
    FusionCore ens;
    ArrayList<float[]> currEmitters_µgPsm2 = new ArrayList<>();

    Dtime dt;
    Dtime[] dates;
    HashMap<String,Double> temp = new HashMap<>();
    final VarAssociations VARA;
    private Met mt;
    private final ArrayList<float[]> Qtemps = new ArrayList<>();

        /**
     * Construct an EmitterShortList for specific time and area.
     *
     * @param dt the time
     * @param dates dates as given by TZT
     * @param ens the fusionCore
     * @param b Bounds for emitter data to be short-listed.
     */
    public EmitterShortList(Dtime dt, Dtime[] dates, FusionCore ens,
            Boundaries b) {
        this.ens = ens;
        this.dt = dt;
        this.dates = dates;
        this.VARA = ens.ops.VARA;
        this.ages = AbstractEmitter.shortListRelevant(ens.ages, dt, dates,
                b, AbstractEmitter.OPERM_MAP, ens);
        //this.rpt = new RpTemp();
    }
    
    /**
     * For a list of emission contributions from several locations, combine and
     * re-map the emission to a list of emission contribution elements.This
 method is used to create a coarser resolution HashedEmissionGrid (20m)
 using 5m HEG data.
     *
     * @param compacts a list of compact emission data, e.g., from a 5m HEG.
     * @param resDiv resolution reduction factor (e.g., for 20m product using
     * base data of 5m this is 4. The emission data is per square meter so the
     * emission density needs to be adjusted accordingly.
     * @param elems a temporary HashMap of emission elements (cleared and
     * returned). The key for this HashMap is defined by emission category and
     * release height. - this means that that same category but different
     * release height will get their own "element".
     * @param ops
     * @return a list of emission elements that contains the same emission data
     * as the input data, but at a coarser resolution.
     */
    public static float[] combineCompactDensities(ArrayList<float[]> compacts,
            int resDiv, HashMap<Integer, float[]> elems, FusionOptions ops) {

        elems.clear();//make it empty
        GlobOptions g = GlobOptions.get();
        for (float[] compressed : compacts) {//iterate over the multiple compact float[] arrays. These must be processed back to elements that are read one at a time.

            int multis = (compressed.length) / ops.VARA.ELEM_LEN;//we have this many elements
            int START_INDEX = 0;
            for (int s = 0; s < multis; s++) {

                float height = compressed[START_INDEX + ops.VARA.ELEMI_H];
                int cat = (int) compressed[START_INDEX + ops.VARA.ELEMI_CAT];
                float rd = compressed[START_INDEX + ops.VARA.ELEMI_RD];

                int key = (int) (height / 5) + cat * 1000;//unique cat-height pairs
                float[] elem = elems.get(key);//is there an entry already with this key?
                if (elem == null) {//the first one, init
                    elem = new float[ops.VARA.ELEM_LEN];
                    elem[ops.VARA.ELEMI_H] = height;
                    elem[ops.VARA.ELEMI_CAT] = cat;
                    elem[ops.VARA.ELEMI_RD] = rd;
                    elems.put(key, elem);
                }
                //then stack densities, taking into account that the cellArea is different
                for (int q : ops.VARA.Q) {
                    elem[q] += compressed[q + START_INDEX] / (resDiv * resDiv);
                }

                START_INDEX += ops.VARA.ELEM_LEN;//update
            }//for multielements
        }//for compacts 

        //re-compress into single float[]  
        return compress(elems,ops);

    }

    /**
     * Update the state and content of this EmitterShortList, for the specified
     * location.Note: The time has been defined previously in the Constructor.
     *
     * @param met the Meteorological conditions for the assessment - can affect
     * emission releases via MetFunctions. The outcome of the method is an
     * updated list of "elements" in "currEmitters_µgPsm2".
     *
     * @param currLat the latitude
     * @param currLon the longitude
     * @param osmMask the osmLayer to be processed (emitters may or may not need
     * this but the osmLayer controls traffic emissions directly)
     * @param ol_h the osmLayer h-index for the latitude
     * @param ol_w the osmLayer w-index for longitude.
     * @param ops
     */
    public void listEmissionDensities(Met met, float currLat, float currLon,
         OsmLayer osmMask, int ol_h, int ol_w, FusionOptions ops) {

        this.currEmitters_µgPsm2.clear();
        if (this.mt == null || this.mt != met) {
            this.mt = met;
        }
        //go over gridEmitters that are active
        if (loc==null) loc = new OsmLocTime();
        loc.set(osmMask,currLat,currLon, ol_h, ol_w, dt, dates);
        
        for (AbstractEmitter age : ages) {
            float[] Qtemp = age.getAreaEmissionRates_µgPsm2(loc, met, VARA.ELEM_LEN,ens);
            if (Qtemp != null) {
                RPstack.scaleAndAutoNOX(Qtemp,ops.VARA);
                this.currEmitters_µgPsm2.add(Qtemp);
                //add element to array
            }

        }//for ges
    }

    private OsmLocTime loc=null;
    private final static float[] UNITY_W = {1f, 1f};


    /**
     * Once the current emission have been listed for specific locations,
 transform the emissions into concentration contributions and stack them
 to the given EnvArray's RPstack.
     *
     * @param man the WeightManager that holds information of meteorological
     * conditions and additional factors that are necessary to convert emissions
     * to percieved concentrations.
     * 
     * @param qtemp a temporary float[] array for which the length should equal
     * to VariableAssociations.Q.
     */
    public void stackEmissionsIntoConcentrations(WeightManager man, float[] qtemp, FusionCore ens) {
    
        FootprintCell e = man.getCurrentCell();
        for (float[] elem : this.currEmitters_µgPsm2) {

            float height = elem[VARA.ELEMI_H];
            int cat = (int) elem[VARA.ELEMI_CAT];
            float distRamp = elem[VARA.ELEMI_RD];

            float[] w;
            if (height < 1) height=1;
            w = man.getWeight(height,ens);//assume that there is no coarsePM here to be added.
            
            float rampFactor = getDistRamp(distRamp, e.dist_m);

            man.enva.addAll(cat, elem, w, rampFactor, 0);//addAll_autoNOxO3(cat, elem,w ,rampFactor,0,qtemp);

        }//for full elements

    }
    
        /**
     * For simple outlier removal nearby strong emission sources for which the
     * exact location is more or less an estimate, then reduce high
     * concentration nearby the source. This factor cannot exceed a value of 1.
     *
     * @param limit maximum distance of effect, where the ramp value is 1.0
     * @param dist_m the distance from source
     * @return scaler value [0,1]
     */
    public static float getDistRamp(float limit, float dist_m) {
        if (limit <=0) return 1f;
        
        if (dist_m<1) dist_m =1;
        if (dist_m>limit) dist_m = limit;
        
        return dist_m/limit;

    }

    /**
     * Assess a single emission element, read from a compact array that has
     * multiple elements. The raw emission data is converted into raw
     * concentrations via WeightManager and FpmCell much in the similar way that
     * is done in stackGaussianEmissionsIntoConcentrations().
     *
     * @param elem the compact emission array, possibly taken from a
     * HashedEmissionGrid.
     * @param START_READ_INDEX read element data starting from this index.
     * @param enva the EnvArray in which the raw concentrations are stacked to.
     * @param man the WeightManager that holds information of meteorological
     * conditions and additional factors that are necessary to convert emissions
     * to percieved concentrations.
     * @param e The FpmCell defines the geometry and area source size applied
     * for the listed emissions.
     */
    private static void stackElementContribution(float[] elem,
            int START_READ_INDEX, WeightManager man,FusionCore ens) {
        
        GlobOptions g = GlobOptions.get();
        float height = elem[START_READ_INDEX + man.ops.VARA.ELEMI_H];
        int cat = (int) elem[START_READ_INDEX + man.ops.VARA.ELEMI_CAT];
        float distRamp = elem[START_READ_INDEX + man.ops.VARA.ELEMI_RD];

        float[] w;
        if (height < 1) height=1;
        w = man.getWeight(height,ens);//assume that there is no coarsePM here to be added.

        float rampFactor = getDistRamp(distRamp, man.getCurrentCell().dist_m);
        man.enva.addAll(cat, elem, w, rampFactor, START_READ_INDEX);

    }

    /**
     * Stack emission elements from a compact array, but only for a specific
     * defined emission height range.
     *
     * @param areaScaler emission area scaler
     * @param dc RPstack for which the data is stacked to.
     * @param compressed the float[] array which holds the compact emission
     * elements.
     * @param height_range specified range of release heights for which the
     * stacking is accepted.
     * @param ops
     */
    public static void stackToDC(float areaScaler, RPstack dc,
            float[] compressed, float[] height_range, FusionOptions ops) {
        GlobOptions g = GlobOptions.get();
        int multis = (compressed.length) / ops.VARA.ELEM_LEN;//so: the total length is 1 + singulars*2 + multis*ELEM_LEN

        //begin processing the singular, if there are any
        int START_INDEX = 0;//the first element is to get the singulars count, so we start from the second value.

        for (int s = 0; s < multis; s++) {

            float height = compressed[START_INDEX + ops.VARA.ELEMI_H];
            if (height < 0) {
                height *= -1;
            }

            if (height_range == null || (height >= height_range[0] && height <= height_range[1])) {
                int cat = (int) compressed[START_INDEX + ops.VARA.ELEMI_CAT];
                dc.addAll(cat, compressed, UNITY_W, areaScaler, START_INDEX);
            }

            START_INDEX += ops.VARA.ELEM_LEN;//update
        }
    }

    /**
     * Once the current emission have been listed for specific locations,
 transform the emissions - which in this case are given as a compact array
 - into concentration contributions and stack them to the given EnvArray's
 RPstack.
     *
     * @param compressed float[] array that holds compact emission elements
     * @param man the WeightManager that holds information of meteorological
     * conditions and additional factors that are necessary to convert emissions
     * to percieved concentrations.
     */
    public static void stackEmissionsIntoConcentrations_compressed(float[] compressed,
            WeightManager man, FusionCore ens) {
        GlobOptions g = GlobOptions.get();
        int multis = (compressed.length) / man.ops.VARA.ELEM_LEN;//so: the total length is 1 + singulars*2 + multis*ELEM_LEN

        //begin processing the singular, if there are any
        int START_INDEX = 0;//the first element is to get the singulars count, so we start from the second value.

        //then the full elements
        for (int s = 0; s < multis; s++) {
            stackElementContribution(compressed, START_INDEX,  man,ens);
            START_INDEX += man.ops.VARA.ELEM_LEN;//update
        }
        //all done!
    }
    
    /**
     * Represent the ArrayList content of multiple elements in a single float[]
     * array.
     *
     * @return float[] compact emission data array.
     */
    public float[] compress() {

        if (!this.hasContent()) {
            return null;
        }
        //there is content, create an array
        GlobOptions g = GlobOptions.get();
        int size = this.currEmitters_µgPsm2.size() * VARA.ELEM_LEN;
        final float[] arr = new float[size];

        int j = 0;

        for (float[] elem : this.currEmitters_µgPsm2) {
            for (float f : elem) {
                arr[j] = f;
                j++;
            }
        }

        return arr;
    }

    /**
     * Represent the HashMap's content of multiple elements in a single float[]
     * array.
     *
     * @param elems a collection of emission elements on a HashMap. The hashMap
     * key can correspond to e.g., emission category, height or both. (The use
     * of HashMap is used to combine multiple emission arrays into one, see
     * :combineCompactDensities())
     * @param ops
     * @return float[] compact emission data array.
     */
    public static float[] compress(HashMap<Integer, float[]> elems, FusionOptions ops) {

        if (elems.isEmpty()) {
            return null;
        }
        //there is content, create an array
        GlobOptions g = GlobOptions.get();
        int size = elems.size() * ops.VARA.ELEM_LEN;
        final float[] arr = new float[size];

        int j = 0;

        for (float[] elem : elems.values()) {
            //DriverCategories.autoNOxO3(elem);
            for (float f : elem) {
                arr[j] = f;
                j++;
            }
        }

        return arr;
    }

    /**
     * After list operation for specific location - is there emission content to
     * prcoess?
     *
     * @return a boolean value.
     */
    public boolean hasContent() {
        return (!this.currEmitters_µgPsm2.isEmpty());// || !this.currEmitters_singular.isEmpty());
    }

    public ArrayList<float[]> currEmitters_µgPsm2() {
       return this.currEmitters_µgPsm2;
    }

}
