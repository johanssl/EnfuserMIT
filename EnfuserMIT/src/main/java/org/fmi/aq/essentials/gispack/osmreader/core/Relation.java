/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Node.Q;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class Relation {

    HashMap<String, String> tags = new HashMap<>();

    ArrayList<Way> members = new ArrayList<>();
    ArrayList<Boolean> outers = new ArrayList<>();
    public long relID;

    boolean corrupt = false;

    public Relation(ArrayList<String> arr, OsmTempPack db) {
        /*
    <relation id="2530151" version="1" timestamp="2012-10-26T20:06:42Z" changeset="13642778" uid="523520" user="Pilkku">
    <member type="way" ref="187842045" role="outer"/>
    <member type="way" ref="187842062" role="outer"/>
    <member type="way" ref="187842064" role="outer"/>
    <member type="way" ref="87499264" role="outer"/>
    <member type="way" ref="187842082" role="outer"/>
    <member type="way" ref="87499283" role="inner"/>
    <tag k="landuse" v="forest"/>
    <tag k="type" v="multipolygon"/>
  </relation>
         */

        String[] temp;
        for (String line : arr) {
            line = line.replace(Q, "");

            temp = line.split(" ");
            if (temp[0].contains("relation")) {

                String id = temp[1].replace("id=", "");
                this.relID = Long.valueOf(id);
                
            } else if (temp[1].contains("type=way")) {//read member way
                String ref = temp[2].replace("ref=", "");

                //multipolygon special
                try {
                    String outer = temp[3].replace("role=", "");
                    outer = outer.replace("/>", "");
                    boolean outerB = true;
                    if (outer.equals("inner")) {
                        outerB = false;
                    } else if (outer.equals("outer")) {
                        outerB = true;
                    }
                    this.outers.add(outerB);
                } catch (Exception eee) {
                    corrupt = true;
                };

                long way_id = Long.valueOf(ref);
                Way way = db.getWay(way_id);
                if (way == null) {
                    corrupt = true;
                } else {
                    this.members.add(way);
                }

            } else if (temp[0].contains("tag")) {
                String tag = temp[1].replace("k=", "");
                String value = temp[2].replace("v=", "");
                value = value.replace("/>", "");
                this.tags.put(tag, value);
            }

        }//for lines

    }

    public String getBusLine() {

        // <tag k="ref" v="3"/>
        // <tag k="route" v="bus"/>
        String bus = this.tags.get("route");
        if (bus != null && bus.equals("bus")) {
            return (tags.get("ref"));
        }
        return null;
    }

    public boolean multiPolygon() {

        if (this.outers.isEmpty() || corrupt) {
            return false;//is not a legit mp
        }
        String tagvalue = this.tags.get("type");
        if (tagvalue != null && tagvalue.equals("multipolygon")) {
            return true;
        } else {
            return false;
        }
    }


    public ArrayList<Way> processMultiPoly() {
        
        EnfuserLogger.log(Level.FINER,this.getClass(),"PROCESSING MULTIPOLY:");
        ArrayList<Way> arr = new ArrayList<>();

        ArrayList<PolyCombo> pols = new ArrayList<>();
        pols.add(new PolyCombo());

        for (int k = 0; k < this.members.size(); k++) {
            Way member = this.members.get(k);
            boolean outer = this.outers.get(k);//is this member outer? or inner
                EnfuserLogger.log(Level.FINER,this.getClass(),member.wayID + " is outer = " + outer);
            
            //check for completedness
            PolyCombo pol = pols.get(pols.size() - 1);//the latest
            if (pol.startNew(outer)) {// this combo is FINISHED
                    EnfuserLogger.log(Level.FINER,this.getClass(),"\t outer is complete => start a new one.");
                
                Way way = new Way();
                way.tags = this.tags;
                way.coords = pol.coords_outer;
                if (!pol.innards.isEmpty()) {
                    way.innards = pol.innards;
                }
                way.wayID = this.relID;
                way.initType(true);
                if (pol.isAcceptable( false)) {
                    arr.add(way);
                }

                pols.add(new PolyCombo());
            }

            pol = pols.get(pols.size() - 1);
            pol.add(member, outer);

        }//for members

        //finally check for completedness
        PolyCombo pol = pols.get(pols.size() - 1);
        if (pol.isAcceptable( false)) {// this combo is FINISHED
            Way way = new Way();
            way.tags = this.tags;
            way.coords = pol.coords_outer;
            way.innards = pol.innards;
            way.wayID = this.relID;
            way.initType(true);
            arr.add(way);
        }

        return arr;
    }

}
