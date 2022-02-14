/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import org.fmi.aq.essentials.geoGrid.Boundaries;

/**
 *
 * @author johanssl
 */
public abstract class RangeLimit {

    public static final double pi = Math.PI;
    public static final double degreeInKM = 2 * pi * 6371 / 360;

    public static Boundaries getBounds(double maxRangeKM, double centLat, double centLon) {

        double scaler = Math.cos(2 * pi * centLat / 360);
        double dlat = maxRangeKM / degreeInKM;
        double dlon = maxRangeKM / degreeInKM / scaler;

        double minlat = centLat - dlat;
        double maxlat = centLat + dlat;
        double minlon = centLon - dlon;
        double maxlon = centLon + dlon;

        return new Boundaries(minlat, maxlat, minlon, maxlon);

    }

}
