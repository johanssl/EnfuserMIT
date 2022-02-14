/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

/**
 * A data container that can be used for the creation of a PuffCarrier from
 * emitters.
 * @author johanssl
 */
public class PrePuff {
    
    public float[] Q;
    public float lat;
    public float lon;
    public float z;
    public String ID = null;
    public float secEvolve =0;
    public boolean canSplitToMiniPuffs=true;
    public int cat =-1;
    public boolean canMerge=true;
    public boolean usesMicroMetModule=false;
    public PrePuff(float[] Q, float lat, float lon, float z) {
        this.Q = Q;
        this.lat = lat;
        this.lon =lon;
        this.z = z;
    }
    
    public boolean hasCustomCategory() {
        return cat >=0;
    }
    public int cat() {
        return this.cat;
    }
}
