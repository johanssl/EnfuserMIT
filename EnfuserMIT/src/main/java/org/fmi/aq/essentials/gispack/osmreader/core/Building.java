/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.io.Serializable;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.FLOORS_LARGE;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.FLOORS_LARGEST;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.FLOORS_MED;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.FLOORS_SMALL;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.SIZE_CAT_LARGE;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.SIZE_CAT_LARGEST;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.SIZE_CAT_MED;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.evaluateBuildingType;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.IND_REPLACABLE;
import static org.fmi.aq.essentials.gispack.osmreader.core.BuildingEstimator.IND_SAVE_TAGS;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class Building implements Serializable {

    private static final long serialVersionUID = 7526472294623476147L;//Important: change this IF changes are made that breaks serialization!
    //roof color properties (e.g., based on satellite data)
    public byte roof_hue = 0;
    public byte roof_sat = 0;
    public byte roof_br = 0;
    public int IDint = -1;//integer-casted referenceID 

    public short area_m2 = 25;
    private byte floors = 0;
    public byte type = -1;
    public boolean physical = true;//setting this to false can make this invisible for dispersion related effects.

    public boolean typeUnspecified = false;
    public boolean floorsUnspecified = false;

    public final int ol_h;
    public final int ol_w;

    public boolean storetags = false;
    public boolean replacable = false;
    public final long ID;
    public byte hnumber = -1;
    public byte amenityType = -1;
    public byte minLevel = -1;//for constructions that are partly elevated and therefore passable on ground.
    public final short circleLineLength_m;

    public static final byte BUILD_UNDEFINED = -10;//also residential, dorm
    public static final byte BUILD_APARTMENTS = -11;//also residential, dorm
    public static final byte BUILD_FARM = -12;
    public static final byte BUILD_HOTEL = -13;
    public static final byte BUILD_HOUSE = -14;//also houseboat and bungalow, static caravan, shed, hut, detached, cabin
    public static final byte BUILD_COMMERCIAL = -15;
    public static final byte BUILD_OFFICE = -16;
    public static final byte BUILD_INDUSTRIAL = -17;//also digester
    public static final byte BUILD_RETAIL = -18; //also kiosk
    public static final byte BUILD_WAREHOUSE = -19;//also hangar
    public static final byte BUILD_RELIQ = -20;//also cathedral, chapel, church, mosque, temple, synagogue,shrine

    public static final byte BUILD_PUBLIC = -21;//also civic
    public static final byte BUILD_HOSPITAL = -22;
    public static final byte BUILD_EDU = -23;//kinderkarten, school, university
    public static final byte BUILD_TRANSPORT = -24;//train_station, transportation
    public static final byte BUILD_PUBLIC_LEISURE = -25;//Stadium, grandstand, riding_hall, conservatory
    public static final byte BUILD_CONSTRUCTION = -26;
    public static final byte BUILD_FARM_AUX = -27;//barn, cowshed, farm_auxiliary,stable,sty
    public static final byte BUILD_SMALL_UTILITY = -28;//garage, garages, garbage_shed, roof, terrace, carport, bunker
    public static final byte BUILD_GREENHOUSE = -29;
    public static final byte BUILD_LARGE_UTILITY = -30;//transformer_tower, service, water_tower, bridge, parking
    public static final byte BUILD_HOUSE_TERRACE = -31; // a row of multiple houses

    public static final int BUILDS_LEN = 22;//there are this many types
    public final static double floorH_m = 3.5;
    
    public float floors() {
        return this.floors;
    }

    
    public static int toNaturalIndex(Building b) {
        int ind = (-1 * b.type) - 10;
        return ind;
    }

    public static int tagIndexFromNatural(int t) {
        int ind = (-1 * t) - 10;
        return ind;
    }

    public static boolean isBuilding(byte osm) {
        // if (isProxyBuilding(osm)) return true;//proxy building slot => yes!
        return (osm >= -40 && osm <= -10);
    }


    public Building(Short lineLen, short area_m2, byte floors, byte type, long id,
            int ol_h, int ol_w) {
        this.area_m2 = area_m2;
        this.floors = floors;
        this.type = type;
        this.ID = id;
        this.ol_h = ol_h;
        this.ol_w = ol_w;
        //estimate circle line length assuming a square and some addition.
        //10m x 10m = 100m2, linelen = sqrt(100)*4 = 40 = 10 + 10 + 10 + 10.
        //20 x 5m = 100m2 line len = 50m which is more. Let's add 15% to all.
        if (lineLen != null) {
            this.circleLineLength_m = lineLen;
        } else {
            this.circleLineLength_m = (short) (Math.sqrt(area_m2) * 4 * 1.15);
        }
    }

    public Building(Way way, double size_m2, byte functional, byte type, AllTags at,
            double cos_lat, int ol_h, int ol_w) {

        this((short) way.getLineLength_m(cos_lat),
                way.wayID, way.poi, way.tags, size_m2, functional, type, at, ol_h, ol_w);
    }

    public Building(Short lineLen, long ID, POI poi, HashMap<String, String> tags,
            double size_m2, byte functional, byte type, AllTags at, int ol_h, int ol_w) {

        this.ID = ID;
        this.ol_h = ol_h;
        this.ol_w = ol_w;
        //building size, this is always known
        if (size_m2 > 30000) {
            size_m2 = 30000;
        }
        this.area_m2 = (short) size_m2;

        if (poi != null && poi.hasContent) {
            this.amenityType = (byte) poi.poiType;
        }
        if (lineLen != null) {
            this.circleLineLength_m = lineLen;
        } else {
            this.circleLineLength_m = (short) (area_m2 * 0.35f);//area_m2 needs to be init first.
        }

        //analyse building height   
        for (String tag : tags.keySet()) {
            String value = tags.get(tag);

            if (tag.equals("building:levels")) {
                try {
                    double v = Double.valueOf(value);
                    floors = (byte) Math.round(v);
                    break;
                } catch (Exception e) {
                }

            } else if (tag.equals("building:height")) {

                try {
                    double v = Double.valueOf(value) / floorH_m;
                    floors = (byte) Math.round(v);
                    break;
                } catch (Exception e) {
                }

            } else if (tag.equals("height")) {
                String temp = value.replace("m", "");
                try {
                    double v = Double.valueOf(temp) / floorH_m;
                    floors = (byte) Math.round(v);
                    break;
                } catch (Exception e) {
                }

            }

        }//for height tags 

        //check height again if not specified
        if (floors <= 0) {
            this.floorsUnspecified = true;//estimate will be updated below 
        }

        this.type = type;

        if (this.type == BUILD_UNDEFINED) {
            this.typeUnspecified = true;
            this.type = evaluateBuildingType(tags, this.area_m2, this.circleLineLength_m, functional, at);
        } //is now defined

        if (false && this.type == BUILD_SMALL_UTILITY && this.area_m2 > 300) {
            EnfuserLogger.log(Level.FINER,this.getClass(),
                    "Large SUB? how did this happen? functional = " + functional 
                            + ", circleLen = " + this.circleLineLength_m + ", typeSpecified = " 
                            + !this.typeUnspecified + ", area =" + this.area_m2);
            for (String tag : tags.keySet()) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"\t\t" + tag + "=" + tags.get(tag));
            }
        }

        int[] specs = at.getBuildingSpecs(this.type);
        int sizeCat;
        if (size_m2 > specs[SIZE_CAT_LARGEST]) {
            sizeCat = FLOORS_LARGEST;
        } else if (size_m2 > specs[SIZE_CAT_LARGE]) {
            sizeCat = FLOORS_LARGE;
        } else if (size_m2 > specs[SIZE_CAT_MED]) {
            sizeCat = FLOORS_MED;
        } else {
            sizeCat = FLOORS_SMALL;
        }

        if (this.floorsUnspecified) {//must be assessed
            this.floors = (byte) specs[sizeCat];
        }

        String num = tags.get("addr:housenumber");
        if (num != null) {
            try {
                if (num.contains("-")) {
                    num = num.split("-")[0];//e.g. "16-18"
                }
                num = num.toLowerCase();
                //EnfuserLogger.log(Level.FINER,this.getClass(),"\t house numer! "+ num);
                int test = Integer.valueOf(num);
                if (test > 127) {
                    test = 127;
                }
                this.hnumber = (byte) test;
            } catch (Exception e) {
            }
        }

        //save tags?
        if (specs[IND_SAVE_TAGS] == 1) {
            this.storetags = true;
        } else {
            this.storetags = false;
        }
        //can be replaced wit a dummy
        if (specs[IND_REPLACABLE] == 1 && this.floorsUnspecified && !this.storetags) {//a pretty generic building that could be proxied in case there are hundreds of thousands of these
            this.replacable = true;
        } else {
            this.replacable = false;
        }

    }
    
    
        public float getHeight_m(OsmLayer ol) {

        float height = floors();
        if (floors() == 0) {
            height = 0.5f;//can happen with small utility etc.
        }
        float raw = (height * (float) Building.floorH_m);
        if (!floorsUnspecified) {
            return raw;
        }

        if (ol != null) {//check grid data
            float gridVal = ol.getFastBuildHeight_fromGrid(ol_h, ol_w);
            if (gridVal > 0) {
                return gridVal;
            }
        }
        return raw;
    }

    public boolean hasNHnumber() {
        return this.hnumber > 0;
    }

    public void printout(AllTags at) {
        String name = BuildingEstimator.getTypeName(this.type, at);
        String s = "area = " + this.area_m2 + "m2, floors (raw)= " 
                + this.floors + ", type = " + name + " (or comparable)";
        EnfuserLogger.log(Level.FINER,this.getClass(),"Building: " + s);
    }
    
        public String getOneLiner(AllTags at) {
        String name = BuildingEstimator.getTypeName(this.type, at);
        String s = "area = " + (int)this.area_m2 + "m2, floors (raw)= " 
                + this.floors + ", type = " + name;
        return s;
    }

    public boolean saveTags() {
        return this.storetags;
    }

    public void changeFloorCount(int floorsgr) {
        floors = (byte) floorsgr;
    }

}
