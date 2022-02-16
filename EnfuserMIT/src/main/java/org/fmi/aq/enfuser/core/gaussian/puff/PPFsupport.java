/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.gaussian.puff;

import java.io.File;
import java.util.ArrayList;
import static org.fmi.aq.enfuser.customemitters.AbstractEmitter.BILLION;
import static org.fmi.aq.enfuser.ftools.FuserTools.tab;
import static org.fmi.aq.enfuser.options.Categories.CAT_BG;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.options.FusionOptions;

/**
 *
 * @author johanssl
 */
public class PPFsupport {
   

    private final static int SOURCCOUNT_ALL = 0;
    private final static int SOURCCOUNT_MERGED = 1;
    private final static int SOURCCOUNT_INBOUNDS = 2;
    private final static int SOURCCOUNT_INACTIVE = 3;

    
    final static int[] WIND_H = {10,30,60,140};
    
    public static ArrayList<String[]> printoutPuffStackSpecs(PuffPlatform ppf, long t0,
            long t1, long t2,
            ArrayList<PuffCarrier> removables,
            int oobs, int terminated, int steps, int injectionSize) {

        ArrayList<String[]> lines = new ArrayList<>();
        FusionOptions ops = ppf.ens.ops;
        int[][] sourcCountss = new int[ops.CATS.CATNAMES.length][4];

        
        
        int supers = 0;
        int mergedPuffs =0;
        int inactives = 0;
        int lespuffs =0;
        lines.add(new String[]{"TIME",  "Total time evolved: " 
                + (steps * ppf.timeTick_s) + "s for "+ ppf.dt.getStringDate()});
        if (t0 > 0) {
            lines.add(new String[]{"TIME",  "Puff injection " + (t1 - t0) + "ms"});
        }
        lines.add(new String[]{"TIME",  "PuffEvolution round took " + (t2 - t1) + "ms"});
        
        if (ppf.mapPuffs!=null) {
            String rme = ppf.mapPuffs.getStatusLogLine();
            lines.add(new String[]{"RME", rme});
        }
        
        //wind
        double mlat = ppf.in.midlat();
        double mlon = ppf.in.midlon();
        Met met = ppf.ens.getStoreMet (ppf.dt,mlat,mlon);
        String windStats ="Wind direction at center: "+met.getWdir() 
                +", rawSpeed = "+ met.getWindSpeed_raw() +"m/s,"; 
        for (int hm:WIND_H) {
            float profiledU = ppf.ens.macroLogWind((float)mlat, (float)mlon,hm, ppf.dt);
            windStats+= ", speed at "+hm+" [m]:" + editPrecision(profiledU,1)+"m/s ";
        }
        lines.add(new String[]{"WIND",windStats});
        
        lines.add(new String[]{"STACK", "Removed " + removables.size() 
                + " puffs from the stack. " + ppf.instances.size() + " remains."});
        lines.add(new String[]{"STACK", "\t from the ones removed " + oobs 
                + " were out of bounds, " + terminated + " terminated for other reasons."});
        
        for (PuffCarrier p : ppf.instances) {
            float lat = p.currentLat(ppf);
            float lon = p.currentLon(ppf);
            sourcCountss[p.sourcType][SOURCCOUNT_ALL]++;

            if (p.isInactive()) {
                inactives++;
                sourcCountss[p.sourcType][SOURCCOUNT_INACTIVE]++;
            }
            
            if (p.usesMicroMeteorology) {
                lespuffs++;
            }

            if (p.merged) {
                supers++;
                mergedPuffs+=p.mergedFrom;
                sourcCountss[p.sourcType][SOURCCOUNT_MERGED]++;
            }

            if (ppf.in.bounds.intersectsOrContains(lat, lon)) {
                sourcCountss[p.sourcType][SOURCCOUNT_INBOUNDS]++;
            }

        }//for puffs   
        lines.add(new String[]{"STACK", "\t" + supers + " are merged, from "+mergedPuffs+""});
        lines.add(new String[]{"STACK", "\t" + inactives + " are inactive."});
        if (lespuffs>0) lines.add(new String[]{"STACK", "\t" + lespuffs + " are LES PUFFS."});
        
        for (int i : ops.CATS.C) {
            lines.add(new String[]{"STACK", "\t " 
                    + ops.CATS.CATNAMES[i] + ": " 
                    + sourcCountss[i][SOURCCOUNT_ALL] 
                    + "\t merged   = " + sourcCountss[i][SOURCCOUNT_MERGED] 
                    + "\t inBounds = "  + sourcCountss[i][SOURCCOUNT_INBOUNDS] 
                    + "\t inActive = " + sourcCountss[i][SOURCCOUNT_INACTIVE]
            });
        }

        //get all emission kgs 
        float[][] kg_qs = sumNonBgEmissionMasses(ppf);
        lines.add(new String[]{"EMS",   "Emission masses [kg] per categories "
                + "(for bg unit is average [µgm-3]): "});
        String header = tab("  ");
        for (String s : ops.CATS.CATNAMES) {
            header += tab(s);
        }
        lines.add(new String[]{"EMS",   header});

        for (int q : ops.VARA.Q) {
            String line = tab(ops.VARA.Q_NAMES[q] + ": ");
            for (int c : ops.CATS.C) {
                line += tab(editPrecision(kg_qs[q][c], 1) + " ");
            }
            lines.add(new String[]{"EMS",   line});
        }

        for (int q : ops.VARA.Q) {
            lines.add(new String[]{"EMS",   "decay/deposition during steps, " 
                    + ops.VARA.Q_NAMES[q] + ": \t"
                    + editPrecision(-1 * ppf.stepDecay_q[q] / BILLION, 3) + " [kg]"});
        }

        for (int q : ops.VARA.Q) {
            lines.add(new String[]{"EMS",   "transformations during steps, " 
                    + ops.VARA.Q_NAMES[q]
                    + ": \t" + editPrecision(ppf.stepTransforms_q[q] / BILLION, 3) + " [kg]"});//in micrograms initially

        }
        return lines;
    }

