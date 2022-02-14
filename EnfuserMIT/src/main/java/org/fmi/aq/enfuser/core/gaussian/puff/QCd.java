/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;


/**
 * This class acts as a data container to hold pollutant concentrations
 * in the Q (species) - C (category) space.
 * For memory considerations the data (2D) is stored in a 1-dim float array,
 * which therefore requires some indexing to be done.
 * @author johanssl
 */
public class QCd {

    public static float[][] getArray(FusionOptions ops) {
      return new float[ops.CATS.CATNAMES.length][ops.VARA.Q_NAMES.length];
    }
    
   private final float[] qc;
   public QCd(FusionOptions ops) {
       qc = new float[ops.CATS.CATNAMES.length * ops.VARA.Q_NAMES.length];
   }
  
   /**
    * Zero the content of this object.
    */
   public void clear() {
       for  (int i =0;i<qc.length;i++) {
           qc[i]=0;
       }
   }
   
   /**
    * get the index for data location in the array.
    * @param q
    * @param c
    * @return 
    */
   private int ind(int q, int c, FusionOptions ops) {
       return ops.VARA.Q_NAMES.length*c + q;
   }
   
   /**
    * get a singular value
    * @param q
    * @param c
     * @param ops
    * @return 
    */
   public float value(int q, int c, FusionOptions ops) {
       return qc[ind(q,c,ops)];
   }
   
  /**
   * Put a singular value to the object
   * @param val
   * @param q
   * @param c 
   */ 
  public void put(float val, int q, int c, FusionOptions ops) {
      qc[ind(q,c,ops)]=val;
  }
  
  /**
   * For the given species index, return an array of concentrations by category.
   * @param q the pollutant species index
   * @param ops
   * @return float[] concentrations, matching the indexing of Categories class.
   */
  public float[] getComponents(int q, FusionOptions ops) {

      float[] vals = new float[ops.CATS.CATNAMES.length];
      
      for (int c:ops.CATS.C) {
          vals[c] = this.value(q, c,ops);
      }
      return vals;
  }
    
    /**
     * Get total concentration, by forming a sum of category concentrations.
     * @param q
     * @param nonZero
     * @param ops
     * @return 
     */
   public float categorySum(int q, boolean nonZero, FusionOptions ops) {
        GlobOptions g = GlobOptions.get();
        float sum =0;
         for (int c:g.CATS.C) {
          sum += this.value(q, c,ops);
         }
         
       if (nonZero && sum<0) return 0;
       return sum;
    }
    
   /**
    * add content with a weight scaler. This is used for smooth clone creation.
    * @param temp
    * @param weight 
    */ 
   protected void stackWithWeight(QCd temp, float weight) {
        for (int i =0;i<this.qc.length;i++) {
            qc[i]+=temp.qc[i]*weight;
        }
    }
    
   /**
    * get a 2-DIM representation of the data.
    * @return float[Q][C].
    */
    float[][] intoArray(FusionOptions ops) {
       float[][] dat = new float[ops.VARA.Q_NAMES.length][ops.CATS.CATNAMES.length];
       for (int q:ops.VARA.Q) {
           for (int c: ops.CATS.C) {
               dat[q][c] = this.value(q,c,ops);
           }
       }
       return dat;
    }

    public void add(int q, int c, float val, FusionOptions ops) {
       qc[ind(q,c,ops)]+=val;
    }

    public int y=-1;
    public int x =-1;
    void addIndex(int y, int x) {
      this.y =y;
      this.x =x;
    }

    
}
