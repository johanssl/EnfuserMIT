/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

/**
 *
 * @author johanssl
 */
public class ProxyBuilding extends Building {

    public final int representatives;

    public ProxyBuilding(int slot, int reps, Short lineLen, short area_m2,
            byte floors, byte type, int ol_h, int ol_w) {
        
        super(lineLen, area_m2, floors, type, slot, ol_h, ol_w);
        this.representatives = reps;
        this.IDint = slot;
        this.replacable = false;//a replacement cannot be replaced.
    }

}
