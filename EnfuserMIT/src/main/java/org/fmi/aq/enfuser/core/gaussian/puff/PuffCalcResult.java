/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

/**
 *
 * @author johanssl
 */
    public class PuffCalcResult {

    float[][][][] cons_HWcQ; //y = ydim (lat), X = xdim (lon), s = source type dim, Q = pollutant type dim.
    final int ymin;
    float fillRate;

    public PuffCalcResult(float fillRate, float[][][][] c, int ymin) {

       this.cons_HWcQ = c;
       this.ymin = ymin;
       this.fillRate = fillRate;

    }

    } 
    
