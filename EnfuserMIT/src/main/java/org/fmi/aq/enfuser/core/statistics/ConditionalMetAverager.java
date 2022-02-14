/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import java.util.ArrayList;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class ConditionalMetAverager extends ConditionalAverager{

    final int metvar;
    final String metVarName;
    final String desc;
    final String units;
    final double[] breakPoints;//this will define which x index is selected
    //for averaging for each inserted data point. 

    public ConditionalMetAverager(int type, FusionOptions ops) {
        this.ops = ops;
        this.metvar = type;
        this.metVarName = ops.VAR_NAME(type);
        this.units = ops.UNIT(type);
        this.desc = ops.VARA.LONG_DESC(type, true);
        
        this.datasetLabel = "#" + this.metVarName + " ["+units+"]";
        this.breakPoints = ops.VARA.getStatsCrunchBreakPoints(type);
        
        if (this.dat_yx==null) {
            int ylen = StatsCruncher.HEADERS_SOURCTYPE.length +ops.CATS.CATNAMES.length;
            int xlen = this.breakPoints.length;
            this.dat_yx = new float[ylen][xlen];
            this.counters = new int[ylen][xlen];
        }
        this.ySkips.put(StatsCruncher.Y_NAME(StatsCruncher.RAW_OBS), Boolean.TRUE);
    }
    
    public static ArrayList<ConditionalAverager> getDefault(FusionOptions ops) {
        ArrayList<ConditionalAverager> arr = new ArrayList<>();
        
        for (int type:ops.VARA.allVars()) {
            if (ops.VARA.IS_METVAR(type)) {//is meteorological type
                double[] breaks = ops.VARA.getStatsCrunchBreakPoints(type);
                if (breaks!=null) {//some breakpoint vector has been set for this!
                   arr.add(new ConditionalMetAverager(type,ops)); 
                }
            }
        }
        
        return arr;        
    }

    @Override
    protected int getXIndex(EvalDat ed) {
        int ind =-1;
        Met met = ed.met;
        float metval = met.getSafe(metvar);
        if (metvar==ops.VARA.VAR_SHORTW_RAD) metval = met.totalSolarRadiation();
        for (double thresh:this.breakPoints) {
            ind++;
            if (metval <thresh) return ind;
        }
        
        return this.breakPoints.length-1;// return last. The value exceeds the largest threshold
    }

    @Override
    public String[] xHeader() {
        String[] heads  =new String[this.breakPoints.length];
        for (int i =0;i<this.breakPoints.length;i++) {
            heads[i] =" < "+this.breakPoints[i];
        }
        int maxI = this.breakPoints.length-1;
        heads[maxI] = " > " + this.breakPoints[maxI-1];
        return heads;
    }

}
