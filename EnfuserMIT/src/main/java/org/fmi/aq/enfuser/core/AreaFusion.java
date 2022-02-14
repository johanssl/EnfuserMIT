/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

import org.fmi.aq.enfuser.core.tasker.GenericArrayTask;
import org.fmi.aq.enfuser.kriging.KrigingAlgorithm;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import java.util.ArrayList;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.core.output.ArchivePack;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.geoGrid.ByteGeoNC;
import java.util.Collections;
import org.fmi.aq.enfuser.core.AreaInfo;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.core.receptor.RPconcentrations;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.enfuser.options.Categories;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.netCdf.NcInfo;
import org.fmi.aq.enfuser.core.output.OutlierReduction;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.ftools.FuserTools;
import static org.fmi.aq.enfuser.ftools.FuserTools.arrayConvert;
import org.fmi.aq.essentials.plotterlib.Visualization.GridDot;

/**
 * This class is for storing of modelled datasets, pollutant concentrations
 * for a single modelling time step. The dimensions and resolution is therefore
 * the same as has been set in RunParameters for the task.
 * 
 * Besides pollutant concentrations there are also supporting information being
 * stored as category-specific concentrations. The allPollutant.zip files will be
 * created mostly based on the content of this temporary data structure.
 * 
 * The concentration computation phase also uses this via the update methods this
 * class provides. Several threads are updating the content of this class simultaneously,
 * however, the threads operate on different sections of the dataset to avoid
 * concurrent exceptions.
 * 
 * Also, several archiving methods use this class to load allPollutant.zip files
 * into AreaFusion instances. In such cases the content is packed more tightly
 * so that several AreaFusion instances that are loaded simultaneously would
 * not hog too much memory (e.g., during the creation of animations).
 * 
 * @author johanssl
 */
public class AreaFusion {

    public final static String N = System.getProperty("line.separator");

    public double dlat, dlon;
    private float[][][] consGrid;
    private ByteGeoGrid[] consGrid_compressed;//create these from Arch instead, to save memory (animation)
    private final boolean compressedMode;
    //===these can be ignored 
    private float[][][][] compontents_cq_hw;
    //======

    HashMap<Integer, String> typeInfo = new HashMap<>();
    public Met metAtCenter = null;

    public GeoGrid[] metGrids = null;
    public GeoGrid[] silamGrids = null;

    public ArrayList<Observation>[] obs;

    public final Dtime dt;
    public float cellArea_km2;
    public final int H, W;
    public final Boundaries bounds;

    ArrayList<Integer> typeInts;

    private final static int COMPRES_DIV = 3;//this should NOT be lower than 3.
    private final VarAssociations VARA;
    private HashMap<String,ByteGeoNC> processedComponents = new HashMap<>();

    public AreaFusion(AreaInfo inf, FusionOptions ops) {
        this(inf.H, inf.W, inf.dt, inf.bounds, arrayConvert(inf.types),false,ops);
    }


    public AreaFusion(int H, int W, Dtime dtF, Boundaries bounds,
            ArrayList<Integer> types, boolean compressForAnim, FusionOptions ops) {

        //addDefaultComponentsOfInterest(ens.ops);     
        this.dt = dtF.clone();
        this.compressedMode = compressForAnim;
        this.VARA = ops.VARA;
        if (compressForAnim) {
            consGrid_compressed = new ByteGeoGrid[VARA.varLen()];
        } else {
            this.consGrid = new float[ops.VARA.VAR_LEN()][][];
        }
        
        this.silamGrids = new GeoGrid[VARA.varLen()];
        this.obs = new ArrayList[VARA.varLen()];

        for (int i = 0; i < obs.length; i++) {
            this.obs[i] = new ArrayList<>();
        }
        Categories CATS = ops.CATS;
        
        //prepare category-pollutant grids (if not archive mode)
        for (int typeInt : types) {
            //component datasets -hash
            if (!compressForAnim) {
                consGrid[typeInt] = new float[H][W];
                if (this.compontents_cq_hw==null) this.compontents_cq_hw = new float[CATS.CATNAMES.length][VARA.Q.length][][];
                if (VARA.isPrimary[typeInt]){
                    for (int c :CATS.C) {
                      this.compontents_cq_hw[c][typeInt] = new float[H / COMPRES_DIV][W / COMPRES_DIV];
                    }
                }
            }   
            
        }//for types
        
        this.cellArea_km2 = bounds.getCellArea_km2(H, W);
        //set the focus to ensemble array as default
        this.H = H;// fH = H;
        this.W = W;// fW = W;

        this.bounds = bounds;
        this.typeInts = types;

        this.dlat = (bounds.latmax - bounds.latmin) / H;
        this.dlon = (bounds.lonmax - bounds.lonmin) / W;

    }

