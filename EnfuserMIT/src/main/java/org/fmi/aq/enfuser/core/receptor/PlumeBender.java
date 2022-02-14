/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import static org.fmi.aq.enfuser.core.receptor.BlockAnalysis.getIndex;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class PlumeBender {
    private final static int MIRROR = 1;
    private final static int DEF = 0;

    public final static float MIRROR_DIST_MAX = 60f;
    public final static float MIN_MIRROR_STREN = 0.05f;

    
    private float[] dir;
    private float[] w;
    boolean mirrored = false;
   public PlumeBender() {
       dir = new float[2];
       w = new float[2];
   }
   
   public void reset(float wdir) {
       w[MIRROR]=0;
       dir[MIRROR]=0;
       
       dir[DEF]=wdir;
       w[DEF]=1f;
       mirrored=false;
   }
   
 /**
     * In urban environment the Gaussian emission footprint needs to bend
     * sometimes (for example in street canyons).This method attempts to create
 another "mirrored" footprint opposite to the wind's direction in case the
 urban landscape so suggests. The Analysis is based on the local urban structures: buildings, the
 distances and their heights. As a rule of thumb, in case there is a
 significantly sized "blocker" towards the wind direction then the
 mirrored emission footprint is introduced. The mirrored footprint gains
 more impact in case there is another strong blocker in the opposite
 direction as well ( a street canyon).
     *
     * @param ba BlockAnalysis that has the local environment scanned.
     * @param wd prevailing wind direction
     * @param edist_m FpmCell evaluation distance - also plays a part,
     * especially in half-open street canyons. In essence, the larger this
     * distance is, the lesser value will be given to the mirror.
     * @param ops FusionOptions that contain strength scalers for this method
     * (mirrorAmplifiers)
     */
    public void setWindReversals(BlockAnalysis ba, float wd,
            float edist_m, FusionOptions ops) {

        //reset reversed. Also: mirrored=false; 
        reset(wd);
        if (ba == null || ops.blockerStrength<=0 || edist_m > MIRROR_DIST_MAX ) {
            return;
        }

        int sec = getIndex(wd);
        float base = 0.011f * ba.BLOCKSTR[sec];// between [0,1]. Gets value of 1 if there's a very sizable blocker
        //how about the opposing side?
        float opposite =  0.02f * ba.BLOCKER_OP[sec];//between [0,1]
        float effectDistance = 5+ Math.max(10, MIRROR_DIST_MAX*opposite);
        
        base*=(effectDistance-edist_m)/effectDistance;
        base*=ops.blockerStrength;
        setMirrorWeight(base,ops);
        
    }   

    private void setMirrorWeight(float base, FusionOptions ops) {
         if (base <MIN_MIRROR_STREN) {
             mirrored=false;
             return;
         }//weight would be so small, not worth it
        mirrored = true;
        
        if (base>1f) base =1f;
        float wd = this.dir[DEF];
        float mwd = wd + 180;
            if (mwd > 360) {
                mwd -= 360;
            }
        this.dir[MIRROR]=mwd;
        this.w[MIRROR]=base;
        this.w[DEF] = Math.max(0.3f, 1f -base);
        
    }
    
    private final static boolean[] DEFB_MIRR= {false,true};
    private final static boolean[] DEFB= {false};
    public boolean[] mirrors() {
       if (this.mirrored) return DEFB_MIRR;
       return DEFB;
    }


    public float getStrength(boolean mirror) {
        if (mirror) return this.w[MIRROR];
        return this.w[DEF];
    }
    
    public float getWD(boolean mirror) {
        if (mirror) return this.dir[MIRROR];
        return this.dir[DEF];
    }
    
}
