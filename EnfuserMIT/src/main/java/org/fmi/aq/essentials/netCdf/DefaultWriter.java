/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.netCdf;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.ByteGeoNC;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGG3D;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.essentials.shg.SparseGrid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import ucar.ma2.Array;
import ucar.ma2.ArrayByte;
import ucar.ma2.ArrayFloat;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 *
 * @author johanssl
 */
public class DefaultWriter {

    private final String fullFileName;
    private final ArrayList<Attribute> attr;

    private Variable lat;
    private Variable lon;
    private Variable timeV;
    private NetcdfFileWriter writeableFile;

    private ArrayList<NcInfo> infoArr;
    private final ArrayList<Variable> vars;

    private int H, W;
    private int maxT;
    private int timeUnitStep;
    private AreaNfo in;
    private boolean byteCompress;

    //choice of input data (one of these must exist
    private ArrayList<GeoGrid> gg = null;
    private ArrayList<ByteGeoNC> bgg = null;
    private ArrayList<float[][][]> dats = null;
    private SparseGrid shg = null;
    private int shg_typeIndex;
    //temporary sets (current var)
    private ByteGG3D bgg3d = null;
    private GeoGrid g = null;
    private ByteGeoNC bg = null;
    private float[][][] dat = null;
    private Float ADD_OFFSET;
    private Float VALUE_SCALER;

    public DefaultWriter(String fullname, ArrayList<Attribute> attr) {
        this.fullFileName = fullname;
        this.attr = attr;
        this.vars = new ArrayList<>();
    }

    public void setData_shg(SparseGrid shg, NcInfo info, int typeIndex) {
        this.shg = shg;
        this.infoArr = new ArrayList<>();
        this.infoArr.add(info);
        this.shg_typeIndex = typeIndex;

        this.H = shg.H;
        this.W = shg.W;
        this.maxT = shg.getMaxT() + 1;
        this.in = new AreaNfo(shg.b.clone(), H, W, Dtime.getSystemDate());

        this.byteCompress = false;
    }

    public void setData_gg(ArrayList<GeoGrid> gg, ArrayList<NcInfo> infos, boolean byteCompress) {

        GeoGrid gTemp = gg.get(0);

        H = gTemp.H;
        W = gTemp.W;
        maxT = 1;
        in = new AreaNfo(gTemp.gridBounds.clone(), H, W, Dtime.getSystemDate());

        this.byteCompress = byteCompress;
        this.infoArr = infos;

        this.gg = gg;
    }

    public void setData_dyn(ArrayList<float[][][]> thw, Boundaries b,
            ArrayList<NcInfo> infos, boolean byteCompress) {

        this.dats = thw;
        float[][][] example = thw.get(0);

        maxT = example.length;
        H = example[0].length;
        W = example[0][0].length;
        in = new AreaNfo(b.clone(), H, W, Dtime.getSystemDate());

        this.byteCompress = byteCompress;
        this.infoArr = infos;

    }

    public void setData_bg(ArrayList<ByteGeoNC> bgg, ArrayList<NcInfo> infos) {

        ByteGeoNC bgTemp = bgg.get(0);

        H = bgTemp.H;
        W = bgTemp.W;
        maxT = 1;
        in = new AreaNfo(bgTemp.gridBounds.clone(), H, W, Dtime.getSystemDate());

        this.byteCompress = true;
        this.infoArr = infos;

        this.bgg = bgg;
    }

    public void write() throws IOException, InvalidRangeException {

        writeableFile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3,
                this.fullFileName, null);

        this.timeUnitStep = this.infoArr.get(0).timeUnitStep;

        if (attr != null) {
            for (Attribute a : attr) {
                writeableFile.addGroupAttribute(null, a);
            }        // define dimensions, including unlimited
        }

        // define dimensions, including unlimited
        Dimension timeDim = writeableFile.addUnlimitedDimension("time");
        Dimension latDim = writeableFile.addDimension(null, "latitude", H);
        Dimension lonDim = writeableFile.addDimension(null, "longitude", W);

        // define Variables
        List<Dimension> dims = new ArrayList<>();
        dims.add(timeDim);
        dims.add(latDim);
        dims.add(lonDim);

        for (NcInfo inf : this.infoArr) {
            Variable var;
            DataType dtype = DataType.FLOAT;
            if (byteCompress) {
                dtype = DataType.BYTE;
            }

            var = writeableFile.addVariable(null, inf.varname, dtype, dims);

            ArrayList<Attribute> atr = inf.getDefVariableAttributes();
            for (Attribute a : atr) {
                var.addAttribute(a);
            }
            this.vars.add(var);
        }//for infos, vars

        //lat
        List<Dimension> latdims = new ArrayList();
        latdims.add(latDim);
        lat = writeableFile.addVariable(null, "latitude", DataType.FLOAT, latdims);
        lat.addAttribute(new Attribute("units", "degrees_north"));

        //lon
        List<Dimension> londims = new ArrayList();
        londims.add(lonDim);
        lon = writeableFile.addVariable(null, "longitude", DataType.FLOAT, londims);
        lon.addAttribute(new Attribute("units", "degrees_east"));

