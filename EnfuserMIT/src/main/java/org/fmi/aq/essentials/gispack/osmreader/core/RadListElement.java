/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class RadListElement implements Serializable {

    private static final long serialVersionUID = -7575081693030943831L;//Important: change this IF changes are made that breaks serialization! 
    public final int hAdd;
    public final int wAdd;
    public final double dist_m;
    public final double latAdd;
    public final double lonAdd;

    public RadListElement(int hadd, int wadd, double dist_m, double latAdd, double lonAdd) {
        this.hAdd = hadd;
        this.wAdd = wadd;
        this.dist_m = dist_m;
        this.latAdd = latAdd;
        this.lonAdd = lonAdd;
    }
    
    /**
    * Check for outOfBounds exception. If true is returned, skip the iteration.
    * @return 
    */     
   public boolean oobs_HW_hw(int H, int W, int h, int w) {
       if (h+hAdd<0) return true;
       if (h+hAdd>= H) return true;
       
       if (w+wAdd<0) return true;
       if (w+wAdd>=W) return true;
       
       return false;       
   }  

}
