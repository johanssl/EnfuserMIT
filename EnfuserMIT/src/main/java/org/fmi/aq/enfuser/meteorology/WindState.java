/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.meteorology;

import java.util.ArrayList;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.BlockAnalysis;
import org.fmi.aq.enfuser.datapack.main.TZT;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.meteorology.WindConverter;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.date.Dtime;

/**
 * This purpose of this class is to provide a generalized yet multi-purpose method
 * for the assessment of wind profiles for different heights above ground.
 * 
 * While doing this, the class is equipped with smooth interpolation methods
 * for meteorological data. With these it does not matter if the meteorological
 * data (as Met in HashedMet) are assessed only once per hour with a resolution
 * of 1 km.
 * 
 * There are several use-cases for this class, ranging from the local-scale wind
 * profiling (GaussianPlume) to Gaussian Puff. The main difference is the 
 * treatment and detail to roughness length estimation. 
 * 
 * The basic flow of operation for this class is as follows: 1) set the time and
 * location (assesses raw met data and roughness parameters) and 2)
 * assess the wind speed at given height.
 * 
 * @author johanssl
 */
public class WindState {
    
    //met interpolation settings
    public boolean timeInterpolation =true;
    public boolean geoInterpolation = false;
    //roughness length estimation settings
    public boolean fineScaleRL=false;//true for local scale plume modelling.
    private boolean windMeasurementOffsetting = true;//correct raw direction and U
    //based on measurements. set this FALSE only when these OFFSETS are computed!
    
    private Dtime dt;
    
    public float rl;//roughness length at set location (and time -> wind direction)
    public float u_raw;//raw wind speed at 10m.
    public int wdir;//raw wind direction [0,359],from.
    
    public float U;//log wind at set location and height (finalize method)
    public Met met;//raw meteorology at location (full hour)

    //some initial meteorology variables (from met)
    int stabiClass =0;
    private int firstStabClass=-1;//for puffs
    private float ABLH;
    private float maxABLH=-1;//for puffs
    
    //secondary location for WeightManager (Gaussan Plume only)
    private float rl_secondary= RoughPack.RL_DEFAULT;
    private float U_secondary=1;
    
    //testing settings
    public static boolean RAW_MET=false;
    public static boolean ADDS_RANDOM=true;
    
    public WindState() {
        
    }
    
    /**
     * A special method for WindMeasurements class - this is needed during
     * estimation of wind corrections based on measurements.
     */
    public void setMeasurementOffsetAssessmentMode() {
        this.windMeasurementOffsetting = false;
    }
    
    /**
     * The step 1 method for this class - set the location and time for the assessment.
     * The settings for temporal- spatial interpolation, or settings how the
     * roughness length should be treated will affect how this process will
     * turn out.
     * This will automatically call the method setRawInputs() (which is not
     * often manually used.)
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     * @param dt time
     * @param ens the core
     * @param ba A BlockAnalysis instance (can be null). If this is not null
     * and fineScaleRL is set to true, then this may be used for the assessment
     * of roughness length.
     */
    public void setTimeLoc(float lat, float lon, Dtime dt, FusionCore ens, BlockAnalysis ba) {
       
          met = ens.getStoreMet(dt, lat, lon);
          if (geoInterpolation || timeInterpolation) {//fetch interpolated wind dir,U
              if (mets==null) mets = new ArrayList<>();
              float[] dirU= getSmoothWind_dirU(mets, lat,lon, dt, ens,
                      this.timeInterpolation, this.geoInterpolation,met);
              wdir = (int)dirU[0];
              u_raw = dirU[1];
 
          } else {
             wdir = met.getWdirInt();  
             u_raw = met.getWindSpeed_raw();
             
          }
         this.dt= dt;
         setRawInputs(wdir, u_raw, ens, ba, lat, lon); 
    }
    
