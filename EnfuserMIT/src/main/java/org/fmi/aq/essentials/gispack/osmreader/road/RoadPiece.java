/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.essentials.gispack.osmreader.road;

import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.gispack.flowestimator.FlowArray;
import org.fmi.aq.essentials.gispack.osmreader.core.Coordinate;
import org.fmi.aq.essentials.gispack.osmreader.core.Rotator;
import org.fmi.aq.essentials.gispack.osmreader.core.Way;
import java.io.Serializable;
import java.util.HashMap;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_MOTORWAY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_MWAY_LINK;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_TRUNK;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_TRUNK_LINK;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_PRIMARY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_PRIMARY_LINK;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_SECONDARY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_TERTIARY;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_RESID;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.HW_LSTREET;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.ftools.FastMath.degreeInMeters;
import org.fmi.aq.essentials.gispack.flowestimator.FlowEstimator;
import org.fmi.aq.essentials.gispack.flowestimator.SimpleFlowArray;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;

/**
 * This class represent a segment of a road. This means that a road often
 * consists multiple RoadPieces.
 *
 * The most notable characteristics of a road are available as properties,
 * including the number of lanes, speed limits, surface properties, amount of
 * traffic signals etc.
 *
 * Each RoadPiece has a unique reference number which is important for
 * geo-coding and finding the proper roadPieces for a number of use-cases.
 *
 * @author johanssl
 */
public class RoadPiece implements Serializable {

    private static final long serialVersionUID = 7386472293822776147L;//Important: change this IF changes are made that breaks serialization! 
    public static HashMap<String, Boolean> UNKNOWN_SPEEDS = new HashMap<>();//collect Strings that do not convert into integers.    
    public static HashMap<String, Integer> GIVEN_SPEEDS = new HashMap<>();

//vehicular flows==============
    private FlowArray flows;//older feature: store vehicular flows directly to RoadPieces. This is a difficult class to use.
    private SimpleFlowArray simpFlow;//new feature: store vehicular flows directly to RoadPieces... with easier-to-use methods!
    
    public short buslines = 0;
    public byte surface = SURFACE_PAVEMENT;
    public short speedLimit = 51;
    public String speedString;
    public byte roadclass = HW_SECONDARY;

    public String name = "";
    public long ref_number = 0;
    public int ref_number_int = -1;

    public boolean tunnel = false;

    public final byte roadWidth_m;
    public final byte renderedWidth_pixels;
    public final float renderedWidthPixels_inverse;//for emission modelling (per m2)

    final short crudeDist_m;
    public byte inclinationAngle;//describes the angle towards uphill (>0) and downhill (<0).
// Note: in case the road is twoWay there is a traffic flow with an opposite inclination
    public byte lanes = 2;
    public boolean oneway = false;
    public final byte wayDir_5degs;

    public float[][] trafficLight_latlons;
    public Coordinate[] cc = new Coordinate[2];
    long firstNode_ref=0;
    long lastNode_ref=1;
    
    public static final byte SURFACE_PAVEMENT = 0;
    public static final byte SURFACE_COBBLE = 1;
    public static final byte SURFACE_DIRT = 2;

    public static final String[] SURF_NAMES = {
        "paved",
        "cobble",
        "dirt"
    };

//some holders for for flow data than can be used for flow modelling
//these two below use the FlowEstimator time indexing: 24x3 elements.
    public float[] congestionProfile = null;//main user: HERE.com
    public float[][] measuredFlowProfiles = null;//actual flow measurements
    public float[] teleFlowProfile = null;//TBD
    public float navigationIndex = -1;//main user: Google Navigation API.

