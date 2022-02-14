/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author johanssl
 */
public class MultiElementImage {
    
       
    
     public static BufferedImage multiElementImage(ArrayList<BufferedImage> imgs, String odir, String nam) {
        BufferedImage comp =multiElementImage(imgs);
        new Imager().saveImage(odir, comp, nam, FigureData.IMG_FILE_PNG);
        return comp;
    }

    public static BufferedImage multiElementImage(Image[][] imgs, boolean exitOnClose, boolean draw) {
        int h = imgs[0][0].getHeight(null);
        int w = imgs[0][0].getWidth(null);

        int rows = imgs.length;
        int cols = imgs[0].length;

        BufferedImage bigPic = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bigPic.createGraphics();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Image simg = imgs[row][col];
                if (simg == null) {
                    continue;
                }

                int hp = row * h;
                int wp = col * w;
                g2d.drawImage(simg, wp, hp, null);
            }
        }
        g2d.dispose();
        if (draw)new Imager().imageToFrame(bigPic, exitOnClose);
        return bigPic;
    }
    
    public static BufferedImage multiElementImage(ArrayList<BufferedImage> imgs) {
        BufferedImage first = imgs.get(0);
        int h = first.getHeight(null);
        int w = first.getWidth(null);
        
        int rows =1;
        int cols =2;
        int N = rows*cols;
        while(N < imgs.size()) {
            if (rows==cols) {//if equal, expand columns first
                cols++;
            } else {
                rows++;
            }
            N =rows*cols;//update until there's room for every image.
        }
        
        BufferedImage bigPic = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bigPic.createGraphics();
        g2d.setColor(Color.BLACK);
        int maxDim = Math.max(bigPic.getWidth(), bigPic.getHeight());
        g2d.fillRect(0, 0, maxDim, maxDim);
        int k =-1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                k++;
                if (k>=imgs.size()) continue;
                Image simg= imgs.get(k);
                if (simg == null) {
                    continue;
                }

                int hp = row * h;
                int wp = col * w;
                g2d.drawImage(simg, wp, hp, null);
            }
        }
        g2d.dispose();
        return bigPic;
    } 
    
}
