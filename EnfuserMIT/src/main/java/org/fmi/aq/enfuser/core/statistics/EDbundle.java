/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.statistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.source.DataBase;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.essentials.plotterlib.Visualization.GridDot;

/**
 *
 * @author johanssl
 */
public class EDbundle {
    
    /**
     * This holds Evaluation Data points as follows: 
     * Integer key - variable type, holds all data with the same type
     * Secondary hash key - source ID name, holds all measurements from that source
     */
    private final HashMap<Integer, HashMap<String,ArrayList<EvalDat>>> type_id_points;
    
    public EDbundle(ArrayList<EvalDat> arr) {
     
        this.type_id_points = new HashMap<>();
        for (EvalDat ed:arr) {
            int typeI = ed.typeInt;
            String ID = ed.ID;
            
            ArrayList<EvalDat> ar = this.getSource(typeI, ID, true);
            ar.add(ed);  
        }
        
    }
    
    public HashMap<String,ArrayList<EvalDat>> getType(int typeI, boolean createMissing) {
        HashMap<String,ArrayList<EvalDat>> hash = this.type_id_points.get(typeI);
        if (hash == null && createMissing) {
            hash = new HashMap<>();
            this.type_id_points.put(typeI, hash);
        }
        return hash;
    }
    
    private ArrayList<EvalDat> getSource(int typeI, String ID, boolean createMissing) {
        HashMap<String,ArrayList<EvalDat>> hash = getType(typeI,createMissing);
        if (hash==null) return null;
        
        ArrayList<EvalDat> arr = hash.get(ID);
        if (arr==null && createMissing) {
            arr = new ArrayList<>();
            hash.put(ID, arr);  
        }
        return arr;
    }
    
      public ArrayList<EvalDat> getData(int typeI, String ID) {
          return this.getSource(typeI, ID, false);
      }
    
    public Collection<Integer> getTypes() {
        return this.type_id_points.keySet();
    }
    
    public Collection<String> getSources(int typeI) {
        if (this.type_id_points.get(typeI)==null) return null;
        return this.type_id_points.get(typeI).keySet();
    }
    
    public void printTotalCount() {
        int types =0;
        int sources =0;
        int obs =0;
        for (Integer typeI:getTypes()) {
            types++;
            
            for (String ID:getSources(typeI)) {
                sources++;
                ArrayList<EvalDat> arr =getData(typeI, ID);
                if (arr!=null) obs+=arr.size();
            }//for ID
            
        }//for type
        
        EnfuserLogger.log(Level.INFO, this.getClass(), "EDbundle contains: "
                + types +" variable types, " + sources +"type-specific sources, " 
                + obs +" total observations.");
    }

    public ArrayList<GridDot> getSourceAverages(int typeInt, boolean useDots,
            FusionOptions ops) {
        
       ArrayList<GridDot> arr = new ArrayList<>();
       Collection<String> cols  =getSources(typeInt);
       if (cols==null) return arr;
       
       for (String ID:cols) {
           ArrayList<EvalDat> eds = getData(typeInt, ID);
           if (eds==null) continue;
           EvalDat one = eds.get(0);
           float imprec = one.variabilityRating();
           
           //float pred =0;
           float ob = 0;
           for (EvalDat ed:eds) {
               //pred+=ed.modelledValue()/eds.size();
               ob+=ed.value()/eds.size();
           }
           
           double val = editPrecision(ob,3);
           GridDot gd = new GridDot(typeInt,one.lat,one.lon,val+"",
                   (float)val,ops.visOps.dotSize_percent);
           gd.qualityDotSizeMod(imprec);
           arr.add(gd);
       }//for ids
       return arr;
    }
      
}
