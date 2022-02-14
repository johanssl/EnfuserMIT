/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.netCdf;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.shg.RawDatapoint;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class NetcdfHandle {

//netcdf
    public static final int T_HOURS = 3600;
    public static final int T_MINS = 60;
    public static final int T_DAYS = 3600 * 24;
    public static final int T_SECS = 1;

    //filespecific variables 
    public ArrayList<String[]> globalAttr;
    NetcdfFile ncfile;
    public List<Variable> vars;
    public ArrayList<String> varNames;
    public Dtime firstDt;
    public Dtime firstDt_raw;
    public boolean timeParse_success = false;

    public int dT_secs;
    public int baseUnitT_secs;

    private float lonOff = 0;
    private float latOff = 0;
    float[] lats;
    float[] lons;
    float[] height;

    int tIndex = -1;
    int yIndex = -1;
    int xIndex = -1;
    int heightIndex = -1;

    //current variables
    public Array loadedData;
    public String loadedVar = "";
    public float[][] dataSlice;
    public int[] loadedShape;
    public ArrayList<String[]> loadedVarAttr;
    public boolean hasHeight = false;

    public boolean timeInfo_removeWhiteSpace = false;

    public double[] times;
    public float timeDiff = 1;

    private double val_scaler = 1.0;
    private double val_offset = 0;
    private Variable loadedVariable;
    List<Attribute> globals;
    private boolean loadFail=false;
      protected Variable getLoadedVariable() {
       return this.loadedVariable;
    }
      
    public List<Attribute> getVariableAttributes() {
        return this.loadedVariable.getAttributes();
    } 


    public void setLatLonOffsets(float latoff, float lonoff) {
        this.latOff = latoff;
        this.lonOff = lonoff;
    }

    public String getGlobalAttribute(String key) {

        for (String[] attr : this.globalAttr) {
            if (attr[0].equals(key)) {
                return attr[1];
            }
        }
        return null;

    }
    final String filename;
    public NetcdfHandle(String filename) {
        this.filename = filename;
        try {
            ncfile = NetcdfFile.open(filename);

            globals = ncfile.getGlobalAttributes();
            globalAttr = new ArrayList<>();
            for (Attribute l:globals) {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        l.getFullName() + ", " + l.getStringValue());
                String[] temp = {l.getFullName(), l.getStringValue()};
                globalAttr.add(temp);
            }

            varNames = new ArrayList<>();
            vars = ncfile.getVariables();

            int minDim = Integer.MAX_VALUE;

            for (int i = 0; i < vars.size(); i++) {

                String varStr2 = vars.get(i).getFullName();

                
                EnfuserLogger.log(Level.FINEST,this.getClass(),"variable: " + varStr2);
                
                varNames.add(varStr2);

                if (varStr2.equals("time")) {
                    EnfuserLogger.log(Level.FINEST,this.getClass(),"Found variable for time reference. (time)");
                    
                    if (!this.timeParse_success) {
                        this.parseTimeInfo(vars.get(i));
                    }
                    tIndex = i;
                    if (i < minDim) {
                        minDim = i;
                    }
                } else if (varStr2.equals("y") || varStr2.contains("atitude")
                        || varStr2.equals("lat") || varStr2.equals("Lat")) {
                    this.parseLatLon("LAT", vars.get(i));
                    yIndex = i;
                    if (i < minDim) {
                        minDim = i;
                    }
                } else if (varStr2.equals("x") || varStr2.contains("ongitude")
                        || varStr2.equals("lon") || varStr2.equals("Lon")) {
                    this.parseLatLon("LON", vars.get(i));
                    xIndex = i;
                    if (i < minDim) {
                        minDim = i;
                    }
                } else if (varStr2.equals("z") || varStr2.contains("height")) {
                    this.parseLatLon("HEIGHT", vars.get(i));
                    heightIndex = i;
                    if (i < minDim) {
                        minDim = i;
                    }
                }

            }

            //more lenient time check
            for (int i = 0; i < vars.size(); i++) {

                String varStr2 = vars.get(i).getFullName();
                EnfuserLogger.log(Level.FINEST,this.getClass(),"variable (full): " + varStr2);
                

                if (varStr2.contains("time") && !this.timeParse_success) {
                    this.parseTimeInfo(vars.get(i));
                    tIndex = i;
                    if (i < minDim) {
                        minDim = i;
                    }
                }
            }
            //last ditch effort
            if (!this.timeParse_success) {
                      for (int i = 0; i < vars.size(); i++) {

                        String varStr2 = vars.get(i).getFullName();
                        if (varStr2.toLowerCase().contains("time") && !this.timeParse_success) {
                            this.parseTimeInfo(vars.get(i));
                            tIndex = i;
                            if (i < minDim) {
                                minDim = i;
                            }
                        }
                    }
            }

            tIndex -= minDim;
            heightIndex -= minDim;
            yIndex -= minDim;
            xIndex -= minDim;

        } catch (IOException ioe) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                    "NetcdfHandle: IOException encountered for file:" + filename);
            this.loadFail=true;
        }
    }

    public boolean loadFailed() {
        return this.loadFail;
    }
    
    public float[][] getForcedSlice(int dim1_ind, int dim2_ind) {

        float[][] dat = null;
        Index index = loadedData.getIndex();

        if (this.loadedShape.length == 2) {//simple case
            int H = this.loadedShape[0];
            int W = this.loadedShape[1];
            EnfuserLogger.log(Level.FINEST,this.getClass(),"ncHandle.forceSlice: " + H + " x " + W);
            dat = new float[H][W];

            for (int h = 0; h < H; h++) {
                for (int w = 0; w < W; w++) {

                    dat[h][w] = loadedData.getFloat(index.set(h, w));

                }
            }
        } else if (this.loadedShape.length == 3) {//simple case
            int H = this.loadedShape[1];
            int W = this.loadedShape[2];
            EnfuserLogger.log(Level.FINEST,this.getClass(),"ncHandle.forceSlice: " + H + " x " + W + ", shape[0] ="
                    + this.loadedShape[0] + " => " + dim1_ind);
            dat = new float[H][W];

            for (int h = 0; h < H; h++) {
                for (int w = 0; w < W; w++) {

                    dat[h][w] = loadedData.getFloat(index.set(dim1_ind, h, w));

                }
            }
        } else if (this.loadedShape.length == 4) {//simple case
            int H = this.loadedShape[2];
            int W = this.loadedShape[3];
            EnfuserLogger.log(Level.FINEST,this.getClass(),"ncHandle.forceSlice: " + H + " x " + W + ", shape[0] ="
                    + this.loadedShape[0] + " => " + dim1_ind + ", shape[1] ="
                    + this.loadedShape[1] + " => " + dim2_ind);
            dat = new float[H][W];

            for (int h = 0; h < H; h++) {
                for (int w = 0; w < W; w++) {

                    dat[h][w] = loadedData.getFloat(index.set(dim1_ind, dim2_ind, h, w));

                }
            }
        }

        return dat;
    }