    private static float[][] sumNonBgEmissionMasses(PuffPlatform ppf) {
        FusionOptions ops = ppf.ens.ops;
        float[][] dat_qs = new float[ops.VARA.Q_NAMES.length][ops.CATS.CATNAMES.length];

        int bgCounter = 0;
        for (PuffCarrier p : ppf.instances) {
            boolean mc = false;
            if (p instanceof MarkerCarrier) {
                bgCounter++;
                mc = true;
            }

            for (int q = 0; q < p.Q.length; q++) {
                if (p.fractionalCategories_cq == null) {//also MarkerCarriers

                    int s = p.sourcType;
                    dat_qs[q][s] += p.Q[q];//for mc these are concentrations 

                } else {//a bit more complex sum operation. The puff has multiple origin categories (mapPuff)
                    for (int cat : ops.CATS.C) {
                        float frac = (float) p.fractionalCategories_cq[cat][q] / 100f;
                        dat_qs[q][cat] += p.Q[q] * frac;
                    }
                }
            }

        }//for puffs

        //transform into kg
        for (int q : ops.VARA.Q) {
            for (int c : ops.CATS.C) {

                if (c != CAT_BG) {
                    dat_qs[q][c] /= 1000000000f;
                } else if (bgCounter > 0) {
                    dat_qs[q][c] /= bgCounter;
                }
            }
        }
        return dat_qs;
    }
    

    
        public static float[][] getAllCons_latlon(PuffPlatform ppf,float z, float lat,
                float lon, boolean fastGrid) {
        FusionOptions ops = ppf.ens.ops;   
        float[][] cq = new float[ops.CATS.C.length][ops.VARA.Q_NAMES.length];
        EnfuserLogger.log(Level.FINER,PPFsupport.class,
                "getAllCons_latlon: This method not finalized!");
        ArrayList<PuffCarrier> localPuffs;
        if (fastGrid) {
            localPuffs = ppf.bins.getPuffList_latlon(lat, lon);
        } else {
            localPuffs = ppf.instances;
        }

        for (PuffCarrier p : localPuffs) {
            p.stackConcentrations_latlon(cq, z, lat, lon, ppf.ens, ppf.in.bounds);
        }

        return cq;
    }

    public static void clearImageBuffer(PuffPlatform ppf) {

        String dir = ppf.ens.ops.defaultOutDir();
        EnfuserLogger.log(Level.FINER,PPFsupport.class,"clearing images in " + dir);
        File f_all = new File(dir);

        File[] ff = f_all.listFiles();

        if (ff != null) {
            for (File f : ff) {

                if (f.getName().contains(".png") || f.getName().contains(".PNG")
                        || f.getName().contains(".jpg") || f.getName().contains(".JPG")) {
                    f.delete();
                }
            }

        }
    }  
    


    public static String getHighestContributorID_zyx_qs(PuffPlatform ppf,float z, float y,
            float x, int q_type, int cat) {
        float largest = 0;
        String id = "";
        FusionOptions ops = ppf.ens.ops;
        for (PuffCarrier p : ppf.instances) {

            if (p.sourcType == cat) {
                float[][] cq = new float[ops.CATS.C.length][ops.VARA.Q_NAMES.length];
                p.stackConcentrations(cq, z, y, x, ppf.ens.ops);
                
                if (cq[cat][q_type] > largest) {
                    largest = cq[cat][q_type];
                    id = p.ID + "_at_" + (int) p.HYX[0] + "m";
                }
            }

        }//for puffs

        return id;
    }
    
}
