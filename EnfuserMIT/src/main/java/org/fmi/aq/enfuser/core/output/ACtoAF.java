/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.output;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.ArchiveContent;
import static org.fmi.aq.enfuser.core.ArchiveContent.HASHI_MET;
import static org.fmi.aq.enfuser.core.ArchiveContent.READ_AP_ONLY;
import static org.fmi.aq.enfuser.core.ArchiveContent.READ_FOR_ANIM;
import static org.fmi.aq.enfuser.core.ArchiveContent.read;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.enfuser.options.RunParameters;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import org.fmi.aq.essentials.date.Dtime;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.geoGrid.GeoGrid;

/**
 *This static class is used for turning an ArchiveContent data package from file
 * into an AreaFusion instance that was originally used for the creation
 * of the archive. So. this is ArchivedContent (AC) to AreaFusion (AF).
 * 
 * Several post-processes still use methods of AreaFusion, such as video animations.
 * @author johanssl
 */
public class ACtoAF {
  
    
       /**
     * Read an ArchiveContent suitable for animation purposes from file, then
     * convert it to an AreaFusion instance. The instance has meteorology,
     * pollutant concentrations and regional background. The component data
     * is ignored.
     * @param ops options
     * @param dt the time
     * @return 
     */
     public static AreaFusion readDefault(FusionOptions ops, Dtime dt) {
        ArchiveContent ac = read(READ_FOR_ANIM, ops, dt,ops.getDir(FusionOptions.DIR_TEMP),
                null, false);
        if (ac==null) {
            EnfuserLogger.log(Level.INFO, ArchiveContent.class, "AC load failed for: "
                    +dt.getStringDate() +", "+ops.areaID());
            return null;
        }
        return convertAF(ac,false);
     } 
    
    
   /**
     * Convert this ArchiveContent instance to AreaFusion in
     * @param ac
     * @param compress
     * @return the converted instance.
     */
    public static AreaFusion convertAF(ArchiveContent ac, boolean compress) {
        try {
            GlobOptions gg = GlobOptions.get();
            EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                    "===== AreaFusion Conversion ============ " + ac.dt.getStringDate());
            FusionOptions ops = ac.ops;
            int H = -1;
            int W = -1;
            Boundaries b = null;
            ArrayList<Integer> types = new ArrayList<>();

            //get dimensions first
            boolean notified = false;
            String availableVars = "";
            for (int i = 0; i < ops.VARA.isPrimary.length; i++) {
                GeoGrid g = ac.getPollutantGrid(i);
                if (g != null) {
                    H = g.H;
                    W = g.W;
                    types.add(i);
                    b = g.gridBounds;
                    availableVars += ops.VARA.VAR_NAME(i) + ", ";
                    if (!notified) {
                        
                        EnfuserLogger.log(Level.FINE,ArchiveContent.class,
                                "AreaFusion conversion from archive: dimensions = " 
                                    + H + "x" + W + ", " + b.toText());
                        
                        notified = true;
                    }
                }
            }//for primaries
            
            EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Available Vars = " + availableVars);
            AreaFusion af = new AreaFusion(H, W, ac.dt.clone(), b, types,compress,ops);
            EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                    "AreaFusion instance created. Types arraySize=" + types.size());
            
            af.metGrids = new GeoGrid[ops.VARA.varLen()];
            af.silamGrids = new GeoGrid[ops.VARA.varLen()];