    public void addTypeSpecificInfo(int typeInt, String nfo) {

        String s = this.typeInfo.get(typeInt);
        if (s == null || s.equals("null")) {
            this.typeInfo.put(typeInt, nfo);
        } else {
            this.typeInfo.put(typeInt, s + nfo);
        }

    }

    public void replaceTypeSpecificInfo(int typeInt, String nfo) {
        this.typeInfo.put(typeInt, nfo);
    }

    public ArrayList<GeoGrid> getAllMetGrids() {
        ArrayList<GeoGrid> arr = new ArrayList<>();

        for (GeoGrid metGrid : this.metGrids) {
            if (metGrid != null) {
                arr.add(metGrid);
            }
        }
        return arr;
    }

    public float[][][] getSolarPower(Dtime[] dts, FusionCore ens) {
        float[][][] pows = new float[dts.length][this.H][this.W];

        //double lat, lon;
        for (int t = 0; t < dts.length; t++) {
            Dtime date = dts[t];
            EnfuserLogger.log(Level.FINER,AreaFusion.class,"Af.Solar power: " + date.getStringDate());

            pows[t] = GenericArrayTask.multiThreadTask(GenericArrayTask.SOLAR, ens, this, date, null, 0)[0];

        }//t
        return pows;
    }

    public static Dtime[] getDates_interpolated(ArrayList<AreaFusion> afs, FusionOptions ops) {

        int SCALER = ops.visOps.vid_interpolationFactor; // 15-minute intervals
        int secondsAdd = 60 * ops.areaFusion_dt_mins() / SCALER;
        EnfuserLogger.log(Level.FINER,AreaFusion.class,
                "AreaFusion.getTimes: seconds to be added every frame: " + secondsAdd 
                        + " (" + (int) (secondsAdd / 60) + "min)");

        Dtime[] temp = new Dtime[afs.size() * SCALER - (SCALER - 1)]; //why -(SCALER-1) : because this is interpolation and not extrapolation. For the last index i there is not going to be i+1, i+2 or i+3

        Dtime dt = afs.get(0).dt.clone();

        for (int i = 0; i < temp.length; i++) {

            temp[i] = dt.clone();
            EnfuserLogger.log(Level.FINER,AreaFusion.class,dt.getStringDate());
            dt.addSeconds(secondsAdd);

        }
        return temp;
    }

    public static String[] getTimes_interpolated(ArrayList<AreaFusion[]> afs, int z, FusionOptions ops) {

        int SCALER = ops.visOps.vid_interpolationFactor; // 15-minute intervals
        int secondsAdd = 60 * ops.areaFusion_dt_mins() / SCALER;
        EnfuserLogger.log(Level.FINER,AreaFusion.class,
                "AreaFusion.getTimes: seconds to be added every frame: " + secondsAdd 
                        + " (" + (int) (secondsAdd / 60) + "min)");

        String[] temp = new String[afs.size() * SCALER - (SCALER - 1)]; //why -(SCALER-1) : because this is interpolation and not extrapolation. For the last index i there is not going to be i+1, i+2 or i+3

        Dtime dt = afs.get(0)[z].dt.clone();

        for (int i = 0; i < temp.length; i++) {

            temp[i] = dt.getStringDate();
            dt.addSeconds(secondsAdd);

        }
        return temp;
    }

    public static String[] getTimes_nonInterp(ArrayList<AreaFusion> afs, FusionOptions ops) {

        String[] temp = new String[afs.size()]; //why -(SCALER-1) : because this is interpolation and not extrapolation. For the last index i there is not going to be i+1, i+2 or i+3

        for (int i = 0; i < temp.length; i++) {
            temp[i] = afs.get(i).dt.getStringDate();
        }
        return temp;
    }
    