    /**
     * With the given raw meteorological input and location, define the roughness
     * length for the location. Also, apply measurement based offsets to 
     * raw meteorology if such are available.
     * @param dir wind direction (raw NWP)
     * @param raw_u wind speed (raw NWP)
     * @param ens the core
     * @param ba if non-null, may provide input for rouhness length estimation
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     */
    public void setRawInputs(int dir, float raw_u, FusionCore ens,
            BlockAnalysis ba, float lat, float lon) {
        this.wdir =dir;
        this.u_raw= raw_u;
        //new feature: measurement driven met.correction
        float[] dirU_correction = null;
        if (this.windMeasurementOffsetting)dirU_correction 
                = ens.hashMet.getMeasurementBasedWindOffsets(ens,dt);
        if (dirU_correction !=null) {
            this.wdir += dirU_correction[0];
            this.u_raw += dirU_correction[1];
            if (this.u_raw<0.1) u_raw = 0.1f;
        }
        
         if (wdir<0) wdir+=360;
         if (wdir >=360) wdir-=360;
         //roughness
         if (this.fineScaleRL && ens.hasWindGrids()) {
            this.rl = ens.windprofiler.getRoughness(wdir, (float) lat,
                    (float) lon, false,ens.ops.RL_scaler()); 
            
         }  else if (this.fineScaleRL && ba!=null) {
            this.rl = ens.ops.scaleRL(
                    ba.getDirectedRoughness(wdir));
            
         } else {//macro
            this.rl = ens.ops.scaleRL(
                    ens.rough().getRoughness(lat, lon, ens,  wdir));
         }
    }
    
    /**
     * A specific method for Gaussian Plume profiler (WeightManager)
     * which needs wind profiles at TWO separate points. In this we simplify
     * the case and use the same base meteorology for point locations
     * that are, in any case, nearby.
     * @param lat latitude coordinate for secondary location
     * @param lon longitude coordinate for secondary location
     * @param ens the core
     */
    public void setSecondaryPoint(float lat, float lon, FusionCore ens) {
        if (this.fineScaleRL && ens.hasWindGrids()) {
            this.rl_secondary = ens.windprofiler.getRoughness(wdir, (float) lat,
                    (float) lon, false,ens.ops.RL_scaler());  

         } else {//macro
            this.rl_secondary= ens.ops.scaleRL(
                    ens.rough().getRoughness(lat, lon, ens,  wdir));
         }  
    }
    
    /**
     * The step 2 method for secondary wind profile (WeightManager) - return the profiled
     * wind speed at the secondary location at the given secondary height.
     * @param ens the core
     * @param height_m height above ground
     * @return 
     */
    public float setSecondaryProfile(FusionCore ens, float height_m) {
       this.U_secondary = ens.rough().logWind_limited(ens.ops.MIN_WINDSPEED,u_raw, rl_secondary, height_m); 
       if (this.U_secondary<ens.ops.MIN_WINDSPEED) this.U_secondary = ens.ops.MIN_WINDSPEED;
       return this.U_secondary;
    }
    
    /**
     * for processing output this class provides, more specifically, to turn
     * it into raw meteorological NWP values. This will also make sure
     * that supporting meteorological variables such as stability and
     * ABLH have been successfully processed for Gaussian Puffs.
     * @return raw wind speed.
     */
    private float dummyProfile() {
        this.U = this.u_raw;
        this.stabiClass = met.stabClass();
        if (this.firstStabClass==-1) this.firstStabClass = stabiClass;
        ABLH = met.getABLh_nullSafe();
        if (ABLH > this.maxABLH) {
             maxABLH = ABLH;
         }
        return U;
    }
    
    
    public float profileForHeight(float height_m,FusionCore ens) {
        if (RAW_MET) {
            return dummyProfile();
        }
        
       FusionOptions ops = ens.ops;
       this.U = ens.rough().logWind_limited(ops.MIN_WINDSPEED,u_raw, rl, height_m); 
       if (this.U<ops.MIN_WINDSPEED) this.U = ops.MIN_WINDSPEED;
       
       stabiClass = Met.getStabClass_profileMod(met.stabClass(), height_m);
       if (this.firstStabClass==-1) this.firstStabClass = stabiClass;
       
       ABLH = met.getABLh_nullSafe();
       if (ABLH < ops.MIN_ABLH) {
           ABLH = ops.MIN_ABLH;
        }
        //try to solve the power plant ABLH problem by artifically increasing ABLH
        if (ops.additionalABLH_limit_puffs) {
            float blh_Add =(height_m-80)*20;
            if (blh_Add>0 ) {//this puff comes from an elevated source at least 100m high.
                float min = ops.MIN_ABLH+blh_Add;
                if (ABLH< min) ABLH = min;
            }
        }
       
       //update max
         if (ABLH > this.maxABLH) {
             maxABLH = ABLH;
         }
        return this.U; 
    }
    
