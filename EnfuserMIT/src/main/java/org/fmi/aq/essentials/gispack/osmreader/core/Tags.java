/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.ArrayList;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.CAT_BUILDING;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.CAT_PEDESTRIAN;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.CAT_ROAD;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.DATA_ROW;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.IND_TYPE;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.SEARCH_ROW;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_OFFICE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_PUBLIC;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_UNDEFINED;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class Tags {

    public static final String NATURAL = "natural";
    public static final String LANDUSE = "landuse";
    public static final String HIGHWAY = "highway";
    public final static String AREA_HWAY = "area:highway";

    public static final String BUILDING = "building";
    public static final String BPART = "building:part";//this is an importnt exception
    public static final String CONSTRUCTION = "construction";
    public static final String AMENITY = "amenity";
    public final static String AEROWAY = "aeroway";
    public final static String RAILWAY = "railway";
    public final static String WATERWAY = "waterway";
    public final static String LEISURE = "leisure";
    public final static String SURFACE = "surface";
    public final static String PLACE = "place";

    public final static int LU_UNDEF = 0;//not identified
    public final static int LU_N_A = -125;//not available, outOfBounds e.g.
    //functionals

    public final static int LU_CONSTRUCTION = 1; //PRIORITY LU
    public final static int LU_INDUST = 2; //PRIORITY LU
    public final static int LU_RESID = 3; //PRIORITY LU
    public final static int LU_RETAIL = 4; //PRIORITY LU

    public final static int LU_ALLOTS = 5;//residents growing vegs
    public final static int LU_BASIN = 6; //artif. pond
    public final static int LU_BROWNFIELD = 7; //for constructions
    public final static int LU_CEMETERY = 8;
    public final static int LU_CONSERVATION = 9;
    public final static int LU_DEPOT = 10; //trains buses trams

    public final static int LU_FARMLAND = 11;//animals crops, vegs, flowers fruit //PRIORITY LU
    public final static int LU_FARMYARD = 12;//for farm dwellings
    public final static int LU_FOREST = 13;//managed forest
    public final static int LU_GARAGES = 14;//small storage units
    public final static int LU_GRASS = 15;//mown managed grass
    public final static int LU_GREENFIELD = 16;//will be turned for construction site

    public final static int LU_GHOUSEHORN = 17;//full of greenhouses
    public final static int LU_LANDFILL = 18;//dump
    public final static int LU_MEADOW = 19;//grassy pasture
    public final static int LU_MILITARY = 20;
    public final static int LU_ORCHARD = 21;//intentional planting of trees shrubs for food
    public final static int LU_PASTURE = 22;

    public final static int LU_FARM = 23;//NOT AVAILABLE
    public final static int LU_PLANTNURS = 24;//farming of plants
    public final static int LU_PORT = 25;//coastal industrial //PRIORITY LU
    public final static int LU_QUARRY = 26;//surface mineral extraction //PRIORITY LU
    public final static int LU_RAILW = 27;
    public final static int LU_RECR_GROUND = 28;//open green space for recreation

    public final static int LU_RELIQ = 29;
    public final static int LU_RESERVOIR = 30;//sweet water reserve
    public final static int LU_SALTPOND = 31;//evaporation of water => salt
    public final static int LU_VILGREEN = 32;//just grass
    public final static int LU_VINEYARD = 33;//grapes
    //public final static int LU_LAST =33;//grapes

    //functional areas from amenities
    public final static int AME_COLLEGE = 34;
    public final static int AME_KINDERG = 35;//
    public final static int AME_SCHOOL = 36;//
    public final static int AME_UNIV = 37;//
    public final static int AME_FUEL = 38;//
    public final static int AME_PARKINGS = 39;//
    public final static int AME_GRAVEYARD = 40;//
    public final static int AME_MARKETPLACE = 41;//
    public final static int AME_FOODCOMMERC = 42;//bar, bbq, cafe, fast_food, pub, restaurant
    public final static int AME_BUSSTATION = 43;
    public final static int AME_FERRYTER = 44;
    //public final static int AME_LAST =44;//grapes

    //functional areas from leisure
    public final static int LEI_BEACH = 45;//managed beach
    public final static int LEI_DOGPARK = 46;//
    public final static int LEI_GARDEN = 47;//
    public final static int LEI_GOLFC = 48;//
    public final static int LEI_MARINA = 49;//
    public final static int LEI_NATURE_RESERVE = 50;//
    public final static int LEI_PARK = 51;//
    public final static int LEI_PITCH = 52;//soccer, skate parks
    public final static int LEI_PLAYGROUND = 53;//
    public final static int LEI_SPORTSCENTER = 54;//also stadium
    public final static int LEI_SWIMPOOL = 55;//also stadium

    //mixed manual types
    public final static int AE_AERODROME = 56; //functional
    public final static int HW_PLAZA = 57; //functional
    public final static int HW_TUNNELPOINT = 58;//functional
    public final static int AE_RUNWAY = 59;// functional
    public final static int LU_COMMERCIAL = 60; //PRIORITY LU //formerly 0

    public final static int NAT_SGRASS = 70;
    public final static int NAT_WOODS = 71;//woodland, covers all kinds of forests, tag leaf_type.
    public final static int NAT_SCRUB = 72;//bushes
    public final static int NAT_GRASSLAND = 73;
    public final static int NAT_WETLAND = 74;
    public final static int NAT_HEATH = 75;
    public final static int NAT_MOOR = 76;
    public final static int NAT_MUD = 77;
    public final static int NAT_FELL = 78;
    public final static int NAT_BAREROCK = 79;
    public final static int NAT_SCREE = 80;
    public final static int NAT_SHINGLE = 81;
    public final static int NAT_BEACH = 82;
    public final static int NAT_SAND = 83;
    public final static int NAT_WATER = 84;// water 1:sweet
    public final static int WW_WATERWAY = 85;//?//sweet?
    public final static int NAT_SEA = 86;//from coastline water 3:saline
    public final static int NAT_COASTLINE = 87;//
    public final static int NAT_GLACIER = 88;
    public final static int NAT_ISLAND = 89;

    public final static int SURF_PAVED = 90;
    public final static int SURF_ASPHALT = 91;
    public final static int SURF_CONCRETE = 92;
    public final static int SURF_COBBLE = 93;
    public final static int SURF_UNPAVED_GRAVEL = 94;
    public final static int SURF_DIRT = 95;
    public final static int SURF_SALT = 96;

    public final static int SURF_TREELINE = 97;
    public final static int RR_RAILROAD = 98;

    public final static int HW_MOTORWAY = 100;
    public final static int HW_MWAY_LINK = 101;
    public final static int HW_TRUNK = 102;
    public final static int HW_TRUNK_LINK = 103;
    public final static int HW_PRIMARY = 104;
    public final static int HW_PRIMARY_LINK = 105;
    public final static int HW_SECONDARY = 106;
    public final static int HW_TERTIARY = 107;
    public final static int HW_RESID = 108;
    public final static int HW_LSTREET = 109;
//note: isHighway() is defined as >= motorway && <= lstreet

    public final static int HW_PEDESTR = 110;
    public final static int HW_FOOTWAY = 111;
    public final static int HW_PATH = 112;
    public final static int HW_MINOR = 113;

    public static boolean isPedestrianWay(byte osm) {
        return (osm >= HW_PEDESTR && osm <= HW_MINOR);
    }

    public static boolean isWater(byte osm) {
        return (osm == NAT_WATER || osm == WW_WATERWAY || osm == NAT_SEA); //swimpool not taken into account
    }

    public static boolean isFunctionalLU(byte osm) {
        //LU_UNDEF is set to 0!!
        return (osm > 0 && osm < NAT_SGRASS);

    }

    public static boolean isHighway(byte b) {

        return (b >= HW_MOTORWAY && b <= HW_LSTREET);
    }

    public static byte assessPedestrian(Way way, AllTags at) {
        String value = way.tags.get(HIGHWAY);
        if (value == null) {
            value = way.tags.get(AREA_HWAY);//switch key for one more try 
            if (value == null) {
                return LU_UNDEF;
            }
        }

        ArrayList<String[][]> eles = at.getSearches(CAT_PEDESTRIAN, HIGHWAY);
        for (String[][] ele : eles) {

            //there is a key for go over searches then
            for (String valueTest : ele[SEARCH_ROW]) {
                if (valueTest.equals(value)) {
                    return Integer.valueOf(ele[DATA_ROW][IND_TYPE]).byteValue();//perfect match.
                }
            }

        }//for elements
        return LU_UNDEF;
    }

    public static byte assessHighway(Way way, AllTags at) {

        String value = way.tags.get(HIGHWAY);
        if (value == null) {
            value = way.tags.get(CONSTRUCTION);
            if (value == null) {
                return LU_UNDEF;
            }
        }

        ArrayList<String[][]> eles = at.getSearches(CAT_ROAD, HIGHWAY);
        for (String[][] ele : eles) {

            //there is a key for go over searches then
            for (String valueTest : ele[SEARCH_ROW]) {
                if (valueTest.equals(value)) {
                    return Integer.valueOf(ele[DATA_ROW][IND_TYPE]).byteValue();//perfect match.
                }
            }

        }//for elements
        return LU_UNDEF;

    }
    private static int offs = 0;
    private static int hots = 0;
    private static int ames = 0;
    private static boolean ADDITIONAL_B = true;

    public static byte assessBuilding(Way way, AllTags at) {

        //step 1: must have the building tag.
        String value = way.tags.get(BUILDING);
        if (value == null) {
            value = way.tags.get(BPART);
            if (value == null) {
                return LU_UNDEF;//not a building for sure.
            }
        }

        //check the most used tag that tells nothing: "yes"
        boolean yesB = false;
        boolean amel = false;
        if (ADDITIONAL_B && value.equals("yes")) {
            yesB = true;
            //1:start from some give-aways

            if (way.tags.get("office") != null) {
                offs++;
                if (offs % 100 == 0) {
                    EnfuserLogger.log(Level.FINER,Tags.class,"An office building catched!  catches = " + offs);
                }
                return BUILD_OFFICE;
            }//nice catch.

            String tour = way.tags.get("tourism");//also nice catch
            if (tour != null) {
                hots++;
                if (hots % 100 == 0) {
                    EnfuserLogger.log(Level.FINER,Tags.class,"A tourism/hotel building catched! catches = " + hots);
                }
                if (tour.equals("hotel")) {
                    return Building.BUILD_HOTEL;
                }
                return BUILD_PUBLIC;
            }

            //Then some trickier stuff. Check amenity
            if (way.tags.get(AMENITY) != null) {
                value = way.tags.get(AMENITY);//a lot better. This can be hotel, hospital etc.  
                amel = true;
                //check leisure, might have sport_centre etc.    
            } else if (way.tags.get(LEISURE) != null) {
                amel = true;
                value = way.tags.get(LEISURE);//a lot better. This can be hotel, hospital etc.    
            }
        }

        ArrayList<String[][]> eles = at.getSearches(CAT_BUILDING, BUILDING);
        for (String[][] ele : eles) {

            //there is a key for go over searches then
            for (String valueTest : ele[SEARCH_ROW]) {
                if (valueTest.equals(value)) {
                    if (amel) {
                        ames++;
                        if (ames % 100 == 0) {
                            EnfuserLogger.log(Level.FINER,Tags.class,"An amenity/leisure building catched!  catches = " + ames);
                        }
                    }
                    return Integer.valueOf(ele[DATA_ROW][IND_TYPE]).byteValue();
                }//perfect match.
            }

        }//for elements

        if (yesB) {
            return BUILD_UNDEFINED;//amenity or leisure tag was used instead, but no luck
        }
        return LU_UNDEF;

    }

    public static byte assessProperty(Way way, AllTags at, int category) {
        byte lu = LU_UNDEF;
        ArrayList<String> mains = at.catalogMains(category); //map all key-values that are relevant for this property

        for (String key : mains) {
            String value = way.tags.get(key);
            if (value == null) {
                continue;
            }

            ArrayList<String[][]> eles = at.getSearches(category, key);
            for (String[][] ele : eles) {

                //there is a key for go over searches then
                for (String valueTest : ele[SEARCH_ROW]) {
                    if (valueTest.equals(value)) {
                        return Integer.valueOf(ele[DATA_ROW][IND_TYPE]).byteValue();//perfect match.
                    }
                }

            }//for elements
        }//for relevant mains 

        return lu;

    }

}