    private int[] getGridHW(int typeInt) {
        if (this.compressedMode) {
            return new int[]{
            consGrid_compressed[typeInt].H,
            consGrid_compressed[typeInt].W
            };
        } else {
            return new int[]{
            consGrid[typeInt].length,
            consGrid[typeInt][0].length
            };
        }
    }
    
    public boolean hasConcentrationGrid(int typeInt) {
        if (this.compressedMode) {
            return  consGrid_compressed[typeInt]!=null;
        } else { 
            return consGrid[typeInt]!=null;
        }
    }
    public int typeLength() {
         if (this.compressedMode) {
            return  consGrid_compressed.length;
        } else { 
            return consGrid.length;
        }
    }
    
    public float getConsValue(int typeInt, int h, int w) {
         if (this.compressedMode) {
            return  (float)consGrid_compressed[typeInt].getValueAtIndex(h, w);
        } else {
             if (consGrid[typeInt]==null) return 0;
            return consGrid[typeInt][h][w];
        }
    }
    
    public float[][] consAsFloatArray(int typeInt) {
         if (this.compressedMode) {
            GeoGrid g = this.consGrid_compressed[typeInt].convert();
            return g.values;
        } else { 
            return consGrid[typeInt];
        }
    }
    
    public void addConsGrid(int typeInt, GeoGrid g) {
        if (compressedMode) {
            this.consGrid_compressed[typeInt] = new ByteGeoGrid(g.values,g.dt,g.gridBounds);
        } else {
            this.consGrid[typeInt] = g.values;
        }
    }

    public static double[] getMinMax(ArrayList<AreaFusion> afs, int typeInt) {
        double min = Double.MAX_VALUE;
        double max = Double.MAX_VALUE*-1;

        for (AreaFusion af :afs) {   
            for (int h = 0; h < af.H; h++) {
                for (int w = 0; w < af.W; w++) {
                    float val = af.getConsValue(typeInt, h, w);

                    if (val > max) {
                        max = val;
                    }
                    if (val < min) {
                        min = val;
                    }

                }//for w
            }//for h    
        }//for afs

        return new double[]{min, max};
    }

    public void addStationaryObservations(FusionCore ens) {

        for (VariableData vd : ens.datapack.variables) {
            if (vd == null) {
                continue;
            }

            for (AbstractSource s : vd.sources()) {

                if (!s.dom.isStationary()) {
                    continue;//not stationary
                }
                if (!this.bounds.intersectsOrContains(s.dom.latmax, s.dom.lonmax)) {
                    continue; // is stationary but out of bounds
                }
                StationSource st = (StationSource) s;
                Observation o = st.getExactObservation(dt, 0);
                if (o != null) {
                    this.obs[vd.typeInt].add(o);
                }

            }//for sources
        }//for vd

    }

    public static ArrayList<Float> getObservedValueHistoGram(ArrayList<AreaFusion> afs, int typeInt) {

        ArrayList<Float> arr = new ArrayList<>();

        for (AreaFusion af : afs) {

            for (Observation o : af.obs[typeInt]) {
                if (o != null) {
                    arr.add(o.value);
                }

            }//for sources
        }//for afs

        Collections.sort(arr);
        return arr;

    }

