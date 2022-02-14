/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import static org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell.IND_GAS;
import static org.fmi.aq.enfuser.core.gaussian.fpm.FootprintBank.nonPerf;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import static java.lang.Math.PI;
import java.util.ArrayList;
import java.util.Collections;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.interfacing.Imager;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_CLASSES;
import static org.fmi.aq.enfuser.core.gaussian.fpm.StabiClasses.STABILITY_CLASS_NAMES;

/**
 *
 * @author johanssl
 */
public class Footprint implements Serializable {

    private static final long serialVersionUID = 7523472293822776147L;//Important: change this IF changes are made that breaks serialization!   
    public final static float STREETCAN_RAD_m = 100;
    public final static int[] ZS = {2, 4, 7, 10, 15, 20, 30, 40, 60, 90, 140, 180, 220, 270, 340, 400};
    public final static int[] DS = {80, 120, 180, 250, 400, 600, 1600};
    
    public ArrayList<FootprintCell> cells;
    //final boolean nullDir;
    int totN = 0;
    final float dh_m;

    public double focalPointOffset_lat;
    public double focalPointOffset_lon;

    public final float MAX_DIST;

    private float[][][] wsums_Dz_SIG;
    public final float[][] yardSticks_DIST = new float[50][STABILITY_CLASS_NAMES.length];
    public final float perf;

    private void initWsums_precalc(FusionOptions ops) {
        wsums_Dz_SIG = new float[DS.length][ZS.length][STABILITY_CLASS_NAMES.length];

        float U = 1;
        long H = 2;

        int k = 0;
        float[] temp = new float[2];
        for (int z_ind = 0; z_ind < ZS.length; z_ind++) {
            int z = ZS[z_ind];
            for (int D_ind = 0; D_ind < DS.length; D_ind++) {
                int D = DS[D_ind];
                k++;
                if (k % 500 == 0) {
                    EnfuserLogger.log(Level.FINER,Footprint.class,"Evaluation precalc weightsum, k = " + k);
                }

                for (int i = 0; i < STABILITY_CLASSES; i++) {
                    float sum = 0f;
                    for (FootprintCell cell : cells) {
                        cell.setAreaWeightedCoeffs_U_zH_D_SIG(temp, U, z, H, D, i, ops, 0);
                        sum += temp[FootprintCell.IND_GAS];
                    }//for cells
                    this.wsums_Dz_SIG[D_ind][z_ind][i] = sum;
                }

            }//for D
        }

    }

    public float getWeightSum_UzD_FAST(float windSpeed_nullsafe, float z, float d, int siguClass) {

        int z_ind = ZS.length - 1;//assume last
        for (int k = 0; k < ZS.length; k++) {
            if (z <= ZS[k]) {
                z_ind = k;
                break;
            }
        }

        int d_ind = DS.length - 1;//assume last
        for (int k = 0; k < DS.length; k++) {
            if (d <= DS[k]) {
                d_ind = k;
                break;
            }
        }

        if (this.wsums_Dz_SIG[d_ind][z_ind][siguClass] < 1) {
            EnfuserLogger.log(Level.FINER,Footprint.class,
                    "getWeightSum_UD_FAST: very LOW weightsum with ABLH " + d);
        }
        return this.wsums_Dz_SIG[d_ind][z_ind][siguClass] / windSpeed_nullsafe;

    }

    public final static int MAX_RATIO = 36;

