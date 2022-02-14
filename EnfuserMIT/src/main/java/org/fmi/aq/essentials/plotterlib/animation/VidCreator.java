/**
 *
 */
package org.fmi.aq.essentials.plotterlib.animation;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class VidCreator {

    public static ArrayList<DatedBuffImage> getDatedImages(String dir) {

        ArrayList<DatedBuffImage> arr = new ArrayList<>();

        EnfuserLogger.log(Level.FINER,VidCreator.class,"Checking all image files...");
        File f = new File(dir);
        File[] files = f.listFiles();

        EnfuserLogger.log(Level.FINER,VidCreator.class,files.length + " files inside.");

        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            if (name.contains(".PNG") || name.contains(".png") || name.contains(".JPG")
                    || name.contains(".jpg")) {

                try {

                    arr.add(new DatedBuffImage(files[i]));
                    // EnfuserLogger.log(Level.FINER,this.getClass(),"Added "+name);

                } catch (IOException e) {
                    System.out.print("Image load failed: " + files[i].getAbsolutePath()
                            + files[i].getName());
                }

            }
        }

        Collections.sort(arr, new DBIcomparator());
        return arr;
    }
    
        public static ArrayList<DatedBuffImage> getDatedImages(ArrayList<File> files) {

        ArrayList<DatedBuffImage> arr = new ArrayList<>();
        EnfuserLogger.log(Level.FINER,VidCreator.class,"Checking all image files...");
        EnfuserLogger.log(Level.FINER,VidCreator.class,files.size() + " files inside.");

        for (File f:files) {
            String name = f.getName();
            if (name.contains(".PNG") || name.contains(".png") || name.contains(".JPG")
                    || name.contains(".jpg")) {

                try {
                    arr.add(new DatedBuffImage(f));
                } catch (IOException e) {
                    System.out.print("Image load failed: " + f.getAbsolutePath()
                            + f.getName());
                }

            }
        }

        Collections.sort(arr, new DBIcomparator());
        return arr;
    }

    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        int type = 0;
        type = image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage resizedImage = new BufferedImage(width, height, type);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }

    public static BufferedImage resizeImage(BufferedImage image, double scaler) {
        int type = 0;
        type = image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage resizedImage = new BufferedImage((int) (image.getWidth() * scaler),
                (int) (image.getHeight() * scaler), type);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, (int) (image.getWidth() * scaler), (int) (image.getHeight() * scaler), null);
        g.dispose();
        return resizedImage;
    }
    
    public static class DBIcomparator implements Comparator<DatedBuffImage> {

    @Override
    public int compare(DatedBuffImage a, DatedBuffImage b) {
        return (int) (a.systemSecs - b.systemSecs);
    }
}

}
