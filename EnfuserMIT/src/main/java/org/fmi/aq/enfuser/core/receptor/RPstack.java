/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.core.combiners.PartialGrid;
import org.fmi.aq.enfuser.options.Categories;
import static org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell.IND_CPM;
import static org.fmi.aq.enfuser.core.gaussian.fpm.FootprintCell.IND_GAS;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 *
 * @author johanssl
 */
public class RPstack {
    
    public float[][] stack_cq;
    private final VarAssociations VARA;
    //partial combiners indexing info
    public int ph;
    public int pw;
    public float p_lat;
    public float p_lon;
    
    public Blocker blocker_midRange;

    public RPstack(FusionOptions ops) {
        this.VARA = ops.VARA;
        stack_cq = new float[ops.CATS.CATNAMES.length][ops.VARA.Q_NAMES.length];
    }
    
    public void setMidRangeReductor(Blocker block) {
        this.blocker_midRange = block;
    }

    public static void scaleAndAutoNOX(float[] Q,VarAssociations VARA) {
            float nox = Q[VARA.VAR_NO2] + Q[VARA.VAR_NO];
            Q[VARA.VAR_NO2] = (Categories.NO2_FRAC_FLAT + Categories.NO2_FRAC_PHOTOCH) * nox;
            Q[VARA.VAR_NO] = Categories.NO_FRAC_FLAT * nox;
            Q[VARA.VAR_O3] = -1 * Categories.NO2_FRAC_PHOTOCH * nox;
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
    public void addAll(int CAT, float[] ems, float[] weights, float amp, int START_READ_INDEX) {
        //copy data
        for (int q : VARA.Q) {
            int ind = q + START_READ_INDEX;
            float w = weights[IND_GAS];
            if (q == VARA.VAR_COARSE_PM) {
                w = weights[IND_CPM];
            }
            this.stack_cq[CAT][q] += ems[ind] * w * amp;
        }//for q
    }

    public void stackDrivers(RPstack dct, float weight) {
        GlobOptions g= GlobOptions.get();
        for (int c : g.CATS.C) {
            for (int q : VARA.Q) {
                this.stack_cq[c][q] += dct.stack_cq[c][q] * weight;
            }
        }
    }

    public void stackAll(ArrayList<PartialGrid> pGrids, float lat, float lon) {
        GlobOptions g= GlobOptions.get();
        for (PartialGrid pg : pGrids) {
            pg.stack(this, lat, lon);
        }

    }

    public float[] categoriesForQ(int q) {
        GlobOptions g= GlobOptions.get();
        float[] cats = new float[g.CATS.CATNAMES.length];
        for (int c : g.CATS.C) {
            cats[c] = this.stack_cq[c][q];
        }
        return cats;
    }

    public void clearDrivers() {
        GlobOptions g= GlobOptions.get();
        for (int c : g.CATS.C) {
            for (int q : VARA.Q) {
                this.stack_cq[c][q] = 0;
            }
        }
    }
   

}
