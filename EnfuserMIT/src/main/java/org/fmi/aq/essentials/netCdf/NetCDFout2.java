/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.netCdf;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.ByteGeoNC;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.IOException;
import java.util.ArrayList;
import org.fmi.aq.essentials.shg.SparseGrid;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;

/**
 *
 * @author Lasse Johansson
 */
public class NetCDFout2 {

    public static void writeDynamicNetCDF(boolean byteCompress, NcInfo nci,
            float[][][] floatData, Boundaries b,
            ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        DefaultWriter dw = new DefaultWriter(filename, attr);
        ArrayList<float[][][]> dats = new ArrayList<>();
        dats.add(floatData);
        ArrayList<NcInfo> infos = new ArrayList<>();
        infos.add(nci);

        dw.setData_dyn(dats, b, infos, byteCompress);
        dw.write();

    }

    /**
     * Stores multiple dynamic datasets in a single netCDF file. For this method
     * to function properly, all datasets MUST have the same Boundaries. Also,
     * all dataset types must have same temporal (t) and physical (b,h,w)
     * dimensions.
     *
     * @param byteCompress true for using scaled-offsetted byte values (25%
     * space usage with respect to float)
     * @param nfos a list of variable infos, one for each variable.
     * @param floatDats the variable data as float[t][h][w]
     * @param b geo definiton for all data.
     * @param attr global attributes
     * @param filename Full filename (path) for data dump (without directory,
     * should end with '.nc')
     * @throws IOException could not write netCDF-file
     * @throws InvalidRangeException dimensions were not set correctly
     */
    public static void writeMultiDynamicNetCDF(boolean byteCompress, ArrayList<NcInfo> nfos,
            ArrayList<float[][][]> floatDats, Boundaries b,
            ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_dyn(floatDats, b, nfos, byteCompress);
        dw.write();
    }

    public static void writeStaticByteNetCDF(ArrayList<NcInfo> infs,
            ArrayList<ByteGeoNC> bgs, ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_bg(bgs, infs);
        dw.write();
    }

    public static void writeDynamicNetCDF(NcInfo info,
            SparseGrid shg, int typeIndex, ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_shg(shg, info, typeIndex);
        dw.write();

    }

    public static void writeDynamicNetCDF_singlevar(boolean byteCompress, String varname,
            String units, String longdesc,
            ArrayList<GeoGrid> gg, ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        ArrayList<float[][][]> floats = new ArrayList<>();
        float[][][] dat = new float[gg.size()][][];
        Boundaries b = null;
        Dtime dt1 = gg.get(0).dt;
        Dtime dt2 = gg.get(1).dt;
        ArrayList<NcInfo> infos = new ArrayList<>();
        infos.add(new NcInfo(varname, units, longdesc, dt1, dt2));
        int k = -1;
        for (GeoGrid g : gg) {
            k++;
            b = g.gridBounds;
            dat[k] = g.values;
        }
        floats.add(dat);

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_dyn(floats, b, infos, byteCompress);
        dw.write();

    }

    public static void writeStaticNetCDFs(boolean byteCompress, ArrayList<NcInfo> infos,
            ArrayList<GeoGrid> gridData, ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_gg(gridData, infos, byteCompress);
        dw.write();
    }

    public static void writeMultiDynamicNetCDF(boolean byteCompress, ArrayList<NcInfo> infos,
            GeoGrid[][] gridData, ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        ArrayList<float[][][]> dats = new ArrayList<>();
        Boundaries b = null;
        for (int i = 0; i < gridData.length; i++) {
            GeoGrid[] gg = gridData[i];
            if (gg == null) {
                continue;
            }

            float[][][] dat = new float[gg.length][][];
            for (int j = 0; j < gg.length; j++) {
                dat[j] = gg[j].values;
                if (b == null) {
                    b = gg[j].gridBounds;
                }
            }

            dats.add(dat);
        }//for types 

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_dyn(dats, b, infos, byteCompress);
        dw.write();

    }

    public static void writeNetCDF_statc(boolean byteCompress, ArrayList<NcInfo> varNfo,
            ArrayList<GeoGrid> emsData,
            ArrayList<Attribute> attr, String filename)
            throws IOException, InvalidRangeException {

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_gg(emsData, varNfo, byteCompress);
        dw.write();

    }

//This method produces a netCDF-3 file which contains the geographical distribution of aggregated CO2 emissions in kg/cell form for each SHIP type
    public static void writeNetCDF_statc(boolean byteCompress, ArrayList<NcInfo> inf,
            float[][][] emsData, Boundaries b, ArrayList<Attribute> attr,
            String filename) throws IOException, InvalidRangeException {

        ArrayList<GeoGrid> gg = new ArrayList<>();
        for (float[][] dat : emsData) {
            GeoGrid g = new GeoGrid(dat, Dtime.getSystemDate(), b.clone());
            gg.add(g);
        }

        DefaultWriter dw = new DefaultWriter(filename, attr);
        dw.setData_gg(gg, inf, byteCompress);
        dw.write();

    }

}
