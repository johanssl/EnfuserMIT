/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.road;

import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.Way;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class TunnelPoint implements Serializable {

    private static final long serialVersionUID = 7518472293822776147L;//Important: change this IF changes are made that breaks serialization! 
    public RoadPiece rp;
    public float length_m;

    public int h, w;
    public float[] latlon;

    public TunnelPoint(RoadPiece rp, float len, OsmLayer op, double[] cc) {
        this.rp = rp;
        this.length_m = len;

        double lat = cc[0];// * Node.TO_DEGREE;
        double lon = cc[1];// * Node.TO_DEGREE;

        this.h = getH(op.b, lat, op.dlat);
        this.w = getW(op.b, lon, op.dlon);
        this.latlon = new float[]{(float) lat, (float) lon};
    }

    public static TunnelPoint[] getEndPoints(Way way, OsmLayer op, byte rc) {
        float len = way.getLineLength_m(op.cos_lat);
        RoadPiece rp = new RoadPiece(way, op.rot, rc, op.resm);

        TunnelPoint tp = new TunnelPoint(rp, len, op, way.coords.get(0));
        TunnelPoint tp2 = new TunnelPoint(rp, len, op, way.coords.get(way.coords.size() - 1));
        return new TunnelPoint[]{tp, tp2};
    }

}