//h-indexing: [0] is the minLat at bottom. An inversion trick is needed if [0] of the netCDF file is the maximum latitude
    private boolean data2D = false;

    public float[][] getSlice(int t, int height) {

        float[][] ncTemp = new float[this.lats.length][this.lons.length];
        Index index = loadedData.getIndex();

        float sum = 0;
        int h_inv;

        for (int h = 0; h < this.lats.length; h++) {
            h_inv = h;
            /*
                      if (this.flipY) {
                      h_inv = (this.lats.length -1) -h;
                      } 
             */

            for (int w = 0; w < this.lons.length; w++) {
                float f = 0;
                if (hasHeight) {
                    f = loadedData.getFloat(index.set(t, height, h, w));

                } else if (data2D) {//special case for data without time index

                    f = loadedData.getFloat(index.set(h, w));

                } else {

                    try {
                        f = loadedData.getFloat(index.set(t, h, w));

                    } catch (ArrayIndexOutOfBoundsException e) {

                        //this could be the result of 2d data shape without t index 0.
                        if (this.loadedShape.length == 2) {

                            f = loadedData.getFloat(index.set(h, w));
                            this.data2D = true;
                            EnfuserLogger.log(Level.FINEST,this.getClass(),"Data has no temporal dimension?");
                        }

                    }

                }

                //offset and scaler
                f = (float) (f * this.val_scaler + this.val_offset);

                ncTemp[h_inv][w] = f;
                sum += f;

            }//for w
        }// for h
        EnfuserLogger.log(Level.FINEST,this.getClass(),"slice sum: " + sum);

        return ncTemp;
    }

    public float[][][] getDynamic(int height) {

        int t0 = 0;
        int tMax = this.loadedShape[0];
        return getDynamic(height, t0, tMax);
    }

    public float[][][] getDynamic(int height, int t0, int tMax) {

        float[][][] ncTemp = new float[tMax - t0][this.lats.length][this.lons.length];
        for (int t = t0; t < tMax; t++) {
            ncTemp[t - t0] = this.getSlice(t, height);

        }// for t

        // EnfuserLogger.log(Level.FINEST,this.getClass(),"getDynamic.sum: "+sum);
        return ncTemp;
    }

    public Dtime[] getTimes() {

        Dtime[] dates = new Dtime[this.loadedShape[0]];
        Dtime dt = this.firstDt.clone();
        for (int t = 0; t < this.loadedShape[0]; t++) {

            dates[t] = dt.clone();
            dt.addSeconds(dT_secs);

        }
        return dates;
    }

    public Dtime[] getTimes(int t0, int tMax) {

        Dtime[] dates = new Dtime[tMax - t0];
        Dtime dt = this.firstDt.clone();
        dt.addSeconds(dT_secs * t0);

        for (int t = t0; t < tMax; t++) {

            dates[t] = dt.clone();
            dt.addSeconds(dT_secs);

        }
        return dates;
    }

    public ArrayList<RawDatapoint> getSpecificContentOnList(int height, Dtime first,
            Dtime last, Boundaries limits, ArrayList<double[]> latlons) {
        Boundaries b = this.getBounds();
        ArrayList<RawDatapoint> tempArr = new ArrayList<>();

        Index index = loadedData.getIndex();
        float val;

        double dlat = Math.abs(lats[0] - lats[1]);
        double dlon = Math.abs(lons[0] - lons[1]);

         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"netCDFhandle.getContentOnList: dlat / dlon = "
                    + dlat + ", " + dlon);
        }
        Dtime dt = firstDt.clone();

        for (short t = 0; t < this.loadedShape[0]; t++) {
            if (first != null && last != null) {

                if (dt.systemHours() < first.systemHours() || dt.systemHours() > last.systemHours()) {
                    // EnfuserLogger.log(Level.FINEST,this.getClass(),"\t not suitable hour");
                    dt.addSeconds(this.dT_secs);
                    dt.initOffset(firstDt); // checks the hour difference and stores this in freeByte. This value now partly reflects the quality of the forecasted datapoint

                    continue;
                }
            }

             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),"\t extracting" + dt.getStringDate());
            }

            for (double[] latlon : latlons) {

                double lat = latlon[0];
                double lon = latlon[1];

                if (limits != null && (lon < limits.lonmin || lon > limits.lonmax)) {
                    continue;
                }
                if (limits != null && (lat < limits.latmin || lat > limits.latmax)) {
                    continue;
                }

                //convert h, w
                int h = GeoGrid.getH(b, lat, dlat);
                //if (this.flipY) h = this.lats.length -h;
                int w = GeoGrid.getW(b, lon, dlon);

                try {
                    if (hasHeight) {
                        val = loadedData.getFloat(index.set(t, height, h, w));
                    } else {
                        val = loadedData.getFloat(index.set(t, h, w));
                    }

                    val = (float) (val * this.val_scaler + this.val_offset);
                    RawDatapoint rd = new RawDatapoint(dt.clone(), (float) lat, (float) lon, val);
                    tempArr.add(rd);

                } catch (Exception e) {
                };

            }//for latlon      

            dt.addSeconds(this.dT_secs);
        }//for t

         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),
                    "getContentOnList: Extracted " + tempArr.size() + " observations");
        }
        return tempArr;
    }

    public ArrayList<RawDatapoint> getContentOnList(int height, Dtime first, Dtime last,
            Boundaries limits, Boundaries exclusion) {

        ArrayList<RawDatapoint> tempArr = new ArrayList<>();

        Index index = loadedData.getIndex();
        float val;

        double dlat = Math.abs(lats[0] - lats[1]);
        double dlon = Math.abs(lons[0] - lons[1]);

         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),
                    "netCDFhandle.getContentOnList: dlat / dlon = " + dlat + ", " + dlon);
        }
        Dtime dt = firstDt.clone();

        for (short t = 0; t < this.loadedShape[0]; t++) {
            if (first != null && last != null) {

                if (dt.systemHours() < first.systemHours() || dt.systemHours() > last.systemHours()) {
                    // EnfuserLogger.log(Level.FINEST,this.getClass(),"\t not suitable hour");
                    dt.addSeconds(this.dT_secs);

                    continue;
                }
            }
            dt.initOffset(firstDt); // checks the hour difference and stores this in freeByte. This value now partly reflects the quality of the forecasted datapoint
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "\t extracting" + dt.getStringDate() + ", data maturity = " + dt.freeByte);
            }

            int startH = 0;
            int endH = lats.length;

            int startW = 0;
            int endW = lons.length;

            for (int w = startW; w < endW; w++) { //lon cannot be inverted so a simple range check can be performed

                float lon = lons[w];

                if (limits != null && (lon < limits.lonmin || lon > limits.lonmax)) {
                    continue;
                }

                for (int h = startH; h < endH; h++) {

                    float lat = lats[h];

                    if (limits != null && (lat < limits.latmin || lat > limits.latmax)) {
                        continue;
                    }
                    if (exclusion != null && exclusion.intersectsOrContains(lat, lon)) {
                        continue;
                    }

                    if (hasHeight) {
                        val = loadedData.getFloat(index.set(t, height, h, w));

                    } else {
                        val = loadedData.getFloat(index.set(t, h, w));

                    }

                    val = (float) (val * this.val_scaler + this.val_offset);

                    RawDatapoint rd = new RawDatapoint(dt.clone(), lat, lon, val);
                    tempArr.add(rd);
                }//w
            }//h

            dt.addSeconds(this.dT_secs);
        }//for t

         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),
                    "getContentOnList: Extracted " + tempArr.size() + " observations");
        }
        return tempArr;
    }

    private int[] getSubsetGeoIndex(Boundaries limits) {

        int minH = Integer.MAX_VALUE;
        boolean lh = false;
        int maxH = Integer.MIN_VALUE;
        boolean uh = false;
        int minW = Integer.MAX_VALUE;
        boolean lw = false;
        int maxW = Integer.MIN_VALUE;
        boolean uw = false;

        for (int w = 0; w < this.lons.length; w++) { //lon cannot be inverted so a simple range check can be performed

            float lon = lons[w];

            if (limits != null && (lon < limits.lonmin || lon > limits.lonmax)) {
                continue;
            }

            for (int h = 0; h < lats.length; h++) {

                float lat = lats[h];

                if (limits != null && (lat < limits.latmin || lat > limits.latmax)) {
                    continue;
                }

                if (h < minH) {
                    minH = h;
                    lh = true;
                }
                if (h > maxH) {
                    maxH = h;
                    uh = true;
                }
                if (w < minW) {
                    minW = w;
                    lw = true;
                }
                if (w > maxW) {
                    maxW = w;
                    uw = true;
                }

            }//w
        }//h

        if (!lh || !uh || !lw || !uw) {
            return null; //subsetting not possible, areas do not align.                
        }
        return new int[]{minH, maxH, minW, maxW};

    }

    public ArrayList<GeoGrid> getSubsetOnGrid(int height, Dtime first, Dtime last,
            Boundaries limits, float[] scale_andAdd, Integer layerCountMax) {

        //EnfuserLogger.log(Level.FINEST,this.getClass(),"SubsetOnGrid: "+ first.getStringDate() +" - "+ last.getStringDate());
        Index index = loadedData.getIndex();
        float val;

        ArrayList<GeoGrid> geos = new ArrayList<>();

        int[] inds = getSubsetGeoIndex(limits);
        if (inds == null) {
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "getSubsetOnGrid: not possible, areas do not align: "
                        + this.getBounds().toText() + " VS " + limits.toText());
            }
            return geos;
        }

        int minH = inds[0];
        int maxH = inds[1];
        if (Math.abs(minH - maxH) < 2) {//this is bad (especially if this is 0)
            if (minH > 0) {
                minH--;//can be decreased
            }
            if (maxH < lats.length - 1) {
                maxH++;//can be increased
            }
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "subset indices was expanded since minH was close or equal to maxH");
            }
        }

        int minW = inds[2];
        int maxW = inds[3];

        if (Math.abs(minW - maxW) < 2) {//this is bad (especially if this is 0)
            if (minW > 0) {
                minW--;//can be decrased
            }
            if (maxW < lons.length - 1) {
                maxW++;
            }
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "subset indices was expanded since minW was close or equal to maxW");
            }
        }
        int subH = maxH - minH;
        int subW = maxW - minW;

        double latmin = lats[minH];
        double latmax = lats[maxH];
        boolean invert = false;
        if (latmin > latmax) {

            invert = true;
            double temp = latmin;
            latmin = latmax;
            latmax = temp;

        }

        Boundaries bsub = new Boundaries(
                latmin,
                latmax,
                this.lons[minW],
                this.lons[maxW]
        );

        if (bsub.latmin > bsub.latmax) {//flip these
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "\tWARNING! latitude bounds NEEDS TO BE flipped!");
            }
        }

         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"Original latlon dims = " + lats.length + ", " + lons.length);
            EnfuserLogger.log(Level.FINEST,this.getClass(),"subset index: h = " + minH + "-" + maxH + ", w=" + minW + "-" + maxW);
            EnfuserLogger.log(Level.FINEST,this.getClass(),"subBounds = " + bsub.toText() + ", new dims = " + subH + " x " + subW);
            EnfuserLogger.log(Level.FINEST,this.getClass(),"dateRange for extraction = " + first.getStringDate() + " - " + last.getStringDate());
        }

        Dtime dt = firstDt.clone();
        for (short t = 0; t < this.loadedShape[0]; t++) {

            if (dt.systemHours() < first.systemHours() || dt.systemHours() > last.systemHours()) {
                    EnfuserLogger.log(Level.FINEST,this.getClass(),"\t skip " + dt.getStringDate());
                dt.addSeconds(this.dT_secs);
                continue;
            }

            dt.initOffset(firstDt); // checks the hour difference and stores this in freeByte. This value now partly reflects the quality of the forecasted datapoint

            float[][] dat = new float[subH][subW];
            GeoGrid g = new GeoGrid(dat, dt.clone(), bsub);
            boolean hadNan = false;
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "\t extracting" + g.dt.getStringDate()
                        + ", dataMaturity = " + g.dt.freeByte);
            }

            for (int h = minH; h < maxH; h++) {
                for (int w = minW; w < maxW; w++) {

                    if (hasHeight) {
                        val = loadedData.getFloat(index.set(t, height, h, w));

                    } else {
                        val = loadedData.getFloat(index.set(t, h, w));

                    }
                    val = (float) (val * this.val_scaler + this.val_offset);

                    if (Float.isNaN(val)) {
                        if (!hadNan) {
                                EnfuserLogger.log(Level.FINE,this.getClass(),
                                        "netCdf.getSubset: NaN encountered for "
                                        + this.loadedVar + "," + dt.getStringDate() +","+this.filename);
                                //this is not yet crucial, the contents will be quality controlled during load afterwards.
  
                        }
                        val = 0;
                        g.dt.freeByte = 100;//flag as untrustworthy, so that it can be replaced with better data if available for the same time.
                        hadNan = true;
                    }

                    if (scale_andAdd != null) {
                        val *= scale_andAdd[0];
                        val += scale_andAdd[1];
                    }

                    int ch = h - minH;//if h is close to minH this is low
                    if (!invert) {
                        ch = maxH - 1 - h;//if h is close to minH, this is large, so, inverted.
                        //Note:In the used system h =0 corresponds to latmax (see geoGrid, Boundaries, AreaNfo for example)
                        //this means that 'inverted' is used more often than not.
                    }

                    g.values[ch][w - minW] = val;

                }//w
            }//h

            geos.add(g);
            if (layerCountMax != null && geos.size() >= layerCountMax) {
                break;
            }
            dt.addSeconds(this.dT_secs);
        }//for t

         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"getContentOnList: Extracted " + geos.size() + " subGeos.");
        }
        return geos;
    }

    public GeoGrid getContentOnGeoGrid_2d() {
        return getContentOnGeoGrid_2d(false, 0,0);
    }
    
    /**
     * This method is for extracting 2D GeoGrid from the loaded variable.
     * This is intended to be used for simple data, without time or height
     * dimensions. 
     * 
     * The method can check for NaN and fillValues (which are replaced to zero).
     * It can also 'flip' the geoGrid if the latitude dimension hints
     * that this needs to be done.
     * @return 
     */
    public GeoGrid getContentOnGeoGrid_2d_withChecks() {
            
        int fillVal  =-2147483648;
        if (this.loadedVarAttr!=null) {
            for (String[] test:this.loadedVarAttr) {
                if (test.length>=2) {
                    EnfuserLogger.log(Level.FINE, this.getClass(),
                            test[0] +" => " + test[1]);
                    
                    if (test[0].toLowerCase().contains("fillvalue")) {
                        fillVal = Double.valueOf(test[1]).intValue();
                        EnfuserLogger.log(Level.INFO, this.getClass(),
                            "Fillvalue is "+fillVal);
                    }
                }
            }
            
        }
        Index index = loadedData.getIndex();
        int nans =0;
        int fills=0;
        Boundaries bounds = this.getBounds();
        EnfuserLogger.log(Level.FINE, this.getClass(),
                            bounds.toText());
        if (!bounds.consistent()) {
            EnfuserLogger.log(Level.FINE, this.getClass(),
                            "Not consistent");
        }
        float[][] data = new float[lats.length][lons.length];
        Dtime dt = null;
        if (this.firstDt!=null){
            dt = this.firstDt.clone();
        }
        if (dt==null) dt = Dtime.getSystemDate();
        
        for (int h = 0; h < this.lats.length; h++) {
            for (int w = 0; w < this.lons.length; w++) {

                if (this.loadedShape.length==2) {
                  data[h][w] = loadedData.getFloat(index.set(h, w)); 
                  
                }else if (this.loadedShape.length==3 ) {
                  data[h][w] = loadedData.getFloat(index.set(0, h, w));
                } else {
                  data[h][w] = loadedData.getFloat(index.set(0,0, h, w));   
                }
                data[h][w] = (float) (data[h][w] * this.val_scaler + this.val_offset);
                if (Float.isNaN(data[h][w])) {
                    data[h][w]=0;
                    nans++;
                }
                int test = (int)data[h][w];
                if (test==fillVal) {
                    data[h][w]=0;
                    fills++;
                }

            }//w
        }//h
            EnfuserLogger.log(Level.INFO, this.getClass(),
                            "NaN count= "+nans +", fills ="+fills);
        GeoGrid g = new GeoGrid(data, dt, bounds.clone());
        
        //flip?
        float latDiff = this.lats[0] - this.lats[this.lats.length-1] ;
            EnfuserLogger.log(Level.FINE, this.getClass(),
                            "LatDiff="+latDiff);
            if (latDiff <0) {
                EnfuserLogger.log(Level.INFO, this.getClass(),
                            "Data will be Flipped vertically.");
                g= GeoGrid.flipY(g);
            }
        
        return g;
    }
    
    public GeoGrid getContentOnGeoGrid_2d(boolean takeDt, int dim1, int dim2) {

        Index index = loadedData.getIndex();

        Boundaries bounds = this.getBounds();

        float[][] data = new float[lats.length][lons.length];
        Dtime dt = null;
        if (takeDt && this.firstDt!=null){
            dt = this.firstDt.clone();
        }
        
        for (int h = 0; h < this.lats.length; h++) {
            for (int w = 0; w < this.lons.length; w++) {

                if (this.loadedShape.length<4 || dim2==0) {
                  data[h][w] = loadedData.getFloat(index.set(dim1, h, w));
                } else {
                  data[h][w] = loadedData.getFloat(index.set(dim1,dim2, h, w));   
                }
                data[h][w] = (float) (data[h][w] * this.val_scaler + this.val_offset);

            }//w
        }//h

        GeoGrid g = new GeoGrid(data, dt, bounds.clone());
        return g;
    }

    public GeoGrid[] getDynContentOnGeoGrid() {

        float[][][] dat = getDynamic(0);

        GeoGrid[] gs = new GeoGrid[dat.length];
        Dtime dt = this.firstDt;

        for (int i = 0; i < gs.length; i++) {

            gs[i] = new GeoGrid(dat[i], dt.clone(), this.getBounds().clone());
            dt.addSeconds(dT_secs);
        }

        return gs;
    }

    public Boundaries getBounds() {

        double latmax = (double) lats[0];
        double latmin = (double) lats[lats.length - 1];

        if (latmin > latmax) {
            latmin = (double) lats[0];
            latmax = (double) lats[lats.length - 1];
        }

        double lonmin = (double) lons[0];
        double lonmax = (double) lons[lons.length - 1];

        Boundaries bounds = new Boundaries(latmin, latmax, lonmin, lonmax);
        bounds.name = this.loadedVar;
         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"netHandle.getBounds: latmin = " + latmin
                    + ", latmax = " + latmax + ", lonmin = " + lonmin + ", lonmax = " + lonmax);
        }

        return bounds;
    }
    
    private ArrayList<Dim> dims;
    public ArrayList<Dim> readDimensions() {
        List<Dimension> dimensions = this.loadedVariable.getDimensions();
        this.dims = Dim.list(dimensions, ncfile);
        for (Dim d:dims) {
            System.out.println(d.toString());
        }
        return dims;
    }

    public void loadVariable(String var) {
        
        if (this.loadedVar.equals(var)) {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"Variable already loaded. ");
            for(int i:this.loadedShape) {
                EnfuserLogger.log(Level.FINEST,this.getClass(),"\tShape: "+i);
            }
            return;
        }
        
        this.val_scaler = 1f;
        this.val_offset = 0;
        this.data2D = false;
        EnfuserLogger.log(Level.FINEST,this.getClass(),"Variable names list length = " + this.varNames.size()
                    + ", amount of Variables = " + this.vars.size());
        
        for (int i = 0; i < this.varNames.size(); i++) {
            if (varNames.get(i).equals(var)) {

                try { 
                    EnfuserLogger.log(Level.FINEST,this.getClass(),"Loading variable: " + var);
                    
                    this.loadedVariable = vars.get(i);
                    this.loadedData = vars.get(i).read();
                    this.loadedVar = var;
                    this.loadedShape = loadedData.getShape();

                    Variable ncVar = this.vars.get(i);
                    //the dimensions of the grid table
                    EnfuserLogger.log(Level.FINEST,this.getClass(),"========DIMS ================");
                    
                    List<Dimension> dimensions = ncVar.getDimensions();
                    for (Dimension dim : dimensions) {
                        try {

                            String dName = dim.getFullName();
                            Variable dVar = this.ncfile.findVariable(dName);
                            Array dArray = dVar.read();

                            // the name of the dimension
                                EnfuserLogger.log(Level.FINEST,this.getClass(),dim.getFullName());
                                EnfuserLogger.log(Level.FINEST,this.getClass(),"\tDIM NAME TESTING (full, short): '"
                                        + dim.getFullName() + "', '" + dim.getShortName() + "'");


                            // the unit of the dimension
                            EnfuserLogger.log(Level.FINEST,this.getClass(),dVar.getUnitsString());

                            for (int j = 0; j < dVar.getSize(); j++) {
                                float val = dArray.getFloat(j);
                                if (dVar.getSize() < 20 || j % 10 == 0) {
                                    EnfuserLogger.log(Level.FINEST,this.getClass(),"\t\t j =" + j + " value = " + val);
                                }
                            }//for dim values

                        } catch (Exception e) {
                            //e.printStackTrace();
                        }
                    }//for dims
                    EnfuserLogger.log(Level.FINEST,this.getClass(),"========DIMS END================");
                    

                    if (loadedShape.length == 4) {
                        this.hasHeight = true;
                            EnfuserLogger.log(Level.FINEST,this.getClass(),"This is a 4-dim variable.");
                        
                    }

                    for (int k : this.loadedShape) {
                        EnfuserLogger.log(Level.FINEST,this.getClass(),"\t loadedShape: " + k);
                    }
                        EnfuserLogger.log(Level.FINER,this.getClass(),"Var loaded: "+var);
                    

                    List<Attribute> list = vars.get(i).getAttributes();
                    this.loadedVarAttr = new ArrayList<>();
                    for (int k = 0; k < list.size(); k++) {
                        Attribute l = list.get(k);

                            EnfuserLogger.log(Level.FINEST,this.getClass(),l.getFullName() + ", " + l.getStringValue());
                            EnfuserLogger.log(Level.FINEST,this.getClass(),"\tATTRIBUTE NAME TESTING (full, short): '"
                                    + l.getFullName() + "', '" + l.getShortName() + "'");
                        

                        String atrVal = l.getStringValue();
                        if (atrVal == null || atrVal.equals("null")) {
                            try {
                                double d = +l.getNumericValue().doubleValue();
                                    EnfuserLogger.log(Level.FINEST,this.getClass(),l.getFullName() + ", numeric value = " + d);
                                
                                atrVal = d + "";
                            } catch (Exception ee) {
                            }
                        }

                        String[] temp = {l.getFullName(), atrVal};
                        this.loadedVarAttr.add(temp);
                    }

                    this.checkForScalers();

                } catch (IOException ex) {
                    Logger.getLogger(NetcdfHandle.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public void close() {
        try {
            ncfile.close();
        } catch (IOException ioe) {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"couldn't close netcdf-file");
            ioe.printStackTrace();
        }
    }

    private void parseTimeInfo(Variable var) {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"parsing time info with '" + var + "'");

        try {

            Array data = var.read();
            int[] shape = data.getShape();
            int maxI = shape[0];
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),"time shape length: " + maxI);
            }

            this.times = new double[maxI];
            for (int i = 0; i < maxI; i++) {
                times[i] = data.getDouble(i);
                    EnfuserLogger.log(Level.FINEST,this.getClass(),"time =" + i + " value = " + times[i]);
                
            }

            if (times.length > 1) {
                this.timeDiff = (float) (times[1] - times[0]);//e.g. if the attribute says "minutes since" and there's a diff of 15 per slices => 15 minutes.
            }

             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),"time array parsed.");
                EnfuserLogger.log(Level.FINEST,this.getClass(),"Time diff per slices = " + this.timeDiff);
            }

        } catch (Exception ex) {
            Logger.getLogger(NetcdfHandle.class.getName()).log(Level.SEVERE, null, ex);
        }

        String initDate = null;
        dT_secs = 3600;//assume 1h

        List<Attribute> list = var.getAttributes();
        for (int k = 0; k < list.size(); k++) {
            Attribute l = list.get(k);
             {
                EnfuserLogger.log(Level.FINEST,this.getClass(),l.getFullName() + ", " + l.getStringValue());
            }
            String s = l.getStringValue();
            if (s == null) {
                continue;
            }
            if (l.getStringValue().contains("minutes since")) {
                dT_secs = NetcdfHandle.T_MINS;
                initDate = l.getStringValue().split("since ")[1];
                break;
            } else if (l.getStringValue().contains("hours since")) {
                dT_secs = NetcdfHandle.T_HOURS;
                initDate = l.getStringValue().split("since ")[1];
                break;
            } else if (l.getStringValue().contains("our since ")) {//allow this one special case for GFS data (Hour since)
                dT_secs = NetcdfHandle.T_HOURS;
                initDate = l.getStringValue().split("since ")[1];
                break;

            } else if (l.getStringValue().contains("days since")) {
                dT_secs = NetcdfHandle.T_DAYS;
                initDate = l.getStringValue().split("since ")[1];
                break;

            } else if (l.getStringValue().contains("seconds since")) {
                dT_secs = NetcdfHandle.T_SECS;
                initDate = l.getStringValue().split("since ")[1];
                break;

            } else if (l.getStringValue().contains("s since ")) {
                EnfuserLogger.log(Level.FINEST,this.getClass(),"ncHandle timeVar: not minutes, hours or days!");

                dT_secs = NetcdfHandle.T_DAYS;
                initDate = l.getStringValue().split("since ")[1];
                break;
            }

        }//for attr
        baseUnitT_secs = dT_secs;
        dT_secs *= this.timeDiff;
        
        if (initDate==null) {
            EnfuserLogger.log(Level.WARNING, this.getClass(),
                    "Time information parsing from "+ this.filename);
            return;
        }
        
        if (this.timeInfo_removeWhiteSpace) {
            initDate = initDate.replace(" ", "");
        }
            initDate = initDate.replace("Z", "");
            
        try {
            this.firstDt = new Dtime(initDate);
            this.firstDt_raw = new Dtime(initDate);
                EnfuserLogger.log(Level.FINEST,this.getClass(),"time information successfully parsed: " + firstDt.getStringDate());
            
            double secsAdd = baseUnitT_secs * this.times[0];
                EnfuserLogger.log(Level.FINEST,this.getClass(),"\t adding temporal units at startTime: " + times[0]);
            
            firstDt.addSeconds((int) secsAdd);
                EnfuserLogger.log(Level.FINEST,this.getClass(),"\t => " + firstDt.getStringDate());
            
            this.timeParse_success = true;
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            this.firstDt = new Dtime("2011-01-01T00:00:00");
            EnfuserLogger.log(Level.FINEST,this.getClass(),"Setting dt as " + this.firstDt.getStringDate());
        }

    }

    private void parseLatLon(String LATLON, Variable var) {
         {
            EnfuserLogger.log(Level.FINEST,this.getClass(),"parsing " + LATLON + " ...");
        }
        try {

            Array data = var.read();
            int[] shape = data.getShape();
            int maxI = shape[0];
            float[] arr = new float[maxI];
            for (int i = 0; i < maxI; i++) {
                arr[i] = data.getFloat(i);
                if (i % 50 == 0 || i == (maxI - 1)) {
                    EnfuserLogger.log(Level.FINEST,this.getClass(),LATLON + ", i=" + i + " value = " + arr[i]);
                }

            }

            if (LATLON.equals("LAT")) {
                for (int i = 0; i < arr.length; i++) {
                    arr[i] += latOff;
                }
                this.lats = arr;
            } else if (LATLON.equals("LON")) {
                for (int i = 0; i < arr.length; i++) {
                    arr[i] += lonOff;
                }
                this.lons = arr;

            } else {
                this.height = arr;
            }

             
          EnfuserLogger.log(Level.FINEST,this.getClass(),LATLON + " information successfully parsed.");
           
        } catch (IOException ex) {
            Logger.getLogger(NetcdfHandle.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public final static String SCALER_ID = "scale_factor";
    public final static String OFFSET_ID = "add_offset";
    public final static String FILLER_ID = "FillValue";
    public float fillValue = 9.969209968386869e36f;

    private void checkForScalers() {
        this.val_scaler = 1f;
        this.val_offset = 0;

        for (String[] attr : this.loadedVarAttr) {
            try {

                String name = attr[0];
                if (name.contains(OFFSET_ID)) {
                    this.val_offset = Double.valueOf(attr[1]);
                     {
                        EnfuserLogger.log(Level.FINEST,this.getClass(),"OFFSET parsed: " + val_offset);
                    }
                } else if (name.contains(SCALER_ID)) {
                    this.val_scaler = Double.valueOf(attr[1]);
                     {
                        EnfuserLogger.log(Level.FINEST,this.getClass(),"SCALER parsed: " + val_scaler);
                    }
                } else if (name.contains(FILLER_ID)) {
                    this.fillValue = Double.valueOf(attr[1]).floatValue();
                     {
                        EnfuserLogger.log(Level.FINEST,this.getClass(),"fillValue parsed: " + fillValue);
                    }
                }

            } catch (Exception e) {

            }
        }

    }

    public List<Attribute> getGlobalAttributes() {
        return this.globals;
    }




}
