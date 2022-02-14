/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class PolyCombo {

    ArrayList<double[]> coords_outer = new ArrayList<>();
    ArrayList< ArrayList<double[]>> innards = new ArrayList<>();

    public PolyCombo() {

    }

    public boolean startNew(boolean outer) {
        if (outer && outerIsComplete()) {
            return true;
        }
        return false;

    }

    public void add( Way way, boolean outer) {
        //outer MUST not be complete
        if (outer) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\t adding outer coords.");
            
            for (double[] cc : way.coords) {//iterate member coordinates
                coords_outer.add(cc);
            }//for member coords
            if (outerIsComplete()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\t\tPC: Adding outer coordinates made it COMPLETE");
            }

        } else {//inner
            if (innards.isEmpty()) {
                innards.add(new ArrayList<>());
            }
            if (innerIsComplete()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\tPC: Inner completed: start a new one.");
                
                innards.add(new ArrayList<>());//new inner polygon is added
            }

            ArrayList<double[]> coords = getLatestInner();//fetch the latest inner poly
                EnfuserLogger.log(Level.FINER,this.getClass(),"\tPC: Adding inner coordinates");
            
            for (double[] cc : way.coords) {//iterate member coordinates
                coords.add(cc);
            }//for member coords

            if (innerIsComplete()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\t\tPC: Adding inner coordinates made it COMPLETE");
            }

        }//inner

    }

    private ArrayList<double[]> getLatestInner() {
        return innards.get(innards.size() - 1);//fetch the latest inner poly
    }

    private boolean innerIsComplete() {
        if (innards.isEmpty()) {
            return false;
        }
        ArrayList<double[]> coords = getLatestInner();//fetch the latest inner poly
        if (coords.size() < 3) {
            return false;//cannot be complete yet, two or less points
        }
        return (coords.get(0) == coords.get(coords.size() - 1));//same coordinates => closed
    }

    private boolean outerIsComplete() {
        if (coords_outer.size() < 3) {
            return false;//cannot be complete yet, two or less points
        }
        return (this.coords_outer.get(0) == this.coords_outer.get(coords_outer.size() - 1));
    }

    boolean isAcceptable(boolean lenient) {

        if (!outerIsComplete()) {
            if (!lenient) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\tPC: Not accepted due to OUTER"); 
                return false;
            }
        }//cannot be ok.

            EnfuserLogger.log(Level.FINER,this.getClass(),"\tPC: outer is OK");
        

        //check inners, since outer is OK. assume ok until not
        boolean innersOk = true;
        for (ArrayList<double[]> inner : innards) {

            if (inner.size() < 3) {
                innersOk = false;
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t\tPC: Not accepted due to INNER not connected");
                
            }//cannot be complete yet, two or less points  
            else if (inner.get(0) != inner.get(inner.size() - 1)) {
                innersOk = false;
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t\tPC: Not accepted due to INNER not connected");
                
            }
                EnfuserLogger.log(Level.FINER,this.getClass(),"\tPC: one inner OK");
            
        }

        return innersOk;
    }

}
