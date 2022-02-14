/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.varproxy;

import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.interfacing.InstanceLoader;


/**
 * This class provides methods to fill in emission content for AbstractEmitters
 * for specific variables types that are commonly not accounted for in certain
 * emitter types (mainly gridded sets in SHG and netCDF).
 *
 * @author johanssl
 */
public class EmitterProxies {

  private Expression[] prox;
  private final VarAssociations VARA;
public EmitterProxies(VarAssociations VARA) {
    this.VARA =VARA;
    prox = new Expression[VARA.Q_NAMES.length];
    for (int q :VARA.Q) {
        Expression ex = getEmitterProxy(VARA,q);
        prox[q]=ex;//this should be AffineExpression 
    }
    
}

/**
 * Give a short textual description of the emitter proxy in a single line.
 * @return description.
 */
public String shortDescription() {
    String s =", uses emitterProxy for ";
    int adds =0;
    for (int q :VARA.Q) {    
        if (prox[q]!=null) {
          s+=VARA.VAR_NAME(q)+",";
          adds++;
        }
    }
    if (adds==0) return "";
    return s;
}
          
    /**
     * For a given emission mass array, fill in missing content as a function of
     * emission category
     *
     * @param qtemp the array to process
     */
   public void fillQ(float[] qtemp) {
       if (qtemp==null) return;
       for (int q :VARA.Q) {
           if (this.prox[q] !=null && qtemp[q]==0) {//recipee exists and the curren value is zero => GO
               //for example, LDSA prox exists and qtemp[LDSA]=0
               qtemp[q] = this.prox[q].evaluateFromPrimaries(qtemp);
           }
       } 
    }

   
    /**
     * For the given primary variable type, return an EmitterProxy from the 
     * variableAssociations. 
     * @param VARA varAssociations instance (may be Regionally customized)
     * @param q the primary variable type index
     * @return the specified EmitterProxy as an mathematical expression (Affine)
     * If not specified, returns null.
     */       
public Expression getPredictionProxy(VarAssociations VARA, int q) {
    String func= VARA.getVariablePropertyString(q,VARA.HEADER_PRED_PROXY);
     try {
        if (func!=null && func.length() > Expression.AFFINE_TAG.length()) {
            return InstanceLoader.getFromString(func,VARA);
        }
        
     }catch (Exception e) {
             e.printStackTrace();
     }
     return null;
}

    /**
     * For the given primary variable type, return an EmitterProxy from the 
     * variableAssociations. 
     * @param VARA varAssociations instance (may be Regionally customized)
     * @param q the primary variable type index
     * @return the specified EmitterProxy as an mathematical expression (Affine)
     * If not specified, returns null.
     */
    private Expression getEmitterProxy(VarAssociations VARA, int q) {
     String func= VARA.getVariablePropertyString(q,VARA.HEADER_EMITTER_PROXY);
     try {
        if (func!=null && func.length() > Expression.AFFINE_TAG.length()) {
            return InstanceLoader.getFromString(func,VARA);
        }
        
     }catch (Exception e) {
             e.printStackTrace();
     }
     return null;
    }

}
