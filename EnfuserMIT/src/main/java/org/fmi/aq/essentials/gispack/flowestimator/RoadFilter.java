/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.flowestimator;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.essentials.gispack.osmreader.core.Coordinate;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import java.util.HashMap;
import java.util.ArrayList;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;

/**
 * This class provides a way to find roadPieces that are in line with a number
 * of selection criteria. This includes geographic filters, speed and tier
 * limits etc.
 *
 * A number of modification variables can also be introduced and this class can
 * automatically apply the modifications to flows, for all the roads that have
 * been accepted based on the filters.
 *
 * @author johanssl
 */
public class RoadFilter {
    
    public final static String CUSTOM_RP_FILTER = "ROAD_FILTER<";
    //filters for appliable road selection
    int[] speedLimit = null;
    int[] tierLimit = null;

    String sourceFilter = null;
    public AreaFilter afilt = new AreaFilter();

    public HashMap<Integer, RoadPiece> selection = new HashMap<>();//store acceptable roads here
    public String nameMustContain = null;

    //Flow modification attributes
    public Integer flowClass=-1;
    public Dtime flowDt=null;
    public Double flowAmp = null;
    
    public RoadFilter() {

    }

    public final static String KEY_SPEEDF = "speed_limit";
    public final static String KEY_TIERF = "tier_limit";

    public final static String KEY_NAME = "name_contains";
    public final static String KEY_SOURCE = "source_equals";

    public final static String KEY_VCLASS = "flowClass";
    public final static String KEY_FLOWDATE = "flowDate";
    public final static String KEY_AMPS = "flowAmp";

    public final static String[] RFKEYS = {
        KEY_SPEEDF,
        KEY_TIERF,
        KEY_NAME,
        KEY_SOURCE,
        KEY_VCLASS,
        KEY_AMPS,
    };

    public String toMapTaskerString() {

        String s = CUSTOM_RP_FILTER + "";

        if (speedLimit != null) {
            s += KEY_SPEEDF + "=" + speedLimit[0] + "," + speedLimit[1] + "&";
        }

        if (tierLimit != null) {
            s += KEY_TIERF + "=" + tierLimit[0] + "," + tierLimit[1] + "&";
        }

        if (this.sourceFilter != null) {
            s += KEY_SOURCE + "=" + this.sourceFilter + "&";
        }

        if (this.nameMustContain != null) {
            s += KEY_NAME + "=" + this.nameMustContain + "&";
        }

        //move on to mods
        //classes
        if (flowClass != null) {
            s += KEY_VCLASS + "=" + this.flowClass;
            s += "&";
        }
        //amps
        if (this.flowAmp != null) {
            s += KEY_AMPS + "=" +flowAmp;
            s += "&";
        } 

        if (this.flowDt!=null) {
            s += KEY_FLOWDATE + "=" + flowDt.getStringDate_noTS();
        }

        s += ">";
        s = s.replace(",&", "&");
        if (this.afilt.hasRules()) {
            s += afilt.toMapTaskerString();
        }
        return s;
    }

