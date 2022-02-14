/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.core;

import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.DATA_ROW;
import static org.fmi.aq.essentials.gispack.osmreader.core.AllTags.IND_DESCR;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_APARTMENTS;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_COMMERCIAL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_HOUSE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_INDUSTRIAL;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_SMALL_UTILITY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.BUILD_WAREHOUSE;
import static org.fmi.aq.essentials.gispack.osmreader.core.Building.floorH_m;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.LU_UNDEF;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class BuildingEstimator {

    public static float POP_PER_HOUSE = 2.2f;
    public static float POP_DIV = 100f;
    public static int HOUSE_M2_THRESH = 280; //larger is something else, e.g., apartments. Note that the 5m pix resolution causes a larger area for small objects than in reality
    public static int SUTIL_M2_THRESH = 51; //larger is something else, e.g., apartments. Note that the 5m pix resolution causes a larger area for small objects than in reality

    public static int[][] SPECLINE = {
        {Building.BUILD_UNDEFINED, 1000, 500, 200, 4, 4, 3, 2, 0, 1, 0, 0},//0 undef  CAN BE PROXIED
        {Building.BUILD_APARTMENTS, 1000, 500, 200, 6, 5, 4, 3, 0, 1, 1500, 1},//1 aparts   CAN BE PROXIED
        {Building.BUILD_FARM, 1000, 500, 200, 3, 2, 2, 2, 0, 0, 1000, 1},//2 farm
        {Building.BUILD_HOTEL, 3000, 1500, 500, 12, 12, 8, 6, 1, 0, 0, 0},//3 hotel // for population this is tricky
        {Building.BUILD_HOUSE, 240, 160, 110, 2, 2, 2, 1, 0, 1, 100, 1},//4 house
        {Building.BUILD_COMMERCIAL, 3000, 1500, 500, 7, 7, 6, 3, 0, 0, 0, 0},//5 commercial
        {Building.BUILD_OFFICE, 3000, 1500, 500, 8, 8, 6, 3, 0, 0, 0, 0},//6 office
        {Building.BUILD_INDUSTRIAL, 3000, 1500, 500, 5, 4, 3, 3, 1, 0, 0, 0},//7 industrial
        {Building.BUILD_RETAIL, 1000, 200, 100, 6, 6, 3, 1, 0, 0, 0, 0},//8 retail, also considering kiosk
        {Building.BUILD_WAREHOUSE, 5000, 2000, 1000, 3, 3, 3, 2, 0, 0, 0, 0},//9 warehouse

        {Building.BUILD_RELIQ, 2000, 1200, 100, 7, 5, 3, 2, 1, 0, 0, 0},//10 reliq
        {Building.BUILD_PUBLIC, 2000, 1200, 100, 6, 5, 4, 2, 1, 0, 0, 0},//11 public civic
        {Building.BUILD_HOSPITAL, 2000, 1200, 200, 7, 6, 5, 2, 1, 0, 0, 0},//12 hospital
        {Building.BUILD_EDU, 2000, 1200, 200, 7, 6, 4, 2, 1, 0, 500, 1},//13 edu, schools kinder, univ
        {Building.BUILD_TRANSPORT, 2000, 1200, 200, 7, 6, 4, 2, 0, 0, 0, 0},//14 transport
        {Building.BUILD_PUBLIC_LEISURE, 2000, 1200, 200, 7, 6, 4, 2, 0, 0, 0, 0},//15 public leisure
        {Building.BUILD_CONSTRUCTION, 2000, 1200, 200, 7, 6, 4, 3, 0, 0, 0, 0},//16 construction
        {Building.BUILD_FARM_AUX, 1000, 500, 200, 3, 2, 2, 2, 0, 0, 100, 1},//17 farm aux
        {Building.BUILD_SMALL_UTILITY, 300, 100, 70, 2, 1, 1, 1, 0, 1, 0, 0},//18 small utility   CAN BE PROXIED
        {Building.BUILD_GREENHOUSE, 2000, 1000, 200, 3, 3, 3, 2, 0, 0, 0, 0},//19 greenhouse
        {Building.BUILD_LARGE_UTILITY, 500, 200, 150, 5, 4, 3, 2, 0, 1, 0, 0},//20 large util
        {Building.BUILD_HOUSE_TERRACE, 800, 500, 350, 3, 3, 2, 1, 0, 1, 1500, 0},//21 row-house CAN BE PROXIED
    };

    //TAGS: 0 for no tags saved, 1 for tag store.
    public final static int IND_TYPE = 0;
    public final static int SIZE_CAT_LARGEST = 1;
    public final static int SIZE_CAT_LARGE = 2;
    public final static int SIZE_CAT_MED = 3;

    public final static int FLOORS_LARGEST = 4;
    public final static int FLOORS_LARGE = 5;
    public final static int FLOORS_MED = 6;
    public final static int FLOORS_SMALL = 7;

    public final static int IND_SAVE_TAGS = 8;
    public final static int IND_REPLACABLE = 9;
    public final static int IND_MAX_M2_POP = 10;
    public final static int IND_POP_DIVISOR = 11;

    public static void reinitBuildFloors(byte type, int[] sizes) {
        for (int i = 0; i < SPECLINE.length; i++) {
            if (SPECLINE[i][IND_TYPE] == (int) type) {

                SPECLINE[i][FLOORS_LARGEST] = sizes[0];
                SPECLINE[i][FLOORS_LARGE] = sizes[1];
                SPECLINE[i][FLOORS_MED] = sizes[2];
                SPECLINE[i][FLOORS_SMALL] = sizes[3];
                EnfuserLogger.log(Level.FINER,BuildingEstimator.class,"Done building floor count reinit.");
                break;
            }
        }
    }


    public static String getTypeName(int bType, AllTags at) {
        // int ind = Building.getTagIndexFromType(bType);
        String[][] elem = at.getElementByType((byte) bType);

        return elem[DATA_ROW][IND_DESCR];
        // return BUILD_TAGS[ind][0];
    }

    /**
     * Estimate building type based on it's size and functional landuse. Also
     * additional information given by OSM tags is used.
     *
     * @param tags OSM-tags hash (key,value) for this object.
     * @param size_m2 size of the building in terms of m2.
     * @param circ_m circle length of the building area.
     * @param functional functional land use type at the center of this
     * building.
     * @param at AllTags instance for fast type matching
     * @return building type (one of Tags)
     */
    public static byte evaluateBuildingType(HashMap<String, String> tags, double size_m2,
            double circ_m, byte functional, AllTags at) {

        //utilize the functional land use
        byte func_type = at.functional_to_B.get(functional);

        //2: functional landuse give-aways
        if (func_type == BUILD_INDUSTRIAL) {//ORANGE color - needs some additional size checks. However, houses and apartments are OFF the table.
            if (size_m2 > 5000) {//not credible as an apartment
                return BUILD_WAREHOUSE;
            } else if (size_m2 > SUTIL_M2_THRESH) {
                return BUILD_INDUSTRIAL;
            } else {
                return BUILD_SMALL_UTILITY;
            }

        } else if (func_type == BUILD_COMMERCIAL) {//RED - needs some additional size checks.  However, houses and apartments are OFF the table.

            if (size_m2 > SUTIL_M2_THRESH) {
                return BUILD_COMMERCIAL;
            } else {
                return BUILD_SMALL_UTILITY;
            }
        } else if (func_type != LU_UNDEF) {
            return func_type;
        }

        //circle length. The strength: small objects like houses. counting 5x5m pixels can be very bad to assess sizes. 
        //apartment buildings can have surprisingly small surface area, so devil is in the detail.
        //if ((int)(size_m2/10)*10 == 300) EnfuserLogger.log(Level.FINER,BuildingEstimator.class,"Area test 300m2: line circle = "+ (int)circ_m +"m"); 
        //next step: assess the smallest elements, particularly the houses   
        //small building detection
        if (circ_m < 8 * 2 + 7 * 2) {//56m2
            return BUILD_SMALL_UTILITY;
        } else if (circ_m < 20 * 2 + 12 * 2) {//240 area
            return BUILD_HOUSE;
        } else if (size_m2 < 2000) {//by now the area becomes more reliable
            return BUILD_APARTMENTS;
        } else if (size_m2 < 5000) {//by now the area becomes more reliable
            return BUILD_COMMERCIAL;
        } else {

            //check ratio of circle to area. warehouses don't have funny shapes
            //a^2 vs 4a
            double sideMetric = Math.sqrt(size_m2);
            double sideMetric2 = circ_m / 4.0;

            if (sideMetric2 > 1.7 * sideMetric) {
                n++;
                EnfuserLogger.log(Level.FINER,BuildingEstimator.class,"Large commercial"
                        + " building revealed based on area-to-circle ratio! n = " + n);
                
                return BUILD_COMMERCIAL;
            } else {
                return BUILD_WAREHOUSE;
            }

        }

    }
    private static int n = 0;

    public static float estimateWoodBurnFactor_perM2(Building b, Double popQ_dens, OsmLayer ol) {
        float height = b.getHeight_m(ol);
        if (!b.floorsUnspecified && height > 12) {
            return 0;
        }

        if (b.type != Building.BUILD_HOUSE && b.type != Building.BUILD_FARM
                && b.type != Building.BUILD_FARM_AUX && b.type != Building.BUILD_APARTMENTS
                && b.type != Building.BUILD_HOUSE_TERRACE) {
            return 0;
        }
        if (b.area_m2 > 350 || b.area_m2 < 51) {
            return 0; //can take in some apartments, but very small ones
        }
        float factor = 2.17f;//default: 2kg per a.

        //then some modifiers, penalties and increase factors based on credibility.
        //if (b.type == Building.BUILD_APARTMENTS) factor*=0.33f;
        //if (b.type == Building.BUILD_HOUSE_TERRACE) factor*=0.5f;
        if (b.area_m2 < HOUSE_M2_THRESH * 0.75 && b.area_m2 > HOUSE_M2_THRESH * 0.4) {
            factor *= 1.2f;//amplify buildings that are more clearly houses
        }
        if (popQ_dens != null) {
            if (popQ_dens < 1200) {
                factor *= 1.3;
            } else if (popQ_dens < 1600) {
                factor *= 1.2;
            } else if (popQ_dens < 2000) {
                factor *= 1.1;
            } else if (popQ_dens > 4000) {
                factor *= 0.8;
            } else if (popQ_dens > 6000) {
                factor *= 0.7;
            }
        }

        return factor / b.area_m2;

    }

    public static float estimatePopulation(Building b, AllTags at, OsmLayer ol) {
        if (b == null) {
            return 0;
        }
        float height = b.getHeight_m(ol);
        int[] specs = at.buildingSpecs.get(b.type);

        int maxArea = specs[IND_MAX_M2_POP];
        if (maxArea == 0) {
            return 0;//has no population capability set
        }
        float area = (float) Math.min(b.area_m2, maxArea);
        //if (!b.typeUnspecified) area = b.area_m2;//trust this area def fully.
        double floors = height / floorH_m;
        int pop = (int) (floors * area / POP_DIV);

        if (b.type == BUILD_HOUSE) {
            return POP_PER_HOUSE;
        }

        return pop;

    }

}
