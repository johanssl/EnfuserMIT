/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author johanssl
 */
public class Node {

    HashMap<String, String> tags = new HashMap<>();
    public final static char q = '"';
    public final static String Q = q + "";


    public Node(ArrayList<String> arr, OsmTempPack op) {
        /* </node>
  <node id="25274389" lat="60.2150582" lon="24.8584961" version="2" timestamp="2011-08-13T21:41:01Z" changeset="9010090" uid="139957" user="ij_"/>
  <node id="25279200" lat="60.2057964" lon="24.8611078" version="3" timestamp="2011-08-11T22:46:08Z" changeset="8990680" uid="139957" user="ij_"/>
  <node id="25279201" lat="60.2054609" lon="24.8608952" version="4" timestamp="2011-08-12T20:34:07Z" changeset="8999718" uid="139957" user="ij_"/>
  <node id="25279208" lat="60.2064459" lon="24.8641236" version="5" timestamp="2011-08-12T19:53:53Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279209" lat="60.2063005" lon="24.8659068" version="5" timestamp="2011-08-12T19:52:48Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279211" lat="60.2069929" lon="24.8659675" version="2" timestamp="2011-08-12T19:52:22Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279212" lat="60.2069927" lon="24.8679918" version="3" timestamp="2011-08-12T19:53:02Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279213" lat="60.2069209" lon="24.8692971" version="2" timestamp="2011-08-12T19:51:46Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279214" lat="60.2066905" lon="24.8700168" version="3" timestamp="2011-08-12T19:52:44Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279215" lat="60.2059692" lon="24.8704137" version="2" timestamp="2011-08-12T19:51:04Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279216" lat="60.2055274" lon="24.8708080" version="3" timestamp="2011-08-12T19:52:13Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279218" lat="60.2051548" lon="24.8718702" version="3" timestamp="2011-08-12T19:51:39Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279219" lat="60.2042759" lon="24.8722079" version="2" timestamp="2011-08-12T19:53:20Z" changeset="8999287" uid="139957" user="ij_"/>
  <node id="25279221" lat="60.2085184" lon="24.8626220" version="2" timestamp="2013-03-01T09:38:37Z" changeset="15206478" uid="38239" user="Daeron"/>
  <node id="25279222" lat="60.2085298" lon="24.8635539" version="1" timestamp="2007-01-17T17:47:30Z" changeset="196747" uid="3855" user="Liimes">
    <tag k="created_by" v="JOSM"/>
  </node>   
         */
        String[] temp;
        ArrayList<Long> refs = new ArrayList<>();
        //EnfuserLogger.log(Level.FINER,this.getClass(),"New Node:");
        for (String line : arr) {
            line = line.replace(Q, "");

            temp = line.split(" ");
            if (temp[0].contains("node")) {//not a tag

                String id = temp[1].replace("id=", "");
                String lat = null;
                String lon = null;
                for (String s : temp) {
                    if (lat == null && s.contains("lat=")) {
                        lat = s.replace("lat=", "");
                    }
                    if (lon == null && s.contains("lon=")) {
                        lon = s.replace("lon=", "");
                    }
                }
                //EnfuserLogger.log(Level.FINER,this.getClass(),"\tadding "+id +", "+lat +", "+lon);
                double latd = Double.valueOf(lat);
                double lond = Double.valueOf(lon);
                long ref = Long.valueOf(id);
                double[] coord = {latd , lond};
                refs.add(ref);
                op.addNode(ref, coord);


            } else if (temp[0].contains("tag")) {
                String tag = temp[1].replace("k=", "");
                String value = temp[2].replace("v=", "");
                value = value.replace("/>", "");
                if (tag.equals("comment") || tag.equals("note") || tag.contains("created_by")) {

                } else {
                    this.tags.put(tag, value);
                }
                // if (value.contains("signals"))EnfuserLogger.log(Level.FINER,this.getClass(),"\t tag: "+tag +" = "+value);
            }

        }//for arr

        if (!this.tags.isEmpty()) {
            op.addPOI(refs, tags);
            if (tags.get("place") != null) {
                op.addPlace(refs, tags);
            }
        }

    }

}
