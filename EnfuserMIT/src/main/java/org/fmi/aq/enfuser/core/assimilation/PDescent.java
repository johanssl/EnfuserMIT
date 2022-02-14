/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import java.util.logging.Level;
import static org.fmi.aq.enfuser.core.assimilation.WSSEbuilder.STATE_LEN;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.core.receptor.RPconcentrations;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 * This class provides the algorithm to compute an inner gradient descend
 * solution for data source penalties, WHEN the emission source state has been
 * fixed.
 *
 * The iterations starts from an arbitrary initial state and then proceeds step
 * by step to the global minimum. The minimum on this case is mainly dependent
 * on (model prediction - observation) the bias. However, we weight these bias
 * additions per each emission source's credibility. This way the algorithm can
 * balance between prediction bias and source reliability.
 *
 * The algorithm operates on a discrete state matrix (row per each measurement
 * source). The column of the state matrix corresponds to different emission
 * source penalty weights. The higher the penalty, the less the measurement bias
 * affects the overall combined total bias.
 *
 * @author Lasse Johansson
 */
public class PDescent {

    public int[] stateIndex;
    Assimilator a;
    HourlyAdjustment ol;

    private int iters = 0;
    public float currentMin = Float.MAX_VALUE;

    private final static int DIR_NEG = -1;
    private final static int DIR_POS = 1;
    private final static int[] DIRS = {DIR_NEG, DIR_POS};
    Observation LOOVo = null;
    float[] LOOVexps = null;
    float[] e2s;//squared errors

    /**
     * The constructor
     *
     * @param a the Assimilator that holds measurement data for the task.
     * @param ol The current overrideList which fixes the emission source
     * states.
     */
    public PDescent(Assimilator a, HourlyAdjustment ol) {
        this.stateIndex = new int[a.obs.size()];// index 0 as default
        this.a = a;
        this.ol = ol;
        e2s = new float[a.obs.size()];

        for (int k = 0; k < a.obs.size(); k++) {
            Observation o = a.obs.get(k);
            if (o == null) {
                continue;
            }

            float[] expComps = o.incorporatedEnv.ccat.getExpectances_assimilation(ol, a.typeInt, a.ens, o.incorporatedEnv);
            float exp = FuserTools.sum(expComps);

            StationSource s = a.sts.get(k);
            if (a.trueLOOV && a.currLOOV!=null && s == a.currLOOV) {
                if (!a.isPrevObs.get(k)) {
                    LOOVo = o;
                    LOOVexps = expComps;
                }
                e2s[k]=0;
                continue;
                //e2 will be zero => cannot affect the outcome.
            }
             
            float e2 = (o.value - exp) * (o.value - exp);
            e2s[k] = e2;

        }//for obs

    }
    

    /**
     * Produce a text-form summary of the current state of this PDescent
 iterator
     *
     * @return a String summary.
     */
    public String printoutState() {
        String s = ("===> Weight Penalty Stochastic Descend <==== iters = " + this.iters + "\n");
        for (int c = 0; c < this.a.obs.size(); c++) {
            if (a.obs.get(c) == null) {
                continue;
            }
            String line = a.obs.get(c).ID + ": \t";
            int stateI = this.stateIndex[c];

            line += a.getDataFusionParams().printoutStateLine(stateI);

            line += "\n";
            s += line;
        }//for cats

        s += HourlyAdjustment.getHeader(a.typeInt) + "\n" + this.ol.getString(true,a.ens.ops) + "\n";

        return s;
    }

    public float wsumBias_corr = 0;

    /**
     * After descend iteration has been concluded the "final" outcome can be
     * stored to the observations themselves (Observation.varPenalty).
     */
    public void flagObsPenalties() {
        boolean skipPrevObs=true;
        float[] wPOA = a.getDataFusionParams().getAppliedWeights_POA(
                this.stateIndex, skipPrevObs,a);

        float[] wPOAu = a.getDataFusionParams().getUnadjustedWeights_POA(
                this.stateIndex, skipPrevObs,a);
        
        byte[] dbq = a.getDataFusionParams().dbq;
        float[] autoc = a.getDataFusionParams().raw_autoCovar;
        
        //compute, naive evenly distributed POA.
        float even;
        int n =0;
         for (int i = 0; i < this.stateIndex.length; i++) {
            if (a.isPrevObs.get(i)) {
                continue;
            }
            n++;
         }
        even=1000/n;
         
        for (int i = 0; i < this.stateIndex.length; i++) {
            Observation o = a.obs.get(i);
            if (a.isPrevObs.get(i)) {
                continue;
            }
            if (o != null) {
                int obsState = stateIndex[i];
                float POA = wPOA[i];//weight: promilles of total
                float POAu = wPOAu[i];//weight: promilles of total before penalties
                byte db = dbq[i];//db assigned variability rating
                float auto = autoc[i];//hourly autocovariance, possible smoothed with averaging.
                
                 o.setDataFusionOutcome(obsState,even, POA,POAu,db,auto);
                
            }
        }
    }