    public static double[] assessConcentrationRange(ArrayList<AreaFusion> afs, int typeInt) {
        VarAssociations VARA = afs.get(0).VARA;
        if (typeInt == VARA.VAR_AQI) {
            EnfuserLogger.log(Level.FINER,AreaFusion.class,
                    "AreaFusion.assessConcentrationRange skipped for AQI (will be processed later)");
            return null;
        } else if (!afs.get(0).hasConcentrationGrid(typeInt)//consGrid_qhw[typeInt] == null
                ) {
            EnfuserLogger.log(Level.FINER,AreaFusion.class,
                    "assessConcentrationRange: grid of " + VARA.VAR_NAME(typeInt) + " is null!");
            return null;
        }

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        String maxLoc = "";
        int[] hw = afs.get(0).getGridHW(typeInt);
        int h = hw[0];
        int w = hw[1];
        
        float observedHistoMax = 0;
        try {

            ArrayList<Float> observed = getObservedValueHistoGram(afs, typeInt);
            if (observed != null && !observed.isEmpty()) {
                observedHistoMax = observed.get((int) (observed.size() * 0.96));
                EnfuserLogger.log(Level.FINER,AreaFusion.class,
                        "AreaFusion.getMinMax: observed minMax for " + VARA.VAR_NAME(typeInt) 
                        + " is " + observed.get(0) + ", " + observed.get(observed.size() - 1));
                EnfuserLogger.log(Level.FINER,AreaFusion.class,
                        "Observed histogram max is " + observedHistoMax);
            }

        } catch (Exception e) {
        }

        int sparser = 4;
        ArrayList<Float> values = new ArrayList<>();
        double cutPercentile = 0.998;
        //int n =1;
        //float sum =0;

        AreaFusion highAf = afs.get(0);
        float maxSum = 0;
        for (int i = 0; i < afs.size(); i++) {
            float afSum = 0;
            for (int H = 0; H < h; H += sparser) {
                for (int W = 0; W < w; W += sparser) {
                    float val = afs.get(i).getConsValue(typeInt, H, W);//consGrid_qhw[typeInt][H][W];
                    afSum += val;

                    if (val < min) {
                        min = val;//global minimum must be searched using all the AreaFusionSlices
                    }
                }//w
            }//h
            if (afSum > maxSum) {
                maxSum = afSum;
                highAf = afs.get(i);
            }
        }

        for (int H = 0; H < h; H += sparser) {
            for (int W = 0; W < w; W += sparser) {

                float val = highAf.getConsValue(typeInt, H, W);//consGrid_qhw[typeInt][H][W];

                //update & add to list
                if (val > max) {
                    max = val;
                    double[] latlon = highAf.getLatLon(H, W);
                    maxLoc = highAf.dt.getStringDate() + "_" + latlon[0] + "_" + latlon[1];
                }
                values.add(val);

            }
        }

        EnfuserLogger.log(Level.FINER,AreaFusion.class,"AreaFusion.getMinMax: min is " + min + " and max is " + max);
        EnfuserLogger.log(Level.FINER,AreaFusion.class,"\tMax value location: " + maxLoc);
        Collections.sort(values);
        int index = (int) (values.size() * cutPercentile);
        float listMax = values.get(index);
        EnfuserLogger.log(Level.FINER,AreaFusion.class,"Value list size: " + values.size() + ", " + (cutPercentile * 100) 
                + "% percentile max value from list is " + listMax);

        float maxLimit = Math.max(listMax, observedHistoMax);
        EnfuserLogger.log(Level.FINER,AreaFusion.class," the outlier limit value will be " + maxLimit);

        return new double[]{min, maxLimit};

    }


    public static float[][][] getGrids_nonInterp(ArrayList<AreaFusion> afs, int typeInt) {
       
        float[][][] grid = new float[afs.size()][][];
        for (int i = 0; i < afs.size(); i++) {
            float[][] dat = afs.get(i).consAsFloatArray(typeInt);
            grid[i] = dat;//afs.get(i).consGrid_qhw[typeInt]; //if i =2 we store grid 8

        }
        return grid;
    }

    
    public void postProcess(FusionCore ens, ArrayList<Integer> nonMet_typeInts) {
        
        for (int typeInt : nonMet_typeInts) {
            if (!ens.ops.VARA.isPrimary[typeInt]) continue;
            for (int c : ens.ops.CATS.C) {
            float[][] dat = this.compontents_cq_hw[c][typeInt];
            if (dat==null) continue;
            //check non-zero
            boolean hasContent = false;
            for (float[] dd : dat) {
                if (hasContent) {
                    break;
                }
                for (float f : dd) {
                    if (f != 0) {
                        hasContent = true;
                        break;
                    }
                }
            }

            if (hasContent) {
                    ByteGeoNC bg = new ByteGeoNC(dat, this.dt, this.bounds);
                    String name = ens.ops.CATS.getArchComponentName(ens.ops,typeInt,c);
                    this.processedComponents.put(name, bg);
                    
                        EnfuserLogger.log(Level.FINER,AreaFusion.class,"Processed archivable cq-component: " + name);     
            }
            
            }//for categories
        }//for types
        
       try {//file down some outlier peaks in the data. Usually there's one pixel
           //from the millions of piexels, in which there are freakishly large concentrations.
          OutlierReduction.cutOutliers(this,ens.ops);  
       }catch (Exception e) {
           e.printStackTrace();
       }
       
    }

