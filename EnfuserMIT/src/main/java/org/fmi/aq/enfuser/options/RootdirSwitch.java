/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.Zipper;

/**
 *This class can switch the root/launch directory to another specified
 * location. This is the first file that is read by the program as it is
 * responsible for the assessment that where data SHOULD be read from.
 * 
 * In order for this to happen, the rootSwitch.txt file must
 * exist in the launch directory. In case the file in ONE of its lines
 * contains a valid path (that exists) then this is used for the programs
 * root directory.
 * 
 * In case the switch file does not exist then the program will use the default
 * root directory as is. In this case, this class also aims to unpack
 * installerPackage.zip in case it exists and has not been unpacked yet.
 * @author johanssl
 */
public class RootdirSwitch {
 private final static String SWITCH_NAME = "rootSwitch.txt";  
 private final static String INST_PACKAGE_FILE ="installerPackage.zip";
 
  public static String[] getRootDirSwith() {
     File f = new File("");
     String fname = f.getAbsolutePath() +FileOps.Z;
     
     ArrayList<String> arr = readCandidates();
     String root = fname+"";
     if (arr!=null && !arr.isEmpty()) root = arr.get(0);
     
     if (arr==null) {//the rootSwitch.txt does not exist, so the program will
         //launch from here. Check installer package
         File file = new File("");
         String path = file.getAbsolutePath() +FileOps.Z + INST_PACKAGE_FILE;
         File instp = new File(path);
         System.out.println("InstallerPackage exists: "+ instp.exists());
         File data = new File(file.getAbsolutePath()+FileOps.Z +"data"+FileOps.Z);
         System.out.println("Data directory exists: "+ data.exists());
         
         if (instp.exists() && !data.exists()) {
             System.out.println("Unpacking " +INST_PACKAGE_FILE);
             ArrayList<String> unzippedFiles = Zipper.UnZip(instp.getAbsolutePath(),
                     file.getAbsolutePath() +FileOps.Z);
         }
         
         
     }
     return new String[]{fname,root};
  }

/**
 * A custom 'bridge' to some other local directory can be established when
 * the model is loaded. Then the model acts as if the source code was run
 * from this remote location. This is especially handy for development and
 * testing under JDK.
 *
 * In case no custom directory has been set, this method has no effect (uses
 * the directory from which the source was launched). In case the first
 * custom directory has been defined but is not found (does not exist) then
 * the secondary custom directory is used.
 *
 * @return a list of root directory candidates (directories that actually exists)
 */
 private static ArrayList<String> readCandidates() {
     ArrayList<String> arr = new ArrayList<>();
     File f = new File("");
     String fname = f.getAbsolutePath() +FileOps.Z +SWITCH_NAME;
     EnfuserLogger.log(Level.FINER,RootdirSwitch.class,"Attempting to read root directory switch file from "+ fname);
     File test = new File(fname);
     if (test.exists()) {
          EnfuserLogger.log(Level.FINER,RootdirSwitch.class,"\t Exists.");
         
          ArrayList<String> possibleRoots = FileOps.readStringArrayFromFile(test);
                   
                for (String custom : possibleRoots) {

                    String path = custom.replace("\\", FileOps.Z);
                    path = path.replace("/", FileOps.Z);
                    EnfuserLogger.log(Level.FINER,RootdirSwitch.class,"Checking "+path +" (this should be a valid path to an existing directory to function)");

                    test = new File(path);
                    if (test.exists()) {
                        EnfuserLogger.log(Level.FINER,RootdirSwitch.class,"Root Directory pointer was read: " + path);
                        String ROOT = path;
                        arr.add(ROOT);
                    }
                }//for candidates
         
     } else {
         EnfuserLogger.log(Level.FINER,RootdirSwitch.class,"\t does not exist.");
         return null;
     }
     
     return arr;
 } 
  
 
    
}
