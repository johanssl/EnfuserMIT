/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.plotterlib.animation;

import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class GridInterpolator {

    private float[][][] dat;
    int T;
    int H, W;
    int interpLength_T;
    int IF;//interpolation factor   

    final boolean singular;
    String[] times_interpolated;

    float[][] indexing;
    public boolean cannotAnim =false;
    public GridInterpolator(float[][][] dat, VisualOptions ops,
            int secondsBetween, Dtime firstDt) {

        this.dat = dat;
        this.T = dat.length;
        this.H = dat[0].length;
        this.W = dat[0][0].length;

        this.IF = ops.vid_interpolationFactor;

        if (T == 1) {//nothing to interpolate
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                    "Animation: grid has only one temporal layer => "
                            + "no interpolation and no video.");
            ops.vid_interpolationFactor = 1;
            cannotAnim=true;
            this.interpLength_T = T;
            singular = true;
            times_interpolated = new String[]{firstDt.getStringDate(Dtime.STAMP_NOZONE)};

        } else {//regular scenario

            singular = false;
            this.interpLength_T = T * IF - (IF - 1);//why -(SCALER-1) : because this is interpolation and not extrapolation. For the last index i there is not going to be i+1, i+2 or i+3   

            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "Anim.interpolation: creating interpolated layers. ListSize =" + T);
            int secondsAdd = (int) (secondsBetween / IF);
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "Animation.getTimes: seconds to be added every frame: " 
                            + secondsAdd + " (" + (int) (secondsAdd / 60) + "min)");

            this.times_interpolated = new String[interpLength_T];
            Dtime dt = firstDt.clone();

            for (int i = 0; i < times_interpolated.length; i++) {
                times_interpolated[i] = dt.getStringDate(Dtime.STAMP_NOZONE);
                EnfuserLogger.log(Level.FINER,this.getClass(),"Frame dt: " 
                        + i + " = " + times_interpolated[i]);
                
                dt.addSeconds(secondsAdd);

            }

        }

        indexing = new float[interpLength_T][4];

        for (int t = 0; t < interpLength_T; t++) {

            int mod = t % IF;

            int prev = t / IF;
            int next = t / IF + 1;
            if (mod == 0) {//no need to interpolate
                next = prev;
                indexing[t] = new float[]{prev, next, 1f, 0f};
            } else {// in between => interpolate

                float next_fac = (float) mod / IF;
                float prev_fac = 1f - next_fac;
                //example: IF is 6, t = 11:
                //mod is then 5, next_fac = 0.833 and prev_fac = 0.167. prev = 1, next = 2.
                //example 2: IF is still 6, t = 12
                // mod is then 0 => prev = 2, next = 2, prev_fac =1f.
                indexing[t] = new float[]{prev, next, prev_fac, next_fac};
            }

        }

        EnfuserLogger.log(Level.FINER,this.getClass(),
                "Anim.interpolation: creating interpolated layers complete."
                        + " ListSize now =" + indexing.length);

    }

    public float[][] getInterpolated(int interp_t) {

        if (this.singular || interp_t == 0) {
            return this.dat[0];//no processing is needed
        } else if (interp_t == this.interpLength_T - 1) {//last one => last original, no processing needed
            return this.dat[T - 1];

        } else {

            float[][] idat = new float[H][W];

            float[] info = this.indexing[interp_t];
            int prev = (int) info[0];
            int next = (int) info[1];

            float fac_prev = info[2];
            float fac_next = info[3];

            for (int h = 0; h < H; h++) {
                for (int w = 0; w < W; w++) {
                    float prevVal = dat[prev][h][w];
                    float nextVal = dat[next][h][w];

                    idat[h][w] = prevVal * fac_prev + nextVal * fac_next; // calculate interpolated value

                }
            }

            return idat;
        }

    }

    String getTimeInterpolated(int i) {
        return this.times_interpolated[i];
    }

}
