/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import org.fmi.aq.essentials.date.Dtime;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class SHGhandler {

    public static float[][] getAreaSum_type(SparseGrid id, String type) {
        float[][] data = new float[id.H][id.W];

        int typeIndex = id.getCellContentIndex(type);
        float totalSum = 0f;
        Float val;

        for (long t : id.getDates_forDataIncluded()) { // iterate over date keys
            //get the index of searched h-slice
            for (short h = 0; h < id.H; h++) {
                for (short w = 0; w < id.W; w++) {

                    val = id.getValue_tkHW(t, h, w, typeIndex);
                    if (val != null) {

                        data[h][w] = val;
                        totalSum += val;
                    }

                }
            }
        }// for t

        EnfuserLogger.log(Level.FINER,SHGhandler.class,"MainList total: " + totalSum);
        return data;
    }

    public static float[][] get2D_timeSlice(SparseGrid id, String type, long date) {
        float[][] data = new float[id.H][id.W];

        int typeIndex = id.getCellContentIndex(type);
        float val;

        for (short h = 0; h < id.H; h++) {
            for (short w = 0; w < id.W; w++) {

                float[] gw = id.getCellContent_tkHW(date, h, w);

                if (gw != null) {
                    val = gw[typeIndex];
                    data[h][w] += val;

                }
            }//for w                                                            
        }//for h      
        return data;
    }

  
    /**
     * This method extracts a [][][] float based on type and selected dates.
     *
     * @param id is the IndexedData structure that is operated upon
     * @param type is the variable, that should be one of listed variables in
     * IndexedData id.
     * @param start - Start date (a Datetime instance), which should be timewise
     * equal or after id.startdt.
     * @param end - End date, which should be equal or before id.enddt.
     * @return float[time][height][width] float array.
     */
    public static float[][][] ExtractSub3D(SparseGrid id, String type, Dtime start, Dtime end) {

        ArrayList<float[][]> data = new ArrayList<>();
        int typeIndex = id.getCellContentIndex(type);

        ArrayList<Long> keys = id.getDates_wholeInterval(); // indexing values. not sure if they are sorted properly at this point.
        EnfuserLogger.log(Level.FINER,SHGhandler.class,"sub3D extraction: "
                + "The whole interval contains " + keys.size() + " temporal keys.");

        int n = 0;
        float totalSum = 0;
        for (long sysSecs : keys) {

            if (sysSecs < start.systemSeconds() || sysSecs > end.systemSeconds()) {
                continue; //not covered
            }
            String dateStr = id.dateStrs.get(sysSecs);
            n++;

            float[][] dat = new float[id.H][id.W];
            float sum = 0;
            for (short h = 0; h < id.H; h++) {
                for (short w = 0; w < id.W; w++) {

                    Float val = id.getValue_tkHW(sysSecs, h, w, typeIndex);

                    if (val != null) {
                        dat[h][w] = val;
                        sum += val;
                        totalSum += val;
                    }

                }//for w
            }//for h
                EnfuserLogger.log(Level.FINER,SHGhandler.class,
                        "Found a proper time slice for 3D sub-copy: " + dateStr 
                                + ", n = " + n + ", sum = " + (int) sum);
            

            data.add(dat);
        }//for time keys
            EnfuserLogger.log(Level.FINER,SHGhandler.class,"TotalSum = " + totalSum);
        

        float[][][] grid = new float[data.size()][][];
        for (int i = 0; i < data.size(); i++) {
            grid[i] = data.get(i);
        }
        return grid;
    }

    public static float[][][] Extract3D(SparseGrid id, String type) {

        Dtime start = new Dtime(id.getStartDate());
        Dtime end = new Dtime(id.getEndDate());

            EnfuserLogger.log(Level.FINER,SHGhandler.class,"Extracting 3D floats with dates "
                    + start.getStringDate() + " - " + end.getStringDate());
        
        return ExtractSub3D(id, type, start, end);
    }

    public static float getsum_total(SparseGrid id, String type) {
        float sum = 0f;
        int typeIndex = id.getCellContentIndex(type);

        for (long t : id.getDates_forDataIncluded()) {
            for (short h = 0; h < id.H; h++) {
                for (short w = 0; w < id.W; w++) {

                    Float val = id.getValue_tkHW(t, h, w, typeIndex);
                    if (val != null) {

                        sum += val;
                    }

                }
            }
        }
        return sum;
    }

    public static float getsum_timeSlice(SparseGrid id, String type, Dtime dt) {
        float sum = 0f;
        int typeIndex = id.getCellContentIndex(type);

        long t = id.getT_key(dt);

        for (short h = 0; h < id.H; h++) {
            for (short w = 0; w < id.W; w++) {

                Float val = id.getValue_tkHW(t, h, w, typeIndex);
                if (val != null) {
                    sum += val;
                }

            }
        }

        return sum;
    }

}//class end
