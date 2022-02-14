/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.HEADERS_SOURCTYPE;
import static org.fmi.aq.enfuser.core.statistics.StatsCruncher.Y_NAME;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.options.FusionOptions;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;

/**
 *
 * @author johanssl
 */
public abstract class ConditionalAverager {
    
    private ArrayList<String> csvBuffer;
    
    String datasetLabel;
    FusionOptions ops;
    float[][] dat_yx;
    int[][] counters;
    boolean normalized =false;
    HashMap<String,Boolean> ySkips = new HashMap<>();
    
    public void updateStats(EvalDat ed) {  
        int x = this.getXIndex(ed);
        if (x<0 )return;
        
        float[] y_vector = this.getExtendedVector(ed);   
        for (int y = 0; y < y_vector.length; y++) {//exp, obs, fused, counter,...
            //cumulate averages
                    dat_yx[y][x] += y_vector[y];
                    counters[y][x]++;
        }   
    }
    
    private int maxLen(FusionOptions ops) {
        return ops.CATS.CATNAMES.length + HEADERS_SOURCTYPE.length;
    }
    
    private boolean skipThisType(String yheader) {
        Boolean b = this.ySkips.get(yheader);
        if (b==null) return false;
        return b;
    }
    
    protected ArrayList<String> yHeaders() {
        ArrayList<String> heads = new ArrayList<>();

        for (int i=0;i<StatsCruncher.HEADERS_SOURCTYPE.length;i++) {
            heads.add(StatsCruncher.HEADERS_SOURCTYPE[i]);
        }
    
        for (int c=0;c<ops.CATS.CATNAMES.length;c++) {
            heads.add(componentYname(c,ops));
        }
        return heads;
    }
    
    private static String componentYname(int c, FusionOptions ops) {
        return "ComponentPredicted_"+ ops.CATS.CATNAMES[c];
    }
    
    public ArrayList<String> processToCSVbuffer() {
        this.csvBuffer = new ArrayList<>();
        String header =this.datasetLabel +";";
        String[] xHeader = this.xHeader();
        for (String s:xHeader) {
            header+=s+";";
        }
        this.csvBuffer.add(header);
        this.normalize();
        int LEN = this.maxLen(ops);
        ArrayList<String> heads = yHeaders();
        
        for (int y =0;y<LEN;y++) {
            String yhead = heads.get(y);
            if (this.skipThisType(yhead)) continue;
            
            String line = yhead+";";
            for (int x =0;x<xHeader.length;x++) {
                String sval;
                if (y==StatsCruncher.COUNTERS) {
                    sval = (int)this.dat_yx[y][x]+";";//this is an integer.
                } else {//a double value with 3 decimals should do nicely.
                    double val = this.dat_yx[y][x];
                    val = editPrecision(val,3);
                    sval = val+";";
                }
                
                line+=sval;
            }//for x
            this.csvBuffer.add(line);
        }//for y
        return this.csvBuffer;
    }
    
    private float[] getExtendedVector(EvalDat ed) {
    float[] dat = new float[this.maxLen(ops)];
        
        int ind =-1;
        for (int i=0;i<ed.statsCrunchVector.length;i++) {
            ind++;
            dat[ind]=ed.statsCrunchVector[i];
        }
    
        for (int i=0;i<ops.CATS.CATNAMES.length;i++) {
            ind++;
            if (ed.expComponents!=null)dat[ind]=ed.expComponents[i];
        }
        return dat;
    }
    
    private void normalize() {
        if (normalized) return;
       
        for (int x =0;x<this.dat_yx[0].length;x++) {
            for (int y =0;y<this.dat_yx.length;y++) {
                if (y!=StatsCruncher.COUNTERS && counters[y][x]>0) {//if the line for this y is NOT the data point counter, divide by data point counter.
                    //Reason: the value held is currently the sum => average.
                    dat_yx[y][x]/= counters[y][x];
                }
            }
        }
        
        this.normalized=true;
    }
     
    protected abstract int getXIndex(EvalDat ed);
    
    public abstract String[] xHeader();

    float[] getXvector(int y) {
       normalize();
       return this.dat_yx[y];
    }

    float[] getYvectorforX(int x) {
       this.normalize();
       float[] dat = new float[this.dat_yx.length];
       for (int y =0;y<dat.length;y++) {
           dat[y] = this.dat_yx[y][x];
       }
       return dat;
    }
    
    
    public static ArrayList<String> processLite(FusionCore ens, FusionOptions ops,
           TZT tzt, ArrayList<Observation> obs, boolean met) {
        
        ArrayList<EvalDat> dats = new ArrayList<>();
        for (Observation o :obs) {
            EvalDat ed = new EvalDat(ens,ops,o);
            dats.add(ed);
        }
        
        ArrayList<String> ySkips = new ArrayList<>();
        for (int c:ops.CATS.C) {
            ySkips.add(componentYname(c, ops));
        }
        ySkips.add(Y_NAME(StatsCruncher.PRED));
        ySkips.add(Y_NAME(StatsCruncher.MODBG));
        ySkips.add(Y_NAME(StatsCruncher.PRED_FORC));
        ySkips.add(Y_NAME(StatsCruncher.PRED_MAE));
        ySkips.add(Y_NAME(StatsCruncher.RAW_OBS));
        //obs and counter remains
        
        return process(ySkips,  ops,tzt, met,  dats);
    } 
    
    
   public static ArrayList<String> process(ArrayList<String> ySkips, FusionOptions ops,
           TZT tzt, boolean met, ArrayList<EvalDat> dats) {
       
       ArrayList<ConditionalAverager> averagers = ConditionalTempoAverager.getDefault(ops,tzt);
       if (met) averagers.addAll(ConditionalMetAverager.getDefault(ops));
       
       ArrayList<String> results = new ArrayList<>();
       
           for (ConditionalAverager ca:averagers) {
               if (ySkips !=null) {
                   for (String skip:ySkips) {
                       ca.ySkips.put(skip, Boolean.TRUE);
                   }
               }
                for (EvalDat ed:dats) {
                    ca.updateStats(ed);
                }
                
                results.addAll(ca.processToCSVbuffer());
                results.add("\n");
           }

      return results; 
   }     
}
