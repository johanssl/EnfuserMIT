/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.netCdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import ucar.ma2.Array;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 *
 * @author johanssl
 */
public class Dim {

    public static HashMap<String, Dim> toHash(ArrayList<Dim> dims) {
       HashMap<String, Dim> hash = new HashMap<>();
       for (Dim d:dims) {
           hash.put(d.fullName, d);
       }
       return hash;
    }
    
    public float[] values;
    public String fullName;
    public String shortName;
    public String units;
    Variable dVar;
    
    public Dim(Dimension dim, NetcdfFile ncfile) {
        
        String dName = dim.getFullName();
        dVar = ncfile.findVariable(dName);
        Array dArray;
        try {
            dArray = dVar.read();
            values = new float[(int)dVar.getSize()];
            for (int j = 0; j < dVar.getSize(); j++) {
                float val = dArray.getFloat(j);
                values[j]=val;
            }//for dim values
            this.fullName = dim.getFullName();
            this.shortName =  dim.getShortName();
            this.units = dVar.getUnitsString();
    
            
        } catch (IOException ex) {
            Logger.getLogger(Dim.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    @Override
    public String toString() {
        String s = this.fullName+", "+this.shortName 
                +", units=" + this.units 
                +", length="+values.length +" ("+values[0] +" => "+ values[values.length-1]+")";
         return s;       
    }
    
    public static ArrayList<Dim> list(List<Dimension> dimensions,NetcdfFile ncfile) {
        ArrayList<Dim> dims = new ArrayList<>();
        for (Dimension dim : dimensions) {
            dims.add(new Dim(dim,ncfile));
        }//for dims
        return dims;
    }
   
    
    
}
