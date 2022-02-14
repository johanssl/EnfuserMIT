/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.core.varproxy;

import java.util.ArrayList;
import java.util.HashMap;
import org.fmi.aq.enfuser.core.AreaFusion;
import org.fmi.aq.enfuser.core.FusionCore;
import org.fmi.aq.enfuser.core.receptor.RP;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.meteorology.Met;
import org.fmi.aq.enfuser.datapack.main.Observation;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.datapack.source.StationSource;
import org.fmi.aq.enfuser.options.VarAssociations;
import org.fmi.aq.essentials.geoGrid.GeoGrid;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import org.fmi.aq.interfacing.InstanceLoader;

/**
 * This sub-class of ProxyRecipee describes an instruction of a proxy computation
 * as a mathematical expression. The expression itself can be symbolic formula
 * or an affine expression.
 * 
 * Based on the expression, this class can create predictions for the target
 * type and also handle input data proxying after the load phase. However,
 * this input proxying can be instructed to bypass gridded datasets or point
 * sources if needed. For example, LDSA gridded input for background is to be proxied
 * but measurements may exist already and the proxy process is not needed for
 * point input.
 * 
 * @author johanssl
 */
public class ExpressionProxy extends ProxyRecipe {

    private final Expression func;
    private final Expression func_inputProx;
    boolean inputGridProcessing = true;
    boolean inputPointProcessing = true;
    
    protected final String funcEx;
    protected final String inps;
    public ExpressionProxy(String funcExpression, int typeInt,
            String inputProxFunc, VarAssociations VARA) {
        super(VARA);
        this.typeInt = typeInt;
        this.funcEx = funcExpression;
        this.inps = inputProxFunc;
        
        if (funcExpression!=null) {
          this.func = InstanceLoader.getFromString(funcExpression,VARA);
        } else {
          this.func =null;  
        }
        
        if (inputProxFunc!=null) {
            this.func_inputProx = InstanceLoader.getFromString(inputProxFunc,VARA);
            this.hasInputProcessing = true;
            if (inputProxFunc.contains("NO_GRIDS")) this.inputGridProcessing = false;
             if (inputProxFunc.contains("NO_POINTS")) this.inputPointProcessing = false;
            
        } else {
            this.func_inputProx = null;
        }
    }
    
    
    public boolean inputProcessingHasMetVars() {
        if (this.func_inputProx==null) return false;
        ArrayList<String> all = this.func_inputProx.getVariableList(false);
        ArrayList<String> nonm = this.func_inputProx.getVariableList(true);
        return (all.size()>0 //there are variables
                && all.size()!= nonm.size()); //and the sizes differ
    }
    
