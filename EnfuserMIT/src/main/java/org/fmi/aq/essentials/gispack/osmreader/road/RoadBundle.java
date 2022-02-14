/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.road;

import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class RoadBundle {

    public static void bundleLesserRoads(OsmLayer ol, int resDiv) {
        if (ol.usePackedRefs) {
            ol.unpackReferences();
        }
        if (resDiv < 10) {
            resDiv = 10;
        }
        if (resDiv > 200) {
            resDiv = 200;
        }
        EnfuserLogger.log(Level.FINER,RoadBundle.class,
                "Bundle lesser roads: distributing lesser roads into " + ol.resm * resDiv 
                        + "m x " + ol.resm * resDiv + "m bundles.");
        HashMap<Integer, RoadPiece>[][] rpGrid = new HashMap[ol.H / resDiv][ol.W / resDiv];
        int[][] championRef = new int[ol.H / resDiv][ol.W / resDiv];

        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w++) {
                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp == null) {
                    continue;
                }
                if (!RoadPiece.isLesserRoad(rp)) {
                    continue;
                }
                int nh = h / resDiv;
                int nw = w / resDiv;

                try {

                    if (rpGrid[nh][nw] == null) {
                        rpGrid[nh][nw] = new HashMap<>();
                    }
                    rpGrid[nh][nw].put(rp.ref_number_int, rp);
                    championRef[nh][nw] = rp.ref_number_int;//choose a champion (any will do)
                } catch (Exception e) {

                }
            }//for w
        }//for h

        //for each cell, select a single road piece to represent them all!
        EnfuserLogger.log(Level.FINER,RoadBundle.class,"Second iteration phase...");
        HashMap<Integer,Integer> old_new=new HashMap<>();
        int switches = 0;
        for (int h = 0; h < ol.H; h++) {
            if (h % 1000 == 0) {
                EnfuserLogger.log(Level.FINER,RoadBundle.class,"H=" + h);
            }
            for (int w = 0; w < ol.W; w++) {
                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp == null) {
                    continue;
                }
                if (!RoadPiece.isLesserRoad(rp)) {
                    continue;
                }

                try {
                    int nh = h / resDiv;
                    int nw = w / resDiv;
                    HashMap<Integer, RoadPiece> hash = rpGrid[nh][nw];
                    if (hash == null) {
                        continue;
                    }
                    //if we get here then we'll do a replacement
                    RoadPiece champion = hash.get(championRef[nh][nw]);//by definition this exists in the hash
                    if (rp.ref_number_int == champion.ref_number_int) {
                        continue;//very special case, this is the champion
                    }
                    boolean forceAdd = true;
                    
                        if (rp.ref_number_int!=champion.ref_number_int) {
                            old_new.put(rp.ref_number_int, champion.ref_number_int);
                        }   
                    
                    ol.addRoadPiece(h, w, champion, forceAdd);
                    switches++;
                } catch (IndexOutOfBoundsException e) {

                }
            }//for w
        }//for h

        EnfuserLogger.log(Level.FINER,RoadBundle.class,"Complete: number of cell-switches done: " + switches);
        ol.removeObsoleteElementKeys(old_new);

    }

}
