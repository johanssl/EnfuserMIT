/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.road;

import org.fmi.aq.essentials.gispack.osmreader.core.Building;
import org.fmi.aq.essentials.gispack.osmreader.core.Coordinate;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.RadListElement;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class RoadDefragmenter {

    /**
     * form a checksum based on the roads amount of lanes, speed limit and tier.
     * Roads that have identical similarity checksum can be considered "similar"
     *
     * The use case for this method is to avoid inserting small, fragmented
     * RoadPiece instances to the osmLayer, especially in urban central areas.
     *
     * @param rp the RoadPiece
     * @return integer checksum
     */
    public static int similarityCheckSum(RoadPiece rp) {

        int sum = rp.lanes * 10000 //min 10000
                + rp.speedLimit * 10 //max 1400?  
                + rp.tier();//max 4
        return sum;
    }

    public static boolean skipRoadAddtion(OsmLayer ol, int h, int w, RoadPiece rp) {

        if (rp.tunnel) {//special case for tunnels with buildings on top. This can be a conflict.
            Building b = ol.getBuilding(h, w);
            if (b != null) {
                return true;
            }
        }
        RoadPiece prev = ol.getRoadPiece(h, w);

        if (prev == null) {
            return false;//does not exist, addition should occur.
        }
        //previous exists
        if (rp.crudeDist_m < 50 && rp.tier() <= prev.tier()) {//same or smaller tier, and this one is a small fragment: SKIP.
            return true;
        }

        int prev_check = similarityCheckSum(prev);
        int check = similarityCheckSum(rp);

        if (check == prev_check) {//this pixel has a road already mapped and it is similar than this RP, SKIP.
            return true;
        } else {
            return false;
        }

    }

    public static void findConnectingSimilarRoads(RoadPiece rp, ArrayList<RoadPiece> temp, OsmLayer ol,
            HashMap<Integer, RoadPiece[]> rems, int dirTolerance, int dist) {

        temp.clear();
        if (rp.crudeLength_m() > dist) {
            return;
        }

        try {

            for (Coordinate c : rp.cc) {
                float lat1 = c.lat;
                float lon1 = c.lon;//OSMget.getRoad_EndPointCoordinate(rp, RP_START, RP_LON, ol); 

                int h = ol.get_h(lat1);
                int w = ol.get_w(lon1);

                for (RadListElement e : ol.radList_5m_100m.elems) {
                    if (e.dist_m > 11) {
                        break;//too far already (this analysis is very local) 
                    }
                    int ch = h + e.hAdd;
                    int cw = w + e.wAdd;

                    RoadPiece rpt = ol.getRoadPiece(ch, cw);
                    if (rpt == rp) {
                        continue;
                    }
                    if (rems != null && rems.get(rpt.ref_number_int) != null) {
                        continue; //a removable RoadPiece cannot work as base for removal
                    }
                    if (rpt.tier() == rp.tier()) {//same tier

                        byte dir = rpt.wayDir_5degs;
                        if (Math.abs(dir - rp.wayDir_5degs) < dirTolerance) {//same-ish direction
                            temp.add(rpt);
                            break;
                        }

                    }//if same tier

                }//for radList 1

            }//for start-end points 
        } catch (Exception e) {

        }
    }

    /**
     * Combine small RoadPiece fragments together.
     *
     * @param ol the layer.
     * @param dist roadPiece distance limit in meters. If larger, this method
     * skips the road.
     */
    public static void reduceFragmentation(OsmLayer ol, float dist) {

        if (ol.usePackedRefs) {
            EnfuserLogger.log(Level.FINER,RoadDefragmenter.class,"Road/building references are packed => unpacking for modifications.");
            ol.unpackReferences();
        }

        int fragments = 0;
        int canBeEliminated = 0;
        //int canBeEliminated_dir = 0;
        ArrayList<RoadPiece> temp = new ArrayList<>();
        EnfuserLogger.log(Level.FINER,RoadDefragmenter.class,"Current RoadPiece count = " + ol.allRPs().size());

        HashMap<Integer, RoadPiece[]> rems_old_new = new HashMap<>();
        int k = 0;
        for (RoadPiece rp : ol.allRPs().values()) {
            k++;
            if (k % 5000 == 0) {
                EnfuserLogger.log(Level.FINER,RoadDefragmenter.class,"Checking removable road piece fragments, k = " + k);
            }

            if (rp.crudeLength_m() > dist) {
                continue;
            }
            fragments++;

            try {

                findConnectingSimilarRoads(rp, temp, ol,
                        rems_old_new, 25, (int) dist);

                if (temp.size() >= 1) {//found removable!
                    RoadPiece rpt = temp.get(0);//take the first one (add modify to this

                    //add the removable end-start coordinates
                    Coordinate[] newCC = new Coordinate[4];
                    newCC[0] = rpt.cc[0];//copy startpoint of rpt
                    newCC[1] = rpt.cc[1];//copy startpoint of rpt
                    newCC[2] = rp.cc[0];//copy startpoint of rpt
                    newCC[3] = rp.cc[1];//copy startpoint of rpt
                    rpt.cc = newCC;

                    //traffic lights
                    if (rp.trafficLight_latlons != null) {//exists for rp
                        if (rpt.trafficLight_latlons == null) {//doesnt for rpt => copy
                            rpt.trafficLight_latlons = rp.trafficLight_latlons;
                        } else {//add, both exist
                            float[][] tempTr = new float[rp.trafficLight_latlons.length + rpt.trafficLight_latlons.length][];
                            int K = 0;
                            for (float[] tr : rp.trafficLight_latlons) {
                                tempTr[K] = tr;
                                K++;
                            }
                            for (float[] tr : rpt.trafficLight_latlons) {
                                tempTr[K] = tr;
                                K++;
                            }
                            rpt.trafficLight_latlons = tempTr;
                        }

                    }

                    rems_old_new.put(rp.ref_number_int, new RoadPiece[]{rp, rpt});
                    canBeEliminated++;
                }

            } catch (Exception e) {

            }

        }//for all rps
        EnfuserLogger.log(Level.FINER,RoadDefragmenter.class,
                "Fragments = " + fragments + ", of which can be eliminated: " + canBeEliminated);

        boolean forceAdd = true;
        HashMap<Integer, RoadPiece> newHash = new HashMap<>();
        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w++) {

                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp == null) {
                    continue;
                }

                //There is a road...check rem entry
                RoadPiece[] rps_old_new = rems_old_new.get(rp.ref_number_int);

                if (rps_old_new == null) {//this one will not be edited.
                    newHash.put(rp.ref_number_int, rp);

                } else {//this is a removable roadPiece, eliminate this (h,w)
                    RoadPiece rnew = rps_old_new[1];
                    ol.addRoadPiece(h, w, rnew, forceAdd);
                    newHash.put(rnew.ref_number_int, rnew);
                }

            }//for w
        }//for h

        ol.replaceRoadHash(newHash,rems_old_new);
        EnfuserLogger.log(Level.FINER,RoadDefragmenter.class,
                "Current RoadPiece count after cleanup = " + ol.allRPs().size());

    }

}
