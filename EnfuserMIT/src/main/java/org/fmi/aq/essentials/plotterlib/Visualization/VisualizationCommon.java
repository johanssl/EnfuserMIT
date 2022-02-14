/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import static org.fmi.aq.essentials.plotterlib.Visualization.FigureData.fileTypes;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 *
 * @author Lasse
 */
public class VisualizationCommon {


    public static BufferedImage fileToBimg(String fullName) {

        BufferedImage bimg = null;
        try {
            bimg = ImageIO.read(new File(fullName));

        } catch (IOException e) {
            e.printStackTrace();

        }
        return bimg;
    }

    public static void saveImageToFile(BufferedImage fullImg, String dir, String name, int figType) {

        BufferedImage img;
        if (figType == FigureData.IMG_FILE_JPG) {

            // create a blank, RGB, same width and height, and a white background
            img = new BufferedImage(fullImg.getWidth(),
                    fullImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            img.createGraphics().drawImage(fullImg, 0, 0, Color.WHITE, null);

        } else {
            img = fullImg;
        }

        String fullName = dir + name + "." + fileTypes[figType];
        try {
            // Save as new values_img
            ImageIO.write(img, fileTypes[figType], new File(fullName));
        } catch (IOException ex) {

        }

    }

    public static String getShortStringFromValue(double value) {
        String s = "N/A";
        String pot;
        int prec;

        if (Math.abs(value) >= 100000) {
            prec = 1;

            for (int i = 16; i > 4; i--) {
                double comparison = Math.pow(10, i);
                if (value > comparison) {
                    pot = "E+" + i;
                    double temp = value / comparison;
                    temp = editPrecision_extended(temp, prec);
                    s = temp + pot;
                    return s;
                }
            }

        } else if (Math.abs(value) >= 10000) {
            prec = -2;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
            s = s.replace(".0", "");

        } else if (Math.abs(value) >= 1000) {
            prec = -1;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
            s = s.replace(".0", "");

        } else if (Math.abs(value) >= 10) {
            prec = 0;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
            s = s.replace(".0", "");

        } else if (Math.abs(value) >= 1) {
            prec = 2;
            double temp = editPrecision(value, prec);
            s = temp + "";

        } else {
            prec = 23;
            double temp = editPrecision(value, prec);
            s = temp + "";
        }

        return s;

    }

    public static double editPrecision_extended(double value, int precision) {

        if (precision >= 0) {
            long prec = (long) Math.pow(10, precision);
            long temp = (long) (prec * value);
            double result = (double) temp / prec;
            return result;
        } else {
            // reduction in precision, e.g. 12345 => 12000
            int divisor = (int) Math.pow(10, Math.abs(precision));
            int temp = (int) (value / divisor);
            double result = temp * divisor;
            return result;

        }

    }

}