    public static GeoGrid[] getMetGrids_interpolated(ArrayList<AreaFusion> afs, 
            int METVAR_I, FusionOptions ops) {
        //METVAR_I -=VAR_MET_OFFSET; 
        ArrayList<float[]> temp = new ArrayList<>();
        int SCALER = ops.visOps.vid_interpolationFactor;

        GeoGrid[] grid = new GeoGrid[afs.size() * SCALER - (SCALER - 1)];

        if (afs.get(0).metGrids[METVAR_I] == null) {
            EnfuserLogger.log(Level.FINER,AreaFusion.class,"AreaFusion.getGrids: selected MET VAR is null, aborting.");
            return null;
        }

        int h = afs.get(0).metGrids[METVAR_I].H;
        int w = afs.get(0).metGrids[METVAR_I].W;

        Boundaries b = afs.get(0).metGrids[METVAR_I].gridBounds;
        String type = afs.get(0).metGrids[METVAR_I].varName;

     
        for (int i = 0; i < afs.size(); i++) {
            Dtime date;
            try {
                date = afs.get(i).metGrids[METVAR_I].dt.clone(); // TODO this is the same for all!
            } catch (NullPointerException e) {
                String info = "";
                if (afs.get(i).metGrids != null) {
                    info += ", metGrid is NOT null,";
                    if (afs.get(i).metGrids[METVAR_I] != null) {
                        info += ", metGrid[METVAR_I] is NOT null,";
                        if (afs.get(i).metGrids[METVAR_I].dt == null) {
                            info += ", metGrid[METVAR_I].dt is NULL";
                        }
                    }
                }
                EnfuserLogger.log(Level.WARNING,AreaFusion.class,
                  "getMetGrids_interpolated: NullPointer encountered for metGrid: " 
                        + afs.get(i).dt.getStringDate() + ", var = " + ops.VAR_NAME(METVAR_I) + info);

                return null;
            }
            grid[i * SCALER] = afs.get(i).metGrids[METVAR_I]; //if i =2 we store grid 8

            if (SCALER > 1 && i + 1 < afs.size()) { // interpolation can be done if this is not the LAST grid

                for (float j = 1.0f; j < SCALER; j++) {
                    float firstWeight = 1.0f - j / SCALER; // e.g. 0.75, 0.5 and 0.25
                    float nextWeight = 1.0f - firstWeight;
                    float[][] n = new float[h][w];
                    grid[i * SCALER + (int) j] = new GeoGrid(type, n, date, b); // if i =2 we create grids 9,10,11

                    for (int H = 0; H < h; H++) {
                        for (int W = 0; W < w; W++) {
                            float firstVal = afs.get(i).metGrids[METVAR_I].values[H][W];
                            float nextVal = afs.get(i + 1).metGrids[METVAR_I].values[H][W];

                            //windDir special interpolation
                            if (METVAR_I == ops.VARA.VAR_WINDDIR_DEG) {

                                temp.clear();
                                temp.add(new float[]{firstVal, firstWeight});
                                temp.add(new float[]{nextVal, nextWeight});
                                grid[i * SCALER + (int) j].values[H][W] 
                                        = KrigingAlgorithm.combineWdir(temp);

                            } else {
                                grid[i * SCALER + (int) j].values[H][W] = 
                                        firstVal * firstWeight + nextVal * nextWeight; // calculate interpolated value
                            }
                        }//for w
                    }//for h

                }//for scaler
                date.addSeconds(ops.areaFusion_dt_mins() / SCALER * 60);
            }//if scaler > 1 and not the last slice

        }
            EnfuserLogger.log(Level.FINER,AreaFusion.class,"AreaFusion.getGrids: creating interpolated"
                    + " MET layers complete. ListSize now =" + grid.length);
        
        return grid;
    }