    public static RoadFilter fromStrings(String argline, String af_args) {
        EnfuserLogger.log(Level.INFO,RoadFilter.class,
                "==========RoadFilter: parsing from strings==============");
        //speed_limit=0,140&tier_limit=0,4&...
        String[] spRaw = argline.split("&");

        HashMap<String, String> temp = new HashMap<>();
        //from the temp hash first.
        for (String key : RFKEYS) {
            for (String arg : spRaw) {
                String[] line = arg.split("=");
                String k = line[0];//the key part
                if (k.equals(key)) {
                    temp.put(k, line[1]);//the value part
                    break;
                }
            }//for array keys
        }//for all keys
        EnfuserLogger.log(Level.INFO,RoadFilter.class,temp.size() + " keys found.");

        //then parse
        RoadFilter rf = new RoadFilter();

        //speed filter
        String val = temp.get(KEY_SPEEDF);
        if (val != null) {
            String[] split = val.split(",");
            rf.addSpeedFilter(
                    new int[]{Integer.valueOf(split[0]), Integer.valueOf(split[1])}
            );
            EnfuserLogger.log(Level.INFO,RoadFilter.class,"RoadFilter: speed filter added: "
                    + rf.speedLimit[0] + " - " + rf.speedLimit[1] + " km/h");
        }

        //name filter
        val = temp.get(KEY_NAME);
        if (val != null && val.length() > 0) {

            rf.addNameFilter(val);
            EnfuserLogger.log(Level.INFO,RoadFilter.class,
                    "RoadFilter: name (must contain) added: " + val);
        }

        val = temp.get(KEY_SOURCE);
        if (val != null && val.length() > 0) {
            rf.sourceFilter = val;
            EnfuserLogger.log(Level.INFO,RoadFilter.class,
                    "RoadFilter: source filter added: " + val);
        }
        //tier filter
        val = temp.get(KEY_TIERF);
        if (val != null) {
            String[] split = val.split(",");
            rf.addTierFilter(
                    new int[]{Integer.valueOf(split[0]), Integer.valueOf(split[1])}
            );
            EnfuserLogger.log(Level.INFO,RoadFilter.class,"RoadFilter: tier filter added: " 
                    + rf.tierLimit[0] + " - " + rf.tierLimit[1] + " km/h");
        }

        //modifiers=================================================================
        //vehicle classes
        val = temp.get(KEY_VCLASS);
        if (val != null) {//a single value
            rf.flowClass = Integer.valueOf(val);
                EnfuserLogger.log(Level.INFO,RoadFilter.class,
                        "RoadFilter: vehicle class " + rf.flowClass + " added");    
        }

        //amps
        val = temp.get(KEY_AMPS);
        if (val != null) {
            rf.flowAmp = Double.valueOf(val);
             EnfuserLogger.log(Level.INFO,RoadFilter.class,
                        "RoadFilter: flow Amplifier " + rf.flowAmp + " added");    
        }//if amps found

        //Dtime
        val = temp.get(KEY_FLOWDATE);
        if (val != null) {
            rf.flowDt = new Dtime(val);
            EnfuserLogger.log(Level.INFO,RoadFilter.class,
                    "RoadFilter: Dt = " + rf.flowDt.getStringDate());
        }

        //============== MAKE MODS================
        
        if (af_args != null) {
            AreaFilter af = AreaFilter.fromStrings(af_args);
            if (af != null) {
                rf.afilt = af;
            }
        }

        return rf;
    }

    public void addSpeedFilter(int[] minmax) {
        this.speedLimit = minmax;
        EnfuserLogger.log(Level.INFO,RoadFilter.class,"RoadFilter: added a "
                + "speed filter" + minmax[0] + " to " + minmax[1]);

    }

    public void addTierFilter(int[] minmax) {
        this.tierLimit = minmax;
        EnfuserLogger.log(Level.INFO,RoadFilter.class,"RoadFilter: added a "
                + "tier filter" + minmax[0] + " to " + minmax[1]);
    }


    public void addFlowSourceFilter(String source) {
        this.sourceFilter = source;
    }

    
    public void applyPresetFlowModifications() {
        if (this.flowClass==null || this.flowAmp ==null) {
              EnfuserLogger.log(Level.INFO,RoadFilter.class,
                     "RoadFilter: nothing to modify.");
            return;
        }//nothing to do
        
        if (this.flowDt!=null) {
             EnfuserLogger.log(Level.INFO,RoadFilter.class,
                     "Adding temporally specific flow modifiers.");
            applyFlowMultiplications(this.flowDt, this.flowClass, this.flowAmp.floatValue());
             
        } else {
             EnfuserLogger.log(Level.INFO,RoadFilter.class,
                     "Adding singular DAVG modifiers.");
             applyFlowMultiplications_DAVG(this.flowClass, this.flowAmp.floatValue());
        }
    }
 
    public void applyFlowMultiplications(Dtime dt, int classI, float amp) {
            for (RoadPiece rp : this.selection.values()) {
               rp.multiplyFlow_temporal(classI, dt, amp);     
            }//for rp
    }
    
    
    public void applyFlowMultiplications_DAVG(int classI, float amp) {
        for (RoadPiece rp : this.selection.values()) {
            rp.editFlow_DAVG(classI, amp, FlowEstimator.EDIT_MULTI); 
        }  
    }

