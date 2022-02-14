/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.shg;

import java.io.Serializable;

/**
 *
 * @author johanssl
 */
public class Container implements Serializable {

    private static final long serialVersionUID = 7522772293822776147L;//Important: change this IF changes are made that breaks serialization! 
    protected float[] values;

    public Container(float[] values) {
        //super(values);
        this.values = values;
    }

    public void sumOrCreateContent(float[] vals, boolean forceNew) {

        if (forceNew) {
            this.values = vals;
        } else {
            for (int i = 0; i < vals.length; i++) {
                values[i] += vals[i];
            }
        }

    }

    public float[] values(SparseHashGrid hg) {
        return this.values;
    }

}
