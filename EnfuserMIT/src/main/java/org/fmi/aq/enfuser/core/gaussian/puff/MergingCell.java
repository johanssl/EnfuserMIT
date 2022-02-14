/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.util.ArrayList;

/**
 * This class represent a single cell in the MergingPool.
 * Puffs can be added to it and then the merging process can occur
 * based on the content held by this cell.
 * @author johanssl
 */
public class MergingCell {
    
    ArrayList<PuffCarrier> content = new ArrayList<>();
    public final static int MERGING_N_LIMIT =2;
    public MergingCell() {
        
    }

    void clear() {
      this.content.clear();
    }

    void add(PuffCarrier p) {
        this.content.add(p);
    }

    /**
     * Return true if merging should occur with the content held by this cell.
     * @return 
     */
    boolean hasMergableContent() {
        return this.content.size()>MERGING_N_LIMIT;
    }

    /**
     * Merge the content held by this cell into super puffs
     * and add them to the list given.
     * @param all the list where merged puffs are added to.
     * @param ppf the platform
     */
    int mergeAndAdd(ArrayList<PuffCarrier> all, PuffPlatform ppf) {
        //TODO add a more intelligent sorting here and possible create
        //several merged puffs.
        PuffCarrier p = PuffMerger.merge(this.content, ppf);
        all.add(p);
        return content.size();
    }
    
    /**
     * add the content held by this cell to the list given.
     * @param puffs the list.
     */
    protected int flushUnmergedContentTo(ArrayList<PuffCarrier> puffs) {
        if (this.content.isEmpty())return 0;
        puffs.addAll(this.content);
        return this.content.size();
    }
    
}
