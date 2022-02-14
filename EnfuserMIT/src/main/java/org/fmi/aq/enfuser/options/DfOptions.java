/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 * Gather all Data Fusion -related settings and parameters in this class.
 * DFOptions will only be used inside FusionOptions.
 * @author johanssl
 */
public class DfOptions {

    public static final int AP_COSTLESS = -1;
    public static final int AP_LOW = 0; //adapter params for datafusion, lowamount of adjustments
    public static final int AP_MED = 1; //adapter params for datafusion, med adjustments
    public static final int AP_HIGH = 2; //adapter params for datafusion, high adjustments
    
    public int adapterParamStrength = AP_MED;//how much df algorithm distributes penaties HIGH => many penalties
    
    //learning rates: these arquably would work also as regional option, but the reload
    //of FusionOptions would be detrimential for this purpose.
    private float LEARNING_SAFETY_MIN = 0.7f;
    private float LEARNING_SAFETY_MAX = 2f;
    private float LEARNING_RATE = 0.01f;
    //data fusion parameters
    public boolean df_trueLOOV = false;
    public float df_history_w = 0.4f;//non zero value includes previous hour observations to data fusion with this weight
    private boolean df_disabled = false;
    
    double[] df_autocovar_coeffs = null;//these parameters, if non-null, van affect how much the measurement provider autocovariance is allowed to affect initial weight.
    public boolean CQL_restore=false;//longer-term memory toggled on/off
    public float dataFusion_ignoreQualityLimit = 100;//this is "varScaler" at db.csv. With a value larger than this, the source will be ignored in data fusion.
    
    
    public DfOptions(ERCFarguments args) {
        this.setFromERCF(args);
    }
    
    /**
     * Read settings from ERCF arguments.
     * @param args 
     */
    private void setFromERCF(ERCFarguments args) {
      
        //init learning vars
            String l = args.get(ERCFarguments.DATAFUSION_MEMORY_FACTORS);
            try {
                if (l != null) {
                    String[] split = l.split(",");
                    LEARNING_SAFETY_MIN = Double.valueOf(split[0]).floatValue();
                    LEARNING_SAFETY_MAX = Double.valueOf(split[1]).floatValue();
                    LEARNING_RATE = Double.valueOf(split[2]).floatValue();
                    EnfuserLogger.log(Level.FINER,this.getClass(),
                            "Learning min/max and rate set (" + split[0] 
                            + "/" + split[1] + "," + split[2] + ")");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        this.CQL_restore =  args.booleanSetting( ERCFarguments.DATAFUSION_MEMORY,false);
       
       String coeffs = args.getCustomContent(ERCFarguments.CUSTOM_DF_AUTOCOVARS);
       if (coeffs!=null) {
           EnfuserLogger.log(Level.FINER,this.getClass(),
                   "Reading custom data fusion coefficients: "+ coeffs);
           String[] split = coeffs.split(",");
           this.df_autocovar_coeffs = new double[]{
           Double.valueOf(split[0]),//pow
           Double.valueOf(split[1]),//w
           Double.valueOf(split[2]),//missing   
           };
       }     
            
     
           boolean loov_df = args.booleanSetting(ERCFarguments.DF_LOOV,false);
           if (loov_df) {
               EnfuserLogger.log(Level.FINE,this.getClass(),"=====================================\n"
                       + "True Leave One Out Validation (LOOV) data fusion enabled!"
                       + "========================================\n");
               this.df_trueLOOV = true;
           }
       
       String strength = args.get(ERCFarguments.DF_STRENGTH) +"";
       
       if (strength.contains("high")) {
           adapterParamStrength = AP_HIGH;
       } else if (strength.contains("med")) {
           adapterParamStrength =AP_MED;
       } else if (strength.contains("low")) {
           adapterParamStrength = AP_LOW;
           
       } else if (strength.contains("costless")) {
           adapterParamStrength = AP_COSTLESS;
           
       } else if (strength.contains("off")) {
           this.df_disabled =true;
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                   "DATA FUSION has been disabled.");
       }

    }
    
    public void setLearning_minMaxRate(double min, double max, double rate) {
       LEARNING_SAFETY_MIN = (float)min;
       LEARNING_SAFETY_MAX = (float)max;
       LEARNING_RATE = (float)rate;
    }
        
    public float LEARNING_RATE() {
        return LEARNING_RATE;
    }
    public float LEARNING_SAFETY_MIN() {
        return LEARNING_SAFETY_MIN;
    }
     public float LEARNING_SAFETY_MAX() {
        return LEARNING_SAFETY_MAX;
    } 

    public boolean dfDisabled() {
        return this.df_disabled;
    }
}
