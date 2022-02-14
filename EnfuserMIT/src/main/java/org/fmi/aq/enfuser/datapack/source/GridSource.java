/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import java.io.File;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.meteorology.WindConverter;
import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.logging.GridQC;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;

/**
 *
 * @author johanssl
 */

public class GridSource extends AbstractSource {

    
    
    public GridSource(DataBase DB, DBline dbl, int typeInt, FusionOptions ops) {

        this.DB = DB;
        this.dbl = dbl;
        this.typeInt = typeInt;
        this.ID = dbl.ID;
        this.dom = new Domain();
        this.VARA = ops.VARA;
    }

    public boolean addGrid(GeoGrid g, String sourcef) {

        this.gridRes_km = (float) g.dlatInMeters / 1000f;
        if (g.dt.timeZone != 0 || g.dt.inSummerTime()) {
            g.dt.convertToUTC();
        }
        dom.update(g);//update domain

        Layer lold = this.getLayer(g.dt);

        if (lold == null) {//was not added previously, create a new Layer
            Layer l = new Layer(g, this,sourcef);
            this.putLayer(g.dt, l);

        } else {//was found
            lold.addGrid(g,sourcef);
        }
        return true;
    }

    @Override
    public String getDataPrintout(DataCore ens) {
        String n = "\n";
        String o = "Time, \t\t gridMin  \t gridMax  \t gridAve  \t maturity[h]  \t adds  \tsource" + n;
        String I = ",\t";
        AbstractSource wNalias = null;
        AbstractSource wEalias = null;

        if (!this.aliasTypes.isEmpty()) {
            for (AbstractSource ss : this.aliasTypes) {
                if (this.typeInt == VARA.VAR_WIND_N && ss.typeInt == VARA.VAR_WIND_E) {
                    wEalias = ss;
                } else if (this.typeInt == VARA.VAR_WIND_E && ss.typeInt == VARA.VAR_WIND_N) {
                    wNalias = ss;
                }
            }
        }
        //obs
        int k = 0;
        this.sortLayers();
        for (Layer l : this.timeSorted) {
            double[] mma = l.getMinMaxAve();
            k++;
            String file = l.sourceFile();
            
            o += l.g.dt.getStringDate_YYYY_MM_DDTHH() + I + editPrecision(mma[0], 2)
                    + I + editPrecision(mma[1], 2) + I + editPrecision(mma[2], 2)
                    + I + l.dataMatur + I + l.addedGrids + I + file +n;

            float[] NE = null;
            if (wNalias != null) {//this is E and alias has N
                Layer Nal = wNalias.getExactLayer(l.g.dt);//layer for Ncomp
                if (Nal != null) {//exists
                    double[] mma2 = Nal.getMinMaxAve();//Take N average
                    NE = new float[]{(float) mma2[2], (float) mma[2]};
                }
            } else if (wEalias != null) {//this is N and alias has E
                Layer Eal = wEalias.getExactLayer(l.g.dt);//layer for Ncomp
                if (Eal != null) {//exists
                    double[] mma2 = Eal.getMinMaxAve();
                    NE = new float[]{(float) mma[2], (float) mma2[2]};
                }
            }
            if (NE != null) {
                float[] dirV = WindConverter.NE_toDirSpeed_from(NE[0], NE[1], false);
                o += "\t avg.dir = " + (int) dirV[0] + ", avg.v=" + editPrecision(dirV[1], 1) + n;
            }

            if (k > 8690) {
                o += "...and many more." + n;
                break;
            }

        }//for obs

        return o;
    }

    public GeoGrid getEnhancedGrid(Dtime dt, float res_m) {
        Layer l = this.getClosestLayer(dt, 6);
        if (l == null) {
            return null;
        }
        Boundaries b = new Boundaries(this.dom.latmin, this.dom.latmax, this.dom.lonmin, this.dom.lonmax);
        AreaNfo in = new AreaNfo(b, res_m, dt.clone());

        float[][] dat = new float[in.H][in.W];
        GeoGrid gg = new GeoGrid(dat, in.dt, in.bounds);
        for (int h = 0; h < in.H; h++) {
            for (int w = 0; w < in.W; w++) {

                double lat = in.getLat(h);
                double lon = in.getLon(w);

                Float val = this.getSmoothValue(dt, lat, lon);
                if (val != null) {
                    gg.values[h][w] = val;
                }

            }
        }
        return gg;
    }

    @Override
    public void finalizeStructure(DataCore ens) {
        this.crossCheckAliasTypes(ens.datapack);
    }

    @Override
    public Float getSmoothValue(Dtime dt, double lat, double lon) {
        Layer l = this.getClosestLayer_backwards(dt, 6);
        if (l == null) {
            return null;
        }
        GeoGrid g = l.g;
        GeoGrid g2 = null;
        //layer l exists.
        float frac2 = 0;
        Layer l2 = this.getClosestLayer_forwards(dt, 6);
        if (l2 != null) {
            float diff_mins = l2.dt_system_min - l.dt_system_min;
            if (diff_mins > 0) {
                g2 = l2.g;
                frac2 = (dt.systemSeconds() / 60 - l.dt_system_min) / diff_mins;
            }
        }

        float val = Layer.getSmoothGridInterpolation(l.rl, lat, lon, g, g2, frac2);
        return val;
    }

