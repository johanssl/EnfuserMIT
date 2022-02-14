/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 *
 * @author johanssl
 */
public class GraphicsRotator {

    public static Graphics setRotatedText(Graphics g, int x, int y, double degrees, String text) {

        Graphics2D g2D = (Graphics2D) g;

        // Create a rotation transformation for the font.
        AffineTransform fontAT = new AffineTransform();

        // get the current font
        Font theFont = g2D.getFont();

        // Derive a new font using a rotatation transform
        double theta = degrees * (2 * Math.PI) / 360.0; // in radians
        fontAT.rotate(theta);
        Font theDerivedFont = theFont.deriveFont(fontAT);

        // set the derived font in the Graphics2D context
        g2D.setFont(theDerivedFont);

        // Render a string using the derived font
        try {
            g2D.drawString(text, x, y);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } //RHEL 7.4 problem with fonts could occur
        // put the original font back
        g2D.setFont(theFont);

        Graphics gg = (Graphics) (g2D);
        return gg;
    }

    public static Graphics2D setRotatedImage(Graphics2D g2D, int x, int y, double degrees, BufferedImage img) {

        // Create a rotation transformation for the font.
        AffineTransform at = new AffineTransform();
        // int dy = (int)(img.getHeight()*0.5f);
        // int dx = (int)(img.getWidth()*0.5f);

        double theta = degrees * (2 * Math.PI) / 360.0; // in radians
        //  dy = (int)(Math.cos(degrees)*dy);
        //  dx = (int)(Math.sin(degrees)*dy);
        at.translate(x, y);

        // Derive a new font using a rotatation transform
        at.rotate(theta);

        // Render a string using the derived font
        g2D.drawImage(img, at, null);

        return g2D;
    }

    public static Graphics2D setRotatedImage(Graphics2D g2D, int x, int y, double degrees, Image img) {

        // Create a rotation transformation for the font.
        AffineTransform at = new AffineTransform();
        at.translate(x, y);

        // Derive a new font using a rotatation transform
        double theta = degrees * (2 * Math.PI) / 360.0; // in radians
        at.rotate(theta);

        // Render a string using the derived font
        g2D.drawImage(img, at, null);

        return g2D;
    }

    public static void setRotatedText(Graphics2D g2D, int x, int y, double degrees, String text) {

        // Create a rotation transformation for the font.
        AffineTransform fontAT = new AffineTransform();

        // get the current font
        Font theFont = g2D.getFont();

        // Derive a new font using a rotatation transform
        double theta = degrees * (2 * Math.PI) / 360.0; // in radians
        fontAT.rotate(theta);
        Font theDerivedFont = theFont.deriveFont(fontAT);

        // set the derived font in the Graphics2D context
        g2D.setFont(theDerivedFont);

        // Render a string using the derived font
        try {
            g2D.drawString(text, x, y);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } //RHEL 7.4 problem with fonts could occur

        // put the original font back
        g2D.setFont(theFont);

    }

    public static void setRotatedArrow(Graphics2D g2D, int x, int y, double degrees) {

        String text = "-->";
        // Create a rotation transformation for the font.
        AffineTransform fontAT = new AffineTransform();

        // get the current font
        Font theFont = g2D.getFont();

        // Derive a new font using a rotatation transform
        double theta = 270 * (2 * Math.PI) / 360.0; // in radians
        double phi = degrees * (2 * Math.PI) / 360.0;
        fontAT.rotate(theta);// now the arrow is up
        fontAT.rotate(phi);// now the arrow is up

        Font theDerivedFont = theFont.deriveFont(fontAT);

        // set the derived font in the Graphics2D context
        g2D.setFont(theDerivedFont);

        // Render a string using the derived font
        try {
            g2D.drawString(text, x, y);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } //RHEL 7.4 problem with fonts could occur

        // put the original font back
        g2D.setFont(theFont);

    }

    public static void placeRotatedImag(Graphics2D g2D, int x, int y, Image img, double degrees) {

        double rotation = degrees * (2 * Math.PI) / 360.0;
        double locationX = img.getWidth(null) / 2;
        double locationY = img.getHeight(null) / 2;
        AffineTransform tx = AffineTransform.getRotateInstance(rotation, locationX, locationY);

        g2D.drawImage(img, tx, null);

    }

}
