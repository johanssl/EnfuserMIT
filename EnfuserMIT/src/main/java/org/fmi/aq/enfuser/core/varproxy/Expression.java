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
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.options.VarAssociations;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This abstract class defines a mathematical expression for a computation
 * that is a function of the known Enfuser variable types.
 * Main use-case are e.g., proxy formulas (e.g., H2SO4) and filling empty
 * emission factors, but there can be other versatile use-cases as well.
 * 
 * For secondary variable types these Expressions are used for computations
 * based on the primary types and meteorological types.
 * 
 * @author johanssl
 */
public abstract class Expression {
    
   
   public final static String AFFINE_TAG= "AFFINE<";
    
   protected String func; 
   protected ArrayList<Integer> parametersPresent;
   protected HashMap<String,double[]> limits;
   public VarAssociations VARA; 
   
  /**
   * List all varibles the expression uses.
   * @param nonMet is true, then only non-met variables are listed.
   * @return a list of names
   */
 public ArrayList<String> getVariableList(boolean nonMet) {
     ArrayList<String> arr = new ArrayList<>();
     for (int typeInt:this.parametersPresent) {
         if (nonMet && VARA.isMetVar[typeInt])continue;
         String name = VARA.VAR_NAME(typeInt);
         arr.add(name);
     }
     return arr;
 }
 
 
    public boolean hasVariableType(int typeInt) {
      return this.parametersPresent.contains(typeInt);
    }

 /**
  * Evaluate the expression value when an RP object is given as input.
  * @param env the RP object that allows the computation of primary types.
  * @param ens the core, that is able to deliver meteorological input.
  * @return expression value.
  */ 
 public abstract double evaluate(RP env, FusionCore ens);
 
 public abstract float[] evaluateComponents(RP env, FusionCore ens);
 
 /**
  * 
  * @param temp a temporary HashMap that may or may not be required
  * for the computations (symbolic expressions)
  * @param met
  * @param limitCheck
  * @return 
  */
 public abstract double evaluate(HashMap<String,Double> temp, Met met,
           boolean limitCheck);
    
/**
 * 
 * @param af
 * @param h
 * @param w
 * @param met
 * @param temp a temporary HashMap that may or may not be required
  * for the computations (symbolic expressions)
 * @return 
 */
  public abstract double evaluate(AreaFusion af, int h, int w, Met met,
          HashMap<String,Double> temp);
  

 public abstract float evaluateFromPrimaries(float[] q);
 
 public abstract String getFunctionString();
 
 protected double[] getLimits(String var) {
     return this.limits.get(var);
 }
    
 /**
  * Printout a longer description of the parsed Expression. This will
  * use the Logging level FINER, so it may not cause a printout to occur.
  */ 
public void printoutInfo() {
  EnfuserLogger.log(Level.FINER,Expression.class,"===============================\nSymbolicExpression");

      EnfuserLogger.log(Level.FINER,Expression.class,"\t has prediction function: "+this.getFunctionString() );
      for (String var:getVariableList(false)) {
          double[] lims = getLimits(var);
          String lim ="";
          if (lims!=null) lim =", has limit: "+ lims[0] + " to "+ lims[1];
          EnfuserLogger.log(Level.FINER,Expression.class,"\t\t" + var + lim);
      }

  EnfuserLogger.log(Level.FINER,Expression.class,"==========================================");  
}  
 
 /**
  * Find parameter limit info in the expression and store them for the respective
  * parameter name
  * @param name parameter name
  * @param funcLine the full expression that  may include MIN, MAX tags for
  * one or more parameters.
  * 
  * @return double[] minmax. if not found, then returns null.
  * If only min or max is found then the one missing will not affect
  * the computations.
  */
 protected static double[] parseLimits(String name, String funcLine) {
     Double pmin = parseMin(name,funcLine);
     Double pmax = parseMax(name,funcLine);
     
     if (pmin==null && pmax==null) {
         return null;
     }
     
     double[] lims = {Double.MAX_VALUE*-1,Double.MAX_VALUE};
     if (pmin!=null) lims[0]=pmin;
     if (pmax!=null) lims[1]=pmax;
     
     return lims;
 }
 
 /**
  * Find an instruction to variable limit value for minimum.
  * If not found then returns null.
  * @param name variable type name (not index)
  * @param funcLine the full function, expression line as String.
  * @return parsed numerical minimum (or null)
  */
 protected static Double parseMin(String name, String funcLine) {
     String sval = getBetween(funcLine, name+"_MIN<",">");
     if (sval!=null) {
         return Double.valueOf(sval);
     } else {
         return null;
     } 
 } 
 
  /**
  * Find an instruction to variable limit value for maximum.
  * If not found then returns null.
  * @param name variable type name (not index)
  * @param funcLine the full function, expression line as String.
  * @return parsed numerical maximum (or null)
  */
  protected static Double parseMax(String name, String funcLine) {
     String sval = getBetween(funcLine, name+"_MAX<",">");
     if (sval!=null) {
         return Double.valueOf(sval);
     } else {
         return null;
     } 
 }  
    
}
