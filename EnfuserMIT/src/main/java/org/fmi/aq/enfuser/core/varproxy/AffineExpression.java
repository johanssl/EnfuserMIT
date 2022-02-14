/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.varproxy;

import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.RP;
import static org.fmi.aq.enfuser.core.varproxy.Expression.AFFINE_TAG;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.options.VarAssociations;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class is to describe a simple mathematical expression for 
 * affine combinations in the form y = a*x1 + b*x2 +...
 * where the coefficients can have negative values as well.
 * The variables x1,x2,... should be primary variable names.
 * 
 * These can be used to describe variable type relations in the variableAssociations.csv.
 * For example: coarsePM input measurements (input_proxy_recipee) can be proxied by adding
 * AFFINE(1*PM10 - 1.0*PM25) but using the 'diamond operator'.
 * 
 * It is also possible to add limits to the variable ranges. This is done
 * in a similar fashion than in other Expressions, e.g., adding 'swRad_MIN(10)'
 * 
 * @author johanssl
 */
public class AffineExpression extends Expression{
    

   
  
   float[] Qscalers;
   double[][] limits_q;
   String editedFunc;
   private final static String MULT ="MULTIPLY_OPERATOR";
 public AffineExpression(String funcLine, VarAssociations VARA) {
     this.VARA = VARA;
     func = "affine, "+getBetween (funcLine,AFFINE_TAG, ">");
     this.editedFunc= getBetween(funcLine,AFFINE_TAG, ">").replace(" ", "" );
     EnfuserLogger.log(Level.FINER,AffineExpression.class,"Affine function parsing: "+ this.editedFunc);
     this.editedFunc= editedFunc.replace("+", " " );
     this.editedFunc= editedFunc.replace("-", " -" );

     this.limits = new HashMap<>();
     this.Qscalers = new float[VARA.VAR_LEN()];
     this.limits_q = new double[VARA.VAR_LEN()][];
     this.parametersPresent= new ArrayList<>();
     String ps ="";
     for (int i =0;i<VARA.varLen();i++) {

         String name = VARA.VAR_NAME(i);
         if (func.contains(name)) {
             parametersPresent.add(i);
             ps+=name+",";
             //min,max limits if present
             double[] lims = parseLimits(name, funcLine);
             if (lims!=null){
                 this.limits.put(name, lims);
                 this.limits_q[i] = lims; 
             }
             
         }//if contains
     }//for types
     EnfuserLogger.log(Level.FINER,AffineExpression.class,"\t parametersPresent: "+ ps);
         
     String[] split = editedFunc.split(" ");
     for (String test:split) {
         test = test.replace("*", MULT);
         if (!test.contains(MULT)) {
             EnfuserLogger.log(Level.FINER,AffineExpression.class,"\tskipping '"+test+"'");
             continue;
         }
         String[] scaler_type = test.split(MULT);
         try {
             double val;
             String type = scaler_type[1];
             int typeInt = VARA.getTypeInt(type);
             if (typeInt>=0) {//successfully identified
              val = Double.valueOf(scaler_type[0]);
             } else {//other way around
                 type = scaler_type[0];
                 typeInt = VARA.getTypeInt(type);
                 val = Double.valueOf(scaler_type[1]);
             }
             this.Qscalers[typeInt] = (float)val;
             EnfuserLogger.log(Level.FINER,AffineExpression.class,"\tAffine function: adding parameter "
                     + type +"=>"+val +"("+scaler_type[0]+","+scaler_type[1]+")");
         } catch (Exception e) {
             EnfuserLogger.log(e,Level.WARNING,this.getClass(),
                  "Affine function: parse fail for " + func +", "+test);
         }
     }
     
 }
 
   @Override
 public ArrayList<String> getVariableList(boolean nonMet) {
     ArrayList<String> arr = new ArrayList<>();
     for (int typeInt:this.parametersPresent) {
         if (nonMet && VARA.isMetVar[typeInt])continue;
         String name = VARA.VAR_NAME(typeInt);
         arr.add(name);
     }
     return arr;
 }
 
 /**
  * Check if range limit has been defined for the primary variable type
  * and make sure that the variable value will be in the range of the limits.
  * @param q the primary type index
  * @param val value for the primary type
  * @return possibly adjusted primary type value.
  */
 private double limitCheck(int q, double val) {
     double[] minmax = this.limits_q[q];
     if (minmax==null) return val;
     if (val < minmax[0]) val = minmax[0];
     if (val > minmax[1]) val = minmax[1];
     
     return val;
 }
 

  
   @Override
 public double evaluate(RP env, FusionCore ens) {
    
     float sum =0;
     for (int typeInt :parametersPresent) {


         double val;
         if (VARA.isMetVar[typeInt]) {
            val = env.met.getSafe(typeInt);
         } else {
            val= env.getExpectance(typeInt, ens);  
         }
         val = limitCheck(typeInt,val);
         sum+=val*this.Qscalers[typeInt];

     }
     
     return sum;
 }
 
   @Override
  public float[] evaluateComponents(RP env, FusionCore ens) {
    
     float[] exps = new float[ens.ops.CATS.C.length];
     for (int typeInt :parametersPresent) {

         if (!VARA.isPrimary[typeInt]) continue;//can't support this.
         
         float[] ex= env.getExpectances(typeInt, ens);
         for (int c:ens.ops.CATS.C) {
             exps[c]+= ex[c]*this.Qscalers[typeInt];
         }
     }
     
     return exps;
 }
 
   @Override
   public double evaluate(HashMap<String,Double> temp, Met met,
           boolean limitCheck) {
    float sum =0;
     for (int typeInt :parametersPresent) {
         String name = VARA.VAR_NAME(typeInt);
         
         double val;
         if (VARA.isMetVar[typeInt]) {
            val = met.getSafe(typeInt);
         } else {
            val = temp.get(name);  
         }
         
         if (limitCheck)val = limitCheck(typeInt,val);
         sum+=val*this.Qscalers[typeInt];

     } 
     return sum;
 }
 
   @Override
  public double evaluate(AreaFusion af, int h, int w, Met met,
          HashMap<String,Double> temp) {
  
      float sum =0;
     for (int typeInt :parametersPresent) {

          double val;
         if (VARA.isMetVar[typeInt]) {
            val = met.getSafe(typeInt);
         } else {
            val = af.getConsValue(typeInt, h, w);
  
         }

         val = limitCheck(typeInt,val);
         sum+=val*this.Qscalers[typeInt];

     }
     return sum;
 }
  
  
   @Override
    public float evaluateFromPrimaries(float[] q) {
    
    float sum =0;
     for (int typeInt :parametersPresent) {
         double val = q[typeInt];
         val = limitCheck(typeInt,val);
         sum+=val*this.Qscalers[typeInt];

     } 
     return sum;
 }

    @Override
    public String getFunctionString() {
       return this.editedFunc;
    }
    
    
 

}
