/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import org.fmi.aq.essentials.geoGrid.ByteGeoNC;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.netCdf.NcInfo;
import java.util.ArrayList;

/**
 *A simple wrapper class for holding dataets for allPollutants file creation.
 * @author johanssl
 */
public class ArchivePack {

    public ArrayList<ByteGeoNC> bgeos;
    public ArrayList<GeoGrid> geos;
    public ArrayList<NcInfo> nfos;

    public ArchivePack(ArrayList<GeoGrid> geos, ArrayList<ByteGeoNC> bgeos,
            ArrayList<NcInfo> nfos) {
        this.bgeos = bgeos;
        this.geos = geos;
        this.nfos = nfos;
    }
}
