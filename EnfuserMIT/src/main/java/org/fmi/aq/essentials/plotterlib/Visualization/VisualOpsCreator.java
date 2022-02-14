/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *Build VisualOptions from a String instructions.
 * @author johanssl
 */
public class VisualOpsCreator {

    public final static String MINMAX = "MINMAX<";
    public final static String STYLE = "STYLE<";
    public final static String PROGRESSION = "CURVER<";
    public final static String TRANSPARENCY = "TRANSP<";
    public final static String COLBAR = "CBAR<";
    public final static String BANNER = "BANNER<";
    public final static String BANNER_FILE = "BANNER_FILE<";
    public final static String TXT = "TXT<";
    public final static String GIS = "GIS<";
    public final static String BASE = "BASECOL<";
    public final static String MAXCUT = "MAXCUT<"; 
    public final static String NUMCOL = "NUMCOL<"; 
    public final static String FONT = "FONT<"; 
    public final static String DOTSIZE = "DOTSIZE<"; 
    public final static String FPS = "FPS<";
    public final static String FRAME_INTERPS = "FRAME_INTERPS<"; 
    public final static String FRAME_IOM = "FRAME_IOM<"; 
    public final static String RWS = "REGIONAL_WINDOW_SIZE<"; 
    public final static String WATERMIX = "SAT_WATERMIX"; 
     
             
    /**
     * Display informtion on how to build the String.
     */
    public static void printoutKeys() {
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"VisualOptions.stringBuilder:");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"The following keys are known:");

        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + MINMAX + "double,double>. If not given then this is automatically assessed." );
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + STYLE + "integer>");
        int k =-1;
        for (String style:FigureData.COLOR_SCHEME_NAMES) {
            k++;
            EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t\t"+k+": "+ style);
        }
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + PROGRESSION + "double>, use [0.5,3]. This defines the curviture of the color-value progression.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + TRANSPARENCY + "integer> ");
        k =-1;
        for (String style:FigureData.TRANSP_NAMES) {
            k++;
            EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t\t"+k+": "+ style);
        }
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + COLBAR + "integer>  use [0=none,-1=basic,2=transparent] to define color range visualization style at the bottom.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + BANNER + "integer>  use [0=none,1=basic,2=transparent] to define the added affiliation symbol on top of the figure.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + BANNER_FILE + "localFileName>  use one of the PNG-files at data/visualizationData/symbols/.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + TXT + "a,b,c>  use [variable name, colorbar text, upper center text]. DON'T use ',' in these texts.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + GIS+ "integer>  use [0=default GIS background,1= satellite,2=satellite(bw),3='darknessGIS', 4=simple coastline]. "
                + "\n\t\tNote: the necessary GIS-dataset must be available (may not always be present)");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + BASE + "'white','black' or 'tr'>  sets up the base image color. In most cases this need not to be specified. For Googe Earth layers use 'tr'");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + MAXCUT+ "double>  cut maximum value by this factor. \n\t\tNote: this can be helpful if the data has very high value peaks.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + NUMCOL+ "integer>  Define amount of color gradient steps.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + FONT+ "name,sizeScaler,{B,W},{true,false}>  Customize font settings.\n "
                + "\t\tExamples: <Arial,2.0,W (for white), true (for 3D effect)>");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + DOTSIZE + "integer>  use [0,1,..5] to set measurement dot size.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + FPS + "integer>  animation frames per second");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + RWS + "integer>  scales the regional background mini window size for ENFUSER. Use a value of 0 to 5.");
        EnfuserLogger.log(Level.INFO,VisualOpsCreator.class,"\t" + WATERMIX + ": have this tag included while GIS<2> to colorize water areas in the satellite background.");

    }
    
    public static void main(String[] args) {
        printoutKeys();
    }  

