/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fmi.aq.enfuser.customemitters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import static org.fmi.aq.enfuser.ftools.FuserTools.copyTo;
import org.fmi.aq.enfuser.options.FusionOptions;
import org.fmi.aq.enfuser.options.GlobOptions;
import org.fmi.aq.essentials.geoGrid.Boundaries;
import org.fmi.aq.essentials.plotterlib.Visualization.FileOps;
import static org.fmi.aq.essentials.plotterlib.Visualization.VisualOptions.Z;
import org.fmi.aq.enfuser.logging.EnfuserLogger;
import java.util.logging.Level;
import static org.fmi.aq.essentials.gispack.utils.Tools.produceGEpins;

/**
 *The purpose of this class is to create a list of point emitters using 
 * a tabled dataset for world power plants. 
 * 
 * Note: The tabled dataset must be present
 * under /setupFiles/powerplants/. During the Setup process, the necessary 
 * LTM-profile files are copied to the emitter directory. 
 * 
 * The created list of power plant also contains non-thermal power plants. However,
 * These are set not to act as combustion emission sources in the model 
 * (power capacity, MW set to 0).
 * 
 * In the assessment the geographic bounds of the modelling region is taken 
 * into account. The methods of this class are used automatically during
 * the regional Setup, however, they can also be launched manually
 * via EnfuserGUI.
 * 
 * 
 * @author johanssl
 */
public class PowerPlantProcessor {
   
  public final static String FNAME = "world-power-plants-list.csv";
  public final static int NAME_INDEX = 1;
  public final static int CAP_MW_INDEX = 2;
  public final static int STATUS_INDEX = 6;
  public final static int EFFICIENCY_INDEX = 7;
  public final static int LAT_INDEX = 13;
  public final static int LON_INDEX = 14;
  public final static int TYPEDESC_INDEX = 17;
  public final static int FUEL_INDEX = 19;
  
  public final static int SOX_CTRL_INDEX = 45;
  public final static int NOX_CTRL_INDEX = 46;
  public final static int PM_CTRL_INDEX = 47;
  public final static int TYPE1_INDEX = 56;
  
  public final static String TYPE_TAG ="TYPE2<";
  public final static String STATUS_TAG ="STATUS<";
  public final static String CAPMW_TAG ="CAP_MW<";
  public final static String EFF_TAG ="EFFICIENCY<";
  public final static String FUEL_TAG ="FUEL<";
  
  public final static String RED_NOX_TAG ="RED_NOX<";
  public final static String RED_SOX_TAG ="RED_SOX<";
  public final static String RED_PM_TAG ="RED_PM<";
  
