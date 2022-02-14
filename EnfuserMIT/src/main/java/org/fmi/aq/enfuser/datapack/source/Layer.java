/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

import java.io.File;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.Serializable;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import org.fmi.aq.essentials.gispack.osmreader.core.RadiusList;

/**
 * This class serves as a container for Observation in a given time Layer. If
 * the Layer contains more than one Observation, then the contents of this layer
 * will be processed into Grid layer (smoother, faster). One important task for
 * the Layer class is to manage DUPLICATE observations (same location, different
 * creation time, e.g. modelled operative data).
 *
 * @author johanssl
 */
public class Layer implements Serializable {

    private Observation singular;
    private float singular_aveSum=0;
    private int singular_aveN;
    
    public byte dataMatur = 120;
    public final long dt_system_min;// hourcounter

//sectors
    public GeoGrid g;
    RadiusList rl = null;
    private final static int RAD_N = 3;
//a radiusList for smooth Kriging work
    boolean grid = false;
    AbstractSource s;

    int addedObs = 0;
    public int addedGrids = 0;
    private String sourcefile=null;
    public Layer(Observation ob, AbstractSource s) {

        singular = ob;
        this.singular_aveSum = ob.value;
        this.singular_aveN =1;
        
        this.dt_system_min = (ob.dt.systemSeconds() / 60);
        this.dataMatur = ob.dataMaturity_h();
        this.addedObs = 1;
        this.s = s;
        sourcefile = ob.sourceFile;
    }

    public String sourceFile() {
        if (this.sourcefile==null) return null;
        File f = new File(this.sourcefile);
        return f.getAbsolutePath();
    }
    
    public Layer(GeoGrid g, GridSource s,String sourcefile) {

        this.dt_system_min = (g.dt.systemSeconds() / 60);
        this.dataMatur = g.dt.freeByte;
        this.addedGrids = 1;
        this.g = g;
        this.sourcefile = sourcefile;
        this.s = s;
        grid = true;
        this.rl = new RadiusList(g, RAD_N);
    }

    public boolean gridSourceLayer() {
        return (s instanceof GridSource);
    }

    public void addGrid(GeoGrid g,String sourcefile) {
        this.addedGrids++;

        if (this.dataMatur > g.dt.freeByte) {
            // EnfuserLogger.log(Level.FINER,this.getClass(),"Layer.addGrid ====================\n previous maturity = "+ this.dataMatur + ", new = "+g.dt.freeByte );
            this.dataMatur = g.dt.freeByte;
            this.g = g;
            this.grid = true;
            this.sourcefile = sourcefile;
        }

    }

    public double[] getMinMaxAve() {

        if (!gridSourceLayer()) {//not grids
            return new double[]{singular.value, singular.value, singular.value};
        } else {
            //possible nullpointer if grid has not been created yet?
            double[] minMaxAve = {Double.MAX_VALUE, Double.MIN_VALUE, 0};
            int n = 0;
            int step = (int) (0.1 * this.g.H);//should be 10% step size => 10x 10 iteration
            if (step < 1) {
                step = 1;
            }
            for (int h = 0; h < this.g.H; h += step) {
                for (int w = 0; w < this.g.W; w += step) {
                    n++;
                    double d = this.g.values[h][w];
                    if (minMaxAve[0] > d) {
                        minMaxAve[0] = d;
                    }
                    if (minMaxAve[1] < d) {
                        minMaxAve[1] = d;
                    }
                    minMaxAve[2] += d;
                }
            }
            minMaxAve[2] /= n;
            return minMaxAve;
        }//grids
    }

    public void addObservation(Observation ob) {
        this.addedObs++;
        //check data maturity - if lower then reset and replace
        if (ob.dataMaturity_h() < this.dataMatur) {
            this.dataMatur = ob.dataMaturity_h();
            ob.logReplacement(this.singular);
            this.singular = ob;
            this.sourcefile = ob.sourceFile;
        } else if (ob.dataMaturity_h() == this.dataMatur) {//update average
            
            if (ob.typeInt == s.VARA.VAR_WINDDIR_DEG) {//this is difficult to average. Consider values of 359 and 1. 180 is not a good "average".
                if (Math.abs(ob.value - this.singular.value)> 90) return;
            }
              this.singular_aveSum+=ob.value;
              this.singular_aveN++;
              this.singular.value = this.singular_aveSum/this.singular_aveN;
              this.singular.logAveragingCount(this.singular_aveN);  
  
            
        }
    }

