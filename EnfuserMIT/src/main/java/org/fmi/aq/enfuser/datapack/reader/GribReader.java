/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.reader;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import ucar.ma2.Array;
import ucar.ma2.ForbiddenConversionException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class GribReader {

    public static HashMap<String, GeoGrid> getSubsetsOnGrid(boolean invert, String filename,
            HashMap<String, Byte> convTypeInts, Dtime start, Dtime end,
            Boundaries limits, HashMap<String, float[]> scale_andAdds) {

        HashMap<String, GeoGrid> hash = new HashMap<>();
        HashMap<String, ArrayList<float[]>> rawHash = new HashMap<>();
        // u <=> x, v <=> y

        try {
            // open netcdf/grib/grib2 file from argument
            NetcdfDataset gid = NetcdfDataset.openDataset(filename);
            // get all grid tables in the file
            NetcdfFile ref = gid.getReferencedFile();
            List<Variable> variables = ref.getVariables();
            Dtime dt_start = null;
            EnfuserLogger.log(Level.FINER,GribReader.class,"variables ============== PRINT OUT==============");
            
            for (int i = 0; i < variables.size(); i++) {
                Variable var = variables.get(i);
                    String msg = var.getUnitsString()
                    +", " + var.getFullName()
                    +", "+ var.getDataType() 
                    +", "+ var.getRanges()
                    +", "+ var.getDescription();
                    
                    EnfuserLogger.log(Level.FINER,GribReader.class,msg);
                String unit = var.getUnitsString();    
                String name = var.getFullName();
                if (name == null) {
                    continue;
                }
                if (name.equals("time") && unit.contains("since")) {
                    String stamp = unit.split("since")[1];
                    stamp = stamp.replace("Z", "");
                    stamp = stamp.replace(" ", "");
                    dt_start = new Dtime(stamp);
                    
                        EnfuserLogger.log(Level.FINER,GribReader.class,"Got TIME: " + dt_start.getStringDate());
                    
                }
 
                List<Attribute> atr = var.getAttributes();
                if (atr != null) {
                    for (Attribute at : atr) {
                            EnfuserLogger.log(Level.FINEST,GribReader.class,"\t attribute: " + at.getFullName() + ", " + at.getStringValue());
                    }
                }

            }
           
                EnfuserLogger.log(Level.FINER,GribReader.class,"variables ============== PRINT OUT==============");
            //get coordinates 
            
                EnfuserLogger.log(Level.FINER,GribReader.class,"reading data...");
            
            for (Variable var : variables) {
                String name = var.getFullName();

                Byte typeInt = convTypeInts.get(name);
                if (typeInt == null) {
                        EnfuserLogger.log(Level.FINER,GribReader.class,"SKIPPING " + name);
                    
                    continue;
                }

                float[] scaleAdd = scale_andAdds.get(name);

                if ((!var.isMetadata())
                        && (!var.getRanges().isEmpty())) {
                    
                    String msg =name +", "+ var.getDataType()+": "+ var.getRanges() +" "+ var.getUnitsString();
                    EnfuserLogger.log(Level.FINER,GribReader.class,msg);
                    
                    //the dimensions of the grid table
                    List<Dimension> dimensions = var.getDimensions();
                    for (int j = 0; j < dimensions.size(); j++) {
                        try {
                            // the name of the dimension
                            
                                String msg2 = dimensions.get(j).getFullName()
                                +", " +ref.findVariable(dimensions.get(j).getFullName())
                                        .findAttribute("_CoordinateAxisType").getStringValue()
                               +": "+ ref.findVariable(dimensions.get(j).getFullName()).getUnitsString();
                                
                            // the unit of the dimension
                                EnfuserLogger.log(Level.FINER,GribReader.class, msg2);
                            
                        } catch (Exception e) {
                        }
                    }

                    // the data in the grid table
                    Array dataArray = var.read();
                        EnfuserLogger.log(Level.FINER,GribReader.class,
                                "Dimensions count: " + dimensions.size());
                    

                    // calculate the total number of dimension combination
                    int elementsCount = 1;
                    ArrayList<Array> dimensionsData = new ArrayList<>();
                    boolean error = false;
                    for (Dimension dim : dimensions) {
                        elementsCount *= dim.getLength();
                        //preload the dimension value
                        try {
                            String dimName = dim.getFullName();
                                EnfuserLogger.log(Level.FINER,GribReader.class,"\t dimName = " + dimName);
                            
                            Array dat = ref.findVariable(dimName).read();
                                EnfuserLogger.log(Level.FINER,GribReader.class,"\t\t size of " + dat.getSize());
                            
                            //add the dimension data
                            dimensionsData.add(dat);

                        } catch (Exception e) {
                                EnfuserLogger.log(Level.FINER,GribReader.class,"\t cannot add dimension data!");
                            
                            error = true;
                        }

                    }
                        EnfuserLogger.log(Level.FINER,GribReader.class,"Elements total: " + elementsCount);
                    
                    if (error) {
                        continue;
                    }

                    float axisVal;
                    int arrayNumber;
                    Variable dimension;

                    float value;
                    float lat = -1;
                    float lon = -1;
                    int timeInt = -1;

                    int h = -1;
                    int w = -1;

                    for (int j = 0; j < var.getSize(); j++) {

                        int catchCount = 0;
                        try {
                            value = (dataArray.getFloat(j));
                            catchCount++;
                            if (scaleAdd != null) {
                                value = scaleAdd[0] * value + scaleAdd[1];
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            break;
                        }

                        // for merging the dimension value
                        int previousDimensionTotal = elementsCount;
                        int k = -1;

                        for (Dimension dim : dimensions) {//iterate dimensions
                            k++;
                            previousDimensionTotal /= dim.getLength();
                            arrayNumber = (j / previousDimensionTotal) % (dim.getLength());
                            dimension = ref.findVariable(dim.getFullName());

                            try {

                                //catch values
                                axisVal = dimensionsData.get(k).getFloat(arrayNumber);
                                String dimName = dimension.getFullName();

                                if (dimName.equals("time")) {
                                    timeInt = (int) axisVal;
                                    catchCount++;
                                } else if (dimName.equals("lat")) {
                                    lat = (int) Math.round(axisVal * 100) / 100f;
                                    h = arrayNumber;
                                    catchCount++;
                                } else if (dimName.equals("lon")) {
                                    lon = (int) Math.round(axisVal * 100) / 100f;
                                    w = arrayNumber;
                                    catchCount++;
                                }

                            } catch (ForbiddenConversionException | NullPointerException exrc) {
                            }

                        }//for dims

                        if (catchCount != 4) {
                            EnfuserLogger.log(Level.FINER,GribReader.class,
                                    "NOT ALL INFO WAS READ PROPERLY FOR DATAPOINT!");
                            continue;
                        }

                        if (j % 50 == 0) {
                                EnfuserLogger.log(Level.FINER,GribReader.class,
                                        "lat = " + lat + ", lon = " + lon + ", timeInt = "
                                        + timeInt + ", value = " + value + ", " + name + ", h =" + h + ",w=" + w);
                            
                        }
                        Dtime current = dt_start.clone();
                        current.addSeconds(timeInt * 3600);

                        //check boundaries
                        if (limits != null && !limits.intersectsOrContains(lat, lon)) {
                            continue;
                        }
                        if (start != null && end != null) {
                            if (current.systemHours() < start.systemHours() || current.systemHours() > end.systemHours()) {
                                continue;
                            }
                        }

                        float[] dat = {value, lat, lon, h, w, timeInt};
                        String key = typeInt + "_" + current.getStringDate();
                        ArrayList<float[]> rds = rawHash.get(key);
                        if (rds == null) {
                            rds = new ArrayList<>();
                            rawHash.put(key, rds);
                        }
                        rds.add(dat);

                    }// for variable values
                    EnfuserLogger.log(Level.FINER,GribReader.class,"--------------------------------------------------");
                }
            }//for variables
            gid.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        for (String key : rawHash.keySet()) {// for each type time -pair
            ArrayList<float[]> rds = rawHash.get(key);
            Boundaries b = new Boundaries(1800, -1800, 1800, -1800);//impossible bounds initially, will be updated
            String stamp = key.split("_")[1];
            Dtime dt = new Dtime(stamp.replace("+0:00", ""));
            int minH = Integer.MAX_VALUE;
            int maxH = -1;
            int minW = Integer.MAX_VALUE;
            int maxW = -1;
            for (float[] ff : rds) {
                float lat = ff[1];
                float lon = ff[2];
                int h = (int) ff[3];
                int w = (int) ff[4];

                if (lat < b.latmin) {
                    b.latmin = lat;
                }
                if (lat > b.latmax) {
                    b.latmax = lat;
                }
                if (lon < b.lonmin) {
                    b.lonmin = lon;
                }
                if (lon > b.lonmax) {
                    b.lonmax = lon;
                }
                if (h < minH) {
                    minH = h;
                }
                if (h > maxH) {
                    maxH = h;
                }
                if (w < minW) {
                    minW = w;
                }
                if (w > maxW) {
                    maxW = w;
                }

            }

                EnfuserLogger.log(Level.FINER,GribReader.class,b.toText() + "," + dt.getStringDate());
            
            int H = maxH - minH + 1;
            int W = maxW - minW + 1;
            float[][] dat = new float[H][W];
            int adds = 0;
                for (float[] ff : rds) {
                    float value = ff[0];
                    float lat = ff[1];
                    float lon = ff[2];
                    int h = (int) ff[3];
                    int w = (int) ff[4];

                    int cw = w - minW;
                    int ch = h - minH;
                    if (invert) {
                        ch = H - ch - 1;
                    }

                    dat[ch][cw] = value;
                    adds++;

                }
            int diff = H * W - adds;
                EnfuserLogger.log(Level.FINER,GribReader.class,"H x W =" + H + " x " + W);
            
            if (diff != 0) {
                EnfuserLogger.log(Level.INFO,GribReader.class,"ADDED CELLS DO NOT MATCH WitH GRID DIMENSIONS! ");
            }
                EnfuserLogger.log(Level.FINER,GribReader.class,"cells =" + H * W + ", adds =" + adds);
            
            GeoGrid g = new GeoGrid(dat, dt, b);//new GeoGrid(rds,6000,"name");
            hash.put(key, g);
        }//for key set

        EnfuserLogger.log(Level.FINER,GribReader.class,hash.size() + " geoGrids extracted from GRIB dataset.");
        return hash;
    }

}
