/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.awt.Image;
import java.awt.image.BufferedImage;

/**
 *
 * @author johanssl
 */
public class FDoneliner {

    public static Image getScaledGridImage(float[][] data, int minRes, int scheme, double scaleProgression, double[] minMax, int transp) {

        GeoGrid g = new GeoGrid(data, null, Boundaries.getDefault());
        VisualOptions ops = new VisualOptions();
        ops.minMax = minMax;
        ops.colBarStyle = VisualOptions.CBAR_NONE;
        ops.bannerStyle = VisualOptions.BANNER_NONE;
        ops.scaleProgression = scaleProgression;
        ops.transparency = transp;
        FigureData fd = new FigureData(g, ops);
        fd.setColorPalette(scheme);

        return fd.getScaledImage(minRes);

    }

    public static BufferedImage getBufferedImage(float[][] data, int scheme, double scaleProgression, double[] minMax, int transp) {

        GeoGrid g = new GeoGrid(data, null, Boundaries.getDefault());

        VisualOptions ops = new VisualOptions();
        ops.minMax = minMax;
        ops.scaleProgression = scaleProgression;
        ops.transparency = transp;

        FigureData fd = new FigureData(g, ops);
        fd.setColorPalette(scheme);

        return fd.getBufferedImage();

    }

}