    public HashMap<Integer, RoadPiece> formFilteredList(OsmLayer ol) {
        this.selection.clear();
        for (RoadPiece rp : ol.allRPs().values()) {
            boolean accept = this.isAcceptable(rp);
            if (accept) {
                this.selection.put(rp.ref_number_int, rp);
            }
            
        }//for roads
        
        if (this.afilt!=null ){
            ArrayList<RoadPiece> close= afilt.getCloseBy(ol);
            if (close!=null ) {//this means that a very closeBy search was applicable (point, rad<10m)
                this.selection.clear();
                for (RoadPiece rp: close) {
                    this.selection.put(rp.ref_number_int, rp);
                }
            }
        }   
        
        EnfuserLogger.log(Level.INFO,RoadFilter.class,"RoadFilter: Selected " 
                + selection.size() + " roadPieces of " + ol.allRPs().size());
        return this.selection;
    }

    private boolean isAcceptable(RoadPiece rp) {
        //filters - speed
        if (speedLimit != null) {
            if (rp.speedLimit < speedLimit[0] || rp.speedLimit > speedLimit[1]) {
                return false;
            }
        }

        //filters tier
        if (tierLimit != null) {
            if (rp.tier() < tierLimit[0] || rp.tier() > tierLimit[1]) {
                return false;
            }
        }
        //source
        if (this.sourceFilter != null) {
            if (this.sourceFilter.equals(rp.flowSource())) {
                return false;
            }
        }

        //name filter
        if (this.nameMustContain != null && rp.name != null && !rp.name.contains(this.nameMustContain)) {
            return false;//name filter exists but was not found from the RP name.  
        }

        //Boundaries
        double lat = rp.getMidLat();
        double lon = rp.getMidLon();
        // if (b!=null && !b.intersectsOrContains(lat, lon)) return false;

        //the final hurdle
        boolean areaOK = this.afilt.isIncluded(lat, lon);
        
        return areaOK; //passed every filter if this is true

    }



    /**
     * Create a simple GeoGrid to show the selected roads.The higher the value,
 the higher the road tier.
     *
     * @param b the bounds
     * @param res_m resolution for grid.
     * @param davg if true, then the daily average vehicles is shown
     * @return GeoGrid.
     */
    public GeoGrid getCrudeSelectionGrid(Boundaries b, float res_m, boolean davg) {
        AreaNfo nfo = new AreaNfo(b, res_m, Dtime.getSystemDate_utc(false, Dtime.ALL));
        int H = nfo.H;
        int W = nfo.W;
        float[][] inc = new float[H][W];

        for (RoadPiece rp : this.selection.values()) {
            float value = rp.tier() + 2;
            if (davg) {
                value = rp.getDAVGS()[FlowEstimator.IND_CAR];
            }
            //interpolate
            int steps = (int) (1.5 * rp.crudeLength_m() / res_m) + 1;
            ArrayList<Coordinate> latlons = Coordinate.interpolateSteps(rp.cc, steps);

            for (Coordinate c : latlons) {

                int h = nfo.getH(c.lat);
                int w = nfo.getW(c.lon);

                try {
                    inc[h][w] = value;
                } catch (IndexOutOfBoundsException e) {

                }
            }//for latlons
        }//for rps

        GeoGrid g = new GeoGrid(inc, Dtime.getSystemDate(), b);
        return g;
    }

    public void addNameFilter(String val) {
        this.nameMustContain = val;
    }

    
    public void addPolygonFilter(ArrayList<double[]> arr) {
        this.afilt.setAsPolygonFilter(arr);
        EnfuserLogger.log(Level.INFO,RoadFilter.class,
                "RoadFilter: added a polygon filter with " + afilt.p.npoints + " points.");
    }
    
    public void addRadiusPointFilter(Coordinate c, float radm) {
       addRadiusPointFilter(c.lat, c.lon, radm);
    }
    
        public void addRadiusPointFilter(float lat, float lon, float rad_m) {
        this.afilt.setAsRadiusFilter(lat, lon, rad_m);
        EnfuserLogger.log(Level.INFO,RoadFilter.class,"RoadFilter: added a "
                + "radius around " + lat + ", " + lon + ", rad_m = " + (int) rad_m);
    }

    public void addPolygonFilter_cc(ArrayList<Coordinate> coords) {
        ArrayList<double[]> arr = new ArrayList<>();
        for (Coordinate c:coords) {
            arr.add(new double[]{c.lat,c.lon});
        }
        addPolygonFilter(arr);
    }

}
