/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;

/**
 *Holds puff modelled concentration fields as effectively as possible.
 * For the data container there are multiple dimensions:
 *  - there are H,W and time dimensions to manage smaller data containers.
 *  - The smaller data containers have Q(pollutant species) and C(category) dimensions.
 * 
 * Time dimension works 
 * All puff-modelled pollutant concentrations will accessed via this class.
 * @author johanssl
 */
public class PPFcontainer {
    
   public AreaNfo in;
   public HashMap<Long,Dtime> dates_key = new HashMap<>();//as minutes
   public final int temporalMins;
   public Dtime firstDt;
   public Dtime lastDt;
   public boolean snapToSpan=true;
   HashMap<Long,QCLayer> layers_minTicks = new HashMap<>();
   final FusionOptions ops;
    PPFcontainer(AreaNfo in, int shgTemporal_mins, FusionOptions ops) {
        this.temporalMins = shgTemporal_mins;
        this.in = in; 
        this.ops = ops;
    }
   
        /**
     * Form a String name for the selected pollutant type (q) and emission
     * source (s)
     *
     * @param q primary variable type (VariableAssociations)
     * @param sourc emission category source index (Category)
     * @return name of this Q,C -pair. E.g., 'traffic_NO2'.
     */
    public String getSHG_indexName(int q, int sourc) {
        return ops.CATS.CATNAMES[sourc] + "_" + ops.VARA.Q_NAMES[q];
    }
    
    /**
     * Form a unique hash key based on time.
     * @param dt the time
     * @return long ley.
     */
    private long getKey(Dtime dt) {
        return dt.systemSeconds()/60/this.temporalMins;
    }
    
    /**
     * Find the closest available data layer for the given time.
     * In case the time is out of bounds for the data contained here,
     * the first or the last layer will be given.
     * @param dt the time
     * @return data layer most suitable for the time.
     */
    private QCLayer getClosest(Dtime dt) {
        Dtime used = dt;
        if (this.firstDt==null) return null;
        
        if (dt.systemSeconds()< firstDt.systemSeconds()) used = firstDt;
        if (dt.systemSeconds()> lastDt.systemSeconds()) used = lastDt;    
       
        return this.layers_minTicks.get(getKey(used));
    }
    
    /**
     * Get singular data container for the given time and location index.
     * @param dt time
     * @param h h-index
     * @param w w-index
     * @return 
     */ 
    public QCd getCellContent_HW(Dtime dt, int h, int w) {
       QCLayer l =getClosest(dt);
       if (l==null) return null;
       
       return l.get(h, w);
    }
    
      /**
     * Get singular data container for the given time and location.
     * @param dt time
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     * @return 
     */ 
    QCd getValues(Dtime dt, double lat, double lon) {
      QCLayer l = this.getClosest(dt);
        if (l==null) return null;
        
        return l.get(lat, lon, in);
    }

          /**
     * Get clone of a singular data container for the given time and location.
     * The content of this will be interpolated for a smoothness.
     * @param dt time
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     * @return 
     */ 
    QCd getSmoothClone(Dtime dt, double lat, double lon,
            int rad, double pixelDistAdd) {
      
        QCLayer l = this.getClosest(dt);
        if (l==null) return null;
        
        return l.getSmoothClone(lat,lon, this.in,rad,pixelDistAdd,ops); 
    }
    
    
    public void removeTimeLayer(Dtime dt) {
        long key = getKey(dt);
        QCLayer l = layers_minTicks.get(key);
        if (l!=null) {
            layers_minTicks.remove(key);
        }
    }

    /**
     * Add a singular data container to this storage.
     * @param dat the data to be added
     * @param h h-index
     * @param w w-index
     * @param dt time
     */
   public void addQCData(QCd dat, int h, int w, Dtime dt) {
       
        long key = getKey(dt);
        QCLayer l = layers_minTicks.get(key);
        if (l==null) {//new layer!
            
            l = new QCLayer(in.H,in.W);
            this.layers_minTicks.put(key, l);
            this.dates_key.put(key, dt.clone());
            //first ever?
            if (firstDt==null) {
                firstDt = dt.clone();
                lastDt = dt.clone();
            }
            
            //update span
            if (dt.systemSeconds()<firstDt.systemSeconds()) firstDt = dt.clone();
            if (dt.systemSeconds()>lastDt.systemSeconds()) lastDt = dt.clone();
        }
        
        l.put(dat, h, w);  
    }
   
   /**
    * set values from an array (Q dimension) for a given category (c).
    * An element mathing the h,w,time must exist in order for this set
    * operation to occur.
    * @param c the category index
    * @param VARA varAssocs
    * @param Qvals the values for category c
    * @param h h index
    * @param w w index
    * @param dt time
    * @return true if set operation was successful.
    */
   public boolean setQvalues(int c,VarAssociations VARA, float[] Qvals,
           int h, int w, Dtime dt) {
        QCLayer l =getClosest(dt);
       if (l==null) return false;
       
       QCd dat = l.get(h, w);
       if (dat==null) return false;
       
       for (int q:VARA.Q) {
           dat.put(Qvals[q], q, c,ops);
       }
       return true;
   }
   
