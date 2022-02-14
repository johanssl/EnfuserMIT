/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import java.util.ArrayList;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class contains the discrete gradient descend algorithm for the
 * assessment of OverrideList.
 *
 * This class is for the MAIN iteration of the overall two-step discrete
 * gradient descend algorithm. The purpose of the class is to optimize emission
 * source states.
 *
 * For each iteration step for emission source adjustment, another one for
 * measurement credibility is launched. This is done by PenaltyDescend which can
 * be used when the emission source state has been fixed.
 *
 * @author Lasse Johansson
 */
public class ADescent {

    public int[] stateIndex;
    Assimilator a;

    public ArrayList<Float>[] states;

    public float currentMin = Float.MAX_VALUE;
    public float worst = Float.MAX_VALUE * -1;

    private final static int DIR_NEG = -1;
    private final static int DIR_POS = 1;
    private final static int[] DIRS = {DIR_NEG, DIR_POS};

    // int counter =0;
    String stamp;
    public PDescent best;
    private int iters = 0;

    /**
     * Constructor for OverDescend.
     *
     * @param a Assimilator instance that holds the necessary input data
 (measurements) for the algorithm.
     */
    public ADescent(Assimilator a) {
        this.stateIndex = new int[a.CATS.CATNAMES.length];// index 0 as default
        this.a = a;
        states = a.catIters;
        //start from the middle.
        for (int c : a.CATS.C) {
            int middleInd = states[c].size() / 2;
            if (middleInd >= states[c].size()) {
                middleInd = states[c].size() - 1;
            }
            stateIndex[c] = middleInd;
        }
        stamp = a.time.getStringDate();

    }

    /**
     * get a text-form printout for the current state of the algorithm.
     *
     * @return a String text.
     */
    public String printoutState() {
        String s = ("===> Override Stochastic Descend <==== iters = " + this.iters 
                + "\n observed Min = " + a.minObs + ", maxBG = " + a.maxBG + "\n");
        for (int c : a.CATS.C) {
            String line = a.CATS.CATNAMES[c] + ": \t";
            int stateI = this.stateIndex[c];
            for (int j = 0; j < this.states[c].size(); j++) {
                double val = editPrecision(this.states[c].get(j), 2);
                if (j == stateI) {
                    line += ",|" + val + "|\t";
                } else {
                    line += " ," + val + " ";
                }
            }
            line += "\n";
            s += line;
        }//for cats
        return s;
    }

    /**
     * Return an updated state for category c for the case when the discrete
     * current state index is updated by 'add'.
     *
     * @param c the Emission category index
     * @param add an index update addition.
     *
     * Example: for category 'traffic' the current state index is [0] and the
     * state value could be e.g., 0.5. For an addition of 1, the state index is
     * [0 +1] could return 0.6.
     * @return a float value for the updated state.
     */
    private float currentStateIndex(int c, int add) {
        int index = this.stateIndex[c];
        return this.states[c].get(index + add);
    }

    /**
     * Return an HourlyAdjustment which corresponds to the current state of the
 iteration but for emission category c_test the state is updated by 'dr'
 (direction).
     *
     * @param c_test emission category index for udpate.
     * @param dr the direction and length of the test step.
     * @return a new HourlyAdjustment which holds the updated state.
     */
    private HourlyAdjustment getCurrentOverride(int c_test, int dr) {

        HourlyAdjustment ol = new HourlyAdjustment(stamp, a.obs, a.sts, a.isPrevObs, a.typeInt);
        for (int c : a.CATS.C) {

            if (c != c_test) {
                ol.overrides[c] = this.currentStateIndex(c, 0);
            } else {//apply something different, return null if this cannot be done

                try {
                    ol.overrides[c] = this.currentStateIndex(c, dr);
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }

            }

        }//for CAT
        return ol;
    }

