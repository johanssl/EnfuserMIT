/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import org.fmi.aq.enfuser.datapack.main.Observation;
import java.io.Serializable;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import static java.lang.Math.abs;

/**
 *
 * @author johanssl
 */
public class Domain implements Serializable {

//public long minHC;
//public long maxHC;
    public Dtime start;
    public Dtime end;

    public double latmin;
    public double latmax;
    public double lonmin;
    public double lonmax;

    public Domain() {
        latmin = 90;
        latmax = -90;
        lonmin = 180;
        lonmax = -180;
    }

    public void update(GeoGrid gg) {

        if (start == null || this.start.systemHours() > gg.dt.systemHours()) {
            this.start = gg.dt.clone();
        }

        if (end == null || this.end.systemHours() < gg.dt.systemHours()) {
            this.end = gg.dt.clone();
        }

        Boundaries b = gg.gridBounds;
        if (latmin > b.latmin) {
            this.latmin = b.latmin;
        }
        if (latmax < b.latmax) {
            this.latmax = b.latmax;
        }
        if (lonmin > b.lonmin) {
            this.lonmin = b.lonmin;
        }
        if (lonmax < b.lonmax) {
            this.lonmax = b.lonmax;
        }

    }

    public void update(Observation obs) {

        if (start == null || this.start.systemHours() > obs.dt.systemHours()) {
            this.start = obs.dt.clone();
        }

        if (end == null || this.end.systemHours() < obs.dt.systemHours()) {
            this.end = obs.dt.clone();
        }

        if (latmin > obs.lat) {
            this.latmin = obs.lat;
        }
        if (latmax < obs.lat) {
            this.latmax = obs.lat;
        }
        if (lonmin > obs.lon) {
            this.lonmin = obs.lon;
        }
        if (lonmax < obs.lon) {
            this.lonmax = obs.lon;
        }

    }

    public boolean isStationary() {
        return (abs(latmax - latmin) < 0.001 && abs(lonmin - lonmax) < 0.001);
    }

    public int getHourInBetweenSpan(Dtime dt) {
        if (dt.systemHours() < start.systemHours()) {
            return start.systemHours();
        }
        if (dt.systemHours() > end.systemHours()) {
            return end.systemHours();
        }
        return dt.systemHours();
    }

}
