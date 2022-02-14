/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.logging;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.netCdf.NcInfo;

/**
 *This class is used to analyze the contents of gridded input datasets.
 * Based on the acceptance limits set in VarAssociations, a gridset
 * can be discarded if the data within is not compliant.
 * 
 * The main use-case for this class is to check data quality at StationSource
 * when a new StationSource is created or added with a new dataset.
 * 
 * @author johanssl
 */

    
public class GridQC {


    int n =0;
    float min =Float.MAX_VALUE;
    float max =Float.MAX_VALUE*-1;
    float ave =0;
    boolean NaN = false;
    final GeoGrid g;
    final int typeInt;
    double[] acceptRanges_minMax;
    final String unit;
    final String varname;
 
    private boolean checked = false;
    private final FusionOptions ops;
 /**
  * Create an instance
  * @param g the geoGrid carrying the gridded data.
  * @param typeInt variable type index as defined in VarAssociations.
     * @param ops
  */   
    public GridQC(GeoGrid g, int typeInt, FusionOptions ops) {
        this.g = g;
        this.typeInt=typeInt;
        VarAssociations VARA = ops.VARA;
        this.acceptRanges_minMax = VARA.getAcceptRange(typeInt);
        this.unit = VARA.UNIT(typeInt);
        this.varname = VARA.VAR_NAME(typeInt);
        this.ops = ops;
    }

    /**
     * Launch the analysis, checking min,max and average values in the grid.
     * Also, any occurences of NaN values are also checked.
     */
   private void check() {
    if (g==null) return;

    for (int h =0;h<g.H;h++) {
        for (int w =0;w<g.W;w++) {
             float val = g.values[h][w];
              ave+=val;
               n++;
               if (Float.isNaN(val)) NaN=true;
               if (val < min) min=val;
               if (val > max) max=val;
        }//for w
    }//for h

    ave/=n;
    checked =true;
   }
 
  /**
   * Check the outocome of the analysis
   * @return false if the dataset is not acceptable for any reason.
   * These can be:
   * - The dataset is null
   * - number of datapoints is 0
   * - A NaN value is present
   * - minimum or the maximum value is not within the acceptangesRanges 
   * (if set for the type)
   */ 
 public boolean isAcceptable() {
     if (!checked) check();
     
     if (g==null || NaN || n ==0) return false;
     if (acceptRanges_minMax!=null) {
        if (min < acceptRanges_minMax[0]) return false;
        if (max > acceptRanges_minMax[1]) return false;
     }
     return true;
 }
 
 /**
  * Get a short String summary of the analysis outcome.
  * @return String summary
  */
 public String getPrintout() {
     String s= "GridQC Acceptance:"+this.isAcceptable() +"\n gridIsNull="+(g==null) +","
             +varname+", "+unit +", hasNaN="+NaN
             + ", n="+n +", min="+min +", max="+max+", ave="+ave;
             if (this.acceptRanges_minMax!=null) {
                s+= ", acceptRanges=[" +acceptRanges_minMax[0] +","+acceptRanges_minMax[1]+"]";
             }
     return s;        
 }

/**
 * Create an instance of GridQC and launch assessment.
 * @param g the dataset to be assessed
 * @param typeInt variable type index as defined in VarAssociations
     * @param ops
 * @return QC result instance
 */ 
public static GridQC assessGridQuality(GeoGrid g, int typeInt, FusionOptions ops) {

GridQC qc = new GridQC(g,typeInt,ops);
qc.check();
return qc;
}

/**
 * Make sure that each GeoGrid in the array has equal dimensions (bounds and HxW)
 * In case not, then the content is modified based on the first GeoGrid's
 * dimensions
     * @param lenient if true, then HxW is only used to check compatibility
 * @param reggs the array of GeoGrids
 * @param desc Identifier String for exception logging.
 * @param reg_nfos
 * @return possibly modified array of GeoGrids that have equal spatial dimensions.
 */
public static ArrayList<GeoGrid> verifyEqualDimensions(boolean lenient,
        ArrayList<GeoGrid> reggs, String desc, ArrayList<NcInfo> reg_nfos) {
    if (reggs.size()<=1) return reggs;
    
    GeoGrid base = reggs.get(0);
   
    String baseid = checkString(base,lenient);
    ArrayList<GeoGrid> gg = new ArrayList<>();
    int k=-1;
    for (GeoGrid g:reggs) {
        k++;
        String check = checkString(g,lenient);
        if (!check.equals(baseid)) {
            String add ="";
            if (reg_nfos!=null) { //more info can be given
                    NcInfo bas = reg_nfos.get(0);
                    NcInfo diff = reg_nfos.get(k);
                    add = ", base dataset="+bas.varname + " ("+baseid+")" 
                            +", comparison dataset="+diff.varname +  " ("+check +")" ; 
                }
            
                EnfuserLogger.log(Level.WARNING, GridQC.class,
                        "GridQC actions taken due to non-uniform grid dimensions: " + desc + add);
                
            g = copyContentWithDimensions(base,g);
        }
        
      gg.add(g);   
    }
    return gg;
}

/**
 * Copy content of 'g' into a new GeoGrid that has spatial properties of
 * 'base'.
 * @param base
 * @param g
 * @return 
 */
public static GeoGrid copyContentWithDimensions(GeoGrid base, GeoGrid g) {
    float[][] dat = new float[base.H][base.W];
    
    GeoGrid ng = new GeoGrid(dat,base.dt.clone(),base.gridBounds.clone());
    for (int h =0;h<base.H;h++) {
        for (int w =0;w<base.W;w++) {
            double lat = base.getLatitude(h);
            double lon = base.getLongitude(w);
            
           Float val = g.getValueAt_exSafe(lat, lon);
           if (val!=null) ng.values[h][w]=val;
        }
    }
    return ng;
}
   
public static String checkString(GeoGrid g, boolean lenient) {
  if (lenient) return g.H +"_"+g.W;
  return g.gridBounds.toText() +"_"+g.H +"_"+g.W;
}   

public static String checkString_editPrecision(GeoGrid g) {
      return g.H +"x"+g.W+", "+g.gridBounds.toText(2);
}
    
}   

