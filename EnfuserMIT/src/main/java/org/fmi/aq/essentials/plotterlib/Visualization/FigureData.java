/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.Visualization;

import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.essentials.plotterlib.animation.VidCreator;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import static org.fmi.aq.essentials.geoGrid.ByteGeoGrid.degreeInM;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLat;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getLon;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.Tags;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualizationCommon.editPrecision_extended;
import static org.fmi.aq.essentials.plotterlib.Visualization.HueList.getHuesAsList;
import static org.fmi.aq.essentials.plotterlib.Visualization.HueList.getHuesAsList_natural;
import org.fmi.aq.essentials.shg.SparseGrid;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.interfacing.Imager;

/**
 *
 * @author Lasse Johansson This class stores all relevant information to produce
 * informative figures from emission grids. Several color Schemes are available
 * (check: final static int fields) Color/value scaling can be manipulated
 * through Options. ALL figures will have a minimum horizontal resolution equal
 * to minW, which means that even low-resolution grids can be neatly drawn. The
 * class is able to produce Google Earth layers with the help of
 * SystemOperations.FileOps.
 */
public class FigureData {

    public int borderSize = 0; // width in pixels for grid Image
    public Color borderCol = Color.BLACK;

    int HEIGHT_ADD_COLBAR = 0;
    int CBAR_HEIGHT = 100;
    int CBAR_DOWN = 0;

    int HEIGHT_ADD_BANNER = 0;
    int BANNER_HEIGHT = 0;

    double scaler = 1.0;

    //fileTypes
    public final static String[] fileTypes = {"PNG", "JPG"};
    public final static int IMG_FILE_PNG = 0;
    public final static int IMG_FILE_JPG = 1;

    //Schemes
    public static final int COLOR_BASIC = 0; //radiant yellow to dark brown
    public static final int COLOR_GREEN_TO_VIOLET = 1;
    public static final int COLOR_TEMPERATURE = 2;
    public static final int COLOR_WHITE_TO_BLUE = 3;
    public static final int COLOR_COMPARISON = 4;
    public static final int COLOR_DEEP_PURPLE = 5;
    public static final int COLOR_BLUE_PURPLE = 6;
    public static final int COLOR_NAT_GREENS = 7;
    public static final int COLOR_PASTEL_GR = 8;
    public static final int COLOR_SHADE = 9;
    public static final int COLOR_FLAME = 10;
    public static final int COLOR_MONOB = 11;
    public static final int COLOR_DIRT_FOREST = 12;
    public static final int COLOR_BLACKTEMP = 13;
    public static final int COLOR_BLACKGR = 14;

    public static final String[] COLOR_SCHEME_NAMES = {
        "LightBrown",
        "GreenToPurple",
        "Temperature",
        "LightBlue",
        "FullPalette",
        "DeepPurple",
        "BluePurple",
        "NaturalGreens",
        "PastelGR",
        "Shade",
        "Flame",
        "MonoBlack",
        "DirtToForest",
        "BTemperature",
        "BGP"
    };
    //old basic 0.17,0.5,1_0.9,0.9,0.3_inv
    
    public static final String[] RECIPEES = {
        "0.17,0.5,1_0.9,0.9,0.3_inv",//basic
        "0.28,1,1_0.8,1,0.6_inv", //GRtoV
        "0.63,1,1_0.95,1,0.8_inv",//TEMP
        "0.47,0.2,1_0.86,1,0.05",//WhB
        "0.6,1,1_0.8,1,0.9_inv",//COMP
        "0.17,1,1_0.83,1,0.25_inv",//DeepPurple
        "0.5,1,1_0.88,1,0.4", //blue purp
        "0.14,0.9,1_0.32,0.9,0.1",//natGreens
        "0.27,0.9,1_0.95,0.9,0.9_inv",//pastel
        "0.0,0.5,0.2_0.15,0.2,1",
        "0.19,1,0_0.88,1,1_inv_flip_flame",
        "0,0,0_1,0,1_trueSB",
        "0.01,0.7,0_0.36,1,1",
        "0.63,1,1_0.95,1,0.8_inv_blackRamp",
        "0.28,1,1_0.8,1,0.6_inv_blackRamp"
    };
    //old WtB "0.51,0.5,1_0.75,1,0.15
    //layers
    public BufferedImage[] layers;
    public final static String[] LAYER_NAMES = {
        "GIS background",
        "Mask background",
        "Shader image (pre)",
        "Grid image",
        "Shader image (post)",
        "Objects",
        "Weather",
        "smallBox"};
    
    public final static int LAYER_BG_GIS = 0;
    public final static int LAYER_BG_MASK = 1;

    public final static int LAYER_SHADER_PRE = 2;
    public final static int LAYER_GRID = 3;
    public final static int LAYER_SHADER_POST = 4;
    public final static int LAYER_OBJECTS = 5;
    public final static int LAYER_WEATHER = 6;
    public final static int LAYER_SMALL_BOX = 7;

    private MapPack maps;
    public boolean prioritizeSat=false;
    
    public BufferedImage colBar;
    public BufferedImage banner;
    public BufferedImage fullImg;

    public static int TRANSPARENCY_OFF = 0;
    public static int TRANSPARENCY_LOW = 1;
    public static int TRANSPARENCY_MED = 2;
    public static int TRANSPARENCY_HIGH = 3;
    public static int TRANSPARENCY_STEAM = 4;

    public static String[] TRANSP_NAMES = {
        "TRANSPARENCY_OFF",
        "TRANSPARENCY_LOW",
        "TRANSPARENCY_MED",
        "TRANSPARENCY_HIGH",
        "TRANSPARENCY_AT_LOW_VALUES"

    };
    //no opacity as default, 0= 100% transparent

    /**
     * This array contains elements with the form (text,lat,lon,additional). The
     * text will be displayed in the figure in the location defined by the
     * coordinates. There's a couple of additional features: colored dots. - In
     * case the text can be parsed into a double value and VisualOptions has a
     * defined "dot size", then a "colored dot" will be drawn instead. - The
     * color will be based on the numeric value, checked from the color range of
     * this FigureData. - In case the 4th element exists (additional) and can be
     * parsed to a double, then the value is used to scale the default radius of
     * the drawn circle.
     */
    public ArrayList<GridDot> dots = new ArrayList<>();
    public ArrayList<float[]> coordinatedLines = new ArrayList<>();

    public VisualOptions ops;
    public int fontSize;

    public Color col_smaller;
    public Color col_larger;
    public Color[] cols;
    private Color[] nonTransp_cols;
    public double[] nonTransp_vals;

    public double[] vals;

    public String type = "type";
    public String aboveScale1 = null;
    public String text_upperCenter = null;

    protected ArrayList<GeoGrid> data;
    public int currentDataSelection = 0;

    public int local_ts = 0;
    public int dataHeight;
    public int dataWidth;
    public int trueH; // low resolution emission grid will not cause the background layer to have a low resoltion. => independent H-W for bg layers!
    public int trueW;

    double colScaling_max;
    double colScaling_min;
    double range;
    double cellArea = 0.0;

    private boolean processed = false;
    Boundaries reg;
    private double northAdjust = 0;
    private double southAdjust = 0;