    public RoadPiece(Way way, Rotator rot, byte rc, float res_m) {
        //class

        //start-end
        double[] start = way.coords.get(0);
        double[] end = way.coords.get(way.coords.size() - 1);
        
        if (way==null || way.nodeRefs==null ||way.nodeRefs.isEmpty()) {
            this.firstNode_ref = -1;
            this.lastNode_ref =-1; 
        } else {
            this.firstNode_ref = way.nodeRefs.get(0);
            this.lastNode_ref = way.nodeRefs.get(way.nodeRefs.size()-1); 
        }
        
       
        
        float lat1 = (float)start[0];// * Node.TO_DEGREE;
        float lon1 = (float)start[1];// * Node.TO_DEGREE;

        float lat2 = (float)end[0];// * Node.TO_DEGREE;
        float lon2 = (float)end[1];// * Node.TO_DEGREE;

        cc[0] = new Coordinate(lat1, lon1);
        cc[1] = new Coordinate(lat2, lon2);
        //estimate direction
        double dlat_m = (lat2 - lat1) * degreeInMeters;
        double dlon_m = (lon2 - lon1) * degreeInMeters * rot.cos_lat[(int) Math.abs(lat1)];

        double dist = Math.sqrt(dlat_m * dlat_m + dlon_m * dlon_m);
        if (dist > 0) {
            double dir = rot.getDirection(dlat_m, dlon_m, dist, 5);//dir of the segment
            this.wayDir_5degs = (byte) (dir / 5);
        } else {
            this.wayDir_5degs = -1;
        }

        this.crudeDist_m = (short) Math.min(dist, 30000);

        this.roadclass = rc;
        int tier = tier(roadclass);
        this.ref_number = way.wayID;

        if (tier == RC_TIER1) {
            this.speedLimit = 31;
            lanes = 2;
            oneway = false;

        } else if (tier == RC_TIER2) {
            this.speedLimit = 41;
            lanes = 2;
            oneway = false;

        } else if (tier == RC_TIER3) {
            this.speedLimit = 51;
            lanes = 2;
            oneway = false;

        } else if (tier == RC_TIER4) {
            this.speedLimit = 61;
            lanes = 2;
            oneway = false;

        } else if (tier == RC_TIER5) {
            this.speedLimit = 81;
            lanes = 2;
            oneway = true;
        } else {
            EnfuserLogger.log(Level.WARNING,this.getClass(),
                  "RoadPiece: roadclass is NOT A ROAD!");
        }

        //speed
        String speed = way.tags.get("maxspeed");
        if (speed != null) {
            this.speedString = speed;
            try {
                double v = Double.valueOf(speed);
                this.speedLimit = (short) v;
            } catch (Exception e) {
                EnfuserLogger.log(Level.FINER,this.getClass(),"RoadPiece: maxSpeed parse fail! " + speed);
                if (speed != null) {
                    UNKNOWN_SPEEDS.put(speed, Boolean.TRUE);

                    //try to get a given speed
                    Integer sp = GIVEN_SPEEDS.get(speed);
                    if (sp != null) {
                        EnfuserLogger.log(Level.FINER,this.getClass(),"\t found the speed from preset hash: " + sp);
                        this.speedLimit = sp.shortValue();
                    }
                }
            }
        }

        String lanesS = way.tags.get("lanes");
        if (lanesS != null) {
            try {
                double d = Double.valueOf(lanesS);
                this.lanes = (byte) d;
            } catch (NumberFormatException eee) {
            };
        }
        String onewayS = way.tags.get("oneway");
        if (onewayS != null && onewayS.equals("yes")) {
            this.oneway = true;
        }

        //surface
        String surf = way.tags.get("surface");
        if (surf != null) {

            if (surf.contains("cobble") || surf.contains("stone")) {
                this.surface = SURFACE_COBBLE;
            } else if (surf.contains("grass") || surf.contains("dirt") || surf.contains("unpaved") || surf.contains("gravel")) {
                this.surface = SURFACE_DIRT;
            }

        }

        //buslines
        this.buslines = (short) way.buslines.size();

        String addr = way.tags.get("name");
        if (addr != null) {
            //EnfuserLogger.log(Level.FINER,this.getClass(),"\t street name: "+ addr);
            this.name = addr;
        }

        //check tunnel
        String val = way.tags.get("tunnel");
        if (val != null && val.equals("yes")) {
            this.tunnel = true;
        } else {
            this.tunnel = false;
        }

        //traffic signals (lights)
        if (way.trLights_latlon != null) {
            this.trafficLight_latlons = new float[way.trLights_latlon.size()][2];
            for (int i = 0; i < way.trLights_latlon.size(); i++) {
                float[] latlon = way.trLights_latlon.get(i);
                this.trafficLight_latlons[i] = latlon;
            }
        }

        
        if (tier == RC_TIER1)  {
            this.renderedWidth_pixels =1;
        } else  {
           
           if (tier > RC_TIER2 && res_m < 4) {
               this.renderedWidth_pixels =3;
           } else {
               this.renderedWidth_pixels =2;
           }
        } 
        
        this.roadWidth_m = (byte) ( this.renderedWidth_pixels * res_m);
        this.renderedWidthPixels_inverse = 1f / (float) (this.renderedWidth_pixels);

    }

