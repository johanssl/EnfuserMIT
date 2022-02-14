/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.parametrization.LightTempoMet;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.gaussian.puff.PuffPlatform;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.io.File;
import org.fmi.aq.enfuser.ftools.FastMath;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * Represent line emissions on a grid. The lines may have variable height
 * (therefore names as 3D lines). The emitter is setup using a text file that
 * can be considered as an extended LTM-file.
 *
 * Possible use-case: runways. This type of an emitter is singular: one value is
 * stored in the grid and the LTM-profile will simply scale different pollutant
 * species.
 *
 * TODO: there are some more work to be done with this emitter class: - speed
 * attributes - overall emitter surface area - line length scales total
 * emissions which is an issue.
 *
 * @author Lasse Johansson
 */
public class Lines3Demitter extends AbstractEmitter {

    public GeoGrid ems_µgPsm2;
    public GeoGrid heights;
    public final float cellA_m2;

    ArrayList<String[]> linedata;
    ArrayList<double[]> lined_latlonh;
    ArrayList<Boolean> isLonlat = new ArrayList<>();

    public final static int IND_SCALER = 3;
    public final static int IND_LATLONH1 = 5;
    public final static int IND_LATLONH2 = 6;

    
        /**
     * Construct the line emitter.The text file (en extended LTM) needs to have
     * information with keys "GRID_RES", and then a series of "LINES". Each line
     * has an start point and an endpoint. These points also need to specify the
     * height (m) as the third attribute after lat,lon. Emitter speed is given
     * as the fourth value, but this feature is not yet utilized.
     *
     * @param file the file
     * @param ops FusionOptions.
     */
    public Lines3Demitter(File file, FusionOptions ops) {
        super(ops);
        this.readAttributesFromFile(file);
        this.ltm = LightTempoMet.create(ops,file);
        double res_m = -1;
        this.linedata = new ArrayList<>();
        lined_latlonh = new ArrayList<>();

        int i = 0;
        for (String s : ltm.lines) {
            i++;
            if (i == 1) {
                continue;
            }

            String[] split = s.split(";");
            String linetype = split[0];

            if (linetype.contains("GRID_RES")) {
                res_m = Double.valueOf(split[1]);
                EnfuserLogger.log(Level.FINER,Lines3Demitter.class,"\t emission grid resolution given: " + (int) res_m + "m");
            }

            if (!linetype.contains("LINE")) {
                continue;
            }

            double[] cc = null;
            boolean lonlat = false;
            if (split[IND_LATLONH2].contains("KML")) {//Google Earth lines format
                //126.7747310373204,37.57334397263141,30 126.8108934280583,37.54487885905173,30, => lon,lat,h
                try {
                    lonlat = true;
                    String[] sp = split[IND_LATLONH1].split(",");
                    double[] vals = new double[sp.length];
                    for (int k = 0; k < sp.length; k++) {
                        vals[k] = Double.valueOf(sp[k]);
                    }
                    cc = vals;
                } catch (Exception e) {
                    e.printStackTrace();

                }
            } else {
                String[] c1 = split[IND_LATLONH1].split(",");
                String[] c2 = split[IND_LATLONH2].split(",");
                cc = new double[]{Double.valueOf(c1[0]), Double.valueOf(c1[1]), Double.valueOf(c1[2]),
                    Double.valueOf(c2[0]), Double.valueOf(c2[1]), Double.valueOf(c2[2])
                };
            }
            if (cc != null) {
                this.lined_latlonh.add(cc);
                this.linedata.add(split);
                this.isLonlat.add(lonlat);
            }

        }//for lines

        double minlat = 1000;
        double maxlat = -1000;

        double minlon = 1000;
        double maxlon = -1000;
        //get coordinates
        for (double[] cc : this.lined_latlonh) {
            double lat1 = cc[0];
            double lon1 = cc[1];

            if (lat1 < minlat) {
                minlat = lat1;
            }
            if (lat1 > maxlat) {
                maxlat = lat1;
            }

            if (lon1 < minlon) {
                minlon = lon1;
            }
            if (lon1 > maxlon) {
                maxlon = lon1;
            }

            double lat2 = cc[3];
            double lon2 = cc[4];

            if (lat2 < minlat) {
                minlat = lat2;
            }
            if (lat2 > maxlat) {
                maxlat = lat2;
            }

            if (lon2 < minlon) {
                minlon = lon2;
            }
            if (lon2 > maxlon) {
                maxlon = lon2;
            }
        }//for coords

        Boundaries b = new Boundaries(minlat, maxlat, minlon, maxlon);
        EnfuserLogger.log(Level.FINER,Lines3Demitter.class,b.toText());
        this.in = new AreaInfo(b, (float) res_m, Dtime.getSystemDate_utc(false, Dtime.ALL), new ArrayList<>());
        float[][] dat = new float[in.H][in.W];

        //this.emGrid = new SparseHashGrid((short)H, (short)W, b, FusionOptions.VARA.Q_NAMES, SparseGrid.TIME_HOUR);
        float[][] hdat = new float[in.H][in.W];
        this.heights = new GeoGrid(hdat, in.dt, b);
        this.ems_µgPsm2 = new GeoGrid(dat, in.dt, b);

        this.isZeroForAll = new boolean[in.H][in.W];
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                this.isZeroForAll[h][w] = true;
            }
        }
        this.cellA_m2 = in.cellA_m2;
        //add data to hashgrid
        float totalSum = 0;//this will be CONSTANT regardless of resolution

        for (int j = 0; j < this.lined_latlonh.size(); j++) {
            double[] cc = this.lined_latlonh.get(j);
            String[] linedata = this.linedata.get(j);
            boolean switchLonlat = this.isLonlat.get(j);

            double scaler = Double.valueOf(linedata[IND_SCALER]);

            double lat1 = cc[0];
            double lon1 = cc[1];
            double height1 = cc[2];

            double lat2 = cc[3];
            double lon2 = cc[4];
            double height2 = cc[5];

            if (switchLonlat) {
                lat1 = cc[1];
                lon1 = cc[0];

                lat2 = cc[4];
                lon2 = cc[3];
            }

            double dist_m = Observation.getDistance_m(lat2, lon2, lat1, lon1);
            double emissionTotal = dist_m * scaler;//length of line x emFactor.
            totalSum += emissionTotal;

            int steps = (int) (2 + 1.5 * dist_m / res_m);

            EnfuserLogger.log(Level.FINER,Lines3Demitter.class,
                    "\t Processing emission line with " + steps + " steps.");

            for (int n = 0; n <= steps; n++) {
                float f1 = (float) n / steps;
                float f2 = (1.0f - f1);

                double lat = lat1 * f1 + f2 * lat2;
                double lon = lon1 * f1 + f2 * lon2;
                double height = height1 * f1 + height2 * f2;

                //add height information to grid
                int h = in.getH(lat);//getH(b,lat,dlat);
                int w = in.getW(lon);//getW(b,lon,dlon);

                boolean oob= !this.heights.setValue_oobSafe(h, w, (float)height);
                if (oob) continue;
                
                //cellArea must be factored in, sinc
                this.ems_µgPsm2.values[h][w] = (float) scaler;//convert into density LATER
                this.isZeroForAll[h][w] = false;

            }//for steps 
        }//for coords 

        //check added total sum
        float initSum = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                initSum += this.ems_µgPsm2.values[h][w];
            }
        }

        float normalizer = totalSum / initSum;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                this.ems_µgPsm2.values[h][w] *= normalizer / in.cellA_m2;
            }
        }

        EnfuserLogger.log(Level.FINER,Lines3Demitter.class,"Lines3D: total amount of added raw content: " 
                + totalSum / FastMath.MILLION + " gs-1");
        float test = 0;
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                test += this.ems_µgPsm2.values[h][w] * in.cellA_m2;
            }
        }
        EnfuserLogger.log(Level.FINER,Lines3Demitter.class,"Lines3D: total amount of raw content "
                + "in the processed density grid: " + test / FastMath.MILLION + " gs-1");
        
        this.ems_µgPsm2 =ltm.normalizeEmissionGridSum(ops,this.ems_µgPsm2,cellA_m2,
        LightTempoMet.NORMALIZE_UGPSM2,file.getAbsolutePath());
    }

    @Override
    protected float[] getRawQ_µgPsm2(Dtime dt, int h, int w) {
        float[] Q = new float[VARA.Q.length];
        for (int q : VARA.Q) {
            Q[q] = this.ems_µgPsm2.values[h][w];
        }
        return Q;
    }

    @Override
    public float getEmHeight(OsmLocTime loc) {
        return this.heights.values[loc.emitter_h][loc.emitter_w];
    }

    @Override
    public ArrayList<PrePuff> getAllEmRatesForPuff(Dtime last, float secs,
            PuffPlatform pf, Dtime[] allDates, FusionCore ens) {
        return null;//This type of emitter never releases individual puffs.
    }

    @Override
    public float[] totalRawEmissionAmounts_kgPh(Dtime dt) {

        float[] Q = new float[VARA.Q.length];

        for (int h = 0; h < this.ems_µgPsm2.H; h++) {
            for (int w = 0; w < this.ems_µgPsm2.W; w++) {
                for (int q : VARA.Q) {
                    float val = this.ems_µgPsm2.values[h][w] * in.cellA_m2 * UGPS_TO_KGPH;
                    Q[q] += val;
                }//for q 
            }//for h
        }//for w

        return Q;
    }

}