    public static GeoGrid[] getMetGrids_nonInterp(ArrayList<AreaFusion> afs,
            int METVAR_I, FusionOptions ops) {

        GeoGrid[] grid = new GeoGrid[afs.size()];

        if (afs.get(0).metGrids[METVAR_I] == null) {
                EnfuserLogger.log(Level.FINER,AreaFusion.class,"AreaFusion.getGrids: selected "
                        + "MET VAR is null, aborting.");
            
            return null;
        }

        for (int i = 0; i < afs.size(); i++) {
            grid[i] = afs.get(i).metGrids[METVAR_I]; //if i =2 we store grid 8

        }

        return grid;
    }

    public int[] getHW(double lat, double lon) {

        int h = (int) ((bounds.latmax - lat) / dlat);
        int w = (int) ((lon - bounds.lonmin) / dlon);
        int[] HW = {h, w};

        return HW;

    }

    public double[] getLatLon(int h, int w) {

        double lat = bounds.latmax - h * dlat;
        double lon = bounds.lonmin + w * dlon;
        double[] latlon = {lat, lon};

        return latlon;

    }

    /**
     * Update concentration grids based on an RP object.
     *
     * @param nonMetTypes a list of variable types (primary) that are to be
     * updated.
     * @param env RP object which holds the emission footprint information and
 all concentration components
     * @param h h index of grid
     * @param w w index of grid
     * @param ens the core
     * @param fill if true then flag this update process to have been a 'fill'
 operation. This means that the updated data originates from interpolation
 of other RP objects.
     */
    public void update(ArrayList<Integer> nonMetTypes, RP env,
            int h, int w, FusionCore ens, boolean fill) {

        for (int typeInt : nonMetTypes) {
            if (!ens.ops.VARA.isPrimary[typeInt]) {
                continue;
            }

            float[] exps = env.getExpectances(typeInt, ens);
            float value = FuserTools.sum(exps);
            updatePrimaries(value, typeInt, exps, h, w);
        }
    }
   
    /**
     * Update concentration grids based on an array of precomputed exps[] for
     * the typeints given in nonMetTypes. Main use case: PartialCombiner when it
     * is not desirable to create an Env for update purposes only.
     *
     * @param nonMetTypes a list of variable types (primary) that are to be
     * updated.
     * @param q_exps a QC float array[][]. C dimension is for categories and Q
     * dim is for primary types. The first dimension is for Q, the second one is
     * for C.
     * @param h h index of grid
     * @param w w index of grid
     * @param fill if true then flag this update process to have been a 'fill'
     * operation. This means that the updated data originates from interpolation
     * of other nearby cells.
     */
    public void update(ArrayList<Integer> nonMetTypes, float[][] q_exps,
            int h, int w,  boolean fill) {

        for (int k = 0; k < nonMetTypes.size(); k++) {
            int typeInt = nonMetTypes.get(k);
            if (!VARA.isPrimary[typeInt]) {
                continue;
            }

            float[] exps = q_exps[k];
            float value = FuserTools.sum(exps);
            updatePrimaries(value, typeInt, exps, h, w);
        }

    }

    private void updatePrimaries(float value,
            int typeInt, float[] exps, int h, int w) {

        //grid dim check
        if (h < 0 || w < 0) {
            return;
        }
        if (h > H - 1 || w > W - 1) {
            return;
        }

        if (h >= consGrid[typeInt].length) {
            return; // since the CombalCallables now operate
        }    //several stripes at a time the h-index can go outOfBounds.

        this.consGrid[typeInt][h][w] = (value);
        updateComponentGrids(typeInt, exps, h, w);
    }
    
