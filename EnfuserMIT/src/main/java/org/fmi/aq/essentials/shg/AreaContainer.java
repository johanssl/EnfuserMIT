/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import java.io.Serializable;
import java.util.HashMap;

/**
 *
 * @author johanssl
 */
public class AreaContainer implements Serializable {

    private static final long serialVersionUID = 7526552293822776147L;//Important: change this IF changes are made that breaks serialization!     
    HashMap<Short, float[]>[] containers; // operates on H

    public AreaContainer() {

    }

    /*
    private Integer getKey(short h, short w) {
        return h*10000+w; // integer can hold up to 2.1 billion and thus this system can easily hold 9999x9999 sized grids
    }
     */
    public float[] get(short h, short w, SparseHashGrid shg) {

        if (containers == null) {
            containers = new HashMap[shg.H];
        }
        if (containers[h] == null) {
            containers[h] = new HashMap<>();
        }

        return this.containers[h].get(w);
        /*
        if (this.hSlices == null) {
           this.hSlices = new Container[shg.maxH][];
       } 
        
       HashMap<Short,Container> HH;
       if (this.hSlices[h]== null) {
           this.hSlices[h] = new Container[shg.maxW]; 
       }
       
       //HH = this.hSlices[h];
       //return HH.get(w);
       return this.hSlices[h][w];
         */
    }

    public void sumOrCreateContent(float[] vals, short h, short w, boolean forceNew, SparseHashGrid shg) {
        /*
       if (this.hSlices == null) {
           this.hSlices = new HashMap[shg.maxH];
       } 
        
       HashMap<Short,Container> HH;
       if (this.hSlices[h]== null) {
           this.hSlices[h] = new HashMap<>(); 
       } 
       
        HH = this.hSlices[h];
        Container cont = HH.get(w);
         */

        //Container cont = this.get(h, w, shg);
        float[] cont = this.get(h, w, shg);

        if (cont == null) {
            //cont = vals.clone();
            cont = new float[vals.length];
            for (int i = 0; i < cont.length; i++) {
                cont[i] = vals[i];
            }
            //cont = new Container(vals); // must be float type at this point
            //HH.put(w, cont );   
            //this.hSlices[h][w] = cont;
            this.containers[h].put(w, cont);
        } else {
            //cont.sumOrCreateContent(vals, forceNew) ;  
            for (int i = 0; i < vals.length; i++) {
                if (forceNew) {
                    cont[i] = vals[i];
                } else {
                    cont[i] += vals[i];
                }

            }

        }

        //check if this is a new geoW
        /*
        Hkey key = new Hkey(h, w);
         Container cont = containers.get(key);
        if (cont== null) {
            cont = new Container_float(vals); // must be float type at this point
          containers.put(key, cont );        
        } else {
        cont.sumOrCreateContent(vals, forceNew) ;     
        }
         */
    }

    public void replace(short h, short w, Container c) {
        //this.containers.replace(new Hkey(h, w), c);
        //this.hSlices[h][w]= null;
        //this.hSlices[h][w] =c;
    }

}
