/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.datapack.source;

/**
 *
 * @author johanssl
 */
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.options.FusionOptions;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.essentials.date.Dtime;
import java.util.Collections;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.DataCore;
import org.fmi.aq.enfuser.options.VarAssociations;

public abstract class AbstractSource {

    public String ID;
    public String addInfo = "";
    public int typeInt;

    public boolean banned = false;
    
    public HashMap<Integer, Layer> layerHash = new HashMap<>();
    protected int layerStepSeconds = 3600;//this determines the temporal step in which data is indexed to
    //this source. default: hourly data.
    
    ArrayList<Layer> timeSorted = null;
    public ArrayList<AbstractSource> aliasTypes = new ArrayList<>();
    public Domain dom;
    public float gridRes_km = 0;
    DataBase DB;
    public DBline dbl;
    int err = 0;
    public VarAssociations VARA;
    
    //quality control with respect modelling timespan
    protected boolean differentGeos = false;
    protected  int missing=0;
    protected  int n =0;
    protected  double min = Double.MAX_VALUE;
    protected  double max = Double.MAX_VALUE*-1;
    protected  double ave = 0;
    protected  String checkGeo = null;
    
    public float getBaseVar(DataBase DB, short t_off, FusionOptions ops) {
        return DB.getTotalVar(this, 0, t_off, 0.0f, ops);
    }

    protected void sortLayers() {
        if (this.timeSorted == null || this.timeSorted.size() != this.layerHash.size()) {
            timeSorted = new ArrayList<>();
            for (Layer l : this.layerHash.values()) {
                timeSorted.add(l);
            }

            Collections.sort(timeSorted, new LayerComparator());
        }
    }

    public boolean hasMinuteData() {
        return this.layerStepSeconds<=60;
    }
    
    protected int LayerKey(Dtime dt) {
        return (int)(dt.systemSeconds()/this.layerStepSeconds);
    }
    protected Layer getLayer(Dtime dt, int add) {
        return this.layerHash.get(LayerKey(dt)+add);
    }
    protected Layer getLayer(Dtime dt) {
        return this.layerHash.get(LayerKey(dt));
    }
    protected void putLayer(Dtime dt, Layer l) {
        int key = this.LayerKey(dt);
        this.layerHash.put(key, l);
    }
    
    public Layer getClosestLayer(Dtime dt, int SEARCH_ADD) {

        for (int i = 0; i < SEARCH_ADD; i++) {
            Layer l = getLayer(dt, -i);
            if (l != null) {
                return l;
            }

            l = getLayer(dt, +i);
            if (l != null) {
                return l;
            }
        }
        return null;
    }

    public Layer getClosestLayer_backwards(Dtime dt, int add) {
        for (int i = 0; i < add; i++) {//note that this starts from zero
            Layer l = getLayer(dt, -i);
            if (l != null) {
                return l;
            }

        }
        return null;
    }

    public Layer getClosestLayer_forwards(Dtime dt, int add) {
        for (int i = 1; i < add; i++) {//note that this starts from ONE.
            Layer l = getLayer(dt, +i);
            if (l != null) {
                return l;
            }
        }
        return null;
    }

    public Layer getExactLayer(Dtime dt) {
        return getLayer(dt,0);
    }

    public Dtime[] getStartAndEnd() {

        Dtime[] dates = new Dtime[2];
        dates[0] = dom.start;
        dates[1] = dom.end;
        return dates;
    }

    public void crossCheckAliasTypes(DataPack dp) {
        //find type alias sources from other variableDatas
        for (VariableData vd : dp.variables) {
            if (vd == null) {
                continue;
            }
            if (vd.typeInt == this.typeInt) {
                continue;
            }
            for (AbstractSource s : vd.sources()) {
                if (s.ID.equals(this.ID)) {//names match
                    if (!this.aliasTypes.contains(s)) {
                        this.aliasTypes.add(s);//not yet listed, add
                    }
                }

            }
        }
    }

    public abstract void finalizeStructure(DataCore ens);

    public abstract String getDataPrintout(DataCore ens);

    public abstract Float getSmoothValue(Dtime dt, double lat, double lon);

