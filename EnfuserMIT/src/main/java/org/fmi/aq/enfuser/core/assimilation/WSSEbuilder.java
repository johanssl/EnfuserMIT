/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.assimilation;

import org.fmi.aq.enfuser.datapack.source.StationSource;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.enfuser.options.DfOptions;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class WSSEbuilder {

    public double AUTOCOVAR_POW =0.4;
    public float  AUTOCOVAR_W =0.6f;
    public float  AUTOCOVAR_MISSING_AVE =3f;
    
    public final static int STATE_LEN = 6;
    public final static String[] STATE_NAMES = {"Good", "Fair", "Moderate",
        "Poor", "Very_poor", "Outlier"};

     /**
     * Defines the SSE reduction factor that is applied to the squared
     * prediction error for different data point quality classifications (See:
     * STATE_NAMES).
     */
    protected final float[] wReducer
            = {1f, 0.6f, 0.4f, 0.2f, 0.1f, 0.05f};
    
    public final static String[] ADAPTER_PARAM_STRENGTHS = {
        "LOW",
        "MED",
        "HIGH"
    };
    
    protected float[] raw_weights;
    protected byte[] dbq;
    protected float[] raw_autoCovar;
    float rawSum = 0;

    private final double B;// use [0.2,1] Larger value => more penalty weights are being associated
    private float costFunction=0;
    private float count =0;
    private final float[] stateCosts;

    public WSSEbuilder(FusionOptions ops) {

        if (ops.dfOps.adapterParamStrength == DfOptions.AP_LOW) {
            this.B = -1.5; 

        } else if (ops.dfOps.adapterParamStrength == DfOptions.AP_COSTLESS) {
            this.B = -30;
            
        } else if (ops.dfOps.adapterParamStrength == DfOptions.AP_MED) {
            this.B = -1.2;

        } else {//high
            this.B = -1;
        }

        stateCosts = new float[STATE_LEN];
        for (int i = 0; i < STATE_LEN; i++) {
            stateCosts[i] = (float) (Math.pow(wReducer[i], B));
        }
    }
    

    public void initWeights(Assimilator a, FusionCore ens) {

        this.raw_weights = new float[a.obs.size()];
        this.raw_autoCovar = new float[a.obs.size()];
        this.dbq = new byte[a.obs.size()];
        
        rawSum = 0;
        
        double[] autocovar_coeffs = ens.ops.getDF_autocovarFactors();
        if (autocovar_coeffs!=null && autocovar_coeffs.length==3) {
          
                AUTOCOVAR_POW = autocovar_coeffs[0];
                AUTOCOVAR_W = (float)autocovar_coeffs[1];
                AUTOCOVAR_MISSING_AVE = (float)autocovar_coeffs[2];
            
        }
        
        //setup raw autocovar stats for all points
        int n =1;
        float ave =0;
        for (int i = 0; i < a.obs.size(); i++) {
            if (a.obs.get(i) == null) {
                continue;
            }
            StationSource st = a.sts.get(i);
            float diurnalAutoCovar = st.hourlyAutoCovar(a.time.hour);
            if (diurnalAutoCovar>0) diurnalAutoCovar = (float)Math.pow(diurnalAutoCovar, AUTOCOVAR_POW);
                n++;
                ave+=diurnalAutoCovar;
            this.raw_autoCovar[i] = diurnalAutoCovar;//can be zero, and this is a problem (see below)
        }//for obs

        
       //raw weights and autocovar smoothing
       ave/=n;
       for (int i = 0; i < a.obs.size(); i++) {
            if (a.obs.get(i) == null) {
                continue;
            }
            StationSource st = a.sts.get(i);
            
            if (this.raw_autoCovar[i]> 0) {
                this.raw_autoCovar[i] = AUTOCOVAR_W*this.raw_autoCovar[i] + (1f-AUTOCOVAR_W)*ave;//smoothen the covar effect
                
            } else {//this can happen for "flatline" sources that are in general, unreliable
                this.raw_autoCovar[i] = AUTOCOVAR_MISSING_AVE*ave;
            }
            
            float dbVarScaler = st.dbl.varScaler(a.typeInt);//larger value => lower effect on SSE.
            if (dbVarScaler < 1) {
                dbVarScaler = 1;
            }
            
            this.dbq[i] = (byte)dbVarScaler;
            float var = dbVarScaler * this.raw_autoCovar[i];
            raw_weights[i] = 1f / var;
            //raw_weights_freeProg[i] = (float)freeProgW/diurnalAutoCovar;

            if (a.isPrevObs.get(i) == true) {
                raw_weights[i] *= a.HISTORY_W;//weight these less, but enough so that continuity is achieved
            }         //concentration estimates by category
            rawSum += raw_weights[i];
       }//for obs once more
        
        
        //finally, normalize weights
        for (int i = 0; i < a.obs.size(); i++) {
            raw_weights[i] /= rawSum;
        }
    }

    protected float getWSE(int i, int state, float e2) {
        float w = this.raw_weights[i];
        if (w == 0) {
            return 0;
        }

        float redF = wReducer[state];//the higher the state, the lower this becomes (--> e2 becomes lower)
        float wReduced = w*redF;
        costFunction+=stateCosts[state]; //this is equal to p^-1
        count++;
        
        return wReduced * e2;//e.g. pen is 0.5 then this added error is smaller than e2 (half in this case).
   
    }

    /**
     * Reset current total cost function and possible discount sums.
     */
    protected void resetCostFunction() {
        this.costFunction = 0;
        this.count=0;
    }

    protected float formTotalCost(float e2_sum) {
        if (count<=0) return Float.MAX_VALUE;
        float costFuncSum = this.costFunction/count;
        return e2_sum * costFuncSum;
    }

    float[] getAppliedWeights_POA(int[] stateIndex, boolean skipPreviousObs, Assimilator a) {
        int LEN = this.raw_weights.length;
        float[] aves = new float[LEN];
        float wsum = 0;
        for (int i = 0; i < LEN; i++) {
            if (skipPreviousObs) {
                if (a.isPrevObs.get(i)) {
                    continue;
                }
            }
            int state = stateIndex[i];
            float penalty = this.wReducer[state];
            float w = this.raw_weights[i]*penalty;

            aves[i] = w;
            wsum += w;

        }//for all observation length
        //normalize in averages
        for (int i = 0; i < LEN; i++) {
            aves[i]*=1000f/wsum;//to promilles of average
            
            //now these values are relative to average
            //e.g., a value of 2 means that the weight was twice the average amount
        }
        return aves;
    }
    
    
    float[] getUnadjustedWeights_POA(int[] stateIndex, boolean skipPreviousObs, Assimilator a) {
        int LEN = this.raw_weights.length;
        float[] aves = new float[LEN];
        float wsum = 0;
        for (int i = 0; i < LEN; i++) {
            if (skipPreviousObs) {
                if (a.isPrevObs.get(i)) {
                    continue;
                }
            }
            float w = this.raw_weights[i];
            aves[i] = w;
            wsum += w;

        }//for all observation length
        //normalize in averages
        for (int i = 0; i < LEN; i++) {
            aves[i]*=1000f/wsum;//to promilles of average
            //now these values are relative to average
            //e.g., a value of 2 means that the weight was twice the average amount
        }
        return aves;
    }
    
    public String printoutStateLine(int stateI) {
        String line = "";
        for (int j = 0; j < STATE_NAMES.length; j++) {
            String val = STATE_NAMES[j];
            if (j == stateI) {
                line += ",|" + val + "|\t";
            } else {
                line += " ," + val + " ";
            }
        }
        return line;
    }
    
        public void printOutRawWeights(Assimilator a) {
        EnfuserLogger.log(Level.FINER,WSSEbuilder.class,"AdapterParams: printout for raw weights:");
        for (int i = 0; i < a.obs.size(); i++) {
            if (a.obs.get(i) == null) {
                continue;
            }
            String historic = "";
            if (a.isPrevObs.get(i) == true) {
                historic = " (PREVIOUS)";
            }

            String line
                    = "w=" + (int) (this.raw_weights[i] * 1000)
                    + "%%, 1h_autoCov=" + this.raw_autoCovar[i]
                    + historic;

            EnfuserLogger.log(Level.FINER,WSSEbuilder.class,line);
        }

    }
}
