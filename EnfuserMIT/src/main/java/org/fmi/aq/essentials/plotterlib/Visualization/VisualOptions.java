/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import org.fmi.aq.enfuser.options.GlobOptions;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.Tags;

/**
 *
 * @author johanssl
 */
public class VisualOptions {

    public final static String Z = System.getProperty("file.separator");
   
    public final static int I_GIS_BG_SEAS=0;
    public final static int I_GIS_GB_SEA_BORDER=1;
    public final static int I_FIGURE_BASE_COLOR=2;
    public final static int I_MASK_OTHER_COL=3;
    public final static int I_FONT_COLOR=4;
    public final static int I_SEA_SATMIX = 5;

    public static VisualOptions darkSteam(OsmLayer ol, boolean temp, double prog) {
        VisualOptions vops = new VisualOptions();
      vops.colorScheme= FigureData.COLOR_COMPARISON;
      if (temp) vops.colorScheme= FigureData.COLOR_TEMPERATURE;
      vops.transparency=FigureData.TRANSPARENCY_STEAM;
      vops.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR, new int[]{0,0,0,255});
      vops.scaleProgression =prog;
      vops.scaleMaxCut=1;
      vops.numCols =80;
      vops.fontScaler =1.5;
      vops.setFontToColor(new int[]{255,255,255,255});
      vops.font3D=true;
      vops.font ="Arial";
      vops.bannerStyle = VisualOptions.BANNER_NONE;
      vops.colBarStyle = VisualOptions.CBAR_TRANSP;
      vops.mapRendering_THE_DARKNESS(true, true, true);
      vops.ol = ol;
      return vops;
    }
    
    public HashMap<Integer, int[]> customColors = new HashMap<>();
    public int fontStyle = 1;//font.bold

    public HashMap<Integer, int[]> mask_categorical_cols = new HashMap<>();
    public HashMap<Integer, int[]> mask_surf_cols = new HashMap<>();
    public HashMap<Integer, int[]> mask_func_cols = new HashMap<>();
    public Object smallPic=null;
    
    public boolean metSymbols = false;
    public double metSymbolSparsity = 0.4; // of total width and height, e.g. amount of symbols is approximately (1/this)*(1/this)
    public float metSymbScaler =1f;
    
    public boolean placeMiniWindow = false;//instruction to automatically add a gridset for Animation.
    public double miniWindow_sizeScaler = 5;
    public boolean displayDensity = false;

    public boolean lowCut_transparency = false;
    public int transparency = FigureData.TRANSPARENCY_MED;

    public int colorScheme = FigureData.COLOR_BASIC;
    public double scaleProgression = 1.0;
    public double scaleMaxCut = 1.0;

    public boolean blackBG = false;
    public boolean drawLandMasses_GIS = false;
    

    public float satMask_saturation=1f;
     public float satMask_br=1f;
    public boolean satMask_mixing = false; // water from landuse
    public boolean satMaskPriority =false;
    
    public double[] minMax;

    //animation options
    public int vid_interpolationFactor = 6;
    public int imgsOutModulo = 12;
    public int vid_frames_perS = 18;
    public int fd_minimumWidth = 800;

    public final static int CBAR_NONE = 0;
    public final static int CBAR_BASIC = 1;
    public final static int CBAR_TRANSP = 2;
    public int colBarStyle = CBAR_BASIC;
    
    public static String[] CBAR_NAMES = {
    "BANNER_NONE",
    "BASIC",
    "TRANSPARENT"
}; 
    
    public final static int BANNER_NONE = 0;
    public final static int BANNER_BASIC = 1;
    public final static int BANNER_TRANSP = 2;
    public int bannerStyle = BANNER_BASIC;
    
