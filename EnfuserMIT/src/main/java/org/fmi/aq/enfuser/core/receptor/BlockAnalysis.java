/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.receptor;

import org.fmi.aq.enfuser.meteorology.RoughnessLength;
import org.fmi.aq.enfuser.meteorology.RoughPack;
import java.io.Serializable;
import org.fmi.aq.enfuser.datapack.source.DBline;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.enfuser.core.FusionCore;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import static org.fmi.aq.enfuser.ftools.FastMath.FM;
import org.fmi.aq.essentials.geoGrid.ByteGeoGrid;
import static org.fmi.aq.essentials.geoGrid.ByteGeoGrid.getValue_OOBzero;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getH;
import static org.fmi.aq.essentials.geoGrid.GeoGrid.getW;
import java.util.ArrayList;
import org.fmi.aq.enfuser.ftools.RotationCell;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.essentials.gispack.osmreader.core.Building;
import org.fmi.aq.essentials.gispack.osmreader.core.OSMget;
import org.fmi.aq.essentials.gispack.osmreader.core.OsmLayer;
import org.fmi.aq.essentials.gispack.osmreader.road.RoadPiece;
import static org.fmi.aq.essentials.gispack.osmreader.core.Tags.LU_UNDEF;
import static org.fmi.aq.enfuser.core.receptor.BlockAnalysis.SECTOR_ANGLE;
import static org.fmi.aq.enfuser.meteorology.RoughnessLength.assessRoughness_simple;
import static org.fmi.aq.enfuser.solar.SolarBank.AZ;
import static org.fmi.aq.enfuser.solar.SolarBank.ZEN;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.enfuser.meteorology.RoughnessLength.RL_SUB1;
import static org.fmi.aq.enfuser.meteorology.RoughnessLength.ROUGHNESS;

/**
 *
 * @author johanssl
 */
public class BlockAnalysis implements Serializable{

    public final static String TEMPORARY_LOCATION = "TEMPORARY";

    public byte[] BH;//used in WallReduction height check & wind mirroring check
    public byte[] BDIST;//used in wallReduction and init
    public byte[] BLOCKSTR;
    public byte[] BLOCKER_OP;//opposite product
    public byte[] VEGE;
    public byte overallBH = 0;

    private float[] directedRL;
    public float vegeDepth_g0 = 0;

    public float roughnessLength = RoughPack.RL_DEFAULT;//will be assessed in constructor.
    private short rlCounter = 0;

    public static final short SECTOR_ANGLE = 10;
    public static final short N_SECTS = 360 / SECTOR_ANGLE;

    boolean atBuilding = false;
    public byte groundZero_type = LU_UNDEF;
    public byte groundZero_func = LU_UNDEF;
    public Building groudZero_building;

    public RoadPiece closestRoad = null;

    public float closestBuilding_m = Float.MAX_VALUE;
    public float closestRoad_m = Float.MAX_VALUE;
    private byte closestRoad_sectInc = -1;
    private boolean customBlockDistClears = false;

    public short canyonCrossDir = -1;
    public short canyonParallelDir = -1;
    public boolean dummy =false;
    
    
     //urban wind modifications (street canyons etc===============
    public static float[] NEIGHBOR_SECTOR_WEIGHTS = {0.7f, 0.15f, 0.15f};
    
