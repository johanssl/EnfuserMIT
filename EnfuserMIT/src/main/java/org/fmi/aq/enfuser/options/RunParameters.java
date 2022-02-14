/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.AreaInfo;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.gispack.utils.AreaNfo;
import static org.fmi.aq.essentials.gispack.utils.Tools.getBetween;

/**
 *This class contains the parameters necessary to define a modelling task
 * that is about to occur.
 * This includes the modelling time span, area name, boundaries,
 * modelling variables, resolution and output customization options.
 * @author johanssl
 */
public class RunParameters {
    
    /**
     * This is the main modelling area bounding box.
     */
    private Boundaries bounds;
    /**
     * This is the expanded modelling area that is used for puff modelling
     * for example. It is larger than the main modelling area with
     * the same center.
     */
    private Boundaries b_exp;
    /**
     *This even larger expanded modelling area which can be used
     * for puff modelling if instructed to do so via options.
     */
    private Boundaries b_exp2;
    
    /**
     *This is very much larger area around the expanded bounding box.
     * It's main use case is for regional AQ data extraction and visualizations
     * but also for point emitter max assessment radius.
     */
    private Boundaries b_max;

    
    private int res_m = 25;
    public int areaFusion_dt_mins = 60;
    private String areaID;//area name for operative runs  
    
    Dtime loadEnd;
    Dtime loadStart;
    Dtime afStart;
    Dtime afEnd;
    
    public ArrayList<String> modellingVars_nonMet=new ArrayList<>();
    ArrayList<ModellingArea> innerAreas = new ArrayList<>();
    
    //negated tags
    public final static String NO_ANIMS ="NO_ANIMATION";
    public final static String NO_IMGS ="NO_IMAGES";
    public final static String NO_STATS ="NO_STATS";
    public final static String NO_GRIDS ="NO_GRIDS";
    public final static String NO_MISC ="NO_MISC";
    public final static String NO_AWS_S3 ="NO_AWS_S3";
    
    public final static String AUTO_GRIDAVE ="AUTO_GRID_AVERAGES<";
    public final static String AUTO_TROPOMI ="S5P";
    public final static String AUTO_SC ="AUTO_STATSCRUNCH";
    public final static String ALLOUT_FORK ="ALL_OUT_FORK<";
    public final static String REDUCED_FORK ="REDUCED_OUT_FORK<";
    public final static String FORK_IGNORE ="FORK_IGNORE<";
    String outputMods ="NO_MISC,";
    
    private ModellingArea activeInner=null;
    
    //preparation phase booleans
    public boolean smooth = true;
    public boolean forcs = true;
    public boolean manualGUI = false;
    public boolean overs =true;//do data fusion (overrides)
    public boolean puffs =true; //do puff modelling (PuffPlatform)
    
    
    RunParameters(Boundaries b, String name, Dtime curr) {
        this.bounds = b.clone();
        this.b_exp = b.clone();
        b_exp.expand(SLIGHT_EXPANSION);
        
        this.b_exp2 = b.clone();
        b_exp2.expand(EXPANSION);
        
        this.b_max = b.clone();
        b_max.expand(HUGE_EXPANSION);
        
        if (name!=null) {
        this.areaID = name+"";
        } else {
            this.areaID = this.boundsName(b);
        }
        
        this.setDates(curr,0,0);
    }
    
    public void setResolution(int resm) {
        this.res_m = resm;
    }
    
    /**
     * Check modelling time span and if two weeks or more, then returns true.
     * Use case: for longer historical point modelling work, and to significantly
     * reduce Puff modelling resolution in such cases.
     * @return 
     */
    public boolean longModellingSpan() {
        return (this.end().systemHours() - this.start().systemHours()) > 24*14;//two weeks
    }
    
    
    /**
     * Create a new instance of RunParameters based on name of an existing
     * ModellingArea. The content of the ModellingArea is transferred to this
     * instance.
     * @param name name for the modelling task area
     * @param curr define the origin for modelling time span.
     * @return RunParameters as has been set for a ModellingArea with the
     * given name.
     */
    public static RunParameters fromModellingArea(String name, Dtime curr) {
        
        GlobOptions g = GlobOptions.get();
        ModellingArea m = g.getModellingArea(name);
        if (curr==null) curr = Dtime.getSystemDate_FH();
        RunParameters rp = new RunParameters(m.b,m.name,curr);
        
        rp.setDates(curr, m.backW, m.forwH);
        rp.setModellingVars(m.vars);
        
        if (m.innerAreas!=null && !m.innerAreas.isEmpty()) {
            for (String in:m.innerAreas) {
                ModellingArea min = g.getModellingArea(in);
                if (min!=null) {
                    rp.innerAreas.add(min);
                }else {
                    //log
                }
            }
        }
        rp.outputMods+=m.mods;
        rp.res_m = m.res_m;
        rp.areaFusion_dt_mins = m.res_min;
        return rp;
    }
    
