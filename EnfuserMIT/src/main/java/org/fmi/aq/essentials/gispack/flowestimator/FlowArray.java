/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import org.fmi.aq.essentials.date.Dtime;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.A_FRIDAY;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.FRI_SAT_SUN;
import static org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator.VC_INDS;
import java.io.Serializable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public final class FlowArray extends TemporalArray implements Serializable {

    private static final long serialVersionUID = 7526472194622776447L;//Important: change this IF changes are made that breaks serialization!

    public FlowArray(String source, ArrayList<Dtime> dates, ArrayList<Float> vals,
            ArrayList<Integer> classes) {
        this.source = source;
        int C = FlowEstimator.VEHIC_CLASS_NAMES.length;
        int W = TemporalDef.WKDS_NAMES.length;
        int D = TemporalDef.DIURNALS;
        this.values = new float[C * W * D];
        boolean set[] = new boolean[this.values.length];

        this.classLen = C;
        this.initAmps();
        //setup values
        for (int i = 0; i < dates.size(); i++) {
            Dtime dt = dates.get(i);
            int classI = classes.get(i);
            float val = vals.get(i);

            int ind = get1Dim_index(classI, dt);

            this.values[ind] = val;
            set[ind] = true;
                EnfuserLogger.log(Level.FINER,this.getClass(),
                        "TIME; " + dt.getStringDate() + ";  index=; " + ind 
                                + "; values ;" + this.getValue(classI, dt) + ";" + val);
            
        }

        //check set percentage
        float percentsPerHit = 100f / this.values.length;
        float counter = 0;
        for (int i = 0; i < values.length; i++) {
            if (set[i]) {
                counter += percentsPerHit;
            }
        }

        this.valuesSet_percentage = (byte) (counter);
        EnfuserLogger.log(Level.FINER,this.getClass(),"Value set percentage = " + this.valuesSet_percentage + "%");
        
        this.normalizeToDAVGs();

    }

    public FlowArray(String source, float[] prevalues, float[] davgs) {
        int C = FlowEstimator.VEHIC_CLASS_NAMES.length;
        int W = TemporalDef.WKDS_NAMES.length;
        int D = TemporalDef.DIURNALS;
        this.source = source;
        this.classLen = C;
        int len = C * W * D;
        this.initAmps();
        //setup values

        this.values = new float[len];
        for (int i = 0; i < prevalues.length; i++) {
            values[i] = prevalues[i];
        }//for prevals

        this.valuesSet_percentage = (byte) (100f * (float) prevalues.length / len);

        for (int c = 0; c < classLen; c++) {//copy also these
            float setAmp = 1f;
            if (davgs != null) {
                setAmp = davgs[c];
            }
            this.editClassAmplifier_davg(c, setAmp, FlowEstimator.EDIT_SET);
        }
        this.normalizeToDAVGs();
    }

    private void normalizeToDAVGs() {
        //make sure class amplifiers are on

        for (int c : VC_INDS) {

            float h24Sum = 0;
            for (int h = 0; h < 24; h++) {
                h24Sum += this.getUnscaledValue(c, A_FRIDAY, h);
            }
            if (h24Sum <= 0) {
                continue;//has NO temporal params and would result in DivByZero.
            }
            //this sum should be equal to 1 for this class.
            float scaler = 1f / h24Sum; //e.g. if h24Sum is 220 then all values need to be divided by 20.

            for (Dtime dt : FRI_SAT_SUN) {
                for (int h = 0; h < 24; h++) {

                    int index = this.get1Dim_index(c, dt, h);
                    this.values[index] *= scaler;

                }
            }

            //if (adjustClassScalers) {
            float amp = 1f / scaler; //adjust the user modifiable part to counter the modifications done to temporal part
            editClassAmplifier_davg(c, amp, FlowEstimator.EDIT_MULTI);
            //}//if classScalers

        }//for c
    }

    public static int getDimLength_classless() {
        return TemporalDef.DIURNALS * TemporalDef.WKDS_NAMES.length;
    }

    public static int get1Dim_index_classless(Dtime dt) {
        int wkdi = 0;
        if (dt.dayOfWeek == Calendar.SATURDAY) {
            wkdi = 1;
        } else if (dt.dayOfWeek == Calendar.SUNDAY) {
            wkdi = 2;
        }

        int index = dt.hour + wkdi*TemporalDef.DIURNALS;
        return index;
    }

    @Override
    public int get1Dim_index(int classI, Dtime dt) {
        int wkdi = 0;
        if (dt.dayOfWeek == Calendar.SATURDAY) {
            wkdi = 1;
        } else if (dt.dayOfWeek == Calendar.SUNDAY) {
            wkdi = 2;
        }

        //int index = dt.hour + wkdi*DIURNALS + classI*DIURNALS*WKDS;
        //example: hour = 0, wkdi =0, class = 0
        // => 0 + 0*24 + 0*24*3 =0; the first element.
        //example 2: hour = 23 (last), wkdi = 2 (last), class =3 (the last one)
        // => 23 + 2*24 + 3*24*3 = 287, which is the LAST element since the length is 24*3*4 = 288
        int index = classI + dt.hour * classLen + wkdi * TemporalDef.DIURNALS * classLen;
        return index;
    }

    @Override
    public int get1Dim_index(int classI, Dtime dt, int overrideH) {

        int wkdi = 0;
        if (dt.dayOfWeek == Calendar.SATURDAY) {
            wkdi = 1;
        } else if (dt.dayOfWeek == Calendar.SUNDAY) {
            wkdi = 2;
        }

        //int index = overrideH + wkdi*DIURNALS + classI*DIURNALS*WKDS;
        int index = classI + overrideH * classLen + wkdi * TemporalDef.DIURNALS * classLen;
        return index;
    }

    public FlowArray(String source, ArrayList<Dtime> dates, ArrayList<float[]> vals, boolean print) {
        this.source = source;
        int C = FlowEstimator.VEHIC_CLASS_NAMES.length;
        int W = TemporalDef.WKDS_NAMES.length;
        int D = TemporalDef.DIURNALS;
        this.values = new float[C * W * D];
        boolean set[] = new boolean[this.values.length];

        this.classLen = C;
        this.initAmps();
        //setup values
        for (int i = 0; i < dates.size(); i++) {
            Dtime dt = dates.get(i);
            float[] valA = vals.get(i);
            for (int classI = 0; classI < classLen; classI++) {

                float val = valA[classI];
                int ind = get1Dim_index(classI, dt);

                this.values[ind] = val;
                set[ind] = true;
                if (print) {
                    EnfuserLogger.log(Level.FINER,this.getClass(),"VALUE_TEST;" + val + "; => ;" + this.getValue(classI, dt) + ";");
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

        this.valuesSet_percentage = (byte) (counter);
        if (print) {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Value set percentage = " + this.valuesSet_percentage + "%");
        }
        this.normalizeToDAVGs();
    }

    public void saveToBinary(String fileName) {

        EnfuserLogger.log(Level.FINER,this.getClass(),"Saving FlowArray to: " + fileName);
        // Write to disk with FileOutputStream
        FileOutputStream f_out;
        try {
            f_out = new FileOutputStream(fileName);

            // Write object with ObjectOutputStream
            ObjectOutputStream obj_out;
            try {
                obj_out = new ObjectOutputStream(f_out);

                // Write object out to disk
                obj_out.writeObject(this);
                obj_out.close();
                f_out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }

    }


    @Override
    protected int getSecondaryScalerIndex(int classI, Dtime dt) {
        //in this class a monthly modifier can be introduced
        return 12 * this.classLen + dt.month_011;
    }

    @Override
    public void setSecondaryScaler(int classI, Dtime dt, float amp) {
        if (this.secondaryScalers == null) {
            this.secondaryScalers = new float[12 * this.classLen];
            for (int i = 0; i < this.secondaryScalers.length; i++) {
                this.secondaryScalers[i] = 1f;
            }
        }

        int ind = getSecondaryScalerIndex(classI, dt);
        this.secondaryScalers[ind] = amp;

    }

    public float[] rawValues() {
        return this.values;
    }

    void stackScalers(FlowArray flows, float afterScaler) {
        for (int c : FlowEstimator.VC_INDS) {
            this.primaryScalers[c] += flows.primaryScalers[c];//ignores user defined part.
            this.primaryScalers[c] *= afterScaler;
        }
    }

    void editTemporal(int classI, Dtime dt, int hour, float hourlyAmp) {
        int ind = this.get1Dim_index(classI, dt, hour);
        this.values[ind] *= hourlyAmp;
    }

    public FlowArray hardCopy() {
        FlowArray copy = new FlowArray(this.source, this.values, null);
        copy.amps = new float[this.amps.length];
        for (int i = 0; i < this.amps.length; i++) {
            copy.amps[i] = this.amps[i];
        }

        copy.primaryScalers = new float[this.primaryScalers.length];
        for (int i = 0; i < this.primaryScalers.length; i++) {
            copy.primaryScalers[i] = this.primaryScalers[i];
        }

        if (this.secondaryScalers != null) {
            copy.secondaryScalers = new float[this.secondaryScalers.length];
            for (int i = 0; i < this.secondaryScalers.length; i++) {
                copy.secondaryScalers[i] = this.secondaryScalers[i];
            }
        }
        copy.valuesSet_percentage = this.valuesSet_percentage;
        return copy;
    }

}
