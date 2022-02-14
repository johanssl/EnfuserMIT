/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author johanssl
 */
public class POI implements Serializable {

    private static final long serialVersionUID = 7526472293813776147L;//Important: change this IF changes are made that breaks serialization! 
    public ArrayList<int[]> coords;
    protected float[] midCC=null;
    public String tags;
    public int poiType = -1;
    public boolean hasContent = false;

    public final static int POI_TRAFFIC_SIGNAL = 0;
    public final static int POI_PGENERATOR = 1;
    public final static int POI_CHIMNEY = 2;
    public final static int POI_BUS_STOP = 3;
    public final static int POI_METRO_ENTRY = 4;
    public final static int POI_STATION = 5;
    public final static int POI_TRAM_STOP = 6;

    public final static int POI_EMPTY1 = 7;
    public final static int POI_EMPTY2 = 8;

    public final static String[][] TAG_VALS = {
        {"highway", "traffic_signals"},//0
        {"power", "plant"},
        {"man_made", "chimney"},
        {"highway", "bus_stop"},
        {"railway", "subway_entrance"},
        {"public_transport", "station"},
        {"railway", "tram_stop"},
        {"empty", "empty"},
        {"empty2", "empty2"},};

    public final static String[] TAGS = {
        null,//traffic lights//0
        "generator:source",
        null,//chimney//2
        null,//bus stop
        null,//metro entry//4
        null,//stations
        null,
        null,
        null
    };

    public final static int POI_SCHOOL = 9;
    public final static int POI_COLLEGE = 10;
    public final static int POI_UNIV = 11;
    public final static int POI_KINDERG = 12;
    public final static int POI_INSTIT = 13;
    public final static int POI_HOSPITAL = 14;
    public final static int POI_BAR = 15;
    public final static int POI_CAFE = 16;
    public final static int POI_FFOOD = 17;
    public final static int POI_PUB = 18;
    public final static int POI_RESTAURANT = 19;
    public final static int POI_FERRYTER = 20;
    public final static int POI_FUEL = 21;
    public final static int POI_NIGHTC = 22;
    public final static int POI_MARKETPLACE = 23;

    /* 
    public final static String[][] POI_TAGS = {
        {POI_TRAFFIC_SIGNAL+""  ,"highway=traffic_signals" },
        {POI_PGENERATOR+""      ,"power=plant", "generator:source=ANY" },
        {POI_CHIMNEY+""         ,"man_made=chimney" },
        {POI_BUS_TRAM_STOP+""   ,"highway=bus_stop","railway=tram_stop" },
        {POI_METRO_ENTRY+""     ,"railway=subway_entrance" },
        {POI_STATION+""         ,"public_transport=station" },
        {POI_SCHOOL},
        {POI_COLLEGE},
        {POI_UNIV},
        {POI_KINDERG},
        {POI_INSTIT},
        {POI_HOSPITAL},
        {POI_BAR},
        {POI_CAFE},
        {POI_FFOOD},
        {POI_PUB},
        {POI_RESTAURANT},
        {POI_FERRYTER},
        {POI_FUEL},
        {POI_NIGHTC},
        {POI_MARKETPLACE},
        
        
        
    };
     */
    public final static String[] AME_TAGS = {
        "school",//6
        "college",
        "university",//8
        "kindergarten",
        "research_institute", //10
        "hospital",
        "bar",//12
        "cafe",
        "fast_food",//14
        "pub",
        "restaurant",//16
        "ferry_terminal",
        "fuel",//18
        "nightclub",
        "marketplace"//20 
    };

    public final static int POI_BEACHRESORT = 24;
    public final static int POI_FITNESSC = 25;
    public final static int POI_GARDEN = 26;
    public final static int POI_GOLFC = 27;
    public final static int POI_MARINA = 28;
    public final static int POI_PARK = 29;
    public final static int POI_PLAYGROUND = 30;
    public final static int POI_SPORTSC = 31;

    public final static int LEN = 32;
    public final static int AME_ADD = 9;
    public final static int LEI_ADD = 24;

    public final static String[] LEIS_TAGS = {
        "beach_resort",//21
        "fitness_centre",
        "garden",//23
        "golf_course",
        "marina",//25
        "park",
        "playground",//27
        "sports_centre"
    };

    public static String getName(int i) {
        if (i < AME_ADD) {
            return TAG_VALS[i][1];
        } else if (i < LEI_ADD) {
            return AME_TAGS[i - AME_ADD];
        } else {
            return LEIS_TAGS[i - LEI_ADD];
        }
    }

    public String printOut() {
        /*
    String s ="";
    for (String key:this.tags.keySet()) {
        s+= "\t\t" + key +" = "+ this.tags.get(key) + "\n";
    }
    return s;
         */
        return "\t\t" + this.tags + "\n";
    }

    public static String tagsToLine(HashMap<String, String> tags) {
        String s = "";
        for (String key : tags.keySet()) {
            s += key + "=" + tags.get(key) + ";";
        }
        return s;
    }

    POI(ArrayList<Long> refs, HashMap<String, String> tags, OsmTempPack aThis) {

        for (int i = 0; i < TAGS.length; i++) {

            if (!this.hasContent) {

                if (TAGS[i] != null && tags.containsKey(TAGS[i])) {
                    this.hasContent = true;
                    this.poiType = i;
                    this.tags = tagsToLine(tags);
                    break;
                } else if (hasTagValue(tags, TAG_VALS[i][0], TAG_VALS[i][1])) {
                    this.hasContent = true;
                    this.poiType = i;
                    this.tags = tagsToLine(tags);
                    break;
                }

            }
        }

        //amenities
        if (!hasContent) {
            String ameVal = tags.get("amenity");
            if (ameVal != null) {
                for (int j = 0; j < AME_TAGS.length; j++) {

                    if (ameVal.equals(AME_TAGS[j])) {
                        this.hasContent = true;
                        this.poiType = j + AME_ADD;
                        this.tags = tagsToLine(tags);
                        break;
                    }

                }//for amenity tags
            }//if amenity
        }

        //leisure
        if (!hasContent) {
            String leisVal = tags.get("leisure");
            if (leisVal != null) {
                for (int j = 0; j < LEIS_TAGS.length; j++) {

                    if (leisVal.equals(LEIS_TAGS[j])) {
                        this.hasContent = true;
                        this.poiType = j + LEI_ADD;
                        this.tags = tagsToLine(tags);
                        break;
                    }

                }//for amenity tags
            }//if amenity
        }

        if (hasContent) {
            this.coords = new ArrayList<>();
            double midlat =0;
            double midlon =0;
            for (Long ref : refs) {
                double[] cc = aThis.getCoords(ref);
                midlat+=cc[0];
                midlon+=cc[1];
                
                int[] ccint = new int[]{//cannot change the type to double due to serialization. Bummer.
                (int)(10000*cc[0]),
                (int)(10000*cc[1]),    
                };
                this.coords.add(ccint);
            }
            if (coords.size()>0) {
                midlat/=coords.size();
                midlon/=coords.size();
                this.midCC = new float[]{(float)midlat,(float)midlon};
            }
        }

    }

    public static boolean hasTagValue(HashMap<String, String> tags, String tag, String val) {
        String v = tags.get(tag);
        if (v == null) {
            return false;
        }

        if (v.equals(val)) {
            return true;
        }
        return false;
    }


    boolean hasContent() {
        return this.hasContent;
    }
}
