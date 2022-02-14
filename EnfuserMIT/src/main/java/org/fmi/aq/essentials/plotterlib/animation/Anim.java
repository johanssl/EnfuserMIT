/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.animation;

import org.fmi.aq.enfuser.ftools.Threader;
import org.fmi.aq.interfacing.GraphicsMod;
import org.fmi.aq.essentials.gispack.Masks.MapPack;
import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.essentials.plotterlib.Visualization.AwtOptions;
import org.fmi.aq.essentials.plotterlib.Visualization.GridDot;
import org.fmi.aq.essentials.shg.SparseGrid;

/**
 * This class creates visualizations based on the visualOptions and given data.
 * It makes figures for frames, of which many will be left permanently as
 * additional output even after a video has been created. The frame production
 * uses multi-threading to make this process faster.
 * 
 * After the creation of frames (into figures on file) this class can then
 * call for the creation of video file (H264 codec, requires addon-libary).
 * 
 * During the creation of the animation this class may also interpolate "mid-frames"
 * based on the data. The capabilities of this class has been designed for
 * Enfuser animations. This means that meteorological data and regional
 * AQ concentrations will be visualized with additional elements.
 * @author johanssl
 */
public class Anim {

    public static final String Z = System.getProperty("file.separator");

    public Boundaries bounds;
    public VisualOptions ops;
    public Dtime firstDt;

    public GridInterpolator gint;
    private GeoGrid[][] metgrids;

    public GeoGrid[] miniWindow_interpolated;
    public FigureData miniFd;
    public int secondsBetween;

    public String varName = "varname";
    public FigureData fd;
    private final MapPack mapPack;

    public String aboveColb = "aboveCol ";
    public boolean displayUTC_Z = false;
    public String regID = "";
    public String imgPostID = "";
    public int local_ts;
    public String fullAviName = null;

    private final int cores;//number of processing cores for multiThreading.
    public ArrayList<GridDot>[] coordinatedStrings;//displays text String in selected locations in the area.
    public Dtime forecasted_after = null;//show a text "Forecasted" after this time during the animation.

    public String outDir = "";
    public String tempDir = "";
    ArrayList<File> animFigs = new ArrayList<>();
    public boolean evenNumPixels = false;
    public boolean animOut=true;
    public boolean figsOut=true;

