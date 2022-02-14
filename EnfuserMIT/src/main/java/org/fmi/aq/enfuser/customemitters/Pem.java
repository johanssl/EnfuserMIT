/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.ftools.FastMath;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import java.io.File;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.interfacing.MicroMet;

/**
 * This class represent a point emitter, e.g., a power plant. As opposed to
 * other emitters a single pointEmitter file can contain multiple Pem's at once,
 * whereas a standard emitter file will lead to a single emitter instance.
 *
 * The list of point emitters defined in a text file needs to have a distinct
 * structure as follows: NAME;UNITS(e.g. max capacity for power plants in
 * MW);LAT;LON;HEIGHT_M;DESCRIPTION;Q_SCALERS;LTM_KEY; Things to consider: - the
 * LTM-key is used to associate a specific Pem to a LTM-profile. Each Pem can
 * techically have it's own LTM profile. Since customization of LTM's per each
 * Pem can be a hassile, a single LTM can be used for multiple Pem's.
 * -Additionally, an additional Q-scaler can be applied on top of LTM-functions
 * in case e.g., a certain point emitter has reduced emission outputs for
 * certain pollutant species. - The description column needs to have an emission
 * category String in it, e.g., "power". - "NAME" is optional, also Q_SCALERS
 * is.
 *
 * The Point emitter is not allowed to function in a map-mode since these are
 * often very strong elevated sources for emissions. This emitter works as an
 * inidividual - either puff or separate (not gridded emissions) plume
 * -modelling is used to account these. Due to this nature, the emitter's
 * releases will not be represented as area sources, only in terms of µg per
 * second.
 *
 * @author Lasse Johansson
 */
public class Pem extends AbstractEmitter {

    public String name;
    public String descr;
    public float lat;
    public float lon;
    public float emUnits;
    public float[] qScalers;

    public final static int IND_NAME = 0;
    public final static int IND_UNITS = 1;
    public final static int IND_LAT = 2;
    public final static int IND_LON = 3;
    public final static int IND_EMSH = 4;
    public final static int IND_DESC = 5;

    public final static int IND_Q_SCALERS = 6;
    public final static int IND_LTM_KEY = 7;

    int releaseCounter = 0;
    boolean usesMicroMetModule=false;
    /**
     * Construct a single PointEmitter (Pem) from a line read from a file that
     * defines a list of multiple Pems.
     *
     * @param line the line which defines the Pem. The structure needs to follow
     * the given guidelines (a CSV-line with 7 elements to be parsed in
     * sequence).
     * @param orig original file name for a PEM list
     * @param ops
     */
    public Pem(String line, File orig, FusionOptions ops) {
        super(ops);
        String[] split = line.split(";");

        this.name = split[IND_NAME];
        this.emUnits = Float.valueOf(split[IND_UNITS]);
        this.lat = Double.valueOf(split[IND_LAT]).floatValue();
        this.lon = Double.valueOf(split[IND_LON]).floatValue();

        double DLAT = 50.0 / degreeInMeters;
        double coslat = FastMath.FM.getFastLonScaler(lat);
        double DLON = DLAT / coslat;

        this.height_def = Double.valueOf(split[IND_EMSH]).floatValue();
        this.descr = split[IND_DESC];
        this.cat = CATS.getSourceType(descr, true);

        this.myName = split[IND_NAME];
        usesMicroMetModule = MicroMet.enabledByName(myName);
        this.origFile = orig;

        Boundaries bDummy = new Boundaries(lat - DLAT, lat + DLAT, lon - DLON, lon + DLON); //a 100m box around the location (has limited use cases, and not very important for modelling)
        this.in = new AreaInfo(bDummy, 2, 2, Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
        this.isZeroForAll = new boolean[in.H][in.W];
        this.operMode = OPERM_INDIV;
        this.gridType = POINTS;
        String ltmKey = split[IND_LTM_KEY];
        this.ltm = LightTempoMet.readWithName(ops, ltmKey);
        if (this.ltm == null) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "LTM for a PointEmitter was NOT found! " + myName + ", " 
                    + name + "-" + descr+", key="+ltmKey+", file= "+orig.getAbsolutePath());
        }

//q scalers
        qScalers = new float[VARA.Q_NAMES.length];
        for (int q : VARA.Q) {
            qScalers[q] = 1f;
        }

        try {
            String allMods = split[IND_Q_SCALERS];
            if (allMods != null && allMods.contains("=")) {
                String[] mods = allMods.split(",");
                for (String mod : mods) {
                    String[] var_value = mod.split("=");
                    int typeInt = VARA.getTypeInt(var_value[0]);
                    double value = Double.valueOf(var_value[1]);
                    this.qScalers[typeInt] = (float) value;
                    EnfuserLogger.log(Level.FINER,Pem.class,
                            "\tPEM: added q modifier for " + VARA.VAR_NAME(typeInt) + " => " + value);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        if (this.ltm.hasNormalizingSum()) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    myName +": LTM has a normalizing sum defined but this feature"
                            + " has been reserved only for annual-totals -type of gridded emitters!");
        }
    }

    /**
     * Get emission releases for this point emitter.
     *
     * @param last the exact time.
     * @param dates dates as given by TZT.
     * @param ens the FusionCOre
     * @param secs amount of seconds for the release (Scales the emissions).
     * @return float[] Q emission amounts.
     */
    float[] getEms_µg(Dtime last, Dtime[] dates, Met met, float secs) {
        if (this.emUnits<=0) return null;
        float[] qtemp = null;

        for (int q : VARA.Q) {
            float scaler = this.ltm.getScaler(last, dates, met, q);

            if (scaler > 0) {
                if (qtemp == null) {
                    qtemp = new float[VARA.Q_NAMES.length];
                }
                qtemp[q] = scaler * this.emUnits * qScalers[q] * secs * FastMath.MILLION;// emFactor is g/s/unit => µg/s
            }
        }
        return qtemp;
    }

    @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        return null;//This type of emitter never releases area cell emissions without puffs.
    }

    @Override
    public float getEmHeight(OsmLocTime loc) {
        return (float) this.height_def;
    }

    /**
     * Overridden method to get emission releases for the given time step.
     *
     * @param last exact time
     * @param secs amount of seconds for emissions
     * @param pf the PuffPlatform (not used)
     * @param allDates dates as given in TZT
     * @param ens the FusionCore
     * @return a list of raw emission data that can be converted into
     * PuffCarriers or GaussianPlumes.
     */
    @Override
    public ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs,
            PuffPlatform pf, Dtime[] allDates,
            FusionCore ens) {
        ArrayList<PrePuff> arr = new ArrayList<>();
        Met met = ens.getStoreMet(last, lat, lon);
        float[] q = getEms_µg(last, allDates, met, secs);
        if (q != null) {
            PrePuff pr = new PrePuff(q,lat,lon, (float) this.height_def );
            pr.cat = this.cat;
            pr.usesMicroMetModule = this.usesMicroMetModule;
            arr.add(pr);
        }

        return arr;
    }

    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {
        float[] qtemp =  new float[VARA.Q_NAMES.length];

        for (int q : VARA.Q) {//basic unit in grams per second
            qtemp[q] = this.emUnits * qScalers[q]  *3600f/1000f;
        }
        return qtemp;
    }

}
