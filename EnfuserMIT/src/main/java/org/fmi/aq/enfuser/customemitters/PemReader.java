/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import java.io.File;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class prives static methods to read a collection of point emitters (Pem)
 * and LTM-profiles from a textFile.
 *
 * @author Lasse Johansson
 */


public class PemReader {

    /**
     * Read a collection of Pems from the specified directory. The directory
     * should contain the pointSource list (csv) but also the necessary
     * LTM-profiles.
     *
     * @param ops options
     * @param file
     * @return an ArrayList of created Pem-instances.
     */
    public static ArrayList<Pem> readPemsFromDir(FusionOptions ops, File file) {
        ArrayList<Pem> allPems = new ArrayList<>();
        if (file.getAbsolutePath().contains("pointEmBackup")) return allPems;//should not be reading these
        
        if (file.getName().contains("pointEmBackup")) return allPems;//this directory is not meant to be read.
        
        EnfuserLogger.log(Level.FINER,PemReader.class,"Reading Point Emitters from "+ file.getParent() + Z);
        ArrayList<String> arr = FileOps.readStringArrayFromFile(file);
        int j = 0;
        for (String line : arr) {
            if (j > 0) {
                Pem p = new Pem(line, file,ops);
                if (ops.boundsMAXexpanded().intersectsOrContains(p.lat, p.lon)) {
                   if (p.ltm!=null) allPems.add(p);
                }
            }
            j++;
        }

        return allPems;

    }


}