    /**
     * For an iteration step length of 'rad', iterate one step further by going
     * through all the emission categories one by one. The emission category for
     * which the updated state holds the most NOTABLE overall improvement, will
     * be selected as the final outcome of the iteration (for which the sate
     * will be updated). This means that only one emission category state is to
     * be updated during the iteration.
     *
     * @param rad iteration step radius.
     * @return boolean value: true in case in improvement was found (for one or
     * more emission categories). Returns false in case no improvements was
     * found.
     */
    private boolean getNextImprovement(int rad) {

        int imprCat = -1;
        int dir = -1;

        for (int c : a.CATS.C) {

            if (this.states[c].size() == 1) {
                continue;//nothing to do, the only possible state is at index 0
            }
            //check negative and positive if possible
            for (int dr : DIRS) {
                dr *= rad;
                PDescent pd = testImprovement(dr, c);
                if (pd != null && pd.currentMin < this.currentMin) {//this is an improvement, log as current best.
                    this.currentMin = pd.currentMin;
                    dir = dr;
                    imprCat = c;
                    this.best = pd;
                    //check a double step improvement which could save a lot of time if found
                    PDescent pd2 = testImprovement(dr * 2, c);
                    if (pd2 != null && pd2.currentMin < this.currentMin) {//this is an improvement, log as current best
                        this.currentMin = pd2.currentMin;
                        dir = dr * 2;
                        this.best = pd2;
                        //check a double step improvement

                    }
                }//if new best
                else if (pd != null && pd.currentMin > worst) {
                    worst = pd.currentMin;
                }
            }//for directions 

        }//for CATS

        //check outcome
        if (imprCat >= 0) {
            this.stateIndex[imprCat] += dir;//UPDATE STATE 
            return true;
        } else {
            return false;//no improvement found
        }

    }

    /**
     * Launch the full algorithm iteration starting from an arbitrary initial
     * state. The iteration is based on the overall optimality function that is
     * to be improved step by step (reduce it each step). After each improvement
     * the state of the emission categories are updated as well.
     *
     *
     */
    public void descendToMinimum() {

        long t = System.currentTimeMillis();

        currentMin = Float.MAX_VALUE;
        boolean cont = true;
        while (cont) {
            cont = this.getNextImprovement(1);
            if (!cont) {
                for (int n = 2; n < 5; n++) {
                    cont = this.getNextImprovement(n);//once more with bigger jumps, in case a local minimum has been encountered.
                    if (cont) {
                        break;
                    }
                }
            }
        }
        //this.best.finalizeStats();
        if (this.best == null) {
            EnfuserLogger.log(Level.FINER,ADescent.class,
                    "Data fusion DescentToMinimum resulted in null! " 
                            + a.VARA.VAR_NAME(a.typeInt) + ", " + a.time.getStringDate());
            return;
        }
        this.best.ol.setupPrintouts(a.ens);
        long t2 = System.currentTimeMillis();
        
        if (a.ens.g.getLoggingLevel().intValue()<= Level.FINER.intValue()) {
            EnfuserLogger.log(Level.FINER,ADescent.class,"COMPLETED. Took " + (t2 - t) + "ms.");
            EnfuserLogger.log(Level.FINER,ADescent.class,this.printoutState());
            EnfuserLogger.log(Level.FINER,ADescent.class,this.best.printoutState());
            EnfuserLogger.log(Level.FINER,ADescent.class,"==========================================");
        }

    }

    /**
     * For a given test step (dr) for emission category (cat) the state is
     * updated by moving the current state by 'dr'.
     *
     * It is possible that the state update by 'dr' would lead to
     * IndexOutOfBounds. In such case the method returns null.
     *
     * @param dr the state update index addition (e.g., -2,-1,1 or 2.
     * @param cat the emission category for which the test is performed.
     *
     * @return A completed PDescent instance that holds the updated
 optimization function value for this tested state.
     */
    private PDescent testImprovement(int dr, int cat) {

        HourlyAdjustment ol = this.getCurrentOverride(cat, dr);
        if (ol == null) {
            return null; //cannot be tested, skip
        }
        a.overIters++;
        this.iters++;
        PDescent pd = new PDescent(a, ol);
        pd.descendToMinimum();
        return pd;

    }

}