    public BlockAnalysis(OsmLayer mp, FusionOptions ops, double lat, double lon, DBline dbl) {
       int N = 10;
       float vegeResScaler = 1f;
        if (dbl != null && dbl.sectorClearDists_30deg != null) {
            this.customBlockDistClears = true;//some specific sectors are manually cleared for obstacles
        }        //ground zero
        
        if (mp != null) {
            this.groundZero_type = OSMget.getSurfaceType(mp, lat, lon);
            this.groundZero_func = OSMget.getFunctionaltype(mp, lat, lon);
            if (OSMget.isBuilding(this.groundZero_type)) {
                this.atBuilding = true;
                this.closestBuilding_m = 0;//must be flagged since getWindShield() will not be activated. this is important for Env. variable resolution mechanics!

                int h = mp.get_h(lat);
                int w = mp.get_w(lon);
                this.groudZero_building = mp.getBuilding(h, w);

            } else if (!OSMget.isHighway(this.groundZero_type)) {//not a building not a road => check 10m vegetation.

                try {
                    this.vegeDepth_g0 = mp.getFastVegeDepth(lat, lon);//the value must be between 0 and 1 originally
                } catch (Exception e) {

                }
            }
           N = (int) (ops.BLOCK_ANALYSIS_M / mp.resm); //5m data for OSM, N cells evaluated per "ray"
           vegeResScaler = mp.resm/5f;//this is 1 with default maps. If 10m maps then this is 2.0.
        }

        //init bscans
        this.directedRL = new float[N_SECTS];
        short[] drlCounts = new short[N_SECTS];

        this.BH = new byte[N_SECTS];//used in WallReduction height check & wind mirroring check
        this.BDIST = new byte[N_SECTS];//used in wallReduction and init
        for (int i = 0; i < N_SECTS; i++) {
            BDIST[i] = 125;
        }

        this.BLOCKSTR = new byte[N_SECTS];
        this.BLOCKER_OP = new byte[N_SECTS];
        this.VEGE = new byte[N_SECTS];
        
       
        float[] rotated_c = new float[2];

        for (int wd = 0; wd < 360; wd += SECTOR_ANGLE) {//for wd

            int customClearDist = -1;
            if (this.customBlockDistClears && dbl!=null) {
                customClearDist = dbl.getCustomBlockDistClear(wd);
            }

            int sec = getIndex(wd);
            if (mp == null) {//there is no map data for this location.
                rlCounter = 1;
                //directed rl
                drlCounts[sec]++;
                this.directedRL[sec] += this.roughnessLength;
                dummy=true;
                continue;
            }

            
            for (int d = 1; d < N; d++) {// up to 125m away, straight to the north
                // if (d==0 && wd!=0) continue;//zero distance needs to be evaluated just once when wd =0.
                if (atBuilding) {
                    continue;//skip all 
                }
                float h_m = d * (float) mp.resm;
                // then we, rotate the indexes so that they are directed towards the wind
                rotated_c = FM.rotateHW(d, 0, wd, rotated_c);
                double currLat = lat + rotated_c[0] * mp.dlat;
                double currLon = lon + rotated_c[1] * mp.dlon;

                int h = mp.get_h(currLat);
                int w = mp.get_w(currLon);
                
                    if (mp.oobs(h, w)) continue;
                    
                    byte osm = mp.luSurf[h][w];
                    Building b = null;
                    if (OSMget.isBuilding(osm) && h_m > customClearDist) {//in case a custom Clear distance has been set then Building b is given as null, if distance is smaller than the manual threshold. Note that the clearDist is -1 as default.
                        b = mp.getBuilding(h, w);
                        if (h_m < this.closestBuilding_m) {
                            this.closestBuilding_m = h_m;
                        }
                        //update blocks 
                        if (b != null) {
                            float height = b.getHeight_m(mp);
                            if (b.area_m2 < 200) {
                                height *= (b.area_m2 / 200f);
                            }
                            if (height < 0) {
                                height = 0;
                            }
                            float dist = Math.max(ops.minBlockDistance, h_m);

                            if (dist < this.BDIST[sec]) {
                                this.BH[sec] = (byte) Math.min(125, height);
                                this.BDIST[sec] = (byte) Math.min(125, dist);
                            }
                        }//if building 
                    }
                    
                    RoadPiece rp = null;
                    if (OSMget.isHighway(osm)) {
                        rp = mp.getRoadPiece(h, w);
                        if (h_m < this.closestRoad_m) {
                            this.closestRoad = rp;
                            this.closestRoad_m = h_m;
                            this.closestRoad_sectInc = (byte) sec;
                        }
                    }

                    float vegeI = mp.getFastVegeDepth(lat, lon);//e [0,1]
                    float vegeDistScaler = 1f;
                    if (h_m>30) vegeDistScaler = 30f/h_m;//100m => 0.1
                    int veg=(int)(VEGE[sec] + 20f*vegeI*vegeDistScaler*vegeResScaler);//so, after 50m of thick forest this will achieve value of 100 that is max.
                    if (veg>100) veg=100;
                    VEGE[sec]=(byte)veg;
                    
                    //update roughness
                    float rl = assessRoughness_simple(rp, b, osm, vegeI, h_m, mp);
                    this.roughnessLength += rl;//sum, average later on.
                    this.rlCounter++;

                    //directed rl
                    drlCounts[sec]++;
                    this.directedRL[sec] += rl;
            }//for eval cells
        }//for wd

        //process strengths
        for (int sec = 0; sec < N_SECTS; sec++) {
            float d = Math.max(0, this.BDIST[sec] - 5);//tolerance reduction
            float distFunc = 1f - d / 100f;
            if (distFunc < 0) {
                distFunc = 0;
            }

            float hFunc = (this.BH[sec] - 3) / 14f;//tolerance reduction for small buildings
            if (hFunc > 1) {
                hFunc = 1;
            }
            if (hFunc < 0) {
                hFunc = 0;
            }

            float stren = distFunc * hFunc;//combine, cannot exceed 1
            this.BLOCKSTR[sec] = (byte) (100 * stren);
        }

        //dual blocker - symmetric directions
        for (int sec = 0; sec < N_SECTS; sec++) {
            int mirSec = this.getOppositeSector(sec);
            this.BLOCKER_OP[sec] = this.BLOCKSTR[mirSec];//(byte) prod;
        }

        //SOFTENERS=========
        for (int sec = 0; sec < N_SECTS; sec++) {
            float softStren = 0;
            float softOppo = 0;
            int j = -1;

            int secAdd = 0;
            for (float w : NEIGHBOR_SECTOR_WEIGHTS) {//0,-1,1 => -20 degs and +20 degs are linearly combined with 0 deg data.
                j++;
                if (j == 1) {
                    secAdd = -1;
                }
                if (j == 2) {
                    secAdd = 1;
                }

                int s = this.getAnotherSector(sec, secAdd);
                softOppo += w * (float) this.BLOCKER_OP[s];
                softStren += w * (float) this.BLOCKSTR[s];

            }//for softers

            this.BLOCKSTR[sec] = (byte) softStren;
            this.BLOCKER_OP[sec] = (byte) softOppo;
        }//for sects

        
        this.roughnessLength /= rlCounter;//into average
        if (this.roughnessLength > RoughnessLength.MAX_RL) {
            this.roughnessLength = RoughnessLength.MAX_RL;
        }

        for (int i = 0; i < this.directedRL.length; i++) {
            this.directedRL[i] /= drlCounts[i];
            if (this.directedRL[i] > RoughnessLength.MAX_RL) {
                this.directedRL[i] = RoughnessLength.MAX_RL;
            }
            if (this.atBuilding) {
                this.roughnessLength =ROUGHNESS[RL_SUB1];
                this.directedRL[i] = ROUGHNESS[RL_SUB1];
            }
        }

            EnfuserLogger.log(Level.FINEST,BlockAnalysis.class,
                    "RoughnessLength = " + editPrecision(this.roughnessLength, 4));
        

        //Legacy metrics, check street canyon mirroring angles. Not really used anymore.  
        if (this.closestRoad != null && this.closestRoad_m < BDIST[closestRoad_sectInc]) {// there is a road and it is not behind an object.
            double minAngle = (this.closestRoad.wayDir_5degs * 5.0);
            double otherAngle = minAngle + 180;
            if (otherAngle > 360) {
                otherAngle -= 360;
            }
            minAngle = Math.min(minAngle, otherAngle);

            this.canyonCrossDir = (short) (minAngle + 90);
            this.canyonParallelDir = (short) minAngle;
        }

    }
    
