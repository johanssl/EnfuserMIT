/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.tools.subgrid;

import java.io.Serializable;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * A compact grid structure - grids within a grid. For data that has similar
 * values being clustered (variability is sparse and infrequent), this can save
 * a lot of memory.
 *
 * This class is for byte data specifically.
 *
 * @author johanssl
 */
public class SubByteGrid extends SubGrid implements Serializable {

    private static final long serialVersionUID = 7529972293822776147L;//Important: change this IF changes are made that breaks serialization! 
    public final SubByte[][] values;
    public float offset = 0;
    public float scalerDiv = 0f;
    public boolean scaling = false;
    public final float savings;

    /**
     * Create a new instance.
     *
     * @param name the name of the SubByteGrid.
     * @param vals original grid of values that processed and compressed.
     * @param dt time attribution for the data.
     * @param bounds geo attribution for the data
     * @param X sparsing number
     */
    public SubByteGrid(String name, byte[][] vals, Dtime dt, Boundaries bounds, int X) {
        this(vals, dt, bounds.clone(name), X);
    }

    public static SubByteGrid fromFloats(float[][] vals, Dtime dt, Boundaries bounds, int[] Xcands) {
        ByteGeoGrid bgg = new ByteGeoGrid(vals, dt, bounds);//convert into bytes with scaling

        SubByteGrid sbg = null;
        if (Xcands.length == 1) {
            int X = Xcands[0];
            sbg = new SubByteGrid(bgg.values, dt, bounds, X);

        } else {

            float bestSave = -1;
            for (int X : Xcands) {
                SubByteGrid sbg_test = new SubByteGrid(bgg.values, dt, bounds, X);
                if (sbg_test.savings > bestSave) {
                    bestSave = sbg_test.savings;
                    EnfuserLogger.log(Level.FINER,SubByteGrid.class,"\t => current highest savings with " + X);
                    sbg = sbg_test;
                }
            }

        }
        //attach scaling and offset info
        sbg.scalerDiv = bgg.VAL_SCALER;
        sbg.offset = bgg.VAL_FLAT_ADD;
        sbg.scaling = true;
        return sbg;
    }

    public static SubByteGrid fromBytes(byte[][] values, Dtime dt, Boundaries bounds, int[] Xcands) {

        SubByteGrid sbg = null;
        if (Xcands.length == 1) {
            int X = Xcands[0];
            sbg = new SubByteGrid(values, dt, bounds, X);

        } else {

            float bestSave = -1;
            for (int X : Xcands) {
                SubByteGrid sbg_test = new SubByteGrid(values, dt, bounds, X);
                if (sbg_test.savings > bestSave) {
                    bestSave = sbg_test.savings;
                    EnfuserLogger.log(Level.FINER,SubByteGrid.class,"\t => current highest savings with " + X);
                    sbg = sbg_test;
                }
            }

        }
        //attach scaling and offset info

        return sbg;
    }

    public SubByteGrid(byte[][] vals, Dtime dt, Boundaries bounds, int X) {
        super(vals.length, vals[0].length, dt, bounds, X);

        this.values = new SubByte[H_s][W_s];
        int subGrids = 0;
        //int spaceSaves =0;
        int storedVals = 0;
        //add data
        for (int h = 0; h < H_s; h++) {
            for (int w = 0; w < W_s; w++) {

                boolean hasMultiple = false;
                byte oneValue = 0;
                byte[][] subs = new byte[X][X];

                for (int x = 0; x < X; x++) {
                    for (int y = 0; y < X; y++) {
                        byte val = vals[h * X + x][w * X + y];
                        subs[x][y] = val;
                        if (x == 0 && y == 0) {
                            oneValue = val;
                        }//init 
                        else if (oneValue != val) {
                            hasMultiple = true;

                        }

                    }//for y
                }//for x

                if (hasMultiple) {
                    this.values[h][w] = new SubByte(oneValue, subs);
                    subGrids++;
                    storedVals += X * X;
                } else {
                    this.values[h][w] = new SubByte(oneValue, null);
                    storedVals++;//only one stored value
                }

            }//for w
        }//for h
        savings = 100f * (1f - (float) storedVals / (H_fine * W_fine));//if stored vals equal total cells then the savings is 0%
        //EnfuserLogger.log(Level.FINER,this.getClass(),"total subFloats: "+ (H_s*W_s) +" of which " + multis/1000 +"k is subGridded and "+ singulars/1000 + "k has single value" );
        EnfuserLogger.log(Level.FINER,this.getClass(),"total elements: " + (H_fine * W_fine) / 1000 + "k, for which the amount of stored values is " + storedVals / 1000 + "k");
        EnfuserLogger.log(Level.FINER,this.getClass(),"Space savings with " + X + "x" + X + "subgrids is " + savings + "%");
    }

    @Override
    public int values(int h, int w) {
        int H = h / X;
        if (H > this.values.length - 1) {
            return OOBS;
        }
        int W = w / X;
        if (W > this.values[0].length - 1) {
            return OOBS;
        }

        SubByte sf = this.values[H][W];
        if (sf == null) {
            return OOBS;
        }

        byte b;
        if (sf.dat == null) {
            b = sf.oneVal;
        } else {
            int hm = h % X;
            int wm = w % X;
            b = sf.dat[hm * X + wm];
        }

        if (scaling) {

            int temp = b + 125; //reposition from -128 scale to 0 -256 scale;
            return (int) (temp / this.scalerDiv + this.offset);//as is done in ByteGeoGrid

        } else {
            return b;
        }

    }

    @Override
    public int changeValueAt(int h, int w, int val) {

        int H = h / X;
        if (H > this.values.length - 1) {
            return OOBS;
        }
        int W = w / X;
        if (W > this.values[0].length - 1) {
            return OOBS;
        }

        SubByte sf = this.values[H][W];
        if (sf == null) {
            return OOBS;
        }
        byte bval = (byte) val;
        int hm = h % X;
        int wm = w % X;
        int index = hm * X + wm;

        //split into two cases - a subGrid exists
        if (sf.dat == null) {//case 1: it is null
            if (bval == sf.oneVal) {
                return NOT_DONE;//do nothing since the oneValue is the same one as this change value

            } else {//the value is different - must create a new subgrid using the old fillValue as base
                SubByte sf_new = new SubByte(this.X, sf.oneVal, index, (byte) val);
                this.values[H][W] = sf_new;
                return DONE;
            }

        } else {//case2: the subgrid exists, just replace
            sf.dat[index] = bval;
            return DONE;
        }

    }

    @Override
    public void checkCompress() {
        int changes = 0;
        int singulars = 0;
        for (int h = 0; h < H_s; h++) {
            for (int w = 0; w < W_s; w++) {
                SubByte sf = this.values[h][w];
                if (sf == null) {
                    continue;
                }
                Byte b = sf.canBeCompressed();
                if (b != null) {
                    this.values[h][w] = new SubByte(b, null);
                    changes++;
                }//swith

                if (this.values[h][w].dat == null) {
                    singulars++;
                }

            }//for w
        }//for h
        EnfuserLogger.log(Level.FINER,this.getClass(),"SubGrid: changed " + changes + " elements of " + H_s * W_s + ". The amount of singulars is now " + singulars);
    }

}