    public Footprint(float performance, float dh_m, float maxDist, boolean analysis) {

        int[] zoneCounter = new int[MAX_RATIO];

        this.cells = new ArrayList<>();

        this.dh_m = dh_m;
        this.MAX_DIST = maxDist;
        String firstReg = GlobOptions.get().getAllRegions().ercfs.get(0).getName();
        FusionOptions ops = new FusionOptions(firstReg);//any options will do

        float dist;
        double lat = 0;
        double lon = 0;
        float counter = 0;
        float directLineCounter = 0;

        double lonScaler_60 = Math.cos(60.0 / 360 * 2 * PI);
        this.perf = performance;
        String info = "";
        //if (streetcan) info = "(STREET_CAN) ";
        if (performance > nonPerf) {
            info += "(PERFORMANCE)";
        } 
        if (dh_m > 4) {
            info += "(FASTEST)";
        }

        ArrayList<int[]> HW;
        int r = -1;
        float[][] dat = new float[(int) MAX_DIST / 5][(int) MAX_DIST / 5];

        float yardStick_dist = 28;//for any cell that has equal footprint value, the resolution should be 5x5m => resInt =2

        float U = 4;
        float z = 2;
        float H = 2;
        int sig = 2;
        float D = 120;
        //general yardsticks (for possible weight reductions near source)
        EnfuserLogger.log(Level.FINER,Footprint.class,"Weight reduction measures: ");
        float[] temp = new float[2];
        for (int hm = 0; hm < this.yardSticks_DIST.length; hm++) {
            for (int sigu = 0; sigu < STABILITY_CLASS_NAMES.length; sigu++) {
                FootprintCell comparatorCell = new FootprintCell(dh_m, hm, 0);
                comparatorCell.setAreaWeightedCoeffs_U_zH_D_SIG(temp, U, z, H, D, sigu, ops, 0);
                float compWeight = temp[IND_GAS];//(float)comparatorCell.getAreaWeightedCoeff_U_zH_D_SIG(1, 2, 2, 100, fpm,sigu,false,FpmCell.METHOD_PRECALC_OLD);
                compWeight /= comparatorCell.cellArea_m2;
                this.yardSticks_DIST[hm][sigu] = compWeight;
                //EnfuserLogger.log(Level.INFO,Footprint.class,"dist = " + hm +"m, weight = "+ compWeight);
            }
        }

        FootprintCell comparatorCell = new FootprintCell(dh_m, yardStick_dist, 0);// the yardstick! a location directly towards the emissions source. The closer this is set the larger weight it will get.
        comparatorCell.setAreaWeightedCoeffs_U_zH_D_SIG(temp, U, z, H, D, sig, ops, 0);
        float compWeight = temp[IND_GAS];//(float)comparatorCell.getAreaWeightedCoeff_U_zH_D_SIG(4, 2, 2, 100, fpm,0,false,FpmCell.METHOD_PRECALC_OLD);

        while (r * dh_m < MAX_DIST) {
            r++;
            HW = getIterationIndex(r);

            for (int[] hw : HW) {

                totN++;
                int h = hw[0];
                int w = hw[1];

                if (h < 0) {
                    continue;
                }

                float h_m = dh_m * h; // meters in the wind's direction, 'up'. NOTE: h and w and NOT the rotated currH and currW.
                float w_m = dh_m * w; //  meters in the direction perpendicular to the wind's direction, 'left or right'
                dist = (float) Math.sqrt(h * h + w * w) * this.dh_m;
                if (dist > MAX_DIST) {
                    continue;
                }

                //test resolution
                FootprintCell egc = new FootprintCell(dh_m, h_m, w_m);
                egc.setAreaWeightedCoeffs_U_zH_D_SIG(temp, U, z, H, D, sig, ops, 0);
                float weight = temp[IND_GAS];//(float)egc.getAreaWeightedCoeff_U_zH_D_SIG(4, 2, 2, 100, fpm,0,false,FpmCell.METHOD_PRECALC_OLD);

                float ratio = compWeight / weight;// 
                ratio = (float) Math.pow(ratio, 0.42);//estimate what the resolution ratio should be with respec to 2.5 x 2.5m

                if (ratio > MAX_RATIO) {
                    continue;//utterly unimportant. Even if the area of evaluation cell is increased significantly the cell simply will not matter in any scenario
                }
                if (ratio - 0.5 > Math.pow(dist, 0.43)) {
                    continue;//idea: it is not desireable to have some 50 x 50m (ratioInt = 10) evaluation cells very close by. Better to leave them out.
                }
                //performance modification
                ratio += 0.7;
                if (perf > 0 && dist > 15) {
                    ratio *= (1 + perf);
                    if (ratio < 2 && dist > 30 / (1f + perf)) {
                        ratio = 2;//2.5m cells not allowed
                    }
                }

                //go over zones and select a suitable one
                int ratioInt;
                if (ratio < 2) {
                    ratioInt = 1;
                } else if (ratio < 6) {
                    ratioInt = (int) (ratio / 2) * 2;
                } else if (ratio < 17) {
                    ratioInt = (int) (ratio / 4) * 4;
                } else if (ratio < 25) {
                    ratioInt = (int) (ratio / 4) * 4;
                } else {
                    ratioInt = 20;
                }

                if (ratioInt > 20) {
                    ratioInt = 20;
                }

                // toCell, if weight is weight is high enough  
                int datw = w + (int) MAX_DIST / 5 / 2;
                if (h % ratioInt == 0 && w % ratioInt == 0) { // E.g: distance is 400m and resScaler is 2.
                    float cellRes = this.dh_m*ratioInt;
                    //Then only every fourth cell will be taken into the array BUT the taken cell gets quadruple effective area: egc.multiplyArea(resScaler*resScaler); 
                   GeoGrid.gridSafeSetValue(dat,h,datw, cellRes * 0.8f);
                } else {
                    float cellRes = this.dh_m*ratioInt;
                   GeoGrid.gridSafeSetValue(dat,h,datw, cellRes);
                   continue;
                }

                zoneCounter[ratioInt]++;

                //CENTER-LINE modifications - the first couple of cells in the direct line (w=0) contain a large fraction of the total footprint. This can cause artifacts in the modelling
                if (w == 0) {//do not allow direct line egc. These can have unrealisticly large weights
                    w_m = 0.5f;
                    if (h % 2 == 0) {
                        w_m *= -1;//modulate this shift from left to right
                    }
                    if (h == 0) {//Gaussian plume from 0m distance, no thanks!
                        h_m = 0.5f;
                    }

                }

                egc = new FootprintCell(dh_m * ratioInt, h_m, w_m);
                egc.setAreaWeightedCoeffs_U_zH_D_SIG(temp, U, z, H, D, sig, ops, 0);
                float c = temp[IND_GAS];

                if (w == 0) {
                    directLineCounter += c;
                }
                counter += c;

                lat += dh_m * h * c;
                lon += dh_m * w * c;

                this.cells.add(egc);

            }//for hw
        }//while not full
        
        //visualize ratio zones
        if (analysis) {
            String upperText ="PlumeForm cells: total cell count = "+cells.size();
            new Imager().dataToImageFrame(dat, null, null,
            "STYLE<10>,TRANSP<10>,BANNER<0>,TXT<test,Cell resolution [m] "+upperText+" at distance "+(int)maxDist+"m, >", false);
        }

        EnfuserLogger.log(Level.INFO,Footprint.class,"Cells amount: " + cells.size() + info);
        this.focalPointOffset_lat = lat / counter;
        this.focalPointOffset_lon = lon / counter;

        int upShift = (int) (this.focalPointOffset_lat);
        int rightShift = (int) (this.focalPointOffset_lon / lonScaler_60);

        EnfuserLogger.log(Level.INFO,Footprint.class,"latitude shift point = " + upShift + "m");
        EnfuserLogger.log(Level.INFO,Footprint.class,"longitude shift point (lat = 60): " + rightShift + "m");

        float dirLineProm = directLineCounter * 1000 / counter;
        EnfuserLogger.log(Level.INFO,Footprint.class,"EnvGridForm complete. ArraySize=" + cells.size() + info);
        EnfuserLogger.log(Level.INFO,Footprint.class,"Total amount of cells evaluated:=" + totN + info);
        EnfuserLogger.log(Level.INFO,Footprint.class,"DirectLineCounter =" + dirLineProm + "%%");

        EnfuserLogger.log(Level.INFO,Footprint.class,"Sorting cells by radius...");

        try {
            Collections.sort(cells, new CellComparator());
        } catch (IllegalArgumentException exx) {

        }
        EnfuserLogger.log(Level.INFO,Footprint.class,"Done. radius for first cell is " + cells.get(0).dist_m + " m.");

        this.initWsums_precalc(ops);
        int imin = 0;
        if (!analysis) {
            imin = STABILITY_CLASSES - 1;
        }

        for (int i = imin; i < STABILITY_CLASSES; i++) {
            EnfuserLogger.log(Level.INFO,Footprint.class,"=============STABILITY CLASS " 
                    + STABILITY_CLASS_NAMES[i] + " =====================" + info);
            EnfuserLogger.log(Level.INFO,Footprint.class,"weight sum at ground level (v=1) = " 
                    + getWeightSum_xmin_U_ZHD_SIG(cells, Float.MAX_VALUE, 1, z, H, 1000, i, ops)[0]);
            EnfuserLogger.log(Level.INFO,Footprint.class,"weight sum at ground level (v=4) = " 
                    + getWeightSum_xmin_U_ZHD_SIG(cells, Float.MAX_VALUE, 4, z, H, 1000, i, ops)[0]);
            EnfuserLogger.log(Level.INFO,Footprint.class,"weight sum at ground level (v=4, ILH 100m) = " 
                    + getWeightSum_xmin_U_ZHD_SIG(cells, Float.MAX_VALUE, 4, z, H, 100, i, ops)[0]);

            int INC = 5;
            for (int m = 1; m < 8000; m += 5 + m / 4) {
                if (m > 50) {
                    INC = 20;
                }
                float[] sums = getWeightSum_xmin_U_ZHD_SIG(cells, m, 4, z, 2, 100, i, ops);
                float[] sums1 = getWeightSum_xmin_U_ZHD_SIG(cells, m, 4, z, 1, 100, i, ops);
                float[] sums3 = getWeightSum_xmin_U_ZHD_SIG(cells, m, 4, z, 3, 100, i, ops);
                EnfuserLogger.log(Level.INFO,Footprint.class,"\t of which is " + m + "m away or closer: " 
                        + sums[0] + ", cellCount = " + sums[1] + ", sum with emission elevation 3m = " 
                        + sums3[0] + ", sum with emission elevation 1m = " + sums1[0]);
            }

            EnfuserLogger.log(Level.INFO,Footprint.class,info);
            for (int d : DS) {
                for (float v = 1; v < 10; v++) {
                    int sum = (int) this.getWeightSum_UzD_FAST(v, 2, d, i); 
                    System.out.print(sum + "\t");
                }
                System.out.print("\n");
            }

        }//for sig classes

        EnfuserLogger.log(Level.INFO,Footprint.class,"Done");

        EnfuserLogger.log(Level.INFO,Footprint.class,"Total number of cells = " 
                + this.cells.size() + ", performance = " + performance);
        EnfuserLogger.log(Level.INFO,Footprint.class,"ZONE COUNTERS");
        for (int i = 0; i < zoneCounter.length; i++) {
            if (zoneCounter[i] == 0) {
                continue;
            }
            //float aveResm = zoneResm[i]/zoneCounter[i];
            float zres = i * 2.5f;
            EnfuserLogger.log(Level.INFO,Footprint.class,"Zone " + zres + "m => " + zoneCounter[i] + "cells");
        }

        EnfuserLogger.log(Level.INFO,Footprint.class,"Setting up rotations...");
        for (FootprintCell e : this.cells) {
            e.setRotations();
        }

        temp = new float[2];
        //check stabilityClass sums over all BLH

        ArrayList<float[]> HDzt = new ArrayList<>();
        HDzt.add(new float[]{2, 180});
        HDzt.add(new float[]{2, 400});
        HDzt.add(new float[]{50, 400});

        for (float[] hd : HDzt) {
            float Dtest = hd[1];
            float Ht = hd[0];

            float[][] testSums = new float[STABILITY_CLASSES][3];
            int lastZ = 0;
            for (int zz : ZS) {
                if (zz > Dtest) {
                    continue;
                }
                int width = zz - lastZ;
                lastZ = zz;

                for (int stab = 0; stab < STABILITY_CLASSES; stab++) {

                    float sum = 0;
                    float closeSum = 0;
                    float lowSum = 0;
                    for (FootprintCell e : this.cells) {
                        e.setAreaWeightedCoeffs_U_zH_D_SIG(temp, 1, zz, Ht, Dtest, stab, ops, 0);
                        float weight = temp[IND_GAS];
                        sum += weight;
                        if (e.dist_m < 1000) {
                            closeSum += weight;
                        }
                        if (zz < 10) {
                            lowSum += weight;
                        }
                    }

                    testSums[stab][0] += width * sum;//this.getWeightSum_UzD_FAST(1, z, D, stab);
                    testSums[stab][1] += width * closeSum;
                    testSums[stab][2] += width * lowSum;
                }
            }
            EnfuserLogger.log(Level.INFO,Footprint.class,"3D sum test for v=1,(H=" + Ht + "m) D=" + Dtest);
            for (int stab = 0; stab < STABILITY_CLASSES; stab++) {
                EnfuserLogger.log(Level.INFO,Footprint.class,"\t" + STABILITY_CLASS_NAMES[stab] 
                        + " => " + (int) testSums[stab][0] + ", closer than 1km = " +
                        (int) testSums[stab][1] + ", below 10m = " + (int) testSums[stab][2]);
            }

        }

        EnfuserLogger.log(Level.INFO,Footprint.class,"Done.");
    }

