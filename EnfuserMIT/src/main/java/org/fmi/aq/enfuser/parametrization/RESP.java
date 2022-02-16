/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.parametrization;

import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import static org.fmi.aq.essentials.gispack.flowestimator.BasicFlowProfile.parseVehicClassIndicesFromHeader;
import org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.enfuser.ftools.FileOps;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.enfuser.options.RegionalFileSwitch;

/**
 * Road Emission Speed Profile (RESP). The use-case for this class is to define
 * speed dependencies with vehicular emissions - taking into account the vehicle
 * class.
 *
 * @author johanssl
 */
public class RESP {

    public int min_v = 10;
    public int MAX_V = 120;
    public final static int V_INCR = 10;
    public final int V_LEN;

    private final float[][][] funcVals_QCv;
    private static final int IND_TYPE = 1;
    private static final int IND_DEF = 0;

    private final static String TAG_REPLACEMENT = "replacement";
    private final static String TAG_DATLINE = "dataline";
    private final static String TAG_RANGE = "speed_range";

    private final HashMap<String, String> replacements = new HashMap<>();
    public String[] header;
    public final float[] masterCoeffs;
    public final VarAssociations VARA;
    final Categories CATS;
    
 
     
    public RESP(FusionOptions ops) {
        this.VARA = ops.VARA;
        CATS = GlobOptions.get().CATS;
        
        File f = RegionalFileSwitch.selectFile(ops, RegionalFileSwitch.RESP_FILE);
        ArrayList<String> arr = FileOps.readStringArrayFromFile(f);
        
        this.masterCoeffs = new float[VARA.Q_NAMES.length];
        for (int q:VARA.Q) {
            this.masterCoeffs[q]=1f;
        }
        header = arr.get(0).split(";");
        ArrayList<int[]> columnClasses = parseVehicClassIndicesFromHeader(header);
        int masterIndex =-1;
        for (int i =0;i<header.length;i++) {
            if (header[i].toLowerCase().contains("master")) {
                masterIndex = i;
                break;
            }
        }
        
        if (masterIndex==-1) {
            EnfuserLogger.log(Level.WARNING,RESP.class,"Warning! " 
                    + f.getAbsolutePath() + " master coefficient column (master) was not found!");
        }
        
        //read preliminary data
        for (int i = 1; i < arr.size(); i++) {
            String[] split = arr.get(i).split(";");
            String def = split[IND_DEF];
            if (def.equals(TAG_RANGE)) {

                min_v = Double.valueOf(split[1]).intValue();
                MAX_V = Double.valueOf(split[2]).intValue();
                EnfuserLogger.log(Level.FINER,RESP.class,"RESP: min and max parsed.");
                

            } else if (def.equals(TAG_REPLACEMENT)) {
                replacements.put(split[1], split[2]);
            }

        }

        int len = (MAX_V - 0) / V_INCR;
        V_LEN = len;
        this.funcVals_QCv = new float[VARA.Q_NAMES.length][FlowEstimator.VEHIC_CLASS_NAMES.length][len];

        //parse data
        for (int i = 1; i < arr.size(); i++) {
            String line = arr.get(i);
            for (String rep : replacements.keySet()) {//apply replacements
                String to = replacements.get(rep);
                line = line.replace(rep, to);
            }

            String[] split = line.split(";");
            String def = split[IND_DEF];
            if (def.equals(TAG_DATLINE)) {
                String type = split[IND_TYPE];
                int q = VARA.getTypeInt(type, true, f.getAbsolutePath());
                if (q<0) continue;
                
                if (masterIndex!=-1) {
                    double master = Double.valueOf(split[masterIndex]);
                    this.masterCoeffs[q] = (float)master;
                }
                
                //go over vehicle classes
                for (int[] colClass : columnClasses) {
                    int column = colClass[0];
                    int vehcClass = colClass[1];
                    String params = split[column];
                    String[] paraSplit = params.split(",");

                    double min = Double.valueOf(paraSplit[0]);
                    double mid = Double.valueOf(paraSplit[1]);
                    double max = Double.valueOf(paraSplit[2]);

                    double amp = Double.valueOf(paraSplit[3]);
                    Polynomial p = Polynomial.getFromEndPoints((float) min,
                            (float) mid, (float) max, (float) MAX_V);
                    for (int k = 0; k < len; k++) {
                        float v = +k * V_INCR;
                        double funcVal = amp * p.getValue(v);
                        //store value
                        funcVals_QCv[q][vehcClass][k] = (float) funcVal;
                    }//for speed steps

                }//for columns that hold data
            }//if line is dataline
        }//for lines

    }

    /**
     * With the given flow information for all vehicle classes, compute emission
     * increments for all primary types taking into account the flow speed.
     *
     * The result stands for emission density for any evaluation point within
     * the road: the rendered width of the road in the osmLayer is taken into
     * account. This means that if the road width is multiple pixels the
     * emission amount is being spread on a larger surface area (and this method
     * retuns a smaller value).
     *
     * @param flows vehicle count flows for the given road. Indexing should
     * follow FlowEstimator definition of vehicle classes.
     * @param rp the RoadPiece
     * @param speed speed for the flow (in this case the speed can be different
     * than the limit available in RoadPiece)
     * @param qtemp container for emission data (if null a new vector is
     * created)
     * @return qtemp vector in which all emission increments have been added to.
     */
    public float[] convertFlowsIntoEmissions(float[] flows, RoadPiece rp,
            float speed, float[] qtemp) {
        if (qtemp == null) {
            qtemp = new float[VARA.Q_NAMES.length];
        }
        int ind = getSpeedIndex(speed);

        for (int q : VARA.Q) {
            for (int vc : FlowEstimator.VC_INDS) {//for vehicle classes
                qtemp[q] += flows[vc] * this.funcVals_QCv[q][vc][ind]*this.masterCoeffs[q];
            }
        }
        return qtemp;
    }

    /**
     * For a given speed (km/h) compute the index where speed profile scaler is
     * to be read.
     *
     * @param speed in km/h
     * @return index.
     */
    public int getSpeedIndex(float speed) {

        if (speed <= min_v) {
            speed = min_v;
        }
        if (speed >= MAX_V) {
            return V_LEN - 1;
        }

        return (int) ((speed) / (float) V_INCR);
    }

    public float getFactor(int Q, int classI, int v) {
        int ind = getSpeedIndex(v);
        return funcVals_QCv[Q][classI][ind];
    }

}
