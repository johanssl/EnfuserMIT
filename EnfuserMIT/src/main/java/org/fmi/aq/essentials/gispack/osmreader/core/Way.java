/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import org.fmi.aq.enfuser.ftools.FileOps;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Node.Q;
import static org.fmi.aq.essentials.gispack.osmreader.core.OsmTempPack.HWAY_SIGNALS;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.LU_UNDEF;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.NATURAL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.RAILWAY;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;

/**
 *
 * @author johanssl
 */
public class Way implements Serializable{
    private static final long serialVersionUID = 7426472293813776147L;//Important: change this IF changes are made that breaks serialization! 
    public HashMap<String, String> tags = new HashMap<>();
    public final static int NTYPE_POINT = 0;
    public final static int NTYPE_LINE = 1;
    public final static int NTYPE_AREA = 2;
    public int ntype;

    public ArrayList<double[]> coords = new ArrayList<>();
    public ArrayList<Long> nodeRefs = new ArrayList<>();
    public long wayID;

    public ArrayList< ArrayList<double[]>> innards;
    public boolean multiPoly = false;
    public ArrayList<String> buslines = new ArrayList<>();
    public ArrayList<float[]> trLights_latlon = null;
    public POI poi = null;

    public Way() {

    }

    public static Way fromKml(File f) {

        String[] nameSplit = f.getName().split("_");
        String tag = nameSplit[1];
        String val = nameSplit[2];
        String id = nameSplit[3];
        id = id.replace(".kml", "");

        Way way = new Way();
        way.tags.put(tag, val);
        way.wayID = Integer.valueOf(id);

        ArrayList<String> arr = FileOps.readStringArrayFromFile(f.getAbsolutePath());
        String content = "";
        for (String s : arr) {
            content += s;
        }

        String coords = getBetween(content, "<coordinates>", "</coordinates>");

        String[] c_split = coords.split(" ");
        for (String cc : c_split) {
            String[] lonlat = cc.split(",");
            double lond = Double.valueOf(lonlat[0]);
            double latd = Double.valueOf(lonlat[1]);
            EnfuserLogger.log(Level.FINER,Way.class,latd + ", " + lond);
            double[] coord = {latd , lond};
            way.coords.add(coord);
        }

        way.initType(false);

        EnfuserLogger.log(Level.FINER,Way.class,"Created a Way from kml:");
        EnfuserLogger.log(Level.FINER,Way.class,"\t " + tag + " = " + val);
        EnfuserLogger.log(Level.FINER,Way.class,"\t coordsSize " + way.coords.size() + ", NTYTPE = " + way.ntype);

        return way;
    }

    public Way(ArrayList<String> arr, OsmTempPack db) {
        /*
     </way>
  <way id="14568241" version="3" timestamp="2010-11-25T18:07:15Z" changeset="6456331" uid="38239" user="Daeron">
    <nd ref="143513135"/>
    <nd ref="143513136"/>
    <nd ref="1004141717"/>
    <nd ref="1004141640"/>
    <nd ref="143513137"/>
    <nd ref="143513139"/>
    <nd ref="1004141705"/>
    <nd ref="1004141866"/>
    <nd ref="143513135"/>
    <tag k="building" v="hall"/>
  </way>
         */
        nodeRefs = new ArrayList<>();
        String[] temp;
        for (String line : arr) {
            line = line.replace(Q, "");

            temp = line.split(" ");
            if (temp[0].contains("way")) {

                String id = temp[1].replace("id=", "");
                this.wayID = Long.valueOf(id);
                //EnfuserLogger.log(Level.FINER,Way.class,"wayID = "+wayID);

            } else if (temp[1].contains("ref=")) {
                String ref = temp[1].replace("ref=", "");
                ref = ref.replace("/>", "");
                long node_id = Long.valueOf(ref);

                double[] coord = db.getCoords(node_id);
                this.coords.add(coord);
                if (coord == null) {
                    EnfuserLogger.log(Level.FINER,Way.class,"WAY: null coordinates added! nodeID = " + node_id);
                    double lat = coord[0];
                }
                nodeRefs.add(node_id);

            } else if (temp[0].contains("tag")) {
                String tag = temp[1].replace("k=", "");
                String value = temp[2].replace("v=", "");

                if (tag.contains("addr:street") || tag.equals("name")) {//the value is a NAME, and a name can contain the whitespace splitter WHICH IS BAD

                    int endIndex = 2;
                    for (int z = 2; z < temp.length; z++) {

                        if (temp[z].contains("/>")) {
                            temp[z] = temp[z].replace("/>", "");
                            endIndex = z;
                            break;
                        }
                    }

                    //concat name
                    value = "";
                    for (int z = 2; z <= endIndex; z++) {
                        value += temp[z];
                        if (z != endIndex) {
                            value += " ";
                        }
                    }

                    value = value.replaceAll("v=", "");
                    tag = "name";
                } else {
                    value = value.replace("/>", "");
                }

                if (tag.equals("source") || tag.equals("FIXME") || tag.equals("note") || tag.equals("fixme")) {//this is never needed

                } else {
                    this.tags.put(tag, value);
                }

            } else if (temp[0].contains("node")) {//THIS ACTUALLY A NODE DEFINITION

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
                double latd = Double.valueOf(lat);
                double lond = Double.valueOf(lon);
                long ref = Long.valueOf(id);
                double[] coord = {latd ,lond};
                db.addNode(ref, coord);

            }

        }//for lines

        initType(false);

        if (!this.tags.isEmpty()) {
            POI p = db.addPOI(nodeRefs, tags);
            if (p.hasContent) {
                this.poi = p;
            }
        }

        //check traffic lights 
        for (Long ref : this.nodeRefs) {
            if (db.nodeFlags[HWAY_SIGNALS].get(ref) != null) {
                if (this.trLights_latlon == null) {
                    this.trLights_latlon = new ArrayList<>();
                }
                this.trLights_latlon.add(db.getCoords_float(ref));
            }
        }

    }