public static String[] BANNER_NAMES = {
    "CBAR_OFF",
    "BASIC",
    "TRANSPARENT"
};
         
    public final static int ENCODING_AVI = 0;
    public final static int ENCODING_H264 = 1;
    public final static int ENCODING_WMV = 2;
    public int vidEncoding = ENCODING_H264;
    public double scaleValueModcaler=1;

    public int numCols = 40;
    public String customPaletteRecipee = null;
    private String customBannerFile = "";

    public final static String BANNER_IMG_JUSTLOGO = "Logo.png";
    public final static String BANNER_IMG_JUSTLOGO_INV = "Logo_noText_inv.png";
    public final static String BANNER_IMG_LOGO_FMI = "Logo_FMI.png";
    public final static String BANNER_IMG_LOGO_FMI_INV = "Logo_FMI.png";

    public final static String BANNER_IMG_ENFUSER = "Logo_ENFUSER.png";
    public final static String BANNER_IMG_STEAM_PUFF = "Logo_STEAMPUFF.png";
    public final static String BANNER_IMG_STEAM = "Logo_STEAM.png";
    
    public boolean anim_multiThread = false;
    public boolean deleteVidImgs = true;//delete temp frame images after vid Creation
    public boolean limitSizeScaling = true;
    public double fontScaler = 1.0;

    public final static String FONT_ARIAL = "Arial";
    public final static String FONT_TIMES_NR = "Times New Roman";
    public final static String FONT_TAHOMA = "Tahoma";
    public final static String FONT_HELVETICA = "Helvetica";
    public final static String FONT_SERIF = "Serif";

    public String font = FONT_SERIF;
    public boolean font3D = false;
    public double cbarScaler = 1.0;
    public boolean colorSmartFecth = true;
    public Double dotSize_percent = 0.013;
    
    //object carried to FigureData========
    public OsmLayer ol=null;//if true, then osmLayer can be inserted to FD automatically during FigureData.createExtended().
    public String[] text=null;
    public ArrayList<GeoGrid> metgrids= null;
    public boolean imageOutAsRunnable=false;
    public String[] fontSpecs = null;//name, scaler, B/W, boolean
    public double waterValueScaler_anim =1.0;
    public boolean slimWindVectorField =false;
    boolean coastToGrid =false;
    
    public void inheritFontSpecs() {
        if (fontSpecs!=null) {
            try {
                String fnam = fontSpecs[0];
                if (fnam!=null && fnam.length()>2)this.font = fnam;
            }catch (Exception e) {}
            
           try {
                double scaler = Double.valueOf(fontSpecs[1]);
                this.fontScaler = scaler;
                
            }catch (Exception e) {} 
            
            try {
                String bw = fontSpecs[2];
                if (bw.contains("B")) {
                  this.customColors.put (I_FONT_COLOR, new int[]{0,0,0,255});
                } else {
                  this.customColors.put (I_FONT_COLOR, new int[]{255,255,255,255});
                }
                
            }catch (Exception e) {}
          
            try {
                String d3 = fontSpecs[3];
                if (d3.contains("true")) {
                    this.font3D=true;
                } else {
                   this.font3D = false;
                }
                
            }catch (Exception e) {}
            
            
        }//if not null
    }
    
   public VisualOptions() {    
    this.customColors.put (I_GIS_BG_SEAS, new int[]{255, 255, 255, 255}); // this is landmass also
    this.customColors.put (I_GIS_GB_SEA_BORDER, new int[]{0,0,0,255}); // if null, no border drawing!
    this.customColors.put (I_FIGURE_BASE_COLOR, new int[]{180, 180, 180, 255}); // this is landmass also
    this.customColors.put (I_FONT_COLOR, new int[]{0,0,0,255});
        
    customBannerFile = BANNER_IMG_LOGO_FMI;//default - logo plus FMI
    setupDefaultMaskColors(0);
   }

    public final static int VALUEDOT_OFF = 0;
    public final static int VALUEDOT_SMALL = 1;
    public final static int VALUEDOT_MEDIUM = 2;
    public final static int VALUEDOT_LARGE = 3;
    public final static int VALUEDOT_LARGEST = 4;
    public final static int VALUEDOT_MEGA = 5;

    public void setValueDotSize(int sizer) {
        this.dotSize_percent =getValueDotSize(sizer); 
    }
    
    public static Double getValueDotSize(int sizer) {
          switch (sizer) {
            case VALUEDOT_SMALL:
                return 0.01;

            case VALUEDOT_MEDIUM:
                return 0.013;

            case VALUEDOT_LARGE:
                return 0.017;

            case VALUEDOT_LARGEST:
                return 0.025;
                
            case VALUEDOT_MEGA:
                return 0.035;    
            default:
               return null;
        }
    }

    public final static int MASK_CAT_COASTLINE = 0;
    public final static int MASK_CAT_ROADS = 1;
    public final static int MASK_CAT_BUILDS = 2;
    public final static int MASK_CAT_WATER = 3;
    public final static int MASK_CAT_PEDESTR = 4;
    public final static int MASK_CAT_SURFS = 5;

    public void setupDefaultMaskColors(int br) {

        clearAllColors();
        addDefaultWaterColors(br);
        addDefaultRoadColors(br);
        addDefaultBuildingColors(br);
        addDefaultPedestrRWColors(br);
        addDefaultSurfaceTypeColors(br);

    }
    
    public AwtOptions getAWTops() {
        return new AwtOptions(this);
    }
 

    public void setupDefaultMaskColors(int brightness_adjust,
            HashMap<Integer, Integer> types) {

        clearAllColors();
        if (types.get(MASK_CAT_WATER) != null) {
            addDefaultWaterColors(types.get(MASK_CAT_WATER));
        }
        if (types.get(MASK_CAT_ROADS) != null) {
            addDefaultRoadColors(types.get(MASK_CAT_ROADS));
        }
        if (types.get(MASK_CAT_BUILDS) != null) {
            addDefaultBuildingColors(types.get(MASK_CAT_BUILDS));
        }
        if (types.get(MASK_CAT_PEDESTR) != null) {
            addDefaultPedestrRWColors(types.get(MASK_CAT_PEDESTR));
        }
        if (types.get(MASK_CAT_SURFS) != null) {
            addDefaultSurfaceTypeColors(types.get(MASK_CAT_SURFS));
        }

    }

    public void mapRendering_THE_DARKNESS(boolean LU, boolean roads, boolean builds) {
        clearAllColors();
        int[] bg= new int[]{22, 22, 22, 255};
        this.customColors.put(I_MASK_OTHER_COL, bg);
        this.customColors.put (I_FIGURE_BASE_COLOR, new int[]{0, 0, 0, 255}); // this is landmass also
        
        this.mask_categorical_cols.put(MASK_CAT_COASTLINE, new int[]{25, 25, 25, 255});//darkish
        this.mask_categorical_cols.put(MASK_CAT_WATER, new int[]{38, 38, 38, 255});//lightest color that is defined


        if (roads) {
            this.mask_categorical_cols.put(MASK_CAT_ROADS, new int[]{3, 3, 3, 255});//quite dark;
        }
        if (builds) {
            this.mask_categorical_cols.put(MASK_CAT_BUILDS, new int[]{0, 0, 0, 255});//very dark
        }
        //LU
        if (LU) {
            this.mask_func_cols.put(Tags.AE_RUNWAY, new int[]{13, 13, 13, 255});//dark gray
            this.mask_func_cols.put(Tags.AE_AERODROME, new int[]{17, 17, 17, 255});//dark gray
            this.mask_surf_cols.put(Tags.NAT_WOODS, new int[]{18, 18, 18, 255});//
            this.mask_surf_cols.put(Tags.NAT_HEATH, new int[]{15, 15, 15, 255});//
            this.mask_surf_cols.put(Tags.NAT_SCRUB, new int[]{19, 19, 19, 255});//
            this.mask_surf_cols.put(Tags.NAT_BEACH, new int[]{20, 20, 20, 255});//
            this.mask_surf_cols.put(Tags.NAT_SAND, new int[]{16, 16, 16, 255});//
            this.mask_surf_cols.put(Tags.NAT_GRASSLAND, new int[]{21, 21, 21, 255});//
        }
    }

    public void clearAllColors() {
        this.mask_categorical_cols = new HashMap<>();
        this.mask_surf_cols = new HashMap<>();
        this.mask_func_cols = new HashMap<>();
    }

    public void addDefaultWaterColors(int br) {
        this.mask_categorical_cols.put(MASK_CAT_COASTLINE, new int[]{60, 60, 60, 255});//darkish
        this.mask_categorical_cols.put(MASK_CAT_WATER, new int[]{220, 220, 220, 255});//lightest color that is defined
    }

    public void addDefaultRoadColors(int br) {
        this.mask_categorical_cols.put(MASK_CAT_ROADS, new int[]{50, 50, 50, 255});//quite dark
    }

    public void addDefaultBuildingColors(int br) {
        this.mask_categorical_cols.put(MASK_CAT_BUILDS, new int[]{20, 20, 20, 255});//very dark
    }

    public void addDefaultPedestrRWColors(int br) {
        this.mask_categorical_cols.put(MASK_CAT_PEDESTR, new int[]{110, 110, 110, 255});//gray
        this.mask_surf_cols.put(Tags.RR_RAILROAD, new int[]{80, 80, 80, 255});//dark gray 
    }

    public void addDefaultSurfaceTypeColors(int br) {
        this.mask_func_cols.put(Tags.AE_RUNWAY, new int[]{90, 90, 90, 255});//dark gray
        this.mask_func_cols.put(Tags.AE_AERODROME, new int[]{110, 110, 110, 255});//dark gray

        this.mask_surf_cols.put(Tags.NAT_WOODS, new int[]{120, 120, 120, 255});//
        this.mask_surf_cols.put(Tags.NAT_HEATH, new int[]{140, 140, 140, 255});//
        this.mask_surf_cols.put(Tags.NAT_SCRUB, new int[]{160, 160, 160, 255});//
        this.mask_surf_cols.put(Tags.NAT_BEACH, new int[]{150, 150, 150, 255});//
        this.mask_surf_cols.put(Tags.NAT_SAND, new int[]{150, 150, 150, 255});//
        this.mask_surf_cols.put(Tags.NAT_GRASSLAND, new int[]{180, 180, 180, 255});//
    }

    public void clearNonWaterColor() {
        HashMap<Integer, int[]> mask_categorical_cols2 = new HashMap<>();
        HashMap<Integer, int[]> mask_surf_cols2 = new HashMap<>();
        this.mask_surf_cols = mask_surf_cols2;
        HashMap<Integer, int[]> mask_func_cols2 = new HashMap<>();
        this.mask_func_cols = mask_func_cols2;

        int[] coast = this.mask_categorical_cols.get(MASK_CAT_COASTLINE);
        int[] water = this.mask_categorical_cols.get(MASK_CAT_WATER);
        mask_categorical_cols2.put(MASK_CAT_COASTLINE, coast);
        mask_categorical_cols2.put(MASK_CAT_WATER, water);
        this.mask_categorical_cols = mask_categorical_cols2;

    }

    public void setBannerImg(String s) {
        this.customBannerFile = s;
    }

    public String getBannerImg() {
        return this.customBannerFile;
    }

    boolean hasCustomBanner() {
      return (this.customBannerFile!=null 
              && this.customBannerFile.length()>3
              && !this.customBannerFile.equals(BANNER_IMG_LOGO_FMI));
    }

    public String visualDataDir() {
       return GlobOptions.get().getRootDir()  
          + "data" + Z + "visualizationData" + Z;
    }

    public void putCustomColor(int I_IND, int[] rgba) {
      this.customColors.put(I_IND, rgba);
    }

    public void setFontToColor(int[] rgb) {
       this.customColors.put(I_FONT_COLOR, rgb);
    }

    void mapRendering_COASTAL() {
         clearAllColors();
        this.customColors.put (I_FIGURE_BASE_COLOR, new int[]{0, 0, 0, 255}); // this is landmass also
        //this.mask_categorical_cols.put(MASK_CAT_COASTLINE, new int[]{100, 100, 100, 90});//darkish
        this.mask_categorical_cols.put(MASK_CAT_WATER, new int[]{60, 60, 60, 40});//lightest color that is defined
    }

    public void setEnfuserDefaults() {
        drawLandMasses_GIS = true;
        setBannerImg(VisualOptions.BANNER_IMG_ENFUSER);
        numCols = 80;

        scaleProgression = 1.5;
        fd_minimumWidth = 1024;
        anim_multiThread = true;
        deleteVidImgs = true;//temp files
        colorSmartFecth = false;
        metSymbols = true;
        bannerStyle = VisualOptions.BANNER_TRANSP;
        colBarStyle = VisualOptions.CBAR_TRANSP;
        font3D = true;

        clearAllColors();
        addDefaultWaterColors(1);//these are alays nice

        transparency = FigureData.TRANSPARENCY_LOW;
        putCustomColor(VisualOptions.I_FONT_COLOR, new int[]{0, 0, 0, 255});
    }
    

}