    /**
     * Iterate one step further and update the state matrix.
     *
     * @param rad define the radius (step length) of the iteration search. E.g.,
     * if no improvement is found with radius 1, then it can be searched again
     * with radius 2. In any case, both directions (+-) will be tested for
     * improvements.
     * @return a boolean value: true in case a better current state was found.
     * returns false if better solution was not found.
     */
    private boolean getNextImprovement(int rad) {

        int index = -1;
        int dir = -1;
        float optFunc = Float.MAX_VALUE;

        for (int i = 0; i < a.obs.size(); i++) {

            if (a.obs.get(i) == null) {
                continue;
            }

            //check negative and positive if possible
            for (int dr : DIRS) {
                dr *= rad;
                float testFuncVal = testImprovement(dr, i);
                if (testFuncVal < optFunc) {//this is an improvement, log as current best
                    optFunc = testFuncVal;
                    dir = dr;
                    index = i;
                    //check a double step improvement which could save a lot of time if found
                    testFuncVal = testImprovement(dr * 2, i);
                    if (testFuncVal < optFunc) {//this is an improvement, log as current best
                        optFunc = testFuncVal;
                        dir = dr * 2;
                        //check a double step improvement

                    }
                }
            }

        }//for LEN

        //check outcome
        if (optFunc < currentMin) {
            currentMin = optFunc;
            this.stateIndex[index] += dir;//UPDATE STATE 
            return true;
            
        } else {
            return false;//no improvement found
        }

    }

    /**
     * Launch the descend iteration. In case the state cannot be improved in the
     * iteration with step size 1, a jump with step-size 2 is attempted. If this
     * doesn't lead to improvements then the iteration terminates: a global
     * minimum has been found.
     */
    public void descendToMinimum() {
        currentMin = Float.MAX_VALUE;
        boolean cont = true;
        while (cont) {
            cont = this.getNextImprovement(1);
            if (!cont) {
                cont = this.getNextImprovement(2);//once more with bigger jumps, in case a local minimum has been encountered.
            }
        }
        //finalizeStats(); 
    }

    /**
     * For a given test step (dr) for data source (row j) the state is updated
     * by moving the current state by 'dr'. Then the optimality function is
     * updated and returned. It is possible that the state update by 'dr' would
     * lead to IndexOutOfBounds if, eg., the current state for row 'j' is the
     * 0th or the last element. In such case the method return Float.MAX_VALUE
     * (this is the worst outcome since a minimum is being searched)
     *
     * @param dr the state update index addition (e.g., -2,-1,1 or 2.
     * @param updateIndex the row for which the test is performed.
     * @return the updated optimality function state (less is better)
     */
    private float testImprovement(int dr, int updateIndex) {
        WSSEbuilder params = a.getDataFusionParams();
        if (this.stateIndex[updateIndex] + dr < 0 || this.stateIndex[updateIndex] + dr >= STATE_LEN) {
            return Float.MAX_VALUE;//cannot compute, out of bounds
        }
        float e2_sum = 0;

        a.getDataFusionParams().resetCostFunction();
        for (int i = 0; i < this.e2s.length; i++) {
            if (a.obs.get(i) == null) {
                continue;
            }

            float e2 = this.e2s[i];//e2 for this measurement i (the penalty state does not affect the error itself)

            if (i == updateIndex) {
                this.stateIndex[i] += dr;//============test a new state! Now it is temporarily set
            }
            int state = this.stateIndex[i];//the penalty index state of ith observation 
            e2_sum += params.getWSE(i, state, e2);

            if (i == updateIndex) {
                this.stateIndex[i] -= dr;//=============revert to original state
            }
        }

        a.penaltyIters++;
        this.iters++;
        float returnable = params.formTotalCost(e2_sum);
        params.resetCostFunction();

        return returnable;
    }

    /**
     *After True Leave One Out Validation (LOOV), log the results to the measurement
     * that was being operated on. This is the data fusion outcome when the
     * measurement was omitted from the data fusion by setting it's squared
     * error to zero.
     */
    protected void flagLOOV() {
        if (this.LOOVo==null) {
            EnfuserLogger.log(Level.INFO, this.getClass(),
                    "LOOV observation is missing? "
                            + a.ens.ops.VAR_NAME(a.typeInt) +", "+ a.currLOOV.ID);
            return;
        }
       this.LOOVo.addValidationValue(a.typeInt,this.LOOVexps);
    }

}