    /**
     * get an estimate on the roadPiece length in meters. This is based on
     * start-end -point coordinates.
     *
     * @return estimated length in meters.
     */
    public float crudeLength_m() {
        return this.crudeDist_m;
    }

    public float getMidLat() {
        return 0.5f * (cc[0].lat + cc[1].lat);
    }

    public float getMidLon() {
        return 0.5f * (cc[0].lon + cc[1].lon);
    }

    public boolean hasDirection() {
        return this.wayDir_5degs >= 0;
    }

    /**
     * Return an estimate on the roadPiece's direction in degrees. This is based
     * on start-end -point coordinates.
     *
     * @return float degree value [0,360[.
     */
    public float getWayDirection_degs() {
        return this.wayDir_5degs * 5f;
    }
    
    public int getAngleDifference(RoadPiece rp) {
        
        if (!this.hasDirection() ||!rp.hasDirection()) return -1;
        
        float angle180 = this.getWayDirection_degs();
        if (angle180>180) angle180 -=180; //if the angle is 10 or 190 the direction of the road is the same.
        
        float angle180_2 = rp.getWayDirection_degs();
        if (angle180_2>180) angle180_2 -=180;
        
        return (int)Math.abs(angle180 - angle180_2);
        
    }

    public void printout() {
        String s = "roadType = " + this.roadclass + ", speedLimit = " 
                + this.speedLimit + ", surface = " + SURF_NAMES[this.surface];
        s += ", lanes = " + this.lanes + ", oneWay = " + this.oneway;
        EnfuserLogger.log(Level.FINER,this.getClass(),"RoadPiece: " + s);
    }

    public final static int RC_TIER1 = 0;
    public final static int RC_TIER2 = 1;
    public final static int RC_TIER3 = 2;
    public final static int RC_TIER4 = 3;
    public final static int RC_TIER5 = 4;

    /**
     * get a 5 tier road classification. The larges tier (4) corresponds to
     * motorways when the smallest tier (0) is more tertiary roads and
     * residential streets
     *
     * @param osm the roadClass osmType.
     * @return int tier
     */
    public static int tier(byte osm) {
        if (osm == HW_MOTORWAY || osm == HW_TRUNK) {
            return RC_TIER5;
        } else if (osm == HW_PRIMARY || osm == HW_MWAY_LINK || osm == HW_TRUNK_LINK) {
            return RC_TIER4;
        } else if (osm == HW_PRIMARY_LINK) {
            return RC_TIER3;
        } else if (osm == HW_SECONDARY) {
            return RC_TIER2;
        } else if (osm == HW_TERTIARY || osm == HW_RESID || osm == HW_LSTREET) {
            return RC_TIER1;
        } else {
            EnfuserLogger.log(Level.WARNING,RoadPiece.class,
                  "RoadPiece: roadclass is NOT A ROAD!");
            return -1;
        }

    }

