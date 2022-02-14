/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.ArrayList;
import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class Coordinate implements Serializable {

    public static ArrayList<Coordinate> interpolateSteps(Coordinate[] cc, int steps) {
        ArrayList<Coordinate> coords = new ArrayList<>();

        Coordinate c1 = cc[0];
        Coordinate c2 = cc[1];
        //EnfuserLogger.log(Level.FINER,this.getClass(),c1.lat +" vs "+ c2.lat);
        //EnfuserLogger.log(Level.FINER,this.getClass(),"Steps = "+ steps);
        for (int i = 0; i <= steps; i++) {

            float frac2 = (float) i / steps;
            float frac1 = 1f - frac2;

            float lat = c1.lat * frac1 + c2.lat * frac2;

            float lon = c1.lon * frac1 + c2.lon * frac2;

            coords.add(new Coordinate(lat, lon));
        }

        return coords;
    }

    public float lat;
    public float lon;

    public Coordinate(float lat, float lon) {
        this.lat = lat;
        this.lon = lon;
    }

}