    @Override
    public void printoutInfo() {

        EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"===============================\nSymbolicProxy: "
                + VARA.VAR_NAME(this.typeInt) +":");
        if (this.func_inputProx!=null){
            EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"\t has input function: "+this.func_inputProx.getFunctionString()
            +"\n\t\t Is enabled / also for grids: "+this.inputPointProcessing +"/"  +this.inputGridProcessing
            );
            EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"\t input processing has metVariables: "+ inputProcessingHasMetVars());
        }
        
        if (this.func!=null){
            EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"\t has prediction function: "+this.func.getFunctionString() );
            for (String var:func.getVariableList(false)) {
                double[] limits = func.getLimits(var);
                String lim ="";
                if (limits!=null) lim =", has limit: "+ limits[0] + " to "+ limits[1];
                EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"\t\t" + var + lim);
            }
        }
        EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"==========================================");  
    }

    @Override
    public float proxy(Met met, AreaFusion af, int h, int w, FusionCore ens,
       double lat, double lon, HashMap<String,Double> temp) {
       return (float)func.evaluate(af, h, w, met,temp);
    }

    @Override
    public float proxy(RP env, FusionCore ens) {
        return (float)func.evaluate(env, ens);
    }
    
    @Override
    public float[] proxyComponents(RP env, FusionCore ens) {
       return func.evaluateComponents(env, ens);
    }


    @Override
    public void dataPackProcessing(FusionCore ens) {
       if (this.func_inputProx==null) return;
       if (inputPointProcessing) inputProxyStations(ens);
       if (inputGridProcessing) inputProxyGrids(ens);
    }
    
     private void inputProxyGrids(FusionCore ens) {
         DataPack dp = ens.datapack;
         boolean getMet = inputProcessingHasMetVars();
         
          //get a list of materials we need for this process
        ArrayList<String> vars = this.func_inputProx.getVariableList(true);
        ArrayList<VariableData> vds = new ArrayList<>();
        for (String var:vars) {
            VariableData vd = dp.getVarData(var);
            if (vd==null) return;//no can do!
            vds.add(vd);
        }
        
        VariableData vd = vds.get(0);//take this is as the basis (any one would do fine)
        int lenMustBe = vds.size();
        
        for (AbstractSource s : vd.sources()) {
            if (s instanceof GridSource) {   
            } else {
                continue;
            }
            
                s.crossCheckAliasTypes(ens.datapack);//must make sure the alias types are available!

                GridSource base = (GridSource) s;
                HashMap<Integer,GridSource> sourcs =new HashMap<>();
                for (AbstractSource s2 : base.aliasTypes) {
                    if (s2.typeInt==this.typeInt) break;//no need to proxy when this kind
                    //of data already exist for the same ID.
                    
                    if (this.func_inputProx.hasVariableType(s2.typeInt) 
                            && s2 instanceof GridSource) {
                        GridSource stt = (GridSource) s2;
                        sourcs.put(s2.typeInt, stt);
                    }
                }

                if ((sourcs.size()+1) !=lenMustBe) {
                    continue;//did not find pm10
                }
                //if we get this far there are both st and stpm10 identified.
                ArrayList<GeoGrid> gg = base.getGrids();
                HashMap<String,GeoGrid> gridHash = new HashMap<>();
                
                for (GeoGrid g:gg) {
                    gridHash.clear();
                    
                    gridHash.put(ens.ops.VAR_NAME(base.typeInt), g);
                    //find other types from the sources
                    boolean allPresent = true;
                    for (GridSource st:sourcs.values()) {
                       GeoGrid oTest = st.getExactGrid(g.dt);
                       if (oTest==null) {
                           allPresent = false;
                       } else {
                           gridHash.put(ens.ops.VAR_NAME(st.typeInt), oTest);
                       }
                    }//for other sources
                    
                    if (allPresent) {
                        GeoGrid newG = proxyGrid(g, gridHash, ens,getMet);
                        dp.addGrid(s.dbl, typeInt, newG, null, ens.DB, ens.ops);
                    }//if all present => clone input proxy and add it.
                    
                }//for base observation
   
        }//for source
     }
     
     private GeoGrid proxyGrid(GeoGrid base, HashMap<String,GeoGrid> gridHash,
             FusionCore ens, boolean getMet) {
         
         GeoGrid gnb = new GeoGrid(new float[base.H][base.W],base.dt.clone(),base.gridBounds.clone());
         HashMap<String,Double> varHash = new HashMap<>();
         for (int h =0;h<base.H;h++) {
             for (int w = 0;w<base.W;w++) {
                 varHash.clear();
                 double lat = base.getLatitude(h);
                 double lon = base.getLongitude(w);
                 Met met = null;
                 if (getMet) met= ens.getStoreMet(base.dt, lat, lon);
                 
                 for (String var:gridHash.keySet()) {
                     GeoGrid varg = gridHash.get(var);
                     double val = varg.getValue_closest(lat, lon);
                     varHash.put(var, val);
                 }
                 
                 //finally the proxy for this h,w
                 double nval = this.func_inputProx.evaluate(varHash, met,true);
                 gnb.values[h][w] = (float)nval; 
             }
         }
         
         return gnb;
     }
    
    private void inputProxyStations(FusionCore ens) {
         DataPack dp = ens.datapack;
         boolean getMet = inputProcessingHasMetVars();
          //get a list of materials we need for this process
        ArrayList<String> vars = this.func_inputProx.getVariableList(true);
        ArrayList<VariableData> vds = new ArrayList<>();
        for (String var:vars) {
            VariableData vd = dp.getVarData(var);
            if (vd==null) return;//no can do!
            vds.add(vd);
        }
        if (vds.isEmpty()) return;
        
        VariableData vd = vds.get(0);//take this is as the basis (any one would do fine)
        int lenMustBe = vds.size();
        
        for (AbstractSource s : vd.sources()) {
            if (s instanceof StationSource) {   
            } else {
                continue;
            }
           EnfuserLogger.log(Level.FINER,ExpressionProxy.class,
                   "Input proxying for station: "+ens.ops.VAR_NAME(this.typeInt) +" for " + s.ID);
                s.crossCheckAliasTypes(ens.datapack);//must make sure the alias types are available!

                StationSource base = (StationSource) s;
                HashMap<Integer,StationSource> sourcs =new HashMap<>();
                for (AbstractSource s2 : base.aliasTypes) {
                    if (s2.typeInt==this.typeInt) break;//no need to proxy when this kind
                    //of data already exist for the same ID.
                    
                    if (this.func_inputProx.hasVariableType(s2.typeInt) 
                            && s2 instanceof StationSource) {
                        StationSource stt = (StationSource) s2;
                        sourcs.put(s2.typeInt, stt);
                    }
                }
                EnfuserLogger.log(Level.FINER,ExpressionProxy.class,"\t Found "+ (sourcs.size()+1) +" alias sources.");
                if ((sourcs.size()+1) !=lenMustBe) {
                    continue;//did not find pm10
                }
                //if we get this far there are both st and stpm10 identified.
                ArrayList<Observation> obs = base.getLayerObs();
                HashMap<String,Double> varHash = new HashMap<>();
                
                for (Observation ob : obs) {
                    varHash.clear();
                    varHash.put(ens.ops.VAR_NAME(ob.typeInt), (double)ob.value);
                    //find other types from the sources
                    boolean allPresent = true;
                    for (StationSource st:sourcs.values()) {
                       Observation oTest = st.getExactObservation(ob);
                       if (oTest!=null) {
                           varHash.put(ens.ops.VAR_NAME(oTest.typeInt), (double)oTest.value);
                       } else {
                           allPresent = false;
                       }
                    }//for other sources
                    
                    if (allPresent) {
                        Met met = null;
                        if (getMet)met = ens.getStoreMet(ob);
                        double input = this.func_inputProx.evaluate(varHash, met,true);
                        Observation newO = ob.cloneDifferentType(this.typeInt);
                        newO.value = (float)input;
                        newO.dtCanChanged = true;//there is a possibilty that local time conversion could occur if this is not set like this. The source observations are always in UTC.
                        dp.addObservation(newO, ens.ops, ens.DB);
                    }//if all present => clone input proxy and add it.
                    
                }//for base observation
   
        }//for source
    }
    
    @Override
    public ArrayList<String> checkVariableListConsistency(
            ArrayList<String> nonMetTypes) {
       
       String thisname = VARA.VAR_NAME(typeInt);
       if (!nonMetTypes.contains(thisname)) return nonMetTypes;
       //not included, not my business.
        boolean nonMet = true;
        
        if (func!=null) {
            ArrayList<String> arr =func.getVariableList(nonMet);

            for (String mustBe:arr) {
                if (!nonMetTypes.contains(mustBe)) {
                    EnfuserLogger.log(Level.FINER,ExpressionProxy.class,
                            "Symbolic proxy: forcing variable to list: "+ mustBe);
                    nonMetTypes.add(mustBe);
                }
            }
        }
        
        if (func_inputProx!=null) {
            ArrayList<String> arr =func_inputProx.getVariableList(nonMet);

            for (String mustBe:arr) {
                if (!nonMetTypes.contains(mustBe)) {
                    EnfuserLogger.log(Level.FINER,ExpressionProxy.class,
                            "Symbolic proxy (input): forcing variable to list: "+ mustBe);
                    nonMetTypes.add(mustBe);
                }
            }
        }
        
        return nonMetTypes;
        
    }

    @Override
    public ProxyRecipe hardClone() {
       return new ExpressionProxy(funcEx, typeInt,inps, VARA);
    }

    
}
