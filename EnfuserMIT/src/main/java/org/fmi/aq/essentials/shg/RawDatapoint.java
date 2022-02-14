/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import org.fmi.aq.essentials.date.Dtime;
import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class RawDatapoint implements Serializable {

    private static final long serialVersionUID = 7567472293822776147L;//Important: change this IF changes are made that breaks serialization! 
    public Dtime dt;
    public float lat;
    public float lon;
    public float value;

    public RawDatapoint(Dtime dt, float lat, float lon, float value) {

        this.dt = dt;
        this.lat = lat;
        this.lon = lon;
        this.value = value;

    }

}
