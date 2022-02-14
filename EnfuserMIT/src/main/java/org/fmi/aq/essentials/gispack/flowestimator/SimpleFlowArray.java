/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;

/**
 * This class has the identical purpose than the FlowArray, however,
 * the content of it is much simpler (has no DAVG, user scaling factors).
 * 
 * This class holds hourly flow values. For temporal indexing there are 24x3 unique
 * hours as defined in TemporalDef. For vehicle categories, there are 4.
 * 
 * The class is used in RoadPieces to hold, edit and fetch flow data.
 * @author johanssl
 */
public class SimpleFlowArray implements Serializable{
    
    private static final long serialVersionUID = 7521472294622776447L;//Important: change this IF changes are made that breaks serialization!
    private final float[] values;//this is a 2-dimensional dataset presented as 1dim array.
    public String source;
    
    //some attributes to descrbe the content of the object, especially with regards dimensions
    final int C;
    final int T;
    
    /**
     * Create a clean oopy of the instance.
     * @return the copy
     */
    public SimpleFlowArray hardCopy() {
        return new SimpleFlowArray(this.values,this.source);
    }
    
    private SimpleFlowArray(float[] vals, String source) {
        this.C =FlowEstimator.VEHIC_CLASS_NAMES.length;
        this.T =24*3;
        this.values = new float[C*T];
        
        for (int i =0;i<this.values.length;i++) {
            this.values[i] =vals[i];
        }
        this.source = source;
    }
    
    /**
     * Create a new instance based on the older, more complicated FlowArray
     * instance
     * @param fa the older class for the treatment of flow data. 
     */
    public SimpleFlowArray(FlowArray fa) {
        this.C =fa.classLen;
        this.T =24*3;
        this.values = new float[C*T];
        
        for (Dtime dt:FlowEstimator.getAllDates()) {
            float[] all = fa.getValues_allClasses(new float[C], dt);
            for (int c =0;c<C;c++) {
               int index = this.get1Dim_index(c, dt, null);
               this.values[index]=all[c];
            }
        }   
    }
    
    /**
     * Create an instance using a collection data arrays, one for time, one for
     * flows and one for vehicle category. The length and order of the lists must match.
     * @param source flow computation approach identification
     * @param dates list of days, for which the hour and day-of-week matters only.
     * @param vals hourly flow values
     * @param classes vehicle class for the value-time pair with the exact same
     * array index.
     */
    public SimpleFlowArray(String source, ArrayList<Dtime> dates, ArrayList<Float> vals,
            ArrayList<Integer> classes) {
        
        this.source = source;
        this.C = FlowEstimator.VEHIC_CLASS_NAMES.length;
        int W = TemporalDef.WKDS_NAMES.length;
        int D = TemporalDef.DIURNALS;
        this.T = W*D;
        this.values = new float[C * T];
        boolean set[] = new boolean[this.values.length];

        //setup values
        for (int i = 0; i < dates.size(); i++) {
            Dtime dt = dates.get(i);
            int classI = classes.get(i);
            float val = vals.get(i);

            int ind = get1Dim_index(classI, dt, null);
            this.values[ind] = val;
            set[ind] = true;    
        }

        //check set percentage
        float percentsPerHit = 100f / this.values.length;
        float counter = 0;
        for (int i = 0; i < values.length; i++) {
            if (set[i]) {
                counter += percentsPerHit;
            }
        }

        byte valuesSet_percentage = (byte) (counter);
        EnfuserLogger.log(Level.FINER,this.getClass(),
                "Value set percentage = " + valuesSet_percentage + "%");
        
    }