  public final static String SOURCE_DIR ="setupFiles"+Z + "powerplants"+Z;

/**
 * Create a list of point emitter lines for power plants, for the given region
 * @param b the region bounds.
 * @return A list of lines that are in proper format to be used with 'PemReader' class.
 */  
  public static ArrayList<String> getListForArea(Boundaries b) {
      EnfuserLogger.log(Level.FINER,PowerPlantProcessor.class,
              "PowerPlantProcessor: list creation for "+ b.toText());
      String fname = GlobOptions.get().getRootDir() + SOURCE_DIR+ FNAME;
      
      ArrayList<String> outs = new ArrayList<>();
      outs.add("Name;Units;LAT;LON;HEIGHT;DESCRIPTIVE;MODS;LTM;");
      
      File test = new File(fname);
      if (!test.exists()) {
          EnfuserLogger.log(Level.FINER,PowerPlantProcessor.class,
                  "PowerPlantProcessor: file does not exits: "+ fname);
          return outs;
      }
      ArrayList<String> arr = FileOps.readStringArrayFromFile(fname);
      
      
      int errs =0;
      int numbErrs =0;
      ArrayList<String> fails = new ArrayList<>();
      for (String line:arr) {
          String[] split = line.split(";");
          
          try {
              
              String name = split[NAME_INDEX];
              double cap;
              
              double lat,lon;
              
              try {
              lat = Double.valueOf(split[LAT_INDEX]);
              lon = Double.valueOf(split[LON_INDEX]);
              cap = Double.valueOf(split[CAP_MW_INDEX]);
              } catch (NumberFormatException e) {
                  numbErrs++;
                  continue;
              }
                      
              if (b!=null && !b.intersectsOrContains(lat, lon)) continue;
              
              String desc = "power," +split[TYPE1_INDEX]+"," 
                      +TYPE_TAG + split[TYPEDESC_INDEX]+">,"
                      +STATUS_TAG + split[STATUS_INDEX]+">,"
                       +CAPMW_TAG + split[CAP_MW_INDEX] + ">,";
                     if (split[EFFICIENCY_INDEX].length()>0) desc+=EFF_TAG + split[EFFICIENCY_INDEX] + ">,";
                     
                      
                    if (split[FUEL_INDEX].length()>0)  desc+=FUEL_TAG + split[FUEL_INDEX] + ">,";  
              
                    if (split[NOX_CTRL_INDEX].length()>1) desc +=RED_NOX_TAG + split[NOX_CTRL_INDEX]+">,";
                    if (split[SOX_CTRL_INDEX].length()>1) desc +=RED_SOX_TAG + split[SOX_CTRL_INDEX]+">,";
                    if (split[PM_CTRL_INDEX].length()>1)  desc +=RED_NOX_TAG + split[PM_CTRL_INDEX]+">";
                      
              
              if (split[STATUS_INDEX].toLowerCase().contains("decom")) {
                  cap =0;
              }
              
              String ltmName = parseLtmName(split[TYPE1_INDEX]);
              if (ltmName.equals(LTM_CLEAN)) cap =0;
              
              String height ="140";
              String coeffAdjusts = parseFactorAdjustments(desc);
              
               String out = 
                       name +";" 
                       + (int)cap +";" 
                       + lat+";" 
                       + lon +";"
                       + height +";"
                       + desc +";"
                       + coeffAdjusts +";"
                       +ltmName +";"
                       ; 
              
               outs.add(out);
               
          } catch (Exception e) {
              errs++;
              fails.add(line);
          }
          
          
      }//for lines
      
      EnfuserLogger.log(Level.FINER,PowerPlantProcessor.class,
              "Done. parsing errors (of which there can often be a hundred or so) = "
              + errs +", coordinate/capacity parse errors = "
              + numbErrs +". Total lines = " + arr.size());
      
        for (String line:fails) {
          EnfuserLogger.log(Level.FINER,PowerPlantProcessor.class,
                  "\t This line could not be parsed: "+line);
        }
      

      return outs;
  }
  
  private final static String LTM_GEN = "genericPower";
  private final static String LTM_COAL = "genericCoalPower"; //coal
  private final static String LTM_GAS = "genericGasPower"; //gas
  private final static String LTM_OIL = "genericOilPower"; //oil
  private final static String LTM_WASTE = "genericWastePower"; //waste
  private final static String LTM_CLEAN = "genericCleanPower"; //waste
  
/**
 * Assess the proper filename body to be used for the given power plant type.
 * @param type the power plant type.
 * @return String LTM-profile name body.
 */
  private static String parseLtmName(String type) {
      type = type.toLowerCase();
      String ltm =LTM_GEN;
      if (type.contains("coal")) {
          ltm = LTM_COAL;
      } else if (type.contains("gas")) {
          ltm = LTM_GAS;
      } else if (type.contains("oil")) {
          ltm = LTM_OIL;
      } else if (type.contains("waste")) {
         ltm = LTM_WASTE; 
      } 
      
      else if (type.contains("hydro") || type.contains("solar") 
              || type.contains("wind") ||type.contains("geoth") || type.contains("nucl")) {
         ltm = LTM_CLEAN; 
      } 
      
      return ltm;
  }
  /**
   * TODO: Adjust emission factors based on defined Emission Abatement Equipment.
   * @param desc String description that contain tags if emission reduction
   * methods are present
   * @return String that are case-by-case emission factor scaler for the
   * individual power plant. Format example: 'NO2=0.5,PM25=0.1,...'
   */
  private static String parseFactorAdjustments(String desc) {
      String adj ="";
      //make an implementation
      return adj;
  }
  
  public final static String POINTLIST_FILE="POINT_pointlist_misc_0_tmin_tmax_-90_90_-180_180.csv";
  public final static void main(String[] args) {
      String regName = "India";
      FusionOptions ops = new FusionOptions(regName);
      Boundaries b = ops.getRegionBounds();
      createForRegion(regName, b, true,true,false);
  }
  
  /**
   * Launch the assessment of power plant point emitters for a region (specified in
   * FusionOptions) and create the necessary emitter datasets.
   * @param ops the options that contain directory paths and the region in question.
   * @param copyLTMs if true, then LTM-profile files are copied when needed. (use true)
   * @param gePins if true, then additional visualization in the gridEmitter directory
   * will be produced.
   * @param areaTest
   */
  public static void createForRegion(FusionOptions ops, boolean copyLTMs,
          boolean gePins, boolean areaTest) {
    
      Boundaries b = ops.getRegionBounds();
      String regName = ops.getRegionName();
      createForRegion(regName, b, copyLTMs,gePins,areaTest);
  }
  
