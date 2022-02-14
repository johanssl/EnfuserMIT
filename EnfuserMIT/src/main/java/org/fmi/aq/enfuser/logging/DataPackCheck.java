/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.logging;

import java.util.ArrayList;
import java.util.logging.Level;
import org.fmi.aq.enfuser.datapack.main.DataPack;
import org.fmi.aq.enfuser.datapack.main.VariableData;
import org.fmi.aq.enfuser.datapack.source.AbstractSource;
import org.fmi.aq.enfuser.datapack.source.GridSource;
import org.fmi.aq.enfuser.options.ERCFarguments;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.VarAssociations;

/**
 * This class checks the content of DataPack with respect to the list
 * of variables to be loaded. This then provides information on the content 
 * and issues warnings if critical types of information seem to be missing.
 * 
 * This class can also check and notify if a source X for variable Y is missing,
 * or, X exists but the resolution of the data is lower than Z.
 * 
 * @author johanssl
 */
public class DataPackCheck {
  
   final DataPack dp;
   final FusionOptions ops;
   VarAssociations VARA; 
   
   ArrayList<Integer> contentChecks_typeI = new ArrayList<>();
   ArrayList<String> contentChecks_ID = new ArrayList<>();
   ArrayList<Double> contentChecks_resReq_km = new ArrayList<>();
   
    public DataPackCheck(DataPack dp, FusionOptions ops) {
        this.dp=dp;
        this.ops = ops;
        this.VARA = ops.VARA;
        
       ERCFarguments args = ops.getArguments();
       String line = args.get(ERCFarguments.CUSTOM_BOOLEAN_SETTINGS);
        EnfuserLogger.log(Level.INFO, this.getClass(),
                   "DataPackCheck: finding instructions from"+line);
        
       String conts = args.getCustomContent(ERCFarguments.CUSTOM_CONTENT_CHECK);
       if (conts!=null) {
           EnfuserLogger.log(Level.INFO, this.getClass(),
                   "Found content check instructions: "+conts 
                           +".These should be of the form, e.g. 'NO2,CMAQ,null &SO2,SILAM,3km &...'");
           
           conts = conts.replace(" ", "");
           String[] sp = conts.split("&");
           try {
               for (String s:sp) {//SO2,SILAM,3km
                   String[] type_id_res = s.split(",");
                   String type = type_id_res[0];
                   String ID = type_id_res[1];
                   String res = type_id_res[2].replace("km", "");
                   
                   int typeInt = ops.VARA.getTypeInt(type);
                   if (typeInt ==-1) {
                       EnfuserLogger.log(Level.INFO, this.getClass(),
                               "Unknown type (DataPackCheck): "+type);
                       continue;
                   }
                   
                   Double reskm = Double.MAX_VALUE;
                   if (!res.contains("null")) {
                     reskm= Double.valueOf(res);
                     if (reskm<0) reskm = Double.MAX_VALUE;//interpret this as 'any resolution will do'
                   }
                   
                   EnfuserLogger.log(Level.INFO, this.getClass(),
                           "\t adding: "+type +", ID="+ID +", resolution_km < "+reskm);
                   this.contentChecks_typeI.add(typeInt);
                   this.contentChecks_ID.add(ID);
                   this.contentChecks_resReq_km.add(reskm);
                   
               }//for elements
           } catch (Exception e) {
               EnfuserLogger.log(e,Level.WARNING, this.getClass(),
                   "Content check instructions: "+conts +" parse failed.");
           }
       }//if instructions given at ERCF custom
       else {
           EnfuserLogger.log(Level.INFO, this.getClass(),
                   "D\t none found for " + ERCFarguments.CUSTOM_CONTENT_CHECK +">");
       }
    }
    
    public static void main(String[] args) {
        FusionOptions ops = new FusionOptions("India","Delhi");
        ERCFarguments arg = ops.getArguments();
        String conts = arg.getCustomContent(ERCFarguments.CUSTOM_CONTENT_CHECK);
        System.out.println(conts);
        new DataPackCheck(null,ops);
    }
    
    private ArrayList<String> checkMissingOrLowResolution() {
         ArrayList<String> warns = new ArrayList<>();
        if (this.contentChecks_ID.isEmpty()) return warns;
       
        
        for (int k =0;k<this.contentChecks_ID.size();k++) {
            try {
            int typeInt = this.contentChecks_typeI.get(k);
            String ID = this.contentChecks_ID.get(k);
            Double lowerThan_km = this.contentChecks_resReq_km.get(k);

            VariableData vd = this.dp.getVarData(typeInt);
            if (vd==null) {//no data at all for the variable type!
                warns.add("VariableData missing for: "+ops.VAR_NAME(typeInt)+", "+ID);
                continue;
            }
            
            AbstractSource sourc = vd.getSource(ID);
            if (sourc==null) {//the source is missing!
                 warns.add("Source missing for: "+ops.VAR_NAME(typeInt)+", "+ID);
                continue;
            }
            
            //ok, at least the source is there. Resolution?
            if (sourc instanceof GridSource) {//StationSource is OK by default.
                GridSource gs = (GridSource)sourc;
                double res_km = gs.getDataResolution_km();
                if (res_km > lowerThan_km) {//too coarse!
                    warns.add("GriddedSource resolution is not acceptable: "
                            +ops.VAR_NAME(typeInt)+", "+ID +", res_km="+res_km); 
                }
            }
            
            }catch (Exception e) {
                 EnfuserLogger.log(e,Level.WARNING, this.getClass(),
                   "Content check process failed, k = " +k);
            }
        }//for checks 
        
        return warns;
    }
    
