/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.Masks;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author johanssl
 */
public class MapPack {

  private final ArrayList<OsmLayer> ols;
  private SatPack sats;
    public MapPack(ArrayList<OsmLayer> ols, boolean loadSats) {
        this.ols = ols;
        Boundaries common = this.getSuperBounds(ols);
        if (common!=null && loadSats) {
            this.sats = SatPack.fromDefaultDir(common,null);
        }
    }
    
     public static MapPack create(OsmLayer ol, boolean loadSats) {
        ArrayList<OsmLayer> ols = new ArrayList<>();
        ols.add(ol);
        return new MapPack(ols,loadSats);
        
     }
     
     public static MapPack satellitesOnly(Boundaries b, String dirAdd) {
        ArrayList<OsmLayer> ols = new ArrayList<>();
        MapPack mp= new MapPack(ols,false);
        mp.sats = SatPack.fromDefaultDir(b,dirAdd);
        return mp;
     }

    public OsmLayer getOsmLayer(double lat, double lon) {
     
        for (OsmLayer ol:ols) {
           if (ol.b.intersectsOrContains(lat, lon)) return ol;
        }   
        return null;
    }
    
    
    private Boundaries getSuperBounds(ArrayList<OsmLayer> ols) {
        if (ols.isEmpty()) return null;
        if (ols.size()==1) return ols.get(0).b.clone();
        
        Boundaries base = ols.get(0).b.clone();
        for (OsmLayer ol:ols) {
            Boundaries b = ol.b;
            if (b.latmin < base.latmin) base.latmin = b.latmin;
            if (b.latmax > base.latmax) base.latmax = b.latmax;
            
            if (b.lonmin < base.lonmin) base.lonmin = b.lonmin;
            if (b.lonmax > base.lonmax) base.lonmax = b.lonmax;
            
        }
        return base;
    }
    
    
    public byte getOSMtype(double lat, double lon) {

        OsmLayer ol = getOsmLayer(lat, lon);
        return OSMget.getSurfaceType(ol, lat, lon);
    }

    public byte[] getOSMtypes(double lat, double lon, byte[] temp) {
        OsmLayer ol = getOsmLayer(lat, lon);
        if (ol==null) return null;
        return OSMget.getOSMtypes(ol, lat, lon, temp);
    }



    public MapPack loadRelevantMaps(String dir, Boundaries b) {

        ArrayList<String> maps = new ArrayList<>();
        File f = new File(dir);
        File[] ff = f.listFiles();
        for (File test : ff) {
            String nam = test.getAbsolutePath();
            if (nam.contains("osmLayer.dat")) {
                boolean add = true;// assume load is OK
                if (b != null) {
                    String fnam = test.getName();
                    String[] temp = fnam.split("_");
                    //upperPks_60.3628_60.5027_24.9548_25.1875_osmLayer.dat
                    double latmin = Double.valueOf(temp[1]);
                    double latmax = Double.valueOf(temp[2]);
                    double lonmin = Double.valueOf(temp[3]);
                    double lonmax = Double.valueOf(temp[4]);
                    Boundaries bt = new Boundaries(latmin, latmax, lonmin, lonmax);

                    if (bt.intersectsOrContains(b) || b.intersectsOrContains(bt)) {
                        add = true;
                        EnfuserLogger.log(Level.FINER,MapPack.class,"\t" + bt.toText_fileFriendly(3) + " is relevant.");
                    } else {
                        add = false;
                    }

                }//if b 

                if (add) {
                    maps.add(nam);//add full path to this file
                }
            }//if name has osmLayer.dat
        }//for files

        ArrayList<OsmLayer> olA = new ArrayList<>();
        for (String mapName : maps) {
            OsmLayer ol = OsmLayer.load(mapName);
            olA.add(ol);
        }

        return new MapPack(olA,true);
    }


    public ArrayList<OsmLayer> layers() {
      return this.ols;
    }
    
    
     public int[] getRGB(double lat, double lon, boolean osmCheck, int[] temp) {
      if (osmCheck) {
        for (OsmLayer ol:ols ) {
            if (!SKIP_OSM_SATELLITE && ol.colMap!=null && ol.colMap.b.intersectsOrContains(lat, lon)) {
                return ol.colMap.getRGB_latlon(lat, lon, temp);
            }
        }   
      }   
      if (sats==null) return temp;   
      return sats.getRGB(lat, lon,temp);
 } 
 
  public static boolean SKIP_OSM_SATELLITE =false;//if true, then HSB, RGB is to be read from the external satellite KMZ's   
  public float[] getHSB(double lat, double lon, float[] temp, Imager im) {
      
      if (ols!=null && !SKIP_OSM_SATELLITE) {
        for (OsmLayer ol:ols) {
            if (ol.colMap!=null && ol.colMap.b.intersectsOrContains(lat, lon)) {
                float[] hsb= ol.colMap.getHSB_latlon(lat, lon, temp, im);
                return hsb;
            }
        }   
      }
     if (sats==null) return temp;
     return sats.getHSB(lat, lon,temp);
 }
  
    /**
     * Fetch an OsmLayer based on the option's modelling area center point.
     * Note: the RunParameters for the options needs to be available for this
     * to work.
     * @param ops the options
     * @return osmLayer. Can be null if none applicable is found.
     */
    public OsmLayer getOsmLayer(FusionOptions ops) {
       return this.getOsmLayer(ops.getRunParameters().bounds().getMidLat(),
               ops.getRunParameters().bounds().getMidLon());
    }

}
