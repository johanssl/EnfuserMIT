/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;

/**
 *This class is for storing smaller data containers (QCd) for a specific
 * time.
 * @author johanssl
 */
public class QCLayer {
    
   private final QCd[] arr;
    final int H;
    final int W;
   
   public QCLayer(int H, int W) {
       this.H =H;
       this.W =W;
       this.arr = new QCd[H*W];
       
   }
   
   /**
    * get the index for data, for the given pair of (h,w)
    * @param h h-index
    * @param w w-index
    * @return index
    */
   private int ind(int h, int w) {
       return h*W + w;
   }
   
   /**
    * Fetch single data container for the given pair of (h,w)
    * @param h h-index
    * @param w w-index
    * @return single data container
    */
   public QCd get(int h, int w) {
       return arr[ind(h,w)];
   }
   
   /**
    * Put a data container to the element according to the given (h,w) index.
    * @param d the element to be added.
    * @param h h-index
    * @param w w-index
    */
   public void put(QCd d, int h, int w) {
       arr[ind(h,w)]=d;
   }

    /**
     * Get a smooth interpolated clone based on the coordinates.
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     * @param in area and resolution paramters (PuffPlatform)
     * @param rad smoothing radius (1,2,3)
     * @param pixelDistAdd when smoothing the values, a larger value here
     * can remove pointiness of the outcome. Use [0,1].
     * @return a smooth interpolated clone.
     */
    QCd getSmoothClone(double lat, double lon, AreaNfo in, int rad,
            double pixelDistAdd, FusionOptions ops) {
      
        if (lat < in.bounds.latmin) lat = in.bounds.latmin;
        if (lat > in.bounds.latmax) lat = in.bounds.latmax;
        
        if (lon < in.bounds.lonmin) lon = in.bounds.lonmin;
        if (lon > in.bounds.lonmax) lon = in.bounds.lonmax;
        
        double dlat = in.dlat;
        double dlon = in.dlon;
        //get hw index from original grid
        int h = in.getH(lat);
        int w = in.getW(lon);

        ArrayList<QCd> vals = new ArrayList<>();
        ArrayList<Float> ws = new ArrayList<>();
        float wsum = 0;

        for (int i = -rad; i <= rad; i++) {
            for (int j = -rad; j <= rad; j++) {
                int ch = h + i;
                int cw = w + j;
                    if (in.oobs(ch, cw)) continue;
                    
                    QCd values = this.get(ch, cw);
                    if (values == null) {
                        continue;
                    }
                    double clat = in.getLat(ch);
                    double clon = in.getLon(cw);

                    double pixelDistP2 = (lat - clat) * (lat - clat) / (dlat * dlat) 
                            + (lon - clon) * (lon - clon) / (dlon * dlon)
                            + pixelDistAdd
                            ;//is always larger than zero

                    //this is the squared distance of evaluation cellContent, turn this into weight
                    float weight = 1f / (float) pixelDistP2;
                    vals.add(values);
                    ws.add(weight);
                    wsum += weight;

            }//for rad1
        }//for rad2

        if (vals.isEmpty()) {
            return null; //no points for kriging found
        }
        //combine product

         QCd d= new QCd(ops);
        for (int i = 0; i < vals.size(); i++) {
            float weight = ws.get(i) / wsum;//sums to 1.
            QCd temp = vals.get(i);
            d.stackWithWeight(temp,weight);
           
        }//for vals

        return d;
         
    }

   public QCd get(double lat, double lon, AreaNfo in) {

        int h = in.getH(lat);
            if (h<0) h=0;
            if (h>in.H-1) h =in.H-1;
        
        int w = in.getW(lon);
            if (w<0) w=0;
            if (w>in.W-1)w=in.W-1;
        
        return this.get(h, w);
    }
   
}