    public float[] getWeightSum_xmin_U_ZHD_SIG(ArrayList<FootprintCell> cells, float xmin,
            float U, float z, float h, float d, int siguClass, FusionOptions ops) {
        float sum = 0f;
        int count = 0;
        float[] temp = new float[2];
        for (FootprintCell cell : cells) {
            if (cell.dist_m < xmin) {
                float inc = cell.setAreaWeightedCoeffs_U_zH_D_SIG(temp, U, z, h, d, siguClass, ops, 0)[IND_GAS];//getAreaWeightedCoeff_U_zH_D_SIG(U, z, h, D, fpm,siguClass,false,FpmCell.METHOD_BEST);
                if (Float.isNaN(inc)) {
                    EnfuserLogger.log(Level.FINER,Footprint.class,"Nan cellWeight:");
                }
                sum += inc;
                count++;
            }

        }
        return new float[]{sum, count};
    }

    public static ArrayList<int[]> getIterationIndex(int r) {

        ArrayList<int[]> arr = new ArrayList<>();

        if (r == 0) {
            arr.add(new int[]{0, 0});
            return arr;
        }

        // e.g. r = 1
        for (int w = -r; w <= r; w++) { // -1 to 1
            arr.add(new int[]{r, w}); // up --, adds (1,-1), (1,0), (1,1)
            arr.add(new int[]{-r, w}); // down --, adds (-1,-1), (-1,0), (-1,-1)

        }

        for (int h = -r + 1; h <= r - 1; h++) { // -1 to 1
            arr.add(new int[]{h, r}); // right |, adds (0,r)
            arr.add(new int[]{h, -r}); // left | , adds (0,-r)

        }

        return arr;
    }