   public ArrayList<String> printout() {
      ArrayList<String> arr = new ArrayList<>();
      
      ArrayList<String> warnings = new ArrayList<>();
      ArrayList<String> severe = new ArrayList<>();
              //evaluation
      ArrayList<String> vars = ops.getRunParameters().loadVars(ops);
        arr.add("/*/*/*/*/*/*/*/*/*/*/*/DATA PACK /*/*/*/*/*/*/*/*/*/*/*/*/");
        String line = "Checking data content with respect to "
                + ops.getRunParameters().start().getStringDate_noTS() 
                +" - " +ops.getRunParameters().end().getStringDate_noTS();
        arr.add(line);
        
        for (String var:vars) {
            int typeInt = VARA.getTypeInt(var);
            VariableData vd = dp.variables[typeInt];
            
            if (vd == null || vd.size() == 0) {
                arr.add("No data for " + var);
                //no data for a variable type. How bad is this?
                Level l = this.getSeverityMissingVD(typeInt,ops);
                if (l==Level.WARNING) {
                    warnings.add("No data for " + var +". For this type data should exist.");
                } else if (l==Level.SEVERE) {
                    severe.add("No data for " + var +". For this type data MUST exist!");
                }
                
            } else {//variable Data exists
                dp.size += vd.size();
                arr.add("(" + var + ") DataPack size is " + vd.sizeString());
                
                for (AbstractSource s : vd.sources()) {
                    try {
                    arr.add("\t " + s.oneLinePrinout(ops));
                    
                    Level l = s.getSeverityDataQuality(ops);
                        if (l==Level.WARNING) {
                            warnings.add(s.getDataQualityAlert());
                        } else if (l==Level.SEVERE) {
                            severe.add(s.getDataQualityAlert());
                        }
                        
                    } catch (NullPointerException e) {
                        EnfuserLogger.log(e,Level.SEVERE, this.getClass(),
                                "Source printout failed for "+ s.ID);
                    }
                }//for sources
                
                //check for gridded data source availability
                ArrayList<GridSource> gs = vd.griddedSources();
                if (gs==null ||gs.isEmpty()) {
                    Level l = this.getSeverityMissingGridSource(typeInt,ops);
                        if (l==Level.WARNING) {
                            warnings.add("Gridded data absent for " + var +". For this type data should exist.");
                        } else if (l==Level.SEVERE) {
                            severe.add("Gridded data absent for " + var +". For this type data MUST exist!");
                        }
                }  
            }//if content
            arr.add("");
        }//for variables
       
        ArrayList<String> arr2 =checkMissingOrLowResolution();
        if (!arr2.isEmpty()) warnings.addAll(arr2);
        
            arr.add("\n====DataPack Warnings:");
        for (String s:warnings) {
            arr.add("[W] "+s);
        }
            if (warnings.isEmpty()) arr.add("No warnings.");
           
            arr.add("====DataPack SEVERE issues:");
        for (String s:severe) {
            arr.add("[S] "+s);
        }
            if (severe.isEmpty()) arr.add("No severe issues found.");
        arr.add("/*/*/*/*/*/*/*/*/*/*/*/DATA PACK /*/*/*/*/*/*/*/*/*/*/*/*/\n"); 
        
        //log content
        EnfuserLogger.logReport(ops.getRunParameters().areaID(), "DataPackCheck", arr);
        for (String s:arr) {
            if (s.startsWith("[W]")) {
                EnfuserLogger.log(Level.WARNING, DataPackCheck.class, s);
            } else if (s.startsWith("[S]")) {
                EnfuserLogger.log(Level.SEVERE, DataPackCheck.class, s);
            } else {
                EnfuserLogger.log(Level.INFO, DataPackCheck.class, s);
            } 
        }

        return arr;
    }



    private Level getSeverityMissingVD(int typeInt, FusionOptions ops) {
        
        if (typeInt == ops.VARA.VAR_NO2 
                || typeInt == ops.VARA.VAR_PM25 
                || typeInt == ops.VARA.VAR_O3
                || typeInt == ops.VARA.VAR_TEMP
                || typeInt == ops.VARA.VAR_ABLH) {
            return Level.WARNING;//this is definately suspicous and probably dangerous
            
        } else if (typeInt == ops.VARA.VAR_WIND_E 
                || typeInt == ops.VARA.VAR_WIND_N) {
            return Level.SEVERE;//the model run is doomed!
        }
        
        return Level.INFO;
    }

    private Level getSeverityMissingGridSource(int typeInt, FusionOptions ops) {
        if (ops.VARA.isPrimary[typeInt] 
                || typeInt == ops.VARA.VAR_TEMP
                || typeInt == ops.VARA.VAR_ABLH) {
            return Level.WARNING;//this is definately suspicous and probably dangerous
            
        } else if (typeInt == ops.VARA.VAR_WIND_E 
                || typeInt == ops.VARA.VAR_WIND_N) {
            return Level.SEVERE;//the model run is doomed!
        }
        
        return Level.INFO;
    }  
    
    
}