    /**
     * Update emission category specific output. The resolution is lower
     * for these, and therefore there is some scaling to be done for the added values.
     * For some type-category pairs the addition can/will be skipped
     * if deemed unimportant for output datasets.
     * @param typeInt the species type
     * @param exps component representation of the modelled concentrations.
     * @param h h index for high-res data
     * @param w  w index for high-res data
     */
    private void updateComponentGrids(int typeInt, float[] exps, int h, int w) {
        //h,w index tranformation to lower resolution
        int lh = h / COMPRES_DIV;
        int lw = w / COMPRES_DIV;
        float resValueScaler = 1f/(COMPRES_DIV * COMPRES_DIV);//lower res => less cells => more additions to cells => must adjust
        Categories CATS = GlobOptions.get().CATS;
        for (int cat : CATS.C) {
            float compVal = exps[cat];
            if (compVal==0) continue;
            float[][] dat = this.compontents_cq_hw[cat][typeInt];
            if (dat!=null) {//will be added (deemed important)
                GeoGrid.gridSafeAddValue(dat, lh, lw, exps[cat]*resValueScaler);
                //e.g., resdiv is 4, then each cell should add 16 times something into 
                //this cell and each increment should be adjusted to get an average. 
            }
        }//for categories

    }

    public String getOutput() {

        double max_ens_val = Double.MIN_VALUE;
        double min_ens_val = Double.MAX_VALUE;

        double[] minCoords = new double[2];
        double[] maxCoords = new double[2];

        //Check the ensemble 
        //general info first
        String sources_allDesc = "Area ensemble in: " 
                + this.dt.getStringDate(Dtime.STAMP_FULL_ZONED)
                + N;
        sources_allDesc += "BoxArea: " + this.bounds.latmin + "N - " 
                + this.bounds.latmax + "N, "
                + this.bounds.lonmin + "E - " + this.bounds.lonmax + "E." + N;

        sources_allDesc += "Resolution : " + this.H + " X " + this.W + " cells. "
                + N;

        if (this.metAtCenter != null && metAtCenter.hasWindData()) {
            sources_allDesc += "Meteorological data was used in the fusion." + N
                    + this.metAtCenter.fullPrintOut() + N;

        }

        sources_allDesc += "====================" + N;

        for (Integer i = 0; i < VARA.VAR_LEN(); i++) {
            if (this.typeInfo.get(i) != null) {
                sources_allDesc += "====" + VARA.VAR_NAME(i) 
                        + " at " + this.dt.getStringDate() + "====" + N;
                sources_allDesc += this.typeInfo.get(i) + N + N;
            }

        }

        for (int typeInt : this.typeInts) {
            try {
                for (int h = 0; h < this.H; h++) {
                    for (int w = 0; w < this.W; w++) {

                        float val = this.getConsValue(typeInt, h, w);
                        if (val//this.consGrid_qhw[typeInt][h][w] 
                                <= min_ens_val) {
                            min_ens_val = val;//this.consGrid_qhw[typeInt][h][w];
                            minCoords = this.getLatLon(h, w);
                        }

                        if (val >= max_ens_val) {
                            max_ens_val = val;
                            maxCoords = this.getLatLon(h, w);
                        }

                    }
                }


                //extremes==============================
                int[] minHW = this.getHWforMin(typeInt);
                int lowValueH = minHW[0];
                int lowValueW = minHW[1];

                int[] maxHW = this.getHWforMax(typeInt);
                int maxH = maxHW[0];
                int maxW = maxHW[1];
                float low = this.getConsValue(typeInt, lowValueH, lowValueW);
                float high = this.getConsValue(typeInt, maxH, maxW);
                
                sources_allDesc += VARA.VAR_NAME(typeInt) + " MINIMUM value: "
                        + editPrecision(low, 3) + N;

                sources_allDesc += "coordinates: " + minCoords[0] + "," + minCoords[1] + N;

                sources_allDesc += "==============================" + N + N;
                sources_allDesc += VARA.VAR_NAME(typeInt) + " MAXIMUM value: "
                        + editPrecision(high, 3) 
                      + N;
                sources_allDesc += "coordinates: " + maxCoords[0] + "," + maxCoords[1] + N;

                //====================================== 
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }// for types  

        return sources_allDesc;

    }

    int[] getHWforMin(int typeInt) {

        int[] temp = new int[2];
        int currH = 0;
        int currW = 0;
        double currMin = Double.MAX_VALUE;

        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                float val = this.getConsValue(typeInt, h, w);
                if (val < currMin) {
                    currMin = val;
                    currH = h;
                    currW = w;
                }
            }
        }
        temp[0] = currH;
        temp[1] = currW;