    public String onelinerSummary() {
        String roadD = "N/A";
        if (this.closestRoad!=null) roadD = (int)this.closestRoad_m
                +", name="+this.closestRoad.name;
        
        String line = "atBuilding="+this.atBuilding+";distance to road="+roadD;
        return line;
    }
    
    
    public boolean atBuilding() {
        return this.atBuilding;
    }
    
    public static int getIndex(float windDir) {
        if (windDir > 359) {
            windDir = 359;
        }
        int i = (int) (windDir / SECTOR_ANGLE);
        //if (i>= this.blockDist.length) i = this.blockDist.length -1;
        return i;
    }

    private final static float[] DIR_W = {0.05f, 0.35f, 0.3f, 0.3f};
    private final static float[] DIR_SECT_ADDS = {0, 0, -SECTOR_ANGLE, SECTOR_ANGLE};

    public float getDirectedRoughness(float windDir) {

        float rl_combined = 0f;
        int j = -1;

        for (float dirAdd : DIR_SECT_ADDS) {
            j++;

            if (j == 0) {
                rl_combined += this.roughnessLength * DIR_W[j]; // overall, "nullDir" roughness added in the first element.
                continue;
            }

            //combine directed products
            float dir = windDir + dirAdd;
            if (dir < 0) {
                dir += 360;
            }
            if (dir >= 360) {
                dir -= 360;
            }

            int sec = this.getIndex(dir);
            float drl = this.directedRL[sec];

            rl_combined += drl * DIR_W[j];
        }

        //float rl = RL_DIR*drl + (1f-RL_DIR)*this.roughnessLength;
        return rl_combined;

    }

