/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import org.fmi.aq.enfuser.datapack.main.Observation;

/**
 *
 * @author johanssl
 */
public class VarConversion {

    public int typeInt;
    public String baseType;

    public String specialTypeName;
    public float[] scaler_offset;

    public VarConversion(String specType, int typeInt, String baseType, double scl, double add) {
        this.baseType = baseType;
        this.typeInt = typeInt;
        this.specialTypeName = specType;

        this.scaler_offset = new float[]{(float) scl, (float) add};

    }

    public void applyScaleOffset(Observation o) {
        if (o.unitScaled) return;
        float val = o.value * this.scaler_offset[0] + this.scaler_offset[1];
        o.value=val;
        o.unitScaled=true;

    }
    
    public float applyScaleOffset(float f) {
        return f * this.scaler_offset[0] + this.scaler_offset[1];
    }

}