    public int currentSize() {
        if (g == null) {
            return 1;
        } else {
            return g.H * g.W;
        }
    }

    /**
     * Get a smooth interpolated value based on the gridded data for any
     * arbitrary point within the grid using Kriging interpolation. In case the
     * requested point is out-of-bounds, then the nearest point in the borders
     * of the grid is returned.
     *
     * Another grid can be used in the process (g2) and if this is not null,
     * then the final returned value is an affine combination of the two
     * smoothed grid values. where factor w2 defines the weight put for g2. The
     * main use case is to interpolate a smooth value also temporally, e.g.,
     * Grid one (g1) is the "previous hourly layer and g2 is the next hourly
     * layer.
     *
     * @param rl a RadiusList element to facilitate fast processing of nearby
     * cells int the grid. The amount of cells in the list defines how many
     * nearby cells are being assimilated. The more, the smoother outcome. The
     * default amount for the list should be 16 (RADIUS of 2). Minimum amount is
     * 9 (RADIUS of 1).
     * @param lat the latitude
     * @param lon the longitude
     * @param g the grid holding the data to be interpolated
     * @param g2 second grid, can be null
     * @param w2 weight for g2 in case g2 is not null and temporal interpolation
     * is also to be applied
     * @return a smoother interpolated grid value
     */
    public static float getSmoothGridInterpolation(RadiusList rl, double lat, double lon, GeoGrid g,
            GeoGrid g2, float w2) {

        double wvalsum1 = 0;
        double wvalsum2 = 0;

        //border control
        if (lat > g.gridBounds.latmax) {
            lat = g.gridBounds.latmax;
        }
        if (lat < g.gridBounds.latmin) {
            lat = g.gridBounds.latmin;
        }

        if (lon > g.gridBounds.lonmax) {
            lon = g.gridBounds.lonmax;
        }
        if (lon < g.gridBounds.lonmin) {
            lon = g.gridBounds.lonmin;
        }

        //find the closest cell h,w
        int h = g.getH(lat);
        int w = g.getW(lon);
        
        int hg2=0;
        int wg2=0;
        double distMin2 = 0;
        double distMax2 = 0;
        
            if (g2!=null) {
               hg2 = g2.getH(lat);
               wg2 = g2.getW(lon);  
               distMin2 = 0.2 * g2.dlatInMeters;
               distMax2 = 2.1 * g2.dlatInMeters;
            }
        
        double wsum =  0.000001;
        double wsum2 = 0.000001;
        
        double distMin = 0.2 * g.dlatInMeters;
        double distMax = 2.1 * g.dlatInMeters;

        
        for (RadListElement e : rl.elems) {
            int ch = h + e.hAdd;
            int cw = w + e.wAdd;
            double clat = g.getLatitude(ch);
            double clon = g.getLongitude(cw);

                if (!g.oobs_hw(ch, cw)) {
                    double val = g.getValueAtIndex(ch, cw);
                    double dist = Observation.getDistance_m(lat, lon, clat, clon);
                    if (dist < distMin) {
                        dist = distMin;
                    }

                    if (dist < distMax) {
                        wvalsum1 += val / dist;
                        wsum += 1.0 / dist;
                    }
                }
                
                //second grid
                if (g2 != null) {
                    ch = hg2 + e.hAdd;
                    cw = wg2 + e.wAdd;
                    clat = g2.getLatitude(ch);
                    clon = g2.getLongitude(cw);
            
                    if (!g2.oobs_hw(ch, cw)) {
                        double val = g2.getValueAtIndex(ch, cw);
                        double dist = Observation.getDistance_m(lat, lon, clat, clon);
                        if (dist < distMin2) {
                            dist = distMin2;
                        }

                        if (dist < distMax2) {
                            wvalsum2 += val / dist;
                            wsum2 += 1.0 / dist;
                        }
                    }
                }//if g2
        }//for radList
        
        wvalsum1 /= wsum;
        if (g2 == null) {
            return (float) wvalsum1;
        }

        //also temporal interpolation is at play
        wvalsum2 /= wsum2;
        float w1 = 1f - w2;
        return (float) (w1 * wvalsum1 + w2 * wvalsum2);
    }

    public int getSysHour() {
        return (int) (this.dt_system_min / 60);
    }

    public Observation getStationaryObs() {
        return this.singular;
    }

}