    public Observation getSmoothObservation(Dtime dt, double lat, double lon) {
        Float val = this.getSmoothValue(dt, lat, lon);
        if (val == null) {
            return null;
        }

        Observation o = new Observation(typeInt, ID, (float) lat, (float) lon,
                dt, val, (float) dbl.height_m, null);
        return o;
    }

    public abstract Observation getExactRawValue(Dtime dt, double lat, double lon);

    public String getSourceInfo(DataCore ens) {
        String n = "\n";

        String s = this.ID + ", " + VARA.VAR_NAME(this.typeInt) + ":\n" + addInfo + "\n"
                + "\tholds " + this.layerHash.size() + " hour layers.\n";
        s += "Source Height[m] = " + dbl.height_m + n + addInfo + n
                + "secondary ID: " + dbl.alias_ID + ", " + dbl.alias_ID2 + n;
        s += dom.latmin + " - " + dom.latmax + ", " + dom.lonmin + " - " + dom.lonmax + n;

        s += "Source has grid structure: " + (this instanceof GridSource) + "\n";
        if (!this.aliasTypes.isEmpty()) {
            s += "Alias types:";
            for (AbstractSource ss : this.aliasTypes) {
                s += VARA.VAR_NAME(ss.typeInt) + ",";
            }
            s += "\n";
        }

        Layer first = this.getClosestLayer(dom.start, 48);
        if (first.g != null) {
            s += ("First layer grid dimensions: " + first.g.H + " x " + first.g.W) + n;
        }

        if (this instanceof StationSource) {
            StationSource st = (StationSource) this;
            if (st.orig_lat != null) {
                s += "Original statin lat,lon =" + st.orig_lat + "," + st.orig_lon + n;
            }
        }

        return s + this.getDataPrintout(ens);
    }

    public double[] getMinMaxAve() {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double ave = 0;

        for (Layer l : this.layerHash.values()) {
            double[] minMax = l.getMinMaxAve();
            if (min > minMax[0]) {
                min = minMax[0];
            }
            if (max < minMax[1]) {
                max = minMax[1];
            }

            ave += minMax[2];
        }
        ave /= this.layerHash.size();
        return new double[]{min, max, ave};
    }

    public String getShortID(DataBase db) {
        String id = this.ID + "";
        if (id.contains("sensor") && this.dbl != null) {
            id = this.dbl.alias_ID + "";//may contain a better name tag
        }
        if (id.contains("sensor")) {
            return "";//proper alias was not found
        }
        try {
            if (id.length() > 4) {//reduce characters
                if (id.contains("_")) {
                    id = id.split("_")[1];
                }
                //e.g. Vaasa_Vesitorni => Vesitorni

                id = id.substring(0, 3);
                // => Ves
            }
            id = id.toUpperCase(); // => VES

        } catch (Exception ee) {

        }
        return id;
    }

    /**
     * Find the closest previous and next layer for the given time. Main purpose
     * for the method is to interpolate additional layers for data that has
     * infrequent data layers with intervals such as 3h and 6h.
     *
     * In case a layer is found for the exact time then both the previous and
     * next returned layers are the same.
     *
     * @param current time for the search
     * @param tol_step tolerance in (usually this is hours) (backwards and forwards) for the search
     * @param ls a temporary Layer[] element. Can be null, and if it is null
     * then a new instance of Layer[2] is created
     * @return Layer[]{previous,next}
     */
    public Layer[] getPreviousNextLayer(Dtime current, int tol_step, Layer[] ls) {
        //clear previous temp structure
        if (ls == null) {
            ls = new Layer[]{null, null};
        } else {
            ls[0] = null;
            ls[1] = null;
        }

        for (int i = 0; i < tol_step; i++) {
            Layer lower = this.getLayer(current, - i);
            if (lower != null) {
                ls[0] = lower;
                break;
            }
        }

        for (int i = 0; i < tol_step; i++) {
            Layer upper = this.getLayer(current, + i);
            if (upper != null) {
                ls[1] = upper;
                break;
            }
        }

        return ls;
    }
    public final static int PREV_NEXT_6H = 6;

    public Layer[] getPreviousNextLayer(Dtime current, Layer[] temp) {
        return getPreviousNextLayer(current, PREV_NEXT_6H, temp);
    }

    public abstract String oneLinePrinout(FusionOptions ops);

    public abstract Level getSeverityDataQuality(FusionOptions ops);

    public abstract String getDataQualityAlert();

    

}