    ArrayList<GeoGrid> metGrids;
    public ArrayList<Polygon> awtPolys;
    private final Imager im;
    private VisualOptions shaderVops = null;
    private SparseGrid shader_shg = null;
    private boolean shaderLayer = false;
    public Dtime dt_shader;
    public boolean shaderPre = true;
    public final static boolean SHADER_PRE = true;
    public final static boolean SHADER_POST = false;
    
    
    public void setShader(VisualOptions shaderVops, SparseGrid shader_shg, boolean pre) {
        this.shaderVops = shaderVops;
        this.shader_shg = shader_shg;
        if (this.shaderVops != null && this.shader_shg != null) {
            this.shaderLayer = true;
        } else {
            this.shaderLayer = false;
        }

        this.shaderPre = pre;//pre => rendered BEFORE the grid image, POST => rendered on top of grid image (after). The visual effect can be signficantly different!

    }
    
    public static GeoGrid waterMaskingEdit(GeoGrid g, VisualOptions vops, MapPack mapPack) {
          
        if (vops.waterValueScaler_anim==1 || g==null || mapPack ==null) return g;
        EnfuserLogger.log(Level.FINER,FigureData.class,"Visual modification for water surfaces...");
        int H = g.H;
        int W = g.W;

        for (int h =0;h<H;h++) {
            for (int w =0;w<W;w++) {
                double lat = g.getLatitude(h);
                double lon = g.getLongitude(w);
                
                byte surf = mapPack.getOSMtype(lat, lon);
                
                if (OSMget.isWater(surf)) {
                        g.values[h][w]*=vops.waterValueScaler_anim;
                }//is water
            }//for w
        }//for h
       return g;
    }

    public static FigureData getSmallGraphics(GeoGrid grid, VisualOptions vops) {
        VisualOptions newOps = new VisualOptions();
        newOps.metSymbols = false;
        newOps.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR, new int[]{140, 140, 140, 255}); // grey
        newOps.bannerStyle = VisualOptions.BANNER_NONE;
        newOps.colBarStyle = VisualOptions.CBAR_NONE;

        newOps.customColors.put(VisualOptions.I_GIS_BG_SEAS, new int[]{255,255,255,255});
        newOps.colorScheme = vops.colorScheme;
        newOps.transparency = vops.transparency;
        newOps.scaleProgression = vops.scaleProgression;
        newOps.scaleMaxCut = vops.scaleMaxCut;
        newOps.minMax = vops.minMax;