    public void setActiveInnerArea(ModellingArea m) {
        this.activeInner = m;
    }
     public void setActiveInnerArea(int k) {
        this.activeInner = this.innerAreas.get(k);
    }
    public void clearActiveInnerArea() {
        this.activeInner=null;
    }
    
    public float res_m() {
        if (activeInner!=null) return activeInner.res_m;
        return this.res_m;
    }
    
    public String areaID() {
        if (activeInner!=null) return activeInner.name+"";
        return this.areaID+"";
    }
    
    public void setModellingVars(ArrayList<String> vars) {
        this.modellingVars_nonMet.clear();
        for (String var:vars) {
            this.modellingVars_nonMet.add(var);
        }
    }
    
    /**
     * Manually customize the temporal span for a modelling task or a custom
     * operation.
     * @param base the origin time
     * @param backH hours backwards from origin
     * @param forwH hours to the future from origin.
     */
    public void setDates(Dtime base, int backH, int forwH) {
        if (base==null) base = Dtime.getSystemDate_FH();
            
        this.loadStart = base.clone();
            loadStart.addSeconds(-3600*48);
            
        afStart = base.clone();
            afStart.addSeconds(-3600*backH);
        afEnd = base.clone();
            afEnd.addSeconds(3600*forwH);
        
        this.loadEnd = afEnd.clone();
        this.loadEnd.addSeconds(3600*6);
    }
        
    /**
    * Manually customize the temporal span for a modelling task or a custom
    * operation.
    * @param start start time
    * @param end end time
    */
   public void setDates(Dtime start, Dtime end) {
         
        this.loadStart = start.clone();
            loadStart.addSeconds(-3600*48);
            
        afStart = start.clone();
        afEnd = end.clone();
        this.loadEnd = afEnd.clone();
        this.loadEnd.addSeconds(3600*6);
    }
    
    /**
     * Set a custom geographic area definition for a modelling task or
     * a custom operation. This automatically sets up a number of expanded
     * boundaries for a larger surrounding area (used for data loading etc)
     * @param b bounds to be set. 
     */
    public void setModellingBounds(Boundaries b) {
        this.bounds = b.clone();
        this.b_exp = b.clone();
        b_exp.expand(SLIGHT_EXPANSION);
        
        this.b_exp2 = b.clone();
        b_exp2.expand(EXPANSION);
        
        this.b_max = b.clone();
        b_max.expand(SLIGHT_EXPANSION);
        b_max.flatExpand_km(70);//70 kilometers to each side
        
        
    }

    public Boundaries bounds() {
        if (activeInner!=null) return activeInner.b;
        return this.bounds;
    }

    public final static double SLIGHT_EXPANSION = 0.2;
    public final static double EXPANSION = 0.4;
    public final static double HUGE_EXPANSION = 1.25;


    protected Boundaries boundsExpanded(boolean puff) {
        if (puff) return this.b_exp2;
        return this.b_exp;
    }

    public Boundaries boundsMAXexpanded() {
        return this.b_max;
    }
    
    public Boundaries boundsCustomExpanded(double km, double fractionIncrease) {
        Boundaries b= this.bounds.clone();
        b.flatExpand_km(km);
        if (fractionIncrease>0) b.expand(fractionIncrease);
        return b;
    }

    
    public int[] getGridDimensions_HW() {
      float resm = this.res_m();
      Boundaries b = this.bounds();
      AreaNfo in = new AreaNfo(b,resm,this.afStart);
      return new int[]{in.H,in.W};
    }

    public ArrayList<Integer> nonMetTypeInts(FusionOptions ops) {
        ArrayList<Integer> nonMet_typeInts =new ArrayList<>();
        for (String var:this.modellingVars_nonMet) {
            nonMet_typeInts.add(ops.VARA.getTypeInt(var));
        }
        return nonMet_typeInts;
    }

    public Dtime loadStart() {
        return this.loadStart;
    }
    
    public Dtime loadEnd() {
        return this.loadEnd;
    }
    
    public Dtime start() {
        return this.afStart;
    }
    public Dtime end() {
        return this.afEnd;
    }

    public ArrayList<String> nonMetTypes() {
       return this.modellingVars_nonMet;
    }

    public boolean hasInners() {
        return this.innerAreas!=null && !this.innerAreas.isEmpty();
    }

    public Iterable<ModellingArea> getInners() {
        return this.innerAreas;
    }
    
    public void clearInners(){
        this.innerAreas.clear();
    }

    public AreaInfo intoAreaInfo(Dtime dt, FusionOptions ops) {
      return new AreaInfo(this.nonMetTypeInts(ops),
              this.bounds().clone(), res_m(), dt);//UPDATE time (dt is final in AreaInfo, so a new instance will do the trick)
    }