    public Anim(String outDir, float[][][] emsData, Boundaries bounds,
            VisualOptions ops, Dtime firstDt, int secondsBetween, MapPack mapPack,String tempAdd) {
        
        this.mapPack = mapPack;
 
        if (outDir != null) {
            this.outDir = outDir;
            File f = new File(this.outDir);
            if (!f.exists()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "Anim: output directory does not exist yet, creating " + this.outDir);
                f.mkdir();
            }

            this.tempDir = outDir + "temp" +Z + tempAdd+Z;
            f = new File(tempDir);
            if (!f.exists()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "Anim: temp directory does not exist yet, creating " + tempDir);
                f.mkdirs();
            } else {//attempt to clear temp dir
                try {
                    File[] ff = f.listFiles();
                    if (ff != null) {
                        for (File test : ff) {
                            if (test.getName().toLowerCase().contains(".png")
                                    || test.getName().toLowerCase().contains(".jpg")) {
                                test.delete();
                            }
                        }
                    }
                } catch (Exception e) {

                }
            }
        }//if constructor specified outDir, as in MOST cases. (Plotter main is an exception)

        //this.emsData = emsData;  
        this.bounds = bounds;
        this.firstDt = firstDt;
        this.secondsBetween = secondsBetween;
        this.ops = ops;
        if (ops.vidEncoding == VisualOptions.ENCODING_H264) {
            this.evenNumPixels = true;
        }
        cores = Runtime.getRuntime().availableProcessors();

        //visual value mods?
        emsData = this.editSurfaceValues(emsData,bounds,mapPack);
        this.interpolate(emsData);
        EnfuserLogger.log(Level.FINER,this.getClass(),"==========INTERPOLATED MIN-MAX============= check");
        if (this.ops.minMax == null) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Anim: given minMax is null. The range will be evaluated from data.");
            this.ops.minMax = getMinMax(emsData);
        }
    }
    
    
    private float[][][] editSurfaceValues(float[][][] emsData, Boundaries bounds,
            MapPack mapPack) {
        
        if (ops.waterValueScaler_anim==1) return emsData;
        EnfuserLogger.log(Level.FINER,this.getClass(),"Visual modification for water surfaces...");
            
        if (mapPack==null) return emsData;
        int T = emsData.length;
        int H = emsData[0].length;
        int W = emsData[0][0].length;
        AreaNfo in = new AreaNfo(bounds,H,W,Dtime.getSystemDate());
        
        for (int h =0;h<H;h++) {
            for (int w =0;w<W;w++) {
                double lat = in.getLat(h);
                double lon = in.getLon(w);
                
                byte surf = mapPack.getOSMtype(lat, lon);
                
                if (OSMget.isWater(surf)) {
                    for (int t =0;t<T;t++) {
                        emsData[t][h][w]*=ops.waterValueScaler_anim;
                    }

                }//is water
            }//for w
        }//for h
       return emsData;
    }

    private SparseGrid shader_shg;
    private VisualOptions shaderVops;
    private boolean shaderPre = true; //shader layer rendered BEFORE (PRE) the actual grid content or AFTER (on top, POST)

    public void setShader(VisualOptions shaderVops, SparseGrid shader_shg, boolean shaderPre) {
        this.shaderVops = shaderVops;
        this.shader_shg = shader_shg;
        this.shaderPre = shaderPre;
    }

    /**
     * Structure for grids[][] MUST BE [metTypes][time]. The grid will be
     * transposed later on.
     *
     * @param grids the grids (of GeoGrid) to be added to Anim object.
     * @param interpolateThem true for launching an interpolation method to get
     * smoother met-data in the animation.
     */
    public void addMetGrids(GeoGrid[][] grids, boolean interpolateThem) {

        if (!interpolateThem) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Animation: adding metData (no interpolation)...");
            this.metgrids = grids;
            this.transposeMetGrids();
            EnfuserLogger.log(Level.FINER,this.getClass(),"\t done.");
        } else {

            //given data has not been interpolated yet. Process additional layers on the go.
            this.metgrids = new GeoGrid[grids.length][];

            EnfuserLogger.log(Level.FINER,this.getClass(),"Animation interpolating metData...");
            for (int i = 0; i < grids.length; i++) {
                if (grids[i] == null) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t skipping null metGrid.");
                    continue;
                }
                this.metgrids[i] = this.interpolateGeo(grids[i]);
                EnfuserLogger.log(Level.FINER,this.getClass(),i + " done.");
            }

            this.transposeMetGrids();
        }// interpolation

    }

    /**
     * MetGrids are given in the following format: [metTypes][TimeSlices].
     * However, the data is read as [TimeSlices][metTypes]. Thus this matrix
     * will be transposed.
     */
    private void transposeMetGrids() {
        EnfuserLogger.log(Level.FINER,this.getClass(),"Animation: metGrid transpose...");
        int mets = this.metgrids.length;
        int times = 1;
        for (GeoGrid[] gg : this.metgrids) {
            if (gg != null) {
                times = gg.length;
                EnfuserLogger.log(Level.FINER,this.getClass(),"\t new length will be " + times);
                break;
            }
        }

        GeoGrid[][] newg = new GeoGrid[times][mets];
        for (int i = 0; i < this.metgrids.length; i++) {
            GeoGrid[] gg = this.metgrids[i];
            if (gg == null) {
                continue;
            }

            for (int k = 0; k < gg.length; k++) {
                newg[k][i] = gg[k]; // [k][i] = [i][k] => transposed
            }

        }
        this.metgrids = newg;
        EnfuserLogger.log(Level.FINER,this.getClass(),"\t Transpose done.");
    }

    private void interpolate(float[][][] dat) {
        this.gint = new GridInterpolator(dat, ops, secondsBetween, firstDt);
        if (gint.cannotAnim) this.animOut=false;
    }

    public GeoGrid[] interpolateGeo(GeoGrid[] orig) {

        int SCALER = ops.vid_interpolationFactor;

        GeoGrid[] grid = new GeoGrid[orig.length * SCALER - (SCALER - 1)];

        int h = orig[0].H;
        int w = orig[0].W;

        Dtime date = orig[0].dt; // TODO this is the same for all!
        Boundaries b = orig[0].gridBounds;
        b.name = orig[0].varName;

        // float[][][] grid = new float[afs.size()*SCALER    -(SCALER-1)][h][w];  //why -(SCALER-1) : because this is interpolation and not extrapolation. For the last index i there is not going to be i+1, i+2 or i+3
        for (int i = 0; i < orig.length; i++) {
            grid[i * SCALER] = orig[i]; //if i =2 we store grid 8

            if (SCALER > 1 && i + 1 < orig.length) { // interpolation can be done if this is not the LAST grid

                for (float j = 1.0f; j < SCALER; j++) {
                    float firstWeight = 1.0f - j / SCALER; // e.g. 0.75, 0.5 and 0.25
                    float nextWeight = 1.0f - firstWeight;
                    float[][] n = new float[h][w];
                    grid[i * SCALER + (int) j] = new GeoGrid(n, date, b); // if i =2 we create grids 9,10,11

                    for (int H = 0; H < h; H++) {
                        for (int W = 0; W < w; W++) {
                            float firstVal = orig[i].values[H][W];
                            float nextVal = orig[i + 1].values[H][W];

                            if (Math.abs(firstVal - nextVal) < 300) { //Wind direction problem!
                                grid[i * SCALER + (int) j].values[H][W] = firstVal * firstWeight + nextVal * nextWeight; // calculate interpolated value
                            } else {
                                grid[i * SCALER + (int) j].values[H][W] = firstVal;
                            }
                        }
                    }

                }

            }

        }
        EnfuserLogger.log(Level.FINER,this.getClass(),"AreaFusion.getGrids: creating"
                + " interpolated MET layers complete. ListSize now =" + grid.length);
        return grid;

    }

    private double[] getMinMax(float[][][] emsData) {

        ArrayList<double[]> mms = getMM(emsData);
        double[] mm = {Double.MAX_VALUE, Double.MIN_VALUE};
        for (double[] d : mms) {
            if (d[0] < mm[0]) {
                mm[0] = d[0];
            }
            if (d[1] > mm[1]) {
                mm[1] = d[1];
            }
        }
        // EnfuserLogger.log(Level.FINER,this.getClass(),"anim.minMax evaluation: "+ mm[0] + ", "+mm[1]);
        return mm;
    }

    ArrayList<double[]> getMM(float[][][] emsData) {

        ArrayList<double[]> mms = new ArrayList<>();

        for (int z = 0; z < emsData.length; z++) {
            double[] mm = {Double.MAX_VALUE, Double.MIN_VALUE};
            for (int h = 0; h < emsData[0].length; h++) {
                for (int w = 0; w < emsData[0][0].length; w++) {

                    if (emsData[z][h][w] < mm[0]) {
                        mm[0] = emsData[z][h][w];
                    }
                    if (emsData[z][h][w] > mm[1]) {
                        mm[1] = emsData[z][h][w];
                    }

                }
            }
            //  EnfuserLogger.log(Level.FINER,this.getClass(),"anim.minMax evaluation: "+ mm[0] + ", "+mm[1]+ ", layer = "+z);
            mms.add(mm);
        }

        return mms;
    }

    public void createFrames() {
        this.produceAnimation_multiThread();
    }

    /**
     * Creates new FigureData instances that have identical Background layers.
     *
     * @param fds_prev if exists, then Image layers are copied from these
     * instances, saving computational effort. index 0: base FigureData, index
     * 1: miniWindow image, for SILAM e.g. This can be null.
     * @return
     */
    private FigureData[] getBase(FigureData[] fds_prev) {
        FigureData[] fds = new FigureData[2];
        AwtOptions awt = ops.getAWTops();
        GeoGrid g_0 = new GeoGrid(this.gint.getInterpolated(0),
                new Dtime(this.gint.getTimeInterpolated(0)), bounds);//doesn't matter whit slice is used here. First one is the fastest >(low mem) to get though
        FigureData f = new FigureData(g_0, ops);
        if (this.evenNumPixels) {
            f.pairedSize = true;
        }
        FigureData fMini = null;

        f.addMapPack(mapPack);

        if (this.miniWindow_interpolated != null) {
            fMini = FigureData.getSmallGraphics(miniWindow_interpolated[0], ops);
            fMini.addMapPack(mapPack);  //these masks (roads, water, urban) are always nice
            fMini.prioritizeSat=true;
            fMini.text_upperCenter = "Regional BG.";
            fMini.borderSize = 1; // the image will be scaled so that this corresponds to 1 pix more likely.
            fMini.borderCol = awt.fontColor();
            fMini.fontSize *= ops.miniWindow_sizeScaler; //the overall size will be much lower so the font size must be adjusted up.
        }

        
        //process BG
        if (fds_prev == null || fds_prev[0] == null) {
            f.processBG();
        } else {//a chance to save memory!
            f.layers[FigureData.LAYER_BG_GIS] = fds_prev[0].layers[FigureData.LAYER_BG_GIS];
            f.layers[FigureData.LAYER_BG_MASK] = fds_prev[0].layers[FigureData.LAYER_BG_MASK];
        }

        if (this.miniWindow_interpolated != null) {//use miniWindow
            if (fds_prev == null || fds_prev[1] == null) {//does not exist previously
                fMini.processBG();
            } else {
                fMini.layers[FigureData.LAYER_BG_GIS] = fds_prev[1].layers[FigureData.LAYER_BG_GIS];
                fMini.layers[FigureData.LAYER_BG_MASK] = fds_prev[1].layers[FigureData.LAYER_BG_MASK];
            }
        }//if use miniWindow

        fds[0] = f;
        fds[1] = fMini;
        return fds;
    }

    private void produceGE(GeoGrid g_0) {
        String time = this.gint.getTimeInterpolated(0);
        if (g_0 == null) {
            g_0 = new GeoGrid(this.gint.getInterpolated(0), new Dtime(time), bounds);
        }
        // google earth layer======================
        int transparencyStore = ops.transparency;
        int[] bgStore = ops.customColors.get(VisualOptions.I_FIGURE_BASE_COLOR);
        ops.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR, new int[]{255, 255, 255, 0}); // image base color must be fully transparent! //GIS data will not be drawn either, since the datasets are not given.
        ops.transparency = FigureData.TRANSPARENCY_STEAM; // must be a scaling transparency when there's a satellite background!

        FigureData ge = new FigureData(g_0, ops);
        ge.addMapPack(mapPack);
        String fileDateString = new Dtime(time).getStringDate_fileFriendly();
        ge.type = varName + "_" + fileDateString;
        ge.setTextAboveColBar(aboveColb, false);
        ge.text_upperCenter = time;
        if (this.displayUTC_Z) {
            ge.text_upperCenter = time + "Z";
        }
        ge.processAll();
        ge.saveImage(FigureData.IMG_FILE_PNG, this.outDir);
        ge.ProduceGEfiles(false, this.outDir);//the actual image is not preserved separately 

        ops.transparency = transparencyStore; // roll-back
        ops.customColors.put(VisualOptions.I_FIGURE_BASE_COLOR,bgStore);
        //GE layer==================================== 
    }


    public class FdCallable implements Callable<ArrayList<File>> {

        ArrayList<Integer> is;
        FigureData fdc, mini;
        Anim anim;

        public FdCallable(ArrayList<Integer> is, FigureData fd, FigureData fMini, Anim anim) {
            this.is = is;
            this.fdc = fd;
            this.mini = fMini;
            this.anim = anim;
        }

        @Override
        public ArrayList<File> call() throws Exception {
            ArrayList<File> processed = new ArrayList<>();
            for (int i : is) {

                try {

                    //this method can be used for image production even if animation is not desired
                    int i_nonInter = i / anim.ops.vid_interpolationFactor;
                    if (i % anim.ops.vid_interpolationFactor != 0 && !anim.animOut) {
                        continue; //without video the interpolated middle frames are useless
                    }
                    if (i % 10 == 0) {
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "prod.Anim: frame production " + i + " of" + anim.gint.interpLength_T);
                    }

                    String time = anim.gint.getTimeInterpolated(i);
                    GeoGrid g = new GeoGrid(anim.gint.getInterpolated(i), new Dtime(time), anim.bounds);

                    fdc.replaceAllDataWith(g);
                    //check shader
                    if (anim.shader_shg != null && anim.shaderVops != null) {

                        fdc.setShader(anim.shaderVops, anim.shader_shg, anim.shaderPre);
                        fdc.dt_shader = new Dtime(time);

                    }

                    if (anim.miniWindow_interpolated != null) {
                        int miniWin_index;
                        float f = (float) i / (anim.gint.interpLength_T - 1f) * (anim.miniWindow_interpolated.length - 1f);
                        miniWin_index = (int) f;
                        if (miniWin_index > anim.miniWindow_interpolated.length - 1) {
                            miniWin_index = anim.miniWindow_interpolated.length - 1;
                        }
                        mini.replaceAllDataWith(anim.miniWindow_interpolated[miniWin_index]);
                        mini.processDataAndMet();
                    }

                    try {
                        fdc.dots = anim.coordinatedStrings[i_nonInter];
                    } catch (Exception e) {//can be null or could go OOB

                    }

                    fdc.setTextAboveColBar(anim.aboveColb, false);
                    fdc.text_upperCenter = time;
                    if (anim.displayUTC_Z) {
                        fdc.text_upperCenter = time + "Z";
                    }

                    fdc.local_ts = anim.local_ts;
                    if (anim.local_ts != 0) {
                        try {
                            Dtime dtl = new Dtime(time);
                            dtl.convertToTimeZone((byte) anim.local_ts);
                            fdc.text_upperCenter += " (local: " + getDaySpecs(dtl) + ")";
                        } catch (Exception e) {

                        }
                    }

                    //fetch interpolated metData
                    ArrayList<GeoGrid> nullGrids = null;
                    fdc.addMetData(nullGrids); // reset

                    if (anim.metgrids != null && anim.metgrids[i] != null) {
                        fdc.addMetData(anim.metgrids[i]);
                    }

                    if (anim.miniWindow_interpolated != null) {
                        fdc.addSmallBox_pic(mini.getBufferedImage());// here we finally merge these two products!
                    }
                    fdc.processDataAndMet();// true=> only the latest datalayer will be processed

                    if (anim.animOut) {
                       File saved=  fdc.saveImage(anim.tempDir,
                                anim.varName + "_" + (new Dtime(time)).systemSeconds() + "_" + i,
                                FigureData.IMG_FILE_PNG);
                       if (saved!=null) {
                           processed.add(saved);
                       }
                    }
                    // these went to temp directory

                    //full hour images can be flushed also directly to results directory
                    if (i % anim.ops.imgsOutModulo == 0 && anim.figsOut) { // these go to results folder and are not involved in the animation
                        fdc.saveImage(anim.outDir,
                                anim.regID + anim.varName + "_" + (new Dtime(time)).systemSeconds() + "_" + i + anim.imgPostID, //e.g. for heroku portal images.
                                FigureData.IMG_FILE_PNG);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }//for i

            return processed;
        }

    }//END CombValCallable


    public void produceAnimation_multiThread() {

        if (!this.animOut && !this.figsOut) {
            return; // well, not much point doing anything 
        }

        produceGE(null);
        ArrayList<FutureTask> futs = new ArrayList<>();
        Threader.reviveExecutor();

        int CLUSTER = (int) (gint.interpLength_T / cores);
        EnfuserLogger.log(Level.INFO,this.getClass(),"Anim.multithread: total "
                + "frames and frames per thread: " + gint.interpLength_T + " / " + CLUSTER);

        //just a couple of frames? CLUSTER cannot be zero or we get an endless loop!
        CLUSTER = Math.max(1, CLUSTER);

        FigureData[] fds = this.getBase(null);
        for (int i = 0; i < gint.interpLength_T; i += CLUSTER) {//every slice will be included but OOB must be handled.

            ArrayList<Integer> is = new ArrayList<>();
            FigureData[] fds_this;
            if (i == 0) {
                fds_this = fds; //can use the initial
            } else {
                fds_this = this.getBase(fds); //create new instances but copy background buffered images to save memory.  
            }

            for (int k = 0; k < CLUSTER; k++) {// slices to be processed in this thread
                int curr_i = i + k;
                if (curr_i >= gint.interpLength_T) {
                    continue;//OOB
                }
                is.add(curr_i);
            }
            if (is.isEmpty()) {
                continue;// could happen in the last iteration.
            }
            FdCallable cal = new FdCallable(is, fds_this[0], fds_this[1], this);
            FutureTask<ArrayList<File>> fut = new FutureTask<>(cal);
            Threader.executor.execute(fut);
            futs.add(fut);

        }//for slices       

        // MANAGE callables           
        boolean notReady = true;
        int K = 0;
        while (notReady) {//
            K++;
            notReady = false; // assume that ready 
            double ready_N = 0;
            for (FutureTask fut : futs) {
                if (!fut.isDone()) {
                    notReady = true;
                } else {
                    ready_N++;
                }
            }
            int prog = (int) (100 * ready_N / futs.size());
            if (K % 10 == 0) {
                EnfuserLogger.log(Level.FINE,this.getClass(),prog + "% of threads are ready.");
            }
            
            EnfuserLogger.sleep(500,Anim.class);
        }// while not ready 

        
        for (FutureTask fut:futs) {
            try {
                Object o = fut.get();
                ArrayList<File> ff = (ArrayList<File>)o;
                for (File f:ff) {
                    animFigs.add(f);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(Anim.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                Logger.getLogger(Anim.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        
        Threader.executor.shutdown();
        // create avi!
        
    }
    
    public AnimEncoder get() {
        if (this.animOut) {

            String name = outDir
                    + varName + "_animation.avi";
            if (this.fullAviName != null && this.fullAviName.length() > 4) {
                name = outDir
                        + this.fullAviName;
            }
    
            return new AnimEncoder(animFigs, name,ops);

        }
        return null;
    }

    public static String getDaySpecs(Dtime dt) {
        String s = "";
        if (dt.dayOfWeek == Calendar.MONDAY) {
            s += "Mon, ";
        } else if (dt.dayOfWeek == Calendar.TUESDAY) {
            s += "Tue, ";
        } else if (dt.dayOfWeek == Calendar.WEDNESDAY) {
            s += "Wed, ";
        } else if (dt.dayOfWeek == Calendar.THURSDAY) {
            s += "Thu, ";
        } else if (dt.dayOfWeek == Calendar.FRIDAY) {
            s += "Fri, ";
        } else if (dt.dayOfWeek == Calendar.SATURDAY) {
            s += "Sat, ";
        } else {
            s += "Sun, ";
        }

        String h = dt.hour + ":";
        if (dt.hour < 10) {
            h = "0" + h;
        }

        String min = dt.min + "";
        if (dt.min < 10) {
            min = "0" + min;
        }

        s += h + min;

        return s;
    }

}