        FigureData fd = new FigureData(grid, newOps);
        return fd;
    }

    public void addSmallBox_pic(BufferedImage img) {
        this.layers[LAYER_SMALL_BOX] = img;
    }

    public void removeSmallBox_pic() {
        this.layers[LAYER_SMALL_BOX] = null;
    }

    public FigureData(float[][] dat, Boundaries reg, VisualOptions ops) {
        this(new GeoGrid(dat, null, reg), ops);
    }

    public FigureData(GeoGrid grid, VisualOptions ops) {
        Boundaries b = grid.gridBounds;
        this.im = new Imager();
        if (b == null) {
            this.reg = Boundaries.getDefault();
            EnfuserLogger.log(Level.FINEST,this.getClass(),
                    "FigureData: warning! no boundaries given!");
            
        } else {
            this.reg = b;
        }

        this.ops = ops;
        this.prioritizeSat = ops.satMaskPriority;
        this.data = new ArrayList<>();
        this.data.add(grid);
        this.currentDataSelection = 0; // first layer which was just added
        this.reg = b;

        //BANNER
        // resolution and margins evaluation      
        this.updateWidthsAndHeights();
        this.loadBanner();

        EnfuserLogger.log(Level.FINEST,this.getClass(),"True dimensions for all"
                    + " images (H/W, without banners and colBars): " + trueH + " / " + trueW);
        

        if (ops.colBarStyle == VisualOptions.CBAR_BASIC) {
            this.HEIGHT_ADD_COLBAR = CBAR_HEIGHT;
        }

        if (ops.bannerStyle == VisualOptions.BANNER_BASIC) {
            this.HEIGHT_ADD_BANNER = BANNER_HEIGHT;
        }

        this.layers = new BufferedImage[LAYER_NAMES.length];
        this.metGrids = new ArrayList<>();

        this.type = reg.name;
        if (this.type == null) {
            this.type = "type";
        }
        
        
        //some newer features, elements from VisualOptions automatically
        if (ops.ol!=null) {
            this.maps = MapPack.create(ops.ol,false);
        }
        if (ops.smallPic!=null) {
          addSmallBox_pic((BufferedImage)ops.smallPic);
        }
        if (ops.text!=null) {
            type= ops.text[TXT_TYPE_IND];
            if (ops.text.length>1)aboveScale1=ops.text[TXT_COLB_IND];
            if (ops.text.length>2)text_upperCenter = ops.text[TXT_HEADER_IND];
        }
        if (ops.metgrids!=null) addMetData(ops.metgrids);
        
    }
    
    public final static int TXT_TYPE_IND =0;
    public final static int TXT_COLB_IND =1;
    public final static int TXT_HEADER_IND =2;

    private void updateWidthsAndHeights() {

        this.dataHeight = this.data.get(this.currentDataSelection).H;
        this.dataWidth = this.data.get(this.currentDataSelection).W;

        this.trueW = Math.max(ops.fd_minimumWidth, this.dataWidth);
        this.trueH = (int) ((float) dataHeight / (float) dataWidth * trueW);

        //scaler variates image and font sizes
        this.scaler = trueW / 1000.0;
        if (this.scaler > trueH / 700.0) {
            this.scaler = trueH / 700.0;
        }

        if (ops.limitSizeScaling) {
            if (scaler > 1.3) {
                scaler = 1.3;
            }
            if (scaler < 0.7) {
                scaler = 0.7;
            }
        }

        fontSize = (int) (scaler * 18 * ops.fontScaler);
        this.CBAR_HEIGHT = (int) (scaler * 110 * ops.cbarScaler);

            EnfuserLogger.log(Level.FINEST,this.getClass(),"FigureData.sizeSetup:"
                    + "scaler/fontSize/cBar-height:" + scaler + " / " + fontSize + " / " + CBAR_HEIGHT);
        

        this.cellArea = this.calcCellArea((reg.latmax + reg.latmin) / 2);

        this.processed = false; // changing resolution should mess-up all layers if they are not processed again accordingly. Settting this false we force re-processing.   

    }

    private static int getTransparency(int transpMode, float colorNumberFraction) {

        if (transpMode == FigureData.TRANSPARENCY_OFF) {
            return 255;
        } else if (transpMode == FigureData.TRANSPARENCY_LOW) {
            return 200;
        } else if (transpMode == FigureData.TRANSPARENCY_MED) {
            return 185;
        } else if (transpMode == FigureData.TRANSPARENCY_HIGH) {
            return 165;

        } else  { //scaling (STEAM) 
            int colIndex_transp = (int) (colorNumberFraction*1600);
            return Math.min(255, colIndex_transp);     // color[10] has 140 which is still  quite transparent.

        } 

    }

    private float[][] getCurrent() {
        return this.data.get(this.currentDataSelection).values;
    }

    public void setCurrent(int i) {
        this.currentDataSelection = i;
        this.updateWidthsAndHeights();
    }

    public void setCurrent(String geoGridName) {

        int index = 0;

        for (int i = 0; i < this.data.size(); i++) {
            if (this.data.get(i).varName.equals(geoGridName)) {
                index = i;
                EnfuserLogger.log(Level.FINEST,this.getClass(),"FigureData: found "
                        + "and selected data layer: " + geoGridName + " DataIndex is now " + index);

                break;
            }
        }

        this.currentDataSelection = index;
        this.updateWidthsAndHeights();

    }

    public boolean hasGeoGrid(String geoGridName) {

        for (int i = 0; i < this.data.size(); i++) {
            if (this.data.get(i).varName.equals(geoGridName)) {

                EnfuserLogger.log(Level.FINEST,this.getClass(),
                        "FigureData: found  data layer: " + geoGridName);
                return true;
            }
        }

        return false;

    }

    public void changeCurrent(String s) {
        for (int i = 0; i < this.data.size(); i++) {
            if (data.get(i).varName.equals(s)) {
                this.currentDataSelection = i;
                this.updateWidthsAndHeights();
                break;
            }
        }
    }

    public void addDataLayer(GeoGrid g) {
        this.data.add(g);
    }

    public Boundaries getBounds() {
        return this.reg;
    }


    public void setTypeString(String s) {
        this.type = s;
    }

    public void setColorPalette(int t) {
        this.ops.colorScheme = t;
    }


    public void addMapPack(MapPack maps) {
        this.maps = maps;
        //null the layer since options may have been changed
        this.layers[FigureData.LAYER_BG_MASK] = null;
    }

    public String[] getGeoGridNames() {

        String[] names = new String[this.data.size()];
        for (int i = 0; i < data.size(); i++) {
            names[i] = data.get(i).varName;
        }
        return names;
    }

    public void setMetDataOnOff(boolean onOff) {

        //check changes and null the layer if found 
        if (this.ops.metSymbols != onOff) {
            this.layers[FigureData.LAYER_WEATHER] = null;
        }

        this.ops.metSymbols = onOff;

    }

    public void addMetData(ArrayList<GeoGrid> grids) {
        this.metGrids = grids;
    }

    public void addMetData(GeoGrid[] grids) {
        this.metGrids = new ArrayList<>();
        for (GeoGrid g : grids) {
            if (g != null) {
                this.metGrids.add(g);
            }
        }

    }

    public void addMetData(GeoGrid grid) {
        if (this.metGrids == null) {
            this.metGrids = new ArrayList<>();
        }
        if (grid != null) {
            this.metGrids.add(grid);
        }
    }

    private BufferedImage createLandScapeLayer_mask() {
        if (this.maps == null) {// nothing to draw? how about satellite
            return null;
        }

        BufferedImage newGridImg = new BufferedImage(this.trueW, this.trueH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newGridImg.createGraphics();

        double dlat = (reg.latmax - reg.latmin) / this.trueH;
        double dlon = (reg.lonmax - reg.lonmin) / this.trueW;
        byte[] osms = new byte[2];
        AwtOptions awt = ops.getAWTops();
        
        for (int h = 0; h < this.trueH; h++) {
            for (int w = 0; w < this.trueW; w++) {

                double lat = reg.latmax - dlat * h;
                double lon = reg.lonmin + dlon * w;

               osms = maps.getOSMtypes(lat, lon, osms);
               byte surf = Tags.LU_N_A;
               byte func = Tags.LU_N_A;
               
                boolean coastal = false;
                if (osms != null) {
                    surf = osms[0];//also buildings and highways
                    func = osms[1];
                    OsmLayer ol = maps.getOsmLayer(lat, lon);
                    if (ol!=null) coastal = OSMget.isCoastal(ol, lat, lon);
                }

                //satellite bg override
                if (this.prioritizeSat) {
                    Color col = this.getSatColor(lat, lon, surf);
                    if (col != null) {
                             g2d.setColor(col);
                             g2d.fillRect(w, h, 1, 1);
                            continue; 
                    }
                }// will not use OSM-based base colors, continue to next pixel.

                boolean set = false;
                //first check coastline 
                if (coastal && awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_COASTLINE) != null) {
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_COASTLINE));
                    set = true;

                } else if (ops.mask_func_cols.get((int) func) != null) {//next try getting a direct color for this exact type from FUNCTIONALS such as airport objects
                    g2d.setColor(awt.mask_func_cols.get((int) func));
                    set = true;

                } else if (ops.mask_surf_cols.get((int) surf) != null) {//next try getting a direct color for this exact type from SURFACE types
                    g2d.setColor(awt.mask_surf_cols.get((int) surf));
                    set = true;
                } else if (OSMget.isWater(surf) && ops.mask_categorical_cols.get(VisualOptions.MASK_CAT_WATER) != null) {//then move on to categories - water
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_WATER));
                    set = true;
                } else if (OSMget.isHighway(surf) && ops.mask_categorical_cols.get(VisualOptions.MASK_CAT_ROADS) != null) {//then move on to categories - roads
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_ROADS));
                    set = true;
                } else if (OSMget.isBuilding(surf) && ops.mask_categorical_cols.get(VisualOptions.MASK_CAT_BUILDS) != null) {//then move on to categories - builds
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_BUILDS));
                    set = true;
                } else if (Tags.isPedestrianWay(surf) && ops.mask_categorical_cols.get(VisualOptions.MASK_CAT_PEDESTR) != null) {//then move on to categories - pedestr
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_PEDESTR));
                    set = true;
                } else if (awt.getCustomColor(VisualOptions.I_MASK_OTHER_COL)!= null) {
                    g2d.setColor(awt.getCustomColor(VisualOptions.I_MASK_OTHER_COL));
                    set = true;
                }

                if (set) {
                    g2d.fillRect(w, h, 1, 1);
                }
            }//for w         
        }// for h

        g2d.dispose();
        EnfuserLogger.log(Level.FINER,this.getClass(),"Landscape layer created.");
        return newGridImg;
    }

    public void setTextAboveColBar(String s, boolean addCellA) {
        this.aboveScale1 = s;
        if (addCellA) {
            String cellA_str = editPrecision_extended(this.cellArea, 3) + " km2";
            if (cellArea < 0.001) {
                cellA_str = editPrecision_extended(this.cellArea, 3) * 1000000 + " m2";
            }
            this.aboveScale1 += " Cell area at center: " + cellA_str;
        }
    }

    public void setTextAboveColBarDefault(String type) {
        String cellA_str = editPrecision_extended(this.cellArea, 3) + " km2";
        this.type = type;
        if (cellArea < 0.001) {
            cellA_str = editPrecision_extended(this.cellArea, 3) * 1000000 + " m2";
        }
        if (type.contains("PL")) {
            this.aboveScale1 = type + ", [km*ton/cell] according to STEAM-model. Cell area at center: " + cellA_str;
        } else if (type.equals("Messages")) {
            this.aboveScale1 = "AIS-messages recieved [amount/cell]. Cell area at center: " + cellA_str;
        } else {
            this.aboveScale1 = this.type + ", Ship emissions [kg/cell] according to STEAM-model. Cell area at center: " + cellA_str;
        }

    }

    public void replaceAllDataWith(GeoGrid g) {
        this.data.clear();
        this.data.add(g);
        this.currentDataSelection = 0;
        this.updateWidthsAndHeights();
        this.processed = false;

    }

    public void replaceAllDataWith(float[][] dat, Boundaries reg) {
        this.data.clear();
        this.data.add(new GeoGrid(dat, null, reg));
        this.currentDataSelection = 0;
        this.updateWidthsAndHeights();
        this.processed = false;

    }

    /**
     * Process all layers, starting with color scale evaluation. If this is the
     * first time this method is used, then all layers, included background
     * layers, will be processed.
     */
    public void processAll() {
        this.process(true, true, true);
    }

    public void processDataLayer() {
        this.process(false, false, true);
    }

    public void processDataAndMet() {
        this.process(false, true, true);
    }

    public void processBG() {
        this.process(true, false, false);
    }

    private void process(boolean processBg, boolean processMet, boolean processData) {

        if (processData) {
            this.setupColsAndVals(false);
            this.layers[LAYER_GRID] = this.setupGridImage();
            if (ops.coastToGrid) {
                this.drawCoast(this.layers[LAYER_GRID]);
            }
            if (this.shaderLayer) {
                BufferedImage sh = this.processShader();
                if (shaderPre) {
                    this.layers[LAYER_SHADER_PRE] = sh;
                    this.layers[LAYER_SHADER_POST] = null;
                } else {
                    this.layers[LAYER_SHADER_PRE] = null;
                    this.layers[LAYER_SHADER_POST] = sh;
                }
            }
        }

        if (processBg) {
            this.layers[LAYER_BG_GIS] = setupBgImage_GIS();
        }
        if (processBg) {
            this.layers[LAYER_BG_MASK] = createLandScapeLayer_mask();
        }

        if (processMet) {

            this.layers[LAYER_WEATHER] = MetSymbolizer.createMetSymbolsLayer(this);
        }

        if (processData) {
            this.setupColBarImage();
            this.fullImg = this.setupFinalImage();
            this.processed = true;
        }
    }

    public BufferedImage getBufferedImage() {
        if (!this.processed) {
            this.processAll(); // process all layers just to be sure
        }
        return this.fullImg;
    }

    public void drawToCanvasScaledByWidth(Canvas canvas, int Width) {
        if (!this.processed) {
            this.processAll();
        }
        canvas.setIgnoreRepaint(false);

        Graphics g = canvas.getGraphics();
        g.clearRect(0, 0, 2000, 2000);

        int height = getCurrent().length;
        int width = getCurrent()[0].length;

        double scaler = (double) Width / (double) width;
        Image img = fullImg.getScaledInstance((int) (width * scaler),
                (int) (height * scaler), Image.SCALE_SMOOTH);
        g.drawImage(img, 0, 0, null);
        g.dispose();

        canvas.setIgnoreRepaint(true);

    }

    public Image getScaledImage(int Width) {
        if (!this.processed) {
            this.processAll();
        }

        int height = getCurrent().length;
        int width = getCurrent()[0].length;

        double scaler = (double) Width / (double) width;
        Image img = fullImg.getScaledInstance((int) (width * scaler),
                (int) (height * scaler), Image.SCALE_SMOOTH);
        return img;
    }

    public static Image getScaledImage(BufferedImage bimg, int nw, int nh) {
        Image img = bimg.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
        return img;
    }

    public void drawToCanvas(Canvas canvas, boolean clear) {
        if (!this.processed) {
            this.processAll();
        }
        canvas.setIgnoreRepaint(false);

        float w_test = (float) canvas.getWidth() / (float) fullImg.getWidth();
        float h_test = (float) canvas.getHeight() / (float) fullImg.getHeight();

        float minFactor = Math.min(w_test, h_test);
        int maximum_w = (int) (fullImg.getWidth() * minFactor);
        Image scaled = getScaledImage(maximum_w);

        Graphics g = canvas.getGraphics();
        if (clear) {
            g.clearRect(0, 0, 2000, 2000);
        }
        g.drawImage(scaled, 0, 0, null);
        g.dispose();

        canvas.setIgnoreRepaint(true);
    }

    public String KMZ_idAdd = null;
    //Produces a Google Earth layer from the full emission figure.

    public void ProduceGEfiles(boolean preserveImage, String outDir) {
        if (!savedToFile) {
            saveImage(outDir);
        }
        String kmzid = this.type + "";
        if (this.KMZ_idAdd != null) {
            kmzid += this.KMZ_idAdd;
        }
        //there is a possibility that the longiude has been shifted. This is we TrueLonmin is called since GE layers must be based on the actual coordinates
        GEProd.produceKML(outDir, this.type, "testi", reg.lonmin, reg.lonmax,
                reg.latmin - southAdjust, reg.latmax + northAdjust, kmzid);
        GEProd.produceKMZ(this.type, outDir, preserveImage);
    }

    public static void saveImage(String dir, BufferedImage img, String name, int figType) {
        String fullName = dir + name + "." + fileTypes[figType];

        try {
            // Save as new values_img
            ImageIO.write(img, fileTypes[figType], new File(fullName));
        } catch (IOException ex) {

        }

    }
    private boolean savedToFile = false;

    public File saveImage(int figType, String outDir) {
        String dir = outDir;
        String name = this.type;
        EnfuserLogger.log(Level.FINE,this.getClass(),"Saving image to file: " + dir + ", " + name);
        File file = saveImage(dir, name, figType);
        savedToFile = true;
        return file;
    }

    public void saveImage(String outDir) {
        String dir = outDir;
        String name = this.type;
        if (ops.imageOutAsRunnable) {
              Runnable runnable = new ImageRunnable(this,outDir,name);
              Thread thread = new Thread(runnable);
              thread.start();
        } else {
        saveImage(dir, name, FigureData.IMG_FILE_PNG);
        }
        savedToFile = true;
    }
    


    public File saveImage(String dir, String name, int figType) {
        if (!this.processed) {
            this.processAll();
        }

        BufferedImage img;
        if (figType == FigureData.IMG_FILE_JPG) {

            // create a blank, RGB, same width and height, and a white background
            img = new BufferedImage(fullImg.getWidth(),
                    fullImg.getHeight(), BufferedImage.TYPE_INT_RGB);
            img.createGraphics().drawImage(fullImg, 0, 0, Color.WHITE, null);

        } else {
            img = this.fullImg;
        }

        String fullName = dir + name + "." + fileTypes[figType];
        try {
            // Save as new values_img
            File f = new File(fullName);
            ImageIO.write(img, fileTypes[figType], f);
            return f;
        } catch (IOException ex) {
            return null;
        }
    }

    public void drawImagetoPane() {
        if (!this.processed) {
            this.processAll();
        }
        new Imager().imageToFrame(fullImg,false);
    }

    public boolean pairedSize = false; //for H264 encoding?
    //This method produces a complete figure with a banner on top and a color scale at the bottom.

    private BufferedImage setupFinalImage() {

        int finalH = trueH + this.HEIGHT_ADD_BANNER + HEIGHT_ADD_COLBAR;

        //for Google earth the corner coordinates needs to be adjusted because a banner and a scale is added to the image
        double naFra = (double) (100.0 * HEIGHT_ADD_BANNER / 100.0 / trueH);
        double saFra = (double) (100.0 * HEIGHT_ADD_COLBAR / 100.0 / trueH);

        this.northAdjust = naFra * (reg.latmax - reg.latmin);//on top of grid
        this.southAdjust = saFra * (reg.latmax - reg.latmin); // scaling and THE scale drives the figure south

        if (pairedSize) {
            if (finalH % 2 == 1) {
                finalH++;//can happen only once
            }
            if (trueW % 2 == 1) {
                trueW++;//can happen only once
            }
        }

        AwtOptions awt = ops.getAWTops();
        //final image
        BufferedImage finalImg = new BufferedImage(
                trueW, finalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = finalImg.createGraphics();
        g2d.setColor(awt.getCustomColor(VisualOptions.I_FIGURE_BASE_COLOR));
        g2d.fillRect(0, 0, trueW, trueH);
        //attach banner

        //actual image is scaled (if needed) and placed
        for (int i = 0; i < layers.length - 1; i++) {
            if (this.layers[i] == null) {
                continue;
            }

            if (ops.fd_minimumWidth > dataWidth && i != FigureData.LAYER_BG_GIS && i != FigureData.LAYER_BG_MASK) { // scaling

                Image scaled = this.layers[i].getScaledInstance(trueW, trueH, Image.SCALE_SMOOTH);
                g2d.drawImage(scaled, 0, this.HEIGHT_ADD_BANNER, null); // just under the banner

            } else {
                // no scaling           
                g2d.drawImage(this.layers[i], 0, this.HEIGHT_ADD_BANNER, null); // just under the banner                                         
            }

        }// for layers

        //miniBox
        if (layers[LAYER_SMALL_BOX] != null) {
            double scaler = 1.0 / ops.miniWindow_sizeScaler;
            if (scaler < 0.05) {
                scaler = 0.05;//at minimum, takes 5% x 5% of the image area.
            }
            if (scaler > 0.4) {
                scaler = 0.4; //at maximum, takes 40% x 40% of the image area.
            }
            double inv = (1.0 - scaler);

            int cornerAdjust = (int) Math.max(0.01 * trueW, 0.01 * trueH); // 1% margin from the upper right corner.

            int w = (int) (trueW * inv - cornerAdjust);
            int h = (int) (this.HEIGHT_ADD_BANNER + cornerAdjust);

            Image scaled = this.layers[LAYER_SMALL_BOX].getScaledInstance(
                    (int) (trueW * scaler), (int) (trueH * scaler), Image.SCALE_SMOOTH);
            g2d.drawImage(scaled, w, h, null); // just under the banner
            // now this box will be placed in the upper right corner.
        }

        g2d.drawImage(this.colBar, 0, finalH - this.CBAR_HEIGHT + CBAR_DOWN, null);
        g2d.drawImage(this.banner, 0, 0, null);
        Color fontc = awt.fontColor();
        Color fontc_inv = awt.getInverseFontColor();
        //TEXT field
        if (this.text_upperCenter != null) {

            g2d.setPaint(fontc);
            int fontSize = (int) (this.fontSize * 1.5f);
            g2d.setFont(new Font(ops.font, ops.fontStyle, fontSize));

            int downAdjust = (int) (0.025f * trueH) + g2d.getFontMetrics().getHeight();

            int halfLen = g2d.getFontMetrics().stringWidth(this.text_upperCenter) / 2; //half of length of the string in pixels               
            if (halfLen > 0.25 * this.trueW) {
                halfLen = (int) (0.25 * this.trueW);
            }

            int y = this.HEIGHT_ADD_BANNER + downAdjust;
            int x = (int) (this.trueW * 0.5f - halfLen); // at center
            GraphicsRotator.setRotatedText(g2d, x, y, 0, this.text_upperCenter);

            if (ops.font3D) {
                g2d.setPaint(fontc_inv);
                GraphicsRotator.setRotatedText(g2d, x + 1, y + 1, 0, this.text_upperCenter); //inverse color with a one pixel offset
                g2d.setPaint(fontc);

            }
            GraphicsRotator.setRotatedText(g2d, x, y, 0, this.text_upperCenter);
        }

        GridDot.drawDots(this,g2d, fontc,fontc_inv);

        //coordinated lines
        if (this.coordinatedLines != null) {
            for (float[] cc : this.coordinatedLines) {
                try {
                    double lat1 = cc[0];
                    double lon1 = cc[1];

                    double lat2 = cc[2];
                    double lon2 = cc[3];

                    int x1 = (int) ((lon1 - reg.lonmin) / (reg.lonmax - reg.lonmin) * this.trueW);
                    int y1 = (int) ((reg.latmax - lat1) / (reg.latmax - reg.latmin) * this.trueH);

                    int x2 = (int) ((lon2 - reg.lonmin) / (reg.lonmax - reg.lonmin) * this.trueW);
                    int y2 = (int) ((reg.latmax - lat2) / (reg.latmax - reg.latmin) * this.trueH);

                    g2d.setPaint(fontc);
                    if (ops.font3D) {
                        g2d.setPaint(fontc_inv);
                        g2d.drawLine(x1, y1 + 1, x2, y2 + 1);
                        g2d.setPaint(fontc);
                    }
                    g2d.drawLine(x1, y1, x2, y2);

                } catch (ArrayIndexOutOfBoundsException e) {
                }

            }
        }

        g2d.dispose();
        return finalImg;

    }

    public static Color[] getColors(String recipee, Canvas canv, int transInt) {
        Graphics g = null;
        if (canv != null) {
            g = canv.getGraphics();
            g.clearRect(0, 0, 1000, 1000);
        }

        String[] split = recipee.split("_");
        int steps = Integer.valueOf(split[0]);

        boolean inverse = recipee.contains("inv");
        boolean natural = recipee.contains("natural");
        boolean flip = recipee.contains("flip");
        boolean trueSB = recipee.contains("trueSB");
        boolean flame = recipee.contains("flame");
        boolean blackRamp = recipee.contains("blackRamp");
        
        String hsb1 = split[1];
        double h1 = Double.valueOf(hsb1.split(",")[0]);
        double s1 = Double.valueOf(hsb1.split(",")[1]);
        double b1 = Double.valueOf(hsb1.split(",")[2]);

        String hsb2 = split[2];
        double h2 = Double.valueOf(hsb2.split(",")[0]);
        double s2 = Double.valueOf(hsb2.split(",")[1]);
        double b2 = Double.valueOf(hsb2.split(",")[2]);

        //check range for natural 
        if (natural) {
            double range = h2 - h1;
            double effectScaler = 0.8f - range;
            if (effectScaler < 0.15) {
                effectScaler = 0.15;
            }

            //in between 0 and 0.5;
            //edits
            s1 -= effectScaler / 5;
            if (s1 < 0.85) {
                s1 = 0.85;
            }

            b2 -= effectScaler;
            if (b2 < 0.3) {
                b2 = 0.4;
            }
            if (b2 > 0.75) {
                b2 = 0.75;
            }
        }

        double stepsize;
        if (inverse) {
            stepsize = (h1 + (1 - h2)) / steps;
        } else {
            stepsize = (h2 - h1) / steps;
        }
        //hakee huen mukaan nyt. Kun sopivat aloitus- ja lopetusarvot löydetään saturaatiolle ja kirkkaudelle niin voin parametreiksi antaa suoraan HSV värit ja lineaarisesti arvioida S ja V.
        ArrayList<Double> hues;
        if (!natural) {
            hues = getHuesAsList(h1, h2, steps, inverse, stepsize);
            //  EnfuserLogger.log(Level.FINER,this.getClass(),"hues:" + hues.size());
        } else {
            hues = getHuesAsList_natural(h1, h2, steps);
            // EnfuserLogger.log(Level.FINER,this.getClass(),"Natural hues:" + hues.size());
        }

        if (flip) {
            ArrayList<Double> fhues = new ArrayList<>();
            for (int i = hues.size() - 1; i >= 0; i--) {
                fhues.add(hues.get(i));
            }
            hues = fhues;
        }
        //testitulos, jotta nähdään että huet kulkevat oikeassa järjestyksessä ja oikeaan paikkaan.

        Color[] cols = new Color[steps];

        int xStrip = 0;
        if (canv != null) {
            xStrip = (int) (canv.getWidth() / steps);
        }

        for (int w = 0; w < hues.size(); w++) {

            double numCol_scaler = (double) w / hues.size();
            double scaler = numCol_scaler * numCol_scaler;
            double br_scaler = scaler;
            if (!natural && (b2 - b1 > 0.8)) {//starts from black tones, and usually it is best to quickly move to brighter tones
                br_scaler = Math.pow(numCol_scaler, 0.45);
            }

            if (trueSB) {//saturation and brightness are exactly based on the number of color, no non-linear visual mods.
                scaler = numCol_scaler;
                br_scaler = scaler;
            }

            double H = hues.get(w);

            double S = (1 - scaler) * s1 + (scaler) * s2;
            double B = (1 - br_scaler) * b1 + (br_scaler) * b2;

            if (blackRamp) {//first colors are significantly reduced in brightness.
                double B2 = 0 + numCol_scaler*8;//at 0.05 this is already 1.0.
                if (B2 >1) B2 =1;
                B = Math.min(B2,B);
            }
            
            if (flame) {//edit SB since it's difficult to get right automatically
                S = 1;
                if (numCol_scaler < 0.0) {
                    B = 0;
                } else {
                    B = (numCol_scaler - 0.0) * 2.5;
                    if (B > 1) {
                        B = 1;
                    }
                }

                double S_toEnd = 1f - numCol_scaler;
                if (S_toEnd < 0.2) {//reduce saturation and go towards pure white
                    S = 1.0 - (0.2 - S_toEnd) * 4;
                    if (S < 0) {
                        S = 0;
                    }
                }
            }

            Color c = Color.getHSBColor((float) H, (float) S, (float) B);
            Color col = new Color(c.getRed(), c.getGreen(), c.getBlue(),
                    getTransparency(transInt, (float) numCol_scaler));
            cols[w] = col;

            if (canv != null) {
                for (int x = 0; x < canv.getHeight(); x++) {
                    g.setColor(col);
                    //g.drawRect(w, x, w + 1, x + 1);
                    g.drawLine(w * xStrip, x, (w + 1) * xStrip, x);

                }
            }
        }

        if (canv != null) {
            g.dispose();
        }
        return cols;
    }

    private static String getRecipeeString(int scheme) {
        if (scheme < 0 || scheme > FigureData.RECIPEES.length - 1) {
            scheme = 0;
        }
        return FigureData.RECIPEES[scheme];
    }

    public static Color[] getCols(int selectedScheme, String recipee, int transInt, int numCols) {
        if (recipee == null || recipee.length() <= 7) {
            recipee = getRecipeeString(selectedScheme);
        }
        Color[] cols = getColors(numCols + "_" + recipee, null, transInt);
        return cols;
    }

  
//This method forms color/value-set based on selectedScheme and transparency options
    private void setupColsAndVals(boolean shader) {
        VisualOptions vops = this.ops;
        if (shader && this.shaderVops != null) {
            vops = this.shaderVops;
        }
        //automatic scaling

        if (vops.minMax == null) {
            float minTemp = Float.MAX_VALUE;
            float maxTemp = Float.MIN_VALUE;

            for (int i = 0; i < this.dataHeight; i++) {
                for (int j = 0; j < this.dataWidth; j++) {
                    if (getCurrent()[i][j] < minTemp) {
                        minTemp = getCurrent()[i][j];
                    }
                    if (getCurrent()[i][j] > maxTemp) {
                        maxTemp = getCurrent()[i][j];
                    }
                }
            }

            this.colScaling_max = maxTemp / vops.scaleMaxCut;
            if (vops.scaleMaxCut != 1.0) {
                    EnfuserLogger.log(Level.FINEST,this.getClass(),
                            "FigureData: automatic scaling with a maxCut: " 
                                    + maxTemp + " / " + vops.scaleMaxCut + " = " + this.colScaling_max);
            }
            this.colScaling_min = minTemp;

        } else {
            this.colScaling_max = vops.minMax[1];
            this.colScaling_min = vops.minMax[0];
        }

        this.range = this.colScaling_max - this.colScaling_min;

        this.cols = getCols(vops.colorScheme, vops.customPaletteRecipee, vops.transparency, vops.numCols); // linear scale progression => double the amount of colors
        this.nonTransp_cols = getCols(vops.colorScheme, vops.customPaletteRecipee, FigureData.TRANSPARENCY_OFF, vops.numCols);
        this.col_larger = cols[cols.length - 1];
        if (vops.lowCut_transparency) {
            this.col_smaller = new Color(255, 255, 255, 0);
        } else {
            this.col_smaller = cols[0];
        }

        this.vals = getVals(cols.length, this.colScaling_max, this.colScaling_min, vops.scaleProgression);
        this.nonTransp_vals = getVals(nonTransp_cols.length, this.colScaling_max, this.colScaling_min, vops.scaleProgression);
        //  this.svals = getSVals(cols.length, this.colScaling_max, this.colScaling_min, ops.scaleProgression);

    }

    private int getColorIndex(double val) {
        //e.g. value = 50.
        if (val >= this.colScaling_max) {
            return this.nonTransp_cols.length - 1;
        } else if (val <= this.colScaling_min) {
            return 0;
        } else {// in between

            double relative = (val - this.colScaling_min) / this.range;
            //e.g. relative = 0.5 in between min 10 and max 90.
            double curver = ops.scaleProgression;//e.g, this is 2.0
            relative = Math.pow(relative, 1.0 / curver);
            //e.g. relative is now 0.25
            int index = (int) (this.nonTransp_cols.length * relative);
            if (index > this.nonTransp_cols.length - 1) {
                index = this.nonTransp_cols.length - 1;
            }
            return index;
        }

    }

    private static double[] getVals(int length, double max, double min, double scaleProgression) {
        double[] vals = new double[length];
        double range = max - min;

        double pow = scaleProgression;
        //VALUES for the scale
        for (int i = 0; i < vals.length; i++) {
            double relIndex = (double) i / (double) (vals.length - 1);
            vals[i] = editPrecision_extended((min + range * Math.pow(relIndex, pow)), 4);
            //EnfuserLogger.log(Level.FINER,this.getClass(),vals[i]);
        }
        return vals;
    }


    private BufferedImage setupBgImage_GIS() {

        if (this.awtPolys== null) {
            return null;
        }

        //resolution check
        double dlat = (reg.latmax - reg.latmin) / this.trueH;
        double resIn_m = dlat * degreeInM;

        if (resIn_m < 100) {
           EnfuserLogger.log(Level.FINER,this.getClass(),
            "Polygon background draw skipped due to low resolution: " + (int) resIn_m + "m");
           return null;
        }

        int[] pixels = new int[this.trueW * this.trueH];
        BufferedImage pixelImage = new BufferedImage(this.trueW, this.trueH, BufferedImage.TYPE_INT_ARGB);
        pixelImage.setRGB(0, 0, this.trueW, this.trueH, pixels, 0, this.trueW);

        //landMasses
        Graphics g = pixelImage.createGraphics();
        PolyDraw.drawToGraphics(g, this.awtPolys, this.trueH, this.trueW,
                this.reg.latmin, this.reg.latmax, this.reg.lonmin, this.reg.lonmax,
                ops);

        g.dispose();

        return pixelImage;
    }

    //This method produces an image describing the emission grid based on color/value-set,maxMin,ops.colorProgression, ops.maxCutOff
    private BufferedImage setupGridImage() {
        int[] pixels = new int[this.dataWidth * this.dataHeight];

        //PIXEL COLOR evaluation
        for (int j = 0; j < this.dataHeight; j++) {
            for (int i = 0; i < this.dataWidth; i++) {

                if (j < borderSize || i < borderSize || (this.dataHeight - j - 1) < borderSize 
                        || (this.dataWidth - i - 1) < borderSize) { //white borders
                    pixels[(j) * this.dataWidth + (i)] = this.borderCol.getRGB();

                } else {
                    float value = (getCurrent()[j][i]);
                    Color col = this.getColor(value);
                    //colIndex has been evaluated at this point
                    pixels[(j) * this.dataWidth + (i)] = col.getRGB();

                }//if not border

            }//width
        }//height

        BufferedImage pixelImage = new BufferedImage(this.dataWidth, this.dataHeight, BufferedImage.TYPE_INT_ARGB);
        pixelImage.setRGB(0, 0, this.dataWidth, this.dataHeight, pixels, 0, this.dataWidth);

        return pixelImage;
    }

    //This method produces an image describing the emission grid based on color/value-set,maxMin,ops.colorProgression, ops.maxCutOff
    private BufferedImage processShader() {
        BufferedImage pixelImage = null;
        //switch the options
        setupColsAndVals(true);
        double dlat = (this.reg.latmax - this.reg.latmin) / this.dataHeight;
        double dlon = (this.reg.lonmax - this.reg.lonmin) / this.dataWidth;

        try {

            // EnfuserLogger.log(Level.FINER,this.getClass(),"fd: setting up grid.");
            int[] pixels = new int[this.dataWidth * this.dataHeight];

            //PIXEL COLOR evaluation
            for (int j = 0; j < this.dataHeight; j++) {
                for (int i = 0; i < this.dataWidth; i++) {

                    if (j < borderSize || i < borderSize || (this.dataHeight - j - 1) < borderSize 
                            || (this.dataWidth - i - 1) < borderSize) { //white borders
                        continue;

                    } else {
                        try {

                            double lat = getLat(this.reg, j, dlat);
                            double lon = getLon(this.reg, i, dlon);
                            float value = this.shader_shg.getCellContent(this.dt_shader, lat, lon)[0];

                            Color col = this.getColor(value);

                            //colIndex has been evaluated at this point
                            int c = col.getRGB();
                            pixels[(j) * this.dataWidth + (i)] = c;
                            
                        } catch (Exception e) {

                        }

                    }//if not border

                }//width
            }//height

            pixelImage = new BufferedImage(this.dataWidth, this.dataHeight, BufferedImage.TYPE_INT_ARGB);
            pixelImage.setRGB(0, 0, this.dataWidth, this.dataHeight, pixels, 0, this.dataWidth);

        } catch (Exception e) {

        }

        setupColsAndVals(false);// reset color definitions to original
        return pixelImage;
    }

    //return grid cell area in km2 for the given latitude
    private double calcCellArea(double latitude) {
        double area;
        final double degreeInKM = 2 * Math.PI * 6371.0 / 360.0;

        double dlat = (reg.latmax - reg.latmin) / this.dataHeight;
        double dlon = (reg.lonmax - reg.lonmin) / this.dataWidth;
        double lonScaler = Math.cos(2 * Math.PI * latitude / 360.0); //middle lat used

        double dlatInKm = dlat * degreeInKM;
        double dlonInKm = dlon * degreeInKM * lonScaler;
        area = dlatInKm * dlonInKm;
        return area;
    }

//this methods fetches fmi_banner.png image which is going to be placed above the grid.
    private void loadBanner() {
        String bannerDir;
        String visdir = ops.visualDataDir();
        
        if (ops.bannerStyle == VisualOptions.BANNER_BASIC) {
            bannerDir = visdir + "fmi_banner.PNG";
            if (ops.hasCustomBanner()) {
              bannerDir = visdir + ops.getBannerImg();  
            }
        } else if (ops.bannerStyle == VisualOptions.BANNER_TRANSP) {
            bannerDir = visdir + ops.getBannerImg();

        } else {
            return;
        }

        try {
            banner = ImageIO.read(new File(bannerDir));
            banner = VidCreator.resizeImage(banner, scaler * 0.7f);

            this.BANNER_HEIGHT = banner.getHeight();
            if (ops.bannerStyle == VisualOptions.BANNER_BASIC) {
                this.HEIGHT_ADD_BANNER = this.BANNER_HEIGHT;
            } else if (ops.bannerStyle == VisualOptions.BANNER_TRANSP) {
                this.HEIGHT_ADD_BANNER = 0;
            }

        } catch (IOException e) {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "CANNOT FIND: " + bannerDir);
        }

    }