    public static Footprint load(String filename) {
        // Read from disk using FileInputStream
        FileInputStream f_in;
        try {
            f_in = new FileInputStream(filename);

            // Read object using ObjectInputStream
            ObjectInputStream obj_in;

            obj_in = new ObjectInputStream(f_in);

            // Read an object
            Object obj = obj_in.readObject();
            if (obj instanceof Footprint) {
                EnfuserLogger.log(Level.FINER,Footprint.class,"fpmForm loaded from .dat-file successfully.");
                Footprint form = (Footprint) obj;
                f_in.close();

                return form;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public void replaceFormCells(float dh_m, float changeDist) {

        Footprint form2 = new Footprint(this.perf, dh_m, changeDist, true);

        ArrayList<FootprintCell> removes = new ArrayList<>();
        for (FootprintCell e : this.cells) {
            if (e.dist_m < changeDist) {
                removes.add(e);
            }
        }

        this.cells.removeAll(removes);
        EnfuserLogger.log(Level.FINER,Footprint.class,"removed " + removes.size() + " cells from Form.");

        this.cells.addAll(form2.cells);
        EnfuserLogger.log(Level.FINER,Footprint.class,"added " + form2.cells);
        try {
            Collections.sort(cells, new CellComparator());
        } catch (IllegalArgumentException exx) {

        }
        EnfuserLogger.log(Level.FINER,Footprint.class,"Done. radius for first cell is " + cells.get(0).dist_m + " m.");

    }

    public static void saveToFile(Footprint form, String name) {

        String fileName;
        fileName = name;
        EnfuserLogger.log(Level.FINER,Footprint.class,"Saving fpmForm to: " + fileName);
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fileName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(form);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }

    }

}
