/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.tools.subgrid;

import java.io.Serializable;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class SubIntGrid extends SubGrid implements Serializable {

    private static final long serialVersionUID = 7526472293822774447L;//Important: change this IF changes are made that breaks serialization! 
    public SubInt[][] values;

    /**
     * Create a SubGrid with Integer types.
     *
     * @param name Name attribution.
     * @param vals Integer values that are going to be distributed in this
     * SubIntGrid.
     * @param dt time attribution.
     * @param bounds geo area attribution.
     * @param X sparsing seed integer for SubInts (X times X)
     */
    public SubIntGrid(String name, int[][] vals, Dtime dt, Boundaries bounds, int X) {
        this(vals, dt, bounds.clone(name), X);
    }

    public SubIntGrid(int[][] vals, Dtime dt, Boundaries bounds, int X) {
        super(vals.length, vals[0].length, dt, bounds, X);

        this.values = new SubInt[H_s + 1][W_s + 1];
        int multis = 0;
        int singulars = 0;
        //add data
        for (int h = 0; h <= H_s; h++) {
            for (int w = 0; w <= W_s; w++) {

                boolean hasMultiple = false;
                int oneValue = 0;
                int[][] subs = new int[X][X];

                for (int x = 0; x < X; x++) {
                    for (int y = 0; y < X; y++) {

                        try {
                            int val = vals[h * X + x][w * X + y];
                            subs[x][y] = val;
                            if (x == 0 && y == 0) {
                                oneValue = val;
                            }//init 
                            else if (oneValue != val) {
                                hasMultiple = true;

                            }

                        } catch (IndexOutOfBoundsException e) {

                        }
                    }//for y
                }//for x

                if (hasMultiple) {
                    this.values[h][w] = new SubInt(oneValue, subs);
                    multis++;
                } else {
                    this.values[h][w] = new SubInt(oneValue, null);
                    singulars++;
                }

            }//for w
        }//for h
        float savings = 100f * singulars * X * X / (H_fine * W_fine);
        EnfuserLogger.log(Level.FINER,this.getClass(),"total subFloats: " + (H_s * W_s) + " of which " + multis + " is subGridded and " + singulars + " has single value");
        EnfuserLogger.log(Level.FINER,this.getClass(),"Space savings: " + savings + "%");
    }

    @Override
    public int values(int h, int w) {
        int H = h / X;
        int W = w / X;

        SubInt sf = this.values[H][W];
        if (sf == null) {
            return 0; //OOB
        }
        if (sf.dat == null) {
            return sf.oneVal;
        } else {
            int hm = h % X;
            int wm = w % X;
            return sf.dat[hm * X + wm];
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

        SubInt sf = this.values[H][W];
        if (sf == null) {
            return OOBS;
        }

        int hm = h % X;
        int wm = w % X;
        int index = hm * X + wm;

        //split into two cases - a subGrid exists
        if (sf.dat == null) {//case 1: it is null
            if (val == sf.oneVal) {
                return NOT_DONE;//do nothing since the oneValue is the same one as this change value

            } else {//the value is different - must create a new subgrid using the old fillValue as base
                SubInt sf_new = new SubInt(this.X, sf.oneVal, index, val);
                this.values[H][W] = sf_new;
                return DONE;
            }

        } else {//case2: the subgrid exists, just replace
            sf.dat[index] = val;
            return DONE;
        }

    }

    @Override
    public void checkCompress() {
        int changes = 0;
        int singulars = 0;
        for (int h = 0; h <= H_s; h++) {
            for (int w = 0; w <= W_s; w++) {
                SubInt sf = this.values[h][w];
                if (sf == null) {
                    continue;
                }
                Integer b = sf.canBeCompressed();
                if (b != null) {
                    this.values[h][w] = new SubInt(b, null);
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
