/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import org.fmi.aq.enfuser.customemitters.AbstractEmitter;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import java.util.ArrayList;
import org.fmi.aq.enfuser.options.Categories;

/**
 *
 * @author johanssl
 */
public class Injector {

public static int CATEGORY_LIMITER =-1;    
    public static ArrayList<PuffCarrier> getInjects(Dtime last, Dtime next, PuffPlatform pf) {
        
        ArrayList<PuffCarrier> arr = new ArrayList<>();
        int secs = (int) (next.systemSeconds() - last.systemSeconds());
        Dtime[] allDates = pf.ens.tzt.getDates(last);

        //CUSTOM POINT EMITTERS=============================
        ArrayList<AbstractEmitter> aems = AbstractEmitter.shortListRelevant(pf.ens.ages, next,
                allDates, pf.ens.ops.boundsMAXexpanded(), AbstractEmitter.OPERM_INDIV, pf.ens);
        
        for (AbstractEmitter ae : aems) {
           if (addable(ae.cat)) {
               ae.getPuffs(last, (float) secs, pf, allDates, pf.ens, arr);
           }
        }

        //Map (ENV) puffs==========================
        if (pf.mapPuffs != null) {
            if (addable(Categories.CAT_TRAF)) {
                ArrayList<PuffCarrier> mps = pf.mapPuffs.getPuffsUpdate(pf,last);
                arr.addAll(mps);
            }
        }//if map sources     

        if (pf.regionalBg!=null) {
            if (addable(Categories.CAT_BG))  pf.regionalBg.addMarkersToArray(pf, last, arr);
        }
      
        return arr;
    }

    private static boolean addable(int cat) {
        if (CATEGORY_LIMITER <0) {
            return true;
        }else if (CATEGORY_LIMITER == cat) {
            return true;
        } else {
           return false;  
        }
       
    }
    
    public static MarkerCarrier getBgQ(PuffPlatform pf, FusionCore ens,
            Dtime last, float[] latlon, boolean errs) {

        float[] Q = new float[ens.ops.VARA.Q_NAMES.length];
        float lat = latlon[0];
        float lon = latlon[1];
        int succs = 0;

        for (int i = 0; i < Q.length; i++) {
            try {
                Q[i] = ens.getModelledBG(i, last, lat, lon, false);//false for non-orthogonal anthropogenic reduction
                succs++;
            } catch (Exception e) {

            }
        }//for pollutant types  

        if (succs == 0) {
            return null;
        }

        float[] yx = Transforms.latlon_toMeters(lat, lon, pf.in.bounds);
        float[] hyx = {0, yx[0], yx[1]};
        MarkerCarrier bgp = new MarkerCarrier(pf.ens, last, hyx, Q, latlon, "BG");
        return bgp;
        //pf.add(bgp);

    }

}