// this method produces a scale image (bufferedImage) based on color/value sets
//Information text is alo added to the image
    private void setupColBarImage() {

        this.CBAR_DOWN = 0;
        if (ops.colBarStyle == VisualOptions.CBAR_NONE) {
            return;
        }
        int colValueModulo = 1 + (int) (this.nonTransp_cols.length / 22);//e.g. 44 colors => modulo =3. If CBAR_transp then modulo will be 4
        //main purpose: many colors in the bar require FEWER amount of value texts for visual clarity!
        colValueModulo=(int)Math.round(colValueModulo*ops.scaleValueModcaler);
        
        float cBar_fractionOfHeight = 0.3f;
        AwtOptions awt = ops.getAWTops();
        Color fontc = awt.fontColor();
        Color fontc_inv = awt.getInverseFontColor();
        
    
        
        Color white = new Color(255, 255, 255, 255);
        if (ops.colBarStyle == VisualOptions.CBAR_TRANSP) {
            white = new Color(255, 255, 255, 65);
            colValueModulo += 1;//text will be displayed a bit sparser
            cBar_fractionOfHeight = 0.28f;
        } else if (ops.colBarStyle == VisualOptions.CBAR_BASIC) {//font color must be black
            fontc = Color.BLACK;
            fontc_inv = Color.WHITE;
        }

        int txtRotation = 25; // degrees rotation downwards
        int cBar_shift = (int) (0.1 * CBAR_HEIGHT);
        
        if (vals[vals.length - 1] < 1000) {
            txtRotation = 0;
            cBar_shift = (int) (0.05 * CBAR_HEIGHT);;

            if (ops.colBarStyle == VisualOptions.CBAR_TRANSP) {
                this.CBAR_DOWN = (int) (this.CBAR_HEIGHT * 0.08f); // values are small so they are not rotated - colbar needs LESS space
            }
        }
        
        int wMargin = (int) (0.03f * trueW); // the amount of pixels between horizontal borders 

        this.colBar = new BufferedImage(trueW, CBAR_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[trueW * CBAR_HEIGHT];
        //now its time to add a color scale to bottom - white background first

        for (int i = 1; i < CBAR_HEIGHT; i++) {
            for (int j = 1; j < trueW - 1; j++) {
                pixels[(CBAR_HEIGHT - i) * trueW + j] = white.getRGB();
            }
        }

        int cLower = (int) (CBAR_HEIGHT / 2 - cBar_fractionOfHeight * CBAR_HEIGHT / 2 - cBar_shift);
        int cUpper = (int) (CBAR_HEIGHT / 2 + cBar_fractionOfHeight * CBAR_HEIGHT / 2 - cBar_shift);

        int colB_width = trueW - 2 * wMargin;
        int n_colors = nonTransp_cols.length;
        int oneColWidth = colB_width / n_colors;

        for (int i = cLower; i <= cUpper; i++) { // top WWWCCCWWWWWW  bottom

            int colorIndex;
            int fromLeft;
            for (int j = wMargin; j <= trueW - wMargin; j++) {

                //borders with black
                if (j == wMargin || j == trueW - wMargin || i == cLower || i == cUpper) {
                    pixels[(i) * trueW + j] = Color.LIGHT_GRAY.getRGB();
                    continue;
                } else if (j == wMargin + 1 || j == trueW - wMargin - 1 || i == cLower + 1 || i == cUpper - 1) {
                    pixels[(i) * trueW + j] = Color.GRAY.getRGB();
                    continue;
                }

                fromLeft = j - wMargin;

                colorIndex = (int) (n_colors * ((float) fromLeft / (float) colB_width));

                if (colorIndex > n_colors - 1) {
                    colorIndex = n_colors - 1;
                }
                pixels[(i) * trueW + j] = nonTransp_cols[colorIndex].getRGB(); // colorbar has no transparency effect
            }
        }
        this.colBar.setRGB(0, 0, trueW, CBAR_HEIGHT, pixels, 0, trueW);

        Graphics2D g2d = this.colBar.createGraphics();

        RenderingHints rh = new RenderingHints( // clear fonts!
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHints(rh);

        //TEXT=======================
        g2d.setPaint(fontc);
        g2d.setFont(new Font(ops.font, ops.fontStyle, fontSize));

        //description        
        if (this.aboveScale1 != null) {
            int y_from_bar = cLower - (int) (this.fontSize * 0.25f); //cBar starts from cLower. The text should be slightly above it

            try {
                if (ops.font3D) {
                    g2d.setPaint(fontc_inv);
                    g2d.drawString(this.aboveScale1, wMargin + 1, y_from_bar + 1); //inverse color with a one pixel offset
                    g2d.setPaint(fontc);
                }
                g2d.drawString(this.aboveScale1, wMargin, y_from_bar);

            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
            } //RHEL 7.4 problem with fonts could occur

        }

        for (int i = 0; i < nonTransp_vals.length; i++) { // nonTransp always contais 40 different values, vals[] might contain 80. cBar has always 40 colors.

            double value = nonTransp_vals[i];
            String s = this.getShortStringFromValue(value);
            //precision conversion for clean color bar values

            double temp = (double) (i) / n_colors;
            int x = wMargin + (int) (temp * (trueW - 2 * wMargin)) + (int) (oneColWidth * 0.15f);
            int y = cUpper + (int) (1.02f * fontSize);

            //draw string s
            if (i % colValueModulo == 0) { // second... fourth.... max value

                if (ops.font3D) {
                    g2d.setPaint(fontc_inv);
                    GraphicsRotator.setRotatedText(g2d, x + 1, y + 1, txtRotation, s); //inverse color with a one pixel offset
                    g2d.setPaint(fontc);
                }
                GraphicsRotator.setRotatedText(g2d, x, y, txtRotation, s);
            }
        }//for vals
        g2d.dispose();
    }

    private String getShortStringFromValue(double value) {
        String s = "N/A";
        String pot;
        int prec;

        if (Math.abs(value) >= 100000) {
            prec = 1;

            for (int i = 16; i > 4; i--) {
                double comparison = Math.pow(10, i);
                if (value > comparison) {
                    pot = "E+" + i;
                    double temp = value / comparison;
                    temp = editPrecision_extended(temp, prec);
                    s = temp + pot;
                    return s;
                }
            }

        } else if (Math.abs(value) >= 10000) {
            prec = -2;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
            s = s.replace(".0", "");

        } else if (Math.abs(value) >= 1000) {
            prec = -1;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
            s = s.replace(".0", "");

        } else if (Math.abs(value) >= 10) {
            prec = 0;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
            s = s.replace(".0", "");

        } else if (Math.abs(value) >= 1) {
            prec = 1;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";

        } else {
            prec = 1;
            double temp = editPrecision_extended(value, prec);
            s = temp + "";
        }

        return s;

    }

    public boolean HSBspecial = false;
    public int HSBs_type = 0;
    public final static int HSB_SPECIAL_HUE = 0;
    public final static int HSB_SPECIAL_BR = 1;
    public final static int HSB_SPECIAL_HUE_BR = 2;

    private Color getHSBspecialCol(float value) {
        float S = 1;
        float B = 1;
        float H = 1;

        if (value < 0) {
            value = 0;
        }
        if (value > 1) {
            value = 1;
        }

        if (HSBs_type == HSB_SPECIAL_HUE) {
            H = value;
        } else if (HSBs_type == HSB_SPECIAL_BR) {
            B = value;
            S = 0;
        } else {
            H = value;
            B = value;
        }

        Color c = Color.getHSBColor(H, S, B);
        Color col = new Color(c.getRed(), c.getGreen(), c.getBlue());
        return col;

    }

    public Color getColor(float value) {
        if (this.HSBspecial) {
            return this.getHSBspecialCol(value);
        }

        //first check outliers
        if (value < vals[0]) {
            return col_smaller;
        } else if (value > this.vals[vals.length - 1]) {
            return col_larger;
        }

        int colIndex = -1;
        if (ops.scaleProgression == 1) {//proper color can be calculated
            double temp = this.cols.length * (value - this.vals[0]) / (this.vals[vals.length - 1] - this.vals[0]); //linear scaling

            colIndex = Math.round((float) temp);
            if (colIndex < 0) {
                colIndex = 0;
            }
            if (colIndex > cols.length - 1) {
                colIndex = cols.length - 1;
            }

        } else if (ops.colorSmartFecth) {
            colIndex = this.getColorIndex(value);

        } else {
            // BRUTE FORCE SEARCH=================
            boolean found = false;
            for (int k = 0; k < this.vals.length; k++) {

                if (value <= this.vals[k]) {
                    colIndex = k;
                    found = true;
                    break;
                }
            }
            if (!found) {
                colIndex = this.cols.length - 1;
            }
            //====================================
        }// else if not linear progression
        return this.cols[colIndex];
    }

    private final float[] hsbTemp = new float[3];
    private Color getSatColor(double lat, double lon, byte surf) {    
        if (ops.satMask_mixing) {
            if (OSMget.isWater(surf)) {
                int[] c = ops.customColors.get(VisualOptions.I_SEA_SATMIX);
                if (c!=null) return new Color(c[0],c[1],c[2],c[3]);
                return new Color(22, 40, 88, 255);
            }
        }
        
        Color col = null;
        try {
            float[] hsb = maps.getHSB(lat, lon, hsbTemp, im);
            hsb[1]*=ops.satMask_saturation;
            hsb[2]*=ops.satMask_br;
            
            int pix = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
            int red = ((pix >> 16) & 0xff);
            int green = ((pix >> 8) & 0xff);
            int blue = (pix & 0xff);
            int[] c = {red, green, blue};
            col = new Color(c[0], c[1], c[2], 255);
            return col;
        
        } catch (Exception e) {
        }
        
        return col;
    }

    public Boundaries getVirtualKMZbounds() {
       return new Boundaries(
               reg.latmin - southAdjust,
               reg.latmax + northAdjust,
               reg.lonmin,
               reg.lonmax
       );
    }

    float metSymbolScaler() {
        return (float)(this.scaler*ops.metSymbScaler);
    }

    private void drawCoast(BufferedImage layer) {
        Graphics2D g2d = layer.createGraphics();

        double dlat = (reg.latmax - reg.latmin) / this.trueH;
        double dlon = (reg.lonmax - reg.lonmin) / this.trueW;
        byte[] osms = new byte[2];
        AwtOptions awt = ops.getAWTops();
        
        for (int h = 0; h < this.trueH; h++) {
            for (int w = 0; w < this.trueW; w++) {

                double lat = reg.latmax - dlat * h;
                double lon = reg.lonmin + dlon * w;

               osms = maps.getOSMtypes(lat, lon, osms);
               byte surf = Tags.LU_N_A;

                boolean coastal = false;
                if (osms != null) {
                    surf = osms[0];//also buildings and highways
                    OsmLayer ol = maps.getOsmLayer(lat, lon);
                    if (ol!=null) coastal = OSMget.isCoastal(ol, lat, lon);
                }


                boolean set = false;
                //first check coastline 
                if (coastal && awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_COASTLINE) != null) {
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_COASTLINE));
                    set = true;

                } else if (OSMget.isWater(surf) && ops.mask_categorical_cols.get(VisualOptions.MASK_CAT_WATER) != null) {//then move on to categories - water
                    g2d.setColor(awt.mask_categorical_cols.get(VisualOptions.MASK_CAT_WATER));
                    set = true;
                } 

                if (set) {
                    g2d.fillRect(w, h, 1, 1);
                }
            }//for w         
        }// for h

        g2d.dispose();
    }

}//class end
