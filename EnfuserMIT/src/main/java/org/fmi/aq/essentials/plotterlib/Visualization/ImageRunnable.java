/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.image.BufferedImage;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import static org.fmi.aq.essentials.plotterlib.Visualization.GEProd.imageToKMZ;

    public class ImageRunnable implements Runnable {
        FigureData fd;
        String out;
        String name;
        
        BufferedImage img;
        boolean kmz;
        Boundaries b;
        boolean preserve;
        public ImageRunnable(FigureData fd, String out, String name) {
            this.fd = fd;
            this.out = out;
            this.name = name;
        }
        
        public ImageRunnable(BufferedImage img, String out, String name,
                Boundaries b, boolean preserve) {
            this.img = img;
            this.out = out;
            this.name = name;
            this.b = b;
            this.preserve = preserve;
        }
        
        @Override
        public void run() {
            
           if (img!=null) {
                try {
                // Save as new values_img
                imageToKMZ(img, out, b, name, preserve);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
           
           } else {
            fd.saveImage(out,name,FigureData.IMG_FILE_PNG);
           }
        }
        
    }
