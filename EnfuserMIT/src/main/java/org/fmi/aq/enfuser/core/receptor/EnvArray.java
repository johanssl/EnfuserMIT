/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.customemitters.AbstractEmitter;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import org.fmi.aq.enfuser.core.gaussian.puff.ApparentPlume;
import java.io.Serializable;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;;
import java.util.ArrayList;
import org.fmi.aq.enfuser.core.combiners.PartialGrid;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import static org.fmi.aq.essentials.geoGrid.ByteGeoGrid.getValue_OOBzero;
import org.fmi.aq.essentials.gispack.osmreader.core.Building;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_ELEVATION;

/**
 *
 * @author johanssl
 */
public class EnvArray implements Serializable {

    public byte bh_g0;
    public byte OSM_res = -1;
    public final boolean fusionPoint;//if true, then this a) for area fusion. If false, then this for real Observation and this affects how BlockAnalysis is initiated.
    
    public float logWind;
    public short obsHeight_aboveG;//obsH,"above surface"
    public short absoluteGroundElevation;//includes absolute ground elevation. Nothing else.
    public short relativeGroundElevation;//relative elevation, based on 8 points with roughly 1km distances, includes obsH but no buildings

    //static-ish vars
    public final Met met;
    public byte groundZero_surf;
    public byte groundZero_func;
    public BlockAnalysis ba;

    public float mirrorStrenSum = 0;//updates if this is below 0
    public short shortestEmDist_m = Short.MAX_VALUE;

    public final RPstack dcat;
    public final String obID;
    public final float lat,lon;

    public EnvArray(Observation o, Met met, FusionOptions ops, BlockAnalysis ba) {

        this.met = met;
        this.obsHeight_aboveG = (short) o.height_m;
        this.fusionPoint = o.fusionPoint();

        this.lat = o.lat;
        this.lon = o.lon;
        this.obID = o.ID;
        dcat = new RPstack(ops);
        this.ba = ba;
    }

    /**
     *
     * @param ol
     * @param dt
     * @param dates
     * @param ens
     * @param addLongPlumes
     * @param windDir

     */
    public void groundZeroAdditions(OsmLayer ol, Dtime dt, Dtime[] dates, FusionCore ens,
            boolean addLongPlumes, float windDir) {

        OSM_res = (byte) ol.resm;

        this.absoluteGroundElevation = 0;
        this.relativeGroundElevation = (short) 0;
        float cos_midlat = FM.getFastLonScaler(lat);//ens.fpm.getCosLat((float)lat);    
        if (ens.ops.applyElevation) {
            ByteGeoGrid elev = ol.getByteGeo(BGN_ELEVATION);
            if (elev != null) {
                this.absoluteGroundElevation = (short) getValue_OOBzero(elev, lat, lon);

                this.relativeGroundElevation = 0;
                if (ens.ops.applyElevation) {
                    this.relativeGroundElevation = (short) assessRelativeElevation(elev, cos_midlat, lat, lon, 400, false);
                }

            }
        }

        byte osm = ba.groundZero_type;
        this.groundZero_surf = osm;
        this.groundZero_func = ba.groundZero_func;
        Building b = ba.groudZero_building;

        if (b != null) {
            double d = b.getHeight_m(ol);
            if (d > 125) {
                d = 125;
            }
            this.bh_g0 = (byte) d;
        }

        if (OSMget.isWater(osm)) {
            this.relativeGroundElevation = 0;
        }

        //setMidRange blockers
        Blocker mr_block = Blocker.getForMidRange(this.obsHeight_aboveG, dt, ba,
                windDir, ens.ops, (float)relativeGroundElevation);
         dcat.setMidRangeReductor(mr_block);
        
        //getPlumes for individual sources
        if (addLongPlumes) {// should NOT be added to a cut EnvArray that is for e.g., 300m -> 8km
            Boundaries bb = ens.ops.bounds();
            //float[] yx =  Transforms.latlon_toMeters(lat, lon,ens.superBounds);
            ArrayList<AbstractEmitter> ages = AbstractEmitter.shortListRelevant(ens.ages, dt,
                    dates, bb, AbstractEmitter.OPERM_INDIV, ens);
            for (AbstractEmitter age : ages) {
                ArrayList<ApparentPlume> plumes = age.getPlumes(dt, dates, ens, bb);
                if (plumes != null) {
                    for (ApparentPlume ap : plumes) {
                        ap.stackConcentrations(this.dcat.stack_cq, this.obsHeight_aboveG,
                                lat, lon, ens.ops);//getConcentration(z, yx[0], yx[1], ens);
                    }
                }

            }
        }//if not puffModelling

       
    }

