/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.Masks;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.osmreader.colmap.ColorMap;
import org.fmi.aq.essentials.gispack.osmreader.colmap.IntColorMap;
import org.fmi.aq.essentials.gispack.osmreader.colmap.MonoColorMap;
import org.fmi.aq.interfacing.Imager;

/**
 *This class holds a collection of satellite image datasets.
 * All kmz-files are read from a given directory. In case the file name
 * contains 'BW' tag then the file is dealt as a MonoColorMap instance.
 * @author johanssl
 */
public class SatPack {
    
    private final ArrayList<ColorMap> sats;
    private final Imager im;
    protected SatPack(String dir, Boundaries b) {
        this.im= new Imager();
        sats = new ArrayList<>();
        File f = new File(dir);
        if (!f.exists()) {
            EnfuserLogger.log(Level.INFO, this.getClass(),
                            "No masks to load from: "+ f.getAbsolutePath());
            return;
        }
            
        File[] ff = f.listFiles();
        for (File test:ff) {
            String name = test.getName();
            if (name.endsWith(".kmz")) {
                
                ColorMap m;
                if (name.contains("BW")) {
                    m = MonoColorMap.readFromKmz(dir, name);
                } else {
                   m = IntColorMap.readFromKmz(dir,name);
                }
                
                if (b ==null || b.intersectsOrContains(m.b)) {
                    sats.add(m);
                    EnfuserLogger.log(Level.FINER, this.getClass(),
                            "Loading mask to SatPack: "+ test.getAbsolutePath());
                }
            }
        }
        
        if (sats.size()>1) {//higher resolution (smaller dlat) first => good for fast search
            Collections.sort(sats, new ColorMapSorter());
        }
    }
    
    protected static SatPack fromDefaultDir(Boundaries b, String dirAdd) {
        String dir =GlobOptions.get().getRootDir() 
                + "data" +FileOps.Z +"visualizationData"+ FileOps.Z + "rgbMaps"+FileOps.Z;
        if (dirAdd!=null) dir+=dirAdd +FileOps.Z;
        return new SatPack(dir,b);
    }
  
 public int[] getRGB(double lat, double lon, int[] temp) {
     
     for (ColorMap m:sats) {
         if (m.b.intersectsOrContains(lat, lon)) {
             return m.getRGB_latlon(lat, lon, temp);
         }
     }
     return null;
 } 
 
  public float[] getHSB(double lat, double lon,float[] temp) {
     
     for (ColorMap m:sats) {
         if (m.b.intersectsOrContains(lat, lon)) {
             float[] hsb= m.getHSB_latlon(lat, lon, temp,im);
             return hsb;
         }
     }
     return null;
 }
     
}
