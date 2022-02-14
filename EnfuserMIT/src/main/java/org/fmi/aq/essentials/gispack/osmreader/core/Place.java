/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.io.Serializable;
import java.util.HashMap;

/**
 *
 * @author johanssl
 */
public class Place implements Serializable {

    private static final long serialVersionUID = 7526472293592776147L;//Important: change this IF changes are made that breaks serialization! 
    public float[] latlon;
    public String tags;
    int type = -1;
    public final static String[] PLACE_NAMES = {
        "country",//0
        "state",
        "region",//2
        "province",
        "district",//4
        "county",
        "municipality",//6
        "city",
        "suburb",//8
        "quarter",
        "neighbourhood",//10
        "city_block",
        "town",//12
        "village",
        "hamlet",//14
        "island",
        "locality"//16
    };

    public final static int PLACE_COUNTRY = 0;
    public final static int PLACE_STATE = 1;
    public final static int PLACE_REGION = 2;
    public final static int PLACE_PROVINCE = 3;
    public final static int PLACE_DISTRICT = 4;
    public final static int PLACE_COUNTY = 5;
    public final static int PLACE_MUNIC = 6;
    public final static int PLACE_CITY = 7;
    public final static int PLACE_SUBURB = 8;
    public final static int PLACE_QUARTER = 9;
    public final static int PLACE_NEIGHB = 10;
    public final static int PLACE_CBLOCK = 11;
    public final static int PLACE_TOWN = 12;
    public final static int PLACE_VILLAGE = 13;
    public final static int PLACE_HAMLET = 14;
    public final static int PLACE_ISLAND = 15;
    public final static int PLACE_LOCALITY = 16;

    public Place(float[] latlon, HashMap<String, String> tags) {
        this.latlon = latlon;
        this.tags = POI.tagsToLine(tags);

        String val = tags.get("place");
        for (int i = 0; i < PLACE_NAMES.length; i++) {
            if (val.equals(PLACE_NAMES[i])) {
                this.type = i;
                break;
            }
        }

    }
}