            //add data
            for (int i : types) {
                
                EnfuserLogger.log(Level.FINER,ArchiveContent.class,"=== fetching data for:" + ops.VARA.VAR_NAME(i));
                
                try {
                    GeoGrid g = ac.getPollutantGrid(i);
                    if (g != null) {
                        //af.consGrid_qhw[i] = g.values;
                        af.addConsGrid(i, g);
                            EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                                    "\t\t setting AreaFusion concentration fields :" + ops.VARA.VAR_NAME(i));
                        
                    }

                    //regional
                    g = ac.getRegionalGrid(i);
                    if (g != null) {
                        Boundaries bb = g.gridBounds.clone(ops.VARA.VAR_NAME(i));//switch name from 'name_convents' so that all ResultDisplayer mechanics work.
                        g = new GeoGrid(g.values, g.dt, bb);
                        af.silamGrids[i] = g;
                        EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                                "\t\t setting AreaFusion SILAM grid :" + ops.VARA.VAR_NAME(i));
                        
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //metData
            for (int i = 0; i < ops.VARA.varLen(); i++) {
                try {
                    if (!ops.VARA.isMetVar[i]) {
                        continue;
                    }
                    String name = ops.VARA.VARNAME_CONV(i);
                    GeoGrid g = ac.hashArr[HASHI_MET].get(name);
                    if (g != null) {
                        Boundaries bb = g.gridBounds.clone(ops.VARA.VAR_NAME(i));//switch name from 'name_convents' so that all ResultDisplayer mechanics work.
                        g = new GeoGrid(g.values, g.dt, bb);
                        af.metGrids[i] = g;
                        EnfuserLogger.log(Level.FINER,ArchiveContent.class,
                                    "\t\t setting AreaFusion metGrid: " + name + "(" + g.varName + ")");
                        
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //observations and coordinatedStrings
            for (int typeI : ac.obsHash.keySet()) {
                if (typeI==-1) continue;
                try {
                    ArrayList<Observation> arr = ac.obsHash.get(typeI);
                    af.obs[typeI] = arr;
                    EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Added " + arr.size() + " observation of type " 
                                + ops.VARA.VAR_NAME(typeI) + " to AF.");
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return af;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
  
    
    public static ArrayList<AreaFusion> getAFs_withRunParams(FusionOptions ops) {
        
        boolean allAnimData = true;
        RunParameters p = ops.getRunParameters();
            if (!p.videoOutput() && !p.imagesOutput()) {
                allAnimData = false;//just pollutant fields
            }
           ArrayList<AreaFusion> afs = getPollutantAFs(allAnimData,
                   p.nonMetTypeInts(ops),p.start(), p.end(),
                    p.areaFusion_dt_mins, ops.getDir(FusionOptions.DIR_TEMP),
                    ops, ops.compressedArchForAnim);
           return afs;    
    }

    /**
     * Read a collection of archives and return an ArrayList of AreaFusions.Use
 cases: for netCDF creation and animations.
     *
     * @param includeAnimData if true then also meteorological data and
     * measurements are loaded since these are needed for animations.
     * @param nonMets a list of pollutant type indices that are to be read.
     * @param start start time for extraction
     * @param end end time for extraction
     * @param step_mins step size in minutes (default: 60)
     * @param tempDir directory for temporary data
     * @param ops options
     * @param compress
     * @return a list of AreaFusions.
     */
    public static ArrayList<AreaFusion> getPollutantAFs(boolean includeAnimData,
            ArrayList<Integer> nonMets, Dtime start, Dtime end, int step_mins,
            String tempDir, FusionOptions ops,boolean compress) {

        long t = System.currentTimeMillis();
        ArrayList<AreaFusion> arr = new ArrayList<>();

        int[] reads = READ_AP_ONLY;
        String filts = "";
        for (int b : nonMets) {
            filts += ops.VAR_NAME(b) + ";";
            filts += ops.VARNAME_CONV(b) + ";";
        }
        String[] filters = filts.split(";");//{VAR_NAME(typeInt), FusionOptions.VARNAME_CONV(typeInt)};

        boolean filterPolDataOnly = false;
        if (includeAnimData) {//more data is needed
            filterPolDataOnly = true;//met data and SILAM reg. is still needed! But not components.
            reads = READ_FOR_ANIM;
        }

        Dtime current = start.clone();

        while (current.systemHours() <= end.systemHours()) {

            ArchiveContent ac = read(reads, ops, current, tempDir, filters, filterPolDataOnly);
            EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Read operation done for " + current.getStringDate());
            if (ac != null) {
                try {
                    arr.add(convertAF(ac,compress));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            current.addSeconds(step_mins * 60);
        }//while

        long t2 = System.currentTimeMillis();
        EnfuserLogger.log(Level.FINER,ArchiveContent.class,"getSpecificPollutant: returning " + arr.size()
                + " archiveContent packs for a duration of " + start.getStringDate() + " -> "
                + end.getStringDate() + " using a timestep of " + step_mins + " min");
        EnfuserLogger.log(Level.FINER,ArchiveContent.class,"Extraction took " + (t2 - t) / 1000f + "s");
        return arr;

    }    
    
    
}
