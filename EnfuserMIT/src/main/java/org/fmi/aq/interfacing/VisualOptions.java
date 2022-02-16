/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.interfacing;

import java.io.File;
import org.fmi.aq.enfuser.options.GlobOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.Tags;

/**
 *
 * @author johanssl
 */
public class VisualOptions {

   public int vid_interpolationFactor = 6;

   public VisualOptions() {    

   }

    public void setEnfuserDefaults() {

    }

    /**
     * Read pre-defined visualization instructions from a file, using a key
     * for ID matching. Main use-case: mapFUSER visualizations.
     * @param key the key for ID matching
     * @return String recipe for VisualiOpsCreator. Return null if key was not
     * matched.
     */
    public static String getVisualizationInstruction(String key) {
        return null;
    }

    public static VisualOptions getScriptedVisualizationOps(String s, OsmLayer ol) {
       return new VisualOptions();

    }
    
    public static VisualOptions fromString(String s, OsmLayer ol) {
        return new VisualOptions();
    }

}
