/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import org.fmi.aq.enfuser.ftools.FileOps;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Node.Q;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class OsmTempPack {

    public final static String NODE = "</node>";
    public final static String WAY = "</way>";
    public final static String RELATION = "</relation>";
    
    public HashMap<Long, double[]> nodeCoords = new HashMap<>();
    public HashMap<Long, Way> ways = new HashMap<>();
    public ArrayList<Node> nods = new ArrayList<>();

    public ArrayList<Way> areaWays = new ArrayList<>();
    public ArrayList<Way> lineWays = new ArrayList<>();

    public ArrayList<Relation> relations = new ArrayList<>();
    public ArrayList<Place> places = new ArrayList<>();

    public HashMap<Long, float[]>[] nodeFlags = new HashMap[4];
    public ArrayList<POI>[] POIs = new ArrayList[POI.LEN];//this will be stored to OsmLayer also.

    public String dir;
    public String filename;
    public String name;
    public Boundaries bounds;

    public final double cos_lat;

    public int relationsToPolys = 0;
    public int relationsToMultip = 0;

    public OsmTempPack(String dir, String name,Boundaries b) {
        String filenam = name + ".osm";
        this.name = name;
        this.filename = filenam;
        this.dir = dir;
        this.bounds = b;
        for (int i = 0; i < nodeFlags.length; i++) {
            nodeFlags[i] = new HashMap<>();
        }

        ArrayList<String> arr = FileOps.readStringArrayFromFile_ISO(dir + filename);

        ArrayList<String> buffer = new ArrayList<>();
        int k = 0;

        for (String line : arr) {
            k++;

            if (k < 100 && bounds == null) {
                try {
                    //<bounds minlat="64.8536" minlon="25.1841" maxlat="65.138" maxlon="25.7348"/> 
                    line = line.replace(Q, "");
                    line = line.replace("/>", "");
                    String[] temp = line.split(" ");
                    double latmin = Double.valueOf(temp[1].replace("minlat=", ""));
                    double lonmin = Double.valueOf(temp[2].replace("minlon=", ""));
                    double latmax = Double.valueOf(temp[3].replace("maxlat=", ""));
                    double lonmax = Double.valueOf(temp[4].replace("maxlon=", ""));
                    bounds = new Boundaries(latmin, latmax, lonmin, lonmax);
                    EnfuserLogger.log(Level.FINER,this.getClass(),"Boundaries taken:");
                    EnfuserLogger.log(Level.FINER,this.getClass(),bounds.toText());

                } catch (Exception e) {

                }
            }

            //check processing clause
            if (line.length() < 15) {
                if (line.contains(WAY)) {
                    Way way = new Way(buffer, this);
                    this.addWay(way);

                    buffer.clear();
                    continue;
                } else if (line.contains(NODE)) {
                    Node node = new Node(buffer, this);
                    nods.add(node);
                    buffer.clear();
                    continue;

                } else if (line.contains(RELATION)) {
                    Relation rel = new Relation(buffer, this);
                    if (rel.multiPolygon()) {
                        
                        ArrayList<Way> combos = rel.processMultiPoly();
                            EnfuserLogger.log(Level.FINER,this.getClass(),
                                    "\t got " + combos.size() + " multipolygons as a result");
                        
                        for (Way w : combos) {
                            addWay(w);
                        }

                        relationsToPolys += combos.size();
                        relationsToMultip++;

                    }

                    String busLine = rel.getBusLine();
                    if (busLine != null) {
                        EnfuserLogger.log(Level.FINER,this.getClass(),
                                "Relation - busline " + busLine + " for " + rel.members.size() + " way members");
                        
                        for (Way way : rel.members) {
                            way.buslines.add(busLine);
                        }
                    }

                    buffer.clear();
                    continue;
                }

                buffer.add(line);

            } else {
                buffer.add(line);
            }

            if (k % 100000 == 0) {
                EnfuserLogger.log(Level.FINE,this.getClass(),"nodes = " + nods.size() + ", ways = " + ways.size());
            }

        }//for lines

        if (bounds == null) {
            EnfuserLogger.log(Level.INFO,this.getClass(),"Boundaries are still undefined => use node coordinates");
            EnfuserLogger.log(Level.INFO,this.getClass(),"DB has " + this.nodeCoords.size() + " entries.");
            bounds = getNodeBounds(this.nodeCoords);
        }
        double midlat = (this.bounds.latmax + this.bounds.latmin) / 2;
        this.cos_lat = Math.cos(midlat / 360 * (2 * Math.PI));

    }

    double[] getCoords(long node_id) {
        return this.nodeCoords.get(node_id);
    }
    float[] getCoords_float(Long ref) {
       double[] ll = this.nodeCoords.get(ref);
       float lat = (float)ll[0];
       float lon = (float)ll[1];
       return new float[]{lat,lon};
    }

    Way getWay(long way_id) {
        return this.ways.get(way_id);
    }

    private Boundaries getNodeBounds(HashMap<Long, double[]> nodeCoords) {
        double latmin = 90;
        double latmax = -90;
        double lonmin = 180;
        double lonmax = -180;

        // for (Node n:nodes) {
        //    
        for (double[] cc : nodeCoords.values()) {
            double lat = cc[0];
            double lon = cc[1];

            if (lat < latmin) {
                latmin = lat;
            }
            if (lat > latmax) {
                latmax = lat;
            }
            if (lon < lonmin) {
                lonmin = lon;
            }
            if (lon > lonmax) {
                lonmax = lon;
            }

        }

        //}
        Boundaries b = new Boundaries(latmin, latmax, lonmin, lonmax);
        EnfuserLogger.log(Level.FINER,this.getClass(),b.toText());
        return b;
    }

    void addNode(long ref, double[] coord) {
        this.nodeCoords.put(ref, coord);

    }

    public void addWay(Way way) {
        this.ways.put(way.wayID, way);

        if (way.isValidLine()) {
            this.lineWays.add(way);
        } else if (way.isValidArea()) {
            this.areaWays.add(way);
        } else {
            EnfuserLogger.log(Level.FINER,this.getClass(),"Way: not a line or area?");
            this.areaWays.add(way);
        }

    }

    /**
     * Checks tag content and adds certain points of interests so that the text
     * content can be easily analysed later on. Also flags significant trees and
     * traffic lights and their node ref. numbers.
     *
     * @param refs a list of reference numbers (nodes)
     * @param tags key-value hash for osm-tags
     * @return POI instance.
     */
    public POI addPOI(ArrayList<Long> refs, HashMap<String, String> tags) {
        ArrayList<Byte> flags = new ArrayList<>();
        if (tags.containsKey(HWAY)) {// highway tag
            String s = tags.get(HWAY);

            if (s.equals(CROSSING)) {
                flags.add(HWAY_CROSS);
            } else if (s.equals(TR_SIG)) {
                flags.add(HWAY_SIGNALS);
            }

        }

        //Crossing tag
        if (tags.containsKey(CROSSING)) {
            String s = tags.get(CROSSING);

            if (s.equals(TR_SIG)) {
                flags.add(CROSSING_SIGNALS);
            }

        }

        //tree
        if (tags.containsKey(NATR)) {
            String s = tags.get(NATR);

            if (s.equals(TREE)) {
                flags.add(NAT_TREE);
            }

        }

        if (!flags.isEmpty()) {
            for (Long ref : refs) {//for each node
                for (Byte b : flags) {//add these flags
                    this.nodeFlags[b].put(ref, this.getCoords_float(ref));
                }
            }
        }

        POI poi = new POI(refs, tags, this);
        if (poi.hasContent()) {
            int ind = poi.poiType;
            if (this.POIs[ind] == null) {
                this.POIs[ind] = new ArrayList<>();
            }
            this.POIs[ind].add(poi);
        }

        return poi;
    }
    private final static String CROSSING = "crossing";
    private final static String TR_SIG = "traffic_signals";
    private final static String HWAY = "highway";
    private final static String NATR = "natural";
    private final static String TREE = "tree";

    public final static byte HWAY_CROSS = 0;
    public final static byte HWAY_SIGNALS = 1;
    public final static byte CROSSING_SIGNALS = 2;
    public final static byte NAT_TREE = 3;

    void addPlace(ArrayList<Long> refs, HashMap<String, String> tags) {
        long ref = refs.get(0);//size is 1 anyways.

        float[] latlon = new float[2];
        for (long l : refs) {//average coords
            double[] ll = this.getCoords(ref);
            latlon[0] += ll[0] / refs.size();
            latlon[1] += ll[1] / refs.size();
        }

        Place p = new Place(latlon, tags);
        if (p.type >= 0) {
            this.places.add(p);
        }
    }



}
