/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 * This class is for the definition of ByteGeoEmitter specs. Main use case is
 * for osmLayer emitters (found in OsmLayer as a ArrayList).
 *
 * @author johanssl
 */
public class BGEspecs implements Serializable {
    public final static String CUSTOM_BGE_SPECS = "BGE_SPECS<";
    private static final long serialVersionUID = 7516472194622776447L;//Important: change this IF changes are made that breaks serialization!

    public double zeroThresh = 0.03;//NOT needed anymore.
    public int height_m;
    public String nameID;//useless?
    public String catS;
    public int cellSparser = 1;

    public String olKey;
    private String customString =null;
    private final static String PARSER_H = "height_m";
    private final static String PARSER_CAT = "category";
    public static final String BGE_KEY = "bge_key";

    public String getMapTaskerString() {
        return CUSTOM_BGE_SPECS

                + PARSER_CAT + "=" + this.catS + "&"
                + BGE_KEY + "=" + this.olKey + "&"
                + PARSER_H + "=" + this.height_m + ">";//+"& "
    }

    public static BGEspecs parse(String content) {

        if (content == null) {
            return null;
        }

        ArrayList<String[]> keyValues = new ArrayList<>();
        String[] sp = content.split("&");
        for (String temp : sp) {
            keyValues.add(temp.split("="));
        }

        Integer height = null;
        String cats = null;
        String key = null;

        String nameID = "osm_emitter_" + (int) (Math.random() * 100);
        int cellSparser = 1;

        for (String[] ss : keyValues) {
            if (ss.length < 2) {
                continue;
            }
            String k = ss[0];
            String val = ss[1];

            if (k.contains(PARSER_H)) {
                height = Double.valueOf(val).intValue();

            } else if (k.contains(PARSER_CAT)) {
                cats = val;;
            } else if (k.contains(BGE_KEY)) {
                key = val;
            }

        }

        if (height != null && cats != null) {
            return new BGEspecs(height, nameID, cats, cellSparser, key);
        }
        return null;
    }

    public BGEspecs( int height_m,
            String nameID, String catS, int cellSparser, String olKey) {

      
        this.height_m = height_m;
        this.nameID = nameID;
        this.catS = catS;
        this.cellSparser = cellSparser;
        this.olKey = olKey;
    }

    public String olKey_filesafe() {
        return this.olKey.replace("_", "-");
    }
    
    
        /**
     * Get the File pointing to a local, regional emitter file directory that is
     * the temporary placeholder for recently created emitter files.
     *
     * @param ops FusionOptions for regional dir structures.
     * @return a directory File
     */
    public static File getTemporaryEmitterDir(FusionOptions ops) {
        String dir = ops.getEmitterDir() + "temp/";
        File f = new File(dir);
        return f;
    }

    public final static String PREVENT_EMITTER_LOAD ="PREVENT_EMITTER_LOAD";
    public boolean loadsAsEmitter() {
        if (this.customString==null) return true;
        if (this.customString.contains(PREVENT_EMITTER_LOAD)) return false;
        return true;
    }
    
    public void setLoadAsEmitter(boolean loads) {
        if (!loads) {
            this.customString = PREVENT_EMITTER_LOAD+"";   
        } else {
            this.customString=null;
        }
    }
}
