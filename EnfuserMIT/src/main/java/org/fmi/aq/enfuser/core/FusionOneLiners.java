/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core;

import java.util.ArrayList;
import java.util.Collections;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.core.output.OutputManager;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.enfuser.core.output.Meta;
import org.fmi.aq.enfuser.core.varproxy.ProxyProcessor;
import org.fmi.aq.enfuser.datapack.reader.DefaultLoader;
import org.fmi.aq.enfuser.ftools.FuserTools;
import org.fmi.aq.enfuser.options.RunParameters;
import static org.fmi.aq.essentials.gispack.utils.Tools.editPrecision;

/**
 *
 * @author johanssl
 */
public class FusionOneLiners {

    
    public static FusionCore load(FusionOptions ops) {

        ops.logBusy(true);
        
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"////Data loading phase "
                + "starting for process:\n"+ops.runParameterPrintout() +"\n");
        
        DefaultLoader loader = new DefaultLoader(ops);
        FuserTools.mkMissingDirsFiles(ops);
        //check that the list of modelling variables are consistent in a sense
        //that if one is dependent of the availably of another, then the dependent
        //variable is also listed.
        checkVariableListConsistency(new ProxyProcessor(ops.VARA), ops);
        //load data
        loader.loadMaps(ops.bounds(), true);
        loader.loadDataPack(true);

        FusionCore ens;
        ens = new FusionCore(ops,  loader);
        return ens;
    }
    
    
    /**
     * Takes care of the whole chain of steps that include a) data extraction,
     * b) area fusion output, c) some point fusion output and c) stores data and
     * may send it further (e.g. Heroku web portal)
     *
     * @param ops All Fusion options.
     * @param jbar null for SERVER mode and exists for GUI-mode.
     * @param jbar2 null for SERVER mode.
     * @return FusionCore instance that was created and used for the overall
     * process
     */
    public static FusionCore extractAndFuse(FusionOptions ops, Object jbar, Object jbar2) {

        
        long t = System.currentTimeMillis();
        FusionCore ens = load(ops);
        ens.addProgressBars(jbar, jbar2);   
        ops.logBusy();
        
        long t1 = System.currentTimeMillis();
        double initTime_mins = (double) (t1 - t) / 1000.0 / 60.0;
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"////Data loading phase"
                + " complete. Took " + ((t1-t)/1000) +" seconds.\n");
        
        //prepare for fusion
        ens.prepareTimeSpanForFusion();

            long t2 = System.currentTimeMillis();
            double prepTime_mins = (double) (t2 - t1) / 1000.0 / 60.0;

        //process the area finally. Check also if there's an inner run (e.g, HKI-METRO and HKI-CENTER) being included
        double afTime_mins = 0;
        double output_mins = 0;

        ens.processAreaFusionSpan();
        ops.logBusy();
        long t3 = System.currentTimeMillis();
        afTime_mins += (double) (t3 - t2) / 1000.0 / 60.0;

        OutputManager.outputAfterAreaRun(ens);

        long t4 = System.currentTimeMillis();
        output_mins += (double) (t4 - t3) / 1000.0 / 60.0;

        long tFinal = System.currentTimeMillis();
        double totTime_mins = (double) (tFinal - t) / 1000.0 / 60.0;
        //double prepTime_mins = (double)(t2-t)/1000.0/60.0;
        
         RunParameters p = ops.getRunParameters();
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"=========TIME TAKEN============");
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,ens.ops.areaID() + ", " + p.start().getStringDate() + " - " + p.end().getStringDate());
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"Total minutes = " + editPrecision(totTime_mins,1));
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"\t Data loading took: " + editPrecision(initTime_mins,2));
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"\t preparaton (puff & overrides) took: " + editPrecision(prepTime_mins,2));
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"\t AreaFusion & file generation took: " + editPrecision(afTime_mins,2));
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"\t output post-processing took: " + editPrecision(output_mins,2));
        EnfuserLogger.log(Level.INFO,FusionOneLiners.class,"==============================");
        
        String toMeta = "TIME;total,load,preparation,computations,outputGen(min);"
                +editPrecision(totTime_mins,2)+";"
                + editPrecision(initTime_mins,2)+";"
                + editPrecision(prepTime_mins,2) +";"
                + editPrecision(afTime_mins,2) +";"
                + editPrecision(output_mins,2);
        Meta.append(toMeta,ops);
//ops.logNotBusy();
        return ens;
    }
    
    
         /**
     * Adjust variable list automatically in case there are issues, such as: -
     * PM10 (a secondary post-processed modelling variable) has been included
     * without PM25 and coarsePM that it is ultimately based on.
     *
     * @param prox
     * @param ops
     */
    public static void checkVariableListConsistency(ProxyProcessor prox, FusionOptions ops) {
      
        ArrayList<Integer> ints = new ArrayList<>();
        RunParameters p = ops.getRunParameters();
        for (String var:p.modellingVars_nonMet) {
            int typeInt = ops.VARA.getTypeInt(var);
            if (typeInt<0 || ops.VARA.isMetVar[typeInt]) {
                EnfuserLogger.log(Level.FINER,p.getClass(),"Variable check: REMOVING "+ var);
            } else {
                ints.add(typeInt);
            }
        }
        
        //re-order. To be on the safe side, use the same order as in varAssociations.
        //For example, the order of O3 and NO2 DOES MATTER.
        Collections.sort(ints);
        ArrayList<String> ordered = new ArrayList<>();
        for (int typeInt:ints) {
            String type = ops.VARA.VAR_NAME(typeInt);
            ordered.add(type);
            EnfuserLogger.log(Level.FINER,p.getClass(),
                    "Variable check: added after reordering: "+ type);
        }
        
        p.modellingVars_nonMet= prox.init(ops, ordered);
    }   

}
