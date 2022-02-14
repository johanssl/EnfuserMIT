package org.fmi.aq.essentials.plotterlib.animation;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author johanssl
 */
public class DatedBuffImage {

    public long systemSecs;
    public String absolutepath;
    final File file;

    public DatedBuffImage(File f) throws IOException {
        String[] temp = f.getName().split("_");
        systemSecs = Long.valueOf(temp[1]);
        this.file = f;
        this.absolutepath = f.getAbsolutePath();
    }
    
    public BufferedImage read() {
        try {
            return ImageIO.read(file);
        } catch (IOException ex) {
            Logger.getLogger(DatedBuffImage.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
         
         
    }

}
