/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

/**
 *
 * @author johanssl
 */
public class CoordinatedVector {

    public float lat;
    public float lon;

    public float[] dat;

    public CoordinatedVector(float[] dat, float lat, float lon) {
        this.dat = dat;
        this.lat = lat;
        this.lon = lon;
    }

}
