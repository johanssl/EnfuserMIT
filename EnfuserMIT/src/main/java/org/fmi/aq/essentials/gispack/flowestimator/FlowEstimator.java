/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.osmreader.core.Coordinate;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.core.Materials;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_POP;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_POPDEN1;
import static org.fmi.aq.essentials.gispack.osmreader.core.Materials.BGN_POPDEN6;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import org.fmi.aq.essentials.plotterlib.Visualization.FigureData;
import org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions;
import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 *
 * @author johanssl
 */
public class FlowEstimator {

    public final static String SOURCE_PCLF = "PolyComponent LineFlow";
    public final static String SOURCE_DEF = "BasicFlow";

    public final static String VEHIC_HEAVY = "heavy";
    public final static String VEHIC_BUS = "bus";
    public final static String VEHIC_CAR = "vehic";
    public final static String VEHIC_LIGHT = "light";

    public final static int IND_HEAVY = 0;
    public final static int IND_BUS = 1;
    public final static int IND_CAR = 2;
    public final static int IND_LIGHT = 3;

    public final static int[] VC_INDS = {
        IND_HEAVY,
        IND_BUS,
        IND_CAR,
        IND_LIGHT
    };

    public static String[] VEHIC_CLASS_NAMES = {
        VEHIC_HEAVY,
        VEHIC_BUS,
        VEHIC_CAR,
        VEHIC_LIGHT
    };

    
    public final static Dtime A_FRIDAY = new Dtime("2020-01-31T00:00:00");
    public final static Dtime A_SATURDAY = new Dtime("2020-02-01T00:00:00");
    public final static Dtime A_SUNDAY = new Dtime("2020-02-02T00:00:00");

    public static Dtime[] FRI_SAT_SUN = {
        A_FRIDAY,
        A_SATURDAY,
        A_SUNDAY
    };
    public static final int EDIT_MULTI = 1;
    public static final int EDIT_ADD = 9;
    public static final int EDIT_SET = 2;


    /**
     * Fetch proper index that matches by name with one of the vehicle classes.
     *
     * @param s the class name for which index is sought.
     * @param cleanup if true, converts to "lower-case" and removes whitespaces.
     * @return index for matching class name. Can return -1 if no match was
     * found.
     */
    public static int parseClassIndex(String s, boolean cleanup) {
        String temp;
        if (cleanup) {
            temp = s.toLowerCase().replace(" ", "");
        } else {
            temp = s;
        }

        if (s.contains("vehc")) {
            return IND_CAR;//"vehic" and "vehc" both used in files
        }
        for (int i = 0; i < VC_INDS.length; i++) {
            String nam = VEHIC_CLASS_NAMES[i];
            if (nam.equals(temp)) {
                return i;
            }
        }
        return -1;//not found
    }

    public static double getRawDAVGIndex(RoadPiece rp, OsmLayer ol) {
        float lat = rp.getMidLat();
        float lon = rp.getMidLon();

        float qpop = OSMget.getBGdata(Materials.BGN_POPDEN1, ol, lat, lon);
        if (qpop < MIN_POPDEN1KM) {
            qpop = MIN_POPDEN1KM;
        }
        float pop = OSMget.getBGdata(Materials.BGN_POPDEN6, ol, lat, lon);
        if (pop < MIN_POPDEN6KM) {
            pop = MIN_POPDEN6KM;
        }

        float popFactor = qpop / 3200;
        if (rp.tier() > 2) {
            popFactor = Math.max(popFactor, pop / 1800);
        }

        popFactor = (float) Math.pow(popFactor, 0.7);
        int lanes = Math.min(rp.lanes, 6);
        //if (RoadPiece.isLink(rp)&& lanes<2) lanes =2; 
        int speed = rp.speedLimit;
        if (speed < 30) {
            speed = 30;
        }
        if (speed > 100) {
            speed = 100;
        }

        double index = Math.pow((rp.tier() + 1), 1.3) * 280;//base
        index *= popFactor;//take into account population density factor
        index *= Math.pow(lanes, 0.5);//take into account n lanes.
        index *= Math.sqrt(speed);

        return index;
    }

    public static float MIN_POPDEN6KM = 800;
    public static float MIN_POPDEN1KM = 1000;

    public static float[] getSimpleDAVGS(RoadPiece rp,
            OsmLayer ol) {

        double index = getRawDAVGIndex(rp, ol);//this should be comparabe to vehicles per day (cars)

        float[] davgs = new float[VEHIC_CLASS_NAMES.length];
        for (int c = 0; c < davgs.length; c++) {

            float trv;
            if (c == IND_BUS) {

                int blines = rp.buslines;
                if (blines < 0) {
                    blines = 50;//the value was actually over 128!
                }
                float baht = (float) rp.buslines / 0.36f;
                trv = (float) baht * 3;

            } else if (c == IND_HEAVY) {

                trv = (float) index / 8;
                if (rp.tier() == 0) {
                    trv /= 3;
                } else if (rp.tier() == 1) {
                    trv /= 2;
                }

            } else if (c == IND_CAR) {
                trv = (float) index;//no additional mods needed 

            } else {
                trv = (float) index * 0.1f;
                if (rp.speedLimit > 40) {
                    float div = rp.speedLimit / 40;
                    trv /= (div * div);

                }
            }
            davgs[c] = trv;
        }//for c

        return davgs;
    }

