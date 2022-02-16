/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import org.fmi.aq.enfuser.ftools.FileOps;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_APARTMENTS;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_COMMERCIAL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_CONSTRUCTION;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_EDU;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_FARM;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_FARM_AUX;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_GREENHOUSE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_HOSPITAL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_HOTEL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_HOUSE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_HOUSE_TERRACE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_INDUSTRIAL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_LARGE_UTILITY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_OFFICE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_PUBLIC;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_PUBLIC_LEISURE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_RELIQ;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_RETAIL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_SMALL_UTILITY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_TRANSPORT;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_UNDEFINED;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_WAREHOUSE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.*;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class AllTags {

    public final static int CAT_FUNCTIONAL = 0;//lines or areas, some nodes.
    public final static int CAT_SURF = 1;//lines or areas, some nodes.
    public final static int CAT_BUILDING = 4;//Note: lines or multipolygons are not used or recommended by OSM for "Building" (this category is only "Building")

    public final static int CAT_ROAD = 2;//NOTE: areas NOT allowed by OSM for "highway" (this category is only "highway"). Exception: highway = pedestrian (area) => plaza
    public final static int CAT_PEDESTRIAN = 3;

    public HashMap<Byte, int[]> buildingSpecs = new HashMap<>();
    public HashMap<Byte, Byte> functional_to_B = new HashMap<>();

    // [0] index [1] tag category [2] main identifier  [3] secondary_ids [4] functional-to-landcover [5] render priority (larger => last) [6] draw color  [7] descr.  
    public final static String[][] ALL_OSM_VARS = {
        {LU_COMMERCIAL + "", CAT_FUNCTIONAL + "", LANDUSE, "commercial", SURF_ASPHALT + "", "0", "255,0,0", BUILD_COMMERCIAL + "", "desc"},//"238,207,207" testing RED
        {LU_CONSTRUCTION + "", CAT_FUNCTIONAL + "", LANDUSE, "construction", SURF_UNPAVED_GRAVEL + "", "0", "199,199,180", BUILD_CONSTRUCTION + "", "desc"},
        {LU_INDUST + "", CAT_FUNCTIONAL + "", LANDUSE, "industrial", SURF_DIRT + "", "0", "248,146,7", BUILD_INDUSTRIAL + "", "desc"},//testing ORANGE, before "230,209,227"
        {LU_RESID + "", CAT_FUNCTIONAL + "", LANDUSE, "residential", SURF_PAVED + "", "3", "218,218,218", LU_UNDEF + "", "desc"},//color is GRAY. build apart needs size check for house! so that's why this is undef.
        {LU_RETAIL + "", CAT_FUNCTIONAL + "", LANDUSE, "retail", SURF_ASPHALT + "", "0", "122,05,125", LU_UNDEF + "", "desc"},//"254,202,197" testing VIOLET

        {LU_ALLOTS + "", CAT_FUNCTIONAL + "", LANDUSE, "allotments", NAT_HEATH + "", "0", "238,207,179", LU_UNDEF + "", "desc"},//important one for buildings
        {LU_BASIN + "", CAT_FUNCTIONAL + "", LANDUSE, "basin", NAT_WATER + "", "0", "180,208,208", LU_UNDEF + "", "desc"},
        {LU_BROWNFIELD + "", CAT_FUNCTIONAL + "", LANDUSE, "brownfield", SURF_UNPAVED_GRAVEL + "", "0", "182,182,144", LU_UNDEF + "", "desc"},
        {LU_CEMETERY + "", CAT_FUNCTIONAL + "", LANDUSE, "cemetery", NAT_SGRASS + "", "1", "171,204,176", BUILD_RELIQ + "", "desc"},
        {LU_CONSERVATION + "", CAT_FUNCTIONAL + "", LANDUSE, "conservation", LU_UNDEF + "", "0", "207,237,165", LU_UNDEF + "", "desc"},//not allowed anymore
        {LU_DEPOT + "", CAT_FUNCTIONAL + "", LANDUSE, "depot", SURF_ASPHALT + "", "1", "130,130,140", BUILD_TRANSPORT + "", "desc"},
        {LU_FARMLAND + "", CAT_FUNCTIONAL + "", LANDUSE, "farmland", NAT_GRASSLAND + "", "1", "249,232,203", BUILD_FARM + "", "desc"},
        {LU_FARMYARD + "", CAT_FUNCTIONAL + "", LANDUSE, "farmyard", NAT_SGRASS + "", "1", "234,204,164", BUILD_FARM + "", "desc"},
        {LU_FOREST + "", CAT_FUNCTIONAL + "", LANDUSE, "forest", NAT_WOODS + "", "0", "97,156,71", LU_UNDEF + "", "desc"},
        {LU_GARAGES + "", CAT_FUNCTIONAL + "", LANDUSE, "garages", SURF_UNPAVED_GRAVEL + "", "1", "222,221,204", BUILD_LARGE_UTILITY + "", "desc"},
        {LU_GRASS + "", CAT_FUNCTIONAL + "", LANDUSE, "grass", NAT_SGRASS + "", "0", "207,237,165", LU_UNDEF + "", "desc"},
        {LU_GREENFIELD + "", CAT_FUNCTIONAL + "", LANDUSE, "greenfield", NAT_HEATH + "", "1", "241,238,232", LU_UNDEF + "", "desc"},
        {LU_GHOUSEHORN + "", CAT_FUNCTIONAL + "", LANDUSE, "greenhouse_horticulture", NAT_GRASSLAND + "", "1", "249,222,183", LU_UNDEF + "", "desc"},
        {LU_LANDFILL + "", CAT_FUNCTIONAL + "", LANDUSE, "landfill", SURF_UNPAVED_GRAVEL + "", "1", "182,162,130", LU_UNDEF + "", "desc"},
        {LU_MEADOW + "", CAT_FUNCTIONAL + "", LANDUSE, "meadow", NAT_HEATH + "", "1", "207,254,165", LU_UNDEF + "", "desc"},
        {LU_MILITARY + "", CAT_FUNCTIONAL + "", LANDUSE, "military", SURF_UNPAVED_GRAVEL + "", "1", "243,223,217", LU_UNDEF + "", "desc"},
        {LU_ORCHARD + "", CAT_FUNCTIONAL + "", LANDUSE, "orchard", NAT_SCRUB + "", "1", "158,220,144", LU_UNDEF + "", "desc"},
        {LU_PASTURE + "", CAT_FUNCTIONAL + "", LANDUSE, "pasture", NAT_HEATH + "", "1", "207,236,158", LU_UNDEF + "", "desc"},
        {LU_FARM + "", CAT_FUNCTIONAL + "", LANDUSE, "farm", NAT_GRASSLAND + "", "1", "234,204,164", BUILD_FARM + "", "desc"},//propably not used anymore
        {LU_PLANTNURS + "", CAT_FUNCTIONAL + "", LANDUSE, "plant_nursery", NAT_WETLAND + "", "1", "165,224,183", BUILD_GREENHOUSE + "", "desc"},
        {LU_PORT + "", CAT_FUNCTIONAL + "", LANDUSE, "port", SURF_CONCRETE + "", "2", "189,130,169", BUILD_INDUSTRIAL + "", "desc"},
        {LU_QUARRY + "", CAT_FUNCTIONAL + "", LANDUSE, "quarry", SURF_DIRT + "", "2", "183,160,160", BUILD_INDUSTRIAL + "", "desc"},
        {LU_RAILW + "", CAT_FUNCTIONAL + "", LANDUSE, "railway", SURF_UNPAVED_GRAVEL + "", "2", "232,205,220", BUILD_TRANSPORT + "", "desc"},
        {LU_RECR_GROUND + "", CAT_FUNCTIONAL + "", LANDUSE, "recreation_ground", NAT_HEATH + "", "1", "226,237,165", BUILD_PUBLIC_LEISURE + "", "desc"},
        {LU_RELIQ + "", CAT_FUNCTIONAL + "", LANDUSE, "religious", NAT_HEATH + "", "0", "213,200,196", BUILD_RELIQ + "", "desc"},
        {LU_RESERVOIR + "", CAT_FUNCTIONAL + "", LANDUSE, "reservoir", NAT_WATER + "", "1", "171,212,224", LU_UNDEF + "", "desc"},
        {LU_SALTPOND + "", CAT_FUNCTIONAL + "", LANDUSE, "salt_pond", SURF_SALT + "", "1", "211,233,239", LU_UNDEF + "", "desc"},
        {LU_VILGREEN + "", CAT_FUNCTIONAL + "", LANDUSE, "village_green", NAT_HEATH + "", "1", "196,236,166", LU_UNDEF + "", "desc"},
        {LU_VINEYARD + "", CAT_FUNCTIONAL + "", LANDUSE, "vineyard", NAT_SCRUB + "", "1", "122,205,129", LU_UNDEF + "", "desc"},
        {AME_COLLEGE + "", CAT_FUNCTIONAL + "", AMENITY, "college", LU_UNDEF + "", "2", "250,72,10", BUILD_EDU + "", "desc"},
        {AME_KINDERG + "", CAT_FUNCTIONAL + "", AMENITY, "kindergarten", LU_UNDEF + "", "2", "252,136,92", BUILD_EDU + "", "desc"},
        {AME_SCHOOL + "", CAT_FUNCTIONAL + "", AMENITY, "school", LU_UNDEF + "", "2", "253,179,151", BUILD_EDU + "", "desc"},
        {AME_UNIV + "", CAT_FUNCTIONAL + "", AMENITY, "university; research_institute", LU_UNDEF + "", "2", "254,206,188", BUILD_EDU + "", "desc"},
        {AME_FUEL + "", CAT_FUNCTIONAL + "", AMENITY, "fuel", SURF_CONCRETE + "", "3", "169,168,152", BUILD_LARGE_UTILITY + "", "desc"},
        {AME_PARKINGS + "", CAT_FUNCTIONAL + "", AMENITY, "parking_space; parking", SURF_CONCRETE + "", "3", "150,149,129", LU_UNDEF + "", "desc"},
        {AME_GRAVEYARD + "", CAT_FUNCTIONAL + "", AMENITY, "grave_yard", NAT_SGRASS + "", "2", "249,232,183", BUILD_RELIQ + "", "desc"},
        {AME_MARKETPLACE + "", CAT_FUNCTIONAL + "", AMENITY, "marketplace", SURF_COBBLE + "", "2", "255,70,163", LU_UNDEF + "", "desc"},
        {AME_FOODCOMMERC + "", CAT_FUNCTIONAL + "", AMENITY, "bar; bbq; cafe; fast_food; pub; restaurant", SURF_CONCRETE + "", "2", "232,3,126", LU_UNDEF + "", "desc"},
        {AME_BUSSTATION + "", CAT_FUNCTIONAL + "", AMENITY, "bus_station", SURF_CONCRETE + "", "2", "95,61,99", LU_UNDEF + "", "desc"},
        {AME_FERRYTER + "", CAT_FUNCTIONAL + "", AMENITY, "ferry_terminal", SURF_CONCRETE + "", "2", "180,179,159", BUILD_TRANSPORT + "", "desc"},
        {LEI_BEACH + "", CAT_FUNCTIONAL + "", LEISURE, "beach_resort", NAT_SAND + "", "2", "255,242,183", LU_UNDEF + "", "desc"},
        {LEI_DOGPARK + "", CAT_FUNCTIONAL + "", LEISURE, "dog_park", NAT_HEATH + "", "2", "184,220,187", LU_UNDEF + "", "desc"},
        {LEI_GARDEN + "", CAT_FUNCTIONAL + "", LEISURE, "garden", NAT_GRASSLAND + "", "2", "170,210,154", LU_UNDEF + "", "desc"},
        {LEI_GOLFC + "", CAT_FUNCTIONAL + "", LEISURE, "golf_course", NAT_SGRASS + "", "2", "152,210,136", BUILD_PUBLIC_LEISURE + "", "desc"},
        {LEI_MARINA + "", CAT_FUNCTIONAL + "", LEISURE, "marina", SURF_UNPAVED_GRAVEL + "", "2", "202,189,179", LU_UNDEF + "", "desc"},
        {LEI_NATURE_RESERVE + "", CAT_FUNCTIONAL + "", LEISURE, "nature_reserve", NAT_WOODS + "", "1", "181,227,181", LU_UNDEF + "", "desc"},
        {LEI_PARK + "", CAT_FUNCTIONAL + "", LEISURE, "park", NAT_SGRASS + "", "2", "183,232,175", LU_UNDEF + "", "desc"},
        {LEI_PITCH + "", CAT_FUNCTIONAL + "", LEISURE, "pitch", NAT_SGRASS + "", "2", "104,188,77", LU_UNDEF + "", "desc"},
        {LEI_PLAYGROUND + "", CAT_FUNCTIONAL + "", LEISURE, "playground", SURF_UNPAVED_GRAVEL + "", "2", "221,236,245", LU_UNDEF + "", "desc"},
        {LEI_SPORTSCENTER + "", CAT_FUNCTIONAL + "", LEISURE, "sports_centre; stadium", SURF_PAVED + "", "2", "255,128,10", BUILD_PUBLIC_LEISURE + "", "desc"},
        {LEI_SWIMPOOL + "", CAT_FUNCTIONAL + "", LEISURE, "swimming_pool", NAT_WATER + "", "2", "201,216,245", LU_UNDEF + "", "desc"},
        {AE_AERODROME + "", CAT_FUNCTIONAL + "", AEROWAY, "aerodrome; apron", SURF_PAVED + "", "2", "128,128,250", BUILD_TRANSPORT + "", "desc"},
        {AE_RUNWAY + "", CAT_FUNCTIONAL + "", AEROWAY, "runway", SURF_PAVED + "", "3", "62,60,255", LU_UNDEF + "", "desc"},
        {HW_PLAZA + "", CAT_FUNCTIONAL + "", HIGHWAY, "plaza; pedestrian", SURF_COBBLE + "", "3", "200,200,250", LU_UNDEF + "", "desc"},
        {HW_TUNNELPOINT + "", CAT_FUNCTIONAL + "", HIGHWAY, "tunnel_point", SURF_ASPHALT + "", "3", "0,0,64", LU_UNDEF + "", "desc"},
        {NAT_SGRASS + "", CAT_SURF + "", NATURAL, "grass", "", "0", "172,210,156", LU_UNDEF + "", "desc"},//not natively for "natural" but it is reasonable to have one for implied surface materials (e.g., golf course)
        {NAT_WOODS + "", CAT_SURF + "", NATURAL, "wood; forest; tree_row", "", "2", "104,168,77", LU_UNDEF + "", "desc"},// nodes for "tree"
        {NAT_SCRUB + "", CAT_SURF + "", NATURAL, "scrub", "", "1", "181,227,181", LU_UNDEF + "", "desc"},
        {NAT_GRASSLAND + "", CAT_SURF + "", NATURAL, "grassland", "", "4", "198,220,175", LU_UNDEF + "", "desc"},
        {NAT_WETLAND + "", CAT_SURF + "", NATURAL, "wetland", "", "1", "75,192,160", LU_UNDEF + "", "desc"},
        {NAT_HEATH + "", CAT_SURF + "", NATURAL, "heath", "", "1", "214,217,159", LU_UNDEF + "", "desc"},
        {NAT_MOOR + "", CAT_SURF + "", NATURAL, "moor", "", "1", "210,210,150", LU_UNDEF + "", "desc"},
        {NAT_MUD + "", CAT_SURF + "", NATURAL, "mud", "", "1", "172,104,85", LU_UNDEF + "", "desc"},
        {NAT_FELL + "", CAT_SURF + "", NATURAL, "fell", "", "1", "198,212,152", LU_UNDEF + "", "desc"},
        {NAT_BAREROCK + "", CAT_SURF + "", NATURAL, "bare_rock", "", "1", "202,189,179", LU_UNDEF + "", "desc"},
        {NAT_SCREE + "", CAT_SURF + "", NATURAL, "scree", "", "1", "233,225,217", LU_UNDEF + "", "desc"},
        {NAT_SHINGLE + "", CAT_SURF + "", NATURAL, "shingle", "", "1", "222,222,235", LU_UNDEF + "", "desc"},
        {NAT_BEACH + "", CAT_SURF + "", NATURAL, "beach", "", "1", "255,242,183", LU_UNDEF + "", "desc"},
        {NAT_SAND + "", CAT_SURF + "", NATURAL, "sand", "", "1", "235,223,189", LU_UNDEF + "", "desc"},
        {NAT_WATER + "", CAT_SURF + "", NATURAL, "water; bay", "", "4", "180,208,208", LU_UNDEF + "", "desc"},
        {WW_WATERWAY + "", CAT_SURF + "", WATERWAY, "river; riverbank; stream; canal", "", "4", "140,173,191", LU_UNDEF + "", "desc"},
        {NAT_SEA + "", CAT_SURF + "", NATURAL, "sea", "", "3", "101,146,169", LU_UNDEF + "", "desc"},
        {NAT_COASTLINE + "", CAT_SURF + "", NATURAL, "coastline", "", "2", "74,112,132", LU_UNDEF + "", "desc"},
        {NAT_GLACIER + "", CAT_SURF + "", NATURAL, "glacier", "", "1", "221,236,245", LU_UNDEF + "", "desc"},
        {SURF_PAVED + "", CAT_SURF + "", SURFACE, "paved", "", "0", "184,163,188", LU_UNDEF + "", "desc"},
        {SURF_ASPHALT + "", CAT_SURF + "", SURFACE, "asphalt", "", "0", "150,125,118", LU_UNDEF + "", "desc"},
        {SURF_CONCRETE + "", CAT_SURF + "", SURFACE, "concrete", "", "0", "188,173,169", LU_UNDEF + "", "desc"},
        {SURF_COBBLE + "", CAT_SURF + "", SURFACE, "sett; unhewn_cobblestone; cobblestone; paving_stones", "", "0", "100,100,100", LU_UNDEF + "", "desc"},
        {SURF_UNPAVED_GRAVEL + "", CAT_SURF + "", SURFACE, "unpaved; compacted; gravel", "", "0", "201,176,156", LU_UNDEF + "", "desc"},
        {SURF_DIRT + "", CAT_SURF + "", SURFACE, "dirt; earth; ground", "", "0", "201,156,136", LU_UNDEF + "", "desc"},
        {SURF_SALT + "", CAT_SURF + "", SURFACE, "salt", "", "0", "209,208,227", LU_UNDEF + "", "desc"},
        {NAT_ISLAND + "", CAT_SURF + "", PLACE, "island; islet", "", "0", "230,248,230", LU_UNDEF + "", "island or islet"},//a very special are way possibility that has no other tags that desrcibes the surface
        //{SURF_TREELINE+""           ,CAT_SURF+""         ,SURFACE,  "tree_row"                      ,"","5", "100,100,100", "desc" },//TODO may not be required, use woods
        {RR_RAILROAD + "", CAT_SURF + "", RAILWAY, "railroad; rail", "", "9", "120,120,120", LU_UNDEF + "", "desc"},
        {HW_MOTORWAY + "", CAT_ROAD + "", HIGHWAY, "motorway", "", "15", "233,144,160", LU_UNDEF + "", "motorway, major divided highway, normally with 2 or more running lanes"},
        {HW_MWAY_LINK + "", CAT_ROAD + "", HIGHWAY, "motorway_link", "", "13", "239,164,180", LU_UNDEF + "", "The link roads (sliproads/ramps) leading to/from a motorway from/to a motorway or lower class highway"},
        {HW_TRUNK + "", CAT_ROAD + "", HIGHWAY, "trunk", "", "14", "251,178,154", LU_UNDEF + "", "trunk, the most important roads in a country's system that aren't motorways"},
        {HW_TRUNK_LINK + "", CAT_ROAD + "", HIGHWAY, "trunk_link", "", "12", "253,207,191", LU_UNDEF + "", "The link roads (sliproads/ramps) leading to/from a trunk road from/to a trunk road or lower class highway."},
        {HW_PRIMARY + "", CAT_ROAD + "", HIGHWAY, "primary", "", "13", "253,214,161", LU_UNDEF + "", "primary road, the next most important roads in a country's system"},
        {HW_PRIMARY_LINK + "", CAT_ROAD + "", HIGHWAY, "primary_link", "", "12", "254,223,182", LU_UNDEF + "", "The link roads (sliproads/ramps) leading to/from a primary road from/to a primary road or lower class highway."},
        {HW_SECONDARY + "", CAT_ROAD + "", HIGHWAY, "secondary", "", "12", "246,250,187", LU_UNDEF + "", "secondary road"},
        {HW_TERTIARY + "", CAT_ROAD + "", HIGHWAY, "tertiary; secondary_link", "", "11", "249,252,205", LU_UNDEF + "", "tertiary road, also secondary link"},
        {HW_RESID + "", CAT_ROAD + "", HIGHWAY, "residential", "", "10", "179,255,255", LU_UNDEF + "", "residential road, Roads which serve as an access to housing, without function of connecting settlements. Often lined with housing"},
        {HW_LSTREET + "", CAT_ROAD + "", HIGHWAY, "living_street", "", "10", "159,255,255", LU_UNDEF + "", "For living streets, which are residential streets where pedestrians have legal priority over cars"},
        {HW_MINOR + "", CAT_PEDESTRIAN + "", HIGHWAY, "service; unclassified", "", "8", "255,187,255", LU_UNDEF + "", "service or unclassified, he least important through roads in a country's system but accessible for some vehicles"},
        {HW_PEDESTR + "", CAT_PEDESTRIAN + "", HIGHWAY, "pedestrian", "", "8", "235,190,254", LU_UNDEF + "", "For roads used mainly/exclusively for pedestrians in shopping and some residential areas which may allow access by motorised vehicles only for very limited periods of the day."},
        {HW_FOOTWAY + "", CAT_PEDESTRIAN + "", HIGHWAY, "footway", "", "8", "240,209,254", LU_UNDEF + "", "For designated footpaths; i.e., mainly/exclusively for pedestrians. "},
        {HW_PATH + "", CAT_PEDESTRIAN + "", HIGHWAY, "path; track", "", "8", "240,200,250", LU_UNDEF + "", "A non-specific path for walks"},
        {BUILD_UNDEFINED + "", CAT_BUILDING + "", BUILDING, "yes", "", "9", "24,75,90", LU_UNDEF + "", "undefined building"},
        {BUILD_APARTMENTS + "", CAT_BUILDING + "", BUILDING, "apartments;residential;block;dorm ", "", "9", "129,0,0", LU_UNDEF + "", "apartment building"}, //BLOOD red
        {BUILD_FARM + "", CAT_BUILDING + "", BUILDING, "farm", "", "9", "24,75,90", LU_UNDEF + "", "farm building"},
        {BUILD_HOTEL + "", CAT_BUILDING + "", BUILDING, "hotel", "", "9", "24,75,90", LU_UNDEF + "", "hotel"},
        {BUILD_HOUSE + "", CAT_BUILDING + "", BUILDING, "house; houseboat; bungalow; static_caravan; shed; hut; detached; cabin", "", "9", "250,0,0", LU_UNDEF + "", "house"},//RED
        {BUILD_COMMERCIAL + "", CAT_BUILDING + "", BUILDING, "commercial", "", "9", "63,13,68", LU_UNDEF + "", "commercial non-retail building"},// dark violet
        {BUILD_OFFICE + "", CAT_BUILDING + "", BUILDING, "office", "", "9", "24,75,90", LU_UNDEF + "", "office building"},
        {BUILD_INDUSTRIAL + "", CAT_BUILDING + "", BUILDING, "industrial; digester", "", "9", "0,0,125", LU_UNDEF + "", "industrial, non-warehouse building"},//dark blue
        {BUILD_RETAIL + "", CAT_BUILDING + "", BUILDING, "retail; kiosk; shop", "", "9", "73,18,98", LU_UNDEF + "", "retail building"},
        {BUILD_WAREHOUSE + "", CAT_BUILDING + "", BUILDING, "warehouse; hangar", "", "9", "236,236,0", LU_UNDEF + "", "warehouse"}, //YELLOW
        {BUILD_RELIQ + "", CAT_BUILDING + "", BUILDING, "religious; cathedral; chapel; church; mosque; temple; sunagogue; shrine", "", "9", "24,75,90", LU_UNDEF + "", "religious building"},
        {BUILD_PUBLIC + "", CAT_BUILDING + "", BUILDING, "civic; public", "", "9", "24,75,90", LU_UNDEF + "", "public building"},
        {BUILD_HOSPITAL + "", CAT_BUILDING + "", BUILDING, "hospital", "", "9", "39,68,69", LU_UNDEF + "", "hospital"},
        {BUILD_EDU + "", CAT_BUILDING + "", BUILDING, "school; university; college; kindergarten", "", "9", "39,68,75", LU_UNDEF + "", "educational building"},
        {BUILD_TRANSPORT + "", CAT_BUILDING + "", BUILDING, "station; train_station; transportation", "", "9", "39,68,55", LU_UNDEF + "", "building for transportation"},
        {BUILD_PUBLIC_LEISURE + "", CAT_BUILDING + "", BUILDING, "stadium; grandstand; riding_hall; conservatory; pavilion", "", "9", "24,75,90", LU_UNDEF + "", "public leisure building"},
        {BUILD_CONSTRUCTION + "", CAT_BUILDING + "", BUILDING, "construction", "", "9", "24,75,90", LU_UNDEF + "", "building under construction"},
        {BUILD_FARM_AUX + "", CAT_BUILDING + "", BUILDING, "barn; cowshed; farm_auxiliary; stable; sty", "", "9", "24,75,90", LU_UNDEF + "", "aux. farm building"},
        {BUILD_SMALL_UTILITY + "", CAT_BUILDING + "", BUILDING, "garage; garages; garbage_shed; roof; carport; bunker", "", "9", "244,2,253", LU_UNDEF + "", "small utility building"},//violet
        {BUILD_GREENHOUSE + "", CAT_BUILDING + "", BUILDING, "greenhouse", "", "9", "24,75,90", LU_UNDEF + "", "greenhouse"},
        {BUILD_LARGE_UTILITY + "", CAT_BUILDING + "", BUILDING, "transformer_tower; service; water_tower; bridge; parking", "", "9", "24,75,90", LU_UNDEF + "", "large utility building"},
        {BUILD_HOUSE_TERRACE + "", CAT_BUILDING + "", BUILDING, "terrace", "", "9", "190,0,0", LU_UNDEF + "", "terrace (row-house)"},};

    public HashMap<Integer, ArrayList<String[][]>> byCategory = new HashMap<>();
    public HashMap<Integer, String[][]> byType = new HashMap<>();

    public HashMap<Integer, ArrayList<String>> mainsByCat = new HashMap<>();

    public HashMap<String, ArrayList<String[][]>> byCatMain = new HashMap<>();

    private HashMap<Integer, Integer> priorities = new HashMap<>();

    public final static int IND_TYPE = 0;
    public final static int IND_CAT = 1;
    public final static int IND_KEY_SEARCH = 2;
    public final static int IND_VALUE_SEARCH = 3;
    public final static int IND_FUNC_TO_SURF = 4;
    public final static int IND_PRIORITY = 5;
    public final static int IND_COLOR = 6;
    public final static int IND_FUNC_TO_BUILDING = 7;
    public final static int IND_DESCR = 8;

    public final static int DATA_ROW = 0;
    public final static int SEARCH_ROW = 1;

    public static String[] getLine(int osm) {

        for (String[] dat : ALL_OSM_VARS) {
            int typ = Integer.valueOf(dat[0]);
            if (typ == osm) {
                return dat;
            }
        }
        return null;
    }

    public static String luTypeDescription(int i) {
        String[] temp = getLine(i);
        String desc = temp[IND_DESCR];
        if (desc.equals("desc")) {
            desc = temp[IND_VALUE_SEARCH];
        }
        String s = temp[IND_TYPE] + ";\t " + temp[IND_KEY_SEARCH] + ";\t " + desc + ";\t " + temp[IND_COLOR];
        return s;
    }

    public static void printoutTags() {
        ArrayList<String> arr = new ArrayList<>();
        String header = ("NUM" + ";\t " + "CATEGORY" + ";\t " + "DESCRIPTION" + ";\t " + "COLOR_CODING");
        EnfuserLogger.log(Level.FINER,AllTags.class,header);
        arr.add(header);
        for (int i = 0; i < ALL_OSM_VARS.length; i++) {
            int typ = Integer.valueOf(ALL_OSM_VARS[i][0]);
            String s = luTypeDescription(typ);
            EnfuserLogger.log(Level.FINER,AllTags.class,s);
            /*
       String s = luTypeDescription(i);
        EnfuserLogger.log(Level.FINER,AllTags.class,s);
             */
            arr.add(s);
        }
        File f = new File("");
        String filename = f.getAbsolutePath() + "/tags_Info.csv";
        EnfuserLogger.log(Level.FINER,AllTags.class,"This information can be found from " + filename);
        FileOps.printOutALtoFile2(f.getAbsolutePath() + "/", arr, "tags_Info.csv", false);

    }

    public AllTags() {

        for (String[] elem : ALL_OSM_VARS) {

            this.functional_to_B.put((byte) LU_UNDEF, (byte) LU_UNDEF);//this must be here as well to avoid NullPointer

            String s = elem[IND_VALUE_SEARCH];
            s = s.replace(" ", "");
            String[] ss = s.split(";");
            String[][] ele2 = { //the second row contains all the search keys in an easily accessible form
                elem,
                ss
            };

            //1: by type only
            int key = Integer.valueOf(elem[IND_TYPE]);
            this.byType.put(key, ele2);

            if (isFunctionalLU((byte) key)) {
                byte btype = Integer.valueOf(elem[IND_FUNC_TO_BUILDING]).byteValue();
                this.functional_to_B.put((byte) key, btype);
            }

            //2: by category
            int cat = Integer.valueOf(elem[IND_CAT]);
            ArrayList<String[][]> arr = this.byCategory.get(cat);
            if (arr == null) {
                arr = new ArrayList<>();
                arr.add(ele2);
                this.byCategory.put(cat, arr);
            } else {
                arr.add(ele2);
            }

            //3: mains by cat
            ArrayList<String> ar = this.mainsByCat.get(cat);
            if (ar == null) {
                ar = new ArrayList<>();
                this.mainsByCat.put(cat, ar);
            }

            String main = elem[IND_KEY_SEARCH];//e.g., natural, landuse
            if (!ar.contains(main)) {
                ar.add(main);
            }

            //4: main_category combination hash
            String kk = cat + "_" + main;
            ArrayList<String[][]> marr = this.byCatMain.get(kk);
            if (marr == null) {
                marr = new ArrayList<>();
                marr.add(ele2);
                this.byCatMain.put(kk, marr);
            } else {
                marr.add(ele2);
            }

            //5 priorities hash
            Integer prior = Integer.valueOf(elem[IND_PRIORITY]);
            this.priorities.put(key, prior);
        }

        //building specs
        for (int[] specs : BuildingEstimator.SPECLINE) {
            int type = specs[BuildingEstimator.IND_TYPE];
            this.buildingSpecs.put((byte) type, specs);
        }

    }

    public String[][] getElementByType(byte osm) {
        /*
    if (isProxyBuilding(osm)) {
        osm = BuildingEstimator.getTrueType_forProxy(osm);
    }
         */
        return this.byType.get((int) osm);
    }

    /**
     * Get all main keys that are listed for this category. Example: CAT_SURF
     * has 'landuse', 'surface' to name a few.
     *
     * @param category
     * @return
     */
    ArrayList<String> catalogMains(int category) {
        return this.mainsByCat.get(category);
    }

    /**
     * Get a list of elements that can be used to form key-value searches for
     * the given category-key combination. Example: CAT_FUNCTIONAL-AMENITY
     *
     * @param category
     * @param key
     * @return
     */
    ArrayList<String[][]> getSearches(int category, String key) {
        String kk = category + "_" + key;
        return this.byCatMain.get(kk);
    }

    /**
     * For the given functional type the implied surface type is given.
     *
     * @param flu
     * @return
     */
    public byte getImpliedSurfaceType(byte flu) {
        if (!Tags.isFunctionalLU(flu)) {
            return LU_UNDEF;
        }
        String[][] elem = this.byType.get((int) flu);
        byte lu = Integer.valueOf(elem[DATA_ROW][IND_FUNC_TO_SURF]).byteValue();
        return lu;

    }

    public int getPriority(byte value) {
        //if (value == 0) EnfuserLogger.log(Level.FINER,AllTags.class,"TAG VALUE ZERO was given!");
        if (value == LU_UNDEF || value == LU_N_A) {
            return -1;//these two are not defined otherwise.
        }
        return this.priorities.get((int) value);
    }

    public HashMap<Byte, int[]> getColorHash() {
        HashMap<Byte, int[]> cols = new HashMap<>();

        for (Integer in : this.byType.keySet()) {
            String[][] elem = this.byType.get(in);
            String[] colLine = elem[DATA_ROW][IND_COLOR].split(",");

            int r = Integer.valueOf(colLine[0]);
            int g = Integer.valueOf(colLine[1]);
            int b = Integer.valueOf(colLine[2]);

            int[] c = {r, g, b};
            cols.put(in.byteValue(), c);
        }

        return cols;
    }

    int[] getBuildingSpecs(byte type) {
        return this.buildingSpecs.get(type);
    }

}
