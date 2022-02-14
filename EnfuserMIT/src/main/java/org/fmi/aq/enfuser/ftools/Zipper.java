/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.ftools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.fmi.aq.enfuser.logging.EnfuserLogger;

/**
 *A static class for zip and unzip operations.
 * @author johanssl
 */
public class Zipper {
  
    
   public static ArrayList<String> UnZip(String zipFile, String toFolder) {

        byte[] buffer = new byte[1024];
        ArrayList<String> names = new ArrayList<>();
        try {

            //get the zip file content
            ZipInputStream zis
                    = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {

                String fileName = ze.getName();
                boolean isDir = ze.isDirectory();
                if (isDir) {
                    File d = new File(toFolder + fileName + "/");
                    EnfuserLogger.log(Level.FINER,Zipper.class,"Creating dir for zip-files: " + d.getAbsolutePath());
                    if (!d.exists()) {
                        d.mkdir();
                    }
                    ze = zis.getNextEntry();
                    continue;
                }
                File newFile = new File(toFolder + fileName);

                EnfuserLogger.log(Level.FINER,Zipper.class,"file unzip : " + newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    if (fos != null) {
                        fos.close();
                    }
                }
                
                names.add(fileName);
                try {
                    ze = zis.getNextEntry();
                } catch (IllegalArgumentException e) {
                   EnfuserLogger.log(Level.WARNING, Zipper.class,
                           "NextEntry MALFORMED. last addition: "+ fileName);
                   ze = zis.getNextEntry();
                }
                
            }//while ze==null

            zis.closeEntry();
            zis.close();

            EnfuserLogger.log(Level.FINER,Zipper.class,"Unzipping Done.");

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return names;
    }
   
    
   public static void zipLocally(String sourceFile, boolean destroy) {
       int i = sourceFile.lastIndexOf('.');
       String extension = sourceFile.substring(i);

       String resultFile = sourceFile.replace(extension,".zip");
       ArrayList<File> sourceFiles = new ArrayList<>();
       sourceFiles.add(new File(sourceFile));
       zipFiles(sourceFiles, resultFile);
       if (destroy) {
           File f = new File(sourceFile);
          f.delete();
       }
   }
   
   
    public static void zipAndDestroyList(boolean destroy, String folder,
            ArrayList<String> filesToAdd, String zipFileName) {

        long alkuAika = System.currentTimeMillis();

        String currentZip = folder + zipFileName;
        ArrayList<File> files = new ArrayList<>();
        for (String nam : filesToAdd) {
            File f = new File(folder + nam);
            files.add(f);
              EnfuserLogger.log(Level.FINE,Zipper.class,"Zipping " + nam + " to " + currentZip);
            
        }

        zipFiles(files, currentZip);

        //poistetaan halutut tiedostot.
        if (destroy) {
            for (File f : files) {
                try {
                    
                    EnfuserLogger.log(Level.FINE,Zipper.class,"Deleting file: " + f.getAbsolutePath());
                    Files.deleteIfExists(Paths.get(f.getAbsolutePath()));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        long loppu = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,Zipper.class,
                "Time spent zipping: " + (loppu - alkuAika) + " ms, " + zipFileName);

    }

    public static void zipFiles(ArrayList<File> sourceFiles, String zipFilePath) {
        try {

            
            EnfuserLogger.log(Level.FINE,Zipper.class,"ZIPPING TO: " + zipFilePath);
            long startTime = System.currentTimeMillis();
            //create byte buffer
            byte[] buffer = new byte[1024];

            /*
             * To create a zip file, use
             *
             * ZipOutputStream(OutputStream out)
             * constructor of ZipOutputStream class.
             */
            //create object of FileOutputStream
            FileOutputStream fout = new FileOutputStream(zipFilePath);

            //create object of ZipOutputStream from FileOutputStream
            ZipOutputStream zout = new ZipOutputStream(fout);

            for (int i = 0; i < sourceFiles.size(); i++) {

                EnfuserLogger.log(Level.FINER,Zipper.class,"Adding " + sourceFiles.get(i));
                //create object of FileInputStream for source file
                FileInputStream fin = new FileInputStream(sourceFiles.get(i).getAbsolutePath());

                /*
                 * To begin writing ZipEntry in the zip file, use
                 *
                 * void putNextEntry(ZipEntry entry)
                 * method of ZipOutputStream class.
                 *
                 * This method begins writing a new Zip entry to
                 * the zip file and positions the stream to the start
                 * of the entry data.
                 */
                zout.putNextEntry(new ZipEntry(sourceFiles.get(i).getName()));

                /*
                 * After creating entry in the zip file, actually
                 * write the file.
                 */
                int length;

                while ((length = fin.read(buffer)) > 0) {
                    zout.write(buffer, 0, length);
                }

                /*
                 * After writing the file to ZipOutputStream, use
                 *
                 * void closeEntry() method of ZipOutputStream class to
                 * close the current entry and position the stream to
                 * write the next entry.
                 */
                zout.closeEntry();

                //close the InputStream
                fin.close();

            }

            //close the ZipOutputStream
            zout.close();

            long endTime = System.currentTimeMillis();

            EnfuserLogger.log(Level.FINER,Zipper.class,
                    "Zip file has been created! TIME: " + (endTime - startTime) + " milliseconds");

        } catch (IOException ioe) {
            EnfuserLogger.log(ioe,Level.WARNING,Zipper.class,"Unzip during setup FAILED:" + ioe);
        }

    }
    
}