    private final static String DEFNAME = "basicTRVprofile.csv";

    /**
     * Use the crude basic flow profile (basicTRVprofile.csv) and basic osmLayer
     * data to estimate traffic flow information.
     *
     * @param dir work folder directory (does not have /addData/)
     * @param ol the layer
     * @param doNotOverwriteThis if flow data is already existing, this source
     * will not be replaced.
     */
    public static void addDefaultTRVproxies(String dir, OsmLayer ol, String doNotOverwriteThis) {

        String dataDir = dir + Materials.ADD_DATA_SUBDIR;
        BasicFlowProfile bfp = new BasicFlowProfile(dataDir, true);

        //if population data has not been set, then this should not work!
        ByteGeoGrid pop = ol.getByteGeo(BGN_POP);
        if (pop == null) {
            EnfuserLogger.log(Level.FINER,FlowEstimator.class,"WARNING! population"
                    + " is not available for basic flow estimates!");
            EnfuserLogger.sleep(300,FlowEstimator.class);
            
        }
        ByteGeoGrid pd1 = ol.getByteGeo(BGN_POPDEN1);
        if (pd1 == null) {
            EnfuserLogger.log(Level.FINER,FlowEstimator.class,
                    "WARNING! population density (1km) is not available for basic flow estimates!");
            EnfuserLogger.sleep(300,FlowEstimator.class);
        }
        ByteGeoGrid pd2 = ol.getByteGeo(BGN_POPDEN6);
        if (pd2 == null) {
            EnfuserLogger.log(Level.FINER,FlowEstimator.class,
                    "WARNING! population density (6km) is not available for basic flow estimates!");
            EnfuserLogger.sleep(300,FlowEstimator.class);
        }

        int k = 0;
        for (RoadPiece rp : ol.allRPs().values()) {
            k++;
            if (rp.hasFlows() && doNotOverwriteThis != null
                    && rp.flowSource().equals(doNotOverwriteThis)) {
                continue;
            }
            if (k % 500 == 0) {
                EnfuserLogger.log(Level.INFO,FlowEstimator.class,
                        "RoadPiece flow data estimation, n = " + k);
            }

            float[] davgs = getSimpleDAVGS(rp, ol);
            SimpleFlowArray fa = bfp.getScaledFlows(davgs);
            rp.setFlow(fa);
        }

        EnfuserLogger.log(Level.INFO,FlowEstimator.class,"Done.");
    }