    @Override
    public Observation getExactRawValue(Dtime dt, double lat, double lon) {
        Layer l = this.getExactLayer(dt);
        if (l == null) {
            return null;
        }

        float val = (float) l.g.getValue_closest(lat, lon);
        Observation o = new Observation(typeInt, ID, (float) lat, (float) lon,
                dt, val, (float) dbl.height_m, null);
        return o;
    }

    /**
     * Get all Geogrids this GridSource holds.
     * @return a list of GeoGrids
     */
    public ArrayList<GeoGrid> getGrids() {
        ArrayList<GeoGrid> gg = new ArrayList<>();
        for (Layer l : this.layerHash.values()) {
            if (l!=null && l.g!=null) {
                gg.add(l.g);
            }
        }
        return gg;
    }

    /**
     * Find a GeoGrid in a layer that matches time-wise.
     * @param dt the time
     * @return the grid
     */
    public GeoGrid getExactGrid(Dtime dt) {
       Layer l = this.getExactLayer(dt);
       if (l==null) return null;
       return l.g;
    }
    
    private boolean incompleteCoverage=false;
    @Override
    public String oneLinePrinout(FusionOptions ops) {
        String s = this.ID +": gridSource, ";
        Dtime end = ops.getRunParameters().end();
        Dtime current = ops.getRunParameters().start().clone();
        current.addSeconds(-3600);
        
        differentGeos = false;
        missing=0;
        String missingString =" ";
        n =0;
        min = Double.MAX_VALUE;
        max = Double.MAX_VALUE*-1;
        ave = 0;
        checkGeo = null;
        File fileFirst =null;
        
        while (current.systemHours()< end.systemHours()) {
            current.addSeconds(3600);
            Layer l = this.getExactLayer(current);
            if (l==null || l.g==null) {
                missing++;
                missingString+= "("+current.getStringDate_YYYY_MM_DDTHH() +"), ";
                continue;
            }
            
            n++;
            if (l.sourceFile()!=null && fileFirst==null) {
                fileFirst = new File(l.sourceFile());
            }
            if (checkGeo==null) {
                checkGeo = GridQC.checkString_editPrecision(l.g);
                if (!l.g.gridBounds.fullyContains(ops.bounds())) {
                    incompleteCoverage=true;
                }
            } else {
                String check2 = GridQC.checkString_editPrecision(l.g);
                if (!check2.equals(checkGeo)) {
                   differentGeos =true; 
                }
            }
            int sparser =5;
            double[] minMaxAve = l.g.getMinMaxAverage(sparser);
            double mi = minMaxAve[0];
                if (mi<min) min = mi;
            double ma = minMaxAve[1];
                if (ma>max) max = ma;
            double av = minMaxAve[2];
                ave+=av;
            
        }//while time
        ave/=n;
        String filex ="";
        if (fileFirst!=null) filex =", fileExample="+fileFirst.getAbsolutePath();
        
       s += "available="+n +", missing = "+missing +missingString + filex
                +"\t\t\tgeo="+checkGeo
                + ", min="+editPrecision(min,2) 
                +", max ="+editPrecision(max,2) 
                +", ave="+editPrecision(ave,2);  
       if (differentGeos) s+=", hasDifferentGeos="+differentGeos;
       if (incompleteCoverage) s+=", hasIncompleteCoverage="+incompleteCoverage;
       return s;
    }

    @Override
    public Level getSeverityDataQuality(FusionOptions ops) {
        if (this.differentGeos) {
            return Level.WARNING;
        } else if (missing > 0) {
            return Level.WARNING;
        } else if (min == max && n > 10) {
            if (this.typeInt == VARA.VAR_SKYCOND || this.typeInt == VARA.VAR_RAIN) {
                return Level.INFO;
            }//for these the data can be constant and it's still ok.
            
            return Level.WARNING;
        } else if (this.incompleteCoverage) {
            return Level.WARNING;
        }
        return Level.INFO;
    }

    @Override
    public String getDataQualityAlert() {
        String start =  this.ID+", " +VARA.VAR_NAME(this.typeInt);
        if (this.differentGeos) {
            return start+": dimensions change over the modelling time span.";
        } else if (missing > 0) {
            return start+": gridded source has temporal gaps.";
        } else if (min == max && n > 10) {
            return start+": gridded values seem constant.";
        } else if (this.incompleteCoverage) {
            return start+": seems to have incomplete coverage with respect to modelling area.";
        }
        return "";
    }

    /**
     * Find the first layer with a GeoGrid and return the resolution of it
     * in kilometers. If none is found then this return Double.MAX_VALUE;
     * @return grid resolution [km];
     */
    public double getDataResolution_km() {
        for (Layer l:this.layerHash.values()) {
            GeoGrid g = l.g;
            if (g==null) continue;
            return editPrecision(Math.sqrt(g.dlatInMeters*g.dlonInMeters)/1000,4);
        }
        return Double.MAX_VALUE;
    }

}