    private int getAnotherSector(int sec, int add) {
        int s = sec + add;
        if (s < 0) {
            s += N_SECTS;
        } else if (s > N_SECTS - 1) {
            s -= N_SECTS;
        }
        return s;
    }

    private int getOppositeSector(int sec) {
        int s = sec + N_SECTS / 2;
        if (s > N_SECTS - 1) {
            s -= N_SECTS;
        }
        return s;
    }

    protected final static float BLOCKER_DIST_MAX = 250;
    /**
     * Factor of 0 means no effect (no reduction). There are multiple
     * components, and if any of them are close to zero the net effect, which is
     * multiplicative, will be zero.
     *
     * @param windDir wind direction in degrees (from, 0 for north, 90 for east,
     * etc.)
     * @param evalPointDistance_m distance to the point of interest. If the
     * "blocker" if farther than this in this direction, then this method return
     * 0 (no effect)
     * @param ops options
     * @param obsH observation height above ground [m]
     * @return float value describing a "damping" factor: 1 for complete block
     * (the point of interest cannot affect concentrations at the BA's ground
     * zero. A value of 0 means the opposite, the surrounding landscape has no
     * effect.
     */
    public float getWallReductionFactor_zeroNoRed(float windDir, float evalPointDistance_m,
            float obsH, FusionOptions ops) {

        if (evalPointDistance_m < 10) {
            return 0f;//at these distances near walls there can also be unintentional ARTIFACTS to be had.   
        } else if (evalPointDistance_m> BLOCKER_DIST_MAX) {
            return 0f;//far away, the wall becomes irrelevant
        }
        int sec = getIndex(windDir);
        float dist_toB = this.BDIST[sec];
        if (dist_toB > evalPointDistance_m) {
            return 0f;//no mods since the point is not blocked at all
        }
        float blockHeight = BH[sec];
        if (blockHeight < obsH) {
            return 0;
        }

        float factor_blockDist = (BLOCKER_DIST_MAX - evalPointDistance_m) / BLOCKER_DIST_MAX;//max 1 given by dist 0
        float fact = 0.02f * this.BLOCKSTR[sec] * factor_blockDist;
        fact*=ops.blockerStrength;
        if (fact > 1) {
            fact = 1;
        }
        return fact;
    }


    private static final float[] SOLAR_BLOCKED = {-1, -1, 0};

