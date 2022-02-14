/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import java.io.File;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.addons.video.Vidh264;
import org.fmi.aq.enfuser.core.DataCore;

/**
 *
 * @author johanssl
 */
public class GraphicsMod  {

    private static boolean IM4J_SET = false;

    public GraphicsMod() {
        
    }
   /**
   * Read a collection of PNG-images from a source directory, then produce 
   * an animation video as instructed according to VisualOptions settings.
   * @param imgFiles a list of files (images)
   * @param fullName full file name for the video to be generated.
   * @param deleteImgs if true, then all frame images will be deleted once the
   * video is ready.
   * @param ops VisualOptions Object, which will be casted to VisualOptions.
   * @param asRunnable if true then the video production will occur in a separate thread.
   */  
    public void produceVideo(ArrayList<File> imgFiles, String fullName,
            boolean deleteImgs, Object ops, boolean asRunnable) {

        if (ops instanceof VisualOptions) {
            VisualOptions vops = (VisualOptions) ops;
            
            if (asRunnable) {
                Runnable runnable = new VideoRun(imgFiles, fullName, deleteImgs, vops);
                Thread thread = new Thread(runnable);
                thread.start();
                
            } else {
                Vidh264.producePngAvi(imgFiles, fullName, deleteImgs, vops.vid_frames_perS);
            }
        } else {
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "GraphicsMod: cannot convert VisualOptions, abort.");
        }

    }

    /**
    * Create PNG map images for the locations of each StationSource held
    * by the DataCore. The image name will be 'ID_secondaryID.PNG'
    * and the location will be data/Regions/'regionName'/locs/.
    * @param core 
    */
    public void stationLocationMapImages(DataCore core) {

    }
    
    
    private class VideoRun implements Runnable {
        ArrayList<File> imgFiles;
        String fullName;
        boolean deleteImgs;
        int vid_frames_perS;
        private VideoRun(ArrayList<File> imgFiles, String fullName,
                boolean deleteImgs, VisualOptions vops) {
            this.imgFiles = imgFiles;
            this.fullName = fullName;
            this.deleteImgs = deleteImgs;
            this.vid_frames_perS = vops.vid_frames_perS;
        }
        
            @Override
            public void run() {
                Vidh264.producePngAvi(imgFiles, fullName, deleteImgs, vid_frames_perS);
            }

    }

  /**
  * Find a JP2000 image file under the given root directory and launch a conversion
  * process into PNG. 
  * @param dir root directory. The JP2000 can also reside in a sub-directory. 
  * @param mustContains String identifiers to distinguish the file that is being sought.
  * Assessed with: file.getName().contains(mustContain). There can be multiple
  * images to be search and each matching one will be converted.
  * @param outDir directory where the converted PNG image will be generated.
  */
    public void searchAndConvertImages_JP2000(String dir,
            String[] mustContains, String outDir) {

    }

   /**
   * Launch a simple interactive visualization tool for a collection of modelling results,
   * generated by FusionCore.Data will be ultimately loaded from an local
   * archive, and for this reason the temporal span and a collection of pollutant species
   * is to be specified.
   * 
   * @param core The FusionCore object. The RunParameters within will
   * determine the area and time span which is needed to load AreFusion instances
   * from archives.
   * @param af ArrayList of AreaFusions. as Object.
   */
    public void LaunchResultsDisplayer(Object core, Object af) {
    }

  /**
  * Launch a Thread for a file download, also supporting a graphic progress bar.
  * @param fname local filename for the download
  * @param outdir directory for file
  * @param totalMegs estimate on file size in megaBytes (if known)
  * @param url url address for file download.
  * @param credentials String[]{user,password}. Can be null if no such credentials
  * are needed.
  * @param certBypass if true, then any SSH missing certificates issues are bypassed
  * for HTTPS connection. 
  * @param retrys 
  */
    public void launchPopupDownloader(String fname, String outdir, float totalMegs,
            String url, String[] credentials, boolean certBypass, int retrys) {

    }

   /**
   * JP2000 image conversion may require external software and this method
   * should make the preparations so that searchAndConvertImages_JP2000()
   * can be used.
   * @param rootdir root directory where modelling system is being launched.
   */
    public void initJP2000externalSoftware(String rootdir) {
    }


   /**
   * Edit current or maximum value of a progress bar.
   * @param jbar JProgressBar as Object.
   * @param value the value to be set
   * @param max if true, then sets max value.
   */
    public void setJbarValue(Object jbar, int value, boolean max) {
       JProgressBar jp = (JProgressBar)jbar;
       if (max) {
           jp.setMaximum(value);
       } else {
           jp.setValue(value);
           
       }
    }

   /**
   * Draw info lines to a frame and show it.
   * @param arr 
   */
    public void textToPanel(ArrayList<String> arr) {
        String output = "";
        for (String line : arr) {
            output += line + "\n";
        }
        JFrame frame = new JFrame();
        JTextArea jTF_full = new JTextArea();
        frame.add(jTF_full);
        jTF_full.setText(output);
        frame.pack();
        frame.setVisible(true);

    }


    public void setJbarString(Object jbar, String txt, boolean stringPainted) {
         JProgressBar jp = (JProgressBar)jbar;
         jp.setString(txt);
         jp.setStringPainted(stringPainted);
    }

    public void launchViewerGUI(File file) {

    }

}