       public boolean setValue_QCHW(int q, int c,
               VarAssociations VARA, float f,
               int h,int w, Dtime dt) {
  
        QCLayer l =getClosest(dt);
       if (l==null) return false;
       
       QCd dat = l.get(h, w);
       if (dat==null) return false;
       
           dat.put(f, q, c,ops);
       
       return true;
    }

    /**
     * Get full data content for a given category (c) and species (q) as a 3-dim float array.
     * Dimensions: Time x H x W.
     * @param q the pollutant species index
     * @param c category index
     * @param nonZero if true, negative values will be cropeed to zero.
     * @return float[T][H][W] concentration dataset. 
     */
    float[][][] Extract3D(int q, Integer c, boolean nonZero) {
        
        ArrayList<Dtime> dates = getDataDates();
        float[][][] dat = new float[dates.size()][][];
        
        int j =-1;
        for (Dtime dt:dates) {
            j++;
            GeoGrid g = this.get2D_timeSlice(0, c, dt, nonZero);
            dat[j]=g.values;
        }
        return dat;
    }
    
     /**
     * Get a subset of data content for a given category (c) and species (q) as a 3-dim float array.
     * Dimensions: Time x H x W.
     * @param q the pollutant species index
     * @param c category index
     * @param nonZero if true, negative values will be cropped to zero.
     * @param start start time for subset
     * @param end end time for subset
     * @return float[T][H][W] concentration dataset. 
     */
    float[][][] ExtractSub3D(int q, Integer c, boolean nonZ, Dtime start, Dtime end) {
        ArrayList<Dtime> all = getDataDates();
        ArrayList<Dtime> dates = new ArrayList<>();
        for (Dtime dt:all) {
            if (dt.systemSeconds()>= start.systemSeconds() 
                    && dt.systemSeconds()<=end.systemSeconds()) {
                dates.add(dt);
            }
        }
        
        float[][][] dat = new float[dates.size()][][];
        
        int j =-1;
        for (Dtime dt:dates) {
            j++;
            GeoGrid g = this.get2D_timeSlice(0, c, dt, nonZ);
            dat[j]=g.values;
        }
        return dat;
        
    }
    
    /**
     * Get a singular concentration value. 
     * @param q pollutant type index
     * @param c category index
     * @param dt time
     * @param h h-index
     * @param w w-index
     * @return concentration value. returns null if data is not found.
     */
    Float getValue(int q, int c, Dtime dt, int h, int w) {
      QCd dat =getCellContent_HW(dt, h, w);
      if (dat==null) return null;
      return dat.value(q, c,ops);
           
    }
    
    /**
     * Get a list of dates for which there are data.
     * @return list of dates
     */
    public ArrayList<Dtime> getDataDates() {
        ArrayList<Long> keys = new ArrayList<>();
        for (Long key:this.dates_key.keySet()) {
            keys.add(key);
        }
        
        Collections.sort(keys);
        
        ArrayList<Dtime> times = new ArrayList<>();
        for (Long key:keys) {
            times.add(this.dates_key.get(key));
        }
        return times;
    }
    
    /**
     * get a 2-dim dataset in the form of GeoGrid.
     * @param q pollutant type index
     * @param c category index
     * @param dt time
     * @param nonZero if true, negative values will be cropped to zero.
     * @return gridded dataset. Returns null if not found.
     */
    public GeoGrid get2D_timeSlice(int q, Integer c, Dtime dt,boolean nonZero) {
       
        QCLayer l = getClosest(dt);
        if (l==null) return null;
        
        float[][] dat = new float[l.H][l.W];
        for (int h =0;h<l.H;h++) {
            for (int w =0;w<l.W;w++) {
                QCd d = l.get(h,w);
                if (c==null) {
                    
                    dat[h][w]=d.categorySum(q,nonZero,ops);
                    
                } else {
                    dat[h][w]=d.value(q, c,ops);
                }
            }//for w
        }//for h
        
        GeoGrid g = new GeoGrid(dat,dt.clone(),this.in.bounds.clone());
        return g;  
    }
    /**
     * Check whether or not data exists for this time.
     * @param dt the time
     * @return true, if data exists.
     */
    boolean hasData(Dtime dt) {
       return layers_minTicks.get(getKey(dt))!=null;
    }

    float fillMissing(Dtime dt) {
        int fills =0;
        int total=0;
        QCLayer l = getClosest(dt);
        ArrayList<QCd> news = new ArrayList<>();
         for (int h =0;h<l.H;h++) {
            for (int w =0;w<l.W;w++) {
                QCd d = l.get(h,w);
                total++;
                if (d!=null) continue;
                //this is empty! Fill it.
                double lat = this.in.getLat(h);
                double lon = this.in.getLon(w);
                
                QCd fill = getSmoothClone(dt, lat, lon,3, 0.2);
                fill.addIndex(h, w);
                news.add(fill);
                fills++;
                
            }//for w
         }//for h
         
         for (QCd fill:news) {
              l.put(fill, fill.y, fill.x);
         }
         
         return 100f*fills/(float)total;
    }




    
   
   
}