        return temp;
    }

    int[] getHWforMax(int typeInt) {

        int[] temp = new int[2];
        int currH = 0;
        int currW = 0;
        double currMax = Double.MIN_VALUE;

        for (int h = 0; h < H; h++) {
            for (int w = 0; w < W; w++) {
                float val = this.getConsValue(typeInt, h, w);
                if (val > currMax) {
                    currMax = val;
                    currH = h;
                    currW = w;
                }
            }
        }
        temp[0] = currH;
        temp[1] = currW;

        return temp;
    }


    public ArchivePack getComponentPack(FusionOptions ops) {
        ArrayList<ByteGeoNC> bgs = new ArrayList<>();
        ArrayList<NcInfo> nfos = new ArrayList<>();

        for (String name:this.processedComponents.keySet()) {
            
            int[] cq = ops.CATS.archComponentName_toCQ(name,ops);
            int cat = cq[0];
            int typeI = cq[1];

            ByteGeoNC bg = this.processedComponents.get(name);
            bgs.add(bg);
            NcInfo nfo = new NcInfo(
                    ops.VARNAME_CONV(typeI) + "_" + ops.CATS.CATNAMES[cat],
                    ops.UNIT(typeI),
                    ops.VARA.LONG_DESC(typeI) + ", for source category " + ops.CATS.CATNAMES[cat],
                    NcInfo.MINS,
                    dt,
                    ops.areaFusion_dt_mins()
            );
            nfos.add(nfo);

        }//for types

        if (!bgs.isEmpty()) {
            ArchivePack ap = new ArchivePack(null, bgs, nfos);
            return ap;
        } else {
            return null;
        }

    }

    public double getLat(int h) {
        return GeoGrid.getLat(this.bounds,h,dlat);
    }

    public double getLon(int w) {
         return GeoGrid.getLon(this.bounds,w,dlon);
    }

    public void putValue(int typeInt, float val, int h, int w) {
        
       if (this.consGrid[typeInt]==null) {
          return;
       }
       
        this.consGrid[typeInt][h][w]=val; 
    }

    public void initGridsIfNull(int typeInt) {
        if (consGrid[typeInt] ==null) {
            consGrid[typeInt] = new float[H][W];
        }
    }
    /**
     * Do a simple fill operation for the index given by h,w.The fill will be 
     * done based on values in other cells given in the array.
     * @param h h index to be filled
     * @param w w index to be filled
     * @param fillHWs must contain valid (h,w) indices that does not cause
     * out-Of-Bounds -Exceptions.
     * @param env the end object
     * @param ens the core
     */
    public void fillOne(int h, int w, ArrayList<int[]> fillHWs, RP env,FusionCore ens) {
        
        float scaler = 1f/fillHWs.size();
        for (int typeInt =0;typeInt <this.consGrid.length;typeInt++) {
           float[][] dat =this.consGrid[typeInt];
           if (dat==null)continue;
           
           for (int[] hws:fillHWs) {
               dat[h][w]+=scaler*dat[hws[0]][hws[1]];
           }
           
           if (ens.ops.VARA.isPrimary[typeInt]) {
               updateComponentGrids(typeInt, env.getExpectances(typeInt, ens), h, w);
           }
        }
        
    }

    public boolean oobs_hw(int h, int w) {
        if (h <0 || h>H-1) {
            return true;
        } else if (w <0 || w>W-1) {
            return true;
        }
        return false;
    }

    public ArrayList<GridDot> getGridDots(int typeInt, FusionOptions ops, DataBase DB) {
       ArrayList<GridDot> garr = new ArrayList<>();
       ArrayList<Observation> arr = this.obs[typeInt];
       if (arr==null) return garr;
       
         for (Observation o : arr) {
             
             double val = editPrecision(o.value, 1);
             String text = "<-- " + val + ops.VARA.UNIT (o.typeInt);
             GridDot gd = new GridDot(typeInt, o.lat,o.lon,text,(float)val,ops.visOps.dotSize_percent);
             if (gd.dottable()) {
                 gd.qualityDotSizeMod(DB,o.ID);
             }
             garr.add(gd);
         }
         return garr;
           
    }

}