    public float maxABLH() {
        return this.maxABLH;
    }

    public void addRandom_uDir(double uadd, double wadd,FusionOptions ops) {
       this.u_raw+=uadd;
       if (this.u_raw <ops.MIN_WINDSPEED) this.u_raw =ops.MIN_WINDSPEED;
       
       this.wdir+=wadd;
       if (wdir<0) wdir+=360;
       if (wdir >=360) wdir-=360;
    }


    public int initialStabiClass() {
        return this.firstStabClass;
    }

    public void setStabiClass(int c) {
        this.stabiClass = c;
        this.firstStabClass = c;
    }

    public void manuallySetUdir(float[] u_dir) {
       this.U = u_dir[0];
       this.wdir = (int)u_dir[1];
    }
    
   private ArrayList<Met> mets; 
    private static float[] getSmoothWind_dirU(ArrayList<Met> mets, float lat,
            float lon, Dtime dt, FusionCore ens,
            boolean timeInterpolation, boolean geoInterpolation, Met met) {
        
        if (!geoInterpolation) {//just time then
             Dtime[] dts = ens.tzt.getDates(dt);
             Dtime dtNext = dts[TZT.NEXT_UTC];
             Met next = ens.getStoreMet(dtNext, lat, lon);//nest hour met.
             float frac2 = (float)dt.min/60.f;//weight for 'second' (next hour) maxed at 0.99f
             float frac1 = 1.0f-frac2;
             
             float N = met.getSafe(ens.ops.VARA.VAR_WIND_N)*frac1 + next.getSafe(ens.ops.VARA.VAR_WIND_N)*frac2;
             float E = met.getSafe(ens.ops.VARA.VAR_WIND_E)*frac1 + next.getSafe(ens.ops.VARA.VAR_WIND_E)*frac2;
             return WindConverter.NE_toDirSpeed_from(N, E, false);//to dir-U
        }    
       
       //smooth geo-interpolated north component, east component for this hour.
       float[] NE = getSmoothWind(mets, lat,
            lon, dt, ens);
       
       if (dt.min>0 && timeInterpolation) {//need to interpolate
                
           //get the same computation for the next hour.
           Dtime[] dts = ens.tzt.getDates(dt);
           Dtime nextH = dts[TZT.NEXT_UTC];
           float[] second = getSmoothWind(mets, lat,
                lon, nextH, ens);

           float frac2 = (float)dt.min/60.f;//weight for 'second' (next hour) maxed at 0.99f
           float frac1 = 1.0f-frac2;
           for (int i =0;i<NE.length;i++) {
               NE[i] = frac1*NE[i] + frac2*second[i];//swith the value to interpolated one.
           }
       }
       return WindConverter.NE_toDirSpeed_from(NE[0], NE[1], false);//to dir-U
    }
        
     /**
     * Get a Kriging interpolated wind data using the nearest 9 Met-objects for
     * the given pair of coordinates.
     *
     * @param mets empty temporary array to hold Met objects.
     * @param lat lat coordinate
     * @param lon lon coordinate
     * @param dt time
     * @param ens FusionCore that holds all data.
     * @return float[]{dir_deg, v_m/s}.
     */
    private static float[] getSmoothWind(ArrayList<Met> mets, float lat,
            float lon, Dtime dt, FusionCore ens) {
 
            mets.clear();
            ens.hashMet.getNearbyMets(dt, lat, lon, ens, mets);
            return ens.hashMet.getSmoothProperties(ens.hashMet.windTypes,
                    lat, lon, mets); 
    }

    public float ABLH() {
        return this.ABLH;
    }
    
    public float getProfiledType(VarAssociations VARA, int i) {
       if (i ==VARA.VAR_WINDDIR_DEG) return this.wdir;
       if (i ==VARA.VAR_WINDSPEED) return this.U;
       if (i ==VARA.VAR_WIND_E || i ==VARA.VAR_WIND_N) {
            float[] NE = WindConverter.dirSpeedFrom_toNE(wdir, U);
            if (i ==VARA.VAR_WIND_N) return NE[0];
            return NE[1];
       }
       return 0;
    }
       

}
