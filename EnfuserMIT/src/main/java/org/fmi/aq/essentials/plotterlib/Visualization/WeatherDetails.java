/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;
import org.fmi.aq.enfuser.meteorology.WindConverter;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.essentials.plotterlib.animation.VidCreator;

/**
 *
 * @author johanssl
 */
public class WeatherDetails {

    
    public static String[] SYMB_NAMES = {"Sunny", "C", "CC", "CCC", "CCCC"};
    public static String[] SYMB_NAMES_RAIN = {"R", "RR", "RRR", "RRR", "RRR"};
    
    public BufferedImage symbol;
    public ArrayList<String> text = new ArrayList<>();

    HashMap<Integer,GeoGrid> metGeos;
    HashMap<Integer,String> varDescs = new HashMap<>();
    final VarAssociations VARA;
    final double lat;
    final double lon;
    
    private final int[] myTypes; 
    private final FigureData fd;
    public WeatherDetails(FigureData fd) {
        GlobOptions gl = GlobOptions.get();
        FusionOptions ops = gl.getForFirstRegion();
        VARA = ops.VARA;
        this.fd = fd;
        
        metGeos= new HashMap<>();
        if (fd.metGrids!=null) {
            for (GeoGrid g:fd.metGrids) {
                if (g==null) continue;
                if (g.varName==null) continue;
                int typeI = VARA.getVarInt_all(g.varName);
                metGeos.put(typeI, g); 
            }
        }
        Boundaries b = fd.getBounds();
        lat = b.getMidLat();
        lon = b.getMidLon();
        
        myTypes = new int[]{
            

            VARA.VAR_IMO_LEN,
            VARA.VAR_RAIN,
            VARA.VAR_SKYCOND,
            VARA.VAR_ABLH,
            VARA.VAR_WINDDIR_DEG, 
            VARA.VAR_WINDSPEED,
            VARA.VAR_TEMP,
        };
        
        varDescs.put(VARA.VAR_WINDSPEED, "WindSpeed [m/s]");
        varDescs.put(VARA.VAR_WINDDIR_DEG, "WindDirection");
        varDescs.put(VARA.VAR_ABLH, "BoundaryLayerHeight [m]");
        varDescs.put(VARA.VAR_SKYCOND, "CloudCoverIndex [0,8]");
        varDescs.put(VARA.VAR_RAIN, "Precipitation [mm/h]");
        varDescs.put(VARA.VAR_IMO_LEN, "Inv.MO-length");
    }
    
    private boolean night() {
        boolean night = false;
        try {
            Dtime dt = fd.data.get(fd.currentDataSelection).dt;
            Dtime local = dt.clone();
            local.convertToTimeZone((byte) fd.local_ts);
            if (local.hour >= 23 || local.hour <= 5) {
                night = true;
            }
        } catch (Exception e) {}
        return night;
    }
    
    private boolean dataForSymbolExists() {
        if (this.metGeos.get(VARA.VAR_TEMP)==null) return false;
        if (this.metGeos.get(VARA.VAR_RAIN)==null) return false;
        if (this.metGeos.get(VARA.VAR_SKYCOND)==null) return false;
        
        return true;
    }
    
    private Double getValue(int type) {
        GeoGrid g = this.metGeos.get(type);
        
        if (g == null && (type == VARA.VAR_WINDSPEED || type ==VARA.VAR_WINDDIR_DEG)) {
            
            GeoGrid N = this.metGeos.get(VARA.VAR_WIND_N);
            GeoGrid E = this.metGeos.get(VARA.VAR_WIND_E);
            if (N!=null && E !=null) {
                double n = N.getValue_closest(lat, lon);
                double e = E.getValue_closest(lat, lon);
                float[] dirU = WindConverter.NE_toDirSpeed_from((float)n, (float)e, false);
                 if (type ==  VARA.VAR_WINDDIR_DEG) return (double)dirU[0];
                if (type ==  VARA.VAR_WINDSPEED) return (double)dirU[1];
            }//if NE components exist   
        }//if wind special and g NULL
        
        if (g==null) return null;
        return g.getValue_closest(lat, lon);
    }
    
    private String getLine(int type) {
        Double val = this.getValue(type);
        if (val==null) return null;
        
        String desc = this.varDescs.get(type);
        if (desc==null) desc = VARA.LONG_DESC(type, true);
        
        int rounder =1;
        if (val<0.1) rounder =3;
        if (val<0.001) rounder =4;
        
        if (val> 100) {
           return desc +": "+val.intValue();
        } else {
           return desc +": "+editPrecision(val,rounder);
        }
    }
    
    public void setContent() {
        
         if (dataForSymbolExists()) {
            String folder = fd.ops.visualDataDir() + "symbols" + Z;

            try {
                double temp = getValue(VARA.VAR_TEMP);
                double rain = getValue(VARA.VAR_RAIN);
                double skyc = getValue(VARA.VAR_SKYCOND);

                String symbName = getSymbolName(temp, rain, skyc, night());

                BufferedImage symb = ImageIO.read(new File(folder + symbName + ".png"));
                float scaler = fd.metSymbolScaler();
                symb = VidCreator.resizeImage(symb, (scaler * 0.45)); // weather symbols needs to be slihlt larger
                symbol = symb;

            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (NullPointerException | ArrayIndexOutOfBoundsException exx) {
            }

        }

        //add text, whatever was available
        for (int type:this.myTypes) {
            String line = this.getLine(type);
            if (line!=null) text.add(line);
        }

    }
    
    
    
    private static String getSymbolName(double temp, double rain, double skyc, boolean night) {
        String dayNight = "";
        if (night) {
            dayNight = "night_";
        }
        //special rules
        if (temp > -0.5 && temp < 1.0 && rain > 0.5 && skyc > 5) {
            return "Sleet";// has no night variation
        }

        if (temp < -1 && rain > 0.3) { //rains but freezing temps      
            if (skyc > 5) {
                return "Snow"; // has no night variation

            } else { // skyconditions may be partly cloudy
                return dayNight + "PcloudySnow";
            }

        }

        //no rain rules
        if (rain < 0.1) {

            if (skyc < 1) {
                return dayNight + SYMB_NAMES[0]; // sunny, night variation is "moony"
            } else if (skyc < 2) {
                return dayNight + SYMB_NAMES[1]; //C
            } else if (skyc < 3) {//CC
                return dayNight + SYMB_NAMES[2]; // CC
            } else if (skyc < 6) {
                return dayNight + SYMB_NAMES[3]; // CCC 
            } else {
                return SYMB_NAMES[4]; // CCCC, // has no night variation 
            }

            //rain rules    
        } else {

            if (skyc < 5) {
                return dayNight + "PcloudyRain";
            } else if (rain < 1) {
                return SYMB_NAMES_RAIN[1]; //R,has no night variation

            } else if (rain < 3) {
                return SYMB_NAMES_RAIN[2]; //RR, has no night variation

            } else {
                return SYMB_NAMES_RAIN[3]; //RRR, has no night variation 
            }

        }

    }
}
