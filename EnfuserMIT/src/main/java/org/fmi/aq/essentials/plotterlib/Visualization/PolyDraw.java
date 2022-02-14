/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import java.util.ArrayList;

/**
 *
 * @author johanssl
 */
public class PolyDraw {

    public final static int transToInt = 10000;
    public final static double latShift = 90.0;
    public final static double lonShift = 180.0;
    public final static int maxY = 180 * transToInt;
    public final static int maxX = 360 * transToInt;
    
    public static int latToY(double lat) {
        return maxY - (int) ((lat + latShift) * transToInt);
    }

    public static int lonToX(double lon) {
        return (int) ((lon + lonShift) * transToInt);
    }
    
    public static double yToLat(int y) {
        double lat = (double) (maxY - y) / (double) transToInt - latShift;
        return lat;
    }

    public static double xToLon(int x) {
        double lon = (double) x / (double) transToInt - lonShift;
        return lon;
    }

    public static void drawToCanvas(Canvas canvas, ArrayList<Polygon> polys, int h, int w,
            double minlat, double maxlat, double minlon, double maxlon, VisualOptions ops) {
        canvas.setIgnoreRepaint(false);
        canvas.setSize(w, h);
        Graphics g = canvas.getGraphics();
        g.clearRect(0, 0, 1600, 1600);

        drawToGraphics(g, polys, h, w, minlat, maxlat, minlon, maxlon, ops);

        canvas.setIgnoreRepaint(true);
    }

    public static void drawToGraphics(Graphics g, ArrayList<Polygon> polys, int h, int w,
            double minlat, double maxlat, double minlon, double maxlon, VisualOptions ops) {

        //calculate offset
        int yOff = latToY(maxlat);
        int xOff = lonToX(minlon);

        double xScaler = (double) w / (lonToX(maxlon) - xOff);
        double yScaler = Math.abs((double) h / (latToY(minlat) - yOff));
        
        AwtOptions awt = ops.getAWTops();
        Color sea = awt.getCustomColor(VisualOptions.I_GIS_BG_SEAS);
        Color border = awt.getCustomColor(VisualOptions.I_GIS_GB_SEA_BORDER);
        for (Polygon p:polys) {

            int[] newX = new int[p.npoints];
            int[] newY = new int[p.npoints];
            for (int j = 0; j < p.npoints; j++) {
                newX[j] = (int) ((p.xpoints[j] - xOff) * xScaler);
                newY[j] = (int) ((p.ypoints[j] - yOff) * yScaler);
            }
            Polygon pp = new Polygon(newX, newY, p.npoints);
            if (sea!=null) {
                g.setColor(sea);
                g.fillPolygon(pp);
            }
            if (border != null) {
                g.setColor(border);
                g.drawPolygon(pp);
            }
        }

        g.dispose();

    }



}
