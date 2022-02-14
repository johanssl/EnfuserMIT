/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.SOURCE_DEF;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class BasicFlowProfile {

    public ArrayList<Dtime> dates;
    public ArrayList<Float> values;
    public ArrayList<Integer> classes;
    private final SimpleFlowArray flowA;

    public final static int DT_IND = 0;

    public final static String PROF_FILE_NAME = "basicTRVprofile.csv";

    /**
     * Create a default copy of basic TRV profile on file in case the directory
     * does not have this file already.
     *
     * @param dir the directory
     */
    public static void createProfileFile(String dir) {
        File test = new File(dir + PROF_FILE_NAME);

        if (!test.exists()) {
            ArrayList<String> arr = new ArrayList<>();
            for (String l : BASIC_PROF_DATA) {
                arr.add(l);
            }

            FileOps.printOutALtoFile2(dir, arr, PROF_FILE_NAME, false);
        }
    }
    
    public SimpleFlowArray getFlowProfiles() {
        return this.flowA;
    }
    
    public SimpleFlowArray getScaledFlows(float[] davgs) {
        
        SimpleFlowArray sf = this.flowA.hardCopy();
        for (int c: FlowEstimator.VC_INDS) {
            float dvg = davgs[c];
            sf.normalizeDAVGto(dvg, c);
        }
        
        return sf;
    }

    public BasicFlowProfile(String dir, boolean createDummy) {

        dates = new ArrayList<>();
        values = new ArrayList<>();
        classes = new ArrayList<>();
        String filename = dir + PROF_FILE_NAME;

        EnfuserLogger.log(Level.FINER,this.getClass(),"Reading BasicFlowProfile...");
        ArrayList<String> arr = new ArrayList<>();
        File ftest = new File(filename);
        if (ftest.exists()) {
            EnfuserLogger.log(Level.FINER,this.getClass(),filename + " exists.");
            arr = FileOps.readStringArrayFromFile(filename);

        } else if (createDummy) {
            EnfuserLogger.log(Level.FINER,this.getClass(),filename + " does NOT exist (using default data).");
            arr = new ArrayList<>();
            for (String l : BASIC_PROF_DATA) {
                arr.add(l);
            }

        }//file does not exist.

        String[] header = arr.get(0).split(";");
        ArrayList<int[]> columnClass = parseVehicClassIndicesFromHeader(header);

        String[] line;
        for (int i = 1; i < arr.size(); i++) {
            line = arr.get(i).split(";");

            Dtime dt = new Dtime(line[DT_IND]);

            for (int[] colClass : columnClass) {
                int col = colClass[0];
                int classI = colClass[1];
                double val = Double.valueOf(line[col]);

                dates.add(dt);
                classes.add(classI);
                values.add((float) val);
            }//for columns

        }//for lines
       
        this.flowA = new SimpleFlowArray(SOURCE_DEF, dates, values, classes);
    }

    /**
     * parse header Strings and find read indices for vehicle classes.
     *
     * @param header String[] to be parsed. The returned column indices follow
     * this order and length.
     * @return a list of successfully parsed pairs of [columnIndex, classIndex].
     * For example: an element int[]{4,IND_HEAVY} shows that the fourth column
     * in the data is for "heavy" vehicles.
     */
    public static ArrayList<int[]> parseVehicClassIndicesFromHeader(String[] header) {
        ArrayList<int[]> columnClass = new ArrayList<>();

        for (int i = 0; i < header.length; i++) {

            String test = header[i];
            int classI = FlowEstimator.parseClassIndex(test, true);
            if (classI >= 0) {
                EnfuserLogger.log(Level.FINER,BasicFlowProfile.class,
                        "Found class index: " + test + " => " + classI);
                columnClass.add(new int[]{i, classI});
            }
        }
        //find types
        return columnClass;
    }

    private final static String[] BASIC_PROF_DATA = {
        "date (fri,sat,sun);heavy;bus;vehic;light",
        "2019-08-30T00:00:00;0.063;0.223;0.091;0.091",
        "2019-08-30T01:00:00;0.063;0.155;0.091;0.091",
        "2019-08-30T02:00:00;0.063;0.075;0.091;0.091",
        "2019-08-30T03:00:00;0.063;0.075;0.091;0.091",
        "2019-08-30T04:00:00;0.063;0.075;0.091;0.091",
        "2019-08-30T05:00:00;0.063;0.075;0.091;0.091",
        "2019-08-30T06:00:00;0.395;0.686;0.539;0.539",
        "2019-08-30T07:00:00;0.739;0.858;0.941;0.941",
        "2019-08-30T08:00:00;0.793;0.762;0.972;0.972",
        "2019-08-30T09:00:00;1;0.746;0.564;0.564",
        "2019-08-30T10:00:00;1;0.658;0.564;0.564",
        "2019-08-30T11:00:00;1;0.658;0.564;0.564",
        "2019-08-30T12:00:00;1;0.658;0.564;0.564",
        "2019-08-30T13:00:00;1;0.658;0.564;0.564",
        "2019-08-30T14:00:00;0.969;0.845;0.646;0.646",
        "2019-08-30T15:00:00;0.882;0.959;0.876;0.876",
        "2019-08-30T16:00:00;0.577;0.879;1;1",
        "2019-08-30T17:00:00;0.557;1;0.843;0.843",
        "2019-08-30T18:00:00;0.421;0.817;0.699;0.699",
        "2019-08-30T19:00:00;0.286;0.602;0.554;0.554",
        "2019-08-30T20:00:00;0.213;0.538;0.511;0.511",
        "2019-08-30T21:00:00;0.139;0.475;0.465;0.465",
        "2019-08-30T22:00:00;0.101;0.351;0.279;0.279",
        "2019-08-30T23:00:00;0.063;0.228;0.091;0.091",
        "2019-08-31T00:00:00;0.053;0.198;0.085;0.085",
        "2019-08-31T01:00:00;0.053;0.126;0.085;0.085",
        "2019-08-31T02:00:00;0.053;0.054;0.085;0.085",
        "2019-08-31T03:00:00;0.053;0.054;0.085;0.085",
        "2019-08-31T04:00:00;0.053;0.054;0.085;0.085",
        "2019-08-31T05:00:00;0.053;0.054;0.085;0.085",
        "2019-08-31T06:00:00;0.053;0.119;0.085;0.085",
        "2019-08-31T07:00:00;0.103;0.185;0.183;0.183",
        "2019-08-31T08:00:00;0.153;0.243;0.282;0.282",
        "2019-08-31T09:00:00;0.173;0.301;0.389;0.389",
        "2019-08-31T10:00:00;0.194;0.273;0.499;0.499",
        "2019-08-31T11:00:00;0.168;0.273;0.546;0.546",
        "2019-08-31T12:00:00;0.142;0.273;0.594;0.594",
        "2019-08-31T13:00:00;0.142;0.273;0.594;0.594",
        "2019-08-31T14:00:00;0.142;0.273;0.594;0.594",
        "2019-08-31T15:00:00;0.142;0.273;0.594;0.594",
        "2019-08-31T16:00:00;0.117;0.294;0.56;0.56",
        "2019-08-31T17:00:00;0.092;0.315;0.527;0.527",
        "2019-08-31T18:00:00;0.078;0.282;0.44;0.44",
        "2019-08-31T19:00:00;0.064;0.248;0.354;0.354",
        "2019-08-31T20:00:00;0.051;0.223;0.29;0.29",
        "2019-08-31T21:00:00;0.038;0.198;0.227;0.227",
        "2019-08-31T22:00:00;0.045;0.198;0.155;0.155",
        "2019-08-31T23:00:00;0.053;0.198;0.085;0.085",
        "2019-09-01T00:00:00;0.042;0.164;0.088;0.088",
        "2019-09-01T01:00:00;0.042;0.1;0.088;0.088",
        "2019-09-01T02:00:00;0.042;0.036;0.088;0.088",
        "2019-09-01T03:00:00;0.042;0.036;0.088;0.088",
        "2019-09-01T04:00:00;0.042;0.036;0.088;0.088",
        "2019-09-01T05:00:00;0.042;0.036;0.088;0.088",
        "2019-09-01T06:00:00;0.037;0.14;0.088;0.088",
        "2019-09-01T07:00:00;0.032;0.188;0.097;0.097",
        "2019-09-01T08:00:00;0.045;0.236;0.154;0.154",
        "2019-09-01T09:00:00;0.058;0.229;0.213;0.213",
        "2019-09-01T10:00:00;0.084;0.222;0.301;0.301",
        "2019-09-01T11:00:00;0.11;0.222;0.391;0.391",
        "2019-09-01T12:00:00;0.107;0.222;0.474;0.474",
        "2019-09-01T13:00:00;0.103;0.222;0.558;0.558",
        "2019-09-01T14:00:00;0.103;0.222;0.558;0.558",
        "2019-09-01T15:00:00;0.103;0.222;0.558;0.558",
        "2019-09-01T16:00:00;0.103;0.307;0.558;0.558",
        "2019-09-01T17:00:00;0.112;0.393;0.593;0.593",
        "2019-09-01T18:00:00;0.12;0.336;0.628;0.628",
        "2019-09-01T19:00:00;0.111;0.278;0.534;0.534",
        "2019-09-01T20:00:00;0.101;0.221;0.442;0.442",
        "2019-09-01T21:00:00;0.072;0.164;0.265;0.265",
        "2019-09-01T22:00:00;0.042;0.164;0.088;0.088",
        "2019-09-01T23:00:00;0.042;0.164;0.088;0.088"
    };

}