    /**
     * Cumulate stats from another partial EnvArray. The partial has no blocking
     * effects so this is done as post-process, using the last block factors.
     * The idea of this is that the completed partial would be almost identical
     * to a full range EnvArray without partial components.
     *
     * @param dat array from the partial EnvArray, with a different evaluation
     * radius, e.g., 300-4000m.
     */
    void cumulatePartialStats(RPstack dc, FusionOptions ops) {
        GlobOptions g= GlobOptions.get();
        for (int q = 0; q < ops.VARA.Q_NAMES.length; q++) {
            for (int c : g.CATS.C) {
                this.dcat.stack_cq[c][q] += dc.stack_cq[c][q];
            }
        }

    }

     /**
     * Read emission from a float[] array, starting from index START_READ_INDEX.
     * To turn this into incremental concentration additions, these pieces of
     * emissions are combined with the gaussian emission footprint weights and
     * added to this DriverCategories instance.
     *
     * @param CAT emission source category index, as defined in Categories
     * @param ems the float[] that contains emission data. This can be a very
     * long vector, that included multiple extended Q[] arrays in total.
     * @param weights these are the scaling factors to turn these emissions into
 concentrations at the location, time and conditions set for this
 RPstack. there are two elements: [0] for gaseous, [1] for course
 particles.
     * @param amp amplifier that operates the addition.
     * @param START_READ_INDEX use a value of 0 if ems[] is a traditional array
     * with the length of Q_NAMES.length.
     */
    public void addAll(int CAT, float[] ems, float[] weights,
            float amp, int START_READ_INDEX) {
        this.dcat.addAll(CAT, ems, weights, amp, START_READ_INDEX);
    }   

    public void clearDrivers() {
       this.dcat.clearDrivers();
    }

    public void stackAll(ArrayList<PartialGrid> pGrids, float lat, float lon) {
        this.dcat.stackAll(pGrids, lat, lon);
    }
    
    public static float assessRelativeElevation(ByteGeoGrid elev, float cos_midlat, double lat,
            double lon, float METERS, boolean allowNegative) {

        float eleNear = 0;
        if (elev == null) {
            return eleNear;
        }

        float actualEle = 0;
        if (elev.gridBounds.intersectsOrContains(lat, lon)) {
            actualEle = (float) getValue_OOBzero(elev, lat, lon);

        }

        int n = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) {
                    continue;//there are 8 cells to be evaluated
                }
                double currLat = lat + i * (METERS + 10) / degreeInMeters;// shift up, down
                double currLon = lon + j * METERS / degreeInMeters / cos_midlat;//shift left,right

                if (elev.gridBounds.intersectsOrContains(currLat, currLon)) {
                    eleNear += getValue_OOBzero(elev, currLat, currLon);//ol.elevation.getValue(currLat, currLon);
                    n++;
                }
            }//j-lon
        }//i-lat
        eleNear /= (n + 0.001f);

        actualEle -= eleNear;//e.g. actual Elevation is 120m and eleNear is 80m => 40m relative elevation
        if (actualEle < 0 && !allowNegative) {
            actualEle = 0;
        }
        return actualEle;
    }  


}