        //time
        List<Dimension> tdims = new ArrayList();
        tdims.add(timeDim);
        timeV = writeableFile.addVariable(null, "time", DataType.INT, tdims);
        Attribute ta = infoArr.get(0).getTimeAttribute();
        timeV.addAttribute(ta);

        // create the file
        writeableFile.create();

        // write out the non-record variables
        double[] lats = new double[H];
        double[] lons = new double[W];
        int[] time = new int[maxT];
        for (int h = 0; h < H; h++) {
            lats[h] = in.getLat(h);
        }
        for (int w = 0; w < W; w++) {
            lons[w] = in.getLon(w);
        }
        for (int i = 0; i < maxT; i++) {
            time[i] = i * timeUnitStep;
        }
        writeableFile.write(lat, Array.factory(lats));
        writeableFile.write(lon, Array.factory(lons));
        writeableFile.write(timeV, Array.factory(time));

        //THEN THE ACTUAL DATA =========================
        //// heres where we write the record variables
        // different ways to create the data arrays. 
        // Note the outer dimension has shape 1, since we will write one record at a time
        int K = -1;
        for (Variable var : vars) {
            K++;
            this.prepareSet(K);
            if (byteCompress) {
                writeableFile.setRedefineMode(true);
                ArrayList<Attribute> bytes = NcInfo.getVariableAttributes_byte(ADD_OFFSET, VALUE_SCALER);
                for (Attribute a : bytes) {
                    writeableFile.addVariableAttribute(var, a);
                }
                writeableFile.setRedefineMode(false);
            }

            ArrayByte.D3 data_bd3 = null;
            ArrayFloat.D3 data_fd3 = null;

            if (byteCompress) {
                data_bd3 = new ArrayByte.D3(1, latDim.getLength(), lonDim.getLength());
            } else {
                data_fd3 = new ArrayFloat.D3(1, latDim.getLength(), lonDim.getLength());
            }

            //Array timeData = Array.factory( DataType.INT, new int[] {1});
            int[] origin = new int[]{0, 0, 0}; //not sure what these origins are
            // loop over each record

            for (int t = 0; t < maxT; t++) {
                for (int h = 0; h < H; h++) {
                    for (int w = 0; w < W; w++) {

                        if (data_bd3 != null) {

                            byte byt = this.byteValue(t, h, w);
                            data_bd3.set(0, h, w, byt);

                        } else if (data_fd3 != null) {

                            float f = floatValue(t, h, w);
                            data_fd3.set(0, h, w, f);
                        }

                    }//for w
                }//for h
                // write the data out for one record
                // set the origin here
                origin[0] = t;

                if (byteCompress) {
                    writeableFile.write(var, origin, data_bd3);
                } else {
                    writeableFile.write(var, origin, data_fd3);
                }

            }//for t   

        }//for variables
        // all done
        writeableFile.close();

    }

    private void prepareSet(int K) {
        //option 0: shg
        if (this.shg != null) {
            return;//nothing to do here
        }
        //options 1: a list of GeoGrids
        if (this.gg != null) {
            if (this.byteCompress) {
                GeoGrid gTemp = gg.get(K);
                this.bg = new ByteGeoNC(gTemp.values, gTemp.dt, gTemp.gridBounds);
                //now bg holds the data (byteValue())
                this.ADD_OFFSET = bg.VAL_OFFSET;
                this.VALUE_SCALER = bg.VAL_SCALER;
            } else {
                this.g = gg.get(K);
                //now g holds data (floatValue())
            }
        }

        //option 2: a list of ByteGeos
        if (this.bgg != null) {
            this.bg = bgg.get(K);//and byte scaling is used as default
            //now bg holds the data (byteValue())
            this.ADD_OFFSET = bg.VAL_OFFSET;
            this.VALUE_SCALER = bg.VAL_SCALER;
        }

        //option 3: a list of float[][][]
        if (this.dats != null) {
            if (this.byteCompress) {
                float[][][] ds = dats.get(K);
                bgg3d = new ByteGG3D(ds);//date does not matter at all
                //now bgg3d holds the data (byteValue())
                this.ADD_OFFSET = bgg3d.VAL_OFFSET;
                this.VALUE_SCALER = bgg3d.VAL_SCALER;
            } else {
                this.dat = dats.get(K);
                //now dat[][][] is used for floatValue()
            }
        }
    }

    private byte byteValue(int t, int h, int w) {
        if (bg != null) {
            return bg.values[h][w];
        } else {
            return bgg3d.getRawByte(t, h, w);
        }
    }

    int warns = 0;
    private float floatValue(int t, int h, int w) {

        if (shg != null) {
            long tKey = shg.minKey + t * shg.timeInterval_min * 60;
            Float f = shg.getValue_tkHW(tKey, (short) h, (short) w, shg_typeIndex);
            if (f == null) {
                return 0;
            } else {
                return f;
            }
        }

        //check for a possible problem
        if (g==null && dat==null) {
            warns++;
            if (warns<1) {
                EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "DefaultWriter.floatValue(): datasets are null: "+ this.fullFileName);
            }
            return 0;
        }
        
        if (g != null) {
            return g.values[h][w];
        } else {
            return dat[t][h][w];
        }
    }
}