/**
 * Parse the given String instruction and return an instance of VisualOptions.
 * @param s the instruction.
 * @param ol for GIS-background (used in case the GIS-instruction is given)
 * @return visual options.
 */
    public static VisualOptions fromString(String s, OsmLayer ol) {
        VisualOptions ops = new VisualOptions();

        if (s.contains(MINMAX)) {
            String[] mm = getBetween(s, MINMAX, ">").split(",");
            ops.minMax = new double[]{Double.valueOf(mm[0]), Double.valueOf(mm[1])};
        }

        if (s.contains(STYLE)) {
            String sch = getBetween(s, STYLE, ">");
            ops.colorScheme = Integer.valueOf(sch);
        }

        if (s.contains(PROGRESSION)) {
            String prog = getBetween(s, PROGRESSION, ">");
            ops.scaleProgression = Double.valueOf(prog);
        }

        if (s.contains(TRANSPARENCY)) {
            String tr = getBetween(s, TRANSPARENCY, ">");
            ops.transparency = Integer.valueOf(tr);
        }

        if (s.contains(COLBAR)) {
            String cb = getBetween(s, COLBAR, ">");
            ops.colBarStyle = Integer.valueOf(cb);
        }

        if (s.contains(BANNER)) {
            String b = getBetween(s, BANNER, ">");
            ops.bannerStyle = Integer.valueOf(b);
        }
        
        if (s.contains(BANNER_FILE)) {
            String customBan = getBetween(s, BANNER_FILE, ">");
            ops.setBannerImg(customBan);
        }
        
        
        if (s.contains(NUMCOL)) {
            String b = getBetween(s, NUMCOL, ">");
            ops.numCols = Integer.valueOf(b);
        }
        
        if (s.contains(MAXCUT)) {
            String b = getBetween(s, MAXCUT, ">");
            ops.scaleMaxCut = Double.valueOf(b);
        }
        
        if (s.contains(TXT)) {
            String[] txt = getBetween(s, TXT, ">").split(",");
            ops.text=txt;
        }
        
         if (s.contains(FONT)) {
            String[] txt = getBetween(s, FONT, ">").split(",");
            ops.fontSpecs=txt;
        }
        
        if (s.contains(GIS)) {
            ops.ol=ol;
            String g = getBetween(s,GIS,">");
            int gis= Integer.valueOf(g);

            ops.setupDefaultMaskColors(1);
            
            if (gis==0) {//default, no action
                
            } else if (gis==1) {
                ops.satMaskPriority=true;
            } else if (gis==2) {
                ops.satMaskPriority=true;
                ops.satMask_saturation=0;
            } else if (gis==3) {
                ops.mapRendering_THE_DARKNESS(true, true, true);
            } else if (gis==4) {
                ops.mapRendering_COASTAL();
                ops.coastToGrid =true;
            }
        }
        
        if (s.contains(BASE)) {
             String g = getBetween(s,BASE,">").toLowerCase();
             if (g.contains("white")) {
                 ops.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR, new int[]{255, 255, 255, 255});
             } else if (g.contains("black")) {
                 ops.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR, new int[]{0,0,0,255});
             } else if (g.contains("tr")) {
                 ops.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR, new int[]{0, 0, 0, 0});
             }
        }
        
        Integer frameinter = parseIfSpecified(s, FRAME_INTERPS);
         if (frameinter!=null)ops.vid_interpolationFactor = frameinter;
        Integer fps = parseIfSpecified(s, FPS);
         if (fps!=null)ops.vid_frames_perS = fps;
        Integer iom = parseIfSpecified(s, FRAME_IOM);
         if (iom!=null)ops.imgsOutModulo = iom;
       
        Integer rws = parseIfSpecified(s, RWS); //regional window size.
        if (rws!=null) {
           ops.miniWindow_sizeScaler = rws.doubleValue();
           if (ops.miniWindow_sizeScaler>0) {
               ops.placeMiniWindow = true;
           } else {
               ops.placeMiniWindow = false;
           }
        } else {
            ops.miniWindow_sizeScaler =4;
            ops.placeMiniWindow = true;
        }
         
        //handle dots (for measurement data)first
        if (s.contains(DOTSIZE)) {//render small dots for measurements
            Integer doti = Integer.valueOf(getBetween(s,DOTSIZE,">"));
            ops.setValueDotSize(doti);
        } 
        
        if (s.contains(WATERMIX)) {
               ops.satMask_mixing = true;
            } else {
               ops.satMask_mixing = false;
            }

        ops.inheritFontSpecs();
        return ops;
    }
    
    private static Integer parseIfSpecified(String line, String tag) {
        if (line.contains(tag)) {
            try {
            return Integer.valueOf(getBetween(line,tag,">"));
            } catch (NumberFormatException e) {
                EnfuserLogger.log(e, Level.WARNING, VisualOpsCreator.class,
                        "Failed integer parsing: "+ line+" with tag "+ tag);
            }
        }
        return null;
    }

}
