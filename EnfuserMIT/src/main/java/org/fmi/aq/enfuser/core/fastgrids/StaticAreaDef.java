/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.fastgrids;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoNC;
import java.io.IOException;
import java.util.ArrayList;
import org.fmi.aq.enfuser.core.AreaInfo;
import static org.fmi.aq.enfuser.ftools.FastMath.MILLION;
import org.fmi.aq.essentials.gispack.osmreader.core.Building;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import static org.fmi.aq.essentials.gispack.osmreader.core.OSMget.isBuilding;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_POP;
import org.fmi.aq.essentials.netCdf.NcInfo;
import org.fmi.aq.essentials.netCdf.NetCDFout2;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;

/**
 *
 * @author johanssl
 */
public class StaticAreaDef {

    public float[][] elevation;
    public byte[][] luType;
    public byte[][] buildH_m;
    public short[][] pops;
    MapPack mp;
    AreaInfo in;
    public final int H,W;
    public StaticAreaDef(AreaInfo in, MapPack mp) {
        this.in = in;
        this.mp = mp;
        this.H =in.H;
        this.W=in.W;
        setInfos();
    }

    private void setInfos() {
        this.elevation = new float[H][W];
        this.luType = new byte[H][W];
        this.buildH_m = new byte[H][W];
        this.pops = new short[H][W];

        double lat;
        double lon;

        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {
                lat = in.bounds.latmax - in.dlat * h;
                lon = in.bounds.lonmin + in.dlon * w;
                OsmLayer ol = mp.getOsmLayer(lat, lon);
                //population
                try {

                    float pop_perKm2 = OSMget.getBGdata_perM2(BGN_POP, ol, lat, lon) / 1000000f;
                    pops[h][w] = (short) (pop_perKm2 * in.cellA_m2/MILLION);

                } catch (Exception e) {

                }

                float ele = OSMget.getElevation(ol, lat, lon);
                elevation[h][w] = ele;
                byte osm = OSMget.getSurfaceType(ol, lat, lon);
                luType[h][w] = osm;

                if (isBuilding(osm)) {
                    int hh = ol.get_h(lat);
                    int ww = ol.get_w(lon);
                    Building b = ol.getBuilding(hh, ww);
                    if (b != null) {
                        float height = b.getHeight_m(ol);
                        if (height > 125) {
                            height = 125;
                        }
                        this.buildH_m[h][w] = (byte) height;
                        elevation[h][w] += height;
                    }
                }

            }//for w
        }//for h

    }

    public float[][] getExposure(float[][] dat ) {

        if (pops == null) {
            this.setInfos();
        }
        float[][] temp = new float[dat.length][dat[0].length];

        for (int h = 0; h < temp.length; h++) {
            for (int w = 0; w < temp[0].length; w++) {

                if (pops != null) {
                    temp[h][w] = dat[h][w] * pops[h][w];
                } else {
                    temp[h][w] = 0;
                }

            }
        }
        return temp;
    }

    public int getTotPersons() {
        int tot = 0;
        for (int h = 0; h < in.H; h++) { //Note that we do not want to check lat and lons here which are included in the ensemble array! thus we start at 1
            for (int w = 0; w < in.W; w++) {
                tot += pops[h][w];
            }
        }

        return tot;
    }


    void saveMaskingData(String rootDir, ArrayList<Attribute> attr, 
            String timeDesc, String fileDesc, Boundaries b) {

       ArrayList<ByteGeoNC> bgs = new ArrayList<>();
        ByteGeoNC buildH = new ByteGeoNC(this.buildH_m, Dtime.getSystemDate(), b);
        ByteGeoNC lu = new ByteGeoNC(this.luType, Dtime.getSystemDate(), b);

        int H = this.elevation.length;
        int W = this.elevation[0].length;

        byte[][] elev = new byte[H][W];
        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                float temp = this.elevation[h][w];
                if (temp > 125) {
                    temp = 125;
                }
                elev[h][w] = (byte) temp;
            }
        }

        ByteGeoNC ele = new ByteGeoNC(elev, Dtime.getSystemDate(), b);
        ArrayList<NcInfo> infos = new ArrayList<>();

        bgs.add(buildH);
        infos.add(new NcInfo("buildingHeight", "m", " Building height [m] based on OSM data (estimated if not specified by OSM)"));
        bgs.add(lu);
        infos.add(new NcInfo("landuse", " classification index [-125,125]", " OSM land use classification index."));
        bgs.add(ele);
        infos.add(new NcInfo("groundElevation", " m", " estimated digital surface map (DSM)"));

        try {
            NetCDFout2.writeStaticByteNetCDF(infos, bgs, attr, rootDir + fileDesc);

        } catch (IOException | InvalidRangeException ex) {
            ex.printStackTrace();
        }
    }

}