     /**
     * Create an instance using a collection data arrays, one for time, one for
     * flows presented as float[4] holding all vehicle classes at once.
     * The length and order of the lists must match.
     * 
     * @param source flow computation approach identification
     * @param dates list of days, for which the hour and day-of-week matters only.
     * @param vals hourly flow values for all vehicle classes per instance
     * @param print
     */
    public SimpleFlowArray(String source, ArrayList<Dtime> dates,
            ArrayList<float[]> vals, boolean print) {
        this.source = source;
        C = FlowEstimator.VEHIC_CLASS_NAMES.length;
        int W = TemporalDef.WKDS_NAMES.length;
        int D = TemporalDef.DIURNALS;
        this.T = W*D;
        this.values = new float[C * T];
        boolean set[] = new boolean[this.values.length];

        //setup values
        for (int i = 0; i < dates.size(); i++) {
            Dtime dt = dates.get(i);
            float[] valA = vals.get(i);
            for (int classI:FlowEstimator.VC_INDS) {

                float val = valA[classI];
                int ind = get1Dim_index(classI, dt,null);

                this.values[ind] = val;
                set[ind] = true;
                if (print) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),"VALUE_TEST;" 
                            + val + "; => ;" + this.getValue(classI, dt) + ";");
                }
            }
        }//for dates

        //check set percentage
        float percentsPerHit = 100f / this.values.length;
        float counter = 0;
        for (int i = 0; i < values.length; i++) {
            if (set[i]) {
                counter += percentsPerHit;
            }
        }

        byte valuesSet_percentage = (byte) (counter);
        if (print) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Value set percentage = " 
                    + valuesSet_percentage + "%");
        }
    }
    

    /**
     * Fetch the raw index for datapoint that relates with the given class and
     * time.
     *
     * @param classI class index
     * @param dt time
     * @return get index for the one dimensional array.
     */
    private int get1Dim_index(int classI, Dtime dt, Integer switchH) {
        return classI*this.T + TemporalDef.getExtendedIndex72(dt,switchH);
    }
    

    private int get1Dim_index(int classI, int timeIndex) {
        return classI*this.T + timeIndex;
    }

     /**
      * Get Daily Average Vehicle counts for all vehicle classes.
      * Mon-Fri days of week is used for this computation.
      * @return the averages indexed as in FlowEstimator
      */
     public float[] getDAVGs() {
          float[] vals = new float[C];
          for (int classI = 0; classI < C; classI++) {
              vals[classI] = this.getDAVG(classI);
          }
          return vals;
     }

      /**
      * Get Daily Average Vehicle count for a vehicle class.
      * Mon-Fri days of week is used for this computation.
     * @param classI the vehicle class
      * @return the average indexed as in FlowEstimator
      */
    public float getDAVG(int classI) {
        Dtime dt = FlowEstimator.A_FRIDAY;//dat or sun does not need to be 
        //taken into account in DAVG. 
        float sum =0;
         for (int h = 0; h < 24; h++) {
             int index = this.get1Dim_index(classI, dt, h);
             sum+= this.values[index];
         }
         return sum;
    }
    
      /**
      * Get Daily Average Vehicle counts for all vehicle classes.
      * @param dt Days of week is based on this for this computation.
      * @return the averages indexed as in FlowEstimator
      */
    public float[] getDAVGs_dow(Dtime dt) {
        float[] vals = new float[C];

        for (int h = 0; h < 24; h++) {
            for (int classI = 0; classI < C; classI++) {
                int index = get1Dim_index(classI, dt, h);
                vals[classI] += this.values[index];
            }//for classes
        }//for hours
        return vals;
    }

    /**
     * Fetch a single value in the compactArray that relates to the given
     * classIndex and time.
     *
     * @param classI the classIndex
     * @param dt time
     * @return stored vehicle flow amount
     */
    public float getValue(int classI, Dtime dt) {
        int ind = get1Dim_index(classI, dt,null);
        return this.values[ind];
    }

    /**
     * For the given vehicle class, multiply all hourly values by 'amp'.
     * @param amp the amplifier for all hourly values.
     * @param classI the class for which the multiplication is applied to.
     */
    public void multiplyAll(float amp, int classI) {
        multiplyValue(amp, classI, null,null);
    }

    public void multiplyValue(float amp, int classI, Dtime dt, Integer customH) {
        if (dt==null) {//multiply ALL values
            for (int h =0;h<T;h++) {
                int index = this.get1Dim_index(classI, h);
                this.values[index]*=amp;
            }
        } else {
            
           int index = this.get1Dim_index(classI, dt,customH);
           this.values[index]*=amp; 
        }
    }

    /**
     * Take the hourly flow data of another instance and sum them to this instance.
     * Scale all values afterwards.
     * @param flows values will be read from this.
     * @param afterScaler scale all values afterwards by this.
     */
    public void stack(SimpleFlowArray flows, float afterScaler) {
        for (int i =0;i<this.values.length;i++) {
            this.values[i]+=flows.values[i];
            this.values[i]*=afterScaler;
        }
    }
    
    /**
     * For the given time (h,dow counts), add the given hourly flow
     * to the existing hourly value.
     * @param add add amount
     * @param classI the vehicle class this summation is done.
     * @param dt the time (for h, doW)
     */
    public void sumValue(float add, int classI, Dtime dt) {
           int index = this.get1Dim_index(classI, dt,null);
           this.values[index]+=add;       
    }
    
     /**
     * For the given time (h,dow counts), replace the given hourly flow.
     * @param val replacement value
     * @param classI the vehicle class this summation is done.
     * @param dt the time (for h, doW)
     */
    public void setValue(float val, int classI, Dtime dt) {
         
           int index = this.get1Dim_index(classI, dt,null);
           this.values[index]=val;     
    }


    /**
     * Write the hourly flow values for the given time to a float[].
     * @param temp if null a new float[4] is created. 
     * @param dt time (for h, doW)
     * @return hourly flows for all vehicle classes.
     */
    public float[] getValues_allClasses(float[] temp, Dtime dt) {
        if (temp == null) {
            temp = new float[C];
        }
        for (int c =0;c<C;c++) {
            temp[c] = getValue(c, dt);
        }
        return temp;
    }

     /**
     * Write the hourly flow values for the given time to a float[].
     * This can read the next hour's data as well, and interpolate smoother
     * data in case the given minute-parameter does not equal zero.
     * 
     * @param temp if null a new float[4] is created. 
     * @param dt time (for h, doW)
     * @param next next local hour
     * @param mins minutes for traffic flow data (for interpolation)
     * @return smooth hourly flows for all vehicle classes.
     */
    public float[] getValues_allClasses_smooth(float[] temp,
            Dtime dt, Dtime next, int mins) {

        if (mins == 0 || next == null) {
            return getValues_allClasses(temp, dt);//it is smooth already
        }
        float secScaler = (float) mins / 60f;//e.g. if 59 then "sec" will get all the emphasis.
        float firstScaler = 1f - secScaler;//must sum up to 1 these two.

        if (temp == null) {
            temp = new float[C];
        }

        for (int i = 0; i < C; i++) {
            float first = getValue(i, dt);
            float sec = getValue(i, next);
            temp[i] = first * firstScaler + sec * secScaler;//smooth combination
        }//for classes
        return temp;
    }

    /**
     * For scale all vehicle class specific values so that the resulting
     * DAVG equals this given DAVG.
     * @param newDavg the target DAVG value
     * @param classI the vehicle class.
     */
    public void normalizeDAVGto(float newDavg, int classI) {
        float davg = this.getDAVG(classI);
        float amp = newDavg/davg;
        this.multiplyAll(amp, classI);
    }
  
}