    /**
     * Is the RoadPiece a link road?. A link road is usually short with only a
     * few lanes.
     *
     * @param rp the RoadPiece
     * @return boolean value: true for link roads.
     */
    public static boolean isLink(RoadPiece rp) {
        byte osm = rp.roadclass;
        if (osm == HW_MWAY_LINK || osm == HW_TRUNK_LINK || osm == HW_PRIMARY_LINK) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * For the lowest tier (0) the tertiary roads are the most notable ones in
     * terms of traffic volumes. This method can be used to distinguish the
     * lesser tier-0 types from the tertiary roads.
     *
     * @param rp the RoadPiece
     * @return boolean value: true in case the road is of the two lesser road
     * types.
     */
    public static boolean isLesserRoad(RoadPiece rp) {
        byte osm = rp.roadclass;
        if (osm == HW_RESID || osm == HW_LSTREET) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * get a 5 tier road classification. The larges tier (4) corresponds to
     * motorways when the smallest tier (0) is more tertiary roads and
     * residential streets
     *
     * @return int tier
     */
    public int tier() {
        return tier(this.roadclass);

    }

    public float getHourlyFlow(Dtime dt, int classI) {
        return this.simpFlow.getValue(classI, dt);
        //return this.flows.getValue(classI, dt);
    }

   public float[] getHourlyFlows(float dynFlowFactor, float[] temp, Dtime dt, Dtime next, int mins) {
       float[] fl = getHourlyFlows(temp, dt, next, mins);
       for (int c:FlowEstimator.VC_INDS) {
           fl[c]*=dynFlowFactor;
       }
       return fl;
    }
    
    public float[] getHourlyFlows(float[] temp, Dtime dt, Dtime next, int mins) {
        return this.simpFlow.getValues_allClasses_smooth(temp, dt, next, mins);
        //return this.flows.getValues_allClasses_smooth(1f, temp, dt, next, mins);
    }

    public float[] getDAVGS() {
        return this.simpFlow.getDAVGs();
        //return this.flows.getDailyAverages(FlowEstimator.A_FRIDAY);
    }

    public void editFlow_DAVG(int classI, float amp, int modType) {

            if (modType == FlowEstimator.EDIT_MULTI) {
                this.simpFlow.multiplyAll(amp, classI);
                
            } else if (modType == FlowEstimator.EDIT_SET) {
                this.simpFlow.normalizeDAVGto(amp,classI);
                
            } else {//edit add -assume this is for DAVG
                float davg = this.simpFlow.getDAVG(classI);
                float newDavg = davg + amp;
                this.simpFlow.normalizeDAVGto(newDavg,classI);
               
            }
            return;
        
         //this.flows.editClassAmplifier_davg(classI, amp, modType);
    }

    public void multiplyFlow_temporal(int classI, Dtime dt,float hourlyAmp) {
       this.simpFlow.multiplyValue(hourlyAmp, classI, dt, null);
    }
    
    public void multiplyFlow_temporal(int classI, Dtime dt, Integer hour, float hourlyAmp) {
       this.simpFlow.multiplyValue(hourlyAmp, classI, dt, hour);
    }

    public String flowSource() {
         return this.simpFlow.source;
         //return this.flows.source;
    }

    public void clearFlows() {
        this.simpFlow =null;
    }

    public boolean hasFlows() {
        return (this.simpFlow!=null);
    }

    public void normalizeToDAVG(int classI, double daily) {
           this.simpFlow.normalizeDAVGto((float)daily,classI);
        //this.flows.editClassAmplifier_davg(classI, (float)daily, FlowEstimator.EDIT_SET);
    }

    public SimpleFlowArray hardCopyFlow() {
        return this.simpFlow.hardCopy();
    }

    public void setFlow(SimpleFlowArray bf) {
        this.simpFlow = bf;
    }

    public void reformFlowData() {
        if (this.flows!=null && this.simpFlow==null) {
            this.simpFlow = new SimpleFlowArray(flows);
            this.flows =null;
        }
    }

    public static void reformAllFlowData(OsmLayer ol) {
        for (RoadPiece rp: ol.allRPs().values()) {
            rp.reformFlowData();
        }
    }

    public void stackFlows(RoadPiece rp, float f) {
        this.simpFlow.stack(rp.simpFlow,f);
    }

    public String getOneLiner() {
       String s = this.name+", class="+this.tier() +", lanes="+this.lanes+", v="+this.speedLimit;
       if (this.simpFlow!=null) {
           float[] davgs = this.getDAVGS();
           int vehc = (int)davgs[FlowEstimator.IND_CAR];
           s+= ", "+vehc +"/d";
           if (this.oneway)s+="(oneWay)";
       }
       return s;
    }
}