    public void customize(boolean boundsName,Boundaries b, Float resm,
            Integer minStep, Dtime start, Dtime end,
            ArrayList<String> nonMetTypes) {
       
      if (b!=null) {
          this.setModellingBounds(b);
          if (boundsName) this.areaID = boundsName(b);
      }
      
      if (resm!=null) this.res_m = resm.intValue();
      if (start!=null) {
          this.setDates(start, end);
      }
        
      if (nonMetTypes !=null) {
          this.modellingVars_nonMet = nonMetTypes;
      }
      
      if (minStep!=null) {
          this.areaFusion_dt_mins =minStep;
      }
    }
    
    public ArrayList<String> loadVars(FusionOptions ops) { 
        VarAssociations VARA = ops.VARA;
        return VARA.allVarsList_PSM(true,true,true);
    }

    private String boundsName(Boundaries b) {
        return "p"+(int)b.getMidLat()+"_"+(int)b.getMidLon();
    }

    public boolean videoOutput() {
       return !this.outputMods.contains(NO_ANIMS);
    }

    public boolean imagesOutput() {
       return !this.outputMods.contains(NO_IMGS);
    }

    public boolean gridsOutput() {
       return !this.outputMods.contains(NO_GRIDS);
    }
    
    public boolean statsOutput() {
       return !this.outputMods.contains(NO_STATS);
    }
    
    public boolean operativeStatsCruncher() {
        return this.outputMods.contains(AUTO_SC);
    }
    
     public boolean miscOutput() {
       return !this.outputMods.contains(NO_MISC);
    }
     
     public boolean outputToS3() {
       return !this.outputMods.contains(NO_AWS_S3);
    }
     
    public void disableAllOutputTypes() {
        
        this.outputMods+=RunParameters.NO_ANIMS+", ";
        this.outputMods+=RunParameters.NO_IMGS+", ";
        this.outputMods+=RunParameters.NO_STATS+", ";
        this.outputMods+=RunParameters.NO_GRIDS+", ";
        this.outputMods+=RunParameters.NO_MISC+", ";
        
    }
    
    public void setToArchivingMode() {
        disableAllOutputTypes();
        this.outputMods= this.outputMods.replace(RunParameters.NO_STATS, "");
    }
        

    public Dtime spinStart() {
        int hours =6;
        double lenKm = Math.sqrt(this.bounds.getFullArea_km2());
        //for a large area this is 70
        //for a small area this is 10
        //the larger the area, the larger the spinup should be.
        hours+= (int)(lenKm/10);
        Dtime dt = this.afStart.clone();
        dt.addSeconds(-3600*hours);
        return dt;
    }

    public int loadSpanHours() {
       return this.loadEnd().systemHours() - this.loadStart().systemHours();
    }

    String allOutForkDir() {
       String s = this.outputMods;
       if (s.contains(ALLOUT_FORK)) {
           return getBetween(s,ALLOUT_FORK,">");
       }
       return null;
    }

    String reducedOutForkDir() {
       String s = this.outputMods;
       if (s.contains(REDUCED_FORK)) {
           return getBetween(s,REDUCED_FORK,">");
       }
       return null;
    }

    public boolean archivingMode() {
     return !videoOutput() && !imagesOutput() && !gridsOutput();
    }

    public void addOutputMod(String s) {
       this.outputMods+=s+", ";
    }

    public boolean operativeGridAverages() {
        return this.outputMods.contains(AUTO_GRIDAVE);
    }

    public int gridAveragerSparser() {
      try {
          String val = getBetween(this.outputMods,AUTO_GRIDAVE,">");
          int k= Double.valueOf(val).intValue();
          if (k<1) k=1;
          return  k;
      } catch (Exception e) {
          EnfuserLogger.log(Level.WARNING, this.getClass(), "Hourly sparser integer"
                  + " for grid averager, parse failed for " + this.areaID);
          return 3;
      }
    }

    private final  HashMap<String, Boundaries> customBounds = new HashMap<>();
    public void addCustomBound(Boundaries b, String key) {
       this.customBounds.put(key, b);
    }
    
    public Boundaries getCustom(String key) {
        return this.customBounds.get(key);
    }

    String[] forkIgnore() {
       String s = this.outputMods;
       if (s.contains(FORK_IGNORE)) {
           String cont= getBetween(s,FORK_IGNORE,">");
           return cont.split(",");
       }
       return null;
    }

    public boolean withinLoadSpan(Dtime dtc) {
        if (dtc.systemHours()< this.loadStart.systemHours()) return false;
        if (dtc.systemHours()> this.loadEnd.systemHours()) return false;
        return true;
    }

    public boolean tropomiAnalysis() {
         return this.outputMods.contains(AUTO_TROPOMI);
    }
 
    
}
