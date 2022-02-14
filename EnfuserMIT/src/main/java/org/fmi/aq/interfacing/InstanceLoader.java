/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.util.HashMap;
import org.fmi.aq.enfuser.core.gaussian.puff.nesting.RegionalBG;
import org.fmi.aq.enfuser.core.varproxy.AffineExpression;
import org.fmi.aq.enfuser.core.varproxy.Expression;
import static org.fmi.aq.enfuser.core.varproxy.Expression.AFFINE_TAG;
import org.fmi.aq.enfuser.core.varproxy.ProxyRecipe;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 *
 * @author johanssl
 */
public class InstanceLoader {
    
   public static RegionalBG selectNestingStrategy(FusionOptions ops) {
        return null;
   }
   
   
      public static Expression getFromString(String line, VarAssociations VARA)  {
       
       if (line.contains(AFFINE_TAG)) {
         return new AffineExpression(line,VARA);
       } 
       return null;
   } 

    public static void addCustomInstructions(HashMap<Integer, ProxyRecipe> reps,
 VarAssociations VARA) {

    }
    
}