    public boolean isValidArea() {
        return (this.ntype == NTYPE_AREA && this.coords.size() > 2);
    }

    public boolean isValidLine() {
        return (this.ntype == NTYPE_LINE && this.coords.size() > 1);
    }

    public boolean isRailWay() {

        if (this.isValidLine()) {
            String value = this.tags.get(RAILWAY);
            if (value != null && value.equals("rail")) {
                return true;
            }
        }
        return false;
    }

    public int getAreaElevation() {
        if (!this.isValidArea()) {
            return LU_UNDEF;
        }
        if (hasTagValue("contour", "elevation")) {
            String val = tags.get("ele");
            //EnfuserLogger.log(Level.FINER,Way.class,"Countour area: ele =  "+val);
            return Integer.valueOf(val);
        }
        return LU_UNDEF;
    }

    public float[] getAverageCoords() {
        return PoiHandler.getAverageCoords(coords);
    }

    public int getContourLineElevation() {
        if (!this.isValidLine()) {
            return LU_UNDEF;
        }

        if (hasTagValue("contour", "elevation")) {
            String val = tags.get("ele");
            //EnfuserLogger.log(Level.FINER,Way.class,"Countour area: ele =  "+val);
            return Integer.valueOf(val);
        }

        return LU_UNDEF;
    }

    public boolean hasTagValue(String tag, String val) {
        String v = this.tags.get(tag);
        if (v == null) {
            return false;
        }

        if (v.equals(val)) {
            return true;
        }
        return false;
    }

    public boolean isCoastline() {
        String value = this.tags.get(NATURAL);
        if (value != null && value.equals("coastline")) {
            return true;
        }
        return false;
    }

    public boolean isRiverLine() {
        //waterway=rive
//	stream : waterway=stream
        //	canal : waterway=canal
        if (!isValidLine()) {
            return false;
        }
        String value = this.tags.get("waterway");
        if (value == null) {
            return false;
        }

        if (value.equals("stream")) {
            return true;
        }
        if (value.equals("canal")) {
            return true;
        }
        if (value.equals("river")) {
            return true;
        }

        return false;
    }

    public void tagPrintout(ArrayList<String> search) {
        String s = "TAG PRINTOUT: \n";
        int k = 0;

        if (search != null) {
            for (String tag : search) {
                String val = this.tags.get(tag);
                if (val == null || val.equals("null")) {
                    continue;
                }
                k++;
                s += "\t" + tag + " = " + val + "\n";

            }
        } else {//null search list - print all tags

            for (String tag : tags.keySet()) {
                String val = this.tags.get(tag);
                k++;
                s += "\t" + tag + " = " + val + "\n";

            }

        }

        if (k > 0) {
            EnfuserLogger.log(Level.FINER,Way.class,s);
        }
    }

    public boolean coordinatesConnect() {

        if (this.coords.size() < 3) {
            return false;
        }

        if (this.coords.size() > 2 && this.coords.get(0) == this.coords.get(coords.size() - 1)) {
            return true;//end-point is also the start-point (pointers match)
        }
        //manual check just in case
        double[] cc1 = this.coords.get(0);
        double[] cc2 = this.coords.get(coords.size() - 1);

        if (cc1[0] == cc2[0] //lats match
                && cc1[1] == cc2[1]) {//lons match
            return true;
        }

        return false;
    }

    public void initType(boolean multiPoly) {
        this.multiPoly = multiPoly;
        this.ntype = NTYPE_POINT;

        if (this.coords.size() == 1) {
            this.ntype = NTYPE_POINT;

        } else if (this.coordinatesConnect()) {

            //        && this.coords.get(0) == this.coords.get(coords.size()-1)) {//end-point is also the start-point
            this.ntype = NTYPE_AREA;
        } else if (this.coords.size() > 1) {
            this.ntype = NTYPE_LINE;
        }

    }

    public float getLineLength_m(double cos_lat) {
        double dist = 0;
        for (int i = 0; i < this.coords.size() - 1; i++) {

            double[] cc1 = this.coords.get(i);
            double[] cc2 = this.coords.get(i + 1);

            double lat1 = cc1[0];// * Node.TO_DEGREE;
            double lon1 = cc1[1];// * Node.TO_DEGREE;

            double lat2 = cc2[0];// * Node.TO_DEGREE;
            double lon2 = cc2[1];// * Node.TO_DEGREE;

            double dist_inc = (lat2 - lat1) * (lat2 - lat1) + (lon2 - lon1) * (lon2 - lon1) * cos_lat * cos_lat;
            dist += Math.sqrt(dist_inc) * degreeInMeters;
        }

        return (float) dist;
    }

    boolean isTreeLine() {
        if (this.ntype != NTYPE_LINE) {
            return false;
        }

        String tr = tags.get("natural");
        if (tr != null && tr.equals("tree_row")) {
            return true;
        }
        return false;
    }

    boolean isRunway() {
        if (this.ntype != NTYPE_LINE) {
            return false;
        }

        String runway = tags.get("aeroway");
        if (runway != null && runway.equals("runway")) {
            return true;
        }
        return false;
    }

}