    public static GeoGrid getFlowGrid(OsmLayer ol, Dtime dt, String classS,
            float resDiv, boolean dailySum) {

        int classI = parseClassIndex(classS, true);
        int H = (int) (ol.H / resDiv);
        int W = (int) (ol.W / resDiv);

        float[][] dat = new float[H][W];

        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w++) {

                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp == null) {
                    continue;
                }
                try {

                    float flow = rp.getHourlyFlow(dt, classI);
                    if (dailySum) flow = rp.getDAVGS()[classI];
                    
                    int nh = (int) (h / resDiv);
                    int nw = (int) (w / resDiv);
                    dat[nh][nw] = flow;
                } catch (Exception e) {

                }
            }//for w
        }//for h

        GeoGrid g = new GeoGrid(dat, Dtime.getSystemDate_utc(false, Dtime.ALL), ol.b);
        return g;
    }

    public static int parseWKD(String s, boolean cleanup) {
        String temp;
        if (cleanup) {
            temp = s.toLowerCase().replace(" ", "");
        } else {
            temp = s;
        }

        for (int i = 0; i < TemporalDef.WKDS; i++) {
            String nam = TemporalDef.WKDS_NAMES[i];
            if (nam.equals(temp)) {
                return i;
            }
        }
        return -1;//not found
    }

    public static ArrayList<Dtime> getRushHours() {
        ArrayList<Dtime> dts = new ArrayList<>();

        for (int i = 6; i < 19; i++) {
            Dtime dt = A_FRIDAY.clone();
            dt.addSeconds(i * 3600);
            dts.add(dt);
        }
        return dts;
    }

    public static ArrayList<Dtime> getAllDates() {
        ArrayList<Dtime> dts = new ArrayList<>();

        for (int i = 0; i < 24 * 3; i++) {
            Dtime dt = A_FRIDAY.clone();
            dt.addSeconds(i * 3600);
            dts.add(dt);
        }
        return dts;
    }

    public final static int DRAW_PROP_SELECTION = 0;
    public final static int DRAW_PROP_DAVG = 1;


    public static GeoGrid getCrudeLineGrid(Boundaries b, float res_m,
            HashMap<Integer, RoadPiece> rps, Dtime dt, int property, int propertyDef2) {

        AreaNfo nfo = new AreaNfo(b, res_m, Dtime.getSystemDate_utc(false, Dtime.ALL));
        int H = nfo.H;
        int W = nfo.W;
        float[][] inc = new float[H][W];

        for (RoadPiece rp : rps.values()) {
            float value = 0;

            if (property == DRAW_PROP_SELECTION) {
                value = 1;
            } else if (property == DRAW_PROP_DAVG) {
                int classI = propertyDef2;
                value = rp.getDAVGS()[classI];
                
            } 

            //interpolate
            int steps = (int) (1.5 * rp.crudeLength_m() / res_m) + 1;
            ArrayList<Coordinate> latlons = Coordinate.interpolateSteps(rp.cc, steps);

            for (Coordinate c : latlons) {

                int h = nfo.getH(c.lat);
                int w = nfo.getW(c.lon);
                if (nfo.oobs(h, w)) continue;
                inc[h][w] = Math.max(value,inc[h][w]);

            }//for latlons
        }//for rps

        GeoGrid g = new GeoGrid(inc, dt, b);
        return g;
    }

    public static GeoGrid getHighResLineGrid(OsmLayer ol, float res_m,
            Dtime dt, int property, int propertyDef2) {
        
        AreaNfo nfo = new AreaNfo(ol.b, res_m, Dtime.getSystemDate_utc(false, Dtime.ALL));
        boolean sameRes = Math.abs(ol.resm-res_m)<1;
        int H = nfo.H;
        int W = nfo.W;
        if (sameRes) {
            H =ol.H;
            W =ol.W;
        }
        float[][] inc = new float[H][W];

        for (int h = 0; h < ol.H; h++) {
            for (int w = 0; w < ol.W; w++) {
                float value = 0;
                RoadPiece rp = ol.getRoadPiece(h, w);
                if (rp == null) {
                    continue;
                }

                if (property == DRAW_PROP_SELECTION) {
                    value = 1;
                } else if (property == DRAW_PROP_DAVG) {
                    int classI = propertyDef2;
                    value = rp.getDAVGS()[classI];
                } 
                
                if (sameRes) {
                    inc[h][w] = Math.max(value, inc[h][w]);
                    continue;
                }
                
                double lat = ol.get_lat(h);
                double lon = ol.get_lon(w);
                int ch = nfo.getH(lat);
                int cw = nfo.getW(lon);
                if (nfo.oobs(ch, cw)) continue;
                inc[ch][cw] = Math.max(value, inc[ch][cw]);
            }
        }//h

        GeoGrid g = new GeoGrid(inc, dt, ol.b);
        return g;
    }

    public static void drawFlow_crude(String dir, int classI, Boundaries b, float res_m,
            HashMap<Integer, RoadPiece> rps, Dtime dt, boolean davg, VisualOptions ops, boolean pane, String name) {

        GeoGrid g = getCrudeLineGrid(b, res_m, rps, dt, DRAW_PROP_DAVG, classI);
        FigureData fd = new FigureData(g, ops);
        fd.type = name;
        fd.saveImage(FigureData.IMG_FILE_PNG, dir);
        fd.ProduceGEfiles(true, dir);
        if (pane) {
            fd.drawImagetoPane();
        }

    }

    public static String getTimeKey(Dtime dt, int hAdd) {
        int wkd = TemporalDef.getWKDindex(dt);
        String s = "h" + (dt.hour + hAdd) + "wkd" + wkd;
        return s;
    }
    private static HashMap<Integer, ArrayList<Dtime>> HOURS = new HashMap<>();

    public static ArrayList<Dtime> getHours(int wkd) {
        ArrayList<Dtime> arr = HOURS.get(wkd);

        if (arr == null) {
            arr = new ArrayList<>();
            Dtime base = FRI_SAT_SUN[wkd];

            for (int h = 0; h < 24; h++) {
                Dtime dtt = base.clone();
                dtt.addSeconds(3600 * h);
                arr.add(dtt);
            }//for h

            HOURS.put(wkd, arr);

            EnfuserLogger.log(Level.FINER,FlowEstimator.class,"Array of dates: " + TemporalDef.WKDS_NAMES[wkd]);
            for (Dtime dtt : arr) {
                EnfuserLogger.log(Level.FINER,FlowEstimator.class,dtt.getStringDate());
            }
            EnfuserLogger.log(Level.FINER,FlowEstimator.class,"====================");
        }

        return arr;
    }

}