    public static float[] getSolarPower(double lat, double lon, Dtime dt,
            FusionCore ens, boolean print, ByteGeoGrid elev) {

        //get soalr specs
        float[] solar = ens.getStoreSolarSpecs(dt, lat, lon);

        double dir = solar[AZ];
        double zenithDeg = solar[ZEN];
        
        if (print) {
            EnfuserLogger.log(Level.FINER,BlockAnalysis.class,"Sunray block analysis,"
                    + " azmuth angle / zenith angle :" + (int) dir + ", " + zenithDeg);
            EnfuserLogger.log(Level.FINER,BlockAnalysis.class,lat + "," + lon + " " + dt.getStringDate());
        }

        if (zenithDeg <= 0) {
            if (print) {
                EnfuserLogger.log(Level.FINER,BlockAnalysis.class,
                        "Sun does not shine this time at this location.");
            }
            return solar;
        }

        //cells to analyze
        float[] latlon_rotated = null;
        ArrayList<RotationCell> cells = FM.getSolarCheckCells();

        float initElevation_m = 0;
        if (elev != null) {
            initElevation_m = (float) getValue_OOBzero(elev, lat, lon);
        }
        if (initElevation_m > 0) {
            if (print) {
                EnfuserLogger.log(Level.FINER,BlockAnalysis.class,
                        "Elevation (non-zero) availalable: " + initElevation_m + "m");
            }
        }

        double initElev_noBuilding = initElevation_m;
        OsmLayer osmMask = null;
        byte osm;

        int n = 0;
        for (RotationCell e : cells) {
            n++;

            latlon_rotated = e.getOffsets_degrees_m(latlon_rotated, (float) dir, FM.getFastLonScaler((float) lat));
            double currLat = lat + latlon_rotated[0];
            double currLon = lon + latlon_rotated[1];

            if ((n - 1) % 30 == 0) {
                osmMask = ens.mapPack.getOsmLayer(currLat, currLon);  
            }
            if (osmMask==null) continue;
            int h = getH(osmMask.b, currLat, osmMask.dlat);
            int w = getW(osmMask.b, currLon, osmMask.dlon);
            osm = OSMget.getSurfaceType(osmMask, currLat, currLon);

            //special case: first cell
            if (n == 1) {
                if (OSMget.isBuilding(osm)) {
                    Building b = osmMask.getBuilding(h, w);
                    if (b != null) {
                        float height = b.getHeight_m(osmMask);
                        initElevation_m += height;
                    }
                }// this is a building and we are on a rooftop
                continue;
            }

            //elevation check
            double currElev_m = 0;
            if (elev != null) {
                currElev_m = (float) getValue_OOBzero(elev, currLat, currLon);
            }

            if (e.dist_m < 25) {
                currElev_m = initElev_noBuilding;//data resolution is 20m so even minor elevation changes nearby could cause blocking
            }

            if (OSMget.isBuilding(osm)) {

                Building b = osmMask.getBuilding(h, w);
                if (b != null) {
                    float height = b.getHeight_m(osmMask);
                    currElev_m += height;
                }//add block height.     
            }

            double diff = currElev_m - initElevation_m;
            if (diff < 0) {
                diff = 0;
            }
            //evaluate
            double refAngle = Math.atan(diff / e.dist_m);
            double refDeg = refAngle * 360 / (2 * Math.PI);

            if (refDeg > zenithDeg) {// this means that the angle pointing to the blocking object max. height is LARGER than the zenith angle
                // => blocks the sun.
                if (print) {
                    EnfuserLogger.log(Level.FINER,BlockAnalysis.class,
                            "Sun block! at distance " + e.dist_m);
                }
                //break;
                return SOLAR_BLOCKED;
            }

        }//for cells

        return solar;
    }

    /*
    public float vegeBlock_zeroNoRed(boolean raw, int wdir, FusionOptions ops, Dtime dt, boolean cpm) {
        if (wdir > 359) wdir =359;
         float raw_vegeBlocker = this.VEGE[wdir/SECTOR_ANGLE]/100f;//zero => no reduction, max 1.
         if (raw) return raw_vegeBlocker;
         if (cpm) {
             return (float)ops.blockerStrength*raw_vegeBlocker*ops.VARA.monthlyVegetationReduct(ops.VARA.VAR_COARSE_PM, dt);
         } else {
             return (float)ops.blockerStrength*raw_vegeBlocker*ops.VARA.monthlyVegetationReduct(ops.VARA.VAR_NO2, dt);
         }
         
    }
    
     public float[] getBlockerFactorsToArray(float z0, Dtime dt, float current_wd, FusionOptions ops, float dist_m) {
           float[] blockers = new float[2];
           int sec = BlockAnalysis.getIndex(current_wd);
           float bbf = getWallReductionFactor_zeroNoRed(sec,
                   dist_m, z0, ops);//zero => no reduction, max 1.
           
           float raw_vegeBlocker = this.VEGE[sec]/100f;//zero => no reduction, max 1.
           float vege_coarseF = (float)ops.blockerStrength*raw_vegeBlocker*ops.VARA.monthlyVegetationReduct(ops.VARA.VAR_COARSE_PM, dt);
           float vege_other = (float)ops.blockerStrength*raw_vegeBlocker*ops.VARA.monthlyVegetationReduct(ops.VARA.VAR_NO2, dt);
           

           blockers[IND_GAS] = Math.max(0, 1f -bbf - vege_other);
           blockers[IND_CPM] = Math.max(0, 1f -bbf - vege_coarseF);
           return blockers;
    }   
*/

}
