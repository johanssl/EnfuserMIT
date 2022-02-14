/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.fpm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.reader.DefaultLoader;
import org.fmi.aq.enfuser.options.GlobOptions;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;

/**
 *
 * @author johanssl
 */
public class FootprintBank implements Serializable {
    private static final long serialVersionUID = 7526472293822876147L;//Important: change this IF changes are made that breaks serialization!   

    public static FootprintBank load() {
       String fname = GlobOptions.get().getRootDir() +"data"+ Z + DefaultLoader.FPM_FORMS_NAME;
       return load(fname);
    }

    public Footprint standard;
    public Footprint performance;
    public Footprint fastest;
    
    public final static float maxDist = 8000;
    
    public final static float perf = 1f;
    public final static float nonPerf = 0.3f;
    public final static float fast = 0.6f;
    
    public final static float res_m = 2.5f;

    public FootprintBank(boolean analysis) {
        this(analysis, maxDist);
    }
    
    public FootprintBank(boolean analysis, float maxDist) {
        standard = new Footprint(nonPerf, res_m, maxDist, analysis);
        performance = new Footprint(perf, res_m, maxDist, analysis);
        fastest = new Footprint(fast, res_m*2, maxDist, analysis);
    }

    public static void saveToFile(FootprintBank forms, String name) {

        String fileName;
        fileName = name;
        EnfuserLogger.log(Level.FINER,FootprintBank.class,"Saving fpmForm to: " + fileName);
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fileName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(forms);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }

    }

    public static FootprintBank load(String filename) {
        // Read from disk using FileInputStream

        //check availability
        File f = new File(filename);
        if (!f.exists()) {
            FootprintBank forms = new FootprintBank(false);
            saveToFile(forms, filename);
            return forms;
        }

        FileInputStream f_in;
        try {
            f_in = new FileInputStream(filename);

            // Read object using ObjectInputStream
            ObjectInputStream obj_in;

            obj_in = new ObjectInputStream(f_in);

            // Read an object
            Object obj = obj_in.readObject();
            if (obj instanceof FootprintBank) {
                EnfuserLogger.log(Level.FINER,FootprintBank.class,"fpmForms loaded from .dat-file successfully.");
                FootprintBank forms = (FootprintBank) obj;
                f_in.close();

                return forms;
            }

        } catch (Exception ex) {

            EnfuserLogger.log(Level.WARNING,FootprintBank.class,
                  "FastPlumeMath forms " + filename 
                    + " cannot be loaded! (does not exist or version conflict)"
                            + " => create and store newer ones.");
            FootprintBank forms = new FootprintBank(false);
            saveToFile(forms, filename);
            return forms;

        }

        return null;
    }

}