  private static void backupFile(String dir, String fname) {
      //create a backup
      File test = new File(dir + fname);
      if (test.exists()) {
          EnfuserLogger.log(Level.FINER,PowerPlantProcessor.class,
                  "Creating a backup copy from existing "+fname);
          String bdir = dir +"pointEmBackup"+Z;
          File f = new File(bdir);
          f.mkdirs();
          try {
              copyTo(test, new File(bdir+fname));
          } catch (IOException ex) {
              Logger.getLogger(PowerPlantProcessor.class.getName()).log(Level.WARNING, null, ex);
          }
      }
      
  }
          
   /**
   * Launch the assessment of power plant point emitters for a region and 
   * create the necessary emitter datasets.
   * @param regName the region name, e.g., "Finland". Note that the region must
   * be an installed one (with Setup).
   * @param b region bounds
   * @param copyLTMs if true, then LTM-profile files are copied when needed. (use true)
   * @param gePins if true, then additional visualization in the gridEmitter directory
   * will be produced.
   * @param areaTest
   */
  public static void createForRegion(String regName, Boundaries b,
          boolean copyLTMs, boolean gePins, boolean areaTest) {
      ArrayList<String> outs = getListForArea(b);
      String dir = emitterDir(regName);
      
      if (areaTest) dir = GlobOptions.get().getRootDir() +"Results"+Z;
      
      String fname = POINTLIST_FILE;
      EnfuserLogger.log(Level.FINER,PowerPlantProcessor.class,
              "Saving the list (with size " + (outs.size()-1) + ") to "
              + dir + fname ) ;
     if (!areaTest) backupFile(dir, fname);
      
      
      FileOps.printOutALtoFile2(dir, outs, fname, false);
      if (copyLTMs && !areaTest) {
          copyLTMs(regName, outs); 
      }
      createGEpins(regName,dir);
  }
  
  /**
   * Form the path to regional emitter directory.
   * @param regName region name
   * @return path to directory
   */
  public final static String emitterDir(String regName) {
       String dir = GlobOptions.get().regionalDataDir(regName)
              + "emitters" + Z 
              + "gridEmitters"+Z;
       return dir;
  }
  
  /**
   * Copy LTM-profiles to emitter directory, taking into account the types
   * of power plants present in the list.
   * @param regName region name
   * @param pointlist a list of String data that is in the point-list format. 
   */ 
  public static void copyLTMs(String regName, ArrayList<String> pointlist) {
      
     HashMap<String,File[]> toCopy = new HashMap<>(); 
     String sourcedir = GlobOptions.get().getRootDir() + SOURCE_DIR;
     String emdir = emitterDir(regName);
     ArrayList<String> cands = new ArrayList<>();
     cands.add(LTM_GEN);
     cands.add(LTM_OIL);
     cands.add(LTM_COAL);
     cands.add(LTM_GAS);
     cands.add(LTM_WASTE);
     cands.add(LTM_CLEAN);
     
     
     for (String s:pointlist) {
         for (String cand:cands) {
             if(s.contains(cand)) {
                 String local = "xprofile_LTM_"+cand+".csv";
                 backupFile(emdir, local);
                 File sourc = new File(sourcedir + local);
                 File dest = new File(emdir + local);
                 toCopy.put(cand,
                         new File[]{sourc, dest});
                 break;
             }
             
         }//for cands
     }//for lines
     
     for (File[] sourcDest:toCopy.values()) {
         File sourc = sourcDest[0];
         File dest = sourcDest[1];
         try {
             copyTo(sourc, dest);
         } catch (IOException ex) {
             Logger.getLogger(PowerPlantProcessor.class.getName()).log(Level.WARNING, null, ex);
         }

     }//for file copy
      
  }
  
  /**
   * Create an additional Google Earth pin visualization for the
   * list of point emitters.
   * @param regName region name
   * @param dir
   */
    public static void createGEpins(String regName, String dir) {

      ArrayList<String> arr = FileOps.readStringArrayFromFile(dir + POINTLIST_FILE);

      ArrayList<String[]> name_latlon = new ArrayList<>();
      for (String line : arr) {
          String[] split = line.split(";");

          String[] add = {
            split[Pem.IND_NAME] + ", MW="+split[Pem.IND_UNITS],
            split[Pem.IND_LAT],
            split[Pem.IND_LON]
          };
         name_latlon.add(add); 
      }

      produceGEpins(dir, "pins_pointList", name_latlon);

  }

  
}
