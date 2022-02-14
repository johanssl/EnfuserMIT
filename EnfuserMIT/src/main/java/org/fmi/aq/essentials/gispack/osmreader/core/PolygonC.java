package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.ArrayList;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.flowestimator.AreaFilter;

/**
 * This class has been built for a single purpose:
 * It describes a polygon in the lat,lon space.
 * The method contains() is to check whether or not a point is inside the polygon.
 * The method itself is based on awt.Polygon.
 * @author johanssl
 */
public class PolygonC  {

    public final int npoints;
    public final double[] lons;
    public final double[] lats;
    public Boundaries b;

    public PolygonC(ArrayList<double[]> latlons) {
        this.npoints = latlons.size();
        lons = new double[npoints];
        lats = new double[npoints];
        int j =0;
        
        b =AreaFilter.getMaxBounds(latlons);
       
        for (double[] cc:latlons) {
            double lat = cc[0];
            double lon = cc[1];
            lats[j] = lat;//latitude to y
            lons[j] = lon;//longitude to x
            j++;
        }
        
    }

    public boolean contains(double lat, double lon) {
        if (npoints <= 2 || !b.intersectsOrContains(lat, lon)) {
            return false;
        }
        int hits = 0;
        double lastx = lons[npoints - 1];
        double lasty = lats[npoints - 1];
        double currLon, currLat;

        // Walk the edges of the polygon
        for (int i = 0; i < npoints; lastx = currLon, lasty = currLat, i++) {
            currLon = lons[i];
            currLat = lats[i];

            if (currLat == lasty) {
                continue;
            }

            double leftx;
            if (currLon < lastx) {
                if (lon >= lastx) {
                    continue;
                }
                leftx = currLon;
            } else {
                if (lon >= currLon) {
                    continue;
                }
                leftx = lastx;
            }

            double test1, test2;
            if (currLat < lasty) {
                if (lat < currLat || lat >= lasty) {
                    continue;
                }
                if (lon < leftx) {
                    hits++;
                    continue;
                }
                test1 = lon - currLon;
                test2 = lat - currLat;
            } else {
                if (lat < lasty || lat >= currLat) {
                    continue;
                }
                if (lon < leftx) {
                    hits++;
                    continue;
                }
                test1 = lon - lastx;
                test2 = lat - lasty;
            }

            if (test1 < (test2 / (lasty - currLat) * (lastx - currLon))) {
                hits++;
            }
        }

        return ((hits & 1) != 0);
    }

public static void main(String[] args) {
    test();
}    
public static void test() {
    
    ArrayList<double[]> coords = new ArrayList<>();
    coords.add(new double[]{1,1});  
    coords.add(new double[]{2,1});  
    coords.add(new double[]{2,2});  
    coords.add(new double[]{2.1,2.7});  
    coords.add(new double[]{1.5,1.5});
    coords.add(new double[]{0.1,1.5});
    coords.add(new double[]{1,1});  
    
    PolygonC c = new PolygonC(coords);
    
    for (double lat = 3;lat>=0;lat-=0.1) {
        String line ="";
        for (double lon = 0;lon<3;lon+=0.1) {
            boolean in = c.contains(lat, lon);
            if (in) {
                line+="1;";
            }else {
                line+="0;";
            }
            
        }
        System.out.println(line);
    }
}    
   
}