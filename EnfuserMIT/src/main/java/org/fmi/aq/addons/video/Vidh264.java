/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.addons.video;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.jcodec.api.SequenceEncoder;
import static org.jcodec.common.Codec.H264;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.fmi.aq.essentials.plotterlib.animation.DatedBuffImage;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.essentials.plotterlib.animation.VidCreator.getDatedImages;

public class Vidh264 {
    
    /**
     * Produce an animation from a collection of PNG-images in sequence.
     * The images must be in the given direcctory and file names, when splitted
     * with '_' should contain a number index for frames.
     * The absolute value of the index does not matter but the order of them
     * does.
     * 
     * @param fullName animation file name (there some replacement to be done)
     * @param deleteImgs if true then the source images will be deleted afterwards.
     */
    public static void producePngAvi(ArrayList<File> files, String fullName,
            boolean deleteImgs, int vid_frames_perS) {

        fullName = fullName.replace(".avi", "_techsm.avi");
        try {
            ArrayList<DatedBuffImage> buffs = getDatedImages(files);

            //SequenceEncoder se = create24Fps(out);
            SequenceEncoder se = new SequenceEncoder(NIOUtils.writableFileChannel(fullName),
                    Rational.R(vid_frames_perS, 1), org.jcodec.common.Format.MOV, H264, null);
            int j = 0;
            for (DatedBuffImage buff : buffs) {
                int complete = (int) (j * 100.0 / buffs.size());
                if (j % 10 == 0) {
                    EnfuserLogger.log(Level.INFO,Vidh264.class,
                            "H264 vid creation complete: " + complete + "%" +"("+fullName+")");
                }
                BufferedImage bimg =buff.read();
                if (bimg!=null) {
                    Picture pic = AWTUtil.fromBufferedImage(bimg, ColorSpace.RGB);
                    se.encodeNativeFrame(pic);
                }
                j++;
            }
            EnfuserLogger.log(Level.FINER,Vidh264.class,"done.");
            se.finish();

            //deleteImages if selected so
            if (deleteImgs) {
                for (DatedBuffImage buff : buffs) {
                    File f = new File(buff.absolutepath);
                    if (f.getName().contains(".png") || f.getName().contains(".PNG")
                            || f.getName().contains(".jpg") || f.getName().contains(".JPG")) {
                        f.delete();
                    }
                }

            }

        } catch (Exception ex) {
            EnfuserLogger.log(ex,Level.WARNING,Vidh264.class,
                    "Avi production failed for "+ fullName);
        }
    }
    
    String fullname;
    int fps;
    SequenceEncoder se;
    int frame =0;
    public Vidh264(String fullName, int fps) {
        this.fullname = fullName.replace(".avi", "_techsm.avi");
        this.fps = fps;
        //SequenceEncoder se = create24Fps(out);
          
    }
    
    public void stream(BufferedImage bimg) throws FileNotFoundException, IOException {
        if (se==null) {
            se = new SequenceEncoder(NIOUtils.writableFileChannel(fullname),
                        Rational.R(fps, 1), org.jcodec.common.Format.MOV, H264, null);
        }
        Picture pic = AWTUtil.fromBufferedImage(bimg, ColorSpace.RGB);
        se.encodeNativeFrame(pic);  
        frame++;
        System.out.println("Streamed new frame, number = "+frame);
    }
    
    public void finish() throws IOException {
        se.finish();
        EnfuserLogger.log(Level.INFO,Vidh264.class,
        "H264 vid creation complete: " +"("+fullname+")");
        se =null;
    }
    
    
    
    /**
     * A test method for animation.
     */
    public static void tester() {
        String fname = "D:/test.avi";

        String dir = "E:\\Dropbox\\Enfuser\\Results\\SensorLoc\\roady25\\";
        ArrayList<BufferedImage> imgs = new ArrayList<>();

        File f = new File(dir);
        File[] ff = f.listFiles();

        for (File file : ff) {
            if (file.getName().contains(".PNG")) {
                try {
                    BufferedImage img = ImageIO.read(file);
                    imgs.add(img);
                    EnfuserLogger.log(Level.FINER,Vidh264.class,"Added " + file.getAbsolutePath());
                } catch (IOException ex) {
                    Logger.getLogger(Vidh264.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }

        try {
            //SequenceEncoder se = create24Fps(out);
            SequenceEncoder se = new SequenceEncoder(NIOUtils.writableFileChannel(fname),
                    Rational.R(6, 1), org.jcodec.common.Format.MOV, H264, null);
            int j = 0;
            for (BufferedImage img : imgs) {
                j++;
                EnfuserLogger.log(Level.FINER,Vidh264.class,"frame " + j);
                Picture pic = AWTUtil.fromBufferedImage(img, ColorSpace.RGB);
                se.encodeNativeFrame(pic);
            }

            se.finish();

        } catch (IOException ex) {
            Logger.getLogger(Vidh264.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